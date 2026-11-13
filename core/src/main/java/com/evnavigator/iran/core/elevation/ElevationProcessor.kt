package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Configuration for elevation processing
 */
data class ElevationProcessingConfig(
    /**
     * Maximum allowable elevation change per meter (meters/meter)
     * Used for outlier detection
     */
    val maxElevationChangePerMeter: Double = 5.0, // 5 meters per meter is very steep
    
    /**
     * Minimum distance between points to consider for outlier detection (meters)
     */
    val minDistanceForOutlierDetection: Double = 10.0,
    
    /**
     * Whether to perform smoothing on elevation data
     */
    val enableSmoothing: Boolean = false,
    
    /**
     * Smoothing window size (number of points to consider)
     */
    val smoothingWindowSize: Int = 3,
    
    /**
     * Threshold for detecting significant elevation changes that might be outliers
     */
    val outlierThresholdMeters: Double = 100.0 // 100 meters elevation change in a short distance
)

/**
 * Result of elevation processing operations
 */
data class ElevationProcessingResult(
    /**
     * Processed elevation points
     */
    val points: List<ElevationPoint>,
    
    /**
     * Processing statistics
     */
    val statistics: ElevationProcessingStats,
    
    /**
     * List of detected outliers
     */
    val outliers: List<OutlierInfo>
)

/**
 * Statistics about elevation processing
 */
data class ElevationProcessingStats(
    /**
     * Total number of points processed
     */
    val totalPoints: Int,
    
    /**
     * Number of points that were interpolated
     */
    val interpolatedPoints: Int,
    
    /**
     * Number of points that were flagged as outliers
     */
    val outlierCount: Int,
    
    /**
     * Minimum elevation in the profile
     */
    val minElevation: Double,
    
    /**
     * Maximum elevation in the profile
     */
    val maxElevation: Double,
    
    /**
     * Average elevation in the profile
     */
    val averageElevation: Double
)

/**
 * Information about detected outliers
 */
data class OutlierInfo(
    /**
     * The coordinate of the outlier
     */
    val coordinate: GeoCoordinate,
    
    /**
     * The elevation value that was flagged as an outlier
     */
    val elevation: Double,
    
    /**
     * Reason for flagging as outlier
     */
    val reason: String
)

/**
 * Utility class for processing elevation data
 */
object ElevationProcessor {
    
    /**
     * Processes elevation data with outlier detection and optional smoothing
     * @param points List of elevation points to process
     * @param config Processing configuration
     * @return Processed elevation points with statistics and outliers
     */
    fun processElevationData(
        points: List<ElevationPoint>,
        config: ElevationProcessingConfig = ElevationProcessingConfig()
    ): ElevationProcessingResult {
        if (points.isEmpty()) {
            return ElevationProcessingResult(
                points = emptyList(),
                statistics = ElevationProcessingStats(
                    totalPoints = 0,
                    interpolatedPoints = 0,
                    outlierCount = 0,
                    minElevation = Double.NaN,
                    maxElevation = Double.NaN,
                    averageElevation = Double.NaN
                ),
                outliers = emptyList()
            )
        }
        
        // Step 1: Detect and flag outliers
        val (processedPoints, outliers) = detectAndHandleOutliers(points, config)
        
        // Step 2: Apply smoothing if enabled
        val finalPoints = if (config.enableSmoothing) {
            applySmoothing(processedPoints, config.smoothingWindowSize)
        } else {
            processedPoints
        }
        
        // Calculate statistics
        val stats = calculateStatistics(finalPoints)
        
        return ElevationProcessingResult(
            points = finalPoints,
            statistics = stats,
            outliers = outliers
        )
    }
    
    /**
     * Detects and handles outliers in elevation data
     * @param points List of elevation points
     * @param config Processing configuration
     * @return Pair of (processed points, list of outliers)
     */
    private fun detectAndHandleOutliers(
        points: List<ElevationPoint>,
        config: ElevationProcessingConfig
    ): Pair<List<ElevationPoint>, List<OutlierInfo>> {
        val outliers = mutableListOf<OutlierInfo>()
        val processedPoints = mutableListOf<ElevationPoint>()
        
        // First pass - mark points as valid or invalid based on quality
        for (point in points) {
            if (!point.isValid()) {
                processedPoints.add(point)
                continue
            }
            
            // Check for potential outliers
            val isOutlier = checkForOutlier(point, points, config)
            if (isOutlier) {
                // Flag as invalid/unknown instead of keeping potentially erroneous data
                val outlierInfo = OutlierInfo(
                    coordinate = point.coordinate,
                    elevation = point.elevationMeters,
                    reason = "Significant elevation change detected"
                )
                outliers.add(outlierInfo)
                
                // Replace with unknown elevation to avoid using questionable data
                processedPoints.add(ElevationPoint.unknown(point.coordinate))
            } else {
                processedPoints.add(point)
            }
        }
        
        return Pair(processedPoints, outliers)
    }
    
    /**
     * Checks if a point is an outlier based on nearby points
     * @param point The point to check
     * @param allPoints All elevation points in the profile
     * @param config Processing configuration
     * @return true if the point is flagged as an outlier, false otherwise
     */
    private fun checkForOutlier(
        point: ElevationPoint,
        allPoints: List<ElevationPoint>,
        config: ElevationProcessingConfig
    ): Boolean {
        if (!point.isValid()) return false
        
        // Find nearby points (within a reasonable distance)
        val nearbyPoints = allPoints.filter { 
            it.isValid() && 
            it.distanceFromStart != point.distanceFromStart &&
            abs(it.distanceFromStart - point.distanceFromStart) <= config.minDistanceForOutlierDetection * 10
        }.sortedBy { abs(it.distanceFromStart - point.distanceFromStart) }
        
        if (nearbyPoints.isEmpty()) return false
        
        // Check the elevation change between this point and nearby points
        for (nearbyPoint in nearbyPoints.take(3)) { // Check first few nearest points
            val distance = abs(point.distanceFromStart - nearbyPoint.distanceFromStart)
            
            // Skip if distance is too small to be meaningful
            if (distance < config.minDistanceForOutlierDetection) continue
            
            val elevationDiff = abs(point.elevationMeters - nearbyPoint.elevationMeters)
            val changePerMeter = elevationDiff / distance
            
            // If elevation changes too rapidly, it's likely an outlier
            if (changePerMeter > config.maxElevationChangePerMeter) {
                return true
            }
            
            // Also check for absolute elevation jumps
            if (elevationDiff > config.outlierThresholdMeters) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Applies smoothing to elevation data using a simple moving average
     * @param points List of elevation points to smooth
     * @param windowSize Size of the smoothing window
     * @return Smoothed elevation points
     */
    private fun applySmoothing(
        points: List<ElevationPoint>,
        windowSize: Int
    ): List<ElevationPoint> {
        if (points.isEmpty() || windowSize <= 1) return points
        
        val smoothedPoints = mutableListOf<ElevationPoint>()
        
        for ((index, point) in points.withIndex()) {
            if (!point.isValid()) {
                smoothedPoints.add(point)
                continue
            }
            
            // Calculate average elevation within the window
            val startIndex = max(0, index - windowSize / 2)
            val endIndex = min(points.size - 1, index + windowSize / 2)
            
            val windowPoints = points.slice(startIndex..endIndex).filter { it.isValid() }
            
            if (windowPoints.isEmpty()) {
                smoothedPoints.add(point)
                continue
            }
            
            // Calculate average elevation in the window
            val avgElevation = windowPoints.map { it.elevationMeters }.average()
            
            // Create a new point with the smoothed elevation
            val smoothedPoint = point.copy(
                elevationMeters = avgElevation,
                quality = if (point.isInterpolated()) ElevationQuality.INTERPOLATED else ElevationQuality.KNOWN
            )
            
            smoothedPoints.add(smoothedPoint)
        }
        
        return smoothedPoints
    }
    
    /**
     * Calculates statistics for processed elevation data
     * @param points List of elevation points
     * @return Statistics about the elevation data
     */
    private fun calculateStatistics(points: List<ElevationPoint>): ElevationProcessingStats {
        if (points.isEmpty()) {
            return ElevationProcessingStats(
                totalPoints = 0,
                interpolatedPoints = 0,
                outlierCount = 0,
                minElevation = Double.NaN,
                maxElevation = Double.NaN,
                averageElevation = Double.NaN
            )
        }
        
        val validPoints = points.filter { it.isValid() }
        
        if (validPoints.isEmpty()) {
            return ElevationProcessingStats(
                totalPoints = points.size,
                interpolatedPoints = points.count { it.isInterpolated() },
                outlierCount = 0,
                minElevation = Double.NaN,
                maxElevation = Double.NaN,
                averageElevation = Double.NaN
            )
        }
        
        val minElevation = validPoints.minOf { it.elevationMeters }
        val maxElevation = validPoints.maxOf { it.elevationMeters }
        val avgElevation = validPoints.map { it.elevationMeters }.average()
        
        return ElevationProcessingStats(
            totalPoints = points.size,
            interpolatedPoints = points.count { it.isInterpolated() },
            outlierCount = points.size - validPoints.size,
            minElevation = minElevation,
            maxElevation = maxElevation,
            averageElevation = avgElevation
        )
    }
    
    /**
     * Identifies segments that are potentially problematic (e.g., very steep, outliers)
     * @param points List of elevation points
     * @return List of problematic segments
     */
    fun identifyProblematicSegments(points: List<ElevationPoint>): List<ProblematicSegment> {
        val problematicSegments = mutableListOf<ProblematicSegment>()
        
        if (points.size < 2) return problematicSegments
        
        // Check for steep sections and other issues
        for (i in 1 until points.size) {
            val prevPoint = points[i - 1]
            val currentPoint = points[i]
            
            if (!prevPoint.isValid() || !currentPoint.isValid()) continue
            
            val distance = abs(currentPoint.distanceFromStart - prevPoint.distanceFromStart)
            
            // Skip very short segments
            if (distance < 1.0) continue
            
            val elevationDiff = currentPoint.elevationMeters - prevPoint.elevationMeters
            val grade = (elevationDiff / distance) * 100 // Convert to percentage
            
            // Flag potentially problematic segments
            if (abs(grade) > 20.0) { // 20% grade is very steep
                problematicSegments.add(
                    ProblematicSegment(
                        startCoordinate = prevPoint.coordinate,
                        endCoordinate = currentPoint.coordinate,
                        distanceMeters = distance.toDouble(),
                        elevationChange = elevationDiff,
                        gradePercent = grade,
                        type = ProblematicSegmentType.STEEP
                    )
                )
            }
        }
        
        return problematicSegments
    }
}

/**
 * Represents a potentially problematic segment in elevation data
 */
data class ProblematicSegment(
    /**
     * Start coordinate of the segment
     */
    val startCoordinate: GeoCoordinate,
    
    /**
     * End coordinate of the segment
     */
    val endCoordinate: GeoCoordinate,
    
    /**
     * Distance of the segment in meters
     */
    val distanceMeters: Double,
    
    /**
     * Elevation change in the segment (positive for climb)
     */
    val elevationChange: Double,
    
    /**
     * Grade percentage of the segment
     */
    val gradePercent: Double,
    
    /**
     * Type of problem detected
     */
    val type: ProblematicSegmentType
)

/**
 * Types of problematic segments
 */
enum class ProblematicSegmentType {
    STEEP,      // Very steep climb/descent
    OUTLIER,    // Contains outlier data points
    JUMP        // Large elevation jump
}