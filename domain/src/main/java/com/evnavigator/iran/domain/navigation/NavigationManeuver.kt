package com.evnavigator.iran.domain.navigation

import com.evnavigator.iran.domain.model.GeoCoordinate
import java.util.*

/**
 * Represents a single navigation maneuver or turn instruction.
 *
 * This model captures all necessary information for providing driver instructions,
 * including the type of maneuver, location details, and any special conditions.
 */
data class NavigationManeuver(
    /**
     * The geographic coordinate where this maneuver occurs
     */
    val coordinate: GeoCoordinate,

    /**
     * Type or category of maneuver to perform
     */
    val type: Type,

    /**
     * Name of the road/street at the maneuver point
     */
    val streetName: String? = null,

    /**
     * Direction of travel after this maneuver (0-360 degrees)
     */
    val direction: Double? = null,

    /**
     * Bearing to take from current position toward next maneuver
     */
    val bearing: Double? = null,

    /**
     * Distance to the next maneuver point in meters
     */
    val distanceToNextManeuverMeters: Double? = null,

    /**
     * Time required to execute this maneuver (seconds)
     */
    val timeToExecuteSeconds: Long? = null,

    /**
     * Approximate duration of the maneuver in seconds
     */
    val estimatedDurationSeconds: Long? = null,

    /**
     * Distance from current position to the maneuver point in meters
     */
    val distanceFromCurrentPositionMeters: Double? = null,

    /**
     * Bearing from current position to this maneuver (for visualization)
     */
    val bearingFromCurrentPosition: Double? = null,

    /**
     * Indicates if there are any warnings or restrictions at this location
     */
    val warnings: List<NavigationWarning> = emptyList(),

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            coordinate: GeoCoordinate,
            type: Type,
            streetName: String? = null,
            direction: Double? = null,
            bearing: Double? = null,
            distanceToNextManeuverMeters: Double? = null,
            timeToExecuteSeconds: Long? = null,
            estimatedDurationSeconds: Long? = null,
            distanceFromCurrentPositionMeters: Double? = null,
            bearingFromCurrentPosition: Double? = null,
            warnings: List<NavigationWarning> = emptyList(),
            additionalProperties: Map<String, Any>? = null
        ): NavigationManeuver {
            return NavigationManeuver(
                coordinate = coordinate,
                type = type,
                streetName = streetName,
                direction = direction,
                bearing = bearing,
                distanceToNextManeuverMeters = distanceToNextManeuverMeters,
                timeToExecuteSeconds = timeToExecuteSeconds,
                estimatedDurationSeconds = estimatedDurationSeconds,
                distanceFromCurrentPositionMeters = distanceFromCurrentPositionMeters,
                bearingFromCurrentPosition = bearingFromCurrentPosition,
                warnings = warnings,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Gets the maneuver type as a human-readable string.
     *
     * @return Formatted description of the maneuver
     */
    fun getFormattedDescription(): String {
        return when (type) {
            Type.TURN_LEFT -> "Turn left"
            Type.TURN_RIGHT -> "Turn right"
            Type.STRAIGHT -> "Continue straight"
            Type.LEFT_SHARP -> "Sharp left turn"
            Type.RIGHT_SHARP -> "Sharp right turn"
            Type.LEFT_SLIGHT -> "Slight left turn"
            Type.RIGHT_SLIGHT -> "Slight right turn"
            Type.U_TURN -> "Make a U-turn"
            Type.ROUNDABOUT -> "Take the roundabout"
            Type.ENTRANCE_RAMP -> "Take the entrance ramp"
            Type.EXIT_RIGHT -> "Exit to the right"
            Type.EXIT_LEFT -> "Exit to the left"
            Type.STEP_UP -> "Go up steps"
            Type.STEP_DOWN -> "Descend steps"
            Type.FERRY -> "Ferry"
            Type.TOLL_BOOTH -> "Toll booth"
            Type.TRAFFIC_LIGHTS -> "Traffic lights"
            Type.OTHER -> "Proceed with caution"
        }
    }

    /**
     * Gets the maneuver type as a string for UI display.
     *
     * @return Simple description of maneuver
     */
    fun getSimpleDescription(): String {
        return when (type) {
            Type.TURN_LEFT, Type.LEFT_SHARP, Type.LEFT_SLIGHT -> "Turn Left"
            Type.TURN_RIGHT, Type.RIGHT_SHARP, Type.RIGHT_SLIGHT -> "Turn Right"
            Type.STRAIGHT -> "Go Straight"
            Type.U_TURN -> "U-Turn"
            Type.ROUNDABOUT -> "Roundabout"
            Type.ENTRANCE_RAMP -> "Ramp"
            Type.EXIT_RIGHT, Type.EXIT_LEFT -> "Exit"
            Type.STEP_UP, Type.STEP_DOWN -> "Stairs"
            Type.FERRY -> "Ferry"
            Type.TOLL_BOOTH -> "Toll Booth"
            Type.TRAFFIC_LIGHTS -> "Lights"
            else -> "Proceed"
        }
    }

    /**
     * Gets the maneuver type as an icon code for UI.
     *
     * @return Icon identifier or null if not available
     */
    val getIconCode: String?
        get() = when (type) {
            Type.TURN_LEFT -> "\u21BA"  // Left turn arrow
            Type.TURN_RIGHT -> "\u21BB" // Right turn arrow  
            Type.STRAIGHT -> "\u2192"   // Straight line
            Type.LEFT_SHARP -> "\u21B0"  // Sharp left curve
            Type.RIGHT_SHARP -> "\u21B1" // Sharp right curve
            Type.LEFT_SLIGHT -> "\u21B0" // Slight left turn
            Type.RIGHT_SLIGHT -> "\u21B1"// Slight right turn
            Type.U_TURN -> "\u21BA"     // U-turn symbol
            Type.ROUNDABOUT -> "○"      // Roundabout circle
            Type.ENTRANCE_RAMP -> "↗"   // Entrance ramp
            Type.EXIT_RIGHT, Type.EXIT_LEFT -> "EXIT"
            Type.STEP_UP -> "↑"         // Up steps
            Type.STEP_DOWN -> "↓"       // Down steps
            Type.FERRY -> "⛴"           // Ferry boat
            Type.TOLL_BOOTH -> "$"      // Toll booth symbol
            Type.TRAFFIC_LIGHTS -> "🚥"  // Traffic lights
            else -> ""                  // Other maneuvers
        }

    /**
     * Checks if the maneuver requires special attention or warnings.
     *
     * @return True if there are important warnings, false otherwise
     */
    val hasImportantWarnings: Boolean
        get() = warnings.isNotEmpty()

    /**
     * Gets a list of all warning messages for this maneuver.
     *
     * @return List of warning strings or empty list
     */
    fun getWarningMessages(): List<String> {
        return warnings.map { it.message }
    }

    /**
     * Checks if the maneuver is related to turning.
     *
     * @return True if it's a turn-related maneuver, false otherwise
     */
    val isTurnManeuver: Boolean
        get() = when (type) {
            Type.TURN_LEFT, Type.TURN_RIGHT, Type.LEFT_SHARP, Type.RIGHT_SHARP,
            Type.LEFT_SLIGHT, Type.RIGHT_SLIGHT, Type.U_TURN -> true
            else -> false
        }

    /**
     * Gets the expected speed limit for this maneuver.
     *
     * @return Speed in km/h or null if not specified
     */
    val expectedSpeedLimitKmh: Double?
        get() = when {
            type == Type.ROUNDABOUT -> 30.0 // Typical roundabout speed limit
            else -> null
        }

    /**
     * Gets the maneuver's bearing to next point.
     *
     * @return Bearing in degrees or null if not available
     */
    val nextBearing: Double?
        get() = when {
            bearing != null -> bearing
            direction != null -> direction
            else -> null
        }

    /**
     * Gets the maneuver type enumeration.
     */
    enum class Type {
        TURN_LEFT,
        TURN_RIGHT, 
        STRAIGHT,
        LEFT_SHARP,
        RIGHT_SHARP,
        LEFT_SLIGHT,
        RIGHT_SLIGHT,
        U_TURN,
        ROUNDABOUT,
        ENTRANCE_RAMP,
        EXIT_RIGHT,
        EXIT_LEFT,
        STEP_UP,
        STEP_DOWN,
        FERRY,
        TOLL_BOOTH,
        TRAFFIC_LIGHTS,
        OTHER
    }
}