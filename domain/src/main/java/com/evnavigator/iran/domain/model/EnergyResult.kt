package com.evnavigator.iran.domain.model

/**
 * Represents the energy consumption result for a trip or segment.
 *
 * @param energyConsumed Energy consumed in kWh
 * @param distanceTraveled Distance traveled in km
 * @param averageSpeed Average speed in km/h
 * @param timeElapsed Time elapsed in seconds
 * @param efficiency Efficiency in kWh per 100 km
 * @param regenerativeEnergy Regenerative energy recovered in kWh
 * @param auxiliaryConsumption Auxiliary power consumption in kWh
 */
data class EnergyResult(
    val energyConsumed: Double, // in kWh
    val distanceTraveled: Double, // in km
    val averageSpeed: Speed, // in km/h
    val timeElapsed: Long, // in seconds
    val efficiency: Double, // in kWh per 100 km
    val regenerativeEnergy: Double, // in kWh
    val auxiliaryConsumption: Double // in kWh
) {
    /**
     * Calculates the energy consumption per kilometer.
     */
    val energyPerKilometer: Double
        get() = if (distanceTraveled > 0) energyConsumed / distanceTraveled else 0.0

    /**
     * Calculates the percentage of regenerative energy compared to total consumed.
     */
    val regenerativePercentage: Double
        get() = if (energyConsumed > 0) (regenerativeEnergy / energyConsumed) * 100 else 0.0
}