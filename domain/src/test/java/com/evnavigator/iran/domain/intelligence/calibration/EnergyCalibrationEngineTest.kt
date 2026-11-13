package com.evnavigator.iran.domain.intelligence.calibration

import org.junit.Assert.*
import org.junit.Test

class EnergyCalibrationEngineTest {

    private val engine = EnergyCalibrationEngine(minSamplesThreshold = 3)

    private fun createObs(
        tripId: String,
        vehicleId: String = "veh_1",
        predictedWh: Double = 10_000.0,
        measuredWh: Double = 11_000.0,
        distanceMeters: Double = 50_000.0,
        isVerified: Boolean = true
    ): TripEnergyObservation {
        return TripEnergyObservation(
            tripId = tripId,
            vehicleId = vehicleId,
            distanceMeters = distanceMeters,
            predictedWh = predictedWh,
            measuredWh = measuredWh,
            averageSpeedKmh = 85.0,
            ambientTempCelsius = 22.0,
            timestampEpochMs = System.currentTimeMillis(),
            isVerifiedTelemetry = isVerified
        )
    }

    @Test
    fun testInsufficientSamplesDoesNotCalibrate() {
        val state1 = engine.recordObservation(createObs("trip_1", measuredWh = 11_000.0))
        assertFalse("1 sample should not activate calibration", state1.isCalibrated)
        assertEquals(1.0, state1.correctionMultiplier, 0.001)

        val state2 = engine.recordObservation(createObs("trip_2", measuredWh = 11_000.0))
        assertFalse("2 samples should not activate calibration", state2.isCalibrated)
        assertEquals(1.0, state2.correctionMultiplier, 0.001)
    }

    @Test
    fun testSufficientSamplesActivatesCalibration() {
        engine.recordObservation(createObs("trip_1", measuredWh = 11_000.0)) // 1.10
        engine.recordObservation(createObs("trip_2", measuredWh = 11_200.0)) // 1.12
        val state = engine.recordObservation(createObs("trip_3", measuredWh = 10_800.0)) // 1.08

        assertTrue("3 samples should activate calibration", state.isCalibrated)
        assertEquals(3, state.eligibleSampleCount)
        assertEquals(1.10, state.correctionMultiplier, 0.02)

        val adjustedWh = engine.applyCalibration(10_000.0, "veh_1")
        assertEquals(11_000.0, adjustedWh, 200.0)
    }

    @Test
    fun testOutlierRejectionProtectsModel() {
        engine.recordObservation(createObs("trip_1", measuredWh = 10_500.0)) // 1.05
        engine.recordObservation(createObs("trip_2", measuredWh = 10_600.0)) // 1.06
        engine.recordObservation(createObs("trip_3", measuredWh = 10_400.0)) // 1.04
        // Massive anomaly (e.g. towing a heavy boat or roof box)
        val state = engine.recordObservation(createObs("trip_outlier", measuredWh = 25_000.0)) // 2.50

        assertTrue("Should remain calibrated", state.isCalibrated)
        assertEquals("Outlier should be detected and filtered", 1, state.excludedOutlierCount)
        // Multiplier should still be ~1.05, not pulled up by the 2.5x trip
        assertEquals(1.05, state.correctionMultiplier, 0.02)
    }

    @Test
    fun testMultiplierIsPhysicallyBounded() {
        // Extreme consistently high consumption (e.g. 1.8x)
        engine.recordObservation(createObs("trip_1", measuredWh = 18_000.0))
        engine.recordObservation(createObs("trip_2", measuredWh = 18_000.0))
        val state = engine.recordObservation(createObs("trip_3", measuredWh = 18_000.0))

        assertTrue(state.isCalibrated)
        assertEquals("Correction multiplier must be capped at maxMultiplier (1.25)", 1.25, state.correctionMultiplier, 0.001)
    }

    @Test
    fun testResetCalibrationClearsState() {
        engine.recordObservation(createObs("trip_1", measuredWh = 11_000.0))
        engine.recordObservation(createObs("trip_2", measuredWh = 11_000.0))
        engine.recordObservation(createObs("trip_3", measuredWh = 11_000.0))
        assertTrue(engine.getCalibration("veh_1").isCalibrated)

        engine.resetCalibration("veh_1")
        val resetState = engine.getCalibration("veh_1")
        assertFalse("Reset must remove calibration", resetState.isCalibrated)
        assertEquals(1.0, resetState.correctionMultiplier, 0.001)
        assertEquals(0, resetState.eligibleSampleCount)
    }

    @Test
    fun testVehicleIsolation() {
        engine.recordObservation(createObs("trip_1", vehicleId = "car_A", measuredWh = 11_500.0))
        engine.recordObservation(createObs("trip_2", vehicleId = "car_A", measuredWh = 11_500.0))
        engine.recordObservation(createObs("trip_3", vehicleId = "car_A", measuredWh = 11_500.0))

        val calA = engine.getCalibration("car_A")
        val calB = engine.getCalibration("car_B")

        assertTrue(calA.isCalibrated)
        assertEquals(1.15, calA.correctionMultiplier, 0.02)

        assertFalse("Vehicle B must have no calibration applied", calB.isCalibrated)
        assertEquals(1.0, calB.correctionMultiplier, 0.001)
    }
}
