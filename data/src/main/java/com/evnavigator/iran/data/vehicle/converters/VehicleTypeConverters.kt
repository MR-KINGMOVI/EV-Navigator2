package com.evnavigator.iran.data.vehicle.converters

import androidx.room.TypeConverter
import com.evnavigator.iran.core.vehicle.*
import com.evnavigator.iran.core.energy.model.*

/**
 * Type converters for Room database to handle complex objects.
 */
class VehicleTypeConverters {
    
    @TypeConverter
    fun fromPower(power: Power?): Double? {
        return power?.watts
    }
    
    @TypeConverter
    fun toPower(watts: Double?): Power? {
        return watts?.let { Power(it) }
    }
    
    @TypeConverter
    fun fromEnergy(energy: Energy?): Double? {
        return energy?.wattHours
    }
    
    @TypeConverter
    fun toEnergy(wh: Double?): Energy? {
        return wh?.let { Energy(it) }
    }
    
    @TypeConverter
    fun fromMass(mass: Mass?): Double? {
        return mass?.kilograms
    }
    
    @TypeConverter
    fun toMass(kg: Double?): Mass? {
        return kg?.let { Mass(it) }
    }
    
    @TypeConverter
    fun fromEfficiency(efficiency: Efficiency?): Double? {
        return efficiency?.fraction
    }
    
    @TypeConverter
    fun toEfficiency(fraction: Double?): Efficiency? {
        return fraction?.let { Efficiency(it) }
    }
    
    @TypeConverter
    fun fromPowertrainType(type: PowertrainType?): String? {
        return type?.name
    }
    
    @TypeConverter
    fun toPowertrainType(name: String?): PowertrainType? {
        return name?.let { PowertrainType.valueOf(it) }
    }
    
    @TypeConverter
    fun fromDrivetrainType(type: DrivetrainType?): String? {
        return type?.name
    }
    
    @TypeConverter
    fun toDrivetrainType(name: String?): DrivetrainType? {
        return name?.let { DrivetrainType.valueOf(it) }
    }
    
    @TypeConverter
    fun fromConnectorTypes(types: Set<ConnectorType>?): String? {
        return types?.joinToString(",") { it.name } 
    }
    
    @TypeConverter
    fun toConnectorTypes(str: String?): Set<ConnectorType> {
        return str?.split(",")?.mapNotNull { 
            try {
                ConnectorType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }?.toSet() ?: emptySet()
    }
    
    @TypeConverter
    fun fromDataQuality(quality: DataQuality?): String? {
        return quality?.name
    }
    
    @TypeConverter
    fun toDataQuality(name: String?): DataQuality? {
        return name?.let { DataQuality.valueOf(it) }
    }
    
    @TypeConverter
    fun fromValidationStatus(status: ValidationStatus?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun toValidationStatus(name: String?): ValidationStatus? {
        return name?.let { ValidationStatus.valueOf(it) }
    }
    
    @TypeConverter
    fun fromIntRange(range: IntRange?): String? {
        return range?.let { "${it.first}-${it.last}" }
    }
    
    @TypeConverter
    fun toIntRange(str: String?): IntRange? {
        return str?.split("-")?.takeIf { it.size == 2 }?.let {
            try {
                IntRange(it[0].toInt(), it[1].toInt())
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
    
    @TypeConverter
    fun fromStringSet(set: Set<String>?): String? {
        return set?.joinToString(",")
    }
    
    @TypeConverter
    fun toStringSet(str: String?): Set<String> {
        return str?.split(",")?.toSet() ?: emptySet()
    }
}