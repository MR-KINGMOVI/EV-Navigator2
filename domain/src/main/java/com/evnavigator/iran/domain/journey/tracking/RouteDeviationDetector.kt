package com.evnavigator.iran.domain.journey.tracking

import com.evnavigator.iran.domain.intelligence.replanning.DynamicReplanningCoordinator
import com.evnavigator.iran.domain.intelligence.replanning.ReplanningEvaluation
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.*

/**
 * Result of evaluating route deviation.
 */
sealed class DeviationAssessment {
    data class OnRoute(val distanceToRouteMeters: Double) : DeviationAssessment()
    data class PotentialDeviation(val distanceToRouteMeters: Double, val consecutiveCount: Int) : DeviationAssessment()
    data class ConfirmedDeviation(
        val distanceToRouteMeters: Double,
        val evaluation: ReplanningEvaluation
    ) : DeviationAssessment()
}

/**
 * Robust route corridor deviation detector with hysteresis and GPS noise suppression.
 */
class RouteDeviationDetector(
    private val replanningCoordinator: DynamicReplanningCoordinator,
    private val corridorWidthMeters: Double = 60.0,
    private val requiredConsecutiveDeviations: Int = 3
) {
    private var consecutiveOffRouteCount = 0

    /**
     * Evaluates current position against a polyline or sequence of route coordinates.
     */
    fun evaluatePosition(
        position: GeoCoordinate,
        routeCoordinates: List<GeoCoordinate>,
        gpsAccuracyMeters: Double = 10.0,
        currentEpochMs: Long = System.currentTimeMillis()
    ): DeviationAssessment {
        if (routeCoordinates.size < 2) {
            return DeviationAssessment.OnRoute(0.0)
        }

        // If GPS accuracy is unknown (NaN / <= 0) or too poor (> 50m), do not falsely trigger off-route
        if (gpsAccuracyMeters.isNaN() || !gpsAccuracyMeters.isFinite() || gpsAccuracyMeters <= 0.0 || gpsAccuracyMeters > 50.0) {
            return DeviationAssessment.OnRoute(0.0)
        }

        // Calculate minimum perpendicular or point-to-segment distance to route polyline
        var minDistanceMeters = Double.MAX_VALUE
        for (i in 0 until routeCoordinates.size - 1) {
            val d = distanceToSegmentMeters(position, routeCoordinates[i], routeCoordinates[i + 1])
            if (d < minDistanceMeters) {
                minDistanceMeters = d
            }
        }

        val effectiveCorridor = corridorWidthMeters + (gpsAccuracyMeters * 0.5)

        return if (minDistanceMeters <= effectiveCorridor) {
            consecutiveOffRouteCount = 0
            DeviationAssessment.OnRoute(minDistanceMeters)
        } else {
            consecutiveOffRouteCount++
            if (consecutiveOffRouteCount >= requiredConsecutiveDeviations) {
                val eval = replanningCoordinator.evaluateDeviation(minDistanceMeters, currentEpochMs)
                DeviationAssessment.ConfirmedDeviation(minDistanceMeters, eval)
            } else {
                DeviationAssessment.PotentialDeviation(minDistanceMeters, consecutiveOffRouteCount)
            }
        }
    }

    fun reset() {
        consecutiveOffRouteCount = 0
    }

    private fun distanceToSegmentMeters(p: GeoCoordinate, a: GeoCoordinate, b: GeoCoordinate): Double {
        val distA = calculateHaversineMeters(p, a)
        val distB = calculateHaversineMeters(p, b)
        val legDist = calculateHaversineMeters(a, b)

        if (legDist == 0.0) return distA

        val cosLat = cos(Math.toRadians((a.latitude + b.latitude) / 2.0)).coerceAtLeast(0.01)
        val dx = (b.longitude - a.longitude) * cosLat
        val dy = b.latitude - a.latitude
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-14) return distA

        val dot = (((p.longitude - a.longitude) * cosLat) * dx + (p.latitude - a.latitude) * dy) / lenSq

        return when {
            dot <= 0.0 -> distA
            dot >= 1.0 -> distB
            else -> {
                val projLat = a.latitude + dot * (b.latitude - a.latitude)
                val projLon = a.longitude + dot * (b.longitude - a.longitude)
                calculateHaversineMeters(p, GeoCoordinate(projLat, projLon))
            }
        }
    }

    private fun calculateHaversineMeters(c1: GeoCoordinate, c2: GeoCoordinate): Double {
        val r = 6371000.0
        val lat1Rad = Math.toRadians(c1.latitude)
        val lat2Rad = Math.toRadians(c2.latitude)
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
