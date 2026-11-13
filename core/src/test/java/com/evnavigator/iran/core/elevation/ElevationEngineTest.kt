package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ElevationEngineImpl
 */
class ElevationEngineTest {
    
    private val elevationEngine = ElevationEngineImpl(TestElevationProvider())
    private val unconfiguredEngine = ElevationEngineImpl(null)
    
    @Test
    fun testGetElevationAt() = runTest {
        // Test basic elevation calculation with configured test provider
        val coordinate = GeoCoordinate(35.6892, 51.3890) // Tehran coordinates
        
        val elevation = elevationEngine.getElevationAt(coordinate)
        
        // Should return a value within reasonable bounds for Iran
        assertTrue("Elevation should be positive", elevation > -200)
        assertTrue("Elevation should not exceed Mount Damavand height", elevation <= 5670)
    }

    @Test
    fun testMissingProviderEmitsUnknownOrNaN() = runTest {
        val coordinate = GeoCoordinate(35.6892, 51.3890)
        val elevation = unconfiguredEngine.getElevationAt(coordinate)
        assertTrue("Unconfigured engine must return NaN for missing DEM, never fake numbers", elevation.isNaN())

        val points = unconfiguredEngine.calculateElevationProfile(listOf(coordinate, GeoCoordinate(35.7000, 51.4000))).toList()
        assertTrue("Points must not be empty", points.isNotEmpty())
        for (pt in points) {
            assertFalse("Point must not be marked valid when DEM is missing", pt.isValid())
            assertTrue("Elevation value must be NaN when DEM is missing", pt.elevationMeters.isNaN())
        }
    }
    
    @Test
    fun testCalculateElevationGainLoss() = runTest {
        // Test with simple straight line (should have minimal gain/loss)
        val coordinates = listOf(
            GeoCoordinate(35.6892, 51.3890), // Tehran
            GeoCoordinate(35.7000, 51.4000)  // Near Tehran
        )
        
        val gainLoss = elevationEngine.calculateElevationGainLoss(coordinates)
        
        // Should return reasonable values
        assertTrue("Total gain should be non-negative", gainLoss.totalGain >= 0)
        assertTrue("Total loss should be non-negative", gainLoss.totalLoss >= 0)
    }
    
    @Test
    fun testCalculateElevationProfile() = runTest {
        // Test with a simple route
        val coordinates = listOf(
            GeoCoordinate(35.6892, 51.3890), // Tehran
            GeoCoordinate(35.7000, 51.4000)  // Near Tehran
        )
        
        val profile = elevationEngine.calculateElevationProfile(coordinates)
        
        // The flow should emit elevation points
        var count = 0
        profile.collect { point ->
            count++
            assertNotNull("Point should not be null", point)
            assertNotNull("Coordinate should not be null", point.coordinate)
            assertTrue("Elevation should be within bounds", 
                point.elevationMeters > -200 && point.elevationMeters <= 5670)
        }
        
        assertTrue("Should have emitted at least 2 elevation points", count >= 2)
    }
}
