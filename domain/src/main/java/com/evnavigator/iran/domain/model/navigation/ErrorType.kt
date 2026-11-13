package com.evnavigator.iran.domain.model.navigation

/**
 * Types of navigation errors.
 */
enum class ErrorType {
    /**
     * Location provider is unavailable.
     */
    LOCATION_UNAVAILABLE,

    /**
     * Route data is unavailable or invalid.
     */
    ROUTE_UNAVAILABLE,

    /**
     * Network connectivity is lost.
     */
    NETWORK_UNAVAILABLE,

    /**
     * Reroute request failed.
     */
    REROUTE_FAILED,

    /**
     * Invalid route data received.
     */
    ROUTE_DATA_INVALID,

    /**
     * Navigation session is invalid or corrupted.
     */
    NAVIGATION_SESSION_INVALID,

    /**
     * Provider timeout occurred.
     */
    PROVIDER_TIMEOUT,

    /**
     * Unknown error occurred.
     */
    UNKNOWN
}