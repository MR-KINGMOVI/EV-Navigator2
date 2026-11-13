package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive traffic travel-time modeling system.
 * 
 * This class provides advanced methods for calculating and modeling travel times
 * based on traffic conditions, including segment-level modeling, route-level aggregation,
 * and confidence assessment.
 */
class TrafficTimeModel {
    
    /**
     * Calculates adjusted travel time for a single route segment.
     * 
     * @param startCoordinate Start coordinate of the segment
     * @param endCoordinate End coordinate of the segment
     * @param startTime Start time of the segment
     * @param endTime End time of the segment
     * @param trafficObservation Traffic data for this segment (if available)
     * @param baseTravelTime Base travel time without traffic considerations
     * @return TravelTimeEstimate with adjusted duration and multiplier
     */
    fun calculateSegmentTravelTime(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        startTime: Instant,
        endTime: Instant,
        trafficObservation: TrafficObservation?,
        baseTravelTime: Duration
    ): TravelTimeEstimate {
        // If no traffic data is available, return base time with minimal confidence
        if (trafficObservation == null) {
            val adjustedDuration = baseTravelTime
            val multiplier = 1.0
            
            return TravelTimeEstimate(
                baseDuration = baseTravelTime,
                adjustedDuration = adjustedDuration,
                travelTimeMultiplier = multiplier,
                delay = Duration.ZERO,
                confidence = 0.5, // Lower confidence without traffic data
                uncertainty = 0.3, // Higher uncertainty without traffic data
                source = "base-only",
                generatedAt = Instant.now(),
                validFrom = startTime,
                validTo = endTime
            )
        }
        
        // Use traffic observation to adjust travel time
        val travelTimeMultiplier = trafficObservation.travelTimeMultiplier ?: 1.0
        
        // Calculate adjusted duration
        val adjustedDuration = baseTravelTime.multipliedBy(travelTimeMultiplier.toLong())
        
        // Calculate delay
        val delay = adjustedDuration.minus(baseTravelTime)
        
        return TravelTimeEstimate(
            baseDuration = baseTravelTime,
            adjustedDuration = adjustedDuration,
            travelTimeMultiplier = travelTimeMultiplier,
            delay = delay,
            confidence = trafficObservation.confidence,
            uncertainty = trafficObservation.uncertainty,
            source = trafficObservation.source,
            generatedAt = Instant.now(),
            validFrom = startTime,
            validTo = endTime
        )
    }
    
    /**
     * Calculates travel time estimate for a route based on segment-level traffic data.
     * 
     * @param segments List of route segments with traffic observations
     * @param baseTravelTime Base travel time without traffic considerations
     * @return TravelTimeEstimate with aggregated results
     */
    fun calculateRouteTravelTime(
        segments: List<RouteSegmentTraffic>,
        baseTravelTime: Duration
    ): TravelTimeEstimate {
        // Calculate total adjusted time from all segments
        val adjustedDuration = segments.fold(Duration.ZERO) { acc, segment ->
            if (segment.traffic != null && segment.traffic.travelTimeMultiplier != null) {
                val multiplier = segment.traffic.travelTimeMultiplier!!
                val segmentBase = Duration.ofMillis(
                    baseTravelTime.toMillis() / segments.size.toDouble().toLong()
                )
                val segmentAdjusted = segmentBase.multipliedBy(multiplier.toLong())
                acc.plus(segmentAdjusted)
            } else {
                // If no traffic data for this segment, assume base time
                acc.plus(Duration.ofMillis(
                    baseTravelTime.toMillis() / segments.size.toDouble().toLong()
                ))
            }
        }
        
        val delay = adjustedDuration.minus(baseTravelTime)
        val multiplier = if (baseTravelTime.toMillis() > 0) {
            adjustedDuration.toMillis().toDouble() / baseTravelTime.toMillis().toDouble()
        } else {
            1.0
        }
        
        // Calculate confidence and uncertainty from segments
        val avgConfidence = segments.filter { it.traffic != null && it.traffic.confidence != null }
            .map { it.traffic!!.confidence }
            .ifEmpty { listOf(0.5) } // Default if no data
            .average()
        
        val avgUncertainty = segments.filter { it.traffic != null && it.traffic.uncertainty != null }
            .map { it.traffic!!.uncertainty }
            .ifEmpty { listOf(0.3) } // Default if no data
            .average()
        
        return TravelTimeEstimate(
            baseDuration = baseTravelTime,
            adjustedDuration = adjustedDuration,
            travelTimeMultiplier = multiplier,
            delay = delay,
            confidence = avgConfidence,
            uncertainty = avgUncertainty,
            source = "aggregated",
            generatedAt = Instant.now(),
            validFrom = segments.firstOrNull()?.startTime ?: Instant.now(),
            validTo = segments.lastOrNull()?.endTime ?: Instant.now()
        )
    }
    
    /**
     * Determines if traffic data is sufficient for reliable travel time estimation.
     * 
     * @param trafficObservations List of traffic observations to evaluate
     * @return True if traffic data is sufficient, false otherwise
     */
    fun isTrafficDataSufficient(trafficObservations: List<TrafficObservation>): Boolean {
        // Require at least some traffic data to be present and reasonably fresh
        val validObservations = trafficObservations.filter { 
            it.trafficClass != TrafficClass.UNKNOWN && 
            (it.freshness == TrafficFreshness.VERY_FRESH || 
             it.freshness == TrafficFreshness.REASONABLY_FRESH)
        }
        
        // At least 50% of segments should have valid traffic data
        return validObservations.size >= trafficObservations.size * 0.5
    }
    
    /**
     * Calculates a travel time multiplier for a given congestion level.
     * 
     * @param congestionRatio The congestion ratio (actual speed / free-flow speed)
     * @return Travel-time multiplier (actual time / free-flow time)
     */
    fun calculateTravelTimeMultiplier(congestionRatio: Double?): Double {
        if (congestionRatio == null) return 1.0
        
        // Convert congestion ratio to travel time multiplier
        // If congestion ratio is 1.0, no delay; if 2.0, twice the time
        return maxOf(1.0, congestionRatio)
    }
    
    /**
     * Determines whether traffic conditions are significantly different from free-flow.
     * 
     * @param trafficObservation The traffic observation to evaluate
     * @return True if significant delay is expected, false otherwise
     */
    fun hasSignificantDelay(trafficObservation: TrafficObservation?): Boolean {
        if (trafficObservation == null) return false
        
        val multiplier = trafficObservation.travelTimeMultiplier
        return multiplier != null && multiplier > 1.2 // 20% or more delay
    }
    
    /**
     * Calculates confidence for travel time estimate based on multiple factors.
     * 
     * @param trafficObservations Traffic observations used in calculation
     * @param weatherObservations Weather observations used in calculation (if any)
     * @param dataFreshness Freshness of the data
     * @return Confidence level (0.0 to 1.0) for the travel time estimate
     */
    fun calculateTravelTimeConfidence(
        trafficObservations: List<TrafficObservation>,
        weatherObservations: List<WeatherObservation> = emptyList(),
        dataFreshness: TrafficFreshness? = null
    ): Double {
        // Base confidence from traffic data quality
        val trafficConfidence = when {
            trafficObservations.isEmpty() -> 0.3
            trafficObservations.size < 3 -> 0.5
            else -> 0.8
        }
        
        // Adjust based on data freshness
        val freshnessFactor = when (dataFreshness) {
            TrafficFreshness.EXTREMELY_FRESH -> 1.0
            TrafficFreshness.VERY_FRESH -> 0.9
            TrafficFreshness.REASONABLY_FRESH -> 0.8
            TrafficFreshness.SOMEWHAT_STALE -> 0.7
            TrafficFreshness.STALE -> 0.5
            else -> 0.6
        }
        
        // Combine factors
        val baseConfidence = trafficConfidence * freshnessFactor
        
        // Adjust for weather data if available (if it's a combined estimate)
        val weatherFactor = if (weatherObservations.isNotEmpty()) {
            1.0 // Weather data can help validate travel time estimates
        } else {
            0.9 // Slightly less confidence without weather context
        }
        
        return minOf(baseConfidence * weatherFactor, 1.0)
    }
    
    /**
     * Calculates uncertainty for travel time estimate.
     * 
     * @param trafficObservations Traffic observations used in calculation
     * @param baseUncertainty Base uncertainty to start with
     * @return Uncertainty level (0.0 to 1.0) for the travel time estimate
     */
    fun calculateTravelTimeUncertainty(
        trafficObservations: List<TrafficObservation>,
        baseUncertainty: Double = 0.3
    ): Double {
        // Start with base uncertainty
        var totalUncertainty = baseUncertainty
        
        // Add uncertainty from traffic observations
        if (trafficObservations.isNotEmpty()) {
            val avgUncertainty = trafficObservations.mapNotNull { it.uncertainty }.average()
            totalUncertainty += avgUncertainty * 0.5 // Weight by half to prevent over-inflation
        }
        
        return minOf(totalUncertainty, 1.0)
    }
}