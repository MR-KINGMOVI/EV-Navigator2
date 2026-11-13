package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.NavigationProgress

/**
 * Interface for tracking navigation progress along a route.
 */
interface RouteProgressTracker {
    /**
     * Track progress along a route.
     */
    fun trackProgress(
        route: Route,
        routePosition: RoutePosition
    ): NavigationProgress?

    /**
     * Calculate progress from start to current position.
     */
    fun calculateProgress(
        route: Route,
        routePosition: RoutePosition
    ): NavigationProgress?
}