package com.evnavigator.iran.domain.model.trip.intelligence

/**
 * Assumptions made during trip intelligence predictions.
 */
data class PredictionAssumptions(
    val isRouteAssumptionValid: Boolean = true,
    val assumedDrivingStyle: String? = null, // e.g., "eco", "normal", "aggressive"
    val assumedWeatherConditions: String? = null, // e.g., "clear", "rainy", "snowy" 
    val assumedTrafficConditions: String? = null, // e.g., "free flowing", "moderate", "heavy"
    val vehicleConditionAssumption: String? = null, // e.g., "good", "average", "poor"
    val roadGradeAssumption: String? = null, // e.g., "flat", "hilly", "mountainous"
    val batteryDegradationFactor: Double? = null, // Factor to adjust for battery age
    val additionalNotes: String? = null  // Any other relevant assumptions
)