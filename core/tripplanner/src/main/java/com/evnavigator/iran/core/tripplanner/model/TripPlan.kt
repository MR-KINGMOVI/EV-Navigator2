package com.evnavigator.iran.core.tripplanner.model

import com.evnavigator.iran.core.domain.Route
import com.evnavigator.iran.core.vehicle.VehicleId

/**
 * Complete trip plan with all charging stops and route information.
 */
data class TripPlan(
    val route: com.evnavigator.iran.core.domain.Route,
    val vehicleId: com.evnavigator.iran.core.vehicle.VehicleId,
    val startSocPercent: Double,
    val chargingStops: List<PlannedChargingStop>,
    val finalArrivalSocPercent: Double,
    val totalDrivingDurationMinutes: Double,
    val totalChargingDurationMinutes: Double,
    val totalTripDurationMinutes: Double,
    val totalEstimatedCost: Double?,
    val strategy: TripPlanningStrategy,
    val constraints: TripConstraints,
    val feasible: Boolean,           // false if no valid plan could be produced (e.g. no reachable station in range)
    val infeasibilityReason: String? = null
)
