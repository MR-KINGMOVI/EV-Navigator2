package com.evnavigator.iran.core.battery

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BatteryOptimizationController.
 * Verifies adaptive GPS rate adjustment and power saving without compromising turn accuracy.
 */
class BatteryOptimizationTest {

    private lateinit var controller: BatteryOptimizationController

    @Before
    fun setUp() {
        controller = BatteryOptimizationController()
    }

    @Test
    fun `test stationary vehicle enters idle mode with 10s GPS polling`() {
        val mode = controller.computeOptimalProfile(
            speedKmh = 0.0,
            distanceToNextManeuverMeters = 2000.0,
            deviceBatteryPercent = 80,
            isPowerSaveModeActive = false
        )

        assertEquals(PowerProfileMode.STATIONARY_IDLE, mode)
        assertEquals(10_000L, controller.getRecommendedGpsIntervalMs(mode))
        assertFalse(controller.shouldAllowSpeculativePrefetch(mode))
    }

    @Test
    fun `test approaching turn within 500m triggers high precision 500ms GPS`() {
        val mode = controller.computeOptimalProfile(
            speedKmh = 45.0,
            distanceToNextManeuverMeters = 250.0, // 250m to turn
            deviceBatteryPercent = 30,
            isPowerSaveModeActive = false
        )

        assertEquals(PowerProfileMode.APPROACHING_MANEUVER, mode)
        assertEquals(500L, controller.getRecommendedGpsIntervalMs(mode))
        assertTrue(controller.shouldAllowSpeculativePrefetch(mode))
    }

    @Test
    fun `test low device battery triggers conservative mode`() {
        val mode = controller.computeOptimalProfile(
            speedKmh = 60.0,
            distanceToNextManeuverMeters = 5000.0,
            deviceBatteryPercent = 12, // Critically low battery
            isPowerSaveModeActive = true
        )

        assertEquals(PowerProfileMode.LOW_BATTERY_CONSERVATIVE, mode)
        assertEquals(3000L, controller.getRecommendedGpsIntervalMs(mode))
        assertFalse(controller.shouldAllowSpeculativePrefetch(mode))
    }
}
