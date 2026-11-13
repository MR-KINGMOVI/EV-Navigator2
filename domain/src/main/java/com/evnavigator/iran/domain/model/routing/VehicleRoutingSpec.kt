package com.evnavigator.iran.domain.model.routing

import com.evnavigator.iran.domain.model.GeoCoordinate
import java.util.*

/**
 * Represents vehicle routing specifications for offline route calculation.
 *
 * This model captures all necessary parameters and constraints that define
 * how a vehicle should be routed, including its capabilities, preferences,
 * and operational constraints for offline navigation scenarios.
 */
data class VehicleRoutingSpec(
    /**
     * Unique identifier for this routing specification
     */
    val id: String,

    /**
     * The make of the vehicle (e.g., "Tesla", "Nissan")
     */
    val make: String? = null,

    /**
     * The model of the vehicle (e.g., "Model 3", "Leaf") 
     */
    val model: String? = null,

    /**
     * Trim level or variant (e.g., "S", "SE", "e-Performance")
     */
    val trim: String? = null,

    /**
     * Manufacturing year of the vehicle
     */
    val year: Int? = null,

    /**
     * Battery capacity specifications for energy calculations
     */
    val batteryCapacityKwh: Double? = null,

    /**
     * Maximum range in kilometers (to calculate feasibility)
     */
    val maxRangeKm: Double? = null,

    /**
     * Vehicle mass including cargo and passengers in kg
     */
    val vehicleMassKg: Double? = null,

    /**
     * Frontal area of the vehicle in square meters
     */
    val frontalAreaSqM: Double? = null,

    /**
     * Drag coefficient (dimensionless)
     */
    val dragCoefficient: Double? = null,

    /**
     * Rolling resistance coefficient 
     */
    val rollingResistanceCoefficient: Double? = null,

    /**
     * Maximum power the vehicle can deliver in kW
     */
    val maxPowerKw: Double? = null,

    /**
     * Peak torque that can be delivered (Nm)
     */
    val peakTorqueNm: Double? = null,

    /**
     * Top speed capability of the vehicle (km/h or mph)
     */
    val topSpeedKmh: Double? = null,

    /**
     * Charging capability - maximum charging power supported in kW
     */
    val maxChargingPowerKw: Double? = null,

    /**
     * Type of fuel or energy source (electric, hybrid, gasoline, etc.)
     */
    val fuelType: String? = null,

    /**
     * Whether the vehicle is currently being used as a reference for routing
     */
    val isActiveReference: Boolean = false,

    /**
     * Version identifier for these specifications
     */
    val version: String? = null,

    /**
     * Source of the vehicle data (e.g., manufacturer, database)
     */
    val source: String? = null,

    /**
     * Timestamp when this specification was last updated
     */
    val lastUpdated: Date = Date(),

    /**
     * Indicates if this is a standard specification or custom variant
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
            make: String? = null,
            model: String? = null,
            trim: String? = null,
            year: Int? = null,
            batteryCapacityKwh: Double? = null,
            maxRangeKm: Double? = null,
            vehicleMassKg: Double? = null,
            frontalAreaSqM: Double? = null,
            dragCoefficient: Double? = null,
            rollingResistanceCoefficient: Double? = null,
            maxPowerKw: Double? = null,
            peakTorqueNm: Double? = null,
            topSpeedKmh: Double? = null,
            maxChargingPowerKw: Double? = null,
            fuelType: String? = null,
            isActiveReference: Boolean = false,
            version: String? = null,
            source: String? = null,
            lastUpdated: Date = Date(),
            isCustomVariant: Boolean = false,
            additionalProperties: Map<String, Any>? = null
        ): VehicleRoutingSpec {
            return VehicleRoutingSpec(
                id = id,
                make = make,
                model = model,
                trim = trim,
                year = year,
                batteryCapacityKwh = batteryCapacityKwh,
                maxRangeKm = maxRangeKm,
                vehicleMassKg = vehicleMassKg,
                frontalAreaSqM = frontalAreaSqM,
                dragCoefficient = dragCoefficient,
                rollingResistanceCoefficient = rollingResistanceCoefficient,
                maxPowerKw = maxPowerKw,
                peakTorqueNm = peakTorqueNm,
                topSpeedKmh = topSpeedKmh,
                maxChargingPowerKw = maxChargingPowerKw,
                fuelType = fuelType,
                isActiveReference = isActiveReference,
                version = version,
                source = source,
                lastUpdated = lastUpdated,
                isCustomVariant = isCustomVariant,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Checks if this routing specification has all required fields for offline routing.
     */
    fun isValidForRouting(): Boolean {
        return (make != null && model != null) || 
               (batteryCapacityKwh != null && maxRangeKm != null)
    }

    /**
     * Gets the effective battery capacity in kWh, considering both gross and usable values.
     *
     * @return Usable capacity or gross if usable not available
     */
    val effectiveBatteryCapacity: Double?
        get() = when {
            // If we have a specific usable capacity, use it
            batteryCapacityKwh != null -> batteryCapacityKwh
            else -> null
        }

    /**
     * Calculates the energy consumption for a given distance.
     *
     * @param distanceKm The distance in kilometers to calculate for
     * @return Energy consumption in kWh or null if not enough data
     */
    fun estimateEnergyConsumption(distanceKm: Double): Double? {
        return when {
            // If we have the battery capacity and range, use it
            batteryCapacityKwh != null && maxRangeKm != null && maxRangeKm!! > 0 -> {
                (batteryCapacityKwh / maxRangeKm!!) * distanceKm
            }
            else -> null
        }
    }

    /**
     * Gets the vehicle's efficiency in kWh/km.
     *
     * @return Efficiency in kWh/km or null if not enough data
     */
    val energyEfficiency: Double?
        get() = when {
            batteryCapacityKwh != null && maxRangeKm != null && maxRangeKm!! > 0 -> 
                (batteryCapacityKwh ?: 0.0) / maxRangeKm!!
            else -> null
        }
}