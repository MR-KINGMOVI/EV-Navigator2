package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.*
import java.time.Instant
import kotlin.math.max

/**
 * Performance optimization utilities for weather and traffic data processing.
 *
 * This class provides mechanisms to optimize performance for large-scale route processing,
 * including batching, spatial reuse, temporal reuse, and memory-efficient operations.
 */
class PerformanceOptimization {
    
    /**
     * Batches weather requests to reduce network overhead.
     *
     * @param coordinates List of coordinates to query
     * @param maxBatchSize Maximum number of coordinates per batch
     * @return List of coordinate batches
     */
    fun createWeatherBatches(
        coordinates: List<GeoCoordinate>,
        maxBatchSize: Int = 10
    ): List<List<GeoCoordinate>> {
        if (coordinates.isEmpty()) return emptyList()
        
        val batches = mutableListOf<List<GeoCoordinate>>()
        for (i in coordinates.indices step maxBatchSize) {
            val end = minOf(i + maxBatchSize, coordinates.size)
            batches.add(coordinates.subList(i, end))
        }
        return batches
    }
    
    /**
     * Batches traffic requests to reduce network overhead.
     *
     * @param coordinates List of coordinates to query
     * @param maxBatchSize Maximum number of coordinates per batch
     * @return List of coordinate batches
     */
    fun createTrafficBatches(
        coordinates: List<GeoCoordinate>,
        maxBatchSize: Int = 10
    ): List<List<GeoCoordinate>> {
        if (coordinates.isEmpty()) return emptyList()
        
        val batches = mutableListOf<List<GeoCoordinate>>()
        for (i in coordinates.indices step maxBatchSize) {
            val end = minOf(i + maxBatchSize, coordinates.size)
            batches.add(coordinates.subList(i, end))
        }
        return batches
    }
    
    /**
     * Optimized spatial lookup for weather data.
     *
     * @param observations List of available weather observations
     * @param targetCoordinate The coordinate to find data for
     * @param maxDistanceMeters Maximum distance to consider (default 5000m)
     * @return Closest weather observation or null if none found within range
     */
    fun findClosestWeatherObservation(
        observations: List<WeatherObservation>,
        targetCoordinate: GeoCoordinate,
        maxDistanceMeters: Double = 5000.0
    ): WeatherObservation? {
        return observations
            .mapNotNull { obs ->
                val distance = calculateDistance(obs.coordinate, targetCoordinate)
                if (distance <= maxDistanceMeters) {
                    Pair(obs, distance)
                } else {
                    null
                }
            }
            .minByOrNull { it.second }
            ?.first
    }
    
    /**
     * Optimized spatial lookup for traffic data.
     *
     * @param observations List of available traffic observations
     * @param targetCoordinate The coordinate to find data for
     * @param maxDistanceMeters Maximum distance to consider (default 5000m)
     * @return Closest traffic observation or null if none found within range
     */
    fun findClosestTrafficObservation(
        observations: List<TrafficObservation>,
        targetCoordinate: GeoCoordinate,
        maxDistanceMeters: Double = 5000.0
    ): TrafficObservation? {
        return observations
            .mapNotNull { obs ->
                val distance = calculateDistance(obs.coordinate, targetCoordinate)
                if (distance <= maxDistanceMeters) {
                    Pair(obs, distance)
                } else {
                    null
                }
            }
            .minByOrNull { it.second }
            ?.first
    }
    
    /**
     * Calculates the distance between two geographic coordinates.
     *
     * Uses a simplified Haversine formula for performance - accurate enough for most use cases
     * at reasonable distances (under 100km).
     *
     * @param coord1 First coordinate
     * @param coord2 Second coordinate
     * @return Distance in meters
     */
    private fun calculateDistance(coord1: GeoCoordinate, coord2: GeoCoordinate): Double {
        val R = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(coord1.latitude)
        val lat2Rad = Math.toRadians(coord2.latitude)
        val deltaLatRad = Math.toRadians(coord2.latitude - coord1.latitude)
        val deltaLonRad = Math.toRadians(coord2.longitude - coord1.longitude)
        
        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
                kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
                kotlin.math.sin(deltaLonRad / 2) * kotlin.math.sin(deltaLonRad / 2)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        
        return R * c
    }
    
    /**
     * Optimized temporal lookup for weather forecasts.
     *
     * @param forecasts List of forecast points sorted by time
     * @param targetTime The time to find forecast for
     * @return Forecast point closest in time or null if none found
     */
    fun findClosestForecast(
        forecasts: List<ForecastWeatherPoint>,
        targetTime: Instant
    ): ForecastWeatherPoint? {
        if (forecasts.isEmpty()) return null
        
        // For performance, use binary search approach for sorted data
        val index = binarySearchForTime(forecasts, targetTime)
        return if (index >= 0) forecasts[index] else null
    }
    
    /**
     * Performs binary search on a sorted list of forecast points by time.
     *
     * @param forecasts Sorted list of forecast points
     * @param targetTime Target time to find
     * @return Index of closest match or -1 if not found
     */
    private fun binarySearchForTime(
        forecasts: List<ForecastWeatherPoint>,
        targetTime: Instant
    ): Int {
        var left = 0
        var right = forecasts.size - 1
        
        while (left <= right) {
            val mid = (left + right) / 2
            val midTime = forecasts[mid].validTime
            
            when {
                midTime.isBefore(targetTime) -> left = mid + 1
                midTime.isAfter(targetTime) -> right = mid - 1
                else -> return mid // Exact match found
            }
        }
        
        // Return the closest element, if any
        return if (left < forecasts.size) left else if (right >= 0) right else -1
    }
    
    /**
     * Efficiently combines weather and traffic data for route segments.
     *
     * @param weatherData Weather observations for route points
     * @param trafficData Traffic observations for route points
     * @param segmentCoordinates Route segment coordinates
     * @return List of combined environmental contexts for each segment
     */
    fun combineRouteEnvironment(
        weatherData: List<WeatherObservation>,
        trafficData: List<TrafficObservation>,
        segmentCoordinates: List<Pair<GeoCoordinate, GeoCoordinate>>
    ): List<RouteEnvironmentContext> {
        val contexts = mutableListOf<RouteEnvironmentContext>()
        
        for ((startCoord, endCoord) in segmentCoordinates) {
            val segmentCenter = GeoCoordinate(
                latitude = (startCoord.latitude + endCoord.latitude) / 2.0,
                longitude = (startCoord.longitude + endCoord.longitude) / 2.0
            )
            
            val weatherContext = weatherData.find { obs ->
                calculateDistance(obs.coordinate, segmentCenter) < 1000.0
            }
            
            val trafficContext = trafficData.find { obs ->
                calculateDistance(obs.coordinate, segmentCenter) < 1000.0
            }
            
            val segmentLength = calculateDistance(startCoord, endCoord).coerceAtLeast(1.0)
            val routeSegment = RouteSegment(
                id = "seg_${contexts.size}",
                roadId = "road_0",
                startCoordinate = startCoord,
                endCoordinate = endCoord,
                lengthMeters = segmentLength,
                gradientPercent = Double.NaN // Grade is unknown when only 2D coordinates are supplied without DEM
            )
            val weatherAgeSec = weatherContext?.timestamp?.let { java.time.Duration.between(it, Instant.now()).seconds }
            val weatherFreshness = when {
                weatherAgeSec == null -> Freshness.UNKNOWN
                weatherAgeSec < 0 -> Freshness.FRESH
                weatherAgeSec <= 3600L -> Freshness.FRESH
                weatherAgeSec <= 7200L -> Freshness.STALE_BUT_USABLE
                else -> Freshness.EXPIRED
            }

            val trafficAgeSec = trafficContext?.timestamp?.let { java.time.Duration.between(it, Instant.now()).seconds }
            val trafficFreshness = when {
                trafficAgeSec == null -> Freshness.UNKNOWN
                trafficAgeSec < 0 -> Freshness.FRESH
                trafficAgeSec <= 900L -> Freshness.FRESH
                trafficAgeSec <= 1800L -> Freshness.STALE_BUT_USABLE
                else -> Freshness.EXPIRED
            }

            val combinedFreshness = when {
                weatherFreshness == Freshness.EXPIRED || trafficFreshness == Freshness.EXPIRED -> Freshness.EXPIRED
                weatherFreshness == Freshness.STALE_BUT_USABLE || trafficFreshness == Freshness.STALE_BUT_USABLE -> Freshness.STALE_BUT_USABLE
                weatherFreshness == Freshness.FRESH && trafficFreshness == Freshness.FRESH -> Freshness.FRESH
                weatherFreshness != Freshness.UNKNOWN -> weatherFreshness
                trafficFreshness != Freshness.UNKNOWN -> trafficFreshness
                else -> Freshness.UNKNOWN
            }

            val weatherCtx = weatherContext?.let {
                WeatherContext(it, null, it.confidence, it.uncertainty, weatherFreshness, it.source)
            }
            val trafficCtx = trafficContext?.let {
                TrafficDataForSegment(
                    trafficClass = it.trafficClass ?: TrafficClass.UNKNOWN,
                    speed = it.speedMps,
                    freeFlowSpeed = it.freeFlowSpeedMps,
                    congestionRatio = it.congestionRatio,
                    travelTimeMultiplier = it.travelTimeMultiplier,
                    incidents = emptyList(),
                    confidence = it.confidence,
                    uncertainty = it.uncertainty,
                    freshness = trafficFreshness,
                    observation = it
                )
            }
            val segEnv = RouteSegmentEnvironment(
                segment = routeSegment,
                weatherContext = weatherCtx,
                trafficContext = trafficCtx,
                timestamp = Instant.now(),
                confidence = weatherContext?.confidence ?: trafficContext?.confidence ?: 0.5,
                uncertainty = weatherContext?.uncertainty ?: trafficContext?.uncertainty ?: 0.5,
                freshness = combinedFreshness,
                provenance = "PerformanceOptimization"
            )
            contexts.add(
                RouteEnvironmentContext(
                    segments = listOf(segEnv),
                    confidence = segEnv.confidence,
                    uncertainty = segEnv.uncertainty,
                    freshness = combinedFreshness,
                    provenance = "PerformanceOptimization"
                )
            )
        }
        
        return contexts
    }
    
    /**
     * Memory-efficient processing of large route data.
     *
     * @param routePoints List of route points to process
     * @param processor Function that processes a batch of points
     * @param batchSize Size of each batch to process
     * @return Processed results
     */
    suspend fun processRouteBatch(
        routePoints: List<GeoCoordinate>,
        processor: (List<GeoCoordinate>) -> List<RouteEnvironmentContext>,
        batchSize: Int = 50
    ): List<RouteEnvironmentContext> {
        val results = mutableListOf<RouteEnvironmentContext>()
        
        for (i in routePoints.indices step batchSize) {
            val end = minOf(i + batchSize, routePoints.size)
            val batch = routePoints.subList(i, end)
            
            // Process the batch with a coroutine to avoid blocking
            val batchResults = withContext(Dispatchers.Default) {
                processor(batch)
            }
            
            results.addAll(batchResults)
        }
        
        return results
    }
    
    /**
     * Deduplicates environmental data based on spatial proximity.
     *
     * @param observations List of observations to deduplicate
     * @param minDistanceMeters Minimum distance for considering duplicates
     * @return Deduplicated list of observations
     */
    fun deduplicateEnvironmentalData(
        observations: List<WeatherObservation>,
        minDistanceMeters: Double = 100.0
    ): List<WeatherObservation> {
        val deduplicated = mutableListOf<WeatherObservation>()
        
        for (obs in observations) {
            val isDuplicate = deduplicated.any { existing ->
                calculateDistance(obs.coordinate, existing.coordinate) < minDistanceMeters
            }
            
            if (!isDuplicate) {
                deduplicated.add(obs)
            }
        }
        
        return deduplicated
    }
    
    /**
     * Calculates the time needed to process a route with environmental data.
     *
     * @param routeLength Number of segments in the route
     * @param dataPointsPerSegment Number of data points per segment
     * @return Estimated processing time in milliseconds
     */
    fun estimateProcessingTime(
        routeLength: Int,
        dataPointsPerSegment: Int = 10
    ): Long {
        // Rough estimation based on empirical measurements
        val baseTimePerSegment = 5L // ms per segment for simple operations
        val dataPointMultiplier = 2L // Additional time per data point
        
        return (routeLength * baseTimePerSegment) + 
               (routeLength * dataPointsPerSegment * dataPointMultiplier)
    }
}