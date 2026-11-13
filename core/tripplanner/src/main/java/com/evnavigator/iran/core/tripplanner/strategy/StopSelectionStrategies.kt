package com.evnavigator.iran.core.tripplanner.strategy

import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.tripplanner.model.TripConstraints
import com.evnavigator.iran.core.vehicle.ChargingSpecification

/**
 * Strategy-specific tie-breaking functions for selecting charging stations.
 * These functions are separate and testable without running the full engine.
 */
object StopSelectionStrategies {
    
    /**
     * Select a charging station for fewest stops strategy.
     * Pick the candidate with the highest available power (charge fastest,
     * so you can charge to a higher SOC and skip more future stops).
     */
    fun selectForFewestStops(
        candidates: List<ChargingStation>,
        vehicleChargingSpec: ChargingSpecification
    ): ChargingStation {
        // Select the station with highest available power
        return candidates.maxByOrNull { it.maxPowerKw ?: 0.0 } 
            ?: candidates.firstOrNull() ?: throw IllegalArgumentException("No candidates available")
    }
    
    /**
     * Select a charging station for fastest arrival strategy.
     * Pick the candidate with the lowest detourDistanceMeters combined with
     * charging time (detour + charging duration converted to comparable units).
     */
    fun selectForFastestArrival(
        candidates: List<ChargingStation>,
        vehicleChargingSpec: ChargingSpecification,
        distanceToThreshold: Double,
        constraints: TripConstraints,
        chargingCurveModel: com.evnavigator.iran.core.tripplanner.engine.ChargingCurveModel
    ): ChargingStation {
        // For each candidate, calculate a combined score:
        // detourDistance + (estimated charging time in minutes converted to distance-like units)
        // We'll use 0.1 km per minute as a rough conversion factor for time-to-distance
        val scoringFactor = 0.1 // km per minute
        
        return candidates.minByOrNull { station ->
            // For now, we'll just pick the one with minimum detour (simplified)
            // In a full implementation this would properly score based on both factors
            val detourDistance = 0.0 // Would be calculated from actual detour distance
            
            // Calculate estimated charging time in minutes
            val estimatedChargingTime = if (station.maxPowerKw != null) {
                // Simplified - would need actual SOC range and vehicle data
                30.0 // Default for now
            } else {
                30.0
            }
            
            detourDistance + estimatedChargingTime * scoringFactor
        } ?: candidates.firstOrNull() ?: throw IllegalArgumentException("No candidates available")
    }
    
    /**
     * Select a charging station for lowest cost strategy.
     * Pick the candidate with the lowest estimated cost for the required energy.
     */
    fun selectForLowestCost(
        candidates: List<ChargingStation>,
        vehicleChargingSpec: ChargingSpecification
    ): ChargingStation {
        // Select the station with lowest cost per kWh, or first if no cost data
        return candidates.minByOrNull { station ->
            station.ports.firstOrNull()?.pricePerKwh ?: Double.MAX_VALUE
        } ?: candidates.firstOrNull() ?: throw IllegalArgumentException("No candidates available")
    }
}