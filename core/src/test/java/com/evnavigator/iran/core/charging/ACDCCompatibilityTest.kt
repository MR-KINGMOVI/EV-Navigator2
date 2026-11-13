package com.evnavigator.iran.core.charging

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for ACDCCompatibilityEngine
 */
class ACDCCompatibilityTest {

    @Test
    fun testCompatibleAcVehicleWithAcStation() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = true,
            vehicleIsDc = false,
            stationIsAc = true,
            stationIsDc = false
        )
        
        assertEquals(ACDCCompatibility.COMPATIBLE, result.status)
        assertTrue(result.vehicleIsAc!!)
        assertFalse(result.vehicleIsDc!!)
        assertTrue(result.stationIsAc!!)
        assertFalse(result.stationIsDc!!)
    }

    @Test
    fun testCompatibleDcVehicleWithDcStation() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = false,
            vehicleIsDc = true,
            stationIsAc = false,
            stationIsDc = true
        )
        
        assertEquals(ACDCCompatibility.COMPATIBLE, result.status)
        assertFalse(result.vehicleIsAc!!)
        assertTrue(result.vehicleIsDc!!)
        assertFalse(result.stationIsAc!!)
        assertTrue(result.stationIsDc!!)
    }

    @Test
    fun testIncompatibleVehicleWithStation() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = true,
            vehicleIsDc = false,
            stationIsAc = false,
            stationIsDc = true
        )
        
        assertEquals(ACDCCompatibility.INCOMPATIBLE, result.status)
        assertTrue(result.vehicleIsAc!!)
        assertFalse(result.vehicleIsDc!!)
        assertFalse(result.stationIsAc!!)
        assertTrue(result.stationIsDc!!)
    }

    @Test
    fun testUnknownVehicleAc() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = null,
            vehicleIsDc = true,
            stationIsAc = true,
            stationIsDc = false
        )
        
        assertEquals(ACDCCompatibility.UNKNOWN, result.status)
        assertNull(result.vehicleIsAc)
        assertTrue(result.vehicleIsDc!!)
        assertTrue(result.stationIsAc!!)
        assertFalse(result.stationIsDc!!)
    }

    @Test
    fun testUnknownStationDc() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = true,
            vehicleIsDc = false,
            stationIsAc = true,
            stationIsDc = null
        )
        
        assertEquals(ACDCCompatibility.UNKNOWN, result.status)
        assertTrue(result.vehicleIsAc!!)
        assertFalse(result.vehicleIsDc!!)
        assertTrue(result.stationIsAc!!)
        assertNull(result.stationIsDc)
    }

    @Test
    fun testBothUnknown() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = null,
            vehicleIsDc = null,
            stationIsAc = null,
            stationIsDc = null
        )
        
        assertEquals(ACDCCompatibility.UNKNOWN, result.status)
        assertNull(result.vehicleIsAc)
        assertNull(result.vehicleIsDc)
        assertNull(result.stationIsAc)
        assertNull(result.stationIsDc)
    }

    @Test
    fun testVehicleSupportsNeither() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = false,
            vehicleIsDc = false,
            stationIsAc = true,
            stationIsDc = false
        )
        
        assertEquals(ACDCCompatibility.INCOMPATIBLE, result.status)
        assertFalse(result.vehicleIsAc!!)
        assertFalse(result.vehicleIsDc!!)
        assertTrue(result.stationIsAc!!)
        assertFalse(result.stationIsDc!!)
    }

    @Test
    fun testStationSupportsNeither() {
        val result = ACDCCompatibilityEngine.determineCompatibility(
            vehicleIsAc = true,
            vehicleIsDc = false,
            stationIsAc = false,
            stationIsDc = false
        )
        
        assertEquals(ACDCCompatibility.INCOMPATIBLE, result.status)
        assertTrue(result.vehicleIsAc!!)
        assertFalse(result.vehicleIsDc!!)
        assertFalse(result.stationIsAc!!)
        assertFalse(result.stationIsDc!!)
    }

    @Test
    fun testIsCompatibleWithKnownValues() {
        val result = ACDCCompatibilityEngine.isCompatible(
            vehicleIsAc = true,
            vehicleIsDc = false,
            stationIsAc = true,
            stationIsDc = false
        )
        
        assertTrue(result)
    }

    @Test
    fun testIsCompatibleWithUnknownValues() {
        val result = ACDCCompatibilityEngine.isCompatible(
            vehicleIsAc = null,
            vehicleIsDc = true,
            stationIsAc = true,
            stationIsDc = false
        )
        
        assertFalse(result)
    }
}