package com.evnavigator.iran.domain.model.charging

import com.evnavigator.iran.domain.model.ConnectorType
import com.evnavigator.iran.domain.model.GeoCoordinate
import java.util.*

/**
 * Represents a charging strategy for electric vehicle trips.
 *
 * This data class defines the logic and parameters for determining when, where,
 * and how to charge an EV during a trip based on route information, battery state,
 * vehicle capabilities, and real-time or offline constraints.
 */
data class ChargingStrategy(
    // Strategy identification
    val id: String,
    val name: String,
    val description: String,
    
    // Route parameters that influence charging decisions
    val startCoordinate: GeoCoordinate,
    val destinationCoordinate: GeoCoordinate,
    val waypoints: List<GeoCoordinate>,
    
    // Vehicle specifications and constraints
    val vehicleSpecs: com.evnavigator.iran.domain.model.vehicle.VehicleSpec,
    
    // Battery state information  
    val initialBatteryLevelPercent: Double,  // Starting battery level (0-100%)
    val targetBatteryLevelPercent: Double,   // Target battery level at destination
    val batteryCapacityKwh: Double,          // Usable battery capacity in kWh
    
    // Route and charging preferences
    val routePreferences: ChargingRoutePreferences,
    
    // Time constraints for the trip
    val departureTime: Long,                 // When the journey is scheduled to start  
    val estimatedArrivalTime: Long?,         // Estimated arrival time at destination
    val maximumChargingTimeSeconds: Long,    // Maximum allowed charging time in seconds
    
    // Charging station preferences and requirements
    val preferredConnectorTypes: List<ConnectorType>,
    val requiredPowerKw: Double,             // Minimum power needed for charging (kW)
    
    // Optimization parameters
    val optimizationGoal: OptimizationGoal,   // How to optimize the strategy
    val maximumRangeLossMeters: Double,       // Maximum acceptable range loss in meters
    
    // Offline-specific constraints
    val offlineConstraints: OfflineChargingConstraints,
    
    // Computed values for charging decisions
    val estimatedTotalDistanceMeters: Double,
    val estimatedEnergyConsumptionKwh: Double,
    val recommendedStops: List<GeoCoordinate>,
    
    // Strategy outcome and recommendations
    val strategyStatus: ChargingStrategyStatus,
    val confidenceScore: Double,              // How confident we are in this strategy (0.0 - 1.0)
    
    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = createdAt,
    val version: String = "1.0"
) {
    /**
     * Gets the recommended charging stops based on current battery state and route.
     */
    fun getRecommendedChargingStops(): List<GeoCoordinate> {
        return recommendedStops
    }

    /**
     * Checks if this strategy is valid for the given parameters.
     */
    fun isValid(): Boolean {
        return id.isNotBlank() &&
                startCoordinate != null && 
                destinationCoordinate != null &&
                waypoints.isNotEmpty() &&
                vehicleSpecs != null &&
                initialBatteryLevelPercent >= 0 &&
                initialBatteryLevelPercent <= 100 &&
                batteryCapacityKwh > 0 &&
                departureTime > 0
    }

    /**
     * Gets the estimated charging time for this strategy.
     */
    fun getEstimatedChargingTime(): Long? {
        // Simplified implementation - in reality, this would be more complex
        return if (estimatedEnergyConsumptionKwh > 0) {
            val chargePower = vehicleSpecs.chargingPowerKwMax ?: 50.0
            (estimatedEnergyConsumptionKwh / chargePower * 3600).toLong() // in seconds
        } else {
            null
        }
    }

    /**
     * Gets the estimated cost of following this charging strategy.
     */
    fun getEstimatedCost(): Double? {
        return if (estimatedEnergyConsumptionKwh > 0) {
            val ratePerKwh = 0.25 // Fixed rate for now, in USD/kWh
            estimatedEnergyConsumptionKwh * ratePerKwh
        } else {
            null
        }
    }

    /**
     * Gets the number of charging stops recommended by this strategy.
     */
    fun getNumberOfChargingStops(): Int {
        return recommendedStops.size
    }

    /**
     * Checks if a specific stop location is part of this strategy.
     */
    fun containsStop(stop: GeoCoordinate): Boolean {
        return recommendedStops.contains(stop)
    }
    
    companion object {
        
        /**
         * Creates a default charging strategy for EV navigation.
         */
        @JvmStatic
        fun createDefault(
            startCoordinate: GeoCoordinate,
            destinationCoordinate: GeoCoordinate,
            vehicleSpecs: com.evnavigator.iran.domain.model.vehicle.VehicleSpec,
            initialBatteryLevelPercent: Double = 100.0,
            targetBatteryLevelPercent: Double = 20.0
        ): ChargingStrategy {
            return ChargingStrategy(
                id = UUID.randomUUID().toString(),
                name = "DefaultChargingStrategy",
                description = "A default charging strategy for EV navigation",
                
                startCoordinate = startCoordinate,
                destinationCoordinate = destinationCoordinate,
                waypoints = listOf(startCoordinate, destinationCoordinate),
                
                vehicleSpecs = vehicleSpecs,
                initialBatteryLevelPercent = initialBatteryLevelPercent,
                targetBatteryLevelPercent = targetBatteryLevelPercent,
                batteryCapacityKwh = vehicleSpecs.getUsableBatteryCapacityKwh(),
                
                routePreferences = ChargingRoutePreferences(
                    avoidHighway = false,
                    preferFastCharging = true,
                    maxRangeLossMeters = 5000.0
                ),
                
                departureTime = System.currentTimeMillis(),
                estimatedArrivalTime = null,
                maximumChargingTimeSeconds = 3600L, // 1 hour
                
                preferredConnectorTypes = listOf(ConnectorType.CHAdeMO),
                requiredPowerKw = 50.0,
                
                optimizationGoal = OptimizationGoal.EFFICIENCY,
                maximumRangeLossMeters = 5000.0,
                
                offlineConstraints = OfflineChargingConstraints(
                    maxDistanceBetweenStopsMeters = 100000.0, // 100km
                    minBatteryLevelAtStopPercent = 20.0,
                    maxWaitTimeSeconds = 3600L
                ),
                
                estimatedTotalDistanceMeters = 50000.0,
                estimatedEnergyConsumptionKwh = 40.0,
                recommendedStops = emptyList(),
                
                strategyStatus = ChargingStrategyStatus.ACTIVE,
                confidenceScore = 0.8
            )
        }
    }
}

/**
 * Represents the preferences for route and charging behavior.
 */
data class ChargingRoutePreferences(
    val avoidHighway: Boolean = false,
    val preferFastCharging: Boolean = true,
    val maxRangeLossMeters: Double = 10000.0, // 10km
    val preferredConnectorTypes: List<ConnectorType> = listOf(ConnectorType.CHAdeMO),
    
    // For optimization purposes
    val avoidTolls: Boolean = false,
    val preferLowEmissionZones: Boolean = false,
    val maxSlope: Double = 5.0, // Maximum acceptable slope percentage
    
    // Time-based preferences  
    val earliestDepartureTime: Long? = null,
    val latestArrivalTime: Long? = null
)

/**
 * Offline-specific constraints for charging.
 */
data class OfflineChargingConstraints(
    val maxDistanceBetweenStopsMeters: Double = 100000.0, // 100km
    val minBatteryLevelAtStopPercent: Double = 20.0,
    val maxWaitTimeSeconds: Long = 3600L, // 1 hour
    val minimumChargingPowerKw: Double = 10.0,
    val preferredChargerTypes: List<ChargerType> = listOf(ChargerType.DC_FAST_CHARGER),
    
    // For offline data validation
    val validateOnStartupOnly: Boolean = true,
    val cacheDurationSeconds: Long = 3600L, // 1 hour
    
    // For handling missing or incomplete information
    val fallbackToPreviousData: Boolean = true,
    val minimumConfidenceThreshold: Double = 0.5
)

/**
 * Different optimization goals for charging strategies.
 */
enum class OptimizationGoal {
    EFFICIENCY,      // Minimize total travel time and energy consumption
    COST_EFFECTIVE,  // Minimize charging costs
    RANGE,           // Maximize range between charges  
    COMFORT          // Balance efficiency with driver comfort
}

/**
 * Status of a charging strategy.
 */
enum class ChargingStrategyStatus {
    ACTIVE,
    INACTIVE,
    STALE,
    ERROR,
    PENDING
}

enum class ChargerType {
    STANDARD,
    FAST,
    DC_FAST_CHARGER,
    RAPID
}