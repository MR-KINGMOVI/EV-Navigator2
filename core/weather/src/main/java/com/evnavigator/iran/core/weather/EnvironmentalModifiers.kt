package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.model.WeatherCondition
import com.evnavigator.iran.core.weather.model.TrafficClass
import java.time.Instant

/**
 * Represents environmental modifiers that can affect energy consumption and vehicle performance.
 *
 * These modifiers are used to adjust the energy calculation models based on real-time weather
 * and traffic conditions without duplicating Phase 5 physics.
 */
data class EnvironmentalModifiers(
    val temperatureModifier: Double? = null, // Multiplier for temperature effects (e.g., HVAC demand)
    val windModifier: Double? = null,        // Multiplier for wind effects (aerodynamic drag)
    val precipitationModifier: Double? = null, // Multiplier for precipitation effects
    val roadConditionModifier: Double? = null, // Multiplier for road condition effects
    val trafficModifier: Double? = null,     // Multiplier for traffic-related effects
    val confidence: Double,                  // Confidence in the modifiers (0.0 to 1.0)
    val uncertainty: Double,                 // Uncertainty in the modifiers (0.0 to 1.0)
    val freshness: Freshness,                // Freshness of the modifier data
    val provenance: String                   // Source or method used to create these modifiers
) {
    companion object {
        /**
         * Creates a default set of environmental modifiers with no active modifications
         */
        fun empty(): EnvironmentalModifiers {
            return EnvironmentalModifiers(
                temperatureModifier = null,
                windModifier = null,
                precipitationModifier = null,
                roadConditionModifier = null,
                trafficModifier = null,
                confidence = 0.0,
                uncertainty = 1.0,
                freshness = Freshness.UNKNOWN,
                provenance = "Empty"
            )
        }
        
        /**
         * Creates a set of modifiers based on weather conditions only
         */
        fun fromWeather(
            temperature: Double?,
            windSpeed: Double?,
            precipitationAmount: Double?,
            condition: WeatherCondition?,
            confidence: Double = 0.5,
            uncertainty: Double = 0.5,
            freshness: Freshness = Freshness.UNKNOWN
        ): EnvironmentalModifiers {
            // Initialize all modifiers to null (no modification)
            var tempMod: Double? = null
            var windMod: Double? = null
            var precipMod: Double? = null
            var roadCondMod: Double? = null
            
            // Apply temperature-based modifier (HVAC load)
            if (temperature != null) {
                // HVAC demand increases with extreme temperatures
                tempMod = when {
                    temperature < 0 -> 1.2  // Cold weather increases HVAC demand
                    temperature > 35 -> 1.3 // Hot weather increases HVAC demand  
                    else -> 1.0             // Normal temperature - no change
                }
            }
            
            // Apply wind-based modifier (aerodynamic drag)
            if (windSpeed != null) {
                // Wind affects aerodynamic drag - headwinds increase resistance
                windMod = if (windSpeed > 10.0) {
                    // Strong winds can increase aerodynamic drag by ~5-10%
                    1.05 + (windSpeed / 100.0) 
                } else {
                    1.0
                }
            }
            
            // Apply precipitation-based modifier (rolling resistance)
            if (precipitationAmount != null && precipitationAmount > 0) {
                // Light rain increases rolling resistance slightly
                precipMod = when {
                    precipitationAmount > 5.0 -> 1.2 // Heavy rain significantly increases resistance
                    precipitationAmount > 1.0 -> 1.1 // Moderate rain increases resistance
                    else -> 1.05 // Light rain slightly increases resistance
                }
            }
            
            // Apply road condition modifier (based on weather condition)
            if (condition != null) {
                roadCondMod = when (condition) {
                    WeatherCondition.SNOW, WeatherCondition.SLEET -> 1.5 // Snow/sleet significantly increases resistance
                    WeatherCondition.HEAVY_RAIN -> 1.3 // Heavy rain increases resistance  
                    WeatherCondition.RAIN -> 1.1 // Light rain increases resistance
                    else -> 1.0 // Clear conditions - no change
                }
            }
            
            return EnvironmentalModifiers(
                temperatureModifier = tempMod,
                windModifier = windMod,
                precipitationModifier = precipMod,
                roadConditionModifier = roadCondMod,
                trafficModifier = null, // Traffic modifiers are handled separately
                confidence = confidence,
                uncertainty = uncertainty,
                freshness = freshness,
                provenance = "WeatherBasedModifiers"
            )
        }
        
        /**
         * Creates a set of modifiers based on traffic conditions only
         */
        fun fromTraffic(
            trafficClass: TrafficClass?,
            congestionRatio: Double?,
            confidence: Double = 0.5,
            uncertainty: Double = 0.5,
            freshness: Freshness = Freshness.UNKNOWN
        ): EnvironmentalModifiers {
            // Initialize all modifiers to null (no modification)
            var trafficMod: Double? = null
            
            // Apply traffic-based modifier (impact on driving behavior)
            if (trafficClass != null) {
                trafficMod = when (trafficClass) {
                    TrafficClass.FREE_FLOW -> 1.0 // No impact
                    TrafficClass.LIGHT -> 1.02   // Slight impact from frequent stops
                    TrafficClass.MODERATE -> 1.05 // Moderate impact from more stops
                    TrafficClass.HEAVY -> 1.1    // Heavy traffic impacts energy consumption
                    TrafficClass.SEVERE -> 1.2  // Severe traffic significantly impacts consumption
                    else -> 1.0 // Unknown - no modification
                }
            } else if (congestionRatio != null) {
                // Fallback to congestion ratio if traffic class is not available
                trafficMod = when {
                    congestionRatio < 0.1 -> 1.0    // Free flow
                    congestionRatio < 0.3 -> 1.02   // Light congestion
                    congestionRatio < 0.5 -> 1.05   // Moderate congestion  
                    congestionRatio < 0.7 -> 1.1    // Heavy congestion
                    else -> 1.2                     // Severe congestion
                }
            }
            
            return EnvironmentalModifiers(
                temperatureModifier = null,
                windModifier = null,
                precipitationModifier = null,
                roadConditionModifier = null,
                trafficModifier = trafficMod,
                confidence = confidence,
                uncertainty = uncertainty,
                freshness = freshness,
                provenance = "TrafficBasedModifiers"
            )
        }
    }
}

/**
 * Combines environmental modifiers from weather and traffic sources
 */
fun combineEnvironmentalModifiers(
    weatherModifiers: EnvironmentalModifiers?,
    trafficModifiers: EnvironmentalModifiers?
): EnvironmentalModifiers {
    
    // If both are null, return empty modifiers
    if (weatherModifiers == null && trafficModifiers == null) {
        return EnvironmentalModifiers.empty()
    }
    
    // If only one is provided, return that one
    if (weatherModifiers == null) {
        return trafficModifiers ?: EnvironmentalModifiers.empty()
    }
    if (trafficModifiers == null) {
        return weatherModifiers
    }
    
    // Combine both modifiers with proper confidence and uncertainty calculations
    
    val combinedTempMod = combineModifierValues(weatherModifiers.temperatureModifier, trafficModifiers.temperatureModifier)
    val combinedWindMod = combineModifierValues(weatherModifiers.windModifier, trafficModifiers.windModifier)
    val combinedPrecipMod = combineModifierValues(weatherModifiers.precipitationModifier, trafficModifiers.precipitationModifier)
    val combinedRoadCondMod = combineModifierValues(weatherModifiers.roadConditionModifier, trafficModifiers.roadConditionModifier)
    val combinedTrafficMod = combineModifierValues(weatherModifiers.trafficModifier, trafficModifiers.trafficModifier)
    
    // Calculate overall confidence as average of both modifier confidences
    val avgConfidence = (weatherModifiers.confidence + trafficModifiers.confidence) / 2.0
    
    // Calculate overall uncertainty as average of both modifier uncertainties  
    val avgUncertainty = (weatherModifiers.uncertainty + trafficModifiers.uncertainty) / 2.0
    
    // Determine overall freshness based on the freshest data source
    val overallFreshness = determineOverallFreshness(weatherModifiers.freshness, trafficModifiers.freshness)
    
    return EnvironmentalModifiers(
        temperatureModifier = combinedTempMod,
        windModifier = combinedWindMod,
        precipitationModifier = combinedPrecipMod,
        roadConditionModifier = combinedRoadCondMod,
        trafficModifier = combinedTrafficMod,
        confidence = avgConfidence,
        uncertainty = avgUncertainty,
        freshness = overallFreshness,
        provenance = "CombinedFromWeatherAndTraffic"
    )
}

/**
 * Combines two modifier values (handles nulls and calculates average when both present)
 */
private fun combineModifierValues(mod1: Double?, mod2: Double?): Double? {
    return when {
        mod1 == null && mod2 == null -> null
        mod1 == null -> mod2
        mod2 == null -> mod1
        else -> (mod1 + mod2) / 2.0 // Average of both modifiers
    }
}

/**
 * Determines the overall freshness between two freshness states
 */
private fun determineOverallFreshness(freshness1: Freshness, freshness2: Freshness): Freshness {
    // FRESH > STALE_BUT_USABLE > EXPIRED > INVALID > UNKNOWN
    val freshnessOrder = listOf(
        Freshness.FRESH,
        Freshness.STALE_BUT_USABLE, 
        Freshness.EXPIRED,
        Freshness.INVALID,
        Freshness.UNKNOWN
    )
    
    val index1 = freshnessOrder.indexOf(freshness1)
    val index2 = freshnessOrder.indexOf(freshness2)
    
    // Return the fresher of the two (lower index means fresher)
    return if (index1 <= index2) freshness1 else freshness2
}

/**
 * Applies environmental modifiers to a base energy consumption value
 *
 * @param baseConsumption Base energy consumption without environmental effects
 * @param modifiers Environmental modifiers to apply
 * @return Modified energy consumption value
 */
fun applyEnvironmentalModifiers(
    baseConsumption: Double,
    modifiers: EnvironmentalModifiers?
): Double {
    if (modifiers == null || baseConsumption <= 0) {
        return baseConsumption
    }
    
    var finalConsumption = baseConsumption
    
    // Apply temperature modifier if present
    modifiers.temperatureModifier?.let { tempMod ->
        finalConsumption *= tempMod
    }
    
    // Apply wind modifier if present  
    modifiers.windModifier?.let { windMod ->
        finalConsumption *= windMod
    }
    
    // Apply precipitation modifier if present
    modifiers.precipitationModifier?.let { precipMod ->
        finalConsumption *= precipMod
    }
    
    // Apply road condition modifier if present
    modifiers.roadConditionModifier?.let { roadCondMod ->
        finalConsumption *= roadCondMod
    }
    
    // Apply traffic modifier if present
    modifiers.trafficModifier?.let { trafficMod ->
        finalConsumption *= trafficMod
    }
    
    return finalConsumption
}

/**
 * Calculates the confidence in combined environmental modifiers based on input confidence levels
 */
fun calculateCombinedConfidence(
    weatherConfidence: Double?,
    trafficConfidence: Double?
): Double {
    return when {
        weatherConfidence == null && trafficConfidence == null -> 0.0
        weatherConfidence == null -> trafficConfidence!!
        trafficConfidence == null -> weatherConfidence
        else -> (weatherConfidence + trafficConfidence) / 2.0
    }
}

/**
 * Calculates the uncertainty in combined environmental modifiers based on input uncertainties
 */
fun calculateCombinedUncertainty(
    weatherUncertainty: Double?,
    trafficUncertainty: Double?
): Double {
    return when {
        weatherUncertainty == null && trafficUncertainty == null -> 1.0
        weatherUncertainty == null -> trafficUncertainty!!
        trafficUncertainty == null -> weatherUncertainty
        else -> (weatherUncertainty + trafficUncertainty) / 2.0
    }
}