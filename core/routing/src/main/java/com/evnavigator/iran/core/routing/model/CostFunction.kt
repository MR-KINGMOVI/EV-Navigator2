package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance

/**
 * Interface for cost functions used in routing calculations
 */
interface CostFunction {
    /**
     * Calculate the cost of traversing an edge
     */
    fun calculateCost(
        edge: RoadEdge,
        profile: RoutingProfile,
        distance: Distance
    ): Double
    
    /**
     * Get the weight factor for different road types
     */
    fun getRoadTypeWeight(roadClass: RoadClass): Double
    
    /**
     * Get the speed multiplier for different travel modes
     */
    fun getSpeedMultiplier(travelMode: TravelMode): Double
}