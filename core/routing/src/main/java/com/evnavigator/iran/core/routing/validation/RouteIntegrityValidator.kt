package com.evnavigator.iran.core.routing.validation

import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteSegment
import com.evnavigator.iran.core.routing.model.RoutingError
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Validates routing inputs, decoded polyline geometries, origin/destination endpoint alignment,
 * segment contiguity, and summary-vs-geometry distance consistency (V13.1 Master Fix Specification — Sections 5 & 6).
 *
 * Non-negotiable V13.1 Invariants:
 * 1. Never injects `speedLimitKmh = 90` into fallback or unannotated [RealRoadRouteSegment]s;
 *    missing legal speed limit is represented as `0` (`legalSpeedLimitKmh = null`, `speedOrigin = "UNKNOWN"`),
 *    while operational cruise speed is kept separate (`estimatedCruiseSpeedKmh`).
 * 2. Validates that the first and last geometry points align with requested `origin` and `destination`
 *    within `maxEndpointSnapMeters` and rejects reversed geometries.
 */
object RouteIntegrityValidator {

    const val MIN_ORIGIN_DESTINATION_SEPARATION_METERS = 5.0
    const val DEFAULT_MAX_ENDPOINT_SNAP_METERS = 2_500.0
    const val MIN_GEOMETRY_TO_SUMMARY_RATIO = 0.45
    const val MAX_GEOMETRY_TO_SUMMARY_RATIO = 1.65

    fun validateWaypoints(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate> = emptyList()
    ): Result<Unit> {
        val allPoints = buildList {
            add("origin" to origin)
            waypoints.forEachIndexed { idx, wp -> add("waypoint[$idx]" to wp) }
            add("destination" to destination)
        }
        for ((label, coord) in allPoints) {
            if (!isValidCoordinate(coord)) {
                return Result.failure(
                    RoutingError.InvalidCoordinate(
                        coordinate = coord,
                        reason = "$label (${coord.latitude}, ${coord.longitude}) is outside valid WGS84 bounds [-90..90, -180..180]"
                    )
                )
            }
        }
        val separationMeters = haversineMeters(origin, destination)
        if (separationMeters < MIN_ORIGIN_DESTINATION_SEPARATION_METERS && waypoints.isEmpty()) {
            return Result.failure(
                RoutingError.InvalidCoordinate(
                    coordinate = destination,
                    reason = "Origin and destination are within ${"%.2f".format(separationMeters)}m (< ${MIN_ORIGIN_DESTINATION_SEPARATION_METERS}m minimum separation)"
                )
            )
        }
        return Result.success(Unit)
    }

    fun isValidCoordinate(coord: GeoCoordinate): Boolean =
        coord.latitude.isFinite() &&
            coord.longitude.isFinite() &&
            coord.latitude in -90.0..90.0 &&
            coord.longitude in -180.0..180.0 &&
            !(abs(coord.latitude) < 1e-6 && abs(coord.longitude) < 1e-6) // Reject Null Island (0,0)

    /**
     * Validates that a decoded route polyline is non-empty, contains at least 2 distinct points,
     * matches requested origin/destination endpoints when supplied, is not reversed,
     * and does not diverge from the reported summary distance.
     */
    fun validateDecodedRouteGeometry(
        providerName: String,
        polyline: List<GeoCoordinate>,
        reportedDistanceMeters: Double,
        reportedDurationSeconds: Double,
        requestedOrigin: GeoCoordinate? = null,
        requestedDestination: GeoCoordinate? = null,
        maxEndpointSnapMeters: Double = DEFAULT_MAX_ENDPOINT_SNAP_METERS
    ): Result<Double> {
        if (polyline.size < 2) {
            return Result.failure(
                RoutingError.RouteGeometryMissing(
                    providerName = providerName,
                    reason = "Decoded route polyline has ${polyline.size} points (minimum 2 required); straight-line fallback is forbidden."
                )
            )
        }
        for (coord in polyline) {
            if (!isValidCoordinate(coord)) {
                return Result.failure(
                    RoutingError.InvalidCoordinate(
                        coordinate = coord,
                        reason = "$providerName returned an invalid coordinate in route geometry: (${coord.latitude}, ${coord.longitude})"
                    )
                )
            }
        }
        if (!reportedDistanceMeters.isFinite() || reportedDistanceMeters <= 0.0) {
            return Result.failure(
                RoutingError.MalformedResponse(
                    providerName = providerName,
                    reason = "Reported route distance ($reportedDistanceMeters m) is non-positive or non-finite"
                )
            )
        }
        if (!reportedDurationSeconds.isFinite() || reportedDurationSeconds <= 0.0) {
            return Result.failure(
                RoutingError.MalformedResponse(
                    providerName = providerName,
                    reason = "Reported route duration ($reportedDurationSeconds s) is non-positive or non-finite"
                )
            )
        }

        if (requestedOrigin != null && requestedDestination != null) {
            val firstPt = polyline.first()
            val lastPt = polyline.last()
            val originSnap = haversineMeters(requestedOrigin, firstPt)
            val destSnap = haversineMeters(requestedDestination, lastPt)
            val revOriginSnap = haversineMeters(requestedOrigin, lastPt)
            val revDestSnap = haversineMeters(requestedDestination, firstPt)

            if (originSnap > maxEndpointSnapMeters &&
                destSnap > maxEndpointSnapMeters &&
                revOriginSnap <= maxEndpointSnapMeters &&
                revDestSnap <= maxEndpointSnapMeters
            ) {
                return Result.failure(
                    RoutingError.MalformedResponse(
                        providerName = providerName,
                        reason = "ROUTE_GEOMETRY_REVERSED: Route geometry start/end are reversed relative to requested origin and destination"
                    )
                )
            }
            if (originSnap > maxEndpointSnapMeters) {
                return Result.failure(
                    RoutingError.MalformedResponse(
                        providerName = providerName,
                        reason = "ORIGIN_ENDPOINT_MISMATCH: Route geometry starts ${originSnap.toInt()}m away from requested origin (max tolerance ${maxEndpointSnapMeters.toInt()}m)"
                    )
                )
            }
            if (destSnap > maxEndpointSnapMeters) {
                return Result.failure(
                    RoutingError.MalformedResponse(
                        providerName = providerName,
                        reason = "DESTINATION_ENDPOINT_MISMATCH: Route geometry ends ${destSnap.toInt()}m away from requested destination (max tolerance ${maxEndpointSnapMeters.toInt()}m)"
                    )
                )
            }
        }

        val polylineLengthMeters = computePolylineLengthMeters(polyline)
        if (polylineLengthMeters < 1.0) {
            return Result.failure(
                RoutingError.RouteGeometryMissing(
                    providerName = providerName,
                    reason = "Decoded route polyline has degenerate total length (${"%.2f".format(polylineLengthMeters)} m)"
                )
            )
        }

        // If the polyline only has 2 points for a long road route (> 2.5 km), check if it was a disguised straight line
        if (polyline.size == 2 && reportedDistanceMeters > 2_500.0) {
            val chordRatio = polylineLengthMeters / reportedDistanceMeters
            if (chordRatio < 0.85) {
                return Result.failure(
                    RoutingError.RouteGeometryMissing(
                        providerName = providerName,
                        reason = "2-point chord (${polylineLengthMeters.toInt()}m) does not represent road geometry for ${reportedDistanceMeters.toInt()}m route"
                    )
                )
            }
        }

        val distanceRatio = polylineLengthMeters / reportedDistanceMeters
        if (reportedDistanceMeters >= 500.0 && distanceRatio !in MIN_GEOMETRY_TO_SUMMARY_RATIO..MAX_GEOMETRY_TO_SUMMARY_RATIO) {
            return Result.failure(
                RoutingError.MalformedResponse(
                    providerName = providerName,
                    reason = "GEOMETRY_DISTANCE_MISMATCH: Polyline length (${polylineLengthMeters.toInt()}m) diverges from reported distance (${reportedDistanceMeters.toInt()}m), ratio=${"%.2f".format(distanceRatio)}"
                )
            )
        }

        return Result.success(polylineLengthMeters)
    }

    /**
     * Slices a full route polyline into per-step sub-polylines and builds contiguous [RealRoadRouteSegment]s
     * so each segment has non-empty geometry and monotonic cumulative distances (V13.1 Section 5 & Section 6).
     *
     * Never injects `speedLimitKmh = 90` when legal speed limit metadata is absent; unannotated segments use
     * `speedLimitKmh = 0` (`UNKNOWN` legal speed limit).
     */
    fun buildContiguousSegmentsFromManeuvers(
        providerPrefix: String,
        fullPolyline: List<GeoCoordinate>,
        maneuvers: List<RealRoadManeuver>,
        totalDistanceMeters: Double
    ): List<RealRoadRouteSegment> {
        if (fullPolyline.size < 2) return emptyList()

        val activeSteps = maneuvers.filter { it.distanceMeters > 0.5 }
        if (activeSteps.isEmpty()) {
            return listOf(
                RealRoadRouteSegment(
                    segmentIndex = 0,
                    edgeId = "${providerPrefix}_edge_0",
                    fromNodeId = "${providerPrefix}_node_0",
                    toNodeId = "${providerPrefix}_node_1",
                    startOffsetMeters = 0.0,
                    endOffsetMeters = totalDistanceMeters,
                    lengthMeters = totalDistanceMeters,
                    cumulativeStartMeters = 0.0,
                    cumulativeEndMeters = totalDistanceMeters,
                    speedLimitKmh = 0, // 0 = UNKNOWN legal road speed limit (never 90)
                    roadClass = "UNKNOWN",
                    roadName = "مسیر اصلی",
                    polyline = fullPolyline
                )
            )
        }

        val cumulativeVertexDist = DoubleArray(fullPolyline.size)
        for (i in 1 until fullPolyline.size) {
            cumulativeVertexDist[i] = cumulativeVertexDist[i - 1] + haversineMeters(fullPolyline[i - 1], fullPolyline[i])
        }
        val polyTotalLen = cumulativeVertexDist.last().coerceAtLeast(1.0)
        val rawStepSum = activeSteps.sumOf { it.distanceMeters }.coerceAtLeast(1.0)
        val stepScale = totalDistanceMeters / rawStepSum

        var cumulativeStart = 0.0
        return activeSteps.mapIndexed { idx, step ->
            val scaledLen = (step.distanceMeters * stepScale).coerceAtLeast(1.0)
            val cumulativeEnd = if (idx == activeSteps.lastIndex) {
                totalDistanceMeters
            } else {
                (cumulativeStart + scaledLen).coerceAtMost(totalDistanceMeters)
            }
            val startFrac = (cumulativeStart / totalDistanceMeters.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            val endFrac = (cumulativeEnd / totalDistanceMeters.coerceAtLeast(1.0)).coerceIn(startFrac, 1.0)
            val polyTargetStart = startFrac * polyTotalLen
            val polyTargetEnd = endFrac * polyTotalLen

            val startIdx = cumulativeVertexDist.indexOfFirst { it >= polyTargetStart }
                .let { if (it <= 0) 0 else it - 1 }
            val endIdx = cumulativeVertexDist.indexOfFirst { it >= polyTargetEnd }
                .let { if (it < 0) fullPolyline.lastIndex else it.coerceAtLeast(startIdx + 1).coerceAtMost(fullPolyline.lastIndex) }

            val subPoly = fullPolyline.subList(startIdx, endIdx + 1).ifEmpty {
                listOf(step.coordinate, fullPolyline.last())
            }
            val validStepSpeedLimit = step.speedLimitKmh.takeIf { it in 5..140 } ?: 0
            val seg = RealRoadRouteSegment(
                segmentIndex = idx,
                edgeId = step.edgeId ?: "${providerPrefix}_edge_$idx",
                fromNodeId = "${providerPrefix}_node_$idx",
                toNodeId = "${providerPrefix}_node_${idx + 1}",
                startOffsetMeters = 0.0,
                endOffsetMeters = cumulativeEnd - cumulativeStart,
                lengthMeters = (cumulativeEnd - cumulativeStart).coerceAtLeast(1.0),
                cumulativeStartMeters = cumulativeStart,
                cumulativeEndMeters = cumulativeEnd,
                speedLimitKmh = validStepSpeedLimit,
                roadClass = if (validStepSpeedLimit > 0) "PRIMARY" else "UNKNOWN",
                roadName = step.roadName,
                polyline = subPoly
            )
            cumulativeStart = cumulativeEnd
            seg
        }
    }

    fun computePolylineLengthMeters(polyline: List<GeoCoordinate>): Double {
        if (polyline.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until polyline.size - 1) {
            sum += haversineMeters(polyline[i], polyline[i + 1])
        }
        return sum
    }

    fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val h = (sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon).coerceIn(0.0, 1.0)
        return 2.0 * r * atan2(sqrt(h), sqrt(1.0 - h))
    }
}
