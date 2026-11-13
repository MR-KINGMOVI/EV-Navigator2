package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.provider.CompositeRoutingProvider
import com.evnavigator.iran.core.routing.provider.EnrichedRouteResult
import com.evnavigator.iran.core.routing.provider.RoutingProvider
import com.evnavigator.iran.core.routing.provider.RoutingSourceType
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.model.*
import com.evnavigator.iran.domain.model.routing.RoutingEngine
import com.evnavigator.iran.domain.model.routing.RoutingEngineStatus
import com.evnavigator.iran.domain.model.routing.RoutingProfile
import com.evnavigator.iran.domain.model.routing.RoutingVehicleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Concrete implementation of the domain [RoutingEngine] interface (V13.3 Sections 5, 11 & 12).
 *
 * V13.3 Invariants:
 * 1. Computes authentic geographic bearing for each segment (`computeBearingDegrees`) instead of `Bearing(0.0)`.
 * 2. Preserves real segment `roadClass`, `speedLimitKmh` (`0` = `UNKNOWN` when unannotated, never `80`),
 *    and grade/elevation metadata from `RealRoadRouteSegment`.
 * 3. Marks `roadType` honestly (`"CORRIDOR_FALLBACK"`, `"ONLINE_ROAD"`, or `"UNKNOWN"` when absent) instead of
 *    hardcoding `"highway"`.
 * 4. Delegates energy consumption estimation to [CanonicalEnergyEngine.predictRouteEnergy].
 */
class DefaultDomainRoutingEngine(
    private val routingProvider: RoutingProvider = CompositeRoutingProvider()
) : RoutingEngine {

    override suspend fun calculateRoute(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vararg viaWaypoints: GeoCoordinate
    ): Route? {
        val allPoints = mutableListOf(start)
        allPoints.addAll(viaWaypoints)
        allPoints.add(destination)
        return calculateRoute(allPoints)
    }

    override suspend fun calculateRoute(waypoints: List<GeoCoordinate>): Route? {
        if (waypoints.size < 2) return null

        val allSegments = mutableListOf<RouteSegment>()
        val fullPolyline = mutableListOf<GeoCoordinate>()
        var totalDistMeters = 0.0
        var totalDurationSec = 0.0
        var cursorMeters = 0.0

        for (i in 0 until waypoints.size - 1) {
            val from = waypoints[i]
            val to = waypoints[i + 1]
            val legResult = routingProvider.calculateRoute(from, to)
            if (legResult.isFailure) return null

            val enriched = legResult.getOrThrow()
            val res = enriched.route
            totalDistMeters += res.totalDistanceMeters
            totalDurationSec += res.totalDurationSeconds

            for (pt in res.polyline) {
                val last = fullPolyline.lastOrNull()
                if (last == null || last.latitude != pt.latitude || last.longitude != pt.longitude) {
                    fullPolyline.add(pt)
                }
            }

            val mappedSegments = mapEnrichedResultToDomainSegments(enriched, legIndex = i, startOffsetBase = cursorMeters)
            allSegments.addAll(mappedSegments)
            cursorMeters += res.totalDistanceMeters
        }

        return Route(
            id = "route_${System.currentTimeMillis()}",
            start = waypoints.first(),
            end = waypoints.last(),
            segments = allSegments,
            distance = Distance.fromMeters(totalDistMeters),
            estimatedTime = totalDurationSec.toLong(),
            elevationProfile = emptyList(),
            waypoints = fullPolyline
        )
    }

    override suspend fun getAlternativeRoutes(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): List<Route> {
        val res = routingProvider.calculateAlternativeRoutes(start, destination)
        if (res.isFailure) return emptyList()

        val providerResults = res.getOrThrow()
        return providerResults.mapIndexed { idx, enriched ->
            val r = enriched.route
            val segments = mapEnrichedResultToDomainSegments(enriched, legIndex = idx, startOffsetBase = 0.0)
            Route(
                id = r.routeId.ifBlank { "alt_${System.currentTimeMillis()}_${r.totalDistanceMeters.toInt()}" },
                start = start,
                end = destination,
                segments = segments,
                distance = Distance.fromMeters(r.totalDistanceMeters),
                estimatedTime = r.totalDurationSeconds.toLong(),
                elevationProfile = emptyList(),
                waypoints = r.polyline
            )
        }
    }

    private fun mapEnrichedResultToDomainSegments(
        enriched: EnrichedRouteResult,
        legIndex: Int,
        startOffsetBase: Double
    ): List<RouteSegment> {
        val res = enriched.route
        val sourceType = enriched.provenance.sourceType
        val fallbackRoadType = when (sourceType) {
            RoutingSourceType.EMBEDDED_TERRAIN -> "CORRIDOR_FALLBACK"
            RoutingSourceType.ONLINE_PRIMARY -> "ONLINE_ROAD"
            RoutingSourceType.OFFLINE_PRIMARY -> "UNKNOWN"
            RoutingSourceType.SYNTHETIC_FALLBACK -> "UNKNOWN"
        }

        if (res.segments.isNotEmpty()) {
            return res.segments.mapIndexed { sIdx, seg ->
                val p1 = seg.polyline.firstOrNull() ?: res.polyline.first()
                val p2 = seg.polyline.lastOrNull() ?: res.polyline.last()
                val segLen = seg.lengthMeters.coerceAtLeast(1.0)
                val segStart = startOffsetBase + seg.cumulativeStartMeters
                val segEnd = startOffsetBase + seg.cumulativeEndMeters
                val knownLimit = seg.speedLimitKmh.takeIf { it in 5..140 } ?: 0
                val roadClassType = if (sourceType == RoutingSourceType.EMBEDDED_TERRAIN) {
                    "CORRIDOR_FALLBACK"
                } else {
                    seg.roadClass.takeIf { it.isNotBlank() && !it.equals("UNKNOWN", ignoreCase = true) } ?: fallbackRoadType
                }
                RouteSegment(
                    id = "seg_${legIndex}_$sIdx",
                    start = p1,
                    end = p2,
                    distance = Distance.fromMeters(segLen),
                    bearing = Bearing(computeBearingDegrees(p1, p2)),
                    roadName = seg.roadName.takeIf { it.isNotBlank() },
                    roadType = roadClassType,
                    edgeId = seg.edgeId,
                    direction = seg.direction.name,
                    startOffsetMeters = segStart,
                    endOffsetMeters = segEnd,
                    speedLimitKmh = knownLimit,
                    gradePercent = seg.gradePercent,
                    isGradeUnknown = seg.isGradeUnknown
                )
            }
        }

        val poly = res.polyline
        if (poly.size < 2) return emptyList()
        val avgCruiseKmh = if (res.totalDurationSeconds > 1.0 && res.totalDistanceMeters > 0.0) {
            ((res.totalDistanceMeters / res.totalDurationSeconds) * 3.6).coerceIn(15.0, 125.0)
        } else null

        var runningOffset = startOffsetBase
        return poly.zipWithNext().mapIndexed { j, (p1, p2) ->
            val segDist = haversineM(p1, p2).coerceAtLeast(1.0)
            val segStart = runningOffset
            runningOffset += segDist
            RouteSegment(
                id = "seg_${legIndex}_$j",
                start = p1,
                end = p2,
                distance = Distance.fromMeters(segDist),
                bearing = Bearing(computeBearingDegrees(p1, p2)),
                roadName = res.routeSummary.takeIf { it.isNotBlank() },
                roadType = fallbackRoadType,
                startOffsetMeters = segStart,
                endOffsetMeters = runningOffset,
                speedLimitKmh = 0,
                estimatedAverageSpeedKmh = avgCruiseKmh
            )
        }
    }

    override suspend fun getEstimatedTravelTime(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Long? {
        val res = routingProvider.calculateRoute(start, destination)
        return if (res.isSuccess) res.getOrThrow().totalDurationSeconds.toLong() else null
    }

    override suspend fun getDistance(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Double? {
        val res = routingProvider.calculateRoute(start, destination)
        return if (res.isSuccess) res.getOrThrow().totalDistanceMeters else null
    }

    override suspend fun getElevationProfile(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): ElevationProfile? = null

    override suspend fun getEnergyConsumption(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Double? {
        val legResult = routingProvider.calculateRoute(start, destination)
        if (legResult.isFailure) return null
        val route = legResult.getOrThrow().route
        if (route.totalDistanceMeters <= 0.0) return null
        val avgSpeedKmh = if (route.totalDurationSeconds > 1.0) {
            ((route.totalDistanceMeters / route.totalDurationSeconds) * 3.6).coerceIn(25.0, 120.0)
        } else {
            80.0
        }
        val prediction = CanonicalEnergyEngine.predictRouteEnergy(
            route = RouteProfileInput(
                segments = listOf(
                    RouteSegmentInput(
                        distanceMeters = route.totalDistanceMeters,
                        speedLimitKmh = null,
                        targetSpeedKmh = avgSpeedKmh
                    )
                )
            ),
            vehicle = VehiclePhysicsParams.assumedReference(),
            conditions = EnvironmentalConditions(
                targetSpeedKmh = avgSpeedKmh,
                isDepartureSocVerified = false
            )
        )
        return prediction.breakdown.netBatteryEnergyWh / 1000.0
    }

    override suspend fun getRoutingProfile(coordinate: GeoCoordinate): RoutingProfile = createDefaultProfile()

    override suspend fun isValidRoute(start: GeoCoordinate, destination: GeoCoordinate): Boolean {
        if (start.latitude.isNaN() || start.longitude.isNaN() || destination.latitude.isNaN() || destination.longitude.isNaN()) {
            return false
        }
        return routingProvider.calculateRoute(start, destination).isSuccess
    }

    override fun getAvailableRoutingProfiles(): Flow<List<RoutingProfile>> = flowOf(listOf(createDefaultProfile()))

    override suspend fun isOfflineRoutingAvailable(): Boolean = true

    override fun getEngineStatus(): RoutingEngineStatus = RoutingEngineStatus.AVAILABLE

    override suspend fun refreshCache() {}

    private fun createDefaultProfile(): RoutingProfile = RoutingProfile(
        id = "profile_default",
        name = "Standard EV",
        description = "Standard electric vehicle routing profile",
        vehicleType = RoutingVehicleType.ELECTRIC_CAR
    )

    internal fun computeBearingDegrees(from: GeoCoordinate, to: GeoCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).takeIf { it.isFinite() } ?: 0.0
    }

    private fun haversineM(c1: GeoCoordinate, c2: GeoCoordinate): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(c1.latitude)) * cos(Math.toRadians(c2.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
