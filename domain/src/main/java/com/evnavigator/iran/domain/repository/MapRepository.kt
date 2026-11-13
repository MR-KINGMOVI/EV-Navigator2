package com.evnavigator.iran.domain.repository

import com.evnavigator.iran.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for map-related operations.
 */
interface MapRepository {
    /**
     * Gets the current map view bounds.
     */
    fun getMapViewBounds(): Flow<GeoBounds?>

    /**
     * Gets map tiles for a given bounds and zoom level.
     */
    suspend fun getMapTiles(bounds: GeoBounds, zoomLevel: Int): List<MapTile>

    /**
     * Gets map features within bounds.
     */
    suspend fun getMapFeatures(bounds: GeoBounds): List<MapFeature>

    /**
     * Gets the current map center.
     */
    fun getCurrentMapCenter(): Flow<GeoCoordinate?>

    /**
     * Sets the map center to a specific coordinate.
     */
    suspend fun setMapCenter(coordinate: GeoCoordinate)

    /**
     * Gets map zoom level.
     */
    fun getMapZoomLevel(): Flow<Int?>

    /**
     * Sets the map zoom level.
     */
    suspend fun setMapZoomLevel(zoomLevel: Int)

    /**
     * Checks if a coordinate is visible on the current map view.
     */
    fun isCoordinateVisible(coordinate: GeoCoordinate): Boolean

    /**
     * Converts screen coordinates to geographic coordinates.
     */
    fun screenToGeo(screenX: Float, screenY: Float): GeoCoordinate?

    /**
     * Converts geographic coordinates to screen coordinates.
     */
    fun geoToScreen(coordinate: GeoCoordinate): Pair<Float, Float>?

    /**
     * Gets the current map style.
     */
    fun getMapStyle(): Flow<MapStyle?>

    /**
     * Sets the map style.
     */
    suspend fun setMapStyle(style: MapStyle)
}