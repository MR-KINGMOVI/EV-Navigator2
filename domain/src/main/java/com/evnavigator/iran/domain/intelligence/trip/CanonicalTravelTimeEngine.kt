package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.units.Seconds
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Canonical Single Source of Truth (SSOT) engine for calculating travel time, charging duration,
 * station queue/waiting time, detour time, and ETA across the entire application (V13.3 Section 10).
 *
 * V13.3 Invariants:
 * 1. Multiplicative combination of traffic and weather speed reduction:
 *    `adjustedDrivingSeconds = baseDrivingSeconds * trafficMultiplier / (1.0 - weatherSpeedReductionFraction)`.
 *    Example: `base = 100 min`, `trafficMultiplier = 1.5`, `weatherSpeedReductionFraction = 0.20`
 *    -> `100 * 1.5 / 0.80 = 187.5 min` (`11,250 s`).
 * 2. Includes [waitingDuration] (station queue + plug-in handshake) as a first-class component.
 * 3. Rejects `totalDistanceMeters > 0.0 && baseDrivingDurationSeconds <= 0.0` instead of silently returning 0 ETA.
 * 4. Rejects empty `route.segments` when `route.distance.meters > 0.0` unless `allowRouteLevelDurationFallback = true`.
 * 5. Calibrates optimistic/pessimistic uncertainty bounds dynamically from data quality flags.
 */
object CanonicalTravelTimeEngine {

    data class SegmentTraversalInput(
        val segmentId: String,
        val distanceMeters: Double,
        val speedLimitKmh: Double?,
        val freeFlowSpeedKmh: Double?,
        val gradePercent: Double = 0.0,
        val trafficDelayMultiplier: Double = 1.0,
        val weatherSpeedReductionFraction: Double = 0.0,
        val maneuverPenaltySeconds: Double = 0.0
    )

    data class TravelTimeBreakdown(
        val baseDrivingDuration: Seconds,
        val weatherAndTrafficPenalty: Seconds,
        val detourDuration: Seconds,
        val chargingDuration: Seconds,
        val waitingDuration: Seconds,
        val totalDuration: Seconds,
        val optimisticTotalDuration: Seconds,
        val pessimisticTotalDuration: Seconds,
        val uncertaintyFraction: Double = 0.12
    ) {
        val totalMinutes: Double
            get() = totalDuration.toMinutes()

        val drivingMinutes: Double
            get() = (baseDrivingDuration + weatherAndTrafficPenalty).toMinutes()

        val chargingMinutes: Double
            get() = chargingDuration.toMinutes()

        val waitingMinutes: Double
            get() = waitingDuration.toMinutes()

        val detourMinutes: Double
            get() = detourDuration.toMinutes()

        fun computeArrivalEpochMillis(departureEpochMillis: Long): Long {
            return departureEpochMillis + (totalDuration.seconds * 1_000.0).roundToLong()
        }
    }

    private fun computeUncertaintyFraction(
        isRouteFromRealGraph: Boolean,
        isTrafficLive: Boolean,
        isWeatherLive: Boolean,
        areStationsVerified: Boolean,
        hasChargingStops: Boolean
    ): Double {
        var unc = 0.06
        if (!isRouteFromRealGraph) unc += 0.12
        if (!isTrafficLive) unc += 0.06
        if (!isWeatherLive) unc += 0.04
        if (hasChargingStops && !areStationsVerified) unc += 0.08
        return unc.coerceIn(0.05, 0.45)
    }

    /**
     * Computes segment-by-segment driving duration and combines with detour, charging, and waiting durations.
     */
    fun calculateFromSegments(
        segments: List<SegmentTraversalInput>,
        detourDurationSeconds: Double = 0.0,
        chargingDurationSeconds: Double = 0.0,
        waitingDurationSeconds: Double = 0.0,
        defaultHighwaySpeedKmh: Double = 85.0,
        isRouteFromRealGraph: Boolean = true,
        isTrafficLive: Boolean = false,
        isWeatherLive: Boolean = false,
        areStationsVerified: Boolean = true
    ): TravelTimeBreakdown {
        require(segments.isNotEmpty()) { "Segment list cannot be empty when calculating segment-based travel time" }
        var baseDrivingSec = 0.0
        var adjustedDrivingSec = 0.0

        for (seg in segments) {
            val distM = seg.distanceMeters.coerceAtLeast(0.0)
            if (distM <= 0.0) continue

            val rawSpeedKmh = seg.freeFlowSpeedKmh?.takeIf { it > 1.0 }
                ?: seg.speedLimitKmh?.takeIf { it > 1.0 }?.let { it * 0.92 }
                ?: defaultHighwaySpeedKmh.coerceIn(20.0, 120.0)

            val gradeFactor = when {
                seg.gradePercent > 6.0 -> 0.82
                seg.gradePercent > 3.0 -> 0.91
                seg.gradePercent < -6.0 -> 0.88
                else -> 1.0
            }

            val baseSpeedMps = max(2.0, (rawSpeedKmh * gradeFactor) / 3.6)
            val segBaseSec = (distM / baseSpeedMps) + seg.maneuverPenaltySeconds.coerceAtLeast(0.0)
            baseDrivingSec += segBaseSec

            val trafficMult = seg.trafficDelayMultiplier.takeIf { it.isFinite() }?.coerceIn(1.0, 3.5) ?: 1.0
            val weatherRed = seg.weatherSpeedReductionFraction.takeIf { it.isFinite() }?.coerceIn(0.0, 0.60) ?: 0.0
            val weatherDivisor = (1.0 - weatherRed).coerceAtLeast(0.25)

            // Multiplicative combination: traffic increases traversal time while weather reduces speed
            val segAdjustedSec = (distM / (baseSpeedMps * weatherDivisor)) * trafficMult +
                seg.maneuverPenaltySeconds.coerceAtLeast(0.0)
            adjustedDrivingSec += max(segBaseSec, segAdjustedSec)
        }

        val penaltySec = max(0.0, adjustedDrivingSec - baseDrivingSec)
        val safeDetourSec = detourDurationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val safeChargeSec = chargingDurationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val safeWaitSec = waitingDurationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0

        val totalNominalSec = baseDrivingSec + penaltySec + safeDetourSec + safeChargeSec + safeWaitSec
        val unc = computeUncertaintyFraction(
            isRouteFromRealGraph = isRouteFromRealGraph,
            isTrafficLive = isTrafficLive,
            isWeatherLive = isWeatherLive,
            areStationsVerified = areStationsVerified,
            hasChargingStops = safeChargeSec > 0.0
        )
        val optimisticSec = (baseDrivingSec * (1.0 - min(0.10, unc * 0.5))) +
            (penaltySec * 0.50) + (safeDetourSec * 0.90) + (safeChargeSec * 0.90) + (safeWaitSec * 0.80)
        val pessimisticSec = totalNominalSec * (1.0 + unc)

        return TravelTimeBreakdown(
            baseDrivingDuration = Seconds(baseDrivingSec),
            weatherAndTrafficPenalty = Seconds(penaltySec),
            detourDuration = Seconds(safeDetourSec),
            chargingDuration = Seconds(safeChargeSec),
            waitingDuration = Seconds(safeWaitSec),
            totalDuration = Seconds(totalNominalSec),
            optimisticTotalDuration = Seconds(min(totalNominalSec, optimisticSec)),
            pessimisticTotalDuration = Seconds(max(totalNominalSec, pessimisticSec)),
            uncertaintyFraction = unc
        )
    }

    /**
     * Computes travel time breakdown from route-level base driving duration, detour duration,
     * charging duration, and waiting duration.
     */
    fun calculateFromRouteDurations(
        baseDrivingDurationSeconds: Double,
        detourDurationSeconds: Double = 0.0,
        chargingDurationSeconds: Double = 0.0,
        waitingDurationSeconds: Double = 0.0,
        trafficMultiplier: Double = 1.0,
        weatherSpeedReductionFraction: Double = 0.0,
        totalDistanceMeters: Double = 0.0,
        isRouteFromRealGraph: Boolean = true,
        isTrafficLive: Boolean = false,
        isWeatherLive: Boolean = false,
        areStationsVerified: Boolean = true
    ): TravelTimeBreakdown {
        require(baseDrivingDurationSeconds.isFinite() && baseDrivingDurationSeconds >= 0.0) {
            "baseDrivingDurationSeconds ($baseDrivingDurationSeconds) must be finite and non-negative"
        }
        if (totalDistanceMeters > 0.0) {
            require(baseDrivingDurationSeconds > 0.0) {
                "baseDrivingDurationSeconds cannot be 0.0 when totalDistanceMeters ($totalDistanceMeters) > 0.0"
            }
        }
        val baseSec = baseDrivingDurationSeconds
        val safeTrafficMult = trafficMultiplier.takeIf { it.isFinite() }?.coerceIn(1.0, 3.0) ?: 1.0
        val safeWeatherReduction = weatherSpeedReductionFraction.takeIf { it.isFinite() }?.coerceIn(0.0, 0.50) ?: 0.0
        val weatherDivisor = (1.0 - safeWeatherReduction).coerceAtLeast(0.25)

        // Multiplicative combination: base * trafficMultiplier / (1 - weatherSpeedReductionFraction)
        val adjustedDrivingSec = baseSec * (safeTrafficMult / weatherDivisor)
        val penaltySec = max(0.0, adjustedDrivingSec - baseSec)
        val safeDetour = detourDurationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val safeCharge = chargingDurationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val safeWait = waitingDurationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0

        val totalSec = adjustedDrivingSec + safeDetour + safeCharge + safeWait
        val unc = computeUncertaintyFraction(
            isRouteFromRealGraph = isRouteFromRealGraph,
            isTrafficLive = isTrafficLive,
            isWeatherLive = isWeatherLive,
            areStationsVerified = areStationsVerified,
            hasChargingStops = safeCharge > 0.0
        )

        return TravelTimeBreakdown(
            baseDrivingDuration = Seconds(baseSec),
            weatherAndTrafficPenalty = Seconds(penaltySec),
            detourDuration = Seconds(safeDetour),
            chargingDuration = Seconds(safeCharge),
            waitingDuration = Seconds(safeWait),
            totalDuration = Seconds(totalSec),
            optimisticTotalDuration = Seconds(totalSec * (1.0 - min(0.10, unc * 0.5))),
            pessimisticTotalDuration = Seconds(totalSec * (1.0 + unc)),
            uncertaintyFraction = unc
        )
    }

    /**
     * Computes travel time directly from a domain [Route] instance and optional charging/detour/waiting overheads.
     */
    fun calculateFromDomainRoute(
        route: Route,
        detourDurationSeconds: Double = 0.0,
        chargingDurationSeconds: Double = 0.0,
        waitingDurationSeconds: Double = 0.0,
        trafficMultiplier: Double = 1.0,
        weatherSpeedReductionFraction: Double = 0.0,
        allowRouteLevelDurationFallback: Boolean = false
    ): TravelTimeBreakdown {
        val distM = route.distance.meters
        if (distM > 0.0 && route.segments.isEmpty() && !allowRouteLevelDurationFallback) {
            throw IllegalArgumentException("Route '${route.id}' has distance ${distM}m but empty segments; segment-based ETA requires non-empty segments.")
        }
        if (route.segments.isNotEmpty() && route.estimatedTime <= 0L) {
            val segInputs = route.segments.map { seg ->
                SegmentTraversalInput(
                    segmentId = seg.id,
                    distanceMeters = seg.distance.meters,
                    speedLimitKmh = seg.knownSpeedLimitKmhOrNull?.toDouble(),
                    freeFlowSpeedKmh = seg.estimatedAverageSpeedKmh,
                    gradePercent = if (seg.averageGrade.isFinite()) seg.averageGrade else 0.0,
                    trafficDelayMultiplier = trafficMultiplier,
                    weatherSpeedReductionFraction = weatherSpeedReductionFraction
                )
            }
            return calculateFromSegments(
                segments = segInputs,
                detourDurationSeconds = detourDurationSeconds,
                chargingDurationSeconds = chargingDurationSeconds,
                waitingDurationSeconds = waitingDurationSeconds
            )
        }
        val baseSec = route.estimatedTime.toDouble()
        return calculateFromRouteDurations(
            baseDrivingDurationSeconds = baseSec,
            detourDurationSeconds = detourDurationSeconds,
            chargingDurationSeconds = chargingDurationSeconds,
            waitingDurationSeconds = waitingDurationSeconds,
            trafficMultiplier = trafficMultiplier,
            weatherSpeedReductionFraction = weatherSpeedReductionFraction,
            totalDistanceMeters = distM
        )
    }
}
