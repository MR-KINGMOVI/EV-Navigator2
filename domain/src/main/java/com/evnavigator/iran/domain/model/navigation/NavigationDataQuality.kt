package com.evnavigator.iran.domain.model.navigation

/**
 * Represents the quality of navigation data.
 */
enum class NavigationDataQuality {
    /**
     * Data is fresh and up-to-date.
     */
    LIVE,

    /**
     * Data is cached and may be stale.
     */
    CACHED,

    /**
     * Data is partially available or incomplete.
     */
    PARTIAL,

    /**
     * Data is unavailable or not accessible.
     */
    UNAVAILABLE,

    /**
     * Data quality is unknown.
     */
    UNKNOWN
}