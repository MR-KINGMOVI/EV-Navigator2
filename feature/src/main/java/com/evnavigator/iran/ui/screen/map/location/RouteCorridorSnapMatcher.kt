package com.evnavigator.iran.ui.screen.map.location

import com.evnavigator.iran.core.routing.RealRoadRouteSegment
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Location
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V12.5 Extracted Collaborator: Continuity-aware and heading-aware snap-to-route projection.
 */
internal object RouteCorridorSnapMatcher {

    private const val EARTH_RADIUS_METERS = 6371000.0

    data class SnapMatchOutcome(
        val matchedFix: GpsLocationState.MapMatchedFix,
        val matchedPolylineIndex: Int
    )

    fun snapToRoute(
        loc: Location,
        polyline: List<GeoCoordinate>,
        segments: List<RealRoadRouteSegment>,
        bearing: Float,
        bearingConfidence: Float,
        currentSpeedKmh: Float,
        prevPolylineIndex: Int,
        snapCorridorMeters: Double,
        routeId: String?,
        routeGenerationId: Long
    ): SnapMatchOutcome? {
        if (polyline.size < 2) return null

        var bestScore = Double.MAX_VALUE
        var bestCrossTrackMeters = Double.MAX_VALUE
        var bestSnappedLat = loc.latitude
        var bestSnappedLng = loc.longitude
        var bestSegmentBearing = bearing
        var bestPolyIdx = 0
        var bestDistAlongPolySeg = 0.0
        var bestCumulativeDistMeters = 0.0

        var runningCumulativeMeters = 0.0

        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            val segLenMeters = haversineDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

            val (projLat, projLng, crossTrackDist) = projectPointToSegment(
                loc.latitude, loc.longitude,
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            )
            val segBearing = computeBearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

            val headingDiff = abs(((bearing - segBearing + 540f) % 360f) - 180f)
            val headingPenaltyMeters = if (bearingConfidence >= 0.5f && currentSpeedKmh >= 7.0f) {
                if (headingDiff > 120f) 40.0 else (headingDiff / 180.0) * 12.0
            } else 0.0

            val continuityPenaltyMeters = if (prevPolylineIndex >= 0) {
                val idxDelta = i - prevPolylineIndex
                when {
                    idxDelta in 0..3 -> 0.0
                    idxDelta == -1 -> 4.0
                    idxDelta < -1 -> 18.0
                    else -> min(20.0, (idxDelta - 3) * 2.5)
                }
            } else 0.0

            val candidateScore = crossTrackDist + headingPenaltyMeters + continuityPenaltyMeters
            if (candidateScore < bestScore) {
                bestScore = candidateScore
                bestCrossTrackMeters = crossTrackDist
                bestSnappedLat = projLat
                bestSnappedLng = projLng
                bestSegmentBearing = segBearing
                bestPolyIdx = i
                val distFromP1 = haversineDistanceMeters(p1.latitude, p1.longitude, projLat, projLng)
                    .coerceIn(0.0, segLenMeters)
                bestDistAlongPolySeg = distFromP1
                bestCumulativeDistMeters = runningCumulativeMeters + distFromP1
            }

            runningCumulativeMeters += segLenMeters
        }

        if (bestCrossTrackMeters <= snapCorridorMeters) {
            val snappedLoc = loc.copy(
                latitude = bestSnappedLat,
                longitude = bestSnappedLng
            )

            val matchedRoadSeg = if (segments.isNotEmpty()) {
                segments.firstOrNull { seg ->
                    bestCumulativeDistMeters >= seg.cumulativeStartMeters &&
                        bestCumulativeDistMeters <= seg.cumulativeEndMeters + 1.0
                } ?: segments.getOrNull(bestPolyIdx.coerceIn(0, segments.lastIndex))
            } else null

            val resolvedSegmentIndex = matchedRoadSeg?.segmentIndex ?: bestPolyIdx
            val resolvedDistAlongSegment = if (matchedRoadSeg != null) {
                (bestCumulativeDistMeters - matchedRoadSeg.cumulativeStartMeters)
                    .coerceIn(0.0, matchedRoadSeg.lengthMeters)
            } else {
                bestDistAlongPolySeg
            }

            val matchConfidence = (1.0 - (bestCrossTrackMeters / snapCorridorMeters) * 0.5)
                .toFloat()
                .coerceIn(0.3f, 1.0f)

            return SnapMatchOutcome(
                matchedFix = GpsLocationState.MapMatchedFix(
                    snappedLocation = snappedLoc,
                    rawLocation = loc,
                    crossTrackOffsetMeters = bestCrossTrackMeters,
                    speedKmh = currentSpeedKmh,
                    bearingDegrees = bestSegmentBearing,
                    matchedRoadName = matchedRoadSeg?.roadName?.takeIf { it.isNotBlank() } ?: "کریدور مسیریابی فعال",
                    speedLimitKmh = matchedRoadSeg?.speedLimitKmh?.takeIf { it > 0 },
                    isSnapped = true,
                    bearingConfidence = bearingConfidence,
                    segmentIndex = resolvedSegmentIndex,
                    distanceAlongSegmentMeters = resolvedDistAlongSegment,
                    distanceAlongRouteMeters = bestCumulativeDistMeters,
                    routeId = routeId,
                    routeGenerationId = routeGenerationId,
                    confidence = matchConfidence
                ),
                matchedPolylineIndex = bestPolyIdx
            )
        }

        return null
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    fun computeBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    fun projectPointToSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Triple<Double, Double, Double> {
        val cosLat = cos(Math.toRadians((aLat + bLat) / 2.0))
        val ax = aLng * cosLat * 111320.0
        val ay = aLat * 110540.0
        val bx = bLng * cosLat * 111320.0
        val by = bLat * 110540.0
        val px = pLng * cosLat * 111320.0
        val py = pLat * 110540.0

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy

        val t = if (lenSq > 0.0) {
            max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / lenSq))
        } else 0.0

        val projX = ax + t * dx
        val projY = ay + t * dy

        val dist = hypot(px - projX, py - projY)
        val projLat = projY / 110540.0
        val projLng = projX / (cosLat * 111320.0)

        return Triple(projLat, projLng, dist)
    }
}
