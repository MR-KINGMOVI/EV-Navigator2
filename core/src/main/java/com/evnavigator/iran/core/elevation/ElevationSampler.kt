package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.DistanceCalculator

/**
 * Configuration for elevation sampling strategy
 */
data class SamplingConfig(
    val targetSpacingMeters: Double = 500.0, // Target spacing between samples in meters
    val minSpacingMeters: Double = 100.0,    // Minimum spacing between samples
    val maxSpacingMeters: Double = 2000.0,   // Maximum spacing between samples
    val preserveEndpoints: Boolean = true,  // Whether to always include start/end points
    val preserveOriginalVertices: Boolean = true // Whether to preserve original route vertices
)

/**
 * Result of elevation sampling operation
 */
data class SamplingResult(
    val sampledCoordinates: List<GeoCoordinate>,
    val cumulativeDistances: List<Double>
)

/**
 * Utility class for sampling routes at appropriate intervals
 */
object ElevationSampler {
    
    /**
     * Samples a route at appropriate intervals based on the configuration
     * @param coordinates List of geographic coordinates representing a route
     * @param config Sampling configuration
     * @return SamplingResult containing sampled coordinates and cumulative distances
     */
    fun sampleRoute(coordinates: List<GeoCoordinate>, config: SamplingConfig = SamplingConfig()): SamplingResult {
        if (coordinates.isEmpty()) {
            return SamplingResult(emptyList(), emptyList())
        }
        
        // If we have only one point, return it
        if (coordinates.size == 1) {
            val cumulativeDistances = listOf(0.0)
            return SamplingResult(coordinates, cumulativeDistances)
        }
        
        // Calculate cumulative distances along the route
        val cumulativeDistances = DistanceCalculator.calculateCumulativeDistances(coordinates)
        val totalDistance = cumulativeDistances.last()
        
        // If total distance is very small, just return original points
        if (totalDistance < config.minSpacingMeters) {
            return SamplingResult(coordinates, cumulativeDistances)
        }
        
        // Calculate number of samples needed based on target spacing
        val numSamples = maxOf(2, (totalDistance / config.targetSpacingMeters).toInt())
        
        // Generate sampled coordinates
        val sampledCoordinates = mutableListOf<GeoCoordinate>()
        
        // Always preserve endpoints if requested
        if (config.preserveEndpoints && coordinates.isNotEmpty()) {
            sampledCoordinates.add(coordinates.first())
        }
        
        // Sample at regular intervals
        val stepSize = totalDistance / (numSamples - 1)
        
        for (i in 1 until numSamples - 1) {
            val targetDistance = i * stepSize
            
            // Find the segment that contains this distance
            val segmentIndex = findSegmentContainingDistance(cumulativeDistances, targetDistance)
            
            if (segmentIndex >= 0 && segmentIndex < coordinates.size - 1) {
                val segmentStart = cumulativeDistances[segmentIndex]
                val segmentEnd = cumulativeDistances[segmentIndex + 1]
                
                // Calculate position within the segment
                val segmentLength = segmentEnd - segmentStart
                val positionInSegment = if (segmentLength > 0) (targetDistance - segmentStart) / segmentLength else 0.0
                
                // Interpolate to get the coordinate at this distance
                val startCoord = coordinates[segmentIndex]
                val endCoord = coordinates[segmentIndex + 1]
                
                val interpolatedCoord = interpolateCoordinate(startCoord, endCoord, positionInSegment)
                sampledCoordinates.add(interpolatedCoord)
            }
        }
        
        // Always preserve endpoints if requested (add last point)
        if (config.preserveEndpoints && coordinates.isNotEmpty()) {
            sampledCoordinates.add(coordinates.last())
        }
        
        // Remove duplicates while preserving order
        val uniqueCoordinates = sampledCoordinates.distinct()
        
        // Recalculate cumulative distances for the sampled route
        val finalCumulativeDistances = DistanceCalculator.calculateCumulativeDistances(uniqueCoordinates)
        
        return SamplingResult(uniqueCoordinates, finalCumulativeDistances)
    }
    
    /**
     * Finds which segment contains a given distance along the route
     */
    private fun findSegmentContainingDistance(cumulativeDistances: List<Double>, targetDistance: Double): Int {
        for (i in 0 until cumulativeDistances.size - 1) {
            if (cumulativeDistances[i] <= targetDistance && cumulativeDistances[i + 1] >= targetDistance) {
                return i
            }
        }
        return -1 // Not found
    }
    
    /**
     * Interpolates between two coordinates based on a position parameter (0.0 to 1.0)
     */
    private fun interpolateCoordinate(start: GeoCoordinate, end: GeoCoordinate, position: Double): GeoCoordinate {
        if (position <= 0) return start
        if (position >= 1) return end
        
        // Simple linear interpolation for coordinates
        val interpolatedLat = start.latitude + (end.latitude - start.latitude) * position
        val interpolatedLon = start.longitude + (end.longitude - start.longitude) * position
        
        return GeoCoordinate(interpolatedLat, interpolatedLon)
    }
}