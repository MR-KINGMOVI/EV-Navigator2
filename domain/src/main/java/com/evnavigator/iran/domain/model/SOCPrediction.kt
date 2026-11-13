package com.evnavigator.iran.domain.model

/**
 * Represents a prediction of State of Charge (SoC) over time.
 *
 * @param timestamp Time of the prediction
 * @param soc State of Charge percentage (0-100)
 * @param distanceTraveled Distance traveled since start
 * @param energyConsumed Energy consumed so far
 * @param estimatedRemainingTime Estimated time remaining until destination
 * @param estimatedRemainingDistance Remaining distance to destination
 * @param predictedBatteryState Predicted battery state at this point
 */
data class SOCPrediction(
    val timestamp: Long, // Unix timestamp
    val soc: Double, // State of Charge percentage (0-100)
    val distanceTraveled: Double, // in km
    val energyConsumed: Double, // in kWh
    val estimatedRemainingTime: Long, // in seconds
    val estimatedRemainingDistance: Double, // in km
    val predictedBatteryState: Battery
) {
    init {
        require(soc in 0.0..100.0) { "SoC must be between 0 and 100" }
    }
}