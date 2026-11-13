package com.evnavigator.iran.core.tripplanner.engine

import org.junit.Test
import org.junit.Assert.*

class ChargingCurveModelTest {
    
    @Test
    fun testEffectivePowerKw() {
        // Test 0% to 50% - should return full power
        val power1 = ChargingCurveModel.effectivePowerKw(20.0, 100.0, 120.0)
        assertEquals(100.0, power1, 0.01) // Should be limited by station power
        
        // Test 50% to 80% - should taper down
        val power2 = ChargingCurveModel.effectivePowerKw(65.0, 100.0, 120.0)
        assertTrue(power2 < 100.0) // Should be less than peak
        assertTrue(power2 > 50.0) // Should be more than 50% of peak
        
        // Test 80% to 100% - should taper further down
        val power3 = ChargingCurveModel.effectivePowerKw(90.0, 100.0, 120.0)
        assertTrue(power3 <= 50.0) // Should be less than or equal to 50% of peak
        assertTrue(power3 >= 15.0) // Should maintain trickle minimum
        
        // Test edge cases
        assertEquals(0.0, ChargingCurveModel.effectivePowerKw(-10.0, 100.0, 120.0), 0.001)
        assertEquals(0.0, ChargingCurveModel.effectivePowerKw(110.0, 100.0, 120.0), 0.001)
    }
    
    @Test
    fun testGoldenValueChargingDuration() {
        // 60 kWh, 10->80%, station 150 kW, vehicle 120 kW -> 24.5 ± 1.0 min
        val d1 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 10.0,
            endSocPercent = 80.0,
            stationMaxPowerKw = 150.0,
            vehicleMaxChargingPowerKw = 120.0,
            usableBatteryCapacityKwh = 60.0
        )
        assertEquals(24.5, d1, 1.0)

        // 60 kWh, 10->80%, station 50 kW, vehicle 120 kW -> 58.7 ± 1.5 min
        val d2 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 10.0,
            endSocPercent = 80.0,
            stationMaxPowerKw = 50.0,
            vehicleMaxChargingPowerKw = 120.0,
            usableBatteryCapacityKwh = 60.0
        )
        assertEquals(58.7, d2, 1.5)

        // 78 kWh, 10->80%, station 100 kW, vehicle 150 kW -> 38.2 ± 1.5 min
        val d3 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 10.0,
            endSocPercent = 80.0,
            stationMaxPowerKw = 100.0,
            vehicleMaxChargingPowerKw = 150.0,
            usableBatteryCapacityKwh = 78.0
        )
        assertEquals(38.2, d3, 1.5)

        // 80->100% (20% slice) must be slower than 50->70% (same 20% slice)
        val d50to70 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 50.0,
            endSocPercent = 70.0,
            stationMaxPowerKw = 150.0,
            vehicleMaxChargingPowerKw = 150.0,
            usableBatteryCapacityKwh = 60.0
        )
        val d80to100 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 80.0,
            endSocPercent = 100.0,
            stationMaxPowerKw = 150.0,
            vehicleMaxChargingPowerKw = 150.0,
            usableBatteryCapacityKwh = 60.0
        )
        assertTrue("80->100% ($d80to100) must be slower than 50->70% ($d50to70)", d80to100 > d50to70)

        // start >= end -> 0.0
        val dZero = ChargingCurveModel.estimateChargingDurationMinutes(80.0, 80.0, 100.0, 100.0, 60.0)
        assertEquals(0.0, dZero, 0.001)

        val dReverse = ChargingCurveModel.estimateChargingDurationMinutes(85.0, 50.0, 100.0, 100.0, 60.0)
        assertEquals(0.0, dReverse, 0.001)

        val dNaN = ChargingCurveModel.estimateChargingDurationMinutes(Double.NaN, 80.0, 100.0, 100.0, 60.0)
        assertEquals(0.0, dNaN, 0.001)
    }

    @Test
    fun testEstimateChargingDurationMinutesMonotonicity() {
        val duration1 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 20.0,
            endSocPercent = 50.0,
            stationMaxPowerKw = 100.0,
            vehicleMaxChargingPowerKw = 120.0,
            usableBatteryCapacityKwh = 60.0
        )
        
        val duration2 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 20.0,
            endSocPercent = 80.0,
            stationMaxPowerKw = 100.0,
            vehicleMaxChargingPowerKw = 120.0,
            usableBatteryCapacityKwh = 60.0
        )
        
        assertTrue(duration1 > 0.0)
        assertTrue(duration2 > duration1)
        
        // Test when no charge needed
        val duration3 = ChargingCurveModel.estimateChargingDurationMinutes(
            startSocPercent = 80.0,
            endSocPercent = 70.0,
            stationMaxPowerKw = 100.0,
            vehicleMaxChargingPowerKw = 120.0,
            usableBatteryCapacityKwh = 60.0
        )
        
        assertEquals(0.0, duration3, 0.001)
    }
}
