package com.evnavigator.iran.domain.intelligence.energy

import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import org.junit.Assert.*
import org.junit.Test

class PredictiveEnergyOrchestratorTest {

    private val orchestrator = PredictiveEnergyOrchestrator()

    private fun createBaseContext(
        distanceMeters: Double = 100_000.0,
        speedKmh: Double = 90.0,
        elevationGainMeters: Double = 0.0,
        elevationLossMeters: Double = 0.0,
        tempCelsius: Double = 20.0,
        startSoc: Double = 80.0
    ): TripIntelligenceContext {
        return TripIntelligenceContext(
            vehicleId = "test_ev",
            batteryCapacityKwh = InputQualityAttribute.verified(60.0),
            curbWeightKg = InputQualityAttribute.verified(1600.0),
            additionalPayloadKg = InputQualityAttribute.verified(150.0),
            dragCoefficient = InputQualityAttribute.verified(0.28),
            frontalAreaM2 = InputQualityAttribute.verified(2.2),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010),
            driveEfficiency = InputQualityAttribute.verified(0.90),
            currentSocPercent = InputQualityAttribute.measured(startSoc, System.currentTimeMillis()),
            targetArrivalSocPercent = InputQualityAttribute.userConfigured(15.0),
            minReserveSocPercent = InputQualityAttribute.userConfigured(10.0),
            routeDistanceMeters = distanceMeters,
            routeEstimatedDurationSeconds = (distanceMeters / (speedKmh / 3.6)),
            elevationGainMeters = InputQualityAttribute.verified(elevationGainMeters),
            elevationLossMeters = InputQualityAttribute.verified(elevationLossMeters),
            averageSpeedKmh = InputQualityAttribute.verified(speedKmh),
            ambientTemperatureCelsius = InputQualityAttribute.verified(tempCelsius),
            windSpeedMps = InputQualityAttribute.verified(0.0),
            windDirectionDegrees = InputQualityAttribute.verified(0.0),
            hvacPowerKw = InputQualityAttribute.verified(0.5),
            drivingMode = DrivingMode.NORMAL
        )
    }

    @Test
    fun testFlatRouteEnergyPrediction() {
        val ctx = createBaseContext()
        val pred = orchestrator.predictTripEnergy(ctx)

        assertTrue("Net consumption must be positive on flat route", pred.breakdown.netBatteryEnergyWh > 0)
        assertTrue("Arrival SOC must be less than start SOC", pred.predictedArrivalSocPercent < 80.0)
        assertTrue("Should be feasible on 100km with 60kWh pack", pred.isFeasibleWithoutCharging)
        assertEquals(0.0, pred.breakdown.elevationWh, 0.01)
    }

    @Test
    fun testUphillRouteConsumesMoreEnergyThanFlat() {
        val flatCtx = createBaseContext(elevationGainMeters = 0.0)
        val uphillCtx = createBaseContext(elevationGainMeters = 1000.0)

        val flatPred = orchestrator.predictTripEnergy(flatCtx)
        val uphillPred = orchestrator.predictTripEnergy(uphillCtx)

        assertTrue(
            "Uphill route must consume significantly more energy",
            uphillPred.breakdown.netBatteryEnergyWh > flatPred.breakdown.netBatteryEnergyWh
        )
        assertTrue(
            "Arrival SOC must be lower for uphill route",
            uphillPred.predictedArrivalSocPercent < flatPred.predictedArrivalSocPercent
        )
    }

    @Test
    fun testDownhillRouteRegeneratesEnergy() {
        val flatCtx = createBaseContext()
        val downhillCtx = createBaseContext(elevationLossMeters = 1200.0)

        val flatPred = orchestrator.predictTripEnergy(flatCtx)
        val downhillPred = orchestrator.predictTripEnergy(downhillCtx)

        assertTrue("Downhill route must have positive regeneration", downhillPred.breakdown.regeneratedWh > 0)
        assertTrue(
            "Net consumption on downhill must be lower than flat route",
            downhillPred.breakdown.netBatteryEnergyWh < flatPred.breakdown.netBatteryEnergyWh
        )
    }

    @Test
    fun testHighSpeedIncreasesAeroDragQuadratically() {
        val lowSpeedCtx = createBaseContext(speedKmh = 60.0)
        val highSpeedCtx = createBaseContext(speedKmh = 120.0)

        val lowPred = orchestrator.predictTripEnergy(lowSpeedCtx)
        val highPred = orchestrator.predictTripEnergy(highSpeedCtx)

        // At double speed, aerodynamic power is 8x, but distance is same so aero energy is 4x (v^2)
        val ratio = highPred.breakdown.aerodynamicDragWh / lowPred.breakdown.aerodynamicDragWh
        assertTrue("Aero energy should scale roughly with square of speed (ratio around 4.0)", ratio in 3.5..4.5)
    }

    @Test
    fun testColdTemperatureDeratingAndHvacHeating() {
        val warmCtx = createBaseContext(tempCelsius = 22.0)
        val coldCtx = createBaseContext(tempCelsius = -10.0)

        val warmPred = orchestrator.predictTripEnergy(warmCtx)
        val coldPred = orchestrator.predictTripEnergy(coldCtx)

        assertTrue(
            "Cold temperature should increase electrical demand due to internal resistance and heating",
            coldPred.breakdown.netBatteryEnergyWh > warmPred.breakdown.netBatteryEnergyWh
        )
        assertTrue(coldPred.diagnosticsNotes.any { it.contains("internal resistance") })
    }

    @Test
    fun testRegenThrottledAtNearlyFullBattery() {
        val normalSocCtx = createBaseContext(startSoc = 50.0, elevationLossMeters = 1000.0)
        val fullSocCtx = createBaseContext(startSoc = 98.0, elevationLossMeters = 1000.0)

        val normalPred = orchestrator.predictTripEnergy(normalSocCtx)
        val fullPred = orchestrator.predictTripEnergy(fullSocCtx)

        assertTrue(
            "Regen at 98% SOC must be heavily capped compared to 50% SOC",
            fullPred.breakdown.regeneratedWh < normalPred.breakdown.regeneratedWh
        )
        assertTrue(fullPred.diagnosticsNotes.any { it.contains("throttled") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMissingBatteryCapacityThrowsException() {
        val invalidCtx = createBaseContext().copy(batteryCapacityKwh = InputQualityAttribute.unknown())
        orchestrator.predictTripEnergy(invalidCtx)
    }

    @Test
    fun testClimbGainSeparationConsumesMoreThanNetFlatGain() {
        // Gain 850m with Loss 620m vs Net Gain 230m with 0 Loss
        val complexTerrainCtx = createBaseContext(
            elevationGainMeters = 850.0,
            elevationLossMeters = 620.0
        )
        val simpleGainCtx = createBaseContext(
            elevationGainMeters = 230.0,
            elevationLossMeters = 0.0
        )

        val complexPred = orchestrator.predictTripEnergy(complexTerrainCtx)
        val simplePred = orchestrator.predictTripEnergy(simpleGainCtx)

        assertTrue(
            "Terrain with 850m gain and 620m loss must have higher net consumption than simple 230m gain: complex=${complexPred.breakdown.netBatteryEnergyWh}, simple=${simplePred.breakdown.netBatteryEnergyWh}",
            complexPred.breakdown.netBatteryEnergyWh > simplePred.breakdown.netBatteryEnergyWh
        )
    }

    @Test
    fun testHighAltitudeReducesAerodynamicDrag() {
        val seaLevelCtx = createBaseContext().copy(
            averageElevationMeters = InputQualityAttribute.verified(0.0)
        )
        val highAltitudeCtx = createBaseContext().copy(
            averageElevationMeters = InputQualityAttribute.verified(1500.0)
        )

        val seaLevelPred = orchestrator.predictTripEnergy(seaLevelCtx)
        val highAltPred = orchestrator.predictTripEnergy(highAltitudeCtx)

        assertTrue(
            "Aerodynamic drag at 1500m (${highAltPred.breakdown.aerodynamicDragWh}) must be less than sea level (${seaLevelPred.breakdown.aerodynamicDragWh})",
            highAltPred.breakdown.aerodynamicDragWh < seaLevelPred.breakdown.aerodynamicDragWh
        )
    }

    @Test
    fun testWindVectorAffectsAerodynamicDrag() {
        // Heading North (0 deg)
        // Headwind: Wind coming from North (0 deg) at 10 m/s
        val headwindCtx = createBaseContext().copy(
            routeHeadingDegrees = 0.0,
            windSpeedMps = InputQualityAttribute.verified(10.0),
            windDirectionDegrees = InputQualityAttribute.verified(0.0)
        )
        // Calm wind (0 m/s)
        val calmCtx = createBaseContext().copy(
            routeHeadingDegrees = 0.0,
            windSpeedMps = InputQualityAttribute.verified(0.0),
            windDirectionDegrees = InputQualityAttribute.verified(0.0)
        )
        // Tailwind: Wind coming from South (180 deg) at 10 m/s
        val tailwindCtx = createBaseContext().copy(
            routeHeadingDegrees = 0.0,
            windSpeedMps = InputQualityAttribute.verified(10.0),
            windDirectionDegrees = InputQualityAttribute.verified(180.0)
        )

        val headwindPred = orchestrator.predictTripEnergy(headwindCtx)
        val calmPred = orchestrator.predictTripEnergy(calmCtx)
        val tailwindPred = orchestrator.predictTripEnergy(tailwindCtx)

        assertTrue(
            "Headwind must cause more aero drag than calm wind",
            headwindPred.breakdown.aerodynamicDragWh > calmPred.breakdown.aerodynamicDragWh
        )
        assertTrue(
            "Tailwind must cause less aero drag than calm wind",
            tailwindPred.breakdown.aerodynamicDragWh < calmPred.breakdown.aerodynamicDragWh
        )
    }

    @Test
    fun testMissingDemKeepsElevationUnknownAndEmitsUncertaintyWarning() {
        // Average elevation missing / unknown
        val noDemCtx = createBaseContext().copy(
            averageElevationMeters = InputQualityAttribute.unknown()
        )

        val pred = orchestrator.predictTripEnergy(noDemCtx)

        assertTrue("DEM uncertainty Wh must be greater than 0 when DEM is missing", pred.totalDemUncertaintyWh > 0.0)
        assertEquals("LOW_CONFIDENCE", pred.dataQuality)
        assertFalse("Calculation must not be marked exact when DEM is absent", pred.isExactCalculation)
        assertEquals(25.0, pred.uncertaintyPercent, 0.01)
        assertTrue(
            "Diagnostics must contain explicit DEM warning",
            pred.diagnosticsNotes.any { it.contains("داده رقومی ارتفاعی (DEM) موجود نیست") }
        )
    }

    @Test
    fun testVerifiedDemProducesExactCalculation() {
        val verifiedDemCtx = createBaseContext().copy(
            averageElevationMeters = InputQualityAttribute.verified(1250.0)
        )

        val pred = orchestrator.predictTripEnergy(verifiedDemCtx)

        assertEquals("VERIFIED", pred.dataQuality)
        assertTrue("Calculation with verified DEM must be marked exact", pred.isExactCalculation)
        assertEquals(5.0, pred.uncertaintyPercent, 0.01)
        assertEquals(0.0, pred.totalDemUncertaintyWh, 0.01)
    }

    @Test
    fun testAssumedOrEstimatedInputsNeverProduceVerifiedQualityEvenWhenDemIsPresent() {
        val assumedSocCtx = createBaseContext().copy(
            averageElevationMeters = InputQualityAttribute.verified(1250.0),
            currentSocPercent = InputQualityAttribute.assumed(80.0, "Default unconfirmed fallback")
        )
        val assumedSocPred = orchestrator.predictTripEnergy(assumedSocCtx)
        assertEquals("ASSUMED", assumedSocPred.dataQuality)
        assertFalse("Unverified SOC must never be marked exact", assumedSocPred.isExactCalculation)

        val estimatedHvacCtx = createBaseContext().copy(
            averageElevationMeters = InputQualityAttribute.verified(1250.0),
            hvacPowerKw = InputQualityAttribute.estimated(0.8)
        )
        val estimatedHvacPred = orchestrator.predictTripEnergy(estimatedHvacCtx)
        assertEquals("ESTIMATED", estimatedHvacPred.dataQuality)
        assertFalse("Estimated HVAC must never be marked exact", estimatedHvacPred.isExactCalculation)
    }
}
