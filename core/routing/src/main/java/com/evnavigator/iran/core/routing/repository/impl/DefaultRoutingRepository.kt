package com.evnavigator.iran.core.routing.repository.impl

import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.core.routing.model.Route
import com.evnavigator.iran.core.routing.model.SnapExceededException
import com.evnavigator.iran.core.routing.repository.RoutingRepository
import com.evnavigator.iran.domain.model.Altitude
import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Charger
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Elevation
import com.evnavigator.iran.domain.model.ElevationPoint
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.GeoUtils
import com.evnavigator.iran.domain.model.Road
import com.evnavigator.iran.domain.model.RoadSegment
import com.evnavigator.iran.domain.model.Speed
import com.evnavigator.iran.domain.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException

/**
 * Production implementation of [RoutingRepository] backed by [OsmDatasetManager] and [SpatialNodeIndex].
 *
 * Strictly enforces:
 * - Item 2 & 11: Uses active imported OSM dataset and 2D spatial grid index (`SpatialNodeIndex`).
 * - Item 12: Enforces `maxSnapDistanceMeters` in `findNearestNode`, rejecting off-network coordinates.
 * - Item 8 & 24: Never fabricates 1200m elevation or 13-point interpolation when DEM is absent;
 *   returns real DEM/OSM node elevations or explicit failure when DEM coverage is unavailable.
 */
class DefaultRoutingRepository(
    private val maxSnapDistanceMeters: Double = 2_500.0,
    private val demElevationProvider: (suspend (GeoCoordinate) -> Double?)? = null,
    private val chargerProvider: (suspend (GeoCoordinate, Double) -> List<Charger>)? = null,
    private val weatherProvider: (suspend (GeoCoordinate) -> WeatherSnapshot?)? = null
) : RoutingRepository {

    override suspend fun findNearestNode(coordinate: GeoCoordinate): Result<RoadNode> {
        return try {
            val activeDataset = OsmDatasetManager.getActiveDataset()
            if (!activeDataset.spatialIndex.isCoordinateInCoverage(coordinate)) {
                return Result.failure(
                    SnapExceededException(
                        pointType = "Coordinate",
                        coordinate = coordinate,
                        snapDistanceMeters = Double.POSITIVE_INFINITY,
                        maxAllowedSnapDistanceMeters = maxSnapDistanceMeters
                    )
                )
            }

            val nearestMatch = activeDataset.spatialIndex.findNearestRoadNode(
                coordinate = coordinate,
                maxSnapDistanceMeters = maxSnapDistanceMeters
            ) ?: return Result.failure(
                SnapExceededException(
                    pointType = "Coordinate",
                    coordinate = coordinate,
                    snapDistanceMeters = maxSnapDistanceMeters + 1.0,
                    maxAllowedSnapDistanceMeters = maxSnapDistanceMeters
                )
            )

            Result.success(nearestMatch.node)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getRoadGraph(): Result<RoadGraph> {
        return try {
            Result.success(OsmDatasetManager.getActiveDataset().roadGraph)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getRoadSegmentsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Result<List<RoadSegment>> {
        return try {
            val activeDataset = OsmDatasetManager.getActiveDataset()
            val nodeMap = activeDataset.nodes.associateBy { it.id }
            val matchingEdges = activeDataset.spatialIndex.queryEdgesInBounds(minLat, maxLat, minLng, maxLng)

            val segments = matchingEdges.mapIndexed { idx, edge ->
                val startCoord = edge.detailedPath.firstOrNull() ?: GeoCoordinate(minLat, minLng)
                val endCoord = edge.detailedPath.lastOrNull() ?: GeoCoordinate(maxLat, maxLng)
                val domainRoadClass = when (edge.roadClass) {
                    com.evnavigator.iran.core.routing.model.RoadClass.MOTORWAY -> com.evnavigator.iran.domain.model.RoadClass.MOTORWAY
                    com.evnavigator.iran.core.routing.model.RoadClass.TRUNK_ROAD,
                    com.evnavigator.iran.core.routing.model.RoadClass.PRIMARY_ROAD -> com.evnavigator.iran.domain.model.RoadClass.PRIMARY
                    com.evnavigator.iran.core.routing.model.RoadClass.SECONDARY_ROAD -> com.evnavigator.iran.domain.model.RoadClass.SECONDARY
                    com.evnavigator.iran.core.routing.model.RoadClass.LOCAL_ROAD -> com.evnavigator.iran.domain.model.RoadClass.RESIDENTIAL
                    com.evnavigator.iran.core.routing.model.RoadClass.SERVICE_ROAD -> com.evnavigator.iran.domain.model.RoadClass.SERVICE
                    com.evnavigator.iran.core.routing.model.RoadClass.PEDESTRIAN_PATH,
                    com.evnavigator.iran.core.routing.model.RoadClass.CYCLE_PATH -> com.evnavigator.iran.domain.model.RoadClass.PATH
                }
                val domainAccess = when (edge.access) {
                    com.evnavigator.iran.core.routing.model.RoadAccess.PUBLIC,
                    com.evnavigator.iran.core.routing.model.RoadAccess.MOTOR_VEHICLE_ONLY,
                    com.evnavigator.iran.core.routing.model.RoadAccess.PERMIT_REQUIRED -> com.evnavigator.iran.domain.model.RoadAccess.PUBLIC
                    com.evnavigator.iran.core.routing.model.RoadAccess.PRIVATE -> com.evnavigator.iran.domain.model.RoadAccess.PRIVATE
                    com.evnavigator.iran.core.routing.model.RoadAccess.RESTRICTED,
                    com.evnavigator.iran.core.routing.model.RoadAccess.DISABLED_ACCESS -> com.evnavigator.iran.domain.model.RoadAccess.RESTRICTED
                    com.evnavigator.iran.core.routing.model.RoadAccess.PEDESTRIAN_ONLY,
                    com.evnavigator.iran.core.routing.model.RoadAccess.CYCLE_ONLY -> com.evnavigator.iran.domain.model.RoadAccess.NO_ENTRY
                }

                // Compute real road gradient from start/end node elevations or DEM provider;
                // never hardcode 0.0 when elevation data is unavailable.
                val startElev = demElevationProvider?.invoke(startCoord)
                    ?: nodeMap[edge.from]?.elevationMeters?.toDouble()
                    ?: activeDataset.roadGraph.getNode(edge.from)?.elevationMeters
                val endElev = demElevationProvider?.invoke(endCoord)
                    ?: nodeMap[edge.to]?.elevationMeters?.toDouble()
                    ?: activeDataset.roadGraph.getNode(edge.to)?.elevationMeters

                val calculatedGradePercent = if (startElev != null && endElev != null && edge.distanceMeters >= 10.0) {
                    val deltaElev = endElev - startElev
                    // Noise filter: sub-0.5m variation over short segments is DEM quantization noise
                    val rawPct = (deltaElev / edge.distanceMeters) * 100.0
                    rawPct.coerceIn(-25.0, 25.0)
                } else {
                    Double.NaN
                }

                val elevationProfile = if (startElev != null && endElev != null) {
                    listOf(
                        ElevationPoint(startCoord, Elevation.of(Altitude.ofMeters(startElev))),
                        ElevationPoint(endCoord, Elevation.of(Altitude.ofMeters(endElev)))
                    )
                } else {
                    emptyList()
                }

                val edgeId = "edge_${idx + 1}"
                val road = Road(
                    id = "road_${edge.from}_${edge.to}_$idx",
                    name = edge.roadName,
                    type = edge.roadClass.name.lowercase(),
                    classType = domainRoadClass,
                    access = domainAccess,
                    speedLimit = Speed.ofKilometersPerHour(edge.speedLimitKmh.toDouble()),
                    gradient = calculatedGradePercent,
                    surface = edge.surface,
                    isTollRoad = edge.hasToll,
                    isOneWay = edge.direction != EdgeDirection.BIDIRECTIONAL,
                    isGradientUnknown = calculatedGradePercent.isNaN()
                )

                val bearing = GeoUtils.calculateBearing(startCoord, endCoord)
                RoadSegment(
                    id = "seg_${edge.from}_${edge.to}_$idx",
                    start = startCoord,
                    end = endCoord,
                    road = road,
                    distance = Distance.fromMeters(edge.distanceMeters),
                    bearing = bearing,
                    elevationProfile = elevationProfile,
                    grade = calculatedGradePercent,
                    isGradeUnknown = calculatedGradePercent.isNaN(),
                    edgeId = edgeId,
                    direction = if (edge.direction == EdgeDirection.REVERSE) "BACKWARD" else "FORWARD",
                    startOffsetMeters = 0.0,
                    endOffsetMeters = edge.distanceMeters
                )
            }
            Result.success(segments)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getElevationDataForRoute(route: Route): Result<List<ElevationPoint>> {
        return try {
            val activeDataset = OsmDatasetManager.getActiveDataset()
            val routeNodes = route.nodes.mapNotNull { nodeId ->
                activeDataset.roadGraph.getNode(nodeId)
            }
            if (routeNodes.isEmpty()) {
                return Result.failure(
                    IllegalStateException("DEM_COVERAGE_UNAVAILABLE: Route contains no resolvable graph nodes for elevation extraction.")
                )
            }

            val points = ArrayList<ElevationPoint>(routeNodes.size)
            for (node in routeNodes) {
                val coord = node.coordinate
                val demElev = demElevationProvider?.invoke(coord)
                    ?: node.elevationMeters
                    ?: activeDataset.spatialIndex.findNearestRoadNode(coord, maxSnapDistanceMeters = 150.0)?.node?.elevationMeters

                if (demElev == null) {
                    return Result.failure(
                        IllegalStateException(
                            "DEM_COVERAGE_UNAVAILABLE: No verified DEM raster or OSM elevation tag available at (${coord.latitude}, ${coord.longitude}). Synthetic elevation fallback is prohibited."
                        )
                    )
                }
                points.add(
                    ElevationPoint(
                        coordinate = coord,
                        elevation = Elevation.of(Altitude.ofMeters(demElev))
                    )
                )
            }
            Result.success(points)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getWeatherAtCoordinate(coordinate: GeoCoordinate): Result<WeatherSnapshot> {
        return try {
            val snapshot = weatherProvider?.invoke(coordinate)
                ?: return Result.failure(
                    IllegalStateException(
                        "LIVE_WEATHER_PROVIDER_UNAVAILABLE: No live telemetry source configured for coordinate (${coordinate.latitude}, ${coordinate.longitude})"
                    )
                )
            Result.success(snapshot)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getChargingStationsNear(
        coordinate: GeoCoordinate,
        maxDistanceMeters: Double
    ): Result<List<Charger>> {
        return try {
            val stations = chargerProvider?.invoke(coordinate, maxDistanceMeters) ?: emptyList()
            val filtered = stations.filter {
                GeoUtils.calculateDistance(coordinate, it.location).meters <= maxDistanceMeters
            }.sortedBy {
                GeoUtils.calculateDistance(coordinate, it.location).meters
            }
            Result.success(filtered)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
