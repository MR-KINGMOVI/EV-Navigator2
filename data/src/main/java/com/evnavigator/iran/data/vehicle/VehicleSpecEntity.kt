package com.evnavigator.iran.data.vehicle

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.evnavigator.iran.data.map.converters.CoverageLevelConverter
import com.evnavigator.iran.data.map.converters.CoverageTypeConverter
import com.evnavigator.iran.data.map.converters.SourceTypeConverter

/**
 * Room database entity for vehicle specification dataset.
 *
 * This entity represents a complete offline vehicle specification dataset that can be used
 * for energy consumption calculations, trip planning, and route optimization when network access is unavailable.
 */
@Entity(
    tableName = "vehicle_specs",
    indices = [
        // Primary indices
        androidx.room.Index(value = ["id"], unique = true),
        
        // Search and filter indices  
        androidx.room.Index(value = ["make", "model"]),
        androidx.room.Index(value = ["year"]),
        androidx.room.Index(value = ["make"]),
        androidx.room.Index(value = ["source"]),
        androidx.room.Index(value = ["marketSegment"]),
        
        // Installation and state indices
        androidx.room.Index(value = ["installationState"]),
        androidx.room.Index(value = ["activeVersion"])
    ]
)
@TypeConverters(
    SourceTypeConverter::class,
    CoverageTypeConverter::class,
    CoverageLevelConverter::class
)
data class VehicleSpecEntity(
    /**
     * Primary key for the vehicle specification dataset
     */
    @PrimaryKey
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
     * Market segment classification
     */
    val marketSegment: String? = null,

    /**
     * Battery capacity specifications (gross and usable)
     */
    val batteryCapacity: String, // Will be JSON string

    /**
     * Drivetrain type affecting efficiency and performance
     */
    val drivetrainType: String,

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
    val bodyType: String? = null,

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
     * Engine displacement or equivalent electric power
     */
    val engineDisplacementKwOrEquivalent: Double? = null,

    /**
     * Whether the vehicle is currently being used as a reference
     */
    val isActiveReference: Boolean = false,

    /**
     * Timestamp when this specification was last updated
     */
    val lastUpdated: Long,

    /**
     * Version identifier for these specifications
     */
    val version: String? = null,

    /**
     * Source of the vehicle data (e.g., manufacturer, database)
     */
    val source: String? = null,

    /**
     * Indicates if this is a custom variant
     */
    val isCustomVariant: Boolean = false,

    /**
     * Installation state of this dataset
     */
    val installationState: String,

    /**
     * Active version identifier (if this vehicle spec is currently active)
     */
    val activeVersion: String? = null,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: String? = null
)