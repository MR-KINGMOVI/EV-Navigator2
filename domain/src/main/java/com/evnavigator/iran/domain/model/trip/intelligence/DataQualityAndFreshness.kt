package com.evnavigator.iran.domain.model.trip.intelligence

/**
 * Information about the quality and freshness of data used in trip intelligence.
 */
data class DataQualityAndFreshness(
    val dataFetchedAt: Long, // Timestamp when data was fetched (milliseconds since epoch)
    val isDataStale: Boolean,
    val dataFreshnessHours: Int? = null,  // Hours old; null if unknown
    val qualityScore: Double? = null,     // Overall quality score from 0.0 to 1.0
    val dataGaps: List<String> = emptyList() // Descriptions of missing or unreliable data points
) {
    val dataQualityScore: Double
        get() = qualityScore ?: 0.0
}