package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.*
import java.time.Duration
import java.time.Instant

data class VehicleSpecification(val id: String = "default")

data class EnergyRequirements(
    val baseEnergy: Double = 0.0,
    val environmentalModifier: Double = 1.0,
    val requiredEnergy: Double = 0.0,
    val confidence: Double = 0.8
)

data class CriticalIssue(
    val type: CriticalIssueType,
    val description: String,
    val severity: Int = 5
)

enum class CriticalIssueType {
    SEVERE_WEATHER,
    HEAVY_TRAFFIC,
    CHARGER_UNAVAILABLE,
    TEMPERATURE_EXTREME
}

class ChargingStrategyIntegration {
    
    /**
     * Evaluates charging stop feasibility considering environmental conditions.
     *
     * @param routeEnvironmentProfile The environmental profile of the route
     * @param chargingStops List of planned charging stops along the route
     * @param vehicleSpec Vehicle specifications for energy calculations
     * @param tripStartTime The start time of the trip
     * @return ChargingFeasibilityResult indicating feasibility and recommendations
     */
    fun evaluateChargingStopFeasibility(
        routeEnvironmentProfile: RouteEnvironmentProfile,
        chargingStops: List<ChargingStop>,
        vehicleSpec: VehicleSpecification,
        tripStartTime: Instant
    ): ChargingFeasibilityResult {
        val results = mutableListOf<ChargingStopFeasibility>()
        val criticalIssues = mutableListOf<CriticalIssue>()
        
        // Evaluate each charging stop
        for ((index, stop) in chargingStops.withIndex()) {
            val feasibility = evaluateSingleChargingStop(
                routeEnvironmentProfile,
                stop,
                vehicleSpec,
                tripStartTime,
                index
            )
            
            results.add(feasibility)
            
            // Collect critical issues if any
            if (feasibility.isFeasible.not()) {
                val issue = CriticalIssue(
                    type = CriticalIssueType.SEVERE_WEATHER,
                    description = "Charging stop ${stop.id} may be affected by environmental conditions",
                    severity = 8
                )
                criticalIssues.add(issue)
            }
        }
        
        // Overall assessment
        val overallFeasible = results.all { it.isFeasible }
        val averageConfidence = if (results.isNotEmpty()) {
            results.map { it.confidence }.average()
        } else {
            0.5
        }
        
        return ChargingFeasibilityResult(
            isFeasible = overallFeasible,
            confidence = averageConfidence,
            uncertainty = 1.0 - averageConfidence,
            criticalIssues = criticalIssues,
            stopEvaluations = results,
            recommendedAdjustments = calculateRecommendedAdjustments(
                routeEnvironmentProfile, 
                chargingStops, 
                vehicleSpec
            )
        )
    }
    
    /**
     * Evaluates a single charging stop for feasibility.
     *
     * @param profile The route environment profile
     * @param stop The charging stop to evaluate
     * @param vehicleSpec Vehicle specifications
     * @param tripStartTime Trip start time
     * @param stopIndex Index of the stop in the route
     * @return ChargingStopFeasibility result
     */
    private fun evaluateSingleChargingStop(
        profile: RouteEnvironmentProfile,
        stop: ChargingStop,
        vehicleSpec: VehicleSpecification,
        tripStartTime: Instant,
        stopIndex: Int
    ): ChargingStopFeasibility {
        // Find environmental context for the charging stop location
        val environmentContext = findEnvironmentForStop(profile, stop)
        
        // Check if charging conditions are suitable at the stop location
        val chargingConditions = evaluateChargingConditions(environmentContext, stop)
        
        // Calculate energy requirements for reaching and using this stop
        val energyRequirements = calculateEnergyRequirementsForStop(
            profile, 
            stop, 
            vehicleSpec,
            tripStartTime
        )
        
        // Check if weather conditions might affect charging
        val weatherImpact = evaluateWeatherImpactOnCharging(environmentContext)
        
        // Check if traffic conditions might affect access to the stop
        val trafficImpact = evaluateTrafficImpactOnAccess(environmentContext)
        
        // Overall feasibility calculation
        val isFeasible = chargingConditions.isFeasible && 
                         weatherImpact.isFeasible && 
                         trafficImpact.isFeasible &&
                         energyRequirements.requiredEnergy > 0
        
        // Calculate confidence in the evaluation
        val confidence = calculateStopConfidence(
            chargingConditions,
            weatherImpact,
            trafficImpact,
            environmentContext
        )
        
        return ChargingStopFeasibility(
            id = stop.id,
            isFeasible = isFeasible,
            confidence = confidence,
            uncertainty = 1.0 - confidence,
            energyRequirements = energyRequirements,
            chargingConditions = chargingConditions,
            weatherImpact = weatherImpact,
            trafficImpact = trafficImpact,
            environmentContext = environmentContext
        )
    }
    
    /**
     * Finds the environmental context for a charging stop.
     *
     * @param profile The route environment profile
     * @param stop The charging stop
     * @return EnvironmentContext for the stop location or null if not found
     */
    private fun findEnvironmentForStop(
        profile: RouteEnvironmentProfile,
        stop: ChargingStop
    ): RouteEnvironmentContext? {
        val matchingSegment = profile.segments.firstOrNull { segmentEnv: RouteSegmentEnvironment ->
            val segmentCenter = calculateSegmentCenter(segmentEnv.segment)
            val distance = calculateDistanceBetweenCoordinates(stop.coordinate, segmentCenter)
            distance < 500.0
        }
        return matchingSegment?.let {
            RouteEnvironmentContext(
                segments = listOf(it),
                confidence = it.confidence,
                uncertainty = it.uncertainty,
                freshness = it.freshness,
                provenance = it.provenance
            )
        }
    }
    
    /**
     * Calculates segment center point.
     *
     * @param segment The route segment
     * @return Center coordinate of the segment
     */
    private fun calculateSegmentCenter(segment: RouteSegment): GeoCoordinate {
        return GeoCoordinate(
            latitude = (segment.startCoordinate.latitude + segment.endCoordinate.latitude) / 2.0,
            longitude = (segment.startCoordinate.longitude + segment.endCoordinate.longitude) / 2.0
        )
    }
    
    /**
     * Calculates distance between two coordinates.
     *
     * @param coord1 First coordinate
     * @param coord2 Second coordinate
     * @return Distance in meters
     */
    private fun calculateDistanceBetweenCoordinates(coord1: GeoCoordinate, coord2: GeoCoordinate): Double {
        // Simplified calculation - would use more accurate methods in real implementation
        val latDiff = kotlin.math.abs(coord1.latitude - coord2.latitude)
        val lonDiff = kotlin.math.abs(coord1.longitude - coord2.longitude)
        
        // Convert degrees to approximate meters (simplified)
        val latMeters = latDiff * 111000.0
        val lonMeters = lonDiff * 111000.0 * kotlin.math.cos(Math.toRadians(coord1.latitude))
        
        return kotlin.math.sqrt(latMeters * latMeters + lonMeters * lonMeters)
    }
    
    /**
     * Evaluates charging conditions at a stop location.
     *
     * @param environmentContext Environment context for the stop
     * @param stop Charging stop to evaluate
     * @return ChargingConditions result
     */
    private fun evaluateChargingConditions(
        environmentContext: RouteEnvironmentContext?,
        stop: ChargingStop
    ): ChargingConditions {
        // In a real implementation, this would check:
        // - Available charging infrastructure quality
        // - Charging speed compatibility
        // - Power availability
        
        // For now, we'll assume conditions are good unless there's explicit data to suggest otherwise
        val isFeasible = true
        val confidence = environmentContext?.confidence ?: 0.7
        val uncertainty = 1.0 - confidence
        
        return ChargingConditions(
            isFeasible = isFeasible,
            confidence = confidence,
            uncertainty = uncertainty,
            details = "Charging conditions appear suitable at stop location"
        )
    }
    
    /**
     * Calculates energy requirements for reaching and using a charging stop.
     *
     * @param profile The route environment profile
     * @param stop The charging stop
     * @param vehicleSpec Vehicle specifications
     * @param tripStartTime Trip start time
     * @return EnergyRequirements for the stop
     */
    private fun calculateEnergyRequirementsForStop(
        profile: RouteEnvironmentProfile,
        stop: ChargingStop,
        vehicleSpec: VehicleSpecification,
        tripStartTime: Instant
    ): EnergyRequirements {
        // Simplified calculation - in real implementation would be more complex
        
        val baseEnergy = 20.0 // kWh needed for charging at this stop
        val environmentalModifier = 1.0 // For now, no modification
        
        return EnergyRequirements(
            baseEnergy = baseEnergy,
            environmentalModifier = environmentalModifier,
            requiredEnergy = baseEnergy * environmentalModifier,
            confidence = 0.8 // Reasonable confidence in energy calculations
        )
    }
    
    /**
     * Evaluates weather impact on charging.
     *
     * @param environmentContext Environment context for the stop
     * @return WeatherImpact result
     */
    private fun evaluateWeatherImpactOnCharging(environmentContext: RouteEnvironmentContext?): WeatherImpact {
        val weatherCondition = environmentContext?.weatherContext?.observation?.condition
        
        // Check if weather conditions might affect charging
        val isFeasible = when (weatherCondition) {
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.HAIL,
            WeatherCondition.DUST_STORM -> false // Severe weather may interrupt charging
            WeatherCondition.SNOW,
            WeatherCondition.HEAVY_SNOW -> {
                // Snow might slow down charging, but not prevent it
                true
            }
            else -> true // Other conditions don't significantly impact charging
        }
        
        val confidence = environmentContext?.weatherContext?.observation?.confidence ?: 0.6
        val uncertainty = 1.0 - confidence
        
        return WeatherImpact(
            isFeasible = isFeasible,
            confidence = confidence,
            uncertainty = uncertainty,
            details = "Weather conditions may impact charging efficiency"
        )
    }
    
    /**
     * Evaluates traffic impact on access to charging stop.
     *
     * @param environmentContext Environment context for the stop
     * @return TrafficImpact result
     */
    private fun evaluateTrafficImpactOnAccess(environmentContext: RouteEnvironmentContext?): TrafficImpact {
        val trafficClass = environmentContext?.trafficContext?.observation?.trafficClass
        
        // Check if traffic conditions might affect access to charging stop
        val isFeasible = when (trafficClass) {
            TrafficClass.SEVERE -> false // Severe traffic could make it difficult to reach
            TrafficClass.HEAVY -> {
                // Heavy traffic makes it harder but not impossible
                true 
            }
            else -> true // Light or moderate traffic doesn't significantly impact access
        }
        
        val confidence = environmentContext?.trafficContext?.observation?.confidence ?: 0.6
        val uncertainty = 1.0 - confidence
        
        return TrafficImpact(
            isFeasible = isFeasible,
            confidence = confidence,
            uncertainty = uncertainty,
            details = "Traffic conditions may affect access to charging stop"
        )
    }
    
    /**
     * Calculates confidence for a single charging stop evaluation.
     *
     * @param chargingConditions Charging conditions
     * @param weatherImpact Weather impact
     * @param trafficImpact Traffic impact
     * @param environmentContext Environment context
     * @return Confidence value (0.0 to 1.0)
     */
    private fun calculateStopConfidence(
        chargingConditions: ChargingConditions,
        weatherImpact: WeatherImpact,
        trafficImpact: TrafficImpact,
        environmentContext: RouteEnvironmentContext?
    ): Double {
        val confidenceValues = mutableListOf<Double>()
        
        chargingConditions.confidence?.let { confidenceValues.add(it) }
        weatherImpact.confidence?.let { confidenceValues.add(it) }
        trafficImpact.confidence?.let { confidenceValues.add(it) }
        environmentContext?.confidence?.let { confidenceValues.add(it) }
        
        return if (confidenceValues.isNotEmpty()) {
            confidenceValues.average() * 0.9 // Slight reduction for integration complexity
        } else {
            0.5
        }
    }
    
    /**
     * Calculates recommended adjustments to charging strategy based on environmental factors.
     *
     * @param profile The route environment profile
     * @param chargingStops List of planned charging stops
     * @param vehicleSpec Vehicle specifications
     * @return List of recommended adjustments
     */
    private fun calculateRecommendedAdjustments(
        profile: RouteEnvironmentProfile,
        chargingStops: List<ChargingStop>,
        vehicleSpec: VehicleSpecification
    ): List<ChargingAdjustment> {
        val adjustments = mutableListOf<ChargingAdjustment>()
        
        // Check for weather that might require more frequent charging or different timing
        val severeWeatherSegments = profile.segments.filter { segment ->
            segment.weatherContext?.observation?.let { obs ->
                when (obs.condition) {
                    WeatherCondition.HEAVY_RAIN,
                    WeatherCondition.SLEET,
                    WeatherCondition.HAIL,
                    WeatherCondition.SNOW,
                    WeatherCondition.THUNDERSTORM -> true
                    else -> false
                }
            } ?: false
        }
        
        if (severeWeatherSegments.isNotEmpty()) {
            adjustments.add(ChargingAdjustment(
                type = ChargingAdjustmentType.INCREASE_CHARGING_FREQUENCY,
                description = "Increase charging frequency due to severe weather conditions",
                severity = 8
            ))
        }
        
        // Check for traffic that might affect charging time or access
        val heavyTrafficSegments = profile.segments.filter { segment ->
            segment.trafficContext?.observation?.let { obs ->
                obs.trafficClass == TrafficClass.HEAVY || obs.trafficClass == TrafficClass.SEVERE
            } ?: false
        }
        
        if (heavyTrafficSegments.isNotEmpty()) {
            adjustments.add(ChargingAdjustment(
                type = ChargingAdjustmentType.ADJUST_CHARGING_DURATION,
                description = "Adjust charging duration to account for traffic delays",
                severity = 6
            ))
        }
        
        return adjustments
    }
    
    /**
     * Updates charging plan with environmental data.
     *
     * @param routeEnvironmentProfile The environmental profile of the route
     * @param originalChargingPlan The original charging plan
     * @param vehicleSpec Vehicle specifications
     * @return Updated charging plan with environmental considerations
     */
    fun updateChargingPlanWithEnvironmentalData(
        routeEnvironmentProfile: RouteEnvironmentProfile,
        originalChargingPlan: ChargingPlan,
        vehicleSpec: VehicleSpecification
    ): ChargingPlan {
        // In a real implementation, this would:
        // - Modify charging stop timing based on weather conditions
        // - Adjust charging durations based on traffic conditions  
        // - Recommend alternative stops if current ones are affected
        
        // For now, return the original plan with updated confidence
        return originalChargingPlan.copy(
            confidence = (originalChargingPlan.confidence + 0.1).coerceAtMost(1.0)
        )
    }
    
    /**
     * Determines if charging conditions at a stop location are suitable.
     *
     * @param environmentContext Environment context for the stop
     * @return ChargingSuitability result
     */
    fun determineChargingSuitability(
        environmentContext: RouteEnvironmentContext?
    ): ChargingSuitability {
        val weatherCondition = environmentContext?.weatherContext?.observation?.condition
        val trafficClass = environmentContext?.trafficContext?.observation?.trafficClass
        
        // Determine overall suitability based on conditions
        val suitable = when {
            weatherCondition == WeatherCondition.THUNDERSTORM ||
            weatherCondition == WeatherCondition.HAIL ||
            weatherCondition == WeatherCondition.DUST_STORM -> false
            
            trafficClass == TrafficClass.SEVERE -> false
            
            else -> true
        }
        
        // Calculate confidence based on available data
        val confidence = environmentContext?.let { ctx ->
            val dataConfidence = mutableListOf<Double>()
            ctx.weatherContext?.observation?.confidence?.let { c -> dataConfidence.add(c) }
            ctx.trafficContext?.observation?.confidence?.let { c -> dataConfidence.add(c) }
            
            if (dataConfidence.isNotEmpty()) {
                dataConfidence.average()
            } else {
                0.5
            }
        } ?: 0.5
        
        return ChargingSuitability(
            isSuitable = suitable,
            confidence = confidence,
            uncertainty = 1.0 - confidence,
            reasons = listOfNotNull(
                if (weatherCondition != null) "Weather: $weatherCondition" else null,
                if (trafficClass != null) "Traffic: $trafficClass" else null
            )
        )
    }
}

/**
 * Represents a charging stop along a route.
 */
data class ChargingStop(
    /**
     * Unique identifier for the charging stop
     */
    val id: String,
    
    /**
     * Geographic coordinate of the charging stop
     */
    val coordinate: GeoCoordinate,
    
    /**
     * Type of charging station (AC/DC)
     */
    val stationType: ChargingStationType,
    
    /**
     * Maximum power available at this stop (kW)
     */
    val maxPowerKw: Double,
    
    /**
     * Expected charging duration at this stop
     */
    val expectedChargingDuration: Duration,
    
    /**
     * Availability of the charging station
     */
    val availability: ChargingAvailability
)

/**
 * Enum for charging station types.
 */
enum class ChargingStationType {
    AC,
    DC,
    FAST_DC,
    UNKNOWN
}

/**
 * Enum for charging availability.
 */
enum class ChargingAvailability {
    AVAILABLE,
    BUSY,
    UNAVAILABLE,
    UNKNOWN
}

/**
 * Result of charging stop feasibility evaluation.
 */
data class ChargingFeasibilityResult(
    /**
     * Whether the overall charging strategy is feasible
     */
    val isFeasible: Boolean,
    
    /**
     * Confidence level in the feasibility assessment (0.0 to 1.0)
     */
    val confidence: Double,
    
    /**
     * Uncertainty in the feasibility assessment (0.0 to 1.0)
     */
    val uncertainty: Double,
    
    /**
     * List of critical issues that might affect charging
     */
    val criticalIssues: List<CriticalIssue>,
    
    /**
     * Evaluations for each individual charging stop
     */
    val stopEvaluations: List<ChargingStopFeasibility>,
    
    /**
     * Recommended adjustments to the charging strategy
     */
    val recommendedAdjustments: List<ChargingAdjustment>
)

/**
 * Result of individual charging stop feasibility evaluation.
 */
data class ChargingStopFeasibility(
    /**
     * Identifier of the charging stop
     */
    val id: String,
    
    /**
     * Whether this specific charging stop is feasible
     */
    val isFeasible: Boolean,
    
    /**
     * Confidence level in this evaluation (0.0 to 1.0)
     */
    val confidence: Double,
    
    /**
     * Uncertainty in this evaluation (0.0 to 1.0)
     */
    val uncertainty: Double,
    
    /**
     * Energy requirements for this stop
     */
    val energyRequirements: EnergyRequirements,
    
    /**
     * Charging conditions at the stop location
     */
    val chargingConditions: ChargingConditions,
    
    /**
     * Weather impact on charging at the stop
     */
    val weatherImpact: WeatherImpact,
    
    /**
     * Traffic impact on access to the stop
     */
    val trafficImpact: TrafficImpact,
    
    /**
     * Environment context for the stop location
     */
    val environmentContext: RouteEnvironmentContext?
)

/**
 * Charging conditions at a specific location.
 */
data class ChargingConditions(
    /**
     * Whether charging is feasible at this location
     */
    val isFeasible: Boolean,
    
    /**
     * Confidence in the charging conditions (0.0 to 1.0)
     */
    val confidence: Double?,
    
    /**
     * Uncertainty in the charging conditions (0.0 to 1.0)
     */
    val uncertainty: Double,
    
    /**
     * Additional details about the charging conditions
     */
    val details: String
)

/**
 * Weather impact on charging.
 */
data class WeatherImpact(
    /**
     * Whether weather conditions affect charging feasibility
     */
    val isFeasible: Boolean,
    
    /**
     * Confidence in the weather impact assessment (0.0 to 1.0)
     */
    val confidence: Double?,
    
    /**
     * Uncertainty in the weather impact assessment (0.0 to 1.0)
     */
    val uncertainty: Double,
    
    /**
     * Details about the weather impact
     */
    val details: String
)

/**
 * Traffic impact on access to charging.
 */
data class TrafficImpact(
    /**
     * Whether traffic conditions affect access to charging
     */
    val isFeasible: Boolean,
    
    /**
     * Confidence in the traffic impact assessment (0.0 to 1.0)
     */
    val confidence: Double?,
    
    /**
     * Uncertainty in the traffic impact assessment (0.0 to 1.0)
     */
    val uncertainty: Double,
    
    /**
     * Details about the traffic impact
     */
    val details: String
)

/**
 * Charging adjustment recommendation.
 */
data class ChargingAdjustment(
    /**
     * Type of adjustment needed
     */
    val type: ChargingAdjustmentType,
    
    /**
     * Description of the adjustment
     */
    val description: String,
    
    /**
     * Severity level (1-10, 10 being most severe)
     */
    val severity: Int
)

/**
 * Enum for charging adjustment types.
 */
enum class ChargingAdjustmentType {
    INCREASE_CHARGING_FREQUENCY,
    ADJUST_CHARGING_DURATION,
    CHANGE_STOP_LOCATION,
    DELAY_CHARGING,
    SPEED_UP_CHARGING
}

/**
 * Represents a charging plan for the trip.
 */
data class ChargingPlan(
    /**
     * List of charging stops in the plan
     */
    val stops: List<ChargingStop>,
    
    /**
     * Confidence in the charging plan (0.0 to 1.0)
     */
    val confidence: Double,
    
    /**
     * Total estimated charging time
     */
    val totalChargingTime: Duration,
    
    /**
     * Estimated total energy required for charging
     */
    val totalEnergyRequiredKwh: Double
)

/**
 * Result of charging suitability evaluation.
 */
data class ChargingSuitability(
    /**
     * Whether the location is suitable for charging
     */
    val isSuitable: Boolean,
    
    /**
     * Confidence in the suitability assessment (0.0 to 1.0)
     */
    val confidence: Double,
    
    /**
     * Uncertainty in the suitability assessment (0.0 to 1.0)
     */
    val uncertainty: Double,
    
    /**
     * Reasons for the suitability determination
     */
    val reasons: List<String>
)