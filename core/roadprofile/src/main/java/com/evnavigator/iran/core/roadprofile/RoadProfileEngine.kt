package com.evnavigator.iran.core.roadprofile

import kotlinx.coroutines.flow.Flow
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Interface for road profile analysis engine
 * Provides functionality to analyze road characteristics along routes
 */
interface RoadProfileEngine {
    
    /**
     * Analyzes road characteristics along a route
     * @param routePoints List of geographic points representing the route
     * @return Flow of road profile data along the route
     */
    suspend fun analyzeRoadProfile(routePoints: List<GeoCoordinate>): Flow<RoadProfileData>
    
    /**
     * Gets detailed road information at specific point
     * @param point Geographic point
     * @return Road information at that point
     */
    suspend fun getRoadInfoAt(point: GeoCoordinate): RoadInfo?
    
    /**
     * Calculates road characteristics for a route segment
     * @param startCoordinate Start point of the segment
     * @param endCoordinate End point of the segment
     * @return Road characteristics for the segment
     */
    suspend fun calculateSegmentRoadCharacteristics(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): RoadSegmentCharacteristics
    
    /**
     * Gets road type information along a route
     * @param routePoints List of geographic points representing the route
     * @return Flow of road type information along the route
     */
    suspend fun getRoadTypesAlongRoute(routePoints: List<GeoCoordinate>): Flow<RoadTypeInfo>
    
    /**
     * Calculates average road grade along a route
     * @param routePoints List of geographic points representing the route
     * @return Average road grade percentage
     */
    suspend fun calculateAverageRoadGrade(routePoints: List<GeoCoordinate>): Double
    
    /**
     * Gets road surface information for a specific area
     * @param boundingBox Bounding box defining the area
     * @return Flow of road surface data within the area
     */
    suspend fun getRoadSurfaceDataInArea(boundingBox: BoundingBox): Flow<RoadSurfaceData>
}

/**
 * Represents road profile data along a route
 */
data class RoadProfileData(
    val coordinate: GeoCoordinate,
    val roadType: RoadType,
    val surfaceType: SurfaceType,
    val speedLimitKmh: Int,
    val gradientPercent: Double,
    val isTollRoad: Boolean,
    val distanceFromStart: Float = 0f
)

/**
 * Represents detailed road information at a specific point
 */
data class RoadInfo(
    val coordinate: GeoCoordinate,
    val roadName: String,
    val roadType: RoadType,
    val surfaceType: SurfaceType,
    val speedLimitKmh: Int,
    val isTollRoad: Boolean,
    val estimatedWidthMeters: Double
)

/**
 * Represents road segment characteristics
 */
data class RoadSegmentCharacteristics(
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val lengthMeters: Double,
    val averageGradientPercent: Double,
    val surfaceType: SurfaceType,
    val speedLimitKmh: Int,
    val isTollSection: Boolean
)

/**
 * Represents road type information along a route
 */
data class RoadTypeInfo(
    val coordinate: GeoCoordinate,
    val roadType: RoadType,
    val roadName: String,
    val distanceFromStart: Float = 0f
)

/**
 * Represents road surface data for an area
 */
data class RoadSurfaceData(
    val coordinate: GeoCoordinate,
    val surfaceType: SurfaceType,
    val qualityIndex: Double // 0.0 to 1.0 scale
)

/**
 * Represents a bounding box for geographic area
 */
data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

/**
 * Enum representing different types of roads
 */
enum class RoadType {
    HIGHWAY,      // Highway (e.g., Interstate)
    MOTORWAY,     // Motorway (e.g., Freeway)
    STREET,       // Regular street
    RURAL,        // Rural/remote road
    PEDESTRIAN    // Pedestrian walkway
}

/**
 * Enum representing different types of road surfaces
 */
enum class SurfaceType {
    ASPHALT,      // Asphalt surface
    CONCRETE,     // Concrete surface
    GRAVEL,       // Gravel surface
    DIRT,         // Dirt surface
    PAVED,        // Paved surface
    UNPAVED       // Unpaved surface
}