package com.evnavigator.iran.domain.navigation

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.RouteSegment
import java.util.*

/**
 * Represents the current state and progress of a navigation session.
 *
 * This model captures real-time information about where the driver is on their route,
 * upcoming maneuvers, distance to turn, time estimates, and other relevant data needed for turn-by-turn navigation.
 */
data class NavigationProgress(
    /**
     * The current position of the vehicle
     */
    val currentPosition: GeoCoordinate,

    /**
     * The active segment of the route being navigated on
     */
    val currentSegment: RouteSegment,

    /**
     * The upcoming maneuver to execute
     */
    val nextManeuver: NavigationManeuver? = null,

    /**
     * Distance remaining to the destination along the current route (meters)
     */
    val distanceToDestinationMeters: Double,

    /**
     * Estimated time of arrival at destination in seconds from now
     */
    val estimatedTimeOfArrivalSeconds: Long? = null,

    /**
     * Time remaining until reaching the next maneuver point (seconds)
     */
    val timeToNextManeuverSeconds: Long? = null,

    /**
     * Distance to the next maneuver point (meters)  
     */
    val distanceToNextManeuverMeters: Double? = null,

    /**
     * Bearing from current position to next maneuver
     */
    val bearingToNextManeuverDegrees: Double? = null,

    /**
     * The index of the current segment in the overall route
     */
    val currentSegmentIndex: Int,

    /**
     * Indicates if we are currently on the correct route (not off-track)
     */
    val isOnRoute: Boolean = true,

    /**
     * Percentage of route completed so far (0.0 to 1.0)
     */
    val routeProgressPercentage: Double? = null,

    /**
     * The current speed limit at this location
     */
    val currentSpeedLimitKmh: Double? = null,

    /**
     * Current speed in km/h
     */
    val currentSpeedKmh: Double? = null,

    /**
     * Timestamp when the navigation progress was last updated
     */
    val lastUpdated: Date = Date(),

    /**
     * The next few segments that will be traveled on (for preview)
     */
    val upcomingSegments: List<RouteSegment> = emptyList(),

    /**
     * Any special instructions or warnings for the driver
     */
    val driverWarnings: List<NavigationWarning> = emptyList(),

    /**
     * Indicates if we are in a restricted zone that requires attention
     */
    val isRestrictedRoad: Boolean = false,

    /**
     * The current navigation status/state
     */
    val status: NavigationStatus,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            currentPosition: GeoCoordinate,
            currentSegment: RouteSegment,
            nextManeuver: NavigationManeuver? = null,
            distanceToDestinationMeters: Double,
            estimatedTimeOfArrivalSeconds: Long? = null,
            timeToNextManeuverSeconds: Long? = null,
            distanceToNextManeuverMeters: Double? = null,
            bearingToNextManeuverDegrees: Double? = null,
            currentSegmentIndex: Int,
            isOnRoute: Boolean = true,
            routeProgressPercentage: Double? = null,
            currentSpeedLimitKmh: Double? = null,
            currentSpeedKmh: Double? = null,
            lastUpdated: Date = Date(),
            upcomingSegments: List<RouteSegment> = emptyList(),
            driverWarnings: List<NavigationWarning> = emptyList(),
            isRestrictedRoad: Boolean = false,
            status: NavigationStatus,
            additionalProperties: Map<String, Any>? = null
        ): NavigationProgress {
            return NavigationProgress(
                currentPosition = currentPosition,
                currentSegment = currentSegment,
                nextManeuver = nextManeuver,
                distanceToDestinationMeters = distanceToDestinationMeters,
                estimatedTimeOfArrivalSeconds = estimatedTimeOfArrivalSeconds,
                timeToNextManeuverSeconds = timeToNextManeuverSeconds,
                distanceToNextManeuverMeters = distanceToNextManeuverMeters,
                bearingToNextManeuverDegrees = bearingToNextManeuverDegrees,
                currentSegmentIndex = currentSegmentIndex,
                isOnRoute = isOnRoute,
                routeProgressPercentage = routeProgressPercentage,
                currentSpeedLimitKmh = currentSpeedLimitKmh,
                currentSpeedKmh = currentSpeedKmh,
                lastUpdated = lastUpdated,
                upcomingSegments = upcomingSegments,
                driverWarnings = driverWarnings,
                isRestrictedRoad = isRestrictedRoad,
                status = status,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Gets the estimated time to reach destination in minutes.
     *
     * @return Time in minutes or null if not available
     */
    val estimatedTimeToDestinationMinutes: Double?
        get() = when {
            estimatedTimeOfArrivalSeconds != null -> 
                (estimatedTimeOfArrivalSeconds / 60.0)
            else -> null
        }

    /**
     * Gets the distance to destination in kilometers.
     *
     * @return Distance in km or null if not available
     */
    val distanceToDestinationKm: Double?
        get() = when {
            distanceToDestinationMeters != null && distanceToDestinationMeters > 0 -> 
                distanceToDestinationMeters / 1000.0
            else -> null
        }

    /**
     * Gets the current direction of travel.
     *
     * @return Bearing in degrees from true north or null if not available
     */
    val bearing: Double?
        get() = when {
            bearingToNextManeuverDegrees != null -> bearingToNextManeuverDegrees
            else -> null
        }

    /**
     * Checks if the current segment is a highway.
     *
     * @return True if on highway, false otherwise
     */
    val isHighway: Boolean
        get() = when {
            currentSegment.roadType != null -> 
                currentSegment.roadType.equals("highway", ignoreCase = true) ||
                currentSegment.roadType.equals("motorway", ignoreCase = true)
            else -> false
        }

    /**
     * Gets the next maneuver type.
     *
     * @return Maneuver type or null if not available
     */
    val nextManeuverType: NavigationManeuver.Type?
        get() = when {
            nextManeuver != null -> nextManeuver.type
            else -> null
        }
}