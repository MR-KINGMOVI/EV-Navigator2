package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertFailsWith as assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PowerCapabilityTest {

    @Test
    fun `test create AC power capability`() {
        val power = PowerCapability.createAcPower(
            powerKw = 7.0,
            voltage = 230.0,
            current = 32.0,
            phaseCount = 1
        )

        assertEquals(7.0, power.powerKw)
        assertEquals(230.0, power.voltage)
        assertEquals(32.0, power.current)
        assertEquals(true, power.isAc)
        assertEquals(false, power.isDc)
        assertEquals(1, power.phaseCount)
    }

    @Test
    fun `test create DC power capability`() {
        val power = PowerCapability.createDcPower(
            powerKw = 50.0,
            voltage = 400.0,
            current = 125.0
        )

        assertEquals(50.0, power.powerKw)
        assertEquals(400.0, power.voltage)
        assertEquals(125.0, power.current)
        assertEquals(false, power.isAc)
        assertEquals(true, power.isDc)
    }

    @Test
    fun `test create variable power capability`() {
        val power = PowerCapability.createVariablePower(
            minPowerKw = 10.0,
            maxPowerKw = 50.0,
            voltage = 400.0,
            current = 125.0,
            isAc = false,
            isDc = true
        )

        assertEquals(null, power.powerKw)
        assertEquals(400.0, power.voltage)
        assertEquals(125.0, power.current)
        assertEquals(true, power.isDc)
        assertEquals(false, power.isAc)
        assertEquals(10.0, power.minPowerKw)
        assertEquals(50.0, power.maxPowerKw)
    }

    @Test
    fun `test create unknown power capability`() {
        val power = PowerCapability.createUnknown()

        assertNull(power.powerKw)
        assertNull(power.voltage)
        assertNull(power.current)
        assertEquals(false, power.isAc)
        assertEquals(false, power.isDc)
    }

    @Test
    fun `test effective power calculation`() {
        // Test with explicit power value
        val power1 = PowerCapability.createAcPower(
            powerKw = 7.0,
            voltage = 230.0,
            current = 32.0,
            phaseCount = 1
        )
        assertEquals(7.0, power1.effectivePowerKw)

        // Test with calculated power value
        val power2 = PowerCapability(
            powerKw = null,
            voltage = 230.0,
            current = 32.0,
            isAc = true,
            isDc = false,
            phaseCount = 1
        )
        org.junit.Assert.assertEquals(7.36, power2.effectivePowerKw!!, 0.01)
    }

    @Test
    fun `test power range`() {
        val power = PowerCapability.createVariablePower(
            minPowerKw = 10.0,
            maxPowerKw = 50.0
        )

        assertEquals(10.0, power.minPossiblePowerKw)
        assertEquals(50.0, power.maxPossiblePowerKw)
    }

    @Test
    fun `test invalid negative power`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = -5.0,
                voltage = 230.0,
                current = 32.0,
                isAc = true,
                isDc = false
            )
        }
    }

    @Test
    fun `test invalid zero voltage`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = 7.0,
                voltage = 0.0,
                current = 32.0,
                isAc = true,
                isDc = false
            )
        }
    }

    @Test
    fun `test invalid zero current`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = 7.0,
                voltage = 230.0,
                current = 0.0,
                isAc = true,
                isDc = false
            )
        }
    }

    @Test
    fun `test invalid confidence level`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = 7.0,
                voltage = 230.0,
                current = 32.0,
                isAc = true,
                isDc = false,
                confidence = 1.5
            )
        }
    }

    @Test
    fun `test invalid AC DC flags`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = 7.0,
                voltage = 230.0,
                current = 32.0,
                isAc = false,
                isDc = false
            )
        }
    }

    @Test
    fun `test inconsistent power calculation`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = 7.0, // This should be ~7.36 based on voltage/current below
                voltage = 230.0,
                current = 32.0,
                isAc = true,
                isDc = false
            )
        }
    }

    @Test
    fun `test valid power range`() {
        val power = PowerCapability(
            powerKw = null,
            voltage = 400.0,
            current = 125.0,
            isAc = false,
            isDc = true,
            minPowerKw = 10.0,
            maxPowerKw = 50.0
        )

        assertEquals(10.0, power.minPowerKw)
        assertEquals(50.0, power.maxPowerKw)
    }

    @Test
    fun `test invalid power range`() {
        assertThrows<IllegalArgumentException> {
            PowerCapability(
                powerKw = null,
                voltage = 400.0,
                current = 125.0,
                isAc = false,
                isDc = true,
                minPowerKw = 50.0, // This should be less than max
                maxPowerKw = 10.0
            )
        }
    }
}