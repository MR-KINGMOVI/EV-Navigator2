package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint

/**
 * Utility class for interpolating elevation data between known points
 */
object ElevationInterpolator {
    
    /**
     * Performs linear interpolation between two elevation points
     * @param startPoint The starting elevation point
     * @param endPoint The ending elevation point
     * @param targetDistance The distance from start where we want to interpolate
     * @return Interpolated elevation point or null if interpolation is not possible
     */
    fun interpolateBetweenPoints(
        startPoint: ElevationPoint,
        endPoint: ElevationPoint,
        targetDistance: Double
    ): ElevationPoint? {
        // Validate that we have valid points with known elevations
        if (!startPoint.isValid() || !endPoint.isValid()) {
            return null
        }
        
        // Check that the target distance is within bounds
        val startDistance = startPoint.distanceFromStart.toDouble()
        val endDistance = endPoint.distanceFromStart.toDouble()
        
        if (targetDistance < startDistance || targetDistance > endDistance) {
            return null
        }
        
        // Handle edge cases
        if (startDistance == endDistance) {
            // Same distance - return the first point
            return startPoint.copy(
                elevationMeters = startPoint.elevationMeters,
                quality = ElevationQuality.INTERPOLATED,
                provenance = ElevationProvenance.SYNTHETIC
            )
        }
        
        // Perform linear interpolation
        val distanceRatio = (targetDistance - startDistance) / (endDistance - startDistance)
        val interpolatedElevation = startPoint.elevationMeters + 
                                    distanceRatio * (endPoint.elevationMeters - startPoint.elevationMeters)
        
        // Create interpolated point with appropriate quality and provenance
        return ElevationPoint(
            coordinate = interpolateCoordinate(startPoint.coordinate, endPoint.coordinate, distanceRatio),
            elevationMeters = interpolatedElevation,
            accuracyMeters = null, // Accuracy not available for interpolation
            quality = ElevationQuality.INTERPOLATED,
            provenance = ElevationProvenance.SYNTHETIC,
            distanceFromStart = targetDistance.toFloat()
        )
    }
    
    /**
     * Interpolates elevation values along a route to fill in gaps
     * @param points List of elevation points (should be sorted by distance)
     * @return List of elevation points with interpolated values where needed
     */
    fun interpolateElevationProfile(points: List<ElevationPoint>): List<ElevationPoint> {
        if (points.isEmpty()) return emptyList()
        
        val result = mutableListOf<ElevationPoint>()
        var i = 0
        
        while (i < points.size) {
            // Add current point to result
            result.add(points[i])
            
            // Check if next point exists and is valid
            if (i + 1 < points.size && points[i + 1].isValid()) {
                val currentPoint = points[i]
                val nextPoint = points[i + 1]
                
                // If there's a gap, interpolate between them
                if (currentPoint.distanceFromStart < nextPoint.distanceFromStart) {
                    val distanceGap = nextPoint.distanceFromStart - currentPoint.distanceFromStart
                    
                    // Only interpolate if the gap is reasonable (not too large)
                    if (distanceGap > 10.0) { // 10 meters minimum gap to consider interpolation
                        // Add interpolated points between these two points
                        val numInterpolationPoints = maxOf(1, (distanceGap / 200.0).toInt()) // Interpolate every 200m
                        
                        for (j in 1..numInterpolationPoints) {
                            val ratio = j.toDouble() / (numInterpolationPoints + 1)
                            val targetDistance = currentPoint.distanceFromStart + ratio * distanceGap
                            
                            val interpolatedPoint = interpolateBetweenPoints(currentPoint, nextPoint, targetDistance)
                            if (interpolatedPoint != null) {
                                result.add(interpolatedPoint)
                            }
                        }
                    }
                }
            }
            
            i++
        }
        
        return result
    }
    
    /**
     * Interpolates coordinate between two points based on a ratio (0.0 to 1.0)
     */
    private fun interpolateCoordinate(
        start: GeoCoordinate,
        end: GeoCoordinate,
        ratio: Double
    ): GeoCoordinate {
        if (ratio <= 0) return start
        if (ratio >= 1) return end
        
        val interpolatedLat = start.latitude + (end.latitude - start.latitude) * ratio
        val interpolatedLon = start.longitude + (end.longitude - start.longitude) * ratio
        
        return GeoCoordinate(interpolatedLat, interpolatedLon)
    }
}