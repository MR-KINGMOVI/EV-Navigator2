package com.evnavigator.iran.domain.model.navigation

/**
 * Represents GPS signal quality.
 */
enum class GpsQuality {
    /**
     * GPS signal is excellent.
     */
    EXCELLENT,

    /**
     * GPS signal is good.
     */
    GOOD,

    /**
     * GPS signal is moderate.
     */
    MODERATE,

    /**
     * GPS signal is poor.
     */
    POOR,

    /**
     * GPS signal is degraded or lost.
     */
    DEGRADED,

    /**
     * GPS signal is unavailable.
     */
    UNAVAILABLE,

    /**
     * GPS quality is unknown.
     */
    UNKNOWN
}