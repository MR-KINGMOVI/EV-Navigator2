package com.evnavigator.iran.core.weather.model

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.Freshness
import java.time.Instant

/**
 * A traffic observation at a specific point in time and location.
 *
 * This represents real-time or near-real-time traffic conditions at a specific coordinate.
 */
data class TrafficObservation(
    /**
     * The geographic coordinate where the traffic was observed
     * May be a road segment midpoint or specific location
     */
    val coordinate: GeoCoordinate = GeoCoordinate(35.6892, 51.3890),
    
    /**
     * The timestamp when this traffic observation was made
     */
    val timestamp: Instant = Instant.now(),
    
    /**
     * Current speed in meters per second
     * UNKNOWN if not available or cannot be measured
     */
    val speedMps: Double? = null,
    
    /**
     * Free-flow speed in meters per second (speed without traffic delays)
     * UNKNOWN if not available or cannot be determined
     */
    val freeFlowSpeedMps: Double? = null,
    
    /**
     * Congestion ratio (actual speed / free-flow speed, typically 0.0 to 1.0)
     * 1.0 = free flow, 0.5 = 50% slower than free flow
     * UNKNOWN if not available or cannot be calculated
     */
    val congestionRatio: Double? = null,
    
    /**
     * Traffic classification based on congestion level
     */
    val trafficClass: TrafficClass? = TrafficClass.UNKNOWN,
    
    /**
     * Travel-time multiplier (actual time / free-flow time)
     * 1.0 = no delay, 2.0 = twice the time
     * UNKNOWN if not available or cannot be calculated
     */
    val travelTimeMultiplier: Double? = null,
    
    /**
     * Confidence level in the accuracy of this observation (0.0 to 1.0)
     * Higher values indicate more reliable data
     */
    val confidence: Double = 1.0,
    
    /**
     * Uncertainty measure for this traffic observation (0.0 to 1.0)
     * Higher values indicate higher uncertainty in the measurements
     */
    val uncertainty: Double = 0.0,
    
    /**
     * Source identifier for where this traffic data came from
     * Useful for tracking provenance and quality
     */
    val source: String = "OBSERVED",
    
    /**
     * Timestamp when the data was retrieved or generated
     * This can differ from observation time if data is cached or delayed
     */
    val retrievedAt: Instant = timestamp,
    
    /**
     * Freshness indicator of this traffic data
     * Indicates how current the information is
     */
    val freshness: TrafficFreshness = TrafficFreshness.FRESH,

    /**
     * Aliases and optional properties for legacy / integration test support
     */
    val speed: Double? = speedMps,
    val freeFlowSpeed: Double? = freeFlowSpeedMps,
    val incidents: List<TrafficIncident> = emptyList()
) {
    constructor(
        coordinate: GeoCoordinate? = null,
        timestamp: Instant = Instant.now(),
        speed: Double? = null,
        freeFlowSpeed: Double? = null,
        congestionRatio: Double? = null,
        trafficClass: TrafficClass? = null,
        travelTimeMultiplier: Double? = null,
        incidents: List<TrafficIncident> = emptyList(),
        confidence: Double = 1.0,
        freshness: Freshness = Freshness.FRESH
    ) : this(
        coordinate = coordinate ?: GeoCoordinate(35.6892, 51.3890),
        timestamp = timestamp,
        speedMps = speed,
        freeFlowSpeedMps = freeFlowSpeed,
        congestionRatio = congestionRatio,
        trafficClass = trafficClass,
        travelTimeMultiplier = travelTimeMultiplier,
        confidence = confidence,
        uncertainty = 0.15,
        source = "INTEGRATION_TEST",
        retrievedAt = timestamp,
        freshness = when (freshness) {
            Freshness.FRESH -> TrafficFreshness.FRESH
            Freshness.CACHED -> TrafficFreshness.REASONABLY_FRESH
            Freshness.STALE_BUT_USABLE -> TrafficFreshness.STALE_BUT_USABLE
            Freshness.EXPIRED -> TrafficFreshness.EXPIRED
            else -> TrafficFreshness.UNKNOWN
        },
        speed = speed,
        freeFlowSpeed = freeFlowSpeed,
        incidents = incidents
    )
}
