package com.evnavigator.iran.domain.model.weather

import java.util.*

/**
 * Represents traffic data for a specific route or segment.
 *
 * This model captures real-time and historical traffic conditions that could affect EV navigation,
 * including congestion levels, travel times, speed limits, and other factors affecting routing decisions.
 */
data class TrafficData(
    /**
     * The geographic coordinate where this traffic condition applies
     */
    val coordinate: com.evnavigator.iran.domain.model.GeoCoordinate,

    /**
     * Timestamp when the traffic data was recorded or updated
     */
    val timestamp: Date,

    /**
     * Current speed in km/h (or null if unknown)
     */
    val currentSpeedKmh: Double? = null,

    /**
     * Speed limit for this segment in km/h 
     */
    val speedLimitKmh: Double? = null,

    /**
     * Congestion level (0-100% of free-flow speed, or 0=free-flow to 100=heavy-congestion)
     */
    val congestionLevelPercent: Double? = null,

    /**
     * Travel time in seconds for this segment
     */
    val travelTimeSeconds: Long? = null,

    /**
     * Freeflow travel time (without congestion) in seconds  
     */
    val freeflowTravelTimeSeconds: Long? = null,

    /**
     * Number of lanes on the road segment
     */
    val numberOfLanes: Int? = null,

    /**
     * Road type or classification (e.g., highway, arterial, residential)
     */
    val roadType: String? = null,

    /**
     * Traffic condition description
     */
    val conditionDescription: String? = null,

    /**
     * Type of traffic incident if any (accident, construction, etc.)
     */
    val incidentType: String? = null,

    /**
     * Severity level of the traffic condition or incident (1-5)
     */
    val severityLevel: Int? = null,

    /**
     * Distance to next known congestion point in meters
     */
    val distanceToCongestionMeters: Double? = null,

    /**
     * Percentage of normal speed (0-100%)
     */
    val speedPercentage: Double? = null,

    /**
     * Source of the traffic data
     */
    val source: String? = null,

    /**
     * Confidence level in this traffic reading (0.0 to 1.0)
     */
    val confidence: Double? = null,

    /**
     * Indicates if there are any known incidents or disruptions on this segment
     */
    val hasIncidents: Boolean = false,

    /**
     * Average delay time due to congestion in seconds per kilometer
     */
    val averageDelaySecondsPerKm: Double? = null,

    /**
     * Percentage of vehicles that are electric (for EV-specific routing)
     */
    val evPercentage: Double? = null,

    /**
     * Expected fuel consumption or energy usage for this segment (if available)
     */
    val expectedFuelConsumptionLPer100Km: Double? = null,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            coordinate: com.evnavigator.iran.domain.model.GeoCoordinate,
            timestamp: Date,
            currentSpeedKmh: Double? = null,
            speedLimitKmh: Double? = null,
            congestionLevelPercent: Double? = null,
            travelTimeSeconds: Long? = null,
            freeflowTravelTimeSeconds: Long? = null,
            numberOfLanes: Int? = null,
            roadType: String? = null,
            conditionDescription: String? = null,
            incidentType: String? = null,
            severityLevel: Int? = null,
            distanceToCongestionMeters: Double? = null,
            speedPercentage: Double? = null,
            source: String? = null,
            confidence: Double? = null,
            hasIncidents: Boolean = false,
            averageDelaySecondsPerKm: Double? = null,
            evPercentage: Double? = null,
            expectedFuelConsumptionLPer100Km: Double? = null,
            additionalProperties: Map<String, Any>? = null
        ): TrafficData {
            return TrafficData(
                coordinate = coordinate,
                timestamp = timestamp,
                currentSpeedKmh = currentSpeedKmh,
                speedLimitKmh = speedLimitKmh,
                congestionLevelPercent = congestionLevelPercent,
                travelTimeSeconds = travelTimeSeconds,
                freeflowTravelTimeSeconds = freeflowTravelTimeSeconds,
                numberOfLanes = numberOfLanes,
                roadType = roadType,
                conditionDescription = conditionDescription,
                incidentType = incidentType,
                severityLevel = severityLevel,
                distanceToCongestionMeters = distanceToCongestionMeters,
                speedPercentage = speedPercentage,
                source = source,
                confidence = confidence,
                hasIncidents = hasIncidents,
                averageDelaySecondsPerKm = averageDelaySecondsPerKm,
                evPercentage = evPercentage,
                expectedFuelConsumptionLPer100Km = expectedFuelConsumptionLPer100Km,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Calculates the effective travel time based on current conditions.
     *
     * @return Travel time in seconds, or null if not enough data
     */
    val calculatedTravelTimeSeconds: Long?
        get() {
            return when {
                travelTimeSeconds != null -> travelTimeSeconds
                congestionLevelPercent != null && speedLimitKmh != null && congestionLevelPercent!! > 0 ->
                    (travelTimeSeconds ?: 
                        Math.round((1.0 + congestionLevelPercent!! / 100) * (freeflowTravelTimeSeconds?.toDouble() ?: 0.0))).toLong()
                else -> null
            }
        }

    /**
     * Gets the traffic impact factor for EV driving.
     *
     * @return Impact score from 0 (no effect) to 1 (highly impactful)
     */
    val evImpactFactor: Double
        get() {
            var impact = 0.0
            
            // Congestion level impact - more congestion = higher impact
            if (congestionLevelPercent != null) {
                impact += Math.min(1.0, congestionLevelPercent!! / 100)
            }
            
            // Speed vs speed limit ratio - slower travel is less efficient for EVs  
            if (currentSpeedKmh != null && speedLimitKmh != null && speedLimitKmh!! > 0) {
                val speedRatio = currentSpeedKmh!! / speedLimitKmh!!
                // If we're going significantly below the speed limit, it's more impactful
                if (speedRatio < 0.5) {
                    impact += 0.5  // High congestion impact for EVs
                } else if (speedRatio < 0.7) {
                    impact += 0.3
                }
            }
            
            return Math.min(1.0, impact)
        }

    /**
     * Checks if the traffic is significantly impacting driving efficiency.
     *
     * @return True if conditions are likely to cause significant delays or impacts
     */
    val isSignificantlyImpactingEfficiency: Boolean
        get() {
            // Considered significantly impacted if:
            // 1. Congestion level > 70% OR 
            // 2. Speed < 30 km/h AND speed limit is > 40 km/h
            return when {
                congestionLevelPercent != null && congestionLevelPercent!! > 70 -> true
                currentSpeedKmh != null && speedLimitKmh != null && 
                        currentSpeedKmh!! < 30 && speedLimitKmh!! > 40 -> true
                else -> false
            }
        }

    /**
     * Gets the percentage of time that is spent in congestion.
     *
     * @return Percentage (0-100%) or null if not enough data
     */
    val congestionPercentage: Double?
        get() {
            return when {
                congestionLevelPercent != null -> congestionLevelPercent
                speedLimitKmh != null && currentSpeedKmh != null && 
                  speedLimitKmh!! > 0 && currentSpeedKmh!! >= 0 ->
                    (1.0 - (currentSpeedKmh!! / speedLimitKmh!!)) * 100.0
                else -> null
            }
        }
}