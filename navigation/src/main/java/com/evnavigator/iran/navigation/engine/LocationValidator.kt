package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Location

/**
 * Interface for validating location data.
 */
interface LocationValidator {
    /**
     * Validate a location and return its quality.
     */
    fun validateLocation(location: Location): LocationQuality

    /**
     * Check if location is stale (older than threshold).
     */
    fun isLocationStale(location: Location, maxAgeSeconds: Long): Boolean

    /**
     * Check if location jump is suspicious.
     */
    fun isLocationJumpSuspicious(
        previousLocation: Location?,
        currentLocation: Location,
        maxDistanceMeters: Double
    ): Boolean
}

/**
 * Quality of a validated location.
 */
enum class LocationQuality {
    /**
     * Location is valid and reliable.
     */
    VALID,

    /**
     * Location has some issues but can be used with caution.
     */
    DEGRADED,

    /**
     * Location is stale or outdated.
     */
    STALE,

    /**
     * Location is invalid or cannot be trusted.
     */
    INVALID,

    /**
     * Location quality is unknown.
     */
    UNKNOWN
}