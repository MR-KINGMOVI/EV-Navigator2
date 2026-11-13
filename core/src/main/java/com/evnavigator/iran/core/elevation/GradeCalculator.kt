package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import kotlin.math.abs

/**
 * Configuration for grade calculations (V13.3.1 Section 7).
 */
data class GradeCalculationConfig(
    val minDistanceForGrade: Double = 1.0,
    val maxPhysicallyPlausibleRoadGradePercent: Double = 35.0,
    val enableSmoothing: Boolean = false,
    val smoothingWindowSize: Int = 3
)

data class GradeStatistics(
    val averageGrade: Double,
    val maxGrade: Double,
    val minGrade: Double,
    val totalDistance: Double,
    val filteredSpikeCount: Int = 0
)

/**
 * Result of grade calculation operation
 */
data class GradeCalculationResult(
    val grades: List<Double>,
    val statistics: GradeStatistics,
    val anomalousSegmentIndices: List<Int> = emptyList()
)

/**
 * Utility class for calculating grades from elevation data with DEM spike filtering (V13.3.1 Section 7).
 */
object GradeCalculator {

    const val MAX_PLAUSIBLE_ROAD_GRADE_PERCENT: Double = 35.0

    /**
     * Calculates grades between consecutive valid elevation points, filtering out
     * unphysical DEM spikes (`abs(gradePercent) > 35.0%`) and recording them in [GradeCalculationResult.anomalousSegmentIndices].
     */
    fun calculateGrades(
        points: List<ElevationPoint>,
        config: GradeCalculationConfig = GradeCalculationConfig()
    ): GradeCalculationResult {
        if (points.isEmpty()) {
            return GradeCalculationResult(
                grades = emptyList(),
                statistics = GradeStatistics(0.0, 0.0, 0.0, 0.0, 0)
            )
        }

        val validPoints = points.filter { it.isValid() }
        if (validPoints.size < 2) {
            return GradeCalculationResult(
                grades = emptyList(),
                statistics = GradeStatistics(0.0, 0.0, 0.0, 0.0, 0)
            )
        }

        val grades = mutableListOf<Double>()
        val anomalousIndices = mutableListOf<Int>()
        var totalDistance = 0.0

        for (i in 1 until validPoints.size) {
            val prevPoint = validPoints[i - 1]
            val currentPoint = validPoints[i]

            val prevDistance = prevPoint.distanceFromStart.toDouble()
            val currentDistance = currentPoint.distanceFromStart.toDouble()
            val elevationDiff = currentPoint.elevationMeters - prevPoint.elevationMeters

            val horizontalDistance = (currentDistance - prevDistance).takeIf { it > 0.0 }
                ?: prevPoint.coordinate.distanceTo(currentPoint.coordinate)

            if (horizontalDistance < config.minDistanceForGrade) {
                continue
            }

            val gradePercent = (elevationDiff / horizontalDistance) * 100.0
            if (!gradePercent.isFinite() || abs(gradePercent) > config.maxPhysicallyPlausibleRoadGradePercent) {
                anomalousIndices.add(i - 1)
                continue
            }

            grades.add(gradePercent)
            totalDistance += horizontalDistance
        }

        val averageGrade = if (grades.isNotEmpty()) grades.average() else 0.0
        val maxGrade = if (grades.isNotEmpty()) grades.maxOf { it } else 0.0
        val minGrade = if (grades.isNotEmpty()) grades.minOf { it } else 0.0

        val statistics = GradeStatistics(
            averageGrade = averageGrade,
            maxGrade = maxGrade,
            minGrade = minGrade,
            totalDistance = totalDistance,
            filteredSpikeCount = anomalousIndices.size
        )

        return GradeCalculationResult(grades, statistics, anomalousIndices)
    }

    /**
     * Calculates the grade percentage for a specific segment, returning [Double.NaN] when
     * data is invalid or exceeds physical road grade bounds (`> 35%`).
     */
    fun calculateGradeForSegment(startPoint: ElevationPoint, endPoint: ElevationPoint): Double {
        if (!startPoint.isValid() || !endPoint.isValid()) {
            return Double.NaN
        }

        val startDistance = startPoint.distanceFromStart.toDouble()
        val endDistance = endPoint.distanceFromStart.toDouble()
        val elevationDiff = endPoint.elevationMeters - startPoint.elevationMeters

        val horizontalDistance = (endDistance - startDistance).takeIf { it > 0.0 }
            ?: startPoint.coordinate.distanceTo(endPoint.coordinate)

        if (horizontalDistance <= 0.0) {
            return 0.0
        }

        val grade = (elevationDiff / horizontalDistance) * 100.0
        return if (abs(grade) <= MAX_PLAUSIBLE_ROAD_GRADE_PERCENT) grade else Double.NaN
    }
}