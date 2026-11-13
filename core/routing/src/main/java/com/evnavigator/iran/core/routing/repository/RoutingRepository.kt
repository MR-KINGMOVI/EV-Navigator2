package com.evnavigator.iran.core.routing.repository

import com.evnavigator.iran.core.routing.model.*
import com.evnavigator.iran.core.routing.model.Route
import com.evnavigator.iran.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for routing data
 */
interface RoutingRepository {
    
    /**
     * Find the nearest node to a given coordinate
     */
    suspend fun findNearestNode(coordinate: GeoCoordinate): Result<RoadNode>
    
    /**
     * Get the complete road graph
     */
    suspend fun getRoadGraph(): Result<RoadGraph>
    
    /**
     * Get road segments within a bounding box
     */
    suspend fun getRoadSegmentsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Result<List<RoadSegment>>
    
    /**
     * Get elevation data for a route
     */
    suspend fun getElevationDataForRoute(route: Route): Result<List<ElevationPoint>>
    
    /**
     * Get weather data for a coordinate
     */
    suspend fun getWeatherAtCoordinate(coordinate: GeoCoordinate): Result<WeatherSnapshot>
    
    /**
     * Get charging stations near a point
     */
    suspend fun getChargingStationsNear(
        coordinate: GeoCoordinate,
        maxDistanceMeters: Double = 5000.0
    ): Result<List<Charger>>
}