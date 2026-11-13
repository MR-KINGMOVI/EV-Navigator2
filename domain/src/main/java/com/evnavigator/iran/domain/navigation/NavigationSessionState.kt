package com.evnavigator.iran.domain.navigation

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import java.util.*

/**
 * Represents the complete state of a navigation session.
 *
 * This model captures all aspects of an active or recent navigation session,
 * including route information, current position, progress tracking, and user settings.
 */
data class NavigationSessionState(
    /**
     * Unique identifier for this navigation session
     */
    val sessionId: String,

    /**
     * The original route being followed
     */
    val route: Route,

    /**
     * Current position of the vehicle in the navigation session
     */
    val currentPosition: GeoCoordinate,

    /**
     * Timestamp when the session was started
     */
    val startTime: Date,

    /**
     * Timestamp when the last update occurred
     */
    val lastUpdated: Date,

    /**
     * Indicates if a route is currently being calculated or updated
     */
    val isCalculatingRoute: Boolean = false,

    /**
     * The current segment of the route that's active
     */
    val currentSegmentIndex: Int = 0,

    /**
     * Distance remaining to destination along the current route (meters)
     */
    val distanceToDestinationMeters: Double? = null,

    /**
     * Estimated time of arrival at destination in seconds from now
     */
    val estimatedTimeOfArrivalSeconds: Long? = null,

    /**
     * Time elapsed since navigation started (seconds)
     */
    val elapsedTimeSeconds: Long = 0,

    /**
     * Current speed in km/h
     */
    val currentSpeedKmh: Double? = null,

    /**
     * Speed limit at the vehicle's current location (km/h)
     */
    val speedLimitKmh: Double? = null,

    /**
     * Indicates if navigation is currently active and following a route
     */
    val isActive: Boolean = true,

    /**
     * The status of the overall navigation system
     */
    val status: NavigationStatus,

    /**
     * Current progress along the route (0.0 to 1.0)
     */
    val routeProgressPercentage: Double? = null,

    /**
     * Indicates if we are currently on track or have deviated significantly
     */
    val isOnRoute: Boolean = true,

    /**
     * The next maneuver in sequence
     */
    val nextManeuver: NavigationManeuver? = null,

    /**
     * List of recent maneuvers for context
     */
    val recentManeuvers: List<NavigationManeuver> = emptyList(),

    /**
     * Warnings or alerts related to the current navigation state
     */
    val activeWarnings: List<NavigationWarning> = emptyList(),

    /**
     * Indicates if a new route calculation is needed
     */
    val needsRouteUpdate: Boolean = false,

    /**
     * The last valid position before GPS was lost (if applicable)
     */
    val lastKnownPosition: GeoCoordinate? = null,

    /**
     * Time since the last valid GPS fix (seconds)
     */
    val timeSinceLastGpsFixSeconds: Long? = null,

    /**
     * Indicates if this session is using offline data only
     */
    val isOfflineMode: Boolean = true,

    /**
     * The routing profile used for this navigation
     */
    val routingProfile: com.evnavigator.iran.domain.model.routing.RoutingProfile? = null,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            sessionId: String,
            route: Route,
            currentPosition: GeoCoordinate,
            startTime: Date,
            lastUpdated: Date,
            isCalculatingRoute: Boolean = false,
            currentSegmentIndex: Int = 0,
            distanceToDestinationMeters: Double? = null,
            estimatedTimeOfArrivalSeconds: Long? = null,
            elapsedTimeSeconds: Long = 0,
            currentSpeedKmh: Double? = null,
            speedLimitKmh: Double? = null,
            isActive: Boolean = true,
            status: NavigationStatus,
            routeProgressPercentage: Double? = null,
            isOnRoute: Boolean = true,
            nextManeuver: NavigationManeuver? = null,
            recentManeuvers: List<NavigationManeuver> = emptyList(),
            activeWarnings: List<NavigationWarning> = emptyList(),
            needsRouteUpdate: Boolean = false,
            lastKnownPosition: GeoCoordinate? = null,
            timeSinceLastGpsFixSeconds: Long? = null,
            isOfflineMode: Boolean = true,
            routingProfile: com.evnavigator.iran.domain.model.routing.RoutingProfile? = null,
            additionalProperties: Map<String, Any>? = null
        ): NavigationSessionState {
            return NavigationSessionState(
                sessionId = sessionId,
                route = route,
                currentPosition = currentPosition,
                startTime = startTime,
                lastUpdated = lastUpdated,
                isCalculatingRoute = isCalculatingRoute,
                currentSegmentIndex = currentSegmentIndex,
                distanceToDestinationMeters = distanceToDestinationMeters,
                estimatedTimeOfArrivalSeconds = estimatedTimeOfArrivalSeconds,
                elapsedTimeSeconds = elapsedTimeSeconds,
                currentSpeedKmh = currentSpeedKmh,
                speedLimitKmh = speedLimitKmh,
                isActive = isActive,
                status = status,
                routeProgressPercentage = routeProgressPercentage,
                isOnRoute = isOnRoute,
                nextManeuver = nextManeuver,
                recentManeuvers = recentManeuvers,
                activeWarnings = activeWarnings,
                needsRouteUpdate = needsRouteUpdate,
                lastKnownPosition = lastKnownPosition,
                timeSinceLastGpsFixSeconds = timeSinceLastGpsFixSeconds,
                isOfflineMode = isOfflineMode,
                routingProfile = routingProfile,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Gets the estimated distance to destination in kilometers.
     *
     * @return Distance in km or null if not available
     */
    val distanceToDestinationKm: Double?
        get() = when {
            distanceToDestinationMeters != null -> 
                distanceToDestinationMeters / 1000.0
            else -> null
        }

    /**
     * Gets the current speed in mph.
     *
     * @return Speed in mph or null if not available
     */
    val currentSpeedMph: Double?
        get() = when {
            currentSpeedKmh != null -> 
                currentSpeedKmh / 1.60934 // Convert km/h to mph
            else -> null
        }

    /**
     * Gets the speed limit in mph.
     *
     * @return Speed limit in mph or null if not available
     */
    val speedLimitMph: Double?
        get() = when {
            speedLimitKmh != null ->
                speedLimitKmh / 1.60934 // Convert km/h to mph
            else -> null
        }

    /**
     * Gets the elapsed time as a formatted string.
     *
     * @return Formatted time string (HH:MM:SS)
     */
    val elapsedTimeFormatted: String
        get() {
            val totalSeconds = elapsedTimeSeconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    /**
     * Gets the current route progress in a user-friendly format.
     *
     * @return Progress percentage or null if not available
     */
    val formattedProgress: String?
        get() = when {
            routeProgressPercentage != null -> 
                String.format("%.1f", (routeProgressPercentage * 100.0)) + "%"
            else -> null
        }

    /**
     * Checks if the session is in a valid state for navigation.
     *
     * @return True if active and valid, false otherwise
     */
    val isValid: Boolean
        get() = when {
            // If we have a route and current position, it's likely valid
            route != null && currentPosition != null -> true
            else -> false
        }

    /**
     * Gets the estimated time of arrival as a formatted string.
     *
     * @return Formatted time string (HH:MM) or null if not available
     */
    val estimatedTimeOfArrivalFormatted: String?
        get() = when {
            estimatedTimeOfArrivalSeconds != null -> {
                // This would normally be combined with the start time to give actual ETA
                null  // For now, return null as we don't have that information here
            }
            else -> null
        }

    /**
     * Gets a value indicating if this is an offline navigation session.
     *
     * @return True for offline sessions, false otherwise
     */
    val isOffline: Boolean
        get() = when {
            isOfflineMode != null -> isOfflineMode
            else -> true  // Assume offline by default
        }

    /**
     * Gets the current segment of the route.
     *
     * @return Current RouteSegment or null if not available
     */
    val currentRouteSegment: com.evnavigator.iran.domain.model.RouteSegment?
        get() = try {
            if (route.segments != null && currentSegmentIndex < route.segments.size) {
                route.segments[currentSegmentIndex]
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * Gets the next maneuver in sequence.
     *
     * @return Next NavigationManeuver or null if not available
     */
    val nextRouteManeuver: NavigationManeuver?
        get() = when {
            nextManeuver != null -> nextManeuver
            else -> null
        }

    /**
     * Gets the current status description.
     *
     * @return Status message or empty string if not available
     */
    val statusDescription: String
        get() = when (status) {
            NavigationStatus.IDLE -> "Navigation is idle"
            NavigationStatus.CALCULATING_ROUTE -> "Calculating route..."
            NavigationStatus.NAVIGATING -> "Navigating to destination"
            NavigationStatus.PAUSED -> "Navigation paused"
            NavigationStatus.STOPPED -> "Navigation stopped"
            NavigationStatus.SEARCHING_ALTERNATIVE_ROUTE -> "Searching for alternative route"
            NavigationStatus.INVALID_ROUTE -> "Invalid route - please recalculate"
            NavigationStatus.GPS_LOST -> "GPS signal lost"
            NavigationStatus.NO_OFFLINE_MAPS_AVAILABLE -> "No offline maps available"
            NavigationStatus.POSITION_UNKNOWN -> "Unable to determine position"
            NavigationStatus.PROCESSING -> "Processing navigation data"
            NavigationStatus.ERROR -> "Navigation error occurred"
            NavigationStatus.NO_MAP_COVERAGE -> "No map coverage in area"
            NavigationStatus.SUSPENDED_LOW_BATTERY -> "Suspended due to low battery"
            NavigationStatus.INITIALIZING -> "Initializing navigation..."
            NavigationStatus.OFFLINE_MODE -> "Offline mode active"
            NavigationStatus.DISABLED -> "Navigation disabled"
        }

    /**
     * Gets the remaining distance in miles.
     *
     * @return Distance in miles or null if not available
     */
    val distanceToDestinationMiles: Double?
        get() = when {
            distanceToDestinationMeters != null ->
                distanceToDestinationMeters / 1609.34 // Convert meters to miles
            else -> null
        }
}