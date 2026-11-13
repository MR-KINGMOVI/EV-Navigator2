package com.evnavigator.iran.core.energy

import kotlinx.coroutines.flow.Flow
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.energy.model.*
import com.evnavigator.iran.core.roadprofile.RoadProfileData

/**
 * Interface for the EV energy, consumption and SOC engine
 * This engine calculates energy requirements for EV routes based on vehicle characteristics,
 * road conditions, and driving behavior.
 */
interface EnergyEngine {
    
    /**
     * Calculates energy consumption for a route segment with detailed breakdown
     * @param startCoordinate Start coordinate of the segment
     * @param endCoordinate End coordinate of the segment  
     * @param vehicleProfile Vehicle characteristics and consumption parameters
     * @param batteryState Battery state at the beginning of this segment
     * @param roadProfileData Road profile information for this segment
     * @param elevationPoint Elevation data for this segment (if available)
     * @param averageSpeedMps Average speed during this segment (m/s)
     * @return Detailed energy consumption result for the segment
     */
    suspend fun calculateSegmentEnergy(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        vehicleProfile: VehicleProfile,
        batteryState: BatteryState,
        roadProfileData: RoadProfileData,
        elevationPoint: ElevationPoint?,
        averageSpeedMps: Double
    ): SegmentEnergyResult
    
    /**
     * Calculates energy consumption for an entire route with segment-by-segment breakdown
     * @param routePoints List of geographic points representing the route
     * @param vehicleProfile Vehicle characteristics and consumption parameters
     * @param initialBatteryState Battery state at the start of the route
     * @param modelFidelity Level of detail for energy calculations
     * @return Complete route energy result with all segments and summary statistics
     */
    suspend fun calculateRouteEnergy(
        routePoints: List<GeoCoordinate>,
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity = EnergyModelFidelity.FULL_PHYSICS
    ): RouteEnergyResult
    
    /**
     * Calculates the feasibility of completing a route with current battery state
     * @param routePoints List of geographic points representing the route
     * @param vehicleProfile Vehicle characteristics and consumption parameters  
     * @param initialBatteryState Battery state at the start of the route
     * @param modelFidelity Level of detail for energy calculations
     * @return Feasibility result indicating if route is reachable
     */
    suspend fun evaluateRouteFeasibility(
        routePoints: List<GeoCoordinate>,
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity = EnergyModelFidelity.FULL_PHYSICS
    ): RouteFeasibilityResult
    
    /**
     * Estimates the reachable range from current position with given battery state
     * @param vehicleProfile Vehicle characteristics and consumption parameters
     * @param initialBatteryState Battery state at the start
     * @param modelFidelity Level of detail for energy calculations
     * @return Estimated maximum distance that can be traveled
     */
    suspend fun estimateReachableRange(
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity = EnergyModelFidelity.FULL_PHYSICS
    ): ReachableRangeResult
    
    /**
     * Propagates battery state along a route segment-by-segment
     * @param startCoordinate Start coordinate of the segment
     * @param endCoordinate End coordinate of the segment
     * @param vehicleProfile Vehicle characteristics and consumption parameters
     * @param currentBatteryState Battery state at the beginning of this segment
     * @param roadProfileData Road profile information for this segment
     * @param elevationPoint Elevation data for this segment (if available)
     * @param averageSpeedMps Average speed during this segment (m/s)
     * @return Updated battery state after this segment
     */
    suspend fun propagateBatteryState(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        vehicleProfile: VehicleProfile,
        currentBatteryState: BatteryState,
        roadProfileData: RoadProfileData,
        elevationPoint: ElevationPoint?,
        averageSpeedMps: Double
    ): BatteryState
}

/**
 * Represents the result of route feasibility evaluation
 */
data class RouteFeasibilityResult(
    val isReachable: Boolean,
    val reasonForUnreachability: String? = null,
    val estimatedEnergyNeededWh: Double = 0.0,
    val estimatedRangeKm: Double = 0.0,
    val minimumSoCPercent: Double = 100.0,
    val modelFidelity: EnergyModelFidelity
)

/**
 * Represents the result of reachable range estimation
 */
data class ReachableRangeResult(
    val estimatedRangeMeters: Double,
    val estimatedEnergyConsumedWh: Double,
    val estimatedTravelTimeSeconds: Double,
    val minimumSoCPercent: Double,
    val modelFidelity: EnergyModelFidelity,
    val optimisticRangeMeters: Double = estimatedRangeMeters * 1.15,
    val nominalRangeMeters: Double = estimatedRangeMeters,
    val pessimisticRangeMeters: Double = estimatedRangeMeters * 0.85
)