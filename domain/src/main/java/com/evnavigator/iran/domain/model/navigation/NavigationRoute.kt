package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Route

/**
 * Navigation-specific route representation with additional metadata for navigation purposes.
 */
data class NavigationRoute(
    /**
     * The base route data.
     */
    val route: Route,

    /**
     * Unique identifier for this route version.
     */
    val version: String,

    /**
     * Timestamp when this route was created or last updated.
     */
    val timestamp: Long
)