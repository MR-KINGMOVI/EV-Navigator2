package com.evnavigator.iran.domain.model.routing

import java.util.*

/**
 * Represents speed-related parameters for routing calculations.
 *
 * This model defines the expected or maximum speeds that should be used when calculating routes,
 * including both typical and maximum speed limits, as well as factors that affect travel time.
 */
data class RouteSpeedProfile(
    /**
     * The base speed in km/h to use for route calculation
     */
    val baseSpeedKmh: Double,

    /**
     * Maximum allowed speed (e.g., highway speeds)
     */
    val maxSpeedKmh: Double? = null,

    /**
     * Minimum recommended speed (for safety or efficiency)
     */
    val minSpeedKmh: Double? = null,

    /**
     * Speed limit for different road types
     */
    val roadTypeSpeeds: Map<String, Double> = emptyMap(),

    /**
     * Adjustment factors based on time of day or conditions
     */
    val timeAdjustmentFactors: Map<TimeWindow, Double> = emptyMap(),

    /**
     * Factors that affect speed due to weather, traffic, etc.
     */
    val environmentalSpeedFactors: EnvironmentalSpeedFactor? = null,

    /**
     * Version identifier for this speed profile
     */
    val version: String? = null,

    /**
     * Source of where this profile originated from
     */
    val source: String? = null,

    /**
     * Timestamp when this specification was last updated
     */
    val lastUpdated: Date = Date(),

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            baseSpeedKmh: Double,
            maxSpeedKmh: Double? = null,
            minSpeedKmh: Double? = null,
            roadTypeSpeeds: Map<String, Double> = emptyMap(),
            timeAdjustmentFactors: Map<TimeWindow, Double> = emptyMap(),
            environmentalSpeedFactors: EnvironmentalSpeedFactor? = null,
            version: String? = null,
            source: String? = null,
            lastUpdated: Date = Date(),
            additionalProperties: Map<String, Any>? = null
        ): RouteSpeedProfile {
            return RouteSpeedProfile(
                baseSpeedKmh = baseSpeedKmh,
                maxSpeedKmh = maxSpeedKmh,
                minSpeedKmh = minSpeedKmh,
                roadTypeSpeeds = roadTypeSpeeds,
                timeAdjustmentFactors = timeAdjustmentFactors,
                environmentalSpeedFactors = environmentalSpeedFactors,
                version = version,
                source = source,
                lastUpdated = lastUpdated,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Gets the effective speed for a specific road type.
     *
     * @param roadType The type of road (e.g., highway, residential)
     * @return Speed in km/h or null if not specified
     */
    fun getSpeedForRoadType(roadType: String): Double? {
        return roadTypeSpeeds[roadType] ?: 
               (if (roadType == "highway") maxSpeedKmh else baseSpeedKmh)
    }

    /**
     * Gets the adjusted speed based on time of day or conditions.
     *
     * @param window Time window for adjustment
     * @return Adjusted speed in km/h or null if not applicable
     */
    fun getAdjustedSpeed(window: TimeWindow = TimeWindow.GENERAL): Double? {
        return timeAdjustmentFactors[window]?.let { 
            baseSpeedKmh * it 
        } ?: baseSpeedKmh
    }

    /**
     * Gets the maximum speed allowed for a given condition.
     *
     * @param roadType The type of road (optional)
     * @return Maximum speed in km/h or null if not specified
     */
    fun getMaxSpeed(roadType: String? = null): Double? {
        return when {
            maxSpeedKmh != null -> maxSpeedKmh
            else -> baseSpeedKmh // Fallback to base speed if no specific max is set
        }
    }

    /**
     * Gets the minimum recommended speed.
     *
     * @return Minimum speed in km/h or null if not specified
     */
    fun getMinSpeed(): Double? {
        return minSpeedKmh ?: (baseSpeedKmh * 0.3) // Conservative default of 30% base speed
    }
}

/**
 * Time windows for different periods.
 */
enum class TimeWindow {
    MORNING_PEAK,      // 7-9 AM
    AFTERNOON_PEAK,     // 4-6 PM  
    NIGHT,             // 10 PM - 6 AM
    WEEKEND,           // Saturday and Sunday
    GENERAL            // All other times
}

data class EnvironmentalSpeedFactor(
    val weatherFactor: Double = 1.0,
    val surfaceFactor: Double = 1.0,
    val trafficFactor: Double = 1.0,
    val visibilityFactor: Double = 1.0
)