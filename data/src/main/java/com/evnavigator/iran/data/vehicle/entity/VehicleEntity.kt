package com.evnavigator.iran.data.vehicle.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.evnavigator.iran.data.vehicle.converters.VehicleTypeConverters
import java.util.*

/**
 * Database entity for vehicle specifications.
 *
 * This is the Room database entity that maps to the vehicle table.
 * Optimized with appropriate indexes for fast queries.
 */
@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["manufacturer"]),
        Index(value = ["model"]),
        Index(value = ["powertrainType"]),
        Index(value = ["market"]),
        Index(value = ["modelYear"]),
        Index(value = ["trim"]),
        Index(value = ["manufacturer", "model", "generation", "modelYear"]),
        Index(value = ["manufacturer", "model"]),
        Index(value = ["powertrainType", "market"]),
        Index(value = ["modelYear", "market"]),
        Index(value = ["manufacturer", "modelYear"]),
        Index(value = ["trim", "modelYear"]),
        Index(value = ["schemaVersion"]),
        Index(value = ["dataVersion"]),
        Index(value = ["source"]),
        Index(value = ["sourceVersion"]),
        Index(value = ["confidence"]),
        Index(value = ["validationStatus"])
    ]
)
@TypeConverters(VehicleTypeConverters::class)
data class VehicleEntity(
    @PrimaryKey
    val id: String, // Unique identifier from VehicleId
    
    // Basic identification  
    val name: String,
    
    // Manufacturer and model information
    val manufacturer: String,
    val model: String,
    val generation: String,
    val modelYear: Int,
    val trim: String,
    val market: String,
    val powertrainType: String, // Stored as string for simplicity
    
    // Technical specifications - Physical characteristics  
    val curbMassKg: Double,
    val grossVehicleMassKg: Double?,
    val lengthMm: Int?,
    val widthMm: Int?, 
    val heightMm: Int?,
    val wheelbaseMm: Int?,
    val groundClearanceMm: Int?,
    
    // Aerodynamic properties
    val dragCoefficient: Double?,
    val frontalAreaM2: Double?,
    
    // Rolling resistance (if known)
    val rollingResistanceCoefficient: Double?,
    
    // Battery specifications
    val nominalBatteryCapacityWh: Double,
    val usableBatteryCapacityWh: Double,
    val batteryChemistry: String?,
    val nominalVoltageV: Double?,
    val batteryConfiguration: String?, 
    
    // Motor/drivetrain specifications  
    val motorCount: Int,
    val frontMotorPowerKw: Double?,
    val rearMotorPowerKw: Double?,
    val peakMotorPowerKw: Double?,
    val continuousMotorPowerKw: Double?,
    val torqueNm: Int?,
    val drivetrainType: String, // Stored as string for simplicity
    
    // Energy-related parameters for Phase 5
    val baselineConsumptionWhPer100Km: Double?,
    
    // Charging capabilities (static vehicle-side specifications)
    val acChargingMaxPowerKw: Double?,
    val dcChargingMaxPowerKw: Double?,
    val chargingVoltageRangeV: String?, // Stored as "min-max" format
    val chargingCurrentRangeA: String?, // Stored as "min-max" format
    val chargePortCount: Int,
    
    // Connector information (static vehicle-side specifications)
    val connectorTypes: String, // Comma-separated values
    
    // Vehicle dimensions and weight for aerodynamics and rolling resistance
    val wheelDiameterMm: Int?,
    val tireWidthMm: Int?,
    
    // Data quality and provenance
    val dataQuality: String, // Stored as string for simplicity
    val source: String,
    val sourceVersion: String?,
    val retrievedDate: Long,
    val confidence: Double,
    
    // Validation status
    val validationStatus: String, // Stored as string for simplicity
    
    // Additional notes or comments
    val notes: String?,
    
    // Versioning information
    val schemaVersion: String,
    val dataVersion: String,
    val vehicleRecordVersion: String,

    // Provenance & consumption standard
    val consumptionStandard: String = "WLTP",
    val consumptionReferenceSpeedKmh: Double = 90.0,
    val sourceUrl: String? = null,
    val verifiedAt: Long? = null,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if this vehicle is expired based on confidence threshold.
     *
     * @param confidenceThreshold Minimum confidence required (default 0.5)
     * @return true if confidence is below threshold
     */
    fun isExpired(confidenceThreshold: Double = 0.5): Boolean {
        return confidence < confidenceThreshold
    }

    /**
     * Check if this vehicle is valid based on validation status.
     *
     * @return true if validation status is VALID
     */
    fun isValid(): Boolean {
        return validationStatus == "VALID" || validationStatus == "VALIDATED" || validationStatus == "VERIFIED"
    }

    /**
     * Check if this vehicle is stale based on age.
     *
     * @param maxAgeMs Maximum age in milliseconds (default 30 days)
     * @return true if vehicle is older than max age
     */
    fun isStale(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000): Boolean {
        val age = System.currentTimeMillis() - retrievedDate
        return age > maxAgeMs
    }

    /**
     * Get the age of this vehicle in milliseconds.
     *
     * @return Age in milliseconds
     */
    fun getAge(): Long {
        return System.currentTimeMillis() - retrievedDate
    }
}
