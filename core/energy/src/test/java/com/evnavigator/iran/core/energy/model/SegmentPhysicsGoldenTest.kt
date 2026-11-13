package com.evnavigator.iran.core.energy.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SegmentPhysicsGoldenTest {

    private fun baseInput(
        gradePercent: Double = 0.0,
        altitudeM: Double? = 0.0,
        speedMps: Double = 25.0
    ) = SegmentPhysicsInput(
        horizontalDistanceM = 1000.0,
        gradePercent = gradePercent,
        altitudeM = altitudeM,
        speedMps = speedMps,
        massKg = 1700.0,
        dragCoefficient = 0.32,
        frontalAreaM2 = 2.2,
        rollingResistanceCoeff = 0.012,
        drivetrainEfficiency = 0.85,
        regenEfficiency = 0.80,
        auxPowerW = 200.0
    )

    @Test
    fun `golden test 1 - flat sea level 1km at 25 mps yields 155_7 Wh draw`() {
        val res = SegmentPhysics.compute(baseInput(gradePercent = 0.0, altitudeM = 0.0))
        assertTrue(res.isValid)
        assertEquals(55.6, res.rollingWh, 0.5)
        assertEquals(74.9, res.aeroWh, 0.5)
        assertEquals(155.7, res.drawWh, 0.6)
    }

    @Test
    fun `golden test 2 - flat 1200m altitude yields 146_0 Wh draw`() {
        val res = SegmentPhysics.compute(baseInput(gradePercent = 0.0, altitudeM = 1200.0))
        assertTrue(res.isValid)
        assertEquals(146.0, res.drawWh, 0.6)
    }

    @Test
    fun `golden test 3 - uphill +5 percent grade yields 428 Wh draw`() {
        val res = SegmentPhysics.compute(baseInput(gradePercent = 5.0, altitudeM = 0.0))
        assertTrue(res.isValid)
        assertEquals(428.0, res.drawWh, 2.5)
    }

    @Test
    fun `golden test 4 - downhill -5 percent grade yields 2_2 Wh draw and 80_8 Wh regen`() {
        val res = SegmentPhysics.compute(baseInput(gradePercent = -5.0, altitudeM = 0.0))
        assertTrue(res.isValid)
        assertEquals(2.2, res.drawWh, 0.2)
        assertEquals(80.8, res.regenWh, 1.0)
    }

    @Test
    fun `golden test 5 - zero speed returns invalid finite result without Infinity`() {
        val res = SegmentPhysics.compute(baseInput(speedMps = 0.0))
        assertFalse(res.isValid)
        assertTrue(res.drawWh.isFinite())
        assertTrue(res.netWh.isFinite())
    }
}
