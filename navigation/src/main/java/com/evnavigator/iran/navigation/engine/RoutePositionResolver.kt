package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.MatchedLocation

/**
 * Interface for determining route position from location data.
 */
interface RoutePositionResolver {
    /**
     * Determine current route position based on matched location and route.
     */
    fun determineRoutePosition(
        route: Route,
        matchedLocation: MatchedLocation
    ): RoutePosition?

    /**
     * Update route position with new location data.
     */
    fun updateRoutePosition(
        route: Route,
        previousPosition: RoutePosition?,
        currentLocation: Location,
        matchedLocation: MatchedLocation
    ): RoutePosition?
}