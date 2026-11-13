package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StationLocationTest {
    
    @Test
    fun `test minimal station location`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        val location = StationLocation.createMinimal(coordinate)
        
        assertEquals(coordinate, location.coordinate)
        assertNull(location.address)
        assertNull(location.province)
        assertNull(location.city)
        assertNull(location.locality)
        assertNull(location.road)
        assertNull(location.postalCode)
    }
    
    @Test
    fun `test station location with address`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        val location = StationLocation.createWithAddress(
            coordinate = coordinate,
            address = "123 Main Street",
            province = "Tokyo",
            city = "Tokyo",
            locality = "Shinjuku",
            road = "Yamanote Line"
        )
        
        assertEquals(coordinate, location.coordinate)
        assertEquals("123 Main Street", location.address)
        assertEquals("Tokyo", location.province)
        assertEquals("Tokyo", location.city)
        assertEquals("Shinjuku", location.locality)
        assertEquals("Yamanote Line", location.road)
        assertNull(location.postalCode)
    }
    
    @Test
    fun `test human readable description`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        val location = StationLocation.createWithAddress(
            coordinate = coordinate,
            address = "123 Main Street",
            province = "Tokyo",
            city = "Tokyo"
        )
        
        val description = location.getHumanReadableDescription()
        assertTrue(description.contains("123 Main Street"))
        assertTrue(description.contains("Tokyo"))
    }
    
    @Test
    fun `test human readable description with no address`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        val location = StationLocation.createMinimal(coordinate)
        
        val description = location.getHumanReadableDescription()
        assertTrue(description.contains("Location at"))
        assertTrue(description.contains("35.689500"))
        assertTrue(description.contains("139.691700"))
    }
    
    @Test
    fun `test has address`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        
        // Location with address
        val locationWithAddress = StationLocation.createWithAddress(
            coordinate = coordinate,
            address = "123 Main Street"
        )
        assertTrue(locationWithAddress.hasAddress)
        
        // Location without address
        val locationWithoutAddress = StationLocation.createMinimal(coordinate)
        assertFalse(locationWithoutAddress.hasAddress)
    }
    
    @Test
    fun `test has geographic context`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        
        // Location with context
        val locationWithContext = StationLocation.createWithAddress(
            coordinate = coordinate,
            address = "123 Main Street",
            province = "Tokyo"
        )
        assertTrue(locationWithContext.hasGeographicContext)
        
        // Location without context
        val locationWithoutContext = StationLocation.createMinimal(coordinate)
        assertFalse(locationWithoutContext.hasGeographicContext)
    }
    
    @Test
    fun `test invalid coordinate`() {
        try {
            StationLocation(
                coordinate = GeoCoordinate(95.0, 0.0) // Invalid latitude
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message == "Coordinate must be within geographic bounds" || e.message == "Latitude must be between -90 and 90 degrees")
        }
    }
    
    @Test
    fun `test invalid address field`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        
        try {
            StationLocation(
                coordinate = coordinate,
                address = ""
            )
            throw AssertionError("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Address cannot be blank if provided", e.message)
        }
    }
    
    @Test
    fun `test location context`() {
        val coordinate = GeoCoordinate(35.6895, 139.6917)
        val location = StationLocation(
            coordinate = coordinate,
            locationContext = LocationContext.URBAN
        )
        
        assertEquals(LocationContext.URBAN, location.locationContext)
    }
}