package com.evnavigator.iran.domain.model.update

/**
 * Priority levels for background dataset updates.
 * 
 * This enum defines the priority of different update types based on:
 * - User importance
 * - Criticality for navigation
 * - Storage constraints
 * - Network constraints
 * - Battery impact
 */
enum class UpdatePriority {
    /**
     * Critical updates that must be applied immediately.
     * Examples:
     * - Security patches
     * - Schema incompatibility fixes
     * - Missing critical dependencies
     * - Corrupted active dataset
     */
    CRITICAL,

    /**
     * High priority updates for essential functionality.
     * Examples:
     * - Major version updates
     * - New charging station data
     * - Significant map coverage expansion
     * - Vehicle database updates
     */
    HIGH,

    /**
     * Medium priority updates for improved functionality.
     * Examples:
     * - Minor version updates
     * - Additional charger locations
     * - Traffic data improvements
     * - Weather data improvements
     */
    MEDIUM,

    /**
     * Low priority updates for optional enhancements.
     * Examples:
     * - Non-critical map refinements
     * - Minor traffic data updates
     * - Weather forecast improvements
     * - User preference updates
     */
    LOW,

    /**
     * Background updates that can be deferred.
     * Examples:
     * - Non-essential data refreshes
     * - Cache optimization
     * - Metadata updates
     */
    BACKGROUND;

    /**
     * Whether this update priority is mandatory (cannot be skipped or deferred indefinitely).
     */
    val isMandatory: Boolean
        get() = this == CRITICAL || this == HIGH
}
