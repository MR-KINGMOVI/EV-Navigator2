package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.EtaEstimate
import com.evnavigator.iran.domain.model.navigation.NavigationDataQuality

/**
 * Interface for calculating ETA (Estimated Time of Arrival).
 */
interface EtaEngine {
    /**
     * Calculate ETA based on current position and route.
     */
    fun calculateEta(
        route: Route,
        routePosition: RoutePosition,
        dataQuality: NavigationDataQuality
    ): EtaEstimate?

    /**
     * Update ETA with new information.
     */
    fun updateEta(
        currentEta: EtaEstimate?,
        route: Route,
        routePosition: RoutePosition,
        dataQuality: NavigationDataQuality
    ): EtaEstimate?
}