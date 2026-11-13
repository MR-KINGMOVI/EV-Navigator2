package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a prediction of battery state of charge (SOC) over time.
 *
 * @param timestamp Unix timestamp for the prediction point
 * @param socPercentage Battery state of charge as percentage
 * @param predictedRangeKm Predicted remaining range in kilometers
 * @param estimatedTimeToDestinationSeconds Estimated time to reach destination
 * @param isCharging Whether the vehicle is currently charging
 */
@Parcelize
data class SOCPrediction(
    val timestamp: Long,
    val socPercentage: Double,
    val predictedRangeKm: Double,
    val estimatedTimeToDestinationSeconds: Long,
    val isCharging: Boolean = false
) : Parcelable {
    
    init {
        require(socPercentage >= 0 && socPercentage <= 100) {
            "SOC percentage must be between 0 and 100"
        }
        require(predictedRangeKm >= 0) { "Predicted range cannot be negative" }
        require(estimatedTimeToDestinationSeconds >= 0) {
            "Estimated time to destination cannot be negative"
        }
    }

    /**
     * Calculates the predicted energy consumption rate (kWh/100km)
     */
    val energyConsumptionRateKWhPer100km: Double
        get() = if (predictedRangeKm > 0) {
            // Simplified calculation - in reality, this would be based on vehicle and conditions
            15.0 // Default average for demonstration
        } else {
            0.0
        }

    /**
     * Checks if the battery level is critically low (below 10%)
     */
    val isCriticallyLow: Boolean
        get() = socPercentage < 10.0

    /**
     * Checks if the battery is in a good state (between 20-80%)
     */
    val isInGoodRange: Boolean
        get() = socPercentage in 20.0..80.0

    /**
     * Gets the time to next charging opportunity based on current consumption rate
     */
    val timeToNextChargingOpportunitySeconds: Long
        get() = if (isCharging) {
            0L // If charging, no need for next charging
        } else {
            // Simplified calculation - in reality, this would use vehicle parameters
            (estimatedTimeToDestinationSeconds * (100.0 - socPercentage) / 50.0).toLong()
        }

    /**
     * Calculates the predicted remaining time in minutes
     */
    val predictedRemainingTimeMinutes: Double
        get() = estimatedTimeToDestinationSeconds / 60.0
}