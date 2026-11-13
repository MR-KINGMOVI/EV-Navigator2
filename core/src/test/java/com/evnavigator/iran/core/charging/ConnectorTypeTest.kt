package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectorTypeTest {
    
    @Test
    fun `test all connector types are defined`() {
        val allTypes = ConnectorType.values()
        assertTrue(allTypes.isNotEmpty())
        
        // Verify specific types exist
        assertTrue(allTypes.contains(ConnectorType.TYPE_2))
        assertTrue(allTypes.contains(ConnectorType.CCS_COMBO_1))
        assertTrue(allTypes.contains(ConnectorType.CCS_COMBO_2))
        assertTrue(allTypes.contains(ConnectorType.CHAdeMO))
        assertTrue(allTypes.contains(ConnectorType.GB_T_AC))
        assertTrue(allTypes.contains(ConnectorType.GB_T_DC))
        assertTrue(allTypes.contains(ConnectorType.TESLA_NACS))
        assertTrue(allTypes.contains(ConnectorType.TESLA_SUPERCHARGER))
        assertTrue(allTypes.contains(ConnectorType.OTHER))
        assertTrue(allTypes.contains(ConnectorType.UNKNOWN))
    }
    
    @Test
    fun `test connector type enumeration order`() {
        val types = ConnectorType.values()
        assertEquals(10, types.size) // Should have 10 connector types
        
        // Check specific positions
        assertEquals(ConnectorType.TYPE_2, types[0])
        assertEquals(ConnectorType.CCS_COMBO_1, types[1])
        assertEquals(ConnectorType.CCS_COMBO_2, types[2])
        assertEquals(ConnectorType.CHAdeMO, types[3])
        assertEquals(ConnectorType.GB_T_AC, types[4])
        assertEquals(ConnectorType.GB_T_DC, types[5])
        assertEquals(ConnectorType.TESLA_NACS, types[6])
        assertEquals(ConnectorType.TESLA_SUPERCHARGER, types[7])
        assertEquals(ConnectorType.OTHER, types[8])
        assertEquals(ConnectorType.UNKNOWN, types[9])
    }
    
    @Test
    fun `test specific connector types`() {
        // Test some common connector types
        assertEquals("TYPE_2", ConnectorType.TYPE_2.name)
        assertEquals("CCS_COMBO_1", ConnectorType.CCS_COMBO_1.name)
        assertEquals("CCS_COMBO_2", ConnectorType.CCS_COMBO_2.name)
        assertEquals("CHAdeMO", ConnectorType.CHAdeMO.name)
        assertEquals("GB_T_AC", ConnectorType.GB_T_AC.name)
        assertEquals("GB_T_DC", ConnectorType.GB_T_DC.name)
        assertEquals("TESLA_NACS", ConnectorType.TESLA_NACS.name)
        assertEquals("TESLA_SUPERCHARGER", ConnectorType.TESLA_SUPERCHARGER.name)
        assertEquals("OTHER", ConnectorType.OTHER.name)
        assertEquals("UNKNOWN", ConnectorType.UNKNOWN.name)
    }
}