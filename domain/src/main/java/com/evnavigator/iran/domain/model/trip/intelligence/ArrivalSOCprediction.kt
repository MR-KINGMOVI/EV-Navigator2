package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.model.Soc

/**
 * Result of arrival state of charge predictions for a trip.
 * Carries bounded uncertainty interval [lowerBoundSocPercent, upperBoundSocPercent] and calibrated confidence tier.
 */
data class ArrivalSocPrediction(
    val predictedArrivalSoc: Soc,
    val isAccurate: Boolean = false,
    val confidence: Double? = null,
    val lowerBoundSocPercent: Double? = null,
    val upperBoundSocPercent: Double? = null,
    val confidenceTier: String = if (isAccurate) "HIGH_CONFIDENCE" else "ESTIMATED_UNCERTAIN"
) {
    val confidenceLevel: Double
        get() = confidence ?: 0.0
}