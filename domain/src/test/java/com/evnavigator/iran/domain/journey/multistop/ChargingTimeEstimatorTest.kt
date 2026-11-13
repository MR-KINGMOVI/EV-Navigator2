package com.evnavigator.iran.domain.journey.multistop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingTimeEstimatorTest {

    @Test
    fun taperMakesHighSocSlowerThanLowSoc() {
        val low = ChargingTimeEstimator.minutes(20.0, 40.0, 100.0, 70.0)
        val high = ChargingTimeEstimator.minutes(80.0, 100.0, 100.0, 70.0)
        assertTrue("80-100% must take longer than 20-40%", high > low * 2.0)
    }

    @Test
    fun flatRegionMatchesSimpleEnergyDivision() {
        // 10..50 % of 70 kWh = 28 kWh at 100 kW and 92 % efficiency
        val minutes = ChargingTimeEstimator.minutes(10.0, 50.0, 100.0, 70.0)
        assertEquals(28.0 / (100.0 * 0.92) * 60.0, minutes, 0.5)
    }

    @Test
    fun invalidInputsGiveZero() {
        assertEquals(0.0, ChargingTimeEstimator.minutes(60.0, 50.0, 100.0, 70.0), 0.0)
        assertEquals(0.0, ChargingTimeEstimator.minutes(10.0, 50.0, 0.0, 70.0), 0.0)
        assertEquals(0.0, ChargingTimeEstimator.minutes(10.0, 50.0, 100.0, 0.0), 0.0)
    }
}
