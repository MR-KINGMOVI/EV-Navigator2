package com.evnavigator.iran.domain.model.navigation

/**
 * Represents current traffic status.
 */
enum class TrafficStatus {
    /**
     * Traffic is flowing normally.
     */
    NORMAL,

    /**
     * Traffic is slow due to congestion.
     */
    SLOW,

    /**
     * Traffic is heavy with significant delays.
     */
    HEAVY,

    /**
     * Traffic is very heavy or stopped.
     */
    STUCK,

    /**
     * Traffic data is unavailable.
     */
    UNAVAILABLE,

    /**
     * Traffic status is unknown.
     */
    UNKNOWN
}