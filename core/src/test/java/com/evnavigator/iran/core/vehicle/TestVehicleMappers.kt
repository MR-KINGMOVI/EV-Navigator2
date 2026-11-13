package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power

data class VehicleTestProfile(
    val id: String,
    val name: String,
    val curbMassKg: Mass,
    val nominalBatteryCapacityWh: Energy,
    val usableBatteryCapacityWh: Energy,
    val dragCoefficient: Double = 0.32,
    val frontalAreaM2: Double = 2.2,
    val rollingResistanceCoefficient: Double = 0.012,
    val motorCount: Int = 1
)

object VehicleToVehicleProfileMapper {
    fun mapToVehicleProfile(vehicle: Vehicle, payloadMassKg: Double = 0.0): VehicleTestProfile {
        return VehicleTestProfile(
            id = vehicle.id.id,
            name = vehicle.name,
            curbMassKg = vehicle.curbMassKg,
            nominalBatteryCapacityWh = vehicle.nominalBatteryCapacityWh,
            usableBatteryCapacityWh = vehicle.usableBatteryCapacityWh,
            dragCoefficient = vehicle.dragCoefficient ?: 0.32,
            frontalAreaM2 = vehicle.frontalAreaM2 ?: 2.2,
            rollingResistanceCoefficient = vehicle.rollingResistanceCoefficient ?: 0.012,
            motorCount = vehicle.motorCount
        )
    }
}

data class VehicleTestEntity(
    val vehicle: Vehicle
) {
    val id: String get() = vehicle.id.id
    val name: String get() = vehicle.name
    val manufacturer: String get() = vehicle.id.manufacturer
    val model: String get() = vehicle.id.model
    val generation: String get() = vehicle.id.generation
    val modelYear: Int get() = vehicle.id.modelYear
    val trim: String get() = vehicle.id.trim
    val market: String? get() = vehicle.id.market
    val powertrainType: PowertrainType? get() = vehicle.id.powertrainType
    val curbMassKg: Mass get() = vehicle.curbMassKg
    val grossVehicleMassKg: Mass? get() = vehicle.grossVehicleMassKg
    val nominalBatteryCapacityWh: Energy get() = vehicle.nominalBatteryCapacityWh
    val usableBatteryCapacityWh: Energy get() = vehicle.usableBatteryCapacityWh
    val motorCount: Int get() = vehicle.motorCount
    val connectorTypes: Set<ConnectorType> get() = vehicle.connectorTypes
    val source: String get() = vehicle.source
    val sourceVersion: String? get() = vehicle.sourceVersion
    val sourceReference: String? get() = vehicle.sourceReference
    val confidence: Double get() = vehicle.confidence
    val dataQuality: DataQuality get() = vehicle.dataQuality
    val validationStatus: ValidationStatus get() = vehicle.validationStatus
    val schemaVersion: String get() = vehicle.schemaVersion
    val dataVersion: String get() = vehicle.dataVersion
    val vehicleRecordVersion: String get() = vehicle.vehicleRecordVersion

    fun toDomainModel(): Vehicle = vehicle
}

fun Vehicle.toEntity(): VehicleTestEntity = VehicleTestEntity(this)
