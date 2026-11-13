package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargingStationIdTest {
    
    @Test
    fun `test create new charging station ID`() {
        val id = ChargingStationId.createNew()
        
        assertTrue(id.id.isNotBlank())
        assertEquals("IR", id.market)
        assertTrue(id.externalIds.isEmpty())
        assertNull(id.operatorId)
        assertNull(id.canonicalName)
    }
    
    @Test
    fun `test create from external IDs`() {
        val externalIds = mapOf(
            "operator_a" to "station_123",
            "operator_b" to "station_456"
        )
        
        val id = ChargingStationId.fromExternalIds(externalIds, "operator_123", "IR")
        
        assertTrue(id.id.isNotBlank())
        assertEquals(externalIds, id.externalIds)
        assertEquals("operator_123", id.operatorId)
        assertEquals("IR", id.market)
    }
    
    @Test
    fun `test is from same source`() {
        val externalIds1 = mapOf("operator_a" to "station_123")
        val externalIds2 = mapOf("operator_a" to "station_456")
        
        val id1 = ChargingStationId.fromExternalIds(externalIds1)
        val id2 = ChargingStationId.fromExternalIds(externalIds2)
        
        assertTrue(id1.isFromSameSource(id2))
    }
    
    @Test
    fun `test is not from same source`() {
        val externalIds1 = mapOf("operator_a" to "station_123")
        val externalIds2 = mapOf("operator_b" to "station_456")
        
        val id1 = ChargingStationId.fromExternalIds(externalIds1)
        val id2 = ChargingStationId.fromExternalIds(externalIds2)
        
        assertFalse(id1.isFromSameSource(id2))
    }
    
    @Test
    fun `test get external ID`() {
        val externalIds = mapOf("operator_a" to "station_123")
        val id = ChargingStationId.fromExternalIds(externalIds)
        
        assertEquals("station_123", id.getExternalId("operator_a"))
        assertNull(id.getExternalId("operator_b"))
    }
    
    @Test
    fun `test stable identifier`() {
        val externalIds = mapOf("operator_a" to "station_123")
        val id = ChargingStationId.fromExternalIds(externalIds, "operator_123", "IR")
        
        assertTrue(id.stableIdentifier.isNotBlank())
    }
    
    @Test
    fun `test invalid internal ID`() {
        try {
            ChargingStationId(id = "")
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Internal ID must not be blank", e.message)
        }
    }
    
    @Test
    fun `test invalid operator ID`() {
        try {
            ChargingStationId(
                id = "test_id",
                operatorId = ""
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Operator ID must not be blank if provided", e.message)
        }
    }
}