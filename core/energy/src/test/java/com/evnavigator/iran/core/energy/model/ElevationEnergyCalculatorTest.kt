package com.evnavigator.iran.core.energy.model

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.atan

class ElevationEnergyCalculatorTest {

    @Test
    fun testCalculateGradeAngleRadians() {
        // 8% grade: atan(0.08) = 0.07982998... rad
        val angle8 = ElevationEnergyCalculator.calculateGradeAngleRadians(8.0)
        assertEquals(0.07983, angle8, 0.0001)

        // 0% grade: atan(0) = 0.0 rad
        val angle0 = ElevationEnergyCalculator.calculateGradeAngleRadians(0.0)
        assertEquals(0.0, angle0, 0.000001)

        // Negative grade: -5% grade -> negative angle
        val angleNeg = ElevationEnergyCalculator.calculateGradeAngleRadians(-5.0)
        assertTrue("Negative grade must yield negative angle", angleNeg < 0.0)
        assertEquals(atan(-0.05), angleNeg, 0.000001)
    }
}
