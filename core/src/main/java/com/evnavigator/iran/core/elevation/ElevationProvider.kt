package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a result from an elevation provider operation
 */
sealed class ElevationProviderResult {
    /**
     * Success result containing elevation data
     */
    data class Success(val points: List<ElevationPoint>) : ElevationProviderResult()
    
    /**
     * Error result with error information
     */
    data class Error(val message: String, val cause: Throwable? = null) : ElevationProviderResult()
    
    /**
     * Partial success result with some valid and some invalid data
     */
    data class PartialSuccess(
        val validPoints: List<ElevationPoint>,
        val invalidPoints: List<GeoCoordinate>
    ) : ElevationProviderResult()
}

/**
 * Interface for elevation data providers
 * This abstraction allows different elevation sources (online services, local caches, DEM files, etc.)
 */
interface ElevationProvider {
    
    /**
     * Gets elevation at a single geographic coordinate
     * @param coordinate The geographic coordinate to query
     * @return ElevationProviderResult with elevation data or error information
     */
    suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult
    
    /**
     * Gets elevation data for multiple coordinates in batch
     * @param coordinates List of geographic coordinates to query
     * @return ElevationProviderResult with elevation data or error information
     */
    suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult
    
    /**
     * Gets elevation profile along a route
     * @param coordinates List of geographic coordinates representing the route
     * @return ElevationProviderResult with elevation data or error information
     */
    suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult
    
    /**
     * Checks if this provider can handle the given coordinate
     * @param coordinate The coordinate to check
     * @return true if this provider can provide data for the coordinate, false otherwise
     */
    fun canHandle(coordinate: GeoCoordinate): Boolean {
        // Default implementation - providers should override if they have specific logic
        return true
    }
    
    /**
     * Gets the name of this elevation provider
     * @return Provider name
     */
    fun getName(): String {
        return this::class.simpleName ?: "UnknownProvider"
    }
}

/**
 * Interface for a batched elevation provider that supports configurable batch sizes
 */
interface BatchedElevationProvider : ElevationProvider {
    
    /**
     * Gets elevation data for multiple coordinates in batches
     * @param coordinates List of geographic coordinates to query
     * @param batchSize Maximum number of coordinates per batch
     * @return ElevationProviderResult with elevation data or error information
     */
    suspend fun getElevationBatch(
        coordinates: List<GeoCoordinate>, 
        batchSize: Int = 100
    ): ElevationProviderResult
    
    /**
     * Gets the maximum batch size supported by this provider
     * @return Maximum batch size, or null if unlimited
     */
    fun getMaxBatchSize(): Int? {
        return null // Unlimited by default
    }
}
