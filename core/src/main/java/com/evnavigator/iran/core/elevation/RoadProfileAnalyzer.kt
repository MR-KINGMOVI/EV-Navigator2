package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Configuration for road profile analysis
 */
data class RoadProfileConfig(
    /**
     * Minimum grade percentage to be considered steep (climb or descent)
     */
    val steepGradeThreshold: Double = 10.0,
    
    /**
     * Minimum distance for a section to be considered significant (meters)
     */
    val minSectionDistance: Double = 100.0,
    
    /**
     * Minimum elevation change for a section to be considered significant (meters)
     */
    val minElevationChange: Double = 20.0,
    
    /**
     * Maximum gap between sections before they're considered separate (meters)
     */
    val maxGapBetweenSections: Double = 50.0
)

/**
 * Result of road profile analysis
 */
data class RoadProfileResult(
    /**
     * The original elevation points processed
     */
    val elevationPoints: List<ElevationPoint>,
    
    /**
     * Road profile statistics
     */
    val statistics: RoadProfileStatistics,
    
    /**
     * Steep climb sections
     */
    val steepClimbSections: List<SteepSection>,
    
    /**
     * Steep descent sections
     */
    val steepDescentSections: List<SteepSection>,
    
    /**
     * All significant sections (climbs and descents)
     */
    val allSignificantSections: List<SteepSection>
)

/**
 * Statistics about the road profile
 */
data class RoadProfileStatistics(
    /**
     * Total distance of the route in meters
     */
    val totalDistance: Double,
    
    /**
     * Elevation at start of route
     */
    val startElevation: Double,
    
    /**
     * Elevation at end of route
     */
    val endElevation: Double,
    
    /**
     * Minimum elevation along the route
     */
    val minElevation: Double,
    
    /**
     * Maximum elevation along the route
     */
    val maxElevation: Double,
    
    /**
     * Total ascent in meters
     */
    val totalAscent: Double,
    
    /**
     * Total descent in meters
     */
    val totalDescent: Double,
    
    /**
     * Net elevation change (end - start)
     */
    val netElevationChange: Double,
    
    /**
     * Average grade percentage
     */
    val averageGrade: Double,
    
    /**
     * Maximum uphill grade percentage
     */
    val maxUphillGrade: Double,
    
    /**
     * Maximum downhill grade percentage
     */
    val maxDownhillGrade: Double
)

/**
 * Represents a steep section in the road profile
 */
data class SteepSection(
    /**
     * Start coordinate of the section
     */
    val startCoordinate: GeoCoordinate,
    
    /**
     * End coordinate of the section
     */
    val endCoordinate: GeoCoordinate,
    
    /**
     * Start distance along the route (meters)
     */
    val startDistance: Double,
    
    /**
     * End distance along the route (meters)
     */
    val endDistance: Double,
    
    /**
     * Distance of the section in meters
     */
    val distanceMeters: Double,
    
    /**
     * Elevation change in the section (positive for climb)
     */
    val elevationChange: Double,
    
    /**
     * Average grade percentage for the section
     */
    val averageGradePercent: Double,
    
    /**
     * Maximum grade percentage in the section
     */
    val maxGradePercent: Double,
    
    /**
     * Section type (climb or descent)
     */
    val isClimb: Boolean,
    
    /**
     * Indicates if this section is considered steep
     */
    val isSteep: Boolean = true
)

/**
 * Utility class for analyzing road profiles from elevation data
 */
object RoadProfileAnalyzer {
    
    /**
     * Analyzes a route's elevation profile to generate road profile information
     * @param points List of elevation points along the route
     * @param config Analysis configuration
     * @return Road profile analysis result
     */
    fun analyzeRoadProfile(
        points: List<ElevationPoint>,
        config: RoadProfileConfig = RoadProfileConfig()
    ): RoadProfileResult {
        if (points.isEmpty()) {
            return RoadProfileResult(
                elevationPoints = emptyList(),
                statistics = RoadProfileStatistics(
                    totalDistance = 0.0,
                    startElevation = Double.NaN,
                    endElevation = Double.NaN,
                    minElevation = Double.NaN,
                    maxElevation = Double.NaN,
                    totalAscent = 0.0,
                    totalDescent = 0.0,
                    netElevationChange = 0.0,
                    averageGrade = 0.0,
                    maxUphillGrade = 0.0,
                    maxDownhillGrade = 0.0
                ),
                steepClimbSections = emptyList(),
                steepDescentSections = emptyList(),
                allSignificantSections = emptyList()
            )
        }
        
        // Get elevation data and calculate basic statistics
        val stats = calculateBasicStatistics(points)
        
        // Calculate grade information
        val gradesResult = GradeCalculator.calculateGrades(points)
        
        // Identify steep sections
        val (steepClimbSections, steepDescentSections) = identifySteepSections(
            points, 
            gradesResult.grades, 
            config
        )
        
        // Merge adjacent sections if they are close together
        val mergedClimbSections = mergeNearbySections(steepClimbSections, config.maxGapBetweenSections)
        val mergedDescentSections = mergeNearbySections(steepDescentSections, config.maxGapBetweenSections)
        
        val allSections = (mergedClimbSections + mergedDescentSections).sortedBy { it.startDistance }
        
        // Create final result
        return RoadProfileResult(
            elevationPoints = points,
            statistics = stats.copy(
                averageGrade = gradesResult.statistics.averageGrade,
                maxUphillGrade = gradesResult.statistics.maxGrade,
                maxDownhillGrade = gradesResult.statistics.minGrade
            ),
            steepClimbSections = mergedClimbSections,
            steepDescentSections = mergedDescentSections,
            allSignificantSections = allSections
        )
    }
    
    /**
     * Calculates basic statistics for the elevation profile
     * @param points List of elevation points
     * @return Road profile statistics
     */
    private fun calculateBasicStatistics(points: List<ElevationPoint>): RoadProfileStatistics {
        if (points.isEmpty()) {
            return RoadProfileStatistics(
                totalDistance = 0.0,
                startElevation = Double.NaN,
                endElevation = Double.NaN,
                minElevation = Double.NaN,
                maxElevation = Double.NaN,
                totalAscent = 0.0,
                totalDescent = 0.0,
                netElevationChange = 0.0,
                averageGrade = 0.0,
                maxUphillGrade = 0.0,
                maxDownhillGrade = 0.0
            )
        }
        
        // Filter valid points
        val validPoints = points.filter { it.isValid() }
        
        if (validPoints.isEmpty()) {
            return RoadProfileStatistics(
                totalDistance = 0.0,
                startElevation = Double.NaN,
                endElevation = Double.NaN,
                minElevation = Double.NaN,
                maxElevation = Double.NaN,
                totalAscent = 0.0,
                totalDescent = 0.0,
                netElevationChange = 0.0,
                averageGrade = 0.0,
                maxUphillGrade = 0.0,
                maxDownhillGrade = 0.0
            )
        }
        
        // Calculate key statistics
        val totalDistance = validPoints.last().distanceFromStart.toDouble()
        val startElevation = validPoints.first().elevationMeters
        val endElevation = validPoints.last().elevationMeters
        
        val minElevation = validPoints.minOf { it.elevationMeters }
        val maxElevation = validPoints.maxOf { it.elevationMeters }
        
        // Calculate ascent and descent
        var totalAscent = 0.0
        var totalDescent = 0.0
        
        for (i in 1 until validPoints.size) {
            val prevPoint = validPoints[i - 1]
            val currentPoint = validPoints[i]
            
            val elevationDiff = currentPoint.elevationMeters - prevPoint.elevationMeters
            
            if (elevationDiff > 0) {
                totalAscent += elevationDiff
            } else if (elevationDiff < 0) {
                totalDescent += abs(elevationDiff)
            }
        }
        
        val netElevationChange = endElevation - startElevation
        
        return RoadProfileStatistics(
            totalDistance = totalDistance,
            startElevation = startElevation,
            endElevation = endElevation,
            minElevation = minElevation,
            maxElevation = maxElevation,
            totalAscent = totalAscent,
            totalDescent = totalDescent,
            netElevationChange = netElevationChange,
            averageGrade = 0.0, // Will be set by caller
            maxUphillGrade = 0.0, // Will be set by caller
            maxDownhillGrade = 0.0 // Will be set by caller
        )
    }
    
    /**
     * Identifies steep climb and descent sections in the elevation profile
     * @param points List of elevation points
     * @param grades Grade values for each segment
     * @param config Analysis configuration
     * @return Pair of (steep climb sections, steep descent sections)
     */
    private fun identifySteepSections(
        points: List<ElevationPoint>,
        grades: List<Double>,
        config: RoadProfileConfig
    ): Pair<List<SteepSection>, List<SteepSection>> {
        val climbSections = mutableListOf<SteepSection>()
        val descentSections = mutableListOf<SteepSection>()
        
        if (points.size < 2 || grades.isEmpty()) return Pair(emptyList(), emptyList())
        
        // Filter valid points for processing
        val validPoints = points.filter { it.isValid() }
        if (validPoints.size < 2) return Pair(emptyList(), emptyList())
        
        var currentSectionStartIndex = 0
        var currentSectionStartDistance = 0.0
        var currentSectionElevationChange = 0.0
        var currentSectionGrades = mutableListOf<Double>()
        var currentSectionStartCoordinate = validPoints[0].coordinate
        
        // Process each segment to identify sections
        for (i in 1 until validPoints.size) {
            val prevPoint = validPoints[i - 1]
            val currentPoint = validPoints[i]
            
            // Calculate segment properties
            val distance = abs(currentPoint.distanceFromStart - prevPoint.distanceFromStart)
            val elevationDiff = currentPoint.elevationMeters - prevPoint.elevationMeters
            val grade = if (distance >= 1.0) (elevationDiff / distance) * 100 else 0.0
            
            // Check if we should start a new section or continue the current one
            val isSteep = abs(grade) >= config.steepGradeThreshold
            val isSignificantDistance = distance >= config.minSectionDistance
            val isSignificantElevationChange = abs(elevationDiff) >= config.minElevationChange
            
            // If we have a significant section or the grade changes significantly, start new section
            if (i == validPoints.size - 1 || 
                (!isSteep && !isSignificantDistance && !isSignificantElevationChange)) {
                
                // End the current section and process it if it's significant
                val sectionDistance = currentPoint.distanceFromStart - currentSectionStartDistance
                val sectionElevationChange = currentPoint.elevationMeters - validPoints[currentSectionStartIndex].elevationMeters
                
                if (sectionDistance >= config.minSectionDistance && 
                    abs(sectionElevationChange) >= config.minElevationChange) {
                    
                    // Calculate average and max grade for this section
                    val avgGrade = if (sectionDistance >= 1.0) 
                        (sectionElevationChange / sectionDistance) * 100 
                    else 0.0
                    
                    val maxGrade = currentSectionGrades.maxOrNull() ?: 0.0
                    val minGrade = currentSectionGrades.minOrNull() ?: 0.0
                    val sectionMaxGrade = max(abs(maxGrade), abs(minGrade))
                    
                    // Create the section
                    val isClimb = sectionElevationChange > 0
                    
                    val section = SteepSection(
                        startCoordinate = currentSectionStartCoordinate,
                        endCoordinate = currentPoint.coordinate,
                        startDistance = currentSectionStartDistance,
                        endDistance = currentPoint.distanceFromStart.toDouble(),
                        distanceMeters = sectionDistance,
                        elevationChange = sectionElevationChange,
                        averageGradePercent = avgGrade,
                        maxGradePercent = sectionMaxGrade,
                        isClimb = isClimb
                    )
                    
                    if (isClimb) {
                        climbSections.add(section)
                    } else {
                        descentSections.add(section)
                    }
                }
                
                // Reset for next section
                currentSectionStartIndex = i
                currentSectionStartDistance = currentPoint.distanceFromStart.toDouble()
                currentSectionElevationChange = 0.0
                currentSectionGrades.clear()
                currentSectionStartCoordinate = currentPoint.coordinate
            } else {
                // Continue the current section
                currentSectionElevationChange += elevationDiff
                currentSectionGrades.add(grade)
            }
        }
        
        return Pair(climbSections, descentSections)
    }
    
    /**
     * Merges nearby sections that are close together to avoid fragmented analysis
     * @param sections List of sections to merge
     * @param maxGap Maximum distance between sections to consider for merging (meters)
     * @return Merged list of sections
     */
    private fun mergeNearbySections(sections: List<SteepSection>, maxGap: Double): List<SteepSection> {
        if (sections.size <= 1) return sections
        
        val mergedSections = mutableListOf<SteepSection>()
        var currentSection = sections[0]
        
        for (i in 1 until sections.size) {
            val nextSection = sections[i]
            
            // Check if the sections are close enough to merge
            val gap = nextSection.startDistance - currentSection.endDistance
            
            if (gap <= maxGap) {
                // Merge these sections
                val mergedSection = currentSection.copy(
                    endCoordinate = nextSection.endCoordinate,
                    endDistance = nextSection.endDistance,
                    distanceMeters = nextSection.endDistance - currentSection.startDistance,
                    elevationChange = nextSection.endCoordinate.distanceTo(currentSection.startCoordinate), // Simplified
                    averageGradePercent = (currentSection.averageGradePercent + nextSection.averageGradePercent) / 2.0
                )
                currentSection = mergedSection
            } else {
                // Add the previous section and start a new one
                mergedSections.add(currentSection)
                currentSection = nextSection
            }
        }
        
        // Don't forget to add the last section
        mergedSections.add(currentSection)
        
        return mergedSections
    }
    
    /**
     * Gets elevation profile statistics for a specific range of points
     * @param points List of elevation points
     * @param startIndex Start index in the list
     * @param endIndex End index in the list (exclusive)
     * @return Statistics for the specified range
     */
    fun getRangeStatistics(
        points: List<ElevationPoint>,
        startIndex: Int,
        endIndex: Int
    ): RoadProfileStatistics {
        val rangePoints = if (startIndex >= 0 && endIndex <= points.size) {
            points.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
        
        return calculateBasicStatistics(rangePoints)
    }
    
    /**
     * Checks if a section is steep based on grade thresholds
     * @param section The section to check
     * @param config Configuration for determining steep sections
     * @return true if the section is considered steep, false otherwise
     */
    fun isSectionSteep(section: SteepSection, config: RoadProfileConfig): Boolean {
        return abs(section.averageGradePercent) >= config.steepGradeThreshold
    }
}