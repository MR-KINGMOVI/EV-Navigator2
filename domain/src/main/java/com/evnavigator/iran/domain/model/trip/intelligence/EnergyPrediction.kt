package com.evnavigator.iran.domain.model.trip.intelligence

/**
 * Result of energy consumption predictions for a trip.
 * Carries bounded uncertainty interval [lowerBoundWh, upperBoundWh] and explicit dataQuality.
 */
data class EnergyPrediction(
    val predictedConsumptionWh: Double,
    val efficiencyKwhPer100km: Double? = null,
    val isAccurate: Boolean = false,
    val lowerBoundWh: Double? = null,
    val upperBoundWh: Double? = null,
    val dataQuality: String = if (isAccurate) "VERIFIED" else "ASSUMED"
)