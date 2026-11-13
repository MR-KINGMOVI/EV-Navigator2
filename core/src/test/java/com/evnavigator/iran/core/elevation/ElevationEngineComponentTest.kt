package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.CoordinateValidator
import com.evnavigator.iran.core.domain.DistanceCalculator
import com.evnavigator.iran.core.elevation.ElevationSampler
import com.evnavigator.iran.core.elevation.SamplingConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for elevation engine components
 */

class ElevationEngineComponentTest {
    
    @Test
    fun testCoordinateValidator() = runTest {
        // Test valid coordinates
        val validCoord1 = GeoCoordinate(35.6892, 51.3890)
        val validCoord2 = GeoCoordinate(-90.0, -180.0)
        val validCoord3 = GeoCoordinate(90.0, 180.0)
        
        assertTrue("Valid coordinates should be accepted", CoordinateValidator.isValidCoordinate(validCoord1))
        assertTrue("Valid coordinates should be accepted", CoordinateValidator.isValidCoordinate(validCoord2))
        assertTrue("Valid coordinates should be accepted", CoordinateValidator.isValidCoordinate(validCoord3))
        
        // Test invalid coordinates
        val invalidLat = GeoCoordinate(91.0, 51.3890) // Latitude out of range
        val invalidLon = GeoCoordinate(35.6892, 181.0) // Longitude out of range
        val invalidNaN = GeoCoordinate(Double.NaN, 51.3890)
        
        assertFalse("Invalid latitude should be rejected", CoordinateValidator.isValidCoordinate(invalidLat))
        assertFalse("Invalid longitude should be rejected", CoordinateValidator.isValidCoordinate(invalidLon))
        assertFalse("NaN coordinates should be rejected", CoordinateValidator.isValidCoordinate(invalidNaN))
    }
    
    @Test
    fun testDistanceCalculator() = runTest {
        // Test identical points
        val coord1 = GeoCoordinate(35.6892, 51.3890)
        val coord2 = GeoCoordinate(35.6892, 51.3890)
        
        assertEquals("Identical points should have zero distance", 0.0, DistanceCalculator.calculateDistance(coord1, coord2), 0.01)
        
        // Test with a known approximate distance
        val coord3 = GeoCoordinate(35.6892, 51.3890)
        val coord4 = GeoCoordinate(35.7000, 51.4000)
        
        val distance = DistanceCalculator.calculateDistance(coord3, coord4)
        assertTrue("Distance should be positive", distance > 0)
        assertTrue("Distance should be reasonable (approx 12km)", distance > 1000 && distance < 2000)
    }
    
    @Test
    fun testElevationSampler() = runTest {
        // Test sampling with a simple route
        val coordinates = listOf(
            GeoCoordinate(35.6892, 51.3890), // Tehran
            GeoCoordinate(35.7000, 51.4000), // Near Tehran
            GeoCoordinate(35.7100, 51.4100)  // Further
        )
        
        val config = SamplingConfig(targetSpacingMeters = 500.0)
        val result = ElevationSampler.sampleRoute(coordinates, config)
        
        // Should have at least the start and end points
        assertTrue("Should have sampled coordinates", result.sampledCoordinates.isNotEmpty())
        assertTrue("Should preserve first point", result.sampledCoordinates.first() == coordinates.first())
        assertTrue("Should preserve last point", result.sampledCoordinates.last() == coordinates.last())
        
        // Check that distances are non-decreasing
        for (i in 1 until result.cumulativeDistances.size) {
            assertTrue("Cumulative distances should be non-decreasing", 
                result.cumulativeDistances[i] >= result.cumulativeDistances[i-1])
        }
    }
    
    @Test
    fun testSamplingEdgeCases() = runTest {
        // Test empty route
        val emptyResult = ElevationSampler.sampleRoute(emptyList())
        assertTrue("Empty route should return empty results", emptyResult.sampledCoordinates.isEmpty())
        
        // Test single point
        val singlePoint = listOf(GeoCoordinate(35.6892, 51.3890))
        val singleResult = ElevationSampler.sampleRoute(singlePoint)
        assertEquals("Single point should return one sampled coordinate", 1, singleResult.sampledCoordinates.size)
        assertEquals("Single point should have zero distance", 0.0, singleResult.cumulativeDistances[0], 0.01)
        
        // Test two points
        val twoPoints = listOf(
            GeoCoordinate(35.6892, 51.3890),
            GeoCoordinate(35.7000, 51.4000)
        )
        val twoResult = ElevationSampler.sampleRoute(twoPoints, SamplingConfig(targetSpacingMeters = 2000.0))
        assertEquals("Two points should return two sampled coordinates", 2, twoResult.sampledCoordinates.size)
        assertTrue("Should preserve both points", 
            twoResult.sampledCoordinates.containsAll(twoPoints))
    }
}