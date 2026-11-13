package com.evnavigator.iran.domain.model.routing

/**
 * Represents the current status of a routing engine.
 *
 * This enum defines different states that the offline routing system can be in,
 * helping to understand whether it's ready for use, processing requests, or has issues.
 */
enum class RoutingEngineStatus {
    /**
     * The routing engine is available and ready to process route requests
     */
    AVAILABLE,

    /**
     * The routing engine is currently calculating a route
     */
    PROCESSING,

    /**
     * The routing engine is not initialized properly
     */
    NOT_INITIALIZED,

    /**
     * The routing engine has encountered an error during processing
     */
    ERROR,

    /**
     * The routing engine is performing maintenance or updates
     */
    MAINTENANCE,

    /**
     * No offline map data is available for routing
     */
    NO_MAP_DATA,

    /**
     * No elevation data is currently available for route calculation
     */
    NO_ELEVATION_DATA,

    /**
     * Offline routing is not supported on this device or platform
     */
    NOT_SUPPORTED,

    /**
     * The engine has been disabled by user or system settings
     */
    DISABLED
}