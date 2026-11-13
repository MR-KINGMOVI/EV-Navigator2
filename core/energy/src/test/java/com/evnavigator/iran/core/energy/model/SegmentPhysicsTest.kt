package com.evnavigator.iran.core.energy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPhysicsTest {

    // Test parameters from specification:
    // mass = 1700 kg, Cd = 0.32, A = 2.2 m², Crr = 0.012, driveEff = 0.85, regenEff = 0.80, aux = 200 W, dist = 1000 m (1 km), speed = 25 m/s
    private val massKg = 1700.0
    private val cd = 0.32
    private val areaM2 = 2.2
    private val crr = 0.012
    private val driveEff = 0.85
    private val regenEff = 0.80
    private val auxW = 200.0
    private val distM = 1000.0
    private val speedMps = 25.0

    @Test
    fun testFlatSeaLevelGoldenValues() {
        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distM,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff,
            auxiliaryPowerWatts = auxW
        )

        // Rolling: ~55.6 Wh, Aero: ~74.9 Wh, Total draw: ~155.7 Wh
        assertEquals(55.6, result.rollingResistanceWh, 0.5)
        assertEquals(74.9, result.aerodynamicDragWh, 0.5)
        assertEquals(155.7, result.drawWh, 0.5)
        assertEquals(0.0, result.regenWh, 0.01)
        assertEquals(155.7, result.netWh, 0.5)
    }

    @Test
    fun testKineticAccelerationWork() {
        // Accelerating from 10 m/s to 30 m/s on a 1000m flat segment:
        // Delta E_kin = 0.5 * 1700 * (30^2 - 10^2) = 0.5 * 1700 * (900 - 100) = 850 * 800 = 680,000 J
        // In Wh: 680,000 / 3600 = 188.89 Wh
        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distM,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = 20.0, // average cruising speed for distance/time
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff,
            auxiliaryPowerWatts = auxW,
            entrySpeedMps = 10.0,
            exitSpeedMps = 30.0
        )

        assertEquals(188.89, result.kineticDeltaWh, 0.5)
        assertTrue("Net energy must include kinetic acceleration work", result.wheelWh > result.rollingResistanceWh + result.aerodynamicDragWh)
    }

    @Test
    fun testTemperatureDeratingAndHvac() {
        // Cold winter condition (-10°C) on high plateau
        val coldRetention = SegmentPhysics.calculateTemperatureCapacityRetention(-10.0)
        assertTrue("Cold capacity should derate below 1.0", coldRetention in 0.70..0.85)

        // Optimal condition (25°C)
        val optimalRetention = SegmentPhysics.calculateTemperatureCapacityRetention(25.0)
        assertEquals(1.0, optimalRetention, 0.001)

        // HVAC heating power at -5°C (deltaT = 27°C)
        val heatingWatts = SegmentPhysics.calculateHvacPowerWatts(-5.0, 22.0)
        assertTrue("Heating draw should be active and substantial", heatingWatts > 2000.0)

        // HVAC cooling power at 40°C in desert (deltaT = 18°C)
        val coolingWatts = SegmentPhysics.calculateHvacPowerWatts(40.0, 22.0)
        assertTrue("AC cooling draw should be active", coolingWatts > 1200.0)
    }

    @Test
    fun testFlat1200mAltitudeGoldenValues() {
        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distM,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 1200.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff,
            auxiliaryPowerWatts = auxW
        )

        // Aero: ~66.6 Wh, Total draw: ~146.0 Wh
        assertEquals(66.6, result.aerodynamicDragWh, 0.5)
        assertEquals(146.0, result.drawWh, 0.5)
        assertEquals(0.0, result.regenWh, 0.01)
        assertEquals(146.0, result.netWh, 0.5)
    }

    @Test
    fun testUphillPlus5PercentGradeGoldenValues() {
        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distM,
            gradePercent = 5.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff,
            auxiliaryPowerWatts = auxW
        )

        // Grade work: ~+231.5 Wh, Total draw: ~428.0 Wh
        assertEquals(231.5, result.gradeWh, 0.8)
        assertEquals(428.0, result.drawWh, 1.0)
        assertEquals(0.0, result.regenWh, 0.01)
        assertEquals(428.0, result.netWh, 1.0)
    }

    @Test
    fun testDownhillMinus5PercentGradeGoldenValues() {
        val result = SegmentPhysics.calculate(
            horizontalDistanceMeters = distM,
            gradePercent = -5.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = speedMps,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            downhillEfficiency = regenEff,
            auxiliaryPowerWatts = auxW
        )

        // Draw: ~2.2 Wh (auxiliary only), Regen: ~80.8 Wh, Net: negative (charging battery)
        assertEquals(2.2, result.drawWh, 0.5)
        assertEquals(80.8, result.regenWh, 0.8)
        assertTrue("Net energy on steep descent should be negative (net recovery)", result.netWh < 0.0)
        assertEquals(result.drawWh - result.regenWh, result.netWh, 0.001)
    }

    @Test
    fun testZeroOrNegativeSpeedThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            SegmentPhysics.calculate(
                horizontalDistanceMeters = distM,
                gradePercent = 0.0,
                speedMps = 0.0,
                vehicleMassKg = massKg,
                dragCoefficient = cd,
                frontalAreaM2 = areaM2,
                rollingResistanceCoeff = crr
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            SegmentPhysics.calculate(
                horizontalDistanceMeters = distM,
                gradePercent = 0.0,
                speedMps = -10.0,
                vehicleMassKg = massKg,
                dragCoefficient = cd,
                frontalAreaM2 = areaM2,
                rollingResistanceCoeff = crr
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            SegmentPhysics.calculate(
                horizontalDistanceMeters = distM,
                gradePercent = 0.0,
                speedMps = Double.NaN,
                vehicleMassKg = massKg,
                dragCoefficient = cd,
                frontalAreaM2 = areaM2,
                rollingResistanceCoeff = crr
            )
        }
    }
}
