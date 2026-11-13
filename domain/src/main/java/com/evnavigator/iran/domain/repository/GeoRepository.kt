package com.evnavigator.iran.domain.repository

import com.evnavigator.iran.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for geographic data operations.
 */
interface GeoRepository {
    /**
     * Gets the current user location.
     */
    fun getCurrentLocation(): Flow<GeoCoordinate?>

    /**
     * Gets the user's heading.
     */
    fun getUserHeading(): Flow<Heading?>

    /**
     * Gets elevation data for a coordinate.
     */
    fun getElevation(coordinate: GeoCoordinate): Flow<Elevation?>

    /**
     * Gets nearby points of interest within a radius.
     */
    suspend fun getNearbyPointsOfInterest(
        center: GeoCoordinate,
        radius: Distance
    ): List<GeoPoint>

    /**
     * Calculates distance between two coordinates.
     */
    fun calculateDistance(from: GeoCoordinate, to: GeoCoordinate): Distance

    /**
     * Calculates bearing from one coordinate to another.
     */
    fun calculateBearing(from: GeoCoordinate, to: GeoCoordinate): Bearing

    /**
     * Calculates destination coordinate given a starting point, bearing, and distance.
     */
    fun calculateDestination(
        from: GeoCoordinate,
        bearing: Bearing,
        distance: Distance
    ): GeoCoordinate

    /**
     * Gets the current geographic bounds of the map view.
     */
    fun getMapViewBounds(): Flow<GeoBounds?>

    /**
     * Checks if a coordinate is within specified bounds.
     */
    fun isInBounds(coordinate: GeoCoordinate, bounds: GeoBounds): Boolean

    /**
     * Gets elevation profile along a path.
     */
    suspend fun getElevationProfile(path: GeoPolyline): List<ElevationPoint>
}