package com.evnavigator.iran.core.energy.test

import com.evnavigator.iran.core.energy.model.SegmentPhysics
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 2 Required Test Suite:
 * - Tests unknown slope & incomplete DEM (propagates uncertainty, flags assumed grade)
 * - Tests uphill vs downhill energy consumption
 * - Tests regenerative braking physics and efficiency
 */
class DemUncertaintyAndEnergyTest {

    private val massKg = 1950.0
    private val speedMps = 25.0 // 90 km/h
    private val distanceM = 1000.0 // 1 km
    private val cd = 0.28
    private val area = 2.30
    private val crr = 0.010
    private val driveEff = 0.88
    private val regenEff = 0.80

    @Test
    fun testUnknownGradePropagatesDemUncertainty() {
        // When grade is NaN (unknown elevation data / missing DEM tile)
        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distanceM,
            gradePercent = Double.NaN,
            absoluteAltitudeMeters = 1200.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = area,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff
        )

        assertTrue("Grade must be flagged as assumed when NaN is supplied", result.isGradeAssumed)
        assertTrue("DEM uncertainty must be positive when elevation is incomplete", result.demUncertaintyWh > 0.0)
        assertEquals(0.0, result.gradeWh, 0.001)
    }

    @Test
    fun testUphillConsumptionVsDownhillAndRegenerativeBraking() {
        // Flat road
        val flat = SegmentPhysics.calculate(
            horizontalDistanceMeters = distanceM,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 1000.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = area,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff
        )

        // Steep uphill (+6%)
        val uphill = SegmentPhysics.calculate(
            horizontalDistanceMeters = distanceM,
            gradePercent = 6.0,
            absoluteAltitudeMeters = 1000.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = area,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff
        )

        // Steep downhill (-6%)
        val downhill = SegmentPhysics.calculate(
            horizontalDistanceMeters = distanceM,
            gradePercent = -6.0,
            absoluteAltitudeMeters = 1000.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = area,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff
        )

        // Uphill assertions
        assertTrue("Uphill net energy must be significantly higher than flat road", uphill.netWh > flat.netWh)
        assertTrue("Uphill grade force must be positive", uphill.gradeForceN > 0.0)
        assertEquals(0.0, uphill.regenWh, 0.001)

        // Downhill assertions
        assertTrue("Downhill wheel work must be negative (gravity overcoming friction)", downhill.wheelWh < 0.0)
        assertTrue("Downhill must produce regenerative braking energy", downhill.regenWh > 0.0)
        assertTrue("Downhill net energy must be lower than flat road", downhill.netWh < flat.netWh)
        assertTrue("Regenerative energy cannot exceed theoretical potential energy times efficiency",
            downhill.regenWh <= (-downhill.gradeWh * regenEff) + 50.0)
    }

    @Test
    fun testIncompleteAltitudeUsesSafeBarometricFallback() {
        val (rhoKnown, assumedKnown) = SegmentPhysics.calculateAirDensity(altitudeM = 1500.0 as Double?)
        val (rhoUnknown, assumedUnknown) = SegmentPhysics.calculateAirDensity(altitudeM = null)

        assertFalse("Known altitude is not assumed", assumedKnown)
        assertTrue("Null altitude must be marked assumed", assumedUnknown)
        assertEquals("Standard sea level air density used as safe baseline for null altitude", 1.225, rhoUnknown, 0.001)
        assertTrue("Air density at 1500m elevation must be less than sea level", rhoKnown < 1.225)
    }
}
