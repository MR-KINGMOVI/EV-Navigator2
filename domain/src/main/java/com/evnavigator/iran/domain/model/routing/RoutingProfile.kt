package com.evnavigator.iran.domain.model.routing

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.elevation.ElevationProfile
import java.util.*

/**
 * Represents a routing profile that defines how to calculate routes for specific use cases.
 *
 * This model captures the constraints, preferences, and rules that govern route calculation,
 * including vehicle capabilities, road restrictions, time windows, and other factors.
 */
data class RoutingProfile(
    /**
     * Unique identifier for this routing profile
     */
    val id: String,

    /**
     * Name or label for easy identification of this profile
     */
    val name: String,

    /**
     * Description of what this profile is used for
     */
    val description: String? = null,

    /**
     * Type of vehicle the routing is optimized for (car, truck, bicycle, pedestrian)
     */
    val vehicleType: RoutingVehicleType,

    /**
     * Speed limits and travel time preferences
     */
    val speedProfile: RouteSpeedProfile? = null,

    /**
     * Road type restrictions or preferences
     */
    val roadTypePreferences: List<RoadTypePreference> = emptyList(),

    /**
     * Time-based constraints (e.g., avoid rush hour)
     */
    val timeConstraints: List<TimeConstraint> = emptyList(),

    /**
     * Elevation and terrain preferences 
     */
    val elevationProfile: ElevationProfile? = null,

    /**
     * Traffic condition preferences
     */
    val trafficPreferences: TrafficPreferences? = null,

    /**
     * Charging station requirements for electric vehicles
     */
    val chargingRequirements: ChargingRequirements? = null,

    /**
     * Fuel type or energy source preference (electric, hybrid, gasoline)
     */
    val fuelType: String? = null,

    /**
     * Whether this is the default profile to use
     */
    val isDefault: Boolean = false,

    /**
     * Version identifier for this routing profile
     */
    val version: String? = null,

    /**
     * Source of where this profile originated from
     */
    val source: String? = null,

    /**
     * Timestamp when this specification was last updated
     */
    val lastUpdated: Date = Date(),

    /**
     * Indicates if this is a standard configuration or custom variant
     */
    val isCustomVariant: Boolean = false,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            id: String,
            name: String,
            description: String? = null,
            vehicleType: RoutingVehicleType,
            speedProfile: RouteSpeedProfile? = null,
            roadTypePreferences: List<RoadTypePreference> = emptyList(),
            timeConstraints: List<TimeConstraint> = emptyList(),
            elevationProfile: ElevationProfile? = null,
            trafficPreferences: TrafficPreferences? = null,
            chargingRequirements: ChargingRequirements? = null,
            fuelType: String? = null,
            isDefault: Boolean = false,
            version: String? = null,
            source: String? = null,
            lastUpdated: Date = Date(),
            isCustomVariant: Boolean = false,
            additionalProperties: Map<String, Any>? = null
        ): RoutingProfile {
            return RoutingProfile(
                id = id,
                name = name,
                description = description,
                vehicleType = vehicleType,
                speedProfile = speedProfile,
                roadTypePreferences = roadTypePreferences,
                timeConstraints = timeConstraints,
                elevationProfile = elevationProfile,
                trafficPreferences = trafficPreferences,
                chargingRequirements = chargingRequirements,
                fuelType = fuelType,
                isDefault = isDefault,
                version = version,
                source = source,
                lastUpdated = lastUpdated,
                isCustomVariant = isCustomVariant,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Gets the effective routing constraints for this profile.
     *
     * @return List of RouteConstraints or empty list if none defined
     */
    val routeConstraints: List<RouteConstraint>
        get() {
            return (if (speedProfile != null) listOf(RouteConstraint.SpeedLimit(speedProfile.maxSpeedKmh)) else emptyList()) +
                    roadTypePreferences.map { it.toRouteConstraint() } +
                    timeConstraints.map { it.toRouteConstraint() }
        }

    /**
     * Checks if this routing profile is compatible with a given vehicle.
     *
     * @param vehicle The vehicle specification to check compatibility with
     * @return True if compatible, false otherwise
     */
    fun isCompatibleWith(vehicle: VehicleRoutingSpec): Boolean {
        // For now, just return true - actual implementation would depend on specific requirements
        return true
    }
}

/**
 * Represents different types of vehicles for routing purposes.
 */
enum class RoutingVehicleType {
    CAR,
    TRUCK,
    BICYCLE,
    PEDESTRIAN,
    MOTORCYCLE,
    ELECTRIC_CAR
}

sealed class RouteConstraint {
    data class SpeedLimit(val maxSpeedKmh: Double?) : RouteConstraint()
    data class AvoidRoadType(val roadType: String) : RouteConstraint()
    data class TimeWindow(val startHour: Int, val endHour: Int) : RouteConstraint()
    data class Custom(val name: String, val value: Any?) : RouteConstraint()
}

data class RoadTypePreference(
    val roadType: String,
    val preference: Double = 1.0,
    val avoid: Boolean = false
) {
    fun toRouteConstraint(): RouteConstraint =
        if (avoid) RouteConstraint.AvoidRoadType(roadType)
        else RouteConstraint.Custom("PREFER_ROAD_TYPE_$roadType", preference)
}

data class TimeConstraint(
    val description: String = "",
    val startHour: Int = 0,
    val endHour: Int = 24
) {
    fun toRouteConstraint(): RouteConstraint = RouteConstraint.TimeWindow(startHour, endHour)
}

data class TrafficPreferences(
    val avoidCongestion: Boolean = true,
    val useHistoricalData: Boolean = true,
    val realTimeFactor: Double = 1.0
)

data class ChargingRequirements(
    val minBatteryAtDestinationPercent: Double = 20.0,
    val minBatteryAtStopsPercent: Double = 15.0,
    val preferredChargerPowerKw: Double = 50.0
)