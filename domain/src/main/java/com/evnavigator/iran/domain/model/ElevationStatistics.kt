package com.evnavigator.iran.domain.model

/**
 * Represents statistics about elevation data.
 *
 * @param minElevation Minimum elevation in the profile (can be null if no samples)
 * @param maxElevation Maximum elevation in the profile (can be null if no samples)
 * @param averageElevation Average elevation in the profile (can be null if no samples)
 */
data class ElevationStatistics(
    val minElevation: Double?,
    val maxElevation: Double?,
    val averageElevation: Double?
)