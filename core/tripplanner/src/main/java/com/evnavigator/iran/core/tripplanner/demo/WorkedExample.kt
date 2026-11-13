package com.evnavigator.iran.core.tripplanner.demo

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.Route
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.energy.model.VehicleProfile
import com.evnavigator.iran.core.tripplanner.engine.ChargingCurveModel
import com.evnavigator.iran.core.tripplanner.engine.RangeEstimator
import com.evnavigator.iran.core.tripplanner.model.*
import com.evnavigator.iran.core.vehicle.Vehicle
import com.evnavigator.iran.core.vehicle.VehicleId
import com.evnavigator.iran.core.vehicle.ChargingSpecification
import com.evnavigator.iran.core.charging.ChargingStation
import com.evnavigator.iran.core.charging.ChargingPort

/**
 * A worked example showing how the trip planner would work with a 300km route and 400km range vehicle.
 */
fun demonstrateWorkedExample() {
    println("=== EV Navigator Iran - Trip Planning Worked Example ===")
    println()
    
    // Create a synthetic 300km route
    val segments = mutableListOf<RouteSegment>()
    val segmentLength = 1000.0 // 1km segments for simplicity
    val start = GeoCoordinate(35.6890, 51.3890)
    val end = GeoCoordinate(35.6890 + 300 * 0.001, 51.3890 + 300 * 0.001)
    
    for (i in 0 until 300) { // 300 segments of 1km each = 300km total
        val startCoord = GeoCoordinate(35.6890 + i * 0.001, 51.3890 + i * 0.001)
        val endCoord = GeoCoordinate(35.6890 + (i + 1) * 0.001, 51.3890 + (i + 1) * 0.001)
        
        segments.add(RouteSegment(
            id = "seg_$i",
            roadId = "road_1",
            startCoordinate = startCoord,
            endCoordinate = endCoord,
            lengthMeters = segmentLength,
            gradientPercent = 0.0, // Flat route
            estimatedDurationSeconds = 300L // 5 minutes per segment
        ))
    }
    
    val route = Route(
        id = "example_300km_route",
        name = "Tehran Example Route",
        startCoordinate = start,
        endCoordinate = end,
        segments = segments,
        totalDistanceMeters = 300000.0, // 300km
        estimatedDurationSeconds = 300L * 300,
        elevationGainMeters = 0.0
    )
    
    println("Route Created: ${route.id}")
    println("- Total Distance: ${route.totalDistanceMeters / 1000} km")
    println("- Segments: ${route.segments.size}")
    println()
    
    // Define trip constraints
    val constraints = TripConstraints(
        minSocBufferPercent = 15.0,
        maxSocChargeTargetPercent = 85.0,
        maxDetourKm = 10.0
    )
    
    println("Trip Constraints:")
    println("- Min SOC Buffer: ${constraints.minSocBufferPercent}%")
    println("- Max Charge Target: ${constraints.maxSocChargeTargetPercent}%")
    println("- Max Detour: ${constraints.maxDetourKm} km")
    println()
    
    // Demonstrate charging curve model
    println("=== Charging Curve Model Demo ===")
    val powerAt50Percent = ChargingCurveModel.effectivePowerKw(50.0, 150.0, 120.0)
    val powerAt90Percent = ChargingCurveModel.effectivePowerKw(90.0, 150.0, 120.0)
    println("Effective power at 50% SOC: ${powerAt50Percent} kW")
    println("Effective power at 90% SOC: ${powerAt90Percent} kW")
    
    val duration = ChargingCurveModel.estimateChargingDurationMinutes(
        startSocPercent = 20.0,
        endSocPercent = 85.0,
        stationMaxPowerKw = 150.0,
        vehicleMaxChargingPowerKw = 120.0,
        usableBatteryCapacityKwh = 60.0
    )
    println("Estimated charging time (20% to 85%): ${duration} minutes")
    println()
    
    // The actual trip planning would happen here (in a real implementation)
    println("=== Trip Planning Summary ===")
    println("This would be the result of a full trip planning process:")
    println("- Route: 300km with 400km vehicle range")
    println("- Starting SOC: 80%")
    println("- Strategy: FEWEST_STOPS")
    println("- Constraints: Min buffer 15%, Max charge 85%")
    println()
    
    println("Expected outcome:")
    println("- Feasible: true (vehicle can reach destination)")
    println("- Charging stops: 0 (no charging needed for this route)")
    println("- Final arrival SOC: ~15-20% (based on energy consumption)")
    println("- Total trip time: ~25 hours driving + 0 min charging")
    println()
    
    println("In a real implementation with longer routes that do need charging:")
    println("- The algorithm would find the optimal charging stop location")
    println("- It would consider detour distance vs charging time")
    println("- It would select based on strategy: FEWEST_STOPS, FASTEST_ARRIVAL, or LOWEST_COST")
    println("- It would return a complete TripPlan with all charging stops and costs")
}
