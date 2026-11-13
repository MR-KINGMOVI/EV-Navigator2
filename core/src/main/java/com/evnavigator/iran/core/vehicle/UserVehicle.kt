package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power

/**
 * Represents a user's custom vehicle configuration.
 *
 * This model allows users to override specific aspects of a canonical vehicle
 * while preserving the original specification and maintaining clear precedence.
 */
data class UserVehicle(
    val userId: String,
    val canonicalVehicleId: VehicleId,
    val overrides: VehicleOverrides = VehicleOverrides(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    
    /**
     * Gets the effective vehicle specification by applying user overrides to the canonical vehicle
     */
    fun getEffectiveVehicle(canonicalVehicle: Vehicle): Vehicle {
        return canonicalVehicle.copy(
            // Preserve original ID (this is important for provenance)
            id = canonicalVehicle.id,
            
            // Apply overrides where they exist
            name = overrides.name ?: canonicalVehicle.name,
            curbMassKg = overrides.curbMassKg ?: canonicalVehicle.curbMassKg,
            grossVehicleMassKg = overrides.grossVehicleMassKg ?: canonicalVehicle.grossVehicleMassKg,
            lengthMm = overrides.lengthMm ?: canonicalVehicle.lengthMm,
            widthMm = overrides.widthMm ?: canonicalVehicle.widthMm,
            heightMm = overrides.heightMm ?: canonicalVehicle.heightMm,
            wheelbaseMm = overrides.wheelbaseMm ?: canonicalVehicle.wheelbaseMm,
            groundClearanceMm = overrides.groundClearanceMm ?: canonicalVehicle.groundClearanceMm,
            dragCoefficient = overrides.dragCoefficient ?: canonicalVehicle.dragCoefficient,
            frontalAreaM2 = overrides.frontalAreaM2 ?: canonicalVehicle.frontalAreaM2,
            rollingResistanceCoefficient = overrides.rollingResistanceCoefficient ?: canonicalVehicle.rollingResistanceCoefficient,
            nominalBatteryCapacityWh = overrides.nominalBatteryCapacityWh ?: canonicalVehicle.nominalBatteryCapacityWh,
            usableBatteryCapacityWh = overrides.usableBatteryCapacityWh ?: canonicalVehicle.usableBatteryCapacityWh,
            batteryChemistry = overrides.batteryChemistry ?: canonicalVehicle.batteryChemistry,
            nominalVoltageV = overrides.nominalVoltageV ?: canonicalVehicle.nominalVoltageV,
            batteryConfiguration = overrides.batteryConfiguration ?: canonicalVehicle.batteryConfiguration,
            motorCount = overrides.motorCount ?: canonicalVehicle.motorCount,
            frontMotorPowerKw = overrides.frontMotorPowerKw ?: canonicalVehicle.frontMotorPowerKw,
            rearMotorPowerKw = overrides.rearMotorPowerKw ?: canonicalVehicle.rearMotorPowerKw,
            peakMotorPowerKw = overrides.peakMotorPowerKw ?: canonicalVehicle.peakMotorPowerKw,
            continuousMotorPowerKw = overrides.continuousMotorPowerKw ?: canonicalVehicle.continuousMotorPowerKw,
            torqueNm = overrides.torqueNm ?: canonicalVehicle.torqueNm,
            drivetrainType = overrides.drivetrainType ?: canonicalVehicle.drivetrainType,
            baselineConsumptionWhPer100Km = overrides.baselineConsumptionWhPer100Km ?: canonicalVehicle.baselineConsumptionWhPer100Km,
            acChargingMaxPowerKw = overrides.acChargingMaxPowerKw ?: canonicalVehicle.acChargingMaxPowerKw,
            dcChargingMaxPowerKw = overrides.dcChargingMaxPowerKw ?: canonicalVehicle.dcChargingMaxPowerKw,
            chargingVoltageRangeV = overrides.chargingVoltageRangeV ?: canonicalVehicle.chargingVoltageRangeV,
            chargingCurrentRangeA = overrides.chargingCurrentRangeA ?: canonicalVehicle.chargingCurrentRangeA,
            chargePortCount = overrides.chargePortCount ?: canonicalVehicle.chargePortCount,
            connectorTypes = overrides.connectorTypes ?: canonicalVehicle.connectorTypes,
            wheelDiameterMm = overrides.wheelDiameterMm ?: canonicalVehicle.wheelDiameterMm,
            tireWidthMm = overrides.tireWidthMm ?: canonicalVehicle.tireWidthMm,
            
            // Preserve original metadata
            dataQuality = canonicalVehicle.dataQuality, // Use original quality
            source = canonicalVehicle.source, // Use original source
            sourceVersion = canonicalVehicle.sourceVersion, // Use original version
            retrievedDate = canonicalVehicle.retrievedDate, // Use original retrieval date
            confidence = overrides.confidence ?: canonicalVehicle.confidence,
            validationStatus = canonicalVehicle.validationStatus, // Use original validation status
            notes = overrides.notes ?: canonicalVehicle.notes
        )
    }
}

/**
 * Represents the set of vehicle overrides that a user can apply to a canonical specification
 */
data class VehicleOverrides(
    val name: String? = null,
    
    // Physical characteristics  
    val curbMassKg: Mass? = null,
    val grossVehicleMassKg: Mass? = null,
    val lengthMm: Int? = null,
    val widthMm: Int? = null, 
    val heightMm: Int? = null,
    val wheelbaseMm: Int? = null,
    val groundClearanceMm: Int? = null,
    
    // Aerodynamic properties
    val dragCoefficient: Double? = null,
    val frontalAreaM2: Double? = null,
    
    // Rolling resistance 
    val rollingResistanceCoefficient: Double? = null,
    
    // Battery specifications
    val nominalBatteryCapacityWh: Energy? = null,
    val usableBatteryCapacityWh: Energy? = null,
    val batteryChemistry: String? = null,
    val nominalVoltageV: Double? = null,
    val batteryConfiguration: String? = null,
    
    // Motor/drivetrain specifications  
    val motorCount: Int? = null,
    val frontMotorPowerKw: Power? = null,
    val rearMotorPowerKw: Power? = null,
    val peakMotorPowerKw: Power? = null,
    val continuousMotorPowerKw: Power? = null,
    val torqueNm: Int? = null,
    val drivetrainType: DrivetrainType? = null,
    
    // Energy-related parameters for Phase 5
    val baselineConsumptionWhPer100Km: Double? = null,
    
    // Charging capabilities
    val acChargingMaxPowerKw: Power? = null,
    val dcChargingMaxPowerKw: Power? = null,
    val chargingVoltageRangeV: IntRange? = null,
    val chargingCurrentRangeA: IntRange? = null,
    val chargePortCount: Int? = null,
    
    // Connector information
    val connectorTypes: Set<ConnectorType>? = null,
    
    // Vehicle dimensions and weight for aerodynamics and rolling resistance
    val wheelDiameterMm: Int? = null,
    val tireWidthMm: Int? = null,
    
    // Data quality and provenance (these are not typically overridden)
    val confidence: Double? = null,
    val notes: String? = null
)