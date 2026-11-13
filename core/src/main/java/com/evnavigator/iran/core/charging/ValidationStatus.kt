package com.evnavigator.iran.core.charging

/**
 * Validation status for charging station data and provenance tracking.
 */
enum class ValidationStatus {
    /**
     * Data has been fully verified with confirmed physical operational proof
     */
    VERIFIED,

    /**
     * Data has been validated and confirmed correct
     */
    VALIDATED,

    /**
     * Data has been partially verified with known location and municipal/operator records
     */
    PARTIALLY_VERIFIED,

    /**
     * Data is partially validated
     */
    PARTIALLY_VALIDATED,

    /**
     * Data has not been verified independently
     */
    UNVERIFIED,

    /**
     * Projected or planned station from infrastructure surveys
     */
    ASSUMED,

    /**
     * Data was reported by a user and has not been independently verified
     */
    USER_REPORTED,

    /**
     * Hardware or station data is outdated or historical
     */
    OUTDATED,

    /**
     * Station is confirmed offline, removed, or decommissioned
     */
    UNAVAILABLE,

    /**
     * Data is stale or outdated and may need updating
     */
    STALE,

    /**
     * Data is confirmed or suspected to be invalid
     */
    INVALID,

    /**
     * Validation status is unknown or not specified
     */
    UNKNOWN
}
