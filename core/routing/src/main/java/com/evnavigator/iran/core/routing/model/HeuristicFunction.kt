package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * Interface for heuristic functions used in pathfinding algorithms
 */
interface HeuristicFunction {
    /**
     * Calculate the estimated cost from a node to the destination
     */
    fun estimateCost(
        from: GeoCoordinate,
        to: GeoCoordinate
    ): Distance
    
    /**
     * Get the estimated time from a node to the destination
     */
    fun estimateTime(
        from: GeoCoordinate,
        to: GeoCoordinate,
        profile: RoutingProfile
    ): Double
}