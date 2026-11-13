package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.RouteDeviation
import com.evnavigator.iran.domain.model.navigation.MatchedLocation

/**
 * Interface for detecting route deviation.
 */
interface DeviationDetector {
    /**
     * Detect if the user is deviating from the route.
     */
    fun detectDeviation(
        route: Route,
        routePosition: RoutePosition,
        matchedLocation: MatchedLocation,
        currentLocation: Location
    ): RouteDeviation?

    /**
     * Check if deviation is significant enough to trigger rerouting.
     */
    fun isRerouteRequired(deviation: RouteDeviation?): Boolean

    /**
     * Calculate distance from route.
     */
    fun calculateDistanceFromRoute(
        route: Route,
        matchedLocation: MatchedLocation
    ): Double
}