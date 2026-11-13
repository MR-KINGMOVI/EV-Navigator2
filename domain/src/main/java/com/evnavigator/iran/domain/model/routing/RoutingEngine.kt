package com.evnavigator.iran.domain.model.routing

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import kotlinx.coroutines.flow.Flow

/**
 * Interface for offline routing engine.
 *
 * This interface defines the contract for calculating routes using locally available datasets,
 * including road networks, elevation data, and vehicle specifications when network access is unavailable.
 */
interface RoutingEngine {
    
    /**
     * Calculates a route between start and end points using offline capabilities.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate  
     * @param viaWaypoints Optional list of waypoints to visit in sequence
     * @return Calculated Route or null if routing is not possible
     */
    suspend fun calculateRoute(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vararg viaWaypoints: GeoCoordinate
    ): Route?

    /**
     * Calculates a route between multiple points with intermediate stops.
     *
     * @param waypoints List of coordinates to visit in sequence
     * @return Calculated Route or null if routing is not possible
     */
    suspend fun calculateRoute(
        waypoints: List<GeoCoordinate>
    ): Route?

    /**
     * Gets the next few alternative routes for a given start and destination.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return A list of calculated routes ordered by preference/quality
     */
    suspend fun getAlternativeRoutes(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): List<Route>

    /**
     * Gets the estimated travel time between two points using offline data.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return Estimated travel time in seconds or null if not available
     */
    suspend fun getEstimatedTravelTime(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Long?

    /**
     * Gets the distance between two points using offline data.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return Distance in meters or null if not available
     */
    suspend fun getDistance(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Double?

    /**
     * Gets the elevation profile between two points.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return ElevationProfile or null if not available
     */
    suspend fun getElevationProfile(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): com.evnavigator.iran.domain.model.ElevationProfile?

    /**
     * Gets the estimated energy consumption between two points for EVs.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return Energy consumption in kWh or null if not available
     */
    suspend fun getEnergyConsumption(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Double?

    /**
     * Gets the current routing profile (speed, restrictions, etc.)
     *
     * @param coordinate A reference point for context
     * @return RoutingProfile or null if not available
     */
    suspend fun getRoutingProfile(coordinate: GeoCoordinate): RoutingProfile?

    /**
     * Checks if a route is valid and feasible with current offline data.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return True if the route can be calculated, false otherwise
     */
    suspend fun isValidRoute(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Boolean

    /**
     * Gets a list of all available routing profiles in the system.
     *
     * @return Flow of RoutingProfile objects
     */
    fun getAvailableRoutingProfiles(): Flow<List<RoutingProfile>>

    /**
     * Checks if offline routing is possible with current data availability.
     *
     * @return True if offline routing is feasible, false otherwise
     */
    suspend fun isOfflineRoutingAvailable(): Boolean

    /**
     * Gets the status of the local routing engine (available, processing, error).
     *
     * @return Status information
     */
    fun getEngineStatus(): RoutingEngineStatus

    /**
     * Forces a refresh of internal caches and data.
     */
    suspend fun refreshCache()
}