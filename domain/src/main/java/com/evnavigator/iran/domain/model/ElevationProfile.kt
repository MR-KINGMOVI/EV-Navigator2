package com.evnavigator.iran.domain.model

/**
 * Represents an elevation profile along a route.
 *
 * @param samples Ordered list of elevation samples
 */
data class ElevationProfile(
    val samples: List<ElevationSample>
) {
    init {
        // Validate that cumulative distance never decreases
        for (i in 1 until samples.size) {
            require(samples[i].distanceAlongRoute.value >= samples[i-1].distanceAlongRoute.value) {
                "Cumulative distance must never decrease. Sample ${i} has distance ${samples[i].distanceAlongRoute.value} " +
                    "but previous sample had ${samples[i-1].distanceAlongRoute.value}"
            }
        }
    }

    /**
     * Gets the total ascent of the profile.
     */
    val totalAscent: Double
        get() {
            if (samples.isEmpty()) return 0.0
            
            var ascent = 0.0
            for (i in 1 until samples.size) {
                val prevElevation = samples[i - 1].elevation.altitude.value()
                val currentElevation = samples[i].elevation.altitude.value()
                if (currentElevation > prevElevation) {
                    ascent += currentElevation - prevElevation
                }
            }
            return ascent
        }

    /**
     * Gets the total descent of the profile.
     */
    val totalDescent: Double
        get() {
            if (samples.isEmpty()) return 0.0
            
            var descent = 0.0
            for (i in 1 until samples.size) {
                val prevElevation = samples[i - 1].elevation.altitude.value()
                val currentElevation = samples[i].elevation.altitude.value()
                if (currentElevation < prevElevation) {
                    descent += prevElevation - currentElevation
                }
            }
            return descent
        }

    /**
     * Gets the maximum uphill grade.
     */
    val maxUphillGrade: Double
        get() {
            if (samples.size < 2) return 0.0
            
            var maxGrade = 0.0
            for (i in 1 until samples.size) {
                val prevSample = samples[i - 1]
                val currentSample = samples[i]
                
                val distance = currentSample.distanceAlongRoute.value - prevSample.distanceAlongRoute.value
                if (distance > 0) {
                    val elevationDiff = currentSample.elevation.altitude.value() - prevSample.elevation.altitude.value()
                    val grade = (elevationDiff / distance) * 100 // Convert to percentage
                    if (grade > maxGrade) {
                        maxGrade = grade
                    }
                }
            }
            return maxGrade
        }

    /**
     * Gets the maximum downhill grade.
     */
    val maxDownhillGrade: Double
        get() {
            if (samples.size < 2) return 0.0
            
            var maxGrade = 0.0
            for (i in 1 until samples.size) {
                val prevSample = samples[i - 1]
                val currentSample = samples[i]
                
                val distance = currentSample.distanceAlongRoute.value - prevSample.distanceAlongRoute.value
                if (distance > 0) {
                    val elevationDiff = currentSample.elevation.altitude.value() - prevSample.elevation.altitude.value()
                    val grade = (elevationDiff / distance) * 100 // Convert to percentage
                    if (grade < -maxGrade) {
                        maxGrade = -grade
                    }
                }
            }
            return maxGrade
        }

    /**
     * Gets elevation statistics for the profile.
     */
    val elevationStats: ElevationStatistics
        get() {
            if (samples.isEmpty()) {
                return ElevationStatistics(
                    minElevation = null,
                    maxElevation = null,
                    averageElevation = null
                )
            }
            
            val elevations = samples.map { it.elevation.altitude.value() }
            return ElevationStatistics(
                minElevation = elevations.minOrNull(),
                maxElevation = elevations.maxOrNull(),
                averageElevation = elevations.average()
            )
        }

    /**
     * Gets grade statistics for the profile.
     */
    val gradeStats: GradeStatistics
        get() {
            if (samples.size < 2) {
                return GradeStatistics(
                    minGrade = 0.0,
                    maxGrade = 0.0,
                    averageGrade = 0.0
                )
            }
            
            val grades = mutableListOf<Double>()
            for (i in 1 until samples.size) {
                val prevSample = samples[i - 1]
                val currentSample = samples[i]
                
                val distance = currentSample.distanceAlongRoute.value - prevSample.distanceAlongRoute.value
                if (distance > 0) {
                    val elevationDiff = currentSample.elevation.altitude.value() - prevSample.elevation.altitude.value()
                    val grade = (elevationDiff / distance) * 100 // Convert to percentage
                    grades.add(grade)
                }
            }
            
            return GradeStatistics(
                minGrade = grades.minOrNull() ?: 0.0,
                maxGrade = grades.maxOrNull() ?: 0.0,
                averageGrade = grades.average()
            )
        }
}