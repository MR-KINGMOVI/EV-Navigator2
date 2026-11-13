package com.evnavigator.iran.core.tripplanner.model

import com.evnavigator.iran.core.charging.ChargingStation

/**
 * A planned charging stop - a prediction made at plan-time, distinct from core/energy's ChargingStop 
 * which records what actually happened during/after a trip.
 */
data class PlannedChargingStop(
    val station: com.evnavigator.iran.core.charging.ChargingStation,
    val arrivalDistanceMeters: Double,        // distance along the route where this stop occurs
    val detourDistanceMeters: Double,         // extra distance to leave/rejoin the route to reach this station
    val predictedArrivalSocPercent: Double,
    val targetDepartureSocPercent: Double,
    val estimatedChargingDurationMinutes: Double,
    val estimatedEnergyAddedWh: Double,
    val estimatedCost: Double?
)