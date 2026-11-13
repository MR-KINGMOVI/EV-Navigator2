package com.evnavigator.iran.domain.model.navigation

/**
 * Represents the current status of a navigation session.
 */
enum class NavigationStatus {
    /**
     * Navigation session is idle, no active navigation.
     */
    IDLE,

    /**
     * Navigation session is starting, preparing to begin navigation.
     */
    STARTING,

    /**
     * Navigation session is actively navigating.
     */
    NAVIGATING,

    /**
     * Navigation session is recalculating route due to deviation or user request.
     */
    RECALCULATING,

    /**
     * GPS signal is degraded, navigation continuing with reduced accuracy.
     */
    GPS_DEGRADED,

    /**
     * Navigation is operating in offline mode with cached data.
     */
    OFFLINE,

    /**
     * Navigation session is paused by user.
     */
    PAUSED,

    /**
     * User has arrived at destination.
     */
    ARRIVED,

    /**
     * Navigation session has been stopped.
     */
    STOPPED,

    /**
     * Navigation session encountered an error.
     */
    ERROR
}