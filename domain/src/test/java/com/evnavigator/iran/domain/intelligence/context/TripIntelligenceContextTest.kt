package com.evnavigator.iran.domain.intelligence.context

import org.junit.Assert.*
import org.junit.Test

class TripIntelligenceContextTest {

    private fun createValidContext(): TripIntelligenceContext {
        return TripIntelligenceContext(
            vehicleId = "ir_samand_ev_2025",
            batteryCapacityKwh = InputQualityAttribute.verified(60.0),
            curbWeightKg = InputQualityAttribute.verified(1650.0),
            additionalPayloadKg = InputQualityAttribute.userConfigured(150.0),
            dragCoefficient = InputQualityAttribute.verified(0.28),
            frontalAreaM2 = InputQualityAttribute.verified(2.2),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010),
            driveEfficiency = InputQualityAttribute.verified(0.90),
            currentSocPercent = InputQualityAttribute.measured(85.0, System.currentTimeMillis()),
            targetArrivalSocPercent = InputQualityAttribute.userConfigured(20.0),
            minReserveSocPercent = InputQualityAttribute.userConfigured(10.0),
            routeDistanceMeters = 120_000.0,
            routeEstimatedDurationSeconds = 4800.0,
            elevationGainMeters = InputQualityAttribute.verified(450.0),
            elevationLossMeters = InputQualityAttribute.verified(200.0),
            averageSpeedKmh = InputQualityAttribute.estimated(90.0),
            ambientTemperatureCelsius = InputQualityAttribute.measured(25.0, System.currentTimeMillis()),
            windSpeedMps = InputQualityAttribute.measured(3.5, System.currentTimeMillis()),
            windDirectionDegrees = InputQualityAttribute.measured(180.0, System.currentTimeMillis()),
            hvacPowerKw = InputQualityAttribute.assumed(1.5, "Standard A/C setting"),
            drivingMode = DrivingMode.NORMAL
        )
    }

    @Test
    fun testValidContextPassesValidation() {
        val ctx = createValidContext()
        val diag = InputQualityValidator.validate(ctx)

        assertTrue("Valid context must be valid for calculation", diag.isValidForCalculation)
        assertFalse("Should not have blocking errors", diag.hasBlockingErrors)
        assertTrue("Confidence score should be high", diag.overallConfidenceScore > 0.8)
        assertEquals("Should list 1 explicit assumption for HVAC", 1, diag.explicitAssumptions.size)
    }

    @Test
    fun testMissingBatteryCapacityBlocksCalculation() {
        val ctx = createValidContext().copy(
            batteryCapacityKwh = InputQualityAttribute.unknown()
        )
        val diag = InputQualityValidator.validate(ctx)

        assertFalse("Missing battery capacity must block calculation", diag.isValidForCalculation)
        assertTrue(diag.hasBlockingErrors)
        assertTrue(diag.missingRequiredFields.contains("batteryCapacityKwh"))
    }

    @Test
    fun testInvalidSocRangeFails() {
        val ctx = createValidContext().copy(
            currentSocPercent = InputQualityAttribute.measured(120.0, System.currentTimeMillis())
        )
        val diag = InputQualityValidator.validate(ctx)

        assertFalse("SOC > 100 must fail validation", diag.isValidForCalculation)
        assertTrue(diag.issues.any { it.field == "currentSocPercent" && it.isBlocking })
    }

    @Test
    fun testStaleObservationsFlagged() {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 3600 * 1000L)
        val ctx = createValidContext().copy(
            weatherFreshnessTimestamp = twoHoursAgo
        )
        val diag = InputQualityValidator.validate(ctx)

        assertTrue("Stale weather should not block, but generate warning", diag.isValidForCalculation)
        assertTrue("Should identify stale weather", diag.staleFields.contains("weather"))
        assertTrue("Should have warning", diag.warningCount > 0)
    }

    @Test
    fun testNegativePayloadBlocksCalculation() {
        val ctx = createValidContext().copy(
            additionalPayloadKg = InputQualityAttribute.userConfigured(-50.0)
        )
        val diag = InputQualityValidator.validate(ctx)

        assertFalse("Negative payload is physically invalid and must block", diag.isValidForCalculation)
    }

    @Test
    fun testExplicitAssumptionsMarkedCorrectly() {
        val ctx = createValidContext().copy(
            curbWeightKg = InputQualityAttribute.assumed(1600.0, "Class average vehicle weight", confidence = 0.6)
        )
        val diag = InputQualityValidator.validate(ctx)

        assertTrue(diag.explicitAssumptions.any { it.contains("Curb weight assumed") })
    }
}
