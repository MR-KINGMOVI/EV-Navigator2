package com.evnavigator.iran.domain.model

/**
 * Represents statistics about grade data.
 *
 * @param minGrade Minimum grade percentage in the profile
 * @param maxGrade Maximum grade percentage in the profile
 * @param averageGrade Average grade percentage in the profile
 */
data class GradeStatistics(
    val minGrade: Double,
    val maxGrade: Double,
    val averageGrade: Double
)