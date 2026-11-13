package com.evnavigator.iran.core.elevation

import kotlinx.coroutines.flow.Flow
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint

/**
 * Interface for elevation data processing and calculation
 */
interface ElevationEngine {
    
    /**
     * Calculates elevation profile along a route with adaptive sampling
     * @param routePoints List of geographic points representing the route
     * @return Flow of elevation points with their coordinates and elevation values
     */
    suspend fun calculateElevationProfile(routePoints: List<GeoCoordinate>): Flow<ElevationPoint>
    
    /**
     * Gets elevation at specific point
     * @param point Geographic point
     * @return Elevation value at the point
     */
    suspend fun getElevationAt(point: GeoCoordinate): Double
    
    /**
     * Calculates total elevation gain/loss along a route
     * @param routePoints List of geographic points representing the route
     * @return Total elevation gain and loss values
     */
    suspend fun calculateElevationGainLoss(routePoints: List<GeoCoordinate>): ElevationGainLoss
    
    /**
     * Gets elevation data for a specific area
     * @param boundingBox Bounding box defining the area
     * @return Flow of elevation data within the area
     */
    suspend fun getElevationDataInArea(boundingBox: BoundingBox): Flow<ElevationData>
    
    /**
     * Calculates grade (slope) along a route segment
     * @param startCoordinate Start point of the segment
     * @param endCoordinate End point of the segment
     * @return Grade percentage for the segment
     */
    suspend fun calculateGrade(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): Double
    
    /**
     * Gets detailed elevation characteristics for a route
     * @param routePoints List of geographic points representing the route
     * @return Route elevation characteristics
     */
    suspend fun getRouteElevationCharacteristics(routePoints: List<GeoCoordinate>): RouteElevationCharacteristics
    
    /**
     * Calculates climb/descent sections along a route
     * @param routePoints List of geographic points representing the route
     * @return List of climb/descent sections
     */
    suspend fun identifyClimbDescentSections(routePoints: List<GeoCoordinate>): List<ClimbDescentSection>
    
    /**
     * Calculates average elevation along a route
     * @param routePoints List of geographic points representing the route
     * @return Average elevation value
     */
    suspend fun calculateAverageElevation(routePoints: List<GeoCoordinate>): Double
    
    /**
     * Gets minimum and maximum elevation values along a route
     * @param routePoints List of geographic points representing the route
     * @return Min/max elevation values
     */
    suspend fun getMinMaxElevation(routePoints: List<GeoCoordinate>): MinMaxElevation
    
    /**
     * Calculates distance between elevation points
     * @param startCoordinate Start point
     * @param endCoordinate End point
     * @return Distance in meters
     */
    suspend fun calculateDistanceBetweenPoints(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): Double
}