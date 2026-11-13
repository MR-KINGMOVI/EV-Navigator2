package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoCoordinateTest {
    
    @Test
    fun `test valid coordinate creation`() {
        val coord = GeoCoordinate(35.6895, 139.6917)
        
        assertEquals(35.6895, coord.latitude)
        assertEquals(139.6917, coord.longitude)
        assertNull(coord.precision)
        assertNull(coord.source)
        assertNull(coord.sourceReference)
    }
    
    @Test
    fun `test coordinate with precision`() {
        val coord = GeoCoordinate(35.6895, 139.6917, 10.0, "GPS", "12345")
        
        assertEquals(35.6895, coord.latitude)
        assertEquals(139.6917, coord.longitude)
        assertEquals(10.0, coord.precision)
        assertEquals("GPS", coord.source)
        assertEquals("12345", coord.sourceReference)
    }
    
    @Test
    fun `test coordinate string representation`() {
        val coord = GeoCoordinate(35.6895, 139.6917)
        val str = coord.toCoordinateString()
        
        assertTrue(str.contains("35.689500"))
        assertTrue(str.contains("139.691700"))
    }
    
    @Test
    fun `test valid latitude range`() {
        // Test boundary values
        val coord1 = GeoCoordinate(90.0, 0.0)
        val coord2 = GeoCoordinate(-90.0, 0.0)
        
        assertTrue(coord1.isValid)
        assertTrue(coord2.isValid)
    }
    
    @Test
    fun `test valid longitude range`() {
        // Test boundary values
        val coord1 = GeoCoordinate(0.0, 180.0)
        val coord2 = GeoCoordinate(0.0, -180.0)
        
        assertTrue(coord1.isValid)
        assertTrue(coord2.isValid)
    }
    
    @Test
    fun `test invalid latitude`() {
        try {
            GeoCoordinate(95.0, 0.0)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Latitude must be between -90 and 90 degrees", e.message)
        }
    }
    
    @Test
    fun `test invalid longitude`() {
        try {
            GeoCoordinate(0.0, 190.0)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Longitude must be between -180 and 180 degrees", e.message)
        }
    }
    
    @Test
    fun `test invalid precision`() {
        try {
            GeoCoordinate(0.0, 0.0, -5.0)
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Precision cannot be negative", e.message)
        }
    }
    
    @Test
    fun `test coordinate validation`() {
        val validCoord = GeoCoordinate(35.6895, 139.6917)
        assertTrue(validCoord.isValid)
        
        val invalidLatCoord = try { GeoCoordinate(95.0, 0.0) } catch (e: IllegalArgumentException) { null }
        assertFalse(invalidLatCoord?.isValid ?: false)
        
        val invalidLonCoord = try { GeoCoordinate(0.0, 190.0) } catch (e: IllegalArgumentException) { null }
        assertFalse(invalidLonCoord?.isValid ?: false)
    }
}