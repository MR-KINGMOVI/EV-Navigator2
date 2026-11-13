package com.evnavigator.iran.domain.intelligence.replanning

import java.util.concurrent.atomic.AtomicLong

/**
 * Triggers that can warrant dynamic trip recalculation.
 */
enum class ReplanningTrigger {
    ROUTE_DEVIATION,
    MATERIAL_SOC_DEFICIT,
    CHARGING_STATION_UNAVAILABLE,
    MAJOR_TRAFFIC_INCIDENT,
    SEVERE_WEATHER_ALERT,
    OFFLINE_DATASET_FALLBACK,
    USER_PREFERENCE_CHANGED
}

/**
 * Outcome of evaluating whether dynamic trip replanning is justified.
 */
data class ReplanningEvaluation(
    val shouldReplan: Boolean,
    val trigger: ReplanningTrigger?,
    val explanation: String,
    val isSuppressedByHysteresis: Boolean = false
)

/**
 * Coordinates dynamic replanning decisions, applying hysteresis, threshold gating,
 * and concurrency protection to prevent navigation oscillation.
 */
class DynamicReplanningCoordinator(
    private val minReplanIntervalMs: Long = 20_000L, // 20s minimum between non-emergency replans
    private val minOffRouteDistanceMeters: Double = 50.0,
    private val minSocDeficitPercent: Double = 4.0, // 4% SOC below predicted
    private val minTrafficDelaySeconds: Double = 600.0 // 10 minutes delay
) {
    private var lastReplanTimestampEpochMs: Long = 0L
    private val replanSequenceCounter = AtomicLong(0L)
    private var currentActiveRouteId: String? = null

    fun setActiveRoute(routeId: String) {
        currentActiveRouteId = routeId
    }

    /**
     * Evaluates route deviation event.
     */
    fun evaluateDeviation(
        deviationDistanceMeters: Double,
        currentEpochMs: Long = System.currentTimeMillis()
    ): ReplanningEvaluation {
        if (deviationDistanceMeters < minOffRouteDistanceMeters) {
            return ReplanningEvaluation(
                shouldReplan = false,
                trigger = null,
                explanation = "Deviation ($deviationDistanceMeters m) is within GPS jitter tolerance (< $minOffRouteDistanceMeters m)."
            )
        }

        // Off-route is a high-priority event, but throttle if fired within 5 seconds to prevent spam
        if (currentEpochMs - lastReplanTimestampEpochMs < 5_000L) {
            return ReplanningEvaluation(
                shouldReplan = false,
                trigger = ReplanningTrigger.ROUTE_DEVIATION,
                explanation = "Rapid successive off-route event suppressed by short-term filter.",
                isSuppressedByHysteresis = true
            )
        }

        lastReplanTimestampEpochMs = currentEpochMs
        replanSequenceCounter.incrementAndGet()
        return ReplanningEvaluation(
            shouldReplan = true,
            trigger = ReplanningTrigger.ROUTE_DEVIATION,
            explanation = "Vehicle deviated by ${deviationDistanceMeters.toInt()} meters from active route. Rerouting to destination."
        )
    }

    /**
     * Evaluates battery consumption tracking vs expectation.
     */
    fun evaluateSocDeficit(
        actualSocPercent: Double,
        expectedSocPercent: Double,
        currentEpochMs: Long = System.currentTimeMillis()
    ): ReplanningEvaluation {
        val deficit = expectedSocPercent - actualSocPercent
        if (deficit < minSocDeficitPercent) {
            return ReplanningEvaluation(
                shouldReplan = false,
                trigger = null,
                explanation = "Battery consumption tracking within expected margin (deficit: ${String.format("%.1f", deficit)}%)."
            )
        }

        if (currentEpochMs - lastReplanTimestampEpochMs < minReplanIntervalMs) {
            return ReplanningEvaluation(
                shouldReplan = false,
                trigger = ReplanningTrigger.MATERIAL_SOC_DEFICIT,
                explanation = "SOC deficit detected, but replanning throttled to prevent oscillation.",
                isSuppressedByHysteresis = true
            )
        }

        lastReplanTimestampEpochMs = currentEpochMs
        replanSequenceCounter.incrementAndGet()
        return ReplanningEvaluation(
            shouldReplan = true,
            trigger = ReplanningTrigger.MATERIAL_SOC_DEFICIT,
            explanation = "Actual battery level is ${String.format("%.1f", deficit)}% below model expectation. Re-evaluating charging stops and range reserve."
        )
    }

    /**
     * Evaluates charging station operational status invalidation.
     */
    fun evaluateChargingStationStatus(
        isPlannedStationStillAvailable: Boolean,
        stationName: String,
        currentEpochMs: Long = System.currentTimeMillis()
    ): ReplanningEvaluation {
        if (isPlannedStationStillAvailable) {
            return ReplanningEvaluation(
                shouldReplan = false,
                trigger = null,
                explanation = "Planned station $stationName remains available."
            )
        }

        // Critical safety trigger - planned charger is down!
        lastReplanTimestampEpochMs = currentEpochMs
        replanSequenceCounter.incrementAndGet()
        return ReplanningEvaluation(
            shouldReplan = true,
            trigger = ReplanningTrigger.CHARGING_STATION_UNAVAILABLE,
            explanation = "Planned charging stop '$stationName' was reported unavailable. Immediate detour rerouting required."
        )
    }

    /**
     * Validates and applies a newly computed replacement route, preserving
     * the existing route if the replacement is invalid.
     */
    fun confirmRouteReplacement(newRouteId: String?, isReplacementValid: Boolean): Boolean {
        if (newRouteId != null && isReplacementValid) {
            currentActiveRouteId = newRouteId
            return true
        }
        // Preserve current active route on failure
        return false
    }

    fun getCurrentActiveRouteId(): String? = currentActiveRouteId
    fun getCurrentSequenceNumber(): Long = replanSequenceCounter.get()
}
