package com.evnavigator.iran.core.charging

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for ConnectorCompatibilityEngine
 */
class ConnectorCompatibilityTest {

    @Test
    fun testCompatibleConnectors() {
        val result = ConnectorCompatibilityEngine.determineCompatibility(
            ConnectorType.CCS_COMBO_2,
            ConnectorType.CCS_COMBO_2
        )
        
        assertEquals(ConnectorCompatibility.COMPATIBLE, result)
    }

    @Test
    fun testIncompatibleConnectors() {
        val result = ConnectorCompatibilityEngine.determineCompatibility(
            ConnectorType.CCS_COMBO_2,
            ConnectorType.TYPE_2
        )
        
        assertEquals(ConnectorCompatibility.INCOMPATIBLE, result)
    }

    @Test
    fun testUnknownVehicleConnector() {
        val result = ConnectorCompatibilityEngine.determineCompatibility(
            ConnectorType.UNKNOWN,
            ConnectorType.CCS_COMBO_2
        )
        
        assertEquals(ConnectorCompatibility.UNKNOWN, result)
    }

    @Test
    fun testUnknownStationConnector() {
        val result = ConnectorCompatibilityEngine.determineCompatibility(
            ConnectorType.CCS_COMBO_2,
            ConnectorType.UNKNOWN
        )
        
        assertEquals(ConnectorCompatibility.UNKNOWN, result)
    }

    @Test
    fun testBothConnectorsUnknown() {
        val result = ConnectorCompatibilityEngine.determineCompatibility(
            ConnectorType.UNKNOWN,
            ConnectorType.UNKNOWN
        )
        
        assertEquals(ConnectorCompatibility.UNKNOWN, result)
    }

    @Test
    fun testMultipleVehicleConnectorsCompatible() {
        val vehicleTypes = setOf(ConnectorType.CCS_COMBO_2, ConnectorType.TYPE_2)
        val stationTypes = setOf(ConnectorType.CCS_COMBO_2, ConnectorType.GB_T_AC)
        
        val result = ConnectorCompatibilityEngine.isCompatible(vehicleTypes, stationTypes)
        
        assertTrue(result)
    }

    @Test
    fun testMultipleVehicleConnectorsIncompatible() {
        val vehicleTypes = setOf(ConnectorType.CCS_COMBO_2, ConnectorType.TYPE_2)
        val stationTypes = setOf(ConnectorType.GB_T_AC, ConnectorType.GB_T_DC)
        
        val result = ConnectorCompatibilityEngine.isCompatible(vehicleTypes, stationTypes)
        
        assertFalse(result)
    }

    @Test
    fun testMultipleVehicleConnectorsWithUnknown() {
        val vehicleTypes = setOf(ConnectorType.CCS_COMBO_2, ConnectorType.UNKNOWN)
        val stationTypes = setOf(ConnectorType.GB_T_AC, ConnectorType.GB_T_DC)
        
        val result = ConnectorCompatibilityEngine.isCompatible(vehicleTypes, stationTypes)
        
        assertFalse(result) // Since one is unknown, we can't determine compatibility
    }
}