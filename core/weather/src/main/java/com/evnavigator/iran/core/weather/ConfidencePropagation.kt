package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.model.WeatherCondition
import com.evnavigator.iran.core.weather.model.TrafficClass
import java.time.Instant

/**
 * Handles uncertainty and confidence propagation for weather and traffic data.
 *
 * This component ensures that uncertainty and confidence values are properly tracked,
 * propagated, and combined throughout the environmental intelligence system without
 * introducing artificial precision or silently converting UNKNOWN to known values.
 */
class ConfidencePropagation {
    
    /**
     * Propagates confidence from source data to destination based on freshness and reliability
     *
     * @param sourceConfidence Confidence of the source data (0.0 to 1.0)
     * @param sourceFreshness Freshness of the source data
     * @param reliabilityFactor Reliability factor of the processing step (0.0 to 1.0)
     * @return Propagated confidence for destination
     */
    fun propagateConfidence(
        sourceConfidence: Double,
        sourceFreshness: Freshness,
        reliabilityFactor: Double = 1.0
    ): Double {
        // Base confidence is reduced based on freshness
        val freshnessMultiplier = getFreshnessMultiplier(sourceFreshness)
        
        // Apply reliability factor of the processing step
        val finalConfidence = sourceConfidence * freshnessMultiplier * reliabilityFactor
        
        // Ensure confidence stays within valid bounds (0.0 to 1.0)
        return finalConfidence.coerceIn(0.0, 1.0)
    }
    
    /**
     * Propagates uncertainty from source data to destination
     *
     * @param sourceUncertainty Uncertainty of the source data (0.0 to 1.0)
     * @param sourceFreshness Freshness of the source data
     * @param processingUncertainty Additional uncertainty introduced by processing
     * @return Propagated uncertainty for destination
     */
    fun propagateUncertainty(
        sourceUncertainty: Double,
        sourceFreshness: Freshness,
        processingUncertainty: Double = 0.0
    ): Double {
        // Base uncertainty is increased based on freshness (stale data introduces more uncertainty)
        val freshnessMultiplier = getFreshnessPenalty(sourceFreshness)
        
        // Combine source uncertainty with processing uncertainty
        val combinedUncertainty = sourceUncertainty * freshnessMultiplier + processingUncertainty
        
        // Ensure uncertainty stays within valid bounds (0.0 to 1.0)
        return combinedUncertainty.coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculates combined confidence from multiple sources
     *
     * @param confidences List of confidence values from different sources (0.0 to 1.0)
     * @param freshnessStates Corresponding freshness states for each source
     * @return Combined confidence value (0.0 to 1.0)
     */
    fun combineConfidence(
        confidences: List<Double>,
        freshnessStates: List<Freshness>
    ): Double {
        if (confidences.isEmpty()) return 0.0
        
        // Weight confidences by freshness
        val weightedConfidences = confidences.zip(freshnessStates).map { (confidence, freshness) ->
            val freshnessMultiplier = getFreshnessMultiplier(freshness)
            confidence * freshnessMultiplier
        }
        
        // Return average of weighted confidences
        return weightedConfidences.average().coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculates combined uncertainty from multiple sources
     *
     * @param uncertainties List of uncertainty values from different sources (0.0 to 1.0)
     * @param freshnessStates Corresponding freshness states for each source
     * @return Combined uncertainty value (0.0 to 1.0)
     */
    fun combineUncertainty(
        uncertainties: List<Double>,
        freshnessStates: List<Freshness>
    ): Double {
        if (uncertainties.isEmpty()) return 1.0
        
        // Weight uncertainties by freshness penalties (stale data increases uncertainty)
        val weightedUncertainties = uncertainties.zip(freshnessStates).map { (uncertainty, freshness) ->
            val freshnessPenalty = getFreshnessPenalty(freshness)
            uncertainty * freshnessPenalty
        }
        
        // Return average of weighted uncertainties (but with some aggregation logic)
        return weightedUncertainties.average().coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculates the confidence penalty based on freshness state
     *
     * @param freshness Freshness state
     * @return Multiplier for confidence (1.0 = full confidence, < 1.0 = reduced confidence)
     */
    private fun getFreshnessMultiplier(freshness: Freshness): Double {
        return when (freshness) {
            Freshness.FRESH -> 1.0
            Freshness.CACHED -> 0.9
            Freshness.STALE_BUT_USABLE -> 0.8
            Freshness.EXPIRED -> 0.5
            Freshness.INVALID -> 0.0
            Freshness.UNKNOWN -> 0.3 // Conservative estimate for unknown freshness
        }
    }
    
    /**
     * Calculates the uncertainty penalty based on freshness state
     *
     * @param freshness Freshness state
     * @return Multiplier for uncertainty (1.0 = base uncertainty, > 1.0 = increased uncertainty)
     */
    private fun getFreshnessPenalty(freshness: Freshness): Double {
        return when (freshness) {
            Freshness.FRESH -> 1.0
            Freshness.CACHED -> 1.1
            Freshness.STALE_BUT_USABLE -> 1.2
            Freshness.EXPIRED -> 2.0
            Freshness.INVALID -> 3.0
            Freshness.UNKNOWN -> 2.5 // Conservative estimate for unknown freshness
        }
    }
    
    /**
     * Propagates uncertainty and confidence through environmental data processing chain
     *
     * @param sourceData Source environmental data with confidence and uncertainty
     * @param processingStepDescription Description of the processing step
     * @param reliabilityFactor Reliability factor of this processing step (0.0 to 1.0)
     * @return Processed data with updated confidence and uncertainty
     */
    fun processEnvironmentalData(
        sourceData: EnvironmentalDataWithConfidence,
        processingStepDescription: String,
        reliabilityFactor: Double = 1.0,
        additionalProcessingUncertainty: Double = 0.0
    ): EnvironmentalDataWithConfidence {
        // Propagate confidence
        val propagatedConfidence = propagateConfidence(
            sourceData.confidence, 
            sourceData.freshness, 
            reliabilityFactor
        )
        
        // Propagate uncertainty
        val propagatedUncertainty = propagateUncertainty(
            sourceData.uncertainty,
            sourceData.freshness,
            additionalProcessingUncertainty
        )
        
        return sourceData.copy(
            confidence = propagatedConfidence,
            uncertainty = propagatedUncertainty,
            provenance = "${sourceData.provenance} -> $processingStepDescription"
        )
    }
    
    /**
     * Calculates confidence and uncertainty for combined weather and traffic data
     *
     * @param weatherConfidence Weather data confidence (0.0 to 1.0)
     * @param weatherUncertainty Weather data uncertainty (0.0 to 1.0)
     * @param trafficConfidence Traffic data confidence (0.0 to 1.0)
     * @param trafficUncertainty Traffic data uncertainty (0.0 to 1.0)
     * @param weatherFreshness Weather data freshness
     * @param trafficFreshness Traffic data freshness
     * @return Combined confidence and uncertainty values
     */
    fun combineWeatherAndTrafficConfidence(
        weatherConfidence: Double,
        weatherUncertainty: Double,
        trafficConfidence: Double,
        trafficUncertainty: Double,
        weatherFreshness: Freshness,
        trafficFreshness: Freshness
    ): ConfidenceUncertaintyPair {
        // Calculate weighted confidences and uncertainties
        val weatherWeightedConf = weatherConfidence * getFreshnessMultiplier(weatherFreshness)
        val trafficWeightedConf = trafficConfidence * getFreshnessMultiplier(trafficFreshness)
        
        val weatherWeightedUnc = weatherUncertainty * getFreshnessPenalty(weatherFreshness)
        val trafficWeightedUnc = trafficUncertainty * getFreshnessPenalty(trafficFreshness)
        
        // Combine weighted values
        val combinedConfidence = (weatherWeightedConf + trafficWeightedConf) / 2.0
        val combinedUncertainty = (weatherWeightedUnc + trafficWeightedUnc) / 2.0
        
        return ConfidenceUncertaintyPair(
            confidence = combinedConfidence.coerceIn(0.0, 1.0),
            uncertainty = combinedUncertainty.coerceIn(0.0, 1.0)
        )
    }
}

/**
 * Data class representing environmental data with associated confidence and uncertainty
 */
data class EnvironmentalDataWithConfidence(
    val data: Any,
    val confidence: Double, // Confidence level (0.0 to 1.0)
    val uncertainty: Double, // Uncertainty level (0.0 to 1.0) 
    val freshness: Freshness, // Freshness state of the data
    val provenance: String // Source or method used to create this data
)

/**
 * Data class representing a pair of confidence and uncertainty values
 */
data class ConfidenceUncertaintyPair(
    val confidence: Double,
    val uncertainty: Double
) {
    companion object {
        /**
         * Creates an unknown confidence/uncertainty pair
         */
        fun unknown(): ConfidenceUncertaintyPair {
            return ConfidenceUncertaintyPair(0.0, 1.0)
        }
    }
}

/**
 * Utility functions for confidence and uncertainty handling
 */
object ConfidenceUtils {
    
    /**
     * Validates that a confidence value is within valid bounds (0.0 to 1.0)
     */
    fun validateConfidence(confidence: Double): Boolean {
        return confidence >= 0.0 && confidence <= 1.0
    }
    
    /**
     * Validates that an uncertainty value is within valid bounds (0.0 to 1.0)
     */
    fun validateUncertainty(uncertainty: Double): Boolean {
        return uncertainty >= 0.0 && uncertainty <= 1.0
    }
    
    /**
     * Normalizes a confidence value to valid range [0.0, 1.0]
     */
    fun normalizeConfidence(confidence: Double): Double {
        return confidence.coerceIn(0.0, 1.0)
    }
    
    /**
     * Normalizes an uncertainty value to valid range [0.0, 1.0]
     */
    fun normalizeUncertainty(uncertainty: Double): Double {
        return uncertainty.coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculates confidence from a set of observations using weighted average
     */
    fun calculateObservationConfidence(
        observations: List<EnvironmentalObservation>,
        weights: List<Double>
    ): Double {
        if (observations.isEmpty() || weights.isEmpty()) return 0.0
        
        val validWeights = weights.take(observations.size)
        val validObservations = observations.take(validWeights.size)
        
        val weightedConfidences = validObservations.zip(validWeights).map { (obs, weight) ->
            obs.confidence * weight
        }
        
        return if (weightedConfidences.isNotEmpty()) {
            weightedConfidences.average().coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }
}

/**
 * Represents an environmental observation with confidence and uncertainty
 */
data class EnvironmentalObservation(
    val value: Any,
    val confidence: Double, // Confidence level (0.0 to 1.0)
    val uncertainty: Double, // Uncertainty level (0.0 to 1.0)
    val freshness: Freshness, // Freshness state of the observation
    val timestamp: Instant, // When this observation was made
    val provenance: String // Source or method used to create this observation
)

/**
 * Calculates confidence and uncertainty based on source quality metrics
 */
class ConfidenceQualityModel {
    
    /**
     * Calculates confidence based on provider quality metrics
     *
     * @param providerReliability Provider reliability score (0.0 to 1.0)
     * @param dataFreshness Freshness of the data
     * @param validationAccuracy Validation accuracy (0.0 to 1.0)
     * @return Calculated confidence level
     */
    fun calculateProviderConfidence(
        providerReliability: Double,
        dataFreshness: Freshness,
        validationAccuracy: Double = 1.0
    ): Double {
        val freshnessMultiplier = getFreshnessMultiplier(dataFreshness)
        return (providerReliability * validationAccuracy * freshnessMultiplier).coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculates uncertainty based on provider quality metrics
     *
     * @param providerReliability Provider reliability score (0.0 to 1.0)
     * @param dataFreshness Freshness of the data
     * @param validationAccuracy Validation accuracy (0.0 to 1.0)
     * @return Calculated uncertainty level
     */
    fun calculateProviderUncertainty(
        providerReliability: Double,
        dataFreshness: Freshness,
        validationAccuracy: Double = 1.0
    ): Double {
        val freshnessPenalty = getFreshnessPenalty(dataFreshness)
        // Lower reliability and accuracy increase uncertainty
        val baseUncertainty = (1.0 - providerReliability) * (1.0 - validationAccuracy)
        return (baseUncertainty * freshnessPenalty).coerceIn(0.0, 1.0)
    }
    
    private fun getFreshnessMultiplier(freshness: Freshness): Double {
        return when (freshness) {
            Freshness.FRESH -> 1.0
            Freshness.CACHED -> 0.9
            Freshness.STALE_BUT_USABLE -> 0.8
            Freshness.EXPIRED -> 0.5
            Freshness.INVALID -> 0.0
            Freshness.UNKNOWN -> 0.3
        }
    }
    
    private fun getFreshnessPenalty(freshness: Freshness): Double {
        return when (freshness) {
            Freshness.FRESH -> 1.0
            Freshness.CACHED -> 1.1
            Freshness.STALE_BUT_USABLE -> 1.2
            Freshness.EXPIRED -> 2.0
            Freshness.INVALID -> 3.0
            Freshness.UNKNOWN -> 2.5
        }
    }
}