package com.evnavigator.iran.domain.intelligence.personalization

import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import org.junit.Assert.*
import org.junit.Test

class PersonalizationManagerTest {

    private val manager = PersonalizationManager()

    @Test
    fun testPersonalizationDisabledByDefault() {
        manager.recordTripTelemetry("car_1", 95.0, 22.0, DrivingMode.NORMAL, 20_000.0, 3_500.0)
        val profile = manager.getProfile("car_1")
        assertFalse("Personalization should be strictly disabled by default", profile.isEnabled)
        assertEquals(0, profile.buckets.size)

        val multiplier = manager.getEfficiencyMultiplier("car_1", 95.0, 22.0, DrivingMode.NORMAL, 150.0)
        assertEquals(1.0, multiplier, 0.001)
    }

    @Test
    fun testEnabledPersonalizationRequiresMinimumSamples() {
        manager.setPersonalizationEnabled("car_1", true)
        assertTrue(manager.getProfile("car_1").isEnabled)

        // 1st sample
        manager.recordTripTelemetry("car_1", 95.0, 22.0, DrivingMode.NORMAL, 20_000.0, 3_600.0)
        assertEquals(1.0, manager.getEfficiencyMultiplier("car_1", 95.0, 22.0, DrivingMode.NORMAL, 150.0), 0.001)

        // 2nd sample
        manager.recordTripTelemetry("car_1", 95.0, 22.0, DrivingMode.NORMAL, 20_000.0, 3_600.0)
        assertEquals(1.0, manager.getEfficiencyMultiplier("car_1", 95.0, 22.0, DrivingMode.NORMAL, 150.0), 0.001)

        // 3rd sample: Total 60km, 10,800 Wh -> 180 Wh/km vs baseline 150 Wh/km -> 1.20 ratio
        manager.recordTripTelemetry("car_1", 95.0, 22.0, DrivingMode.NORMAL, 20_000.0, 3_600.0)
        val mult = manager.getEfficiencyMultiplier("car_1", 95.0, 22.0, DrivingMode.NORMAL, 150.0)
        assertEquals(1.20, mult, 0.01)
    }

    @Test
    fun testVehicleIsolation() {
        manager.setPersonalizationEnabled("car_1", true)
        manager.setPersonalizationEnabled("car_2", true)

        // Record for car 1 (driving fast, higher consumption: 200 Wh/km)
        repeat(3) {
            manager.recordTripTelemetry("car_1", 100.0, 20.0, DrivingMode.NORMAL, 10_000.0, 2_000.0)
        }

        val multCar1 = manager.getEfficiencyMultiplier("car_1", 100.0, 20.0, DrivingMode.NORMAL, 150.0)
        val multCar2 = manager.getEfficiencyMultiplier("car_2", 100.0, 20.0, DrivingMode.NORMAL, 150.0)

        assertTrue("Car 1 should have calibrated higher consumption factor", multCar1 > 1.1)
        assertEquals("Car 2 with no data must have 1.0 default multiplier", 1.0, multCar2, 0.001)
    }

    @Test
    fun testResetClearsProfile() {
        manager.setPersonalizationEnabled("car_1", true)
        repeat(3) {
            manager.recordTripTelemetry("car_1", 100.0, 20.0, DrivingMode.NORMAL, 10_000.0, 2_000.0)
        }
        assertTrue(manager.getEfficiencyMultiplier("car_1", 100.0, 20.0, DrivingMode.NORMAL, 150.0) > 1.0)

        manager.resetVehicleProfile("car_1")
        assertFalse(manager.getProfile("car_1").isEnabled)
        assertEquals(1.0, manager.getEfficiencyMultiplier("car_1", 100.0, 20.0, DrivingMode.NORMAL, 150.0), 0.001)
    }
}
