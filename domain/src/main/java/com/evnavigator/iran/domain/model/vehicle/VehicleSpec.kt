package com.evnavigator.iran.domain.model.vehicle

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Energy
import com.evnavigator.iran.domain.model.Power
import java.util.*

/**
 * Represents the technical specifications of an electric vehicle.
 *
 * This model captures all essential physical and performance characteristics
 * that affect EV driving behavior, energy consumption, and charging requirements.
 */
data class VehicleSpec(
    /**
     * Unique identifier for this vehicle specification set
     */
    val id: String,

    /**
     * Make of the vehicle (e.g., Tesla, Nissan, BMW)
     */
    val make: String,

    /**
     * Model of the vehicle (e.g., Model 3, Leaf, i3)
     */
    val model: String,

    /**
     * Trim level or variant (e.g., S, SE, e-Performance)
     */
    val trim: String? = null,

    /**
     * Year of manufacture
     */
    val year: Int,

    /**
     * Market segment classification (e.g., Subcompact, Compact, Midsize, Full-size)
     */
    val marketSegment: VehicleMarketSegment? = null,

    /**
     * Battery capacity specifications - gross and usable
     */
    val batteryCapacity: BatterySpecifications,

    /**
     * Drivetrain type affecting efficiency and performance
     */
    val drivetrainType: DrivetrainType,

    /**
     * Mass of the vehicle in kilograms (empty weight)
     */
    val massEmptyKg: Double,

    /**
     * Mass of the vehicle when loaded with passengers/fuel in kilograms
     */
    val massWithOccupantsKg: Double? = null,

    /**
     * Frontal area in square meters - affects aerodynamic drag
     */
    val frontalAreaSqM: Double? = null,

    /**
     * Coefficient of drag (Cd) - affects aerodynamic efficiency
     */
    val dragCoefficient: Double? = null,

    /**
     * Rolling resistance coefficient - affects rolling loss at speed
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
     * Range estimation under standard conditions
     */
    val estimatedRangeKm: Double? = null,

    /**
     * Estimated consumption per 100 km in kWh/100km
     */
    val energyConsumptionPer100Km: Double? = null,

    /**
     * Charging capability - maximum charging power supported
     */
    val maxChargingPowerKw: Double? = null,

    /**
     * Type of vehicle body (e.g., Sedan, SUV, Hatchback)
     */
    val bodyType: VehicleBodyType? = null,

    /**
     * Number of seats in the vehicle
     */
    val numberOfSeats: Int? = null,

    /**
     * Trunk/boot capacity in liters
     */
    val trunkCapacityLiters: Double? = null,

    /**
     * Fuel type for non-electric vehicles (if applicable)
     */
    val fuelType: String? = null,

    /**
     * Engine displacement in liters or equivalent electric power in kW
     */
    val engineDisplacementKwOrEquivalent: Double? = null,

    /**
     * Whether the vehicle is currently being used as a reference for calculations
     */
    val isActiveReference: Boolean = false,

    /**
     * Timestamp when this specification was last updated
     */
    val lastUpdated: Date = Date(),

    /**
     * Version identifier for these specifications (for versioning)
     */
    val version: String? = null,

    /**
     * Source of the vehicle data (e.g., manufacturer, database, user input)
     */
    val source: String? = null,

    /**
     * Indicates if this is a standard specification or a custom variant
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
            make: String,
            model: String,
            year: Int,
            trim: String? = null,
            marketSegment: VehicleMarketSegment? = null,
            batteryCapacity: BatterySpecifications,
            drivetrainType: DrivetrainType,
            massEmptyKg: Double,
            massWithOccupantsKg: Double? = null,
            frontalAreaSqM: Double? = null,
            dragCoefficient: Double? = null,
            rollingResistanceCoefficient: Double? = null,
            maxPowerKw: Double? = null,
            peakTorqueNm: Double? = null,
            topSpeedKmh: Double? = null,
            estimatedRangeKm: Double? = null,
            energyConsumptionPer100Km: Double? = null,
            maxChargingPowerKw: Double? = null,
            bodyType: VehicleBodyType? = null,
            numberOfSeats: Int? = null,
            trunkCapacityLiters: Double? = null,
            fuelType: String? = null,
            engineDisplacementKwOrEquivalent: Double? = null,
            isActiveReference: Boolean = false,
            lastUpdated: Date = Date(),
            version: String? = null,
            source: String? = null,
            isCustomVariant: Boolean = false,
            additionalProperties: Map<String, Any>? = null
        ): VehicleSpec {
            return VehicleSpec(
                id = id,
                make = make,
                model = model,
                year = year,
                trim = trim,
                marketSegment = marketSegment,
                batteryCapacity = batteryCapacity,
                drivetrainType = drivetrainType,
                massEmptyKg = massEmptyKg,
                massWithOccupantsKg = massWithOccupantsKg,
                frontalAreaSqM = frontalAreaSqM,
                dragCoefficient = dragCoefficient,
                rollingResistanceCoefficient = rollingResistanceCoefficient,
                maxPowerKw = maxPowerKw,
                peakTorqueNm = peakTorqueNm,
                topSpeedKmh = topSpeedKmh,
                estimatedRangeKm = estimatedRangeKm,
                energyConsumptionPer100Km = energyConsumptionPer100Km,
                maxChargingPowerKw = maxChargingPowerKw,
                bodyType = bodyType,
                numberOfSeats = numberOfSeats,
                trunkCapacityLiters = trunkCapacityLiters,
                fuelType = fuelType,
                engineDisplacementKwOrEquivalent = engineDisplacementKwOrEquivalent,
                isActiveReference = isActiveReference,
                lastUpdated = lastUpdated,
                version = version,
                source = source,
                isCustomVariant = isCustomVariant,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Gets the usable battery capacity in kWh (gross minus potential dead weight).
     */
    val usableBatteryKwh: Double?
        get() = batteryCapacity.usableKwh

    val chargingPowerKwMax: Double?
        get() = maxChargingPowerKw

    fun getUsableBatteryCapacityKwh(): Double =
        usableBatteryKwh ?: batteryCapacity.usableKwh ?: batteryCapacity.grossKwh ?: 50.0

    /**
     * Calculates the energy required to travel a given distance.
     *
     * @param distance The distance to calculate for
     * @return Energy consumption in kWh or null if calculation is not possible
     */
    fun estimateEnergyConsumption(distance: Distance): Double? {
        return this.energyConsumptionPer100Km?.let { 
            (distance.inKilometers() / 100.0) * it 
        }
    }

    /**
     * Checks if the vehicle has all required specifications for energy calculations.
     */
    fun isValidForEnergyCalculation(): Boolean {
        return batteryCapacity.grossKwh != null && 
                massEmptyKg > 0 &&
                maxPowerKw != null
    }
}

enum class VehicleMarketSegment {
    SUBCOMPACT, COMPACT, MIDSIZE, FULL_SIZE, LUXURY, SPORTS, SUV, CROSSOVER, VAN, TRUCK
}

enum class DrivetrainType {
    BEV, PHEV, HEV, FCEV, FWD, RWD, AWD
}

enum class VehicleBodyType {
    SEDAN, HATCHBACK, SUV, CROSSOVER, COUPE, CONVERTIBLE, WAGON, VAN, PICKUP
}