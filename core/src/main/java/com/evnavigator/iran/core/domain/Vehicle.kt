package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents an electric vehicle with its specifications and current state.
 *
 * @param id Unique identifier for the vehicle
 * @param make Manufacturer of the vehicle
 * @param model Model name of the vehicle
 * @param year Year of manufacture
 * @param batteryCapacityKWh Total battery capacity in kilowatt-hours
 * @param currentBatteryLevelKWh Current battery level in kilowatt-hours
 * @param maxChargingPowerKw Maximum charging power in kilowatts
 * @param rangeKm Estimated driving range in kilometers
 * @param isCharging Whether the vehicle is currently charging
 */
@Parcelize
data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    val year: Int,
    val batteryCapacityKWh: Double,
    val currentBatteryLevelKWh: Double,
    val maxChargingPowerKw: Double,
    val rangeKm: Double,
    val isCharging: Boolean = false
) : Parcelable {
    
    init {
        require(batteryCapacityKWh > 0) { "Battery capacity must be positive" }
        require(currentBatteryLevelKWh >= 0 && currentBatteryLevelKWh <= batteryCapacityKWh) {
            "Current battery level must be between 0 and battery capacity"
        }
        require(maxChargingPowerKw > 0) { "Max charging power must be positive" }
        require(rangeKm >= 0) { "Range must be non-negative" }
    }

    /**
     * Calculates the current battery percentage
     */
    val batteryPercentage: Double
        get() = (currentBatteryLevelKWh / batteryCapacityKWh) * 100.0

    /**
     * Checks if the vehicle has low battery (below 20%)
     */
    val isLowBattery: Boolean
        get() = batteryPercentage < 20.0
}