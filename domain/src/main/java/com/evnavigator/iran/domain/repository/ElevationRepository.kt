package com.evnavigator.iran.domain.repository

import com.evnavigator.iran.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for elevation data operations.
 */
interface ElevationRepository {
    /**
     * Gets elevation data for a single coordinate.
     */
    fun getElevation(coordinate: GeoCoordinate): Flow<Elevation?>

    /**
     * Gets elevation data for multiple coordinates.
     */
    suspend fun getElevations(coordinates: List<GeoCoordinate>): List<Elevation>

    /**
     * Gets elevation profile along a path.
     */
    suspend fun getElevationProfile(path: GeoPolyline): List<ElevationPoint>

    /**
     * Gets elevation data for a bounding box.
     */
    suspend fun getElevationInBounds(bounds: GeoBounds): List<ElevationPoint>

    /**
     * Calculates the average elevation in a region.
     */
    suspend fun calculateAverageElevation(bounds: GeoBounds): Altitude?

    /**
     * Calculates the maximum elevation in a region.
     */
    suspend fun calculateMaxElevation(bounds: GeoBounds): Altitude?

    /**
     * Calculates the minimum elevation in a region.
     */
    suspend fun calculateMinElevation(bounds: GeoBounds): Altitude?
}