package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.TravelTimeEstimate
import java.time.Duration
import java.time.Instant

/**
 * Models travel time adjustments based on traffic conditions for route segments.
 *
 * This component provides accurate travel time estimates by applying traffic modifiers
 * to base travel times, taking into account real-time traffic conditions and their impact
 * on journey duration without duplicating energy physics from Phase 5.
 */
class TrafficTravelTimeModel {
    
    private fun createTravelTimeEstimate(
        baseSeconds: Double,
        adjustedSeconds: Double,
        delaySeconds: Double,
        confidence: Double,
        uncertainty: Double,
        source: String
    ): TravelTimeEstimate {
        val now = Instant.now()
        val mult = if (baseSeconds > 0) adjustedSeconds / baseSeconds else 1.0
        return TravelTimeEstimate(
            baseDuration = baseSeconds,
            trafficAdjustedDuration = adjustedSeconds,
            travelTimeMultiplier = mult,
            delay = delaySeconds,
            confidence = confidence,
            uncertainty = uncertainty,
            source = source,
            generatedAt = now,
            validFrom = now,
            validTo = now.plusSeconds(3600),
            provenance = source
        )
    }

    /**
     * Calculates adjusted travel time for a route based on traffic conditions
     *
     * @param segments List of route segments with associated traffic data
     * @param baseTravelTime Base travel time without traffic adjustments (in seconds)
     * @return Travel time estimate including traffic impact
     */
    fun calculateTravelTimeWithTraffic(
        segments: List<RouteSegment>,
        trafficData: Map<RouteSegment, TrafficDataForSegment>,
        baseTravelTime: Double // in seconds
    ): TravelTimeEstimate {
        if (segments.isEmpty()) {
            return createTravelTimeEstimate(
                baseSeconds = 0.0,
                adjustedSeconds = 0.0,
                delaySeconds = 0.0,
                confidence = 0.0,
                uncertainty = 1.0,
                source = "EmptyRoute"
            )
        }
        
        var totalBaseDuration = 0.0
        var totalTrafficAdjustedDuration = 0.0
        var totalDelay = 0.0
        val allConfidences = mutableListOf<Double>()
        val allUncertainties = mutableListOf<Double>()
        
        // Process each segment
        for (segment in segments) {
            val trafficForSegment = trafficData[segment]
            
            // Get base duration for this segment (if available)
            val segmentBaseDuration = segment.estimatedDurationSeconds.toDouble()
            
            // Calculate adjusted duration based on traffic data
            val (adjustedDuration, delay, confidence, uncertainty) = 
                calculateSegmentTravelTime(segment, segmentBaseDuration, trafficForSegment)
            
            totalBaseDuration += segmentBaseDuration
            totalTrafficAdjustedDuration += adjustedDuration
            totalDelay += delay
            
            // Collect confidence and uncertainty for overall calculation
            if (confidence != null) allConfidences.add(confidence)
            if (uncertainty != null) allUncertainties.add(uncertainty)
        }
        
        // Calculate overall confidence and uncertainty
        val overallConfidence = if (allConfidences.isNotEmpty()) {
            allConfidences.average()
        } else {
            0.0
        }
        
        val overallUncertainty = if (allUncertainties.isNotEmpty()) {
            allUncertainties.average()
        } else {
            1.0
        }
        
        return createTravelTimeEstimate(
            baseSeconds = totalBaseDuration,
            adjustedSeconds = totalTrafficAdjustedDuration,
            delaySeconds = totalDelay,
            confidence = overallConfidence,
            uncertainty = overallUncertainty,
            source = "TrafficTravelTimeModel"
        )
    }
    
    /**
     * Calculates travel time for a single segment based on traffic data
     *
     * @param segment The route segment
     * @param baseDuration Base duration of the segment (in seconds)
     * @param trafficData Traffic data for this segment
     * @return Tuple of (adjusted duration, delay, confidence, uncertainty)
     */
    private fun calculateSegmentTravelTime(
        segment: RouteSegment,
        baseDuration: Double,
        trafficData: TrafficDataForSegment?
    ): Tuple4<Double, Double, Double?, Double?> {
        // If no traffic data or base duration is zero, return base values
        if (trafficData == null || baseDuration <= 0) {
            return Tuple4(baseDuration, 0.0, 0.0, 1.0)
        }
        
        // Get the travel time multiplier from traffic data
        val multiplier = trafficData.travelTimeMultiplier
        
        // If no multiplier available, return base values with low confidence
        if (multiplier == null || multiplier <= 0) {
            return Tuple4(baseDuration, 0.0, 0.0, 1.0)
        }
        
        // Calculate adjusted duration and delay
        val adjustedDuration = baseDuration * multiplier
        val delay = adjustedDuration - baseDuration
        
        // Determine confidence based on traffic data quality
        val confidence = when {
            trafficData.freshness == Freshness.FRESH -> trafficData.confidence
            trafficData.freshness == Freshness.STALE_BUT_USABLE -> trafficData.confidence * 0.8
            trafficData.freshness == Freshness.EXPIRED -> trafficData.confidence * 0.5
            else -> 0.0 // UNKNOWN or INVALID freshness
        }
        
        // Determine uncertainty based on traffic data quality and multiplier
        val uncertainty = when {
            trafficData.freshness == Freshness.FRESH -> trafficData.uncertainty
            trafficData.freshness == Freshness.STALE_BUT_USABLE -> trafficData.uncertainty * 1.2
            trafficData.freshness == Freshness.EXPIRED -> trafficData.uncertainty * 1.5
            else -> 1.0 // UNKNOWN or INVALID freshness
        }
        
        return Tuple4(adjustedDuration, delay, confidence, uncertainty)
    }
    
    /**
     * Calculates travel time for a specific segment using only base duration and traffic multiplier
     *
     * @param baseDuration Base duration of the segment (in seconds)
     * @param trafficMultiplier Traffic multiplier (1.0 = no change, > 1.0 = slower than base)
     * @return Travel time estimate for this segment
     */
    fun calculateSegmentTravelTime(
        baseDuration: Double,
        trafficMultiplier: Double?
    ): TravelTimeEstimate {
        if (baseDuration <= 0) {
            return createTravelTimeEstimate(
                baseSeconds = 0.0,
                adjustedSeconds = 0.0,
                delaySeconds = 0.0,
                confidence = 0.0,
                uncertainty = 1.0,
                source = "InvalidDuration"
            )
        }
        
        if (trafficMultiplier == null || trafficMultiplier <= 0) {
            return createTravelTimeEstimate(
                baseSeconds = baseDuration,
                adjustedSeconds = baseDuration,
                delaySeconds = 0.0,
                confidence = 0.0,
                uncertainty = 1.0,
                source = "NoTrafficData"
            )
        }
        
        val adjustedDuration = baseDuration * trafficMultiplier
        val delay = adjustedDuration - baseDuration
        
        return createTravelTimeEstimate(
            baseSeconds = baseDuration,
            adjustedSeconds = adjustedDuration,
            delaySeconds = delay,
            confidence = 0.8, // Default confidence for single segment calculation
            uncertainty = 0.2, // Default uncertainty for single segment calculation
            source = "TrafficTravelTimeModel"
        )
    }
    
    /**
     * Calculates the impact of traffic on a specific route by comparing base and adjusted times
     *
     * @param segments Route segments with traffic data
     * @param baseTravelTime Base travel time without traffic (in seconds)
     * @return Detailed traffic impact analysis
     */
    fun analyzeTrafficImpact(
        segments: List<RouteSegment>,
        trafficData: Map<RouteSegment, TrafficDataForSegment>,
        baseTravelTime: Double
    ): TrafficImpactAnalysis {
        if (segments.isEmpty()) {
            return TrafficImpactAnalysis(
                totalBaseDuration = 0.0,
                totalAdjustedDuration = 0.0,
                totalDelay = 0.0,
                averageCongestionRatio = 0.0,
                maxDelaySegment = null,
                segmentsWithDelay = emptyList(),
                confidence = 0.0,
                uncertainty = 1.0
            )
        }
        
        var totalBaseDuration = 0.0
        var totalAdjustedDuration = 0.0
        var totalDelay = 0.0
        val segmentDelays = mutableListOf<SegmentDelay>()
        val congestionRatios = mutableListOf<Double>()
        
        // Process each segment
        for (segment in segments) {
            val trafficForSegment = trafficData[segment]
            
            // Get base duration for this segment (if available)
            val segmentBaseDuration = segment.estimatedDurationSeconds.toDouble()
            
            // Calculate adjusted duration based on traffic data
            val (adjustedDuration, delay, _, _) = 
                calculateSegmentTravelTime(segment, segmentBaseDuration, trafficForSegment)
            
            totalBaseDuration += segmentBaseDuration
            totalAdjustedDuration += adjustedDuration
            totalDelay += delay
            
            // Track segment delays and congestion ratios
            if (delay > 0) {
                segmentDelays.add(SegmentDelay(segment.id, delay))
            }
            
            // Collect congestion ratios for average calculation
            trafficForSegment?.congestionRatio?.let { congestionRatios.add(it) }
        }
        
        // Calculate average congestion ratio
        val averageCongestionRatio = if (congestionRatios.isNotEmpty()) {
            congestionRatios.average()
        } else {
            0.0
        }
        
        // Find segment with maximum delay
        val maxDelaySegment = segmentDelays.maxByOrNull { it.delay }
        
        return TrafficImpactAnalysis(
            totalBaseDuration = totalBaseDuration,
            totalAdjustedDuration = totalAdjustedDuration,
            totalDelay = totalDelay,
            averageCongestionRatio = averageCongestionRatio,
            maxDelaySegment = maxDelaySegment,
            segmentsWithDelay = segmentDelays,
            confidence = 0.85, // Default confidence
            uncertainty = 0.15 // Default uncertainty
        )
    }
}

data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Represents the impact of traffic on a specific route segment
 */
data class SegmentDelay(
    val segmentId: String,
    val delay: Double // Delay in seconds
)

/**
 * Analysis of traffic impact on a route
 */
data class TrafficImpactAnalysis(
    val totalBaseDuration: Double, // Total base duration (seconds)
    val totalAdjustedDuration: Double, // Total adjusted duration (seconds) 
    val totalDelay: Double, // Total delay due to traffic (seconds)
    val averageCongestionRatio: Double, // Average congestion ratio across all segments
    val maxDelaySegment: SegmentDelay?, // Segment with maximum delay
    val segmentsWithDelay: List<SegmentDelay>, // All segments that have delays
    val confidence: Double, // Overall confidence in the analysis (0.0 to 1.0)
    val uncertainty: Double // Overall uncertainty in the analysis (0.0 to 1.0)
)

/**
 * Calculates travel time estimates for traffic data using different approaches
 */
object TravelTimeCalculator {
    
    /**
     * Calculates travel time based on speed and distance
     *
     * @param distance Distance of segment in meters
     * @param averageSpeed Average speed in m/s
     * @return Travel time estimate in seconds
     */
    fun calculateFromSpeedAndDistance(distance: Double, averageSpeed: Double): Double {
        return if (averageSpeed > 0) {
            distance / averageSpeed
        } else {
            0.0
        }
    }
    
    /**
     * Calculates travel time based on speed and free-flow speed
     *
     * @param baseDuration Base duration in seconds
     * @param trafficMultiplier Traffic multiplier (1.0 = no change)
     * @return Adjusted travel time in seconds
     */
    fun calculateFromTrafficMultiplier(baseDuration: Double, trafficMultiplier: Double): Double {
        return baseDuration * trafficMultiplier
    }
    
    /**
     * Calculates traffic multiplier from congestion ratio
     *
     * @param congestionRatio Congestion ratio (0.0 to 1.0)
     * @return Traffic multiplier (> 1.0 means slower than free-flow)
     */
    fun calculateTrafficMultiplierFromCongestion(congestionRatio: Double): Double {
        // Simple linear model: multiplier increases with congestion
        return 1.0 + (congestionRatio * 2.0) // Max 3x slowdown at 100% congestion
    }
}
