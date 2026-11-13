package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.SOCPrediction

/**
 * Interface for projecting energy/SOC along the route.
 */
interface NavigationEnergyProjector {
    /**
     * Project SOC along the route.
     */
    fun projectEnergy(
        route: Route,
        routePosition: RoutePosition,
        currentSoc: Double
    ): SOCPrediction?

    /**
     * Update energy projection with new information.
     */
    fun updateEnergyProjection(
        currentProjection: SOCPrediction?,
        route: Route,
        routePosition: RoutePosition,
        currentSoc: Double
    ): SOCPrediction?
}