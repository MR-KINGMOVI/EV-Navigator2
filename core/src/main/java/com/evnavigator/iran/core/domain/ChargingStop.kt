package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a charging stop during a trip.
 *
 * @param id Unique identifier for the charging stop
 * @param charger Charger used for this stop
 * @param startTime Unix timestamp when charging started
 * @param endTime Unix timestamp when charging ended
 * @param energyAddedKWh Energy added to the battery in kilowatt-hours
 * @param costIraniRials Cost of charging in Iranian Rials
 * @param isCompleted Whether the charging session has been completed
 */
@Parcelize
data class ChargingStop(
    val id: String,
    val charger: Charger,
    val startTime: Long,
    val endTime: Long,
    val energyAddedKWh: Double,
    val costIraniRials: Double,
    val isCompleted: Boolean = false
) : Parcelable {
    
    init {
        require(endTime >= startTime) { "End time must be after or equal to start time" }
        require(energyAddedKWh >= 0) { "Energy added cannot be negative" }
        require(costIraniRials >= 0) { "Cost cannot be negative" }
    }

    /**
     * Calculates the duration of the charging session in seconds
     */
    val durationSeconds: Long
        get() = endTime - startTime

    /**
     * Gets the duration of the charging session in minutes
     */
    val durationMinutes: Double
        get() = durationSeconds / 60.0

    /**
     * Calculates the average charging power in kilowatts
     */
    val averagePowerKw: Double
        get() = if (durationSeconds > 0) {
            energyAddedKWh * 3600.0 / durationSeconds
        } else {
            0.0
        }

    /**
     * Gets the charging efficiency percentage (energy added vs energy drawn)
     */
    val chargingEfficiencyPercentage: Double
        get() = if (energyAddedKWh > 0) {
            100.0 // Simplified - actual implementation would require more data
        } else {
            0.0
        }

    /**
     * Checks if this was a fast charging session (power > 50kW)
     */
    val isFastCharge: Boolean
        get() = averagePowerKw > 50.0

    /**
     * Gets the cost per kilowatt-hour in Iranian Rials
     */
    val costPerKWhRials: Double
        get() = if (energyAddedKWh > 0) {
            costIraniRials / energyAddedKWh
        } else {
            0.0
        }
}