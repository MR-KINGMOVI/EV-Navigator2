package com.evnavigator.iran.domain.navigation

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.routing.RoutingProfile
import kotlinx.coroutines.flow.Flow

/**
 * Interface for offline navigation functionality.
 *
 * This interface defines the contract for providing turn-by-turn navigation,
 * route guidance, and map-matching capabilities when network access is unavailable.
 */
interface NavigationEngine {
    
    /**
     * Gets the current GPS position with accuracy information.
     *
     * @return Current location or null if not available
     */
    suspend fun getCurrentPosition(): GeoCoordinate?

    /**
     * Gets the current heading/direction of travel.
     *
     * @return Heading in degrees from true north, or null if unknown
     */
    suspend fun getHeading(): Double?

    /**
     * Gets map-matched position for a GPS coordinate.
     *
     * @param gpsCoordinate The raw GPS coordinate
     * @return Map-matched coordinate or the original if matching fails
     */
    suspend fun matchToRoad(gpsCoordinate: GeoCoordinate): GeoCoordinate

    /**
     * Gets real-time route progress information.
     *
     * @param currentLocation Current position of the vehicle
     * @return RouteProgress object with turn-by-turn instructions and status
     */
    suspend fun getRouteProgress(currentLocation: GeoCoordinate): NavigationProgress?

    /**
     * Calculates a new route based on current position and destination.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate  
     * @param routingProfile Optional custom routing profile to use
     * @return New calculated route or null if not possible
     */
    suspend fun calculateRoute(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        routingProfile: RoutingProfile? = null
    ): Route?

    /**
     * Gets the current navigation status.
     *
     * @return Current navigation state information
     */
    fun getNavigationStatus(): NavigationStatus

    /**
     * Starts turn-by-turn navigation along a route.
     *
     * @param route The route to navigate on
     * @param startCoordinate Starting coordinate (optional, can be from route)
     */
    suspend fun startNavigation(route: Route, startCoordinate: GeoCoordinate? = null)

    /**
     * Updates the current route during active navigation.
     *
     * @param newRoute New route to follow
     */
    suspend fun updateActiveRoute(newRoute: Route)

    /**
     * Stops active navigation and clears any ongoing turn-by-turn session.
     */
    fun stopNavigation()

    /**
     * Gets real-time traffic information for current location (if available).
     *
     * @return TrafficData or null if not available
     */
    suspend fun getTrafficAtCurrentLocation(): com.evnavigator.iran.domain.model.weather.TrafficData?

    /**
     * Checks if the vehicle is currently in a navigation session.
     *
     * @return True if actively navigating, false otherwise
     */
    fun isInNavigationSession(): Boolean

    /**
     * Gets the current route being followed.
     *
     * @return Current active route or null if not navigating
     */
    suspend fun getActiveRoute(): Route?

    /**
     * Gets real-time weather conditions at current location (if available).
     *
     * @return WeatherSnapshot or null if not available
     */
    suspend fun getWeatherAtCurrentLocation(): com.evnavigator.iran.domain.model.weather.WeatherSnapshot?

    /**
     * Checks if the device has sufficient offline map data for navigation.
     *
     * @return True if offline maps are available, false otherwise
     */
    fun hasOfflineMapsAvailable(): Boolean

    /**
     * Gets the current estimated time of arrival at destination.
     *
     * @param currentLocation Current position of the vehicle
     * @return Estimated time of arrival or null if not calculable
     */
    suspend fun getEstimatedTimeOfArrival(currentLocation: GeoCoordinate): Long?

    /**
     * Gets a list of nearby charging stations (if available offline).
     *
     * @param coordinate Reference coordinate for location-based search
     * @param maxDistanceMeters Maximum distance to look for stations
     * @return List of nearby charging stations or empty list if none found
     */
    suspend fun getNearbyChargingStations(
        coordinate: GeoCoordinate,
        maxDistanceMeters: Double = 5000.0
    ): List<com.evnavigator.iran.domain.model.Charger>

    /**
     * Gets the current navigation session state.
     *
     * @return NavigationSessionState or null if not in a session
     */
    fun getNavigationSessionState(): NavigationSessionState?

    /**
     * Updates the vehicle's position for map-matching and progress tracking.
     *
     * @param newPosition The new GPS coordinate of the vehicle
     * @return Updated navigation status
     */
    suspend fun updatePosition(newPosition: GeoCoordinate): NavigationStatus

    /**
     * Gets the current route progress percentage (0.0 to 1.0).
     *
     * @param currentLocation Current position of the vehicle
     * @return Progress percentage or null if not available
     */
    suspend fun getRouteProgressPercentage(currentLocation: GeoCoordinate): Double?

    /**
     * Checks if a location is on track for the current route.
     *
     * @param coordinate The location to check
     * @return True if location is on track, false otherwise
     */
    suspend fun isOnTrack(coordinate: GeoCoordinate): Boolean

    /**
     * Gets the remaining distance to destination along the active route.
     *
     * @param currentLocation Current position of the vehicle
     * @return Remaining distance in meters or null if not calculable
     */
    suspend fun getRemainingDistance(currentLocation: GeoCoordinate): Double?

    /**
     * Gets the estimated time to travel from current location to destination.
     *
     * @param currentLocation Current position of the vehicle
     * @return Estimated time in seconds or null if not calculable
     */
    suspend fun getTimeToDestination(currentLocation: GeoCoordinate): Long?
}