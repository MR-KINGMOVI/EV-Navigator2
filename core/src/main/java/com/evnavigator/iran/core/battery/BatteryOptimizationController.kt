package com.evnavigator.iran.core.battery

/**
 * Operating mode determined by vehicle velocity and battery power profile.
 */
enum class PowerProfileMode {
    STANDARD_NAVIGATION,    // 1000ms GPS, full offline pre-fetching
    APPROACHING_MANEUVER,   // 500ms GPS for high precision guidance
    STATIONARY_IDLE,        // 10,000ms GPS, suspended background workers
    LOW_BATTERY_CONSERVATIVE // 3000ms GPS, cancelled speculative prefetch
}

/**
 * Intelligent battery and CPU consumption controller for offline navigation.
 *
 * Saves device battery without sacrificing maneuver accuracy:
 * - Increases GPS sampling to 500ms when within 500m of a turn
 * - Throttles GPS sampling to 10s when vehicle is stopped at a traffic light or parked
 * - Defers background downloads/recomputations when device battery is critically low
 */
class BatteryOptimizationController {

    /**
     * Determines optimal location sampling interval based on vehicle movement,
     * maneuver proximity, and battery state.
     */
    fun computeOptimalProfile(
        speedKmh: Double,
        distanceToNextManeuverMeters: Double?,
        deviceBatteryPercent: Int,
        isPowerSaveModeActive: Boolean
    ): PowerProfileMode {
        // Critical safety priority: If approaching a turn within 500m, always provide high precision
        if (distanceToNextManeuverMeters != null && distanceToNextManeuverMeters <= 500.0 && speedKmh > 5.0) {
            return PowerProfileMode.APPROACHING_MANEUVER
        }

        // Vehicle is stationary (parked or long stop)
        if (speedKmh < 1.0) {
            return PowerProfileMode.STATIONARY_IDLE
        }

        // Device battery conservation
        if (isPowerSaveModeActive || deviceBatteryPercent <= 15) {
            return PowerProfileMode.LOW_BATTERY_CONSERVATIVE
        }

        return PowerProfileMode.STANDARD_NAVIGATION
    }

    fun getRecommendedGpsIntervalMs(mode: PowerProfileMode): Long {
        return when (mode) {
            PowerProfileMode.APPROACHING_MANEUVER -> 500L
            PowerProfileMode.STANDARD_NAVIGATION -> 1000L
            PowerProfileMode.LOW_BATTERY_CONSERVATIVE -> 3000L
            PowerProfileMode.STATIONARY_IDLE -> 10_000L
        }
    }

    fun shouldAllowSpeculativePrefetch(mode: PowerProfileMode): Boolean {
        return mode == PowerProfileMode.STANDARD_NAVIGATION || mode == PowerProfileMode.APPROACHING_MANEUVER
    }
}
