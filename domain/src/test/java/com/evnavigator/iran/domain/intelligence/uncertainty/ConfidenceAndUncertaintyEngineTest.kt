package com.evnavigator.iran.domain.intelligence.uncertainty

import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.IntelligenceCoverageStatus
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import org.junit.Assert.*
import org.junit.Test

class ConfidenceAndUncertaintyEngineTest {

    private val energyOrchestrator = PredictiveEnergyOrchestrator()
    private val uncertaintyEngine = ConfidenceAndUncertaintyEngine()

    private fun createVerifiedContext(coverage: IntelligenceCoverageStatus = IntelligenceCoverageStatus.FULL_COVERAGE): TripIntelligenceContext {
        val now = System.currentTimeMillis()
        return TripIntelligenceContext(
            vehicleId = "verified_car",
            batteryCapacityKwh = InputQualityAttribute.verified(70.0),
            curbWeightKg = InputQualityAttribute.verified(1700.0),
            additionalPayloadKg = InputQualityAttribute.verified(150.0),
            dragCoefficient = InputQualityAttribute.verified(0.27),
            frontalAreaM2 = InputQualityAttribute.verified(2.2),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010),
            driveEfficiency = InputQualityAttribute.verified(0.92),
            currentSocPercent = InputQualityAttribute.measured(90.0, now),
            targetArrivalSocPercent = InputQualityAttribute.userConfigured(20.0),
            minReserveSocPercent = InputQualityAttribute.userConfigured(10.0),
            routeDistanceMeters = 80_000.0,
            routeEstimatedDurationSeconds = 3200.0,
            elevationGainMeters = InputQualityAttribute.verified(200.0),
            elevationLossMeters = InputQualityAttribute.verified(200.0),
            averageSpeedKmh = InputQualityAttribute.verified(90.0),
            ambientTemperatureCelsius = InputQualityAttribute.measured(20.0, now),
            windSpeedMps = InputQualityAttribute.measured(2.0, now),
            windDirectionDegrees = InputQualityAttribute.measured(90.0, now),
            hvacPowerKw = InputQualityAttribute.verified(0.5),
            drivingMode = DrivingMode.NORMAL,
            weatherFreshnessTimestamp = now,
            trafficFreshnessTimestamp = now,
            coverageStatus = coverage
        )
    }

    @Test
    fun testVerifiedInputsYieldLowUncertainty() {
        val ctx = createVerifiedContext()
        val pred = energyOrchestrator.predictTripEnergy(ctx)
        val uncertainty = uncertaintyEngine.evaluateUncertainty(pred, ctx, isCalibrated = true)

        assertEquals("Verified inputs with calibration should yield LOW_UNCERTAINTY", QualitativeUncertainty.LOW_UNCERTAINTY, uncertainty.qualitativeLevel)
        assertTrue("Primary risks should be empty for verified inputs", uncertainty.primaryRiskFactors.isEmpty())
        assertTrue("Lower energy bound must be less than nominal", uncertainty.energyIntervalWh.lowerBound < uncertainty.energyIntervalWh.nominalValue)
        assertTrue("Upper energy bound must be greater than nominal", uncertainty.energyIntervalWh.upperBound > uncertainty.energyIntervalWh.nominalValue)
    }

    @Test
    fun testMissingElevationIncreasesUncertaintyInterval() {
        val verifiedCtx = createVerifiedContext()
        val missingElevCtx = createVerifiedContext(coverage = IntelligenceCoverageStatus.ELEVATION_MISSING)

        val predVerified = energyOrchestrator.predictTripEnergy(verifiedCtx)
        val predMissingElev = energyOrchestrator.predictTripEnergy(missingElevCtx)

        val uncVerified = uncertaintyEngine.evaluateUncertainty(predVerified, verifiedCtx)
        val uncMissingElev = uncertaintyEngine.evaluateUncertainty(predMissingElev, missingElevCtx)

        assertTrue(
            "Missing elevation must widen the uncertainty span",
            uncMissingElev.energyIntervalWh.span > uncVerified.energyIntervalWh.span
        )
        assertTrue(uncMissingElev.primaryRiskFactors.any { it.contains("Elevation profile is missing") })
    }

    @Test
    fun testStaleWeatherIncreasesUncertainty() {
        val verifiedCtx = createVerifiedContext()
        val twoHoursAgo = System.currentTimeMillis() - (2 * 3600 * 1000L)
        val staleWeatherCtx = verifiedCtx.copy(weatherFreshnessTimestamp = twoHoursAgo)

        val pred = energyOrchestrator.predictTripEnergy(staleWeatherCtx)
        val uncVerified = uncertaintyEngine.evaluateUncertainty(pred, verifiedCtx)
        val uncStale = uncertaintyEngine.evaluateUncertainty(pred, staleWeatherCtx)

        assertTrue(
            "Stale weather must strictly increase uncertainty delta",
            uncStale.sources.weatherStalenessDeltaWh > uncVerified.sources.weatherStalenessDeltaWh
        )
    }

    @Test
    fun testArrivalSocLowerBoundIsStrictlyConservative() {
        val ctx = createVerifiedContext()
        val pred = energyOrchestrator.predictTripEnergy(ctx)
        val uncertainty = uncertaintyEngine.evaluateUncertainty(pred, ctx)

        assertTrue(
            "Worst-case arrival SOC must be less than nominal arrival SOC",
            uncertainty.arrivalSocIntervalPercent.lowerBound < uncertainty.arrivalSocIntervalPercent.nominalValue
        )
    }
}
