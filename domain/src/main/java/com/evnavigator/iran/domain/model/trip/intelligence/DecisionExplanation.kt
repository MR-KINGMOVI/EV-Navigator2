package com.evnavigator.iran.domain.model.trip.intelligence

/**
 * Explanation of the decision made by trip intelligence.
 */
data class DecisionExplanation(
    val keyInsight: String,  // A brief summary of what drove the decision
    val supportingDetails: List<String>, // Detailed reasoning behind the insight
    val confidenceLevel: Double? = null, // Confidence in this explanation (0-100%)
    val dataQuality: DataQualityAndFreshness? = null, // Quality of data used for this explanation
    val assumptions: PredictionAssumptions? = null  // Assumptions made during reasoning
)