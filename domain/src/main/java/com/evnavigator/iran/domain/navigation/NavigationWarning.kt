package com.evnavigator.iran.domain.navigation

import java.util.*

/**
 * Represents a warning or special instruction related to navigation.
 *
 * This model captures important information that should be communicated to the driver,
 * such as speed limits, road conditions, hazards, etc., that are not part of core routing
 * but affect safe and efficient travel.
 */
data class NavigationWarning(
    /**
     * Unique identifier for this warning type
     */
    val id: String,

    /**
     * Human-readable description of the warning
     */
    val message: String,

    /**
     * Severity level of the warning (1-5, with 5 being most severe)
     */
    val severity: Int,

    /**
     * Type or category of the warning
     */
    val type: WarningType,

    /**
     * The geographic coordinate where this warning applies
     */
    val coordinate: com.evnavigator.iran.domain.model.GeoCoordinate? = null,

    /**
     * Distance from current position to the warning location (meters)
     */
    val distanceToWarningMeters: Double? = null,

    /**
     * Time until this warning becomes active or relevant (seconds)
     */
    val timeUntilActiveSeconds: Long? = null,

    /**
     * Indicates if the warning is currently active
     */
    val isActive: Boolean = false,

    /**
     * Optional URL to a web page with more information about the warning
     */
    val infoUrl: String? = null,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            id: String,
            message: String,
            severity: Int,
            type: WarningType,
            coordinate: com.evnavigator.iran.domain.model.GeoCoordinate? = null,
            distanceToWarningMeters: Double? = null,
            timeUntilActiveSeconds: Long? = null,
            isActive: Boolean = false,
            infoUrl: String? = null,
            additionalProperties: Map<String, Any>? = null
        ): NavigationWarning {
            return NavigationWarning(
                id = id,
                message = message,
                severity = severity,
                type = type,
                coordinate = coordinate,
                distanceToWarningMeters = distanceToWarningMeters,
                timeUntilActiveSeconds = timeUntilActiveSeconds,
                isActive = isActive,
                infoUrl = infoUrl,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Checks if this warning is of high importance.
     *
     * @return True for severity 4 or 5, false otherwise
     */
    val isHighPriority: Boolean
        get() = when (severity) {
            4, 5 -> true
            else -> false
        }

    /**
     * Gets a simplified representation of the warning.
     *
     * @return Formatted string for UI display
     */
    fun toDisplayString(): String {
        return "[${type.name}] $message"
    }

    /**
     * Checks if this warning is related to traffic or speed restrictions.
     *
     * @return True if it's a traffic-related warning, false otherwise
     */
    val isTrafficRelated: Boolean
        get() = when (type) {
            WarningType.SPEED_LIMIT,
            WarningType.CONSTRUCTION_ZONE,
            WarningType.TOLL_BOOTH,
            WarningType.TRAFFIC_JAM -> true
            else -> false
        }

    /**
     * Gets the warning type as a string for UI display.
     *
     * @return Formatted name of the warning type
     */
    val formattedTypeName: String
        get() = when (type) {
            WarningType.SPEED_LIMIT -> "Speed Limit"
            WarningType.CONSTRUCTION_ZONE -> "Construction Zone" 
            WarningType.TOLL_BOOTH -> "Toll Booth"
            WarningType.TRAFFIC_JAM -> "Traffic Jam"
            WarningType.ROAD_HAZARD -> "Road Hazard"
            WarningType.WEATHER_HAZARD -> "Weather Hazard"
            WarningType.LANE_CLOSED -> "Lane Closed"
            WarningType.RESTRICTED_AREA -> "Restricted Area"
            WarningType.SCHOOL_ZONE -> "School Zone"
            WarningType.TRAFFIC_LIGHT -> "Traffic Light"
            WarningType.RAILWAY_CROSSING -> "Railway Crossing"
            WarningType.FERRY -> "Ferry Service"
            WarningType.OTHER -> "Other Notice"
        }
}

/**
 * Enumerates different types of navigation warnings.
 */
enum class WarningType {
    /**
     * Speed limit changes or restrictions
     */
    SPEED_LIMIT,

    /**
     * Construction zones or road work areas
     */
    CONSTRUCTION_ZONE,

    /**
     * Toll booths or payment points
     */
    TOLL_BOOTH,

    /**
     * Traffic congestion or jams
     */
    TRAFFIC_JAM,

    /**
     * Road hazards (potholes, debris, etc.)
     */
    ROAD_HAZARD,

    /**
     * Weather-related hazards (ice, snow, rain)
     */
    WEATHER_HAZARD,

    /**
     * Lane closures or restrictions
     */
    LANE_CLOSED,

    /**
     * Restricted access areas or no entry signs
     */
    RESTRICTED_AREA,

    /**
     * School zones with time-based speed limits
     */
    SCHOOL_ZONE,

    /**
     * Traffic lights and signals
     */
    TRAFFIC_LIGHT,

    /**
     * Railway crossings without barriers
     */
    RAILWAY_CROSSING,

    /**
     * Ferry services or waterway crossings
     */
    FERRY,

    /**
     * Any other type of warning not covered above
     */
    OTHER
}