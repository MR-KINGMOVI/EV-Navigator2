package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Distance
import com.evnavigator.iran.core.energy.model.Speed
import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.core.energy.model.Efficiency

/**
 * Canonical vehicle domain model that represents a complete electric vehicle specification.
 *
 * This is the production-grade vehicle model that will be used throughout the application
 * and fed into Phase 5 energy calculations.
 */
data class Vehicle(
    val id: VehicleId,
    
    // Basic identification
    val name: String, // Human-readable display name
    
    // Technical specifications - Physical characteristics  
    val curbMassKg: Mass,
    val grossVehicleMassKg: Mass?,
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
    val nominalBatteryCapacityWh: Energy,
    val usableBatteryCapacityWh: Energy,
    val batteryChemistry: String?, // e.g., "Lithium Iron Phosphate", "Nickel Manganese Cobalt"
    val nominalVoltageV: Double?,
    val batteryConfiguration: String?, // e.g., "48S10P", "60S8P" 
    
    // Motor/drivetrain specifications  
    val motorCount: Int,
    val frontMotorPowerKw: Power?,
    val rearMotorPowerKw: Power?,
    val peakMotorPowerKw: Power?,
    val continuousMotorPowerKw: Power?,
    val torqueNm: Int?,
    val drivetrainType: DrivetrainType,
    
    // Energy-related parameters for Phase 5
    val baselineConsumptionWhPer100Km: Double?,
    
    // Charging capabilities (static vehicle-side specifications)
    val acChargingMaxPowerKw: Power?,
    val dcChargingMaxPowerKw: Power?,
    val chargingVoltageRangeV: IntRange?, // e.g., 230-480V
    val chargingCurrentRangeA: IntRange?, // e.g., 6-32A
    val chargePortCount: Int,
    
    // Connector information (static vehicle-side specifications)
    val connectorTypes: Set<ConnectorType>,
    
    // Vehicle dimensions and weight for aerodynamics and rolling resistance
    val wheelDiameterMm: Int?,
    val tireWidthMm: Int?,
    
    // Data quality and provenance
    val dataQuality: DataQuality,
    val source: String, // Source of the specification (e.g., manufacturer, database)
    val sourceVersion: String?, // Version of the data source
    val sourceReference: String? = null, // Reference or identifier within the source
    val retrievedDate: Long, // Unix timestamp when data was retrieved
    val confidence: Double, // Confidence level 0.0 to 1.0
    
    // Validation status
    val validationStatus: ValidationStatus,
    
    // Additional notes or comments
    val notes: String?,
    
    // Versioning information
    val schemaVersion: String = "1.0", // Schema version of this vehicle record
    val dataVersion: String = "1.0",   // Data version (for tracking updates)
    val vehicleRecordVersion: String = "1.0", // Specific version of this vehicle record
    
    // Timestamps for tracking changes
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Validation performed by ValidationEngine
    /**
     * Gets the total operating mass (curb mass + payload)
     */
    val totalOperatingMass: Mass get() = curbMassKg

    val chargingSpecification: ChargingSpecification
        get() = ChargingSpecification(
            acChargingMaxPowerKw = acChargingMaxPowerKw,
            dcChargingMaxPowerKw = dcChargingMaxPowerKw,
            chargingVoltageRangeV = chargingVoltageRangeV,
            chargingCurrentRangeA = chargingCurrentRangeA,
            connectorTypes = connectorTypes,
            chargePortCount = chargePortCount,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = (dcChargingMaxPowerKw?.watts ?: 0.0) >= 40000.0,
            isV2GCompatible = null
        )
    
    /**
     * Determines if this vehicle has complete specifications
     */
    val isComplete: Boolean 
        get() {
            // Basic requirements for a complete vehicle
            return name.isNotBlank() && 
                   nominalBatteryCapacityWh.wattHours > 0 &&
                   usableBatteryCapacityWh.wattHours > 0 &&
                   curbMassKg.kilograms > 0 &&
                   motorCount >= 0 &&
                   connectorTypes.isNotEmpty()
        }
    
    /**
     * Determines if this vehicle has sufficient data for energy calculations
     */
    val isEnergyReady: Boolean
        get() {
            // For Phase 5 energy calculations, we need:
            return curbMassKg.kilograms > 0 &&
                   usableBatteryCapacityWh.wattHours > 0 &&
                   motorCount >= 0 &&
                   connectorTypes.isNotEmpty()
        }
    
    /**
     * Creates a copy of this vehicle with updated timestamps
     */
    fun withUpdatedTimestamp(): Vehicle {
        val now = System.currentTimeMillis()
        val nextTimestamp = if (now <= updatedAt) updatedAt + 1 else now
        return this.copy(updatedAt = nextTimestamp)
    }
    
    /**
     * Checks if this vehicle has the same core identity as another
     */
    fun hasSameIdentity(other: Vehicle): Boolean {
        return id.manufacturer == other.id.manufacturer &&
               id.model == other.id.model &&
               id.generation == other.id.generation &&
               id.modelYear == other.id.modelYear &&
               id.trim == other.id.trim &&
               id.market == other.id.market
    }
    
    /**
     * Gets a human-readable description of this vehicle
     */
    fun getHumanReadableDescription(): String {
        return "$name (${id.id})"
    }
}

/**
 * Represents different drivetrain configurations
 */
enum class DrivetrainType {
    FWD,      // Front Wheel Drive
    RWD,      // Rear Wheel Drive  
    AWD,      // All Wheel Drive
    MIXED,    // Mixed or configurable drivetrain
    UNKNOWN   // Unknown or unspecified drivetrain
}

/**
 * Represents different connector types for charging
 */
enum class ConnectorType {
    TYPE_2,       // IEC 62196 Type 2 (Mennekes)
    CCS_COMBO_2,  // Combined Charging System Combo 2
    CCS_COMBO_1,  // Combined Charging System Combo 1  
    GB_T,         // GB/T (Chinese standard)
    GB_T_DC,      // GB/T DC
    GB_T_AC,      // GB/T AC
    CHAdeMO,      // SAE J1772 with DC capability
    TESLA_ROADSTER, // Tesla proprietary connector
    TESLA_Supercharger, // Tesla Supercharger connector
    OTHER         // Other unspecified connector type
}

/**
 * Represents data quality levels for vehicle specifications
 */
enum class DataQuality {
    HIGH,      // Verified and complete specification  
    MEDIUM,    // Partially verified or estimated
    LOW,       // Incomplete or questionable data
    UNKNOWN    // No data available
}

/**
 * Represents the validation status of a vehicle record
 */
enum class ValidationStatus {
    VALIDATED,     // All validations passed
    PARTIALLY_VALIDATED,  // Some validations failed but record is usable  
    INVALID,     // Record has critical validation errors
    UNVERIFIED,  // No validation performed yet
    UNVALIDATED  // Unvalidated record
}