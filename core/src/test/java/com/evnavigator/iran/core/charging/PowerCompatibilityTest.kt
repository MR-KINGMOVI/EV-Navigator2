package com.evnavigator.iran.core.charging

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for PowerCompatibilityEngine
 */
class PowerCompatibilityTest {

    @Test
    fun testCompatiblePowers() {
        val result = PowerCompatibilityEngine.determineEffectivePower(50.0, 100.0)
        
        assertEquals(PowerCompatibility.COMPATIBLE, result.status)
        assertEquals(50.0, result.vehiclePowerKw!!, 0.001)
        assertEquals(100.0, result.stationPowerKw!!, 0.001)
        assertEquals(50.0, result.effectivePowerKw!!, 0.001)
    }

    @Test
    fun testStationLimitingPower() {
        val result = PowerCompatibilityEngine.determineEffectivePower(100.0, 50.0)
        
        assertEquals(PowerCompatibility.COMPATIBLE, result.status)
        assertEquals(100.0, result.vehiclePowerKw!!, 0.001)
        assertEquals(50.0, result.stationPowerKw!!, 0.001)
        assertEquals(50.0, result.effectivePowerKw!!, 0.001)
    }

    @Test
    fun testUnknownVehiclePower() {
        val result = PowerCompatibilityEngine.determineEffectivePower(null, 50.0)
        
        assertEquals(PowerCompatibility.UNKNOWN, result.status)
        assertNull(result.vehiclePowerKw)
        assertEquals(50.0, result.stationPowerKw!!, 0.001)
        assertNull(result.effectivePowerKw)
    }

    @Test
    fun testUnknownStationPower() {
        val result = PowerCompatibilityEngine.determineEffectivePower(50.0, null)
        
        assertEquals(PowerCompatibility.UNKNOWN, result.status)
        assertEquals(50.0, result.vehiclePowerKw!!, 0.001)
        assertNull(result.stationPowerKw)
        assertNull(result.effectivePowerKw)
    }

    @Test
    fun testBothPowersUnknown() {
        val result = PowerCompatibilityEngine.determineEffectivePower(null, null)
        
        assertEquals(PowerCompatibility.UNKNOWN, result.status)
        assertNull(result.vehiclePowerKw)
        assertNull(result.stationPowerKw)
        assertNull(result.effectivePowerKw)
    }

    @Test
    fun testZeroPowerIncompatible() {
        val result = PowerCompatibilityEngine.determineEffectivePower(0.0, 50.0)
        
        assertEquals(PowerCompatibility.INCOMPATIBLE, result.status)
        assertEquals(0.0, result.vehiclePowerKw!!, 0.001)
        assertEquals(50.0, result.stationPowerKw!!, 0.001)
        assertEquals(0.0, result.effectivePowerKw!!, 0.001)
    }

    @Test
    fun testNegativePowerIncompatible() {
        val result = PowerCompatibilityEngine.determineEffectivePower(-10.0, 50.0)
        
        assertEquals(PowerCompatibility.INCOMPATIBLE, result.status)
        assertEquals(-10.0, result.vehiclePowerKw!!, 0.001)
        assertEquals(50.0, result.stationPowerKw!!, 0.001)
        assertEquals(-10.0, result.effectivePowerKw!!, 0.001)
    }

    @Test
    fun testIsCompatibleWithKnownValues() {
        val result = PowerCompatibilityEngine.isCompatible(50.0, 100.0)
        
        assertTrue(result)
    }

    @Test
    fun testIsCompatibleWithUnknownValues() {
        val result = PowerCompatibilityEngine.isCompatible(null, 100.0)
        
        assertFalse(result)
    }

    @Test
    fun testIsCompatibleWithZeroValues() {
        val result = PowerCompatibilityEngine.isCompatible(0.0, 100.0)
        
        assertFalse(result)
    }
}