package com.evnavigator.iran.domain.journey.tracking

import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.MultiStopJourneyPlan
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.*

/**
 * Validated Map-Matched longitudinal progress input from [AutomotiveGpsEngine].
 */
data class GpsMatchedProgressInput(
    val segmentIndex: Int,
    val distanceAlongSegmentMeters: Double,
    val distanceAlongRouteMeters: Double,
    val crossTrackOffsetMeters: Double,
    val routeGenerationId: Long,
    val confidence: Float = 1.0f,
    val segmentLengthMeters: Double = 0.0,
    val routeId: String? = null,
    val fixTimestampEpochMs: Long? = null
)

/**
 * Real-time progress update along a multi-stop journey.
 */
data class JourneyProgress(
    val currentPosition: GeoCoordinate,
    val currentWaypointIndex: Int,
    val nextWaypoint: JourneyWaypoint?,
    val distanceToNextWaypointMeters: Double,
    val nextChargingStop: JourneyWaypoint?,
    val distanceToNextChargingStopMeters: Double?,
    val remainingDistanceToDestinationMeters: Double,
    val estimatedRemainingSeconds: Double,
    val fractionCompleted: Double, // 0.0 to 1.0
    val currentSpeedKmh: Double,
    val gpsAccuracyMeters: Double,
    val isGpsStale: Boolean,
    val timestampEpochMs: Long,
    val isAccuracyKnown: Boolean = !gpsAccuracyMeters.isNaN() && gpsAccuracyMeters.isFinite() && gpsAccuracyMeters > 0.0,
    val isLowConfidence: Boolean = !isAccuracyKnown || gpsAccuracyMeters > 50.0,
    val confidenceScore: Double = if (!gpsAccuracyMeters.isNaN() && gpsAccuracyMeters.isFinite() && gpsAccuracyMeters > 0.0) {
        (1.0 - (gpsAccuracyMeters / 100.0)).coerceIn(0.20, 0.98)
    } else 0.30,
    val segmentProgressFraction: Double = fractionCompleted,
    val routeProgressFraction: Double = fractionCompleted,
    val matchedRouteGenerationId: Long? = null,
    val isMatchedFixAccepted: Boolean = false,
    val distanceSource: String = if (isMatchedFixAccepted) "MAP_MATCHED_ROUTE_GRAPH" else "ESTIMATED_FALLBACK",
    val isFallbackDistanceEstimate: Boolean = !isMatchedFixAccepted,
    val isEtaEstimated: Boolean = true,
    val etaCalculationSource: String = "ROUTE_PLAN_AVERAGE_SPEED"
) {
    val remainingDurationToDestinationSeconds: Double
        get() = estimatedRemainingSeconds
}

/**
 * Tracks and computes live journey progress along a validated MultiStopJourneyPlan.
 *
 * Enforces:
 * - Timestamp freshness check BEFORE accepting any MapMatchedFix or advancing waypoints.
 * - Strict routeGenerationId (> 0L and equal to activeRouteGenerationId), segmentIndex,
 *   distance bounds, crossTrackOffset corridor, and confidence validation.
 * - Longitudinal route-graph waypoint progression and remaining leg distance summation
 *   (no Haversine magic multiplier).
 * - Authentic speed/ETA calculation without fabricating a 90 km/h vehicle speed when stopped.
 */
class JourneyProgressTracker(
    private val stalenessThresholdMs: Long = 10_000L,
    private val maxCrossTrackOffsetMeters: Double = 50.0,
    private val minMatchedConfidence: Float = 0.25f
) {

    companion object {
        private const val MAX_FUTURE_CLOCK_SKEW_MS = 60_000L
        private const val WAYPOINT_ARRIVAL_RADIUS_METERS = 150.0
    }

    /**
     * Updates and computes progress given the latest location observation.
     * Never substitutes a fake accuracy (such as 10m or 50m) when [accuracyMeters] is NaN or invalid,
     * and never invents a 90 km/h speed when the vehicle is stationary or moving slowly.
     */
    fun computeProgress(
        plan: MultiStopJourneyPlan,
        location: GeoCoordinate,
        speedKmh: Double = 0.0,
        accuracyMeters: Double = Double.NaN,
        timestampEpochMs: Long = System.currentTimeMillis(),
        matchedFix: GpsMatchedProgressInput? = null,
        activeRouteGenerationId: Long = 0L,
        previousTargetWaypointIndex: Int = 1,
        nowEpochMs: Long = System.currentTimeMillis()
    ): JourneyProgress {
        val isAccuracyValid = !accuracyMeters.isNaN() && accuracyMeters.isFinite() && accuracyMeters > 0.0
        val effectiveGpsAccuracy = if (isAccuracyValid) accuracyMeters else Double.NaN

        // Section 13: Evaluate timestamp freshness FIRST before accepting matchedFix or advancing waypoints
        val ageMs = nowEpochMs - timestampEpochMs
        val isInvalidOrFutureTimestamp = timestampEpochMs <= 0L || ageMs < -MAX_FUTURE_CLOCK_SKEW_MS
        val isStale = isInvalidOrFutureTimestamp || ageMs > stalenessThresholdMs
        val isHighConfidenceFix = !isStale && isAccuracyValid && accuracyMeters <= 50.0

        val waypoints = plan.waypoints
        if (waypoints.isEmpty()) {
            return JourneyProgress(
                currentPosition = location,
                currentWaypointIndex = 0,
                nextWaypoint = null,
                distanceToNextWaypointMeters = 0.0,
                nextChargingStop = null,
                distanceToNextChargingStopMeters = null,
                remainingDistanceToDestinationMeters = 0.0,
                estimatedRemainingSeconds = 0.0,
                fractionCompleted = 1.0,
                currentSpeedKmh = speedKmh,
                gpsAccuracyMeters = effectiveGpsAccuracy,
                isGpsStale = isStale,
                timestampEpochMs = timestampEpochMs,
                isAccuracyKnown = isAccuracyValid,
                isLowConfidence = !isHighConfidenceFix,
                segmentProgressFraction = 1.0,
                routeProgressFraction = 1.0,
                distanceSource = "EMPTY_PLAN",
                isFallbackDistanceEstimate = false,
                isEtaEstimated = false,
                etaCalculationSource = "COMPLETED"
            )
        }

        // Precompute cumulative longitudinal distances to each waypoint from plan.legs
        val hasCompleteLegs = plan.legs.size == waypoints.size - 1 &&
            plan.legs.all { it.distanceMeters.isFinite() && it.distanceMeters >= 0.0 }
        val cumulativeWaypointDist = DoubleArray(waypoints.size)
        if (hasCompleteLegs) {
            var acc = 0.0
            for (i in plan.legs.indices) {
                acc += plan.legs[i].distanceMeters
                cumulativeWaypointDist[i + 1] = acc
            }
        } else {
            var acc = 0.0
            for (i in 0 until waypoints.size - 1) {
                val legDist = plan.legs.getOrNull(i)?.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
                    ?: calculateHaversineDistanceMeters(waypoints[i].coordinate, waypoints[i + 1].coordinate)
                acc += legDist
                cumulativeWaypointDist[i + 1] = acc
            }
        }
        val totalRouteDist = if (plan.totalDistanceMeters.isFinite() && plan.totalDistanceMeters > 0.0) {
            plan.totalDistanceMeters
        } else {
            cumulativeWaypointDist.lastOrNull()?.coerceAtLeast(1.0) ?: 1.0
        }

        // Section 14 & 20: Strict validation of MapMatchedFix (never allow 0L wildcard or stale fix)
        val acceptedMatchedFix = if (isStale) {
            null
        } else {
            matchedFix?.takeIf { fix ->
                val isFixTimestampFresh = fix.fixTimestampEpochMs == null || (
                    fix.fixTimestampEpochMs > 0L &&
                        (nowEpochMs - fix.fixTimestampEpochMs) in -MAX_FUTURE_CLOCK_SKEW_MS..stalenessThresholdMs
                    )
                val isGenerationValid = activeRouteGenerationId > 0L &&
                    fix.routeGenerationId > 0L &&
                    fix.routeGenerationId == activeRouteGenerationId
                val isRouteIdValid = fix.routeId == null ||
                    fix.routeId == plan.routeId ||
                    fix.routeId == plan.planId
                val isSegmentIndexValid = fix.segmentIndex >= 0
                val isSegDistValid = fix.distanceAlongSegmentMeters.isFinite() &&
                    fix.distanceAlongSegmentMeters >= 0.0 &&
                    (fix.segmentLengthMeters <= 0.0 || fix.distanceAlongSegmentMeters <= fix.segmentLengthMeters + 5.0)
                val isRouteDistValid = fix.distanceAlongRouteMeters.isFinite() &&
                    fix.distanceAlongRouteMeters >= 0.0 &&
                    fix.distanceAlongRouteMeters <= totalRouteDist + 50.0
                val isCrossTrackValid = fix.crossTrackOffsetMeters.isFinite() &&
                    abs(fix.crossTrackOffsetMeters) <= maxCrossTrackOffsetMeters
                val isConfidenceValid = fix.confidence.isFinite() &&
                    fix.confidence in minMatchedConfidence..1.0f

                isFixTimestampFresh &&
                    isGenerationValid &&
                    isRouteIdValid &&
                    isSegmentIndexValid &&
                    isSegDistValid &&
                    isRouteDistValid &&
                    isCrossTrackValid &&
                    isConfidenceValid
            }
        }

        // Section 15: Sequential longitudinal waypoint progression
        val clampedPrevTarget = previousTargetWaypointIndex.coerceIn(1, (waypoints.size - 1).coerceAtLeast(1))
        var targetIndex = clampedPrevTarget

        if (acceptedMatchedFix != null) {
            val alongRoute = acceptedMatchedFix.distanceAlongRouteMeters
            for (i in clampedPrevTarget until waypoints.size - 1) {
                if (alongRoute >= cumulativeWaypointDist[i] - WAYPOINT_ARRIVAL_RADIUS_METERS) {
                    targetIndex = i + 1
                } else {
                    break
                }
            }
        } else if (isHighConfidenceFix) {
            // In fallback mode without map-matching, only allow advancing the immediate current target waypoint
            // (never skip intermediate waypoints out of order)
            val distToCurrentTarget = calculateHaversineDistanceMeters(location, waypoints[targetIndex].coordinate)
            if (distToCurrentTarget <= WAYPOINT_ARRIVAL_RADIUS_METERS && targetIndex < waypoints.size - 1) {
                targetIndex += 1
            }
        }

        val nextWp = waypoints.getOrNull(targetIndex) ?: waypoints.last()

        // Section 16: Remaining distances from authentic route geometry / leg distances
        val distToNextWp: Double
        val remainingDist: Double
        val distanceSource: String

        if (acceptedMatchedFix != null) {
            val alongRoute = acceptedMatchedFix.distanceAlongRouteMeters.coerceIn(0.0, totalRouteDist)
            distToNextWp = (cumulativeWaypointDist[targetIndex] - alongRoute).coerceAtLeast(0.0)
            remainingDist = (totalRouteDist - alongRoute).coerceIn(0.0, totalRouteDist)
            distanceSource = "MAP_MATCHED_ROUTE_GRAPH"
        } else {
            val prevWpCoord = waypoints[(targetIndex - 1).coerceAtLeast(0)].coordinate
            val nextWpCoord = nextWp.coordinate
            val straightCurrentToNext = calculateHaversineDistanceMeters(location, nextWpCoord)
            val straightPrevToNext = calculateHaversineDistanceMeters(prevWpCoord, nextWpCoord)
            val activeLegRoadDist = plan.legs.getOrNull(targetIndex - 1)?.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }

            distToNextWp = if (activeLegRoadDist != null && straightPrevToNext > 1.0) {
                val fractionRemainingInLeg = (straightCurrentToNext / straightPrevToNext).coerceIn(0.0, 1.0)
                activeLegRoadDist * fractionRemainingInLeg
            } else {
                straightCurrentToNext
            }

            var subsequentLegsDist = 0.0
            for (i in targetIndex until waypoints.size - 1) {
                val legRoadDist = plan.legs.getOrNull(i)?.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
                    ?: calculateHaversineDistanceMeters(waypoints[i].coordinate, waypoints[i + 1].coordinate)
                subsequentLegsDist += legRoadDist
            }
            remainingDist = (distToNextWp + subsequentLegsDist).coerceIn(0.0, totalRouteDist)
            distanceSource = "ESTIMATED_FALLBACK"
        }

        // Next charging stop distance using longitudinal route distance or authentic leg summation
        val nextChargingIndex = (targetIndex until waypoints.size).firstOrNull { waypoints[it].isChargingStop }
        val nextChargingWp = nextChargingIndex?.let { waypoints[it] }
        val distToCharging: Double? = if (nextChargingIndex != null && nextChargingWp != null) {
            if (acceptedMatchedFix != null) {
                val stationRouteDist = nextChargingWp.stationDetails?.distanceAlongRouteMeters
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?: cumulativeWaypointDist[nextChargingIndex]
                (stationRouteDist - acceptedMatchedFix.distanceAlongRouteMeters).coerceAtLeast(0.0)
            } else {
                var sumToCharger = distToNextWp
                for (i in targetIndex until nextChargingIndex) {
                    val legRoadDist = plan.legs.getOrNull(i)?.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
                        ?: calculateHaversineDistanceMeters(waypoints[i].coordinate, waypoints[i + 1].coordinate)
                    sumToCharger += legRoadDist
                }
                sumToCharger.coerceAtLeast(0.0)
            }
        } else {
            null
        }

        val routeFraction = (1.0 - (remainingDist / totalRouteDist.coerceAtLeast(1.0))).coerceIn(0.0, 1.0)
        val segFraction = if (acceptedMatchedFix != null && acceptedMatchedFix.segmentLengthMeters > 0.0) {
            (acceptedMatchedFix.distanceAlongSegmentMeters / acceptedMatchedFix.segmentLengthMeters).coerceIn(0.0, 1.0)
        } else {
            routeFraction
        }

        // Section 12: Authentic speed / ETA calculation — never fabricate 90 km/h speed when stationary
        val isLiveSpeedUsable = !isStale && speedKmh.isFinite() && speedKmh in 3.6..180.0
        val planAverageSpeedMps = if (
            plan.totalDrivingDurationSeconds.isFinite() &&
            plan.totalDrivingDurationSeconds > 1.0 &&
            totalRouteDist > 0.0
        ) {
            (totalRouteDist / plan.totalDrivingDurationSeconds).coerceIn(3.0, 38.89)
        } else {
            Double.NaN
        }

        val effectiveSpeedMps: Double
        val isEtaEstimated: Boolean
        val etaSource: String

        when {
            isLiveSpeedUsable -> {
                effectiveSpeedMps = speedKmh / 3.6
                isEtaEstimated = acceptedMatchedFix == null
                etaSource = "LIVE_TELEMETRY_SPEED"
            }
            planAverageSpeedMps.isFinite() && planAverageSpeedMps > 0.0 -> {
                effectiveSpeedMps = planAverageSpeedMps
                isEtaEstimated = true
                etaSource = "ROUTE_PLAN_AVERAGE_SPEED"
            }
            else -> {
                effectiveSpeedMps = Double.NaN
                isEtaEstimated = true
                etaSource = "UNAVAILABLE"
            }
        }

        val remainingSeconds = when {
            remainingDist <= 0.0 -> 0.0
            effectiveSpeedMps.isFinite() && effectiveSpeedMps > 0.0 -> remainingDist / effectiveSpeedMps
            else -> Double.NaN
        }

        val baseConf = if (isAccuracyValid) {
            (1.0 - (accuracyMeters / 100.0)).coerceIn(0.20, 0.98)
        } else {
            0.30
        }
        val finalConf = when {
            isStale -> 0.20
            acceptedMatchedFix != null -> ((baseConf * 0.6) + (acceptedMatchedFix.confidence.toDouble() * 0.4)).coerceIn(0.20, 0.99)
            else -> (baseConf * 0.75).coerceIn(0.20, 0.85)
        }

        return JourneyProgress(
            currentPosition = location,
            currentWaypointIndex = targetIndex - 1,
            nextWaypoint = nextWp,
            distanceToNextWaypointMeters = distToNextWp,
            nextChargingStop = nextChargingWp,
            distanceToNextChargingStopMeters = distToCharging,
            remainingDistanceToDestinationMeters = remainingDist,
            estimatedRemainingSeconds = remainingSeconds,
            fractionCompleted = routeFraction,
            currentSpeedKmh = speedKmh,
            gpsAccuracyMeters = effectiveGpsAccuracy,
            isGpsStale = isStale,
            timestampEpochMs = timestampEpochMs,
            isAccuracyKnown = isAccuracyValid,
            isLowConfidence = !isHighConfidenceFix || isStale || acceptedMatchedFix == null,
            confidenceScore = finalConf,
            segmentProgressFraction = segFraction,
            routeProgressFraction = routeFraction,
            matchedRouteGenerationId = acceptedMatchedFix?.routeGenerationId,
            isMatchedFixAccepted = acceptedMatchedFix != null,
            distanceSource = distanceSource,
            isFallbackDistanceEstimate = acceptedMatchedFix == null,
            isEtaEstimated = isEtaEstimated,
            etaCalculationSource = etaSource
        )
    }

    private fun calculateHaversineDistanceMeters(c1: GeoCoordinate, c2: GeoCoordinate): Double {
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
