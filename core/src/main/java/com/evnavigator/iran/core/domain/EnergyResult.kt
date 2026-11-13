package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the energy consumption and efficiency results for a route or trip.
 *
 * @param totalEnergyConsumedKWh Total energy consumed in kilowatt-hours
 * @param totalDistanceMeters Total distance traveled in meters
 * @param averageConsumptionKWhPer100km Average energy consumption per 100 km
 * @param efficiencyRating Efficiency rating (0-100 scale)
 * @param batteryDrainPercentage Battery percentage drained during the trip
 * @param regenerativeEnergyKWh Energy recovered through regenerative braking
 * @param costIraniRials Total cost in Iranian Rials
 * @param chargingStops Number of charging stops required
 * @param estimatedChargingTimeSeconds Estimated time spent charging
 */
@Parcelize
data class EnergyResult(
    val totalEnergyConsumedKWh: Double,
    val totalDistanceMeters: Double,
    val averageConsumptionKWhPer100km: Double,
    val efficiencyRating: Int,
    val batteryDrainPercentage: Double,
    val regenerativeEnergyKWh: Double,
    val costIraniRials: Double,
    val chargingStops: Int,
    val estimatedChargingTimeSeconds: Long
) : Parcelable {
    
    init {
        require(totalEnergyConsumedKWh >= 0) { "Energy consumed cannot be negative" }
        require(totalDistanceMeters >= 0) { "Distance cannot be negative" }
        require(averageConsumptionKWhPer100km >= 0) { "Average consumption cannot be negative" }
        require(efficiencyRating in 0..100) { "Efficiency rating must be between 0 and 100" }
        require(batteryDrainPercentage >= 0 && batteryDrainPercentage <= 100) {
            "Battery drain percentage must be between 0 and 100"
        }
        require(regenerativeEnergyKWh >= 0) { "Regenerative energy cannot be negative" }
        require(costIraniRials >= 0) { "Cost cannot be negative" }
        require(chargingStops >= 0) { "Charging stops cannot be negative" }
        require(estimatedChargingTimeSeconds >= 0) { "Charging time cannot be negative" }
    }

    /**
     * Calculates the energy consumption per kilometer
     */
    val energyPerKilometerKWh: Double
        get() = if (totalDistanceMeters > 0) {
            totalEnergyConsumedKWh / (totalDistanceMeters / 1000.0)
        } else {
            0.0
        }

    /**
     * Gets the distance in kilometers
     */
    val distanceKm: Double
        get() = totalDistanceMeters / 1000.0

    /**
     * Checks if this is an efficient trip (efficiency rating > 75)
     */
    val isEfficientTrip: Boolean
        get() = efficiencyRating > 75

    /**
     * Calculates the cost per kilometer in Iranian Rials
     */
    val costPerKilometerRials: Double
        get() = if (distanceKm > 0) {
            costIraniRials / distanceKm
        } else {
            0.0
        }

    /**
     * Gets the charging time in minutes
     */
    val chargingTimeMinutes: Double
        get() = estimatedChargingTimeSeconds / 60.0
}