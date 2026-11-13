package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.navigation.RouteDeviation
import com.evnavigator.iran.domain.model.navigation.NavigationSessionId

/**
 * Interface for coordinating reroute requests.
 */
interface RerouteCoordinator {
    /**
     * Initiate a reroute request.
     */
    suspend fun initiateReroute(
        sessionId: NavigationSessionId,
        route: Route,
        deviation: RouteDeviation?
    ): Boolean

    /**
     * Cancel pending reroute request.
     */
    fun cancelPendingReroute(sessionId: NavigationSessionId)

    /**
     * Check if reroute is currently in progress.
     */
    fun isRerouteInProgress(sessionId: NavigationSessionId): Boolean

    /**
     * Handle reroute result.
     */
    suspend fun handleRerouteResult(
        sessionId: NavigationSessionId,
        newRoute: Route?
    ): Boolean
}