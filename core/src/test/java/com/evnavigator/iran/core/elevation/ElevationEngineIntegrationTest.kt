package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.CoordinateValidator
import com.evnavigator.iran.core.domain.DistanceCalculator
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for the elevation engine implementation
 */
class ElevationEngineIntegrationTest {
    
    @Test
    fun testCoordinateValidation() = runTest {
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
    fun testDistanceCalculation() = runTest {
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
    fun testElevationSampling() = runTest {
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
    fun testElevationInterpolation() = runTest {
        // Create test elevation points
        val point1 = ElevationPoint.known(
            coordinate = GeoCoordinate(35.6892, 51.3890),
            elevation = 1000.0,
            accuracy = 5.0,
            distanceFromStart = 0f
        )
        
        val point2 = ElevationPoint.known(
            coordinate = GeoCoordinate(35.7000, 51.4000),
            elevation = 1200.0,
            accuracy = 5.0,
            distanceFromStart = 2000f
        )
        
        // Test interpolation between these points
        val interpolated = ElevationInterpolator.interpolateBetweenPoints(point1, point2, 1000.0)
        assertNotNull("Interpolation should return a value", interpolated)
        assertTrue("Interpolated elevation should be between the two points", 
            interpolated!!.elevationMeters in 1000.0..1200.0)
        
        // Test interpolation with invalid points
        val unknownPoint = ElevationPoint.unknown(GeoCoordinate(35.7100, 51.4100))
        val result = ElevationInterpolator.interpolateBetweenPoints(point1, unknownPoint, 1000.0)
        assertNull("Interpolation should return null for invalid points", result)
    }
}