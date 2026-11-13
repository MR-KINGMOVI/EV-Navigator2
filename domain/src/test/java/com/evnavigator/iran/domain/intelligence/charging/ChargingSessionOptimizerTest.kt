package com.evnavigator.iran.domain.intelligence.charging

import org.junit.Assert.*
import org.junit.Test

class ChargingSessionOptimizerTest {

    private val optimizer = ChargingSessionOptimizer()

    private val vehicleWithCurve = VehicleChargingProfile(
        vehicleId = "verified_ev_70",
        batteryCapacityKwh = 70.0,
        maxDCPowerKw = 150.0,
        verifiedCurve = listOf(
            ChargingCurvePoint(10.0, 150.0),
            ChargingCurvePoint(40.0, 140.0),
            ChargingCurvePoint(60.0, 100.0),
            ChargingCurvePoint(80.0, 50.0),
            ChargingCurvePoint(95.0, 20.0),
            ChargingCurvePoint(100.0, 10.0)
        )
    )

    private val vehicleWithoutCurve = VehicleChargingProfile(
        vehicleId = "unverified_ev_60",
        batteryCapacityKwh = 60.0,
        maxDCPowerKw = 100.0,
        verifiedCurve = null
    )

    @Test
    fun testChargerPowerBottleneck() {
        // Vehicle can take 150 kW, but charger only provides 50 kW
        val result = optimizer.optimizeSession(
            profile = vehicleWithCurve,
            chargerPowerLimitKw = 50.0,
            startSocPercent = 10.0,
            targetSocPercent = 60.0
        )

        // On 50 kW charger, effective average power cannot exceed 50 kW
        assertTrue("Effective average power must not exceed charger limit", result.effectiveAveragePowerKw <= 50.0)
        assertTrue("Estimated duration should be approximately (35 kWh / 50 kW) * 60 = 42 min", result.estimatedDurationMinutes in 40.0..46.0)
    }

    @Test
    fun testVehiclePowerBottleneck() {
        // Charger can give 350 kW, but vehicle max is 100 kW
        val result = optimizer.optimizeSession(
            profile = vehicleWithoutCurve,
            chargerPowerLimitKw = 350.0,
            startSocPercent = 10.0,
            targetSocPercent = 50.0
        )

        assertTrue("Effective average power must not exceed vehicle max power", result.effectiveAveragePowerKw <= 100.0)
    }

    @Test
    fun testTaperAboveEightyPercentTakesLonger() {
        // Charging 20% between 60% and 80% (14 kWh)
        val midSession = optimizer.optimizeSession(
            profile = vehicleWithCurve,
            chargerPowerLimitKw = 150.0,
            startSocPercent = 60.0,
            targetSocPercent = 80.0
        )

        // Charging 20% between 80% and 100% (14 kWh)
        val highSession = optimizer.optimizeSession(
            profile = vehicleWithCurve,
            chargerPowerLimitKw = 150.0,
            startSocPercent = 80.0,
            targetSocPercent = 100.0
        )

        assertTrue(highSession.isTaperingActive)
        assertTrue(
            "Charging 80-100% must take significantly longer than 60-80% due to steep taper",
            highSession.estimatedDurationMinutes > midSession.estimatedDurationMinutes * 1.8
        )
        assertTrue(highSession.recommendationNote.contains("Stopping at 80% saves charging time"))
    }

    @Test
    fun testColdTemperatureDeratingIncreasesDuration() {
        val warmSession = optimizer.optimizeSession(
            profile = vehicleWithCurve,
            chargerPowerLimitKw = 150.0,
            startSocPercent = 20.0,
            targetSocPercent = 70.0,
            batteryTempCelsius = 25.0
        )

        val coldSession = optimizer.optimizeSession(
            profile = vehicleWithCurve,
            chargerPowerLimitKw = 150.0,
            startSocPercent = 20.0,
            targetSocPercent = 70.0,
            batteryTempCelsius = -5.0
        )

        assertTrue(
            "Cold session must take significantly longer due to battery thermal limits",
            coldSession.estimatedDurationMinutes > warmSession.estimatedDurationMinutes * 1.5
        )
        assertTrue(coldSession.recommendationNote.contains("throttled"))
    }

    @Test
    fun testUnverifiedCurveFlaggedAsConservativeApproximation() {
        val result = optimizer.optimizeSession(
            profile = vehicleWithoutCurve,
            chargerPowerLimitKw = 100.0,
            startSocPercent = 20.0,
            targetSocPercent = 80.0
        )

        assertTrue(result.isConservativeApproximation)
        assertTrue(result.recommendationNote.contains("conservative automotive taper approximation"))
    }
}
