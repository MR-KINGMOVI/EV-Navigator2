package com.evnavigator.iran.domain.repository

import com.evnavigator.iran.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for route-related operations.
 */
interface RouteRepository {
    /**
     * Calculates a route between two points.
     */
    suspend fun calculateRoute(
        start: GeoCoordinate,
        end: GeoCoordinate,
        preferences: RoutePreferences = RoutePreferences()
    ): Route?

    /**
     * Gets the current route.
     */
    fun getCurrentRoute(): Flow<Route?>

    /**
     * Gets the route segments for a given route.
     */
    suspend fun getRouteSegments(route: Route): List<RouteSegment>

    /**
     * Calculates the total distance of a route.
     */
    fun calculateTotalDistance(route: Route): Distance

    /**
     * Calculates the estimated travel time for a route.
     */
    fun calculateEstimatedTime(route: Route): Long // in seconds

    /**
     * Gets route summary information.
     */
    suspend fun getRouteSummary(route: Route): RouteSummary

    /**
     * Checks if a coordinate is on the route.
     */
    suspend fun isCoordinateOnRoute(coordinate: GeoCoordinate, route: Route): Boolean

    /**
     * Gets the nearest point on the route to a given coordinate.
     */
    suspend fun getNearestPointOnRoute(coordinate: GeoCoordinate, route: Route): GeoCoordinate?

    /**
     * Updates the current route with new segments.
     */
    suspend fun updateRoute(route: Route)

    /**
     * Clears the current route.
     */
    suspend fun clearCurrentRoute()
}