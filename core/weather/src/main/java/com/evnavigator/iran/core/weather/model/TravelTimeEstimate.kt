package com.evnavigator.iran.core.weather.model

import java.time.Duration
import java.time.Instant

/**
 * Represents an estimate of travel time for a route segment or entire route.
 *
 * This model captures both the base travel time and any adjustments due to traffic,
 * weather, or other environmental factors.
 */
data class TravelTimeEstimate(
    /**
     * The base travel time without any adjustments (free-flow time) in seconds
     */
    val baseDuration: Double,
    
    /**
     * The adjusted travel time accounting for traffic, weather, and other conditions in seconds
     */
    val trafficAdjustedDuration: Double,
    
    /**
     * The ratio of adjusted time to base time (travel-time multiplier)
     * 1.0 = no delay, 2.0 = twice the time
     * UNKNOWN if not available or cannot be calculated
     */
    val travelTimeMultiplier: Double?,
    
    /**
     * The delay introduced by traffic or other conditions (adjusted - base) in seconds
     */
    val delay: Double,
    
    /**
     * Confidence level in this travel time estimate (0.0 to 1.0)
     * Higher values indicate more reliable data
     */
    val confidence: Double,
    
    /**
     * Uncertainty measure for this travel time estimate (0.0 to 1.0)
     * Higher values indicate higher uncertainty in the estimates
     */
    val uncertainty: Double,
    
    /**
     * Source identifier for where this travel time data came from
     * Useful for tracking provenance and quality
     */
    val source: String,
    
    /**
     * Timestamp when the estimate was generated or retrieved
     */
    val generatedAt: Instant = Instant.now(),
    
    /**
     * The valid time range for this estimate
     */
    val validFrom: Instant = generatedAt,
    
    /**
     * The end of the validity period for this estimate
     */
    val validTo: Instant = generatedAt.plusSeconds(3600),

    /**
     * Provenance tracking
     */
    val provenance: String = source
) {
    /**
     * Secondary constructor taking Duration instances for API compatibility.
     */
    constructor(
        baseDuration: Duration,
        adjustedDuration: Duration,
        travelTimeMultiplier: Double?,
        delay: Duration,
        confidence: Double,
        uncertainty: Double,
        source: String,
        generatedAt: Instant = Instant.now(),
        validFrom: Instant = generatedAt,
        validTo: Instant = generatedAt.plusSeconds(3600)
    ) : this(
        baseDuration = baseDuration.toMillis() / 1000.0,
        trafficAdjustedDuration = adjustedDuration.toMillis() / 1000.0,
        travelTimeMultiplier = travelTimeMultiplier,
        delay = delay.toMillis() / 1000.0,
        confidence = confidence,
        uncertainty = uncertainty,
        source = source,
        generatedAt = generatedAt,
        validFrom = validFrom,
        validTo = validTo,
        provenance = source
    )

    /**
     * Duration representation accessors for components needing java.time.Duration.
     */
    val baseDurationAsDuration: Duration
        get() = Duration.ofMillis((baseDuration * 1000).toLong())

    val adjustedDuration: Duration
        get() = Duration.ofMillis((trafficAdjustedDuration * 1000).toLong())

    val delayAsDuration: Duration
        get() = Duration.ofMillis((delay * 1000).toLong())
}
