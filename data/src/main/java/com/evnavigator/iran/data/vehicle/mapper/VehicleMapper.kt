package com.evnavigator.iran.data.vehicle.mapper

import com.evnavigator.iran.core.vehicle.*
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity

/**
 * Mappers between Vehicle domain model and VehicleEntity database entity.
 */
object VehicleMapper {
    
    /**
     * Maps a VehicleEntity to a Vehicle domain model
     */
    fun VehicleEntity.toDomainModel(): Vehicle {
        return Vehicle(
            id = VehicleId(
                manufacturer = manufacturer,
                model = model,
                generation = generation,
                modelYear = modelYear,
                trim = trim,
                market = market,
                powertrainType = PowertrainType.valueOf(powertrainType)
            ),
            name = name,
            
            // Physical characteristics  
            curbMassKg = com.evnavigator.iran.core.energy.model.Mass(curbMassKg),
            grossVehicleMassKg = grossVehicleMassKg?.let { com.evnavigator.iran.core.energy.model.Mass(it) },
            lengthMm = lengthMm,
            widthMm = widthMm,
            heightMm = heightMm,
            wheelbaseMm = wheelbaseMm,
            groundClearanceMm = groundClearanceMm,
            
            // Aerodynamic properties
            dragCoefficient = dragCoefficient,
            frontalAreaM2 = frontalAreaM2,
            
            // Rolling resistance  
            rollingResistanceCoefficient = rollingResistanceCoefficient,
            
            // Battery properties
            nominalBatteryCapacityWh = com.evnavigator.iran.core.energy.model.Energy(nominalBatteryCapacityWh),
            usableBatteryCapacityWh = com.evnavigator.iran.core.energy.model.Energy(usableBatteryCapacityWh),
            batteryChemistry = batteryChemistry,
            nominalVoltageV = nominalVoltageV,
            batteryConfiguration = batteryConfiguration,
            
            // Motor/drivetrain specifications  
            motorCount = motorCount,
            frontMotorPowerKw = frontMotorPowerKw?.let { com.evnavigator.iran.core.energy.model.Power(it) },
            rearMotorPowerKw = rearMotorPowerKw?.let { com.evnavigator.iran.core.energy.model.Power(it) },
            peakMotorPowerKw = peakMotorPowerKw?.let { com.evnavigator.iran.core.energy.model.Power(it) },
            continuousMotorPowerKw = continuousMotorPowerKw?.let { com.evnavigator.iran.core.energy.model.Power(it) },
            torqueNm = torqueNm,
            drivetrainType = DrivetrainType.valueOf(drivetrainType),
            
            // Energy-related parameters for Phase 5
            baselineConsumptionWhPer100Km = baselineConsumptionWhPer100Km,
            
            // Charging capabilities
            acChargingMaxPowerKw = acChargingMaxPowerKw?.let { com.evnavigator.iran.core.energy.model.Power(it) },
            dcChargingMaxPowerKw = dcChargingMaxPowerKw?.let { com.evnavigator.iran.core.energy.model.Power(it) },
            chargingVoltageRangeV = chargingVoltageRangeV?.let { parseIntRange(it) },
            chargingCurrentRangeA = chargingCurrentRangeA?.let { parseIntRange(it) },
            chargePortCount = chargePortCount,
            
            // Connector information
            connectorTypes = toConnectorTypes(connectorTypes),
            
            // Vehicle dimensions and weight for aerodynamics and rolling resistance
            wheelDiameterMm = wheelDiameterMm,
            tireWidthMm = tireWidthMm,
            
            // Data quality and provenance
            dataQuality = DataQuality.valueOf(dataQuality),
            source = source,
            sourceVersion = sourceVersion,
            retrievedDate = retrievedDate,
            confidence = confidence,
            validationStatus = ValidationStatus.valueOf(validationStatus),
            notes = notes
        )
    }
    
    /**
     * Maps a Vehicle domain model to a VehicleEntity
     */
    fun Vehicle.toEntity(): VehicleEntity {
        return VehicleEntity(
            id = id.id,
            
            // Basic identification  
            name = name,
            
            // Manufacturer and model information
            manufacturer = id.manufacturer,
            model = id.model,
            generation = id.generation,
            modelYear = id.modelYear,
            trim = id.trim,
            market = id.market,
            powertrainType = id.powertrainType.name,
            
            // Physical characteristics  
            curbMassKg = curbMassKg.kilograms,
            grossVehicleMassKg = grossVehicleMassKg?.kilograms,
            lengthMm = lengthMm,
            widthMm = widthMm,
            heightMm = heightMm,
            wheelbaseMm = wheelbaseMm,
            groundClearanceMm = groundClearanceMm,
            
            // Aerodynamic properties
            dragCoefficient = dragCoefficient,
            frontalAreaM2 = frontalAreaM2,
            
            // Rolling resistance  
            rollingResistanceCoefficient = rollingResistanceCoefficient,
            
            // Battery properties
            nominalBatteryCapacityWh = nominalBatteryCapacityWh.wattHours,
            usableBatteryCapacityWh = usableBatteryCapacityWh.wattHours,
            batteryChemistry = batteryChemistry,
            nominalVoltageV = nominalVoltageV,
            batteryConfiguration = batteryConfiguration,
            
            // Motor/drivetrain specifications  
            motorCount = motorCount,
            frontMotorPowerKw = frontMotorPowerKw?.watts,
            rearMotorPowerKw = rearMotorPowerKw?.watts,
            peakMotorPowerKw = peakMotorPowerKw?.watts,
            continuousMotorPowerKw = continuousMotorPowerKw?.watts,
            torqueNm = torqueNm,
            drivetrainType = drivetrainType.name,
            
            // Energy-related parameters for Phase 5
            baselineConsumptionWhPer100Km = baselineConsumptionWhPer100Km,
            
            // Charging capabilities
            acChargingMaxPowerKw = acChargingMaxPowerKw?.watts,
            dcChargingMaxPowerKw = dcChargingMaxPowerKw?.watts,
            chargingVoltageRangeV = chargingVoltageRangeV?.let { "${it.first}-${it.last}" },
            chargingCurrentRangeA = chargingCurrentRangeA?.let { "${it.first}-${it.last}" },
            chargePortCount = chargePortCount,
            
            // Connector information
            connectorTypes = connectorTypes.joinToString(",") { it.name },
            
            // Vehicle dimensions and weight for aerodynamics and rolling resistance
            wheelDiameterMm = wheelDiameterMm,
            tireWidthMm = tireWidthMm,
            
            // Data quality and provenance
            dataQuality = dataQuality.name,
            source = source,
            sourceVersion = sourceVersion,
            retrievedDate = retrievedDate,
            confidence = confidence,
            validationStatus = validationStatus.name,
            notes = notes,
            
            // Versioning information
            schemaVersion = "1.0",
            dataVersion = "1.0", 
            vehicleRecordVersion = "1.0",
            
            // Timestamps (handled by entity)
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * Parses a range string like "min-max" into an IntRange
     */
    private fun parseIntRange(rangeStr: String): IntRange? {
        return rangeStr.split("-").takeIf { it.size == 2 }?.let {
            try {
                IntRange(it[0].toInt(), it[1].toInt())
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
    
    /**
     * Converts a comma-separated string of connector type names to a Set<ConnectorType>
     */
    private fun toConnectorTypes(str: String): Set<ConnectorType> {
        return str.split(",").mapNotNull { 
            try {
                ConnectorType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toSet()
    }
}

// Top-level extensions for package-level imports
fun VehicleEntity.toDomainModel(): Vehicle = VehicleMapper.run { this@toDomainModel.toDomainModel() }
fun Vehicle.toEntity(): VehicleEntity = VehicleMapper.run { this@toEntity.toEntity() }
