package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a vehicle battery with its specifications and current state.
 *
 * @param capacityKWh Total battery capacity in kilowatt-hours
 * @param currentLevelKWh Current battery level in kilowatt-hours
 * @param maxChargingPowerKw Maximum charging power in kilowatts
 * @param chargeState Current charging state
 */
@Parcelize
data class Battery(
    val capacityKWh: Double,
    val currentLevelKWh: Double,
    val maxChargingPowerKw: Double,
    val chargeState: ChargeState = ChargeState.DISCHARGING
) : Parcelable {
    
    init {
        require(capacityKWh > 0) { "Battery capacity must be positive" }
        require(currentLevelKWh >= 0 && currentLevelKWh <= capacityKWh) {
            "Current level must be between 0 and capacity"
        }
        require(maxChargingPowerKw > 0) { "Max charging power must be positive" }
    }

    /**
     * Calculates the battery percentage
     */
    val percentage: Double
        get() = (currentLevelKWh / capacityKWh) * 100.0

    /**
     * Checks if the battery is low (below 20%)
     */
    val isLow: Boolean
        get() = percentage < 20.0

    /**
     * Checks if the battery is full (above 95%)
     */
    val isFull: Boolean
        get() = percentage > 95.0
}

/**
 * Enum representing the charging state of a battery
 */
enum class ChargeState {
    CHARGING,
    DISCHARGING,
    FULL,
    IDLE
}