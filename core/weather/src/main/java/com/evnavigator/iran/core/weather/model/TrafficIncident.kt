package com.evnavigator.iran.core.weather.model

import com.evnavigator.iran.core.domain.GeoCoordinate

import java.time.Instant

/**
 * A traffic incident that affects road conditions or travel.
 *
 * This represents a specific event or condition that impacts traffic flow or road usability.
 */

data class TrafficIncident(
    /**
     * Unique identifier for this incident
     */
    val id: String,
    
    /**
     * The geographic coordinate where the incident occurred
     */
    val coordinate: GeoCoordinate,
    
    /**
     * The type of incident that occurred
     */
    val type: IncidentType,
    
    /**
     * Severity level of the incident (1-5 scale, 5 being most severe)
     * UNKNOWN if severity cannot be determined
     */
    val severity: Int?,
    
    /**
     * Start time when the incident began or was reported
     */
    val startTime: Instant,
    
    /**
     * Expected end time when the incident should be resolved
     * null if unknown or ongoing
     */
    val expectedEndTime: Instant?,
    
    /**
     * Actual end time when the incident was resolved
     * null if still ongoing
     */
    val actualEndTime: Instant?,
    
    /**
     * Status of the incident (active, resolved, cancelled)
     */
    val status: IncidentStatus,
    
    /**
     * Description or details about the incident
     */
    val description: String?,
    
    /**
     * Source identifier for where this incident data came from
     * Useful for tracking provenance and quality
     */
    val source: String,
    
    /**
     * Timestamp when the data was retrieved or generated
     */
    val retrievedAt: Instant,
    
    /**
     * Confidence level in the accuracy of this incident (0.0 to 1.0)
     * Higher values indicate more reliable data
     */
    val confidence: Double,
    
    /**
     * Uncertainty measure for this incident (0.0 to 1.0)
     * Higher values indicate higher uncertainty in the details
     */
    val uncertainty: Double,
    
    /**
     * Freshness indicator of this incident data
     * Indicates how current the information is
     */
    val freshness: TrafficFreshness
)