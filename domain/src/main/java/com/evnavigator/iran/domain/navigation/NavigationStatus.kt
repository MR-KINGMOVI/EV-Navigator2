package com.evnavigator.iran.domain.navigation

import java.util.*

/**
 * Represents the current status of a navigation session.
 *
 * This enum defines different states that an active navigation session can be in,
 * helping to understand whether it's idle, navigating, or has issues.
 */
enum class NavigationStatus {
    /**
     * Navigation is currently idle with no route being followed
     */
    IDLE,

    /**
     * Currently calculating a new route
     */
    CALCULATING_ROUTE,

    /**
     * Actively following a route with turn-by-turn directions
     */
    NAVIGATING,

    /**
     * Navigating but paused by user or system
     */
    PAUSED,

    /**
     * Navigation has been stopped manually
     */
    STOPPED,

    /**
     * System is searching for an alternative route due to being off-course
     */
    SEARCHING_ALTERNATIVE_ROUTE,

    /**
     * Route is invalid and needs re-calculation
     */
    INVALID_ROUTE,

    /**
     * GPS signal is lost or unreliable
     */
    GPS_LOST,

    /**
     * Device has no offline map data available for navigation
     */
    NO_OFFLINE_MAPS_AVAILABLE,

    /**
     * The system cannot determine current position accurately enough
     */
    POSITION_UNKNOWN,

    /**
     * Navigation engine is processing requests or updating state
     */
    PROCESSING,

    /**
     * An error occurred during navigation setup or operation
     */
    ERROR,

    /**
     * Device is in an area with no map coverage for navigation
     */
    NO_MAP_COVERAGE,

    /**
     * Navigation session has been suspended due to low battery
     */
    SUSPENDED_LOW_BATTERY,

    /**
     * Navigation system is initializing and not yet ready
     */
    INITIALIZING,

    /**
     * Device is currently offline but navigation can still function with cached data
     */
    OFFLINE_MODE,

    /**
     * System has been disabled by user or settings
     */
    DISABLED
}