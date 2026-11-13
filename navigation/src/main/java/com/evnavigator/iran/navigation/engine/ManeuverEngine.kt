package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.maneuver.UpcomingManeuver

/**
 * Interface for determining current maneuvers in navigation.
 */
interface ManeuverEngine {
    /**
     * Determine the upcoming maneuver based on route and position.
     */
    fun determineUpcomingManeuver(
        route: Route,
        routePosition: RoutePosition
    ): UpcomingManeuver?

    /**
     * Check if a maneuver has been completed.
     */
    fun isManeuverCompleted(
        route: Route,
        routePosition: RoutePosition,
        previousManeuver: UpcomingManeuver?
    ): Boolean

    /**
     * Get the next few maneuvers in sequence.
     */
    fun getNextManeuvers(
        route: Route,
        routePosition: RoutePosition,
        count: Int = 3
    ): List<UpcomingManeuver>
}