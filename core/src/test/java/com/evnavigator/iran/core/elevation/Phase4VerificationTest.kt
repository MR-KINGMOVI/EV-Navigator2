package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.elevation.ElevationQuality
import com.evnavigator.iran.core.elevation.ElevationProvenance
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify that Phase 4 implementation is structurally sound
 */
class Phase4VerificationTest {
    
    @Test
    fun testElevationPointCreation() = runTest {
        // Test creating different types of elevation points
        val coord = GeoCoordinate(35.6892, 51.3890)
        
        // Test known point
        val knownPoint = ElevationPoint.known(coord, 1200.0, 5.0, ElevationProvenance.SRTM30M)
        assertTrue("Known point should be valid", knownPoint.isValid())
        assertFalse("Known point should not be interpolated", knownPoint.isInterpolated())
        
        // Test unknown point
        val unknownPoint = ElevationPoint.unknown(coord)
        assertFalse("Unknown point should not be valid", unknownPoint.isValid())
        assertFalse("Unknown point should not be interpolated", unknownPoint.isInterpolated())
        
        // Test invalid point
        val invalidPoint = ElevationPoint.invalid(coord)
        assertFalse("Invalid point should not be valid", invalidPoint.isValid())
        assertFalse("Invalid point should not be interpolated", invalidPoint.isInterpolated())
        
        // Test interpolated point
        val interpolatedPoint = ElevationPoint.interpolated(coord, 1100.0, 10.0)
        assertTrue("Interpolated point should be valid", interpolatedPoint.isValid())
        assertTrue("Interpolated point should be marked as interpolated", interpolatedPoint.isInterpolated())
    }
    
    @Test
    fun testElevationQualityEnum() = runTest {
        // Verify all quality states are defined correctly
        val qualities = ElevationQuality.values()
        assertEquals("Should have 5 quality states", 5, qualities.size)
        
        assertTrue("Should contain UNKNOWN", qualities.contains(ElevationQuality.UNKNOWN))
        assertTrue("Should contain INVALID", qualities.contains(ElevationQuality.INVALID))
        assertTrue("Should contain KNOWN", qualities.contains(ElevationQuality.KNOWN))
        assertTrue("Should contain INTERPOLATED", qualities.contains(ElevationQuality.INTERPOLATED))
    }
    
    @Test
    fun testSamplingConfig() = runTest {
        // Test default configuration
        val config = SamplingConfig()
        assertEquals("Default target spacing should be 500.0", 500.0, config.targetSpacingMeters, 0.01)
        assertEquals("Default min spacing should be 100.0", 100.0, config.minSpacingMeters, 0.01)
        assertEquals("Default max spacing should be 2000.0", 2000.0, config.maxSpacingMeters, 0.01)
        assertTrue("Should preserve original vertices by default", config.preserveOriginalVertices)
    }
    
    @Test
    fun testElevationProcessorConfig() = runTest {
        // Test default configuration
        val config = ElevationProcessingConfig()
        assertEquals("Default max elevation change should be 5.0", 5.0, config.maxElevationChangePerMeter, 0.01)
        assertEquals("Default min distance for outlier detection should be 10.0", 10.0, config.minDistanceForOutlierDetection, 0.01)
        assertFalse("Should not enable smoothing by default", config.enableSmoothing)
        assertEquals("Default smoothing window size should be 3", 3, config.smoothingWindowSize)
    }
    
    @Test
    fun testGradeCalculationConfig() = runTest {
        // Test default configuration
        val config = GradeCalculationConfig()
        assertEquals("Default min distance for grade should be 1.0", 1.0, config.minDistanceForGrade, 0.01)
        assertFalse("Should not enable smoothing by default", config.enableSmoothing)
        assertEquals("Default smoothing window size should be 3", 3, config.smoothingWindowSize)
    }
    
    @Test
    fun testRoadProfileConfig() = runTest {
        // Test default configuration
        val config = RoadProfileConfig()
        assertEquals("Default steep grade threshold should be 10.0", 10.0, config.steepGradeThreshold, 0.01)
        assertEquals("Default min section distance should be 100.0", 100.0, config.minSectionDistance, 0.01)
        assertEquals("Default min elevation change should be 20.0", 20.0, config.minElevationChange, 0.01)
        assertEquals("Default max gap between sections should be 50.0", 50.0, config.maxGapBetweenSections, 0.01)
    }
}