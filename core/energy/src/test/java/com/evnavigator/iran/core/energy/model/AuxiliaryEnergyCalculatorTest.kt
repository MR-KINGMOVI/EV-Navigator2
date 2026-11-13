package com.evnavigator.iran.core.energy.model

import org.junit.Test
import org.junit.Assert.*

class AuxiliaryEnergyCalculatorTest {

    @Test
    fun testCalculateAuxiliaryEnergyGuards() {
        val p = Power(1000.0) // 1 kW

        // Infinite, NaN, or non-positive time returns 0.0
        assertEquals(0.0, AuxiliaryEnergyCalculator.calculateAuxiliaryEnergy(p, Double.POSITIVE_INFINITY), 0.0001)
        assertEquals(0.0, AuxiliaryEnergyCalculator.calculateAuxiliaryEnergy(p, Double.NEGATIVE_INFINITY), 0.0001)
        assertEquals(0.0, AuxiliaryEnergyCalculator.calculateAuxiliaryEnergy(p, Double.NaN), 0.0001)
        assertEquals(0.0, AuxiliaryEnergyCalculator.calculateAuxiliaryEnergy(p, 0.0), 0.0001)
        assertEquals(0.0, AuxiliaryEnergyCalculator.calculateAuxiliaryEnergy(p, -120.0), 0.0001)

        // Normal valid case: 1000 W for 3600 s = 1000 Wh
        assertEquals(1000.0, AuxiliaryEnergyCalculator.calculateAuxiliaryEnergy(p, 3600.0), 0.0001)
    }
}
