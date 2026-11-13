package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment
import java.time.Duration
import kotlin.math.max

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Implements spatial mapping of weather data to route segments.
 *
 * This class handles the mapping of weather observations and forecasts to specific
 * route segments, ensuring proper geographic alignment without global averaging.
 */
class WeatherRouteSpatialMapping {
    
    /**
     * Represents a spatial mapping result for a route segment.
     */
    data class SpatialMappingResult(
        val segmentIndex: Int,
        val startCoordinate: GeoCoordinate,
        val endCoordinate: GeoCoordinate,
        val weatherContext: WeatherContext?,
        val confidence: Double,
        val uncertainty: Double,
        val source: String
    )
    
    /**
     * Maps weather observations to route segments.
     *
     * @param routeCoordinates The coordinates representing the route
     * @param weatherObservations The weather observations to map
     * @param startTime The start time of the route
     * @param endTime The end time of the route
     * @return List of spatial mapping results for each segment
     */
    fun mapWeatherToRouteSegments(
        routeCoordinates: List<GeoCoordinate>,
        weatherObservations: List<WeatherObservation>,
        startTime: Instant,
        endTime: Instant
    ): List<SpatialMappingResult> {
        if (routeCoordinates.isEmpty() || weatherObservations.isEmpty()) {
            return emptyList()
        }
        
        val segments = createRouteSegments(routeCoordinates)
        val result = mutableListOf<SpatialMappingResult>()
        
        // Create a map for efficient lookup by coordinate
        val weatherByCoordinate = weatherObservations.groupBy { it.coordinate }
        
        for ((index, segment) in segments.withIndex()) {
            val mappingResult = mapSegmentToWeather(
                segment,
                weatherByCoordinate,
                startTime,
                endTime
            )
            result.add(mappingResult)
        }
        
        return result
    }
    
    /**
     * Maps a single route segment to the most relevant weather data.
     */
    private fun mapSegmentToWeather(
        segment: RouteSegment,
        weatherByCoordinate: Map<GeoCoordinate, List<WeatherObservation>>,
        startTime: Instant,
        endTime: Instant
    ): SpatialMappingResult {
        // Find the most relevant weather observation for this segment
        val relevantWeather = findMostRelevantWeather(segment, weatherByCoordinate)
        
        val (confidence, uncertainty) = if (relevantWeather != null) {
            // Calculate confidence based on spatial proximity and temporal alignment
            val spatialConfidence = calculateSpatialConfidence(segment, relevantWeather.observation?.coordinate ?: segment.startCoordinate)
            val temporalConfidence = calculateTemporalConfidence(
                relevantWeather.observation?.timestamp ?: startTime,
                startTime,
                endTime
            )
            
            // Combine confidences
            val combinedConfidence = (spatialConfidence + temporalConfidence) / 2.0
            
            // Calculate uncertainty (inverse of confidence)
            val combinedUncertainty = 1.0 - combinedConfidence
            
            Pair(combinedConfidence.coerceIn(0.0, 1.0), combinedUncertainty.coerceIn(0.0, 1.0))
        } else {
            // No relevant weather data - return default values
            Pair(0.0, 1.0)
        }
        
        return SpatialMappingResult(
            segmentIndex = 0,
            startCoordinate = segment.startCoordinate,
            endCoordinate = segment.endCoordinate,
            weatherContext = relevantWeather,
            confidence = confidence,
            uncertainty = uncertainty,
            source = relevantWeather?.observation?.source ?: "none"
        )
    }
    
    /**
     * Finds the most relevant weather observation for a route segment.
     */
    private fun findMostRelevantWeather(
        segment: RouteSegment,
        weatherByCoordinate: Map<GeoCoordinate, List<WeatherObservation>>
    ): WeatherContext? {
        // Find the weather observation that is closest to the segment midpoint
        val segmentCenter = calculateSegmentCenter(segment)
        
        var bestMatch: WeatherObservation? = null
        var minDistance = Double.MAX_VALUE
        
        for ((coordinate, observations) in weatherByCoordinate) {
            val distance = calculateDistance(segmentCenter, coordinate)
            if (distance < minDistance) {
                minDistance = distance
                // Return the most recent observation if multiple exist at same location
                bestMatch = observations.maxByOrNull { it.timestamp }
            }
        }
        
        return bestMatch?.let { 
            WeatherContext(
                observation = it,
                forecast = null,
                confidence = 0.8,
                uncertainty = 0.2,
                freshness = Freshness.FRESH,
                provenance = it.source
            )
        }
    }
    
    /**
     * Calculates spatial confidence based on distance from segment center.
     */
    private fun calculateSpatialConfidence(segment: RouteSegment, weatherCoordinate: GeoCoordinate): Double {
        val segmentCenter = calculateSegmentCenter(segment)
        val distance = calculateDistance(segmentCenter, weatherCoordinate)
        
        // Confidence decreases with distance (assuming 10km radius as maximum acceptable)
        return when {
            distance <= 1000.0 -> 1.0 // Very close
            distance <= 5000.0 -> max(0.7, 1.0 - (distance - 1000.0) / 4000.0) // Moderate distance
            distance <= 10000.0 -> max(0.3, 1.0 - (distance - 5000.0) / 5000.0) // Far but acceptable
            else -> 0.0 // Too far
        }
    }
    
    /**
     * Calculates temporal confidence based on time alignment.
     */
    private fun calculateTemporalConfidence(
        weatherTime: Instant,
        routeStartTime: Instant,
        routeEndTime: Instant
    ): Double {
        val routeDuration = Duration.between(routeStartTime, routeEndTime).seconds
        
        // If the weather is for a time that falls within the route duration, good confidence
        // Otherwise, lower confidence (but still usable if it's close to the route period)
        val timeDiff = Math.abs(Duration.between(weatherTime, routeStartTime).seconds.toDouble())
        
        return when {
            timeDiff <= 3600 -> 1.0 // Within 1 hour
            timeDiff <= 7200 -> max(0.7, 1.0 - (timeDiff - 3600) / 3600.0) // Within 2 hours
            timeDiff <= 14400 -> max(0.3, 1.0 - (timeDiff - 7200) / 7200.0) // Within 4 hours
            else -> 0.0 // Too far from route period
        }
    }
    
    /**
     * Maps weather forecasts to route segments.
     *
     * @param routeCoordinates The coordinates representing the route
     * @param forecasts The forecast points to map
     * @param startTime The start time of the route
     * @param endTime The end time of the route
     * @return List of spatial mapping results for each segment
     */
    fun mapWeatherForecastsToRouteSegments(
        routeCoordinates: List<GeoCoordinate>,
        forecasts: List<ForecastWeatherPoint>,
        startTime: Instant,
        endTime: Instant
    ): List<SpatialMappingResult> {
        if (routeCoordinates.isEmpty() || forecasts.isEmpty()) {
            return emptyList()
        }
        
        val segments = createRouteSegments(routeCoordinates)
        val result = mutableListOf<SpatialMappingResult>()
        
        // Create a map for efficient lookup by coordinate
        val forecastsByCoordinate = forecasts.groupBy { it.coordinate }
        
        for ((index, segment) in segments.withIndex()) {
            val mappingResult = mapSegmentToWeatherForecast(
                segment,
                forecastsByCoordinate,
                startTime,
                endTime
            )
            result.add(mappingResult)
        }
        
        return result
    }
    
    /**
     * Maps a single route segment to the most relevant weather forecast.
     */
    private fun mapSegmentToWeatherForecast(
        segment: RouteSegment,
        forecastsByCoordinate: Map<GeoCoordinate, List<ForecastWeatherPoint>>,
        startTime: Instant,
        endTime: Instant
    ): SpatialMappingResult {
        // Find the most relevant forecast for this segment
        val relevantForecast = findMostRelevantForecast(segment, forecastsByCoordinate)
        
        val (confidence, uncertainty) = if (relevantForecast != null) {
            // Calculate confidence based on spatial proximity and temporal alignment
            val spatialConfidence = calculateSpatialConfidence(segment, relevantForecast.coordinate)
            val temporalConfidence = calculateTemporalConfidence(
                relevantForecast.validTime,
                startTime,
                endTime
            )
            
            // Combine confidences
            val combinedConfidence = (spatialConfidence + temporalConfidence) / 2.0
            
            // Calculate uncertainty (inverse of confidence)
            val combinedUncertainty = 1.0 - combinedConfidence
            
            Pair(combinedConfidence.coerceIn(0.0, 1.0), combinedUncertainty.coerceIn(0.0, 1.0))
        } else {
            // No relevant forecast data - return default values
            Pair(0.0, 1.0)
        }
        
        // Create a WeatherContext from the forecast for consistency
        val weatherContext = relevantForecast?.let { forecast ->
            // Convert forecast to WeatherObservation for compatibility with existing context
            val observation = WeatherObservation(
                coordinate = forecast.coordinate,
                timestamp = forecast.validTime, // Use validFrom as timestamp for consistency
                temperatureCelsius = forecast.temperatureCelsius,
                apparentTemperatureCelsius = forecast.apparentTemperatureCelsius,
                windSpeedMps = forecast.windSpeedMps,
                windDirectionDegrees = forecast.windDirectionDegrees,
                precipitationMm = forecast.precipitationMm,
                precipitationProbability = forecast.precipitationProbability,
                humidityPercentage = forecast.humidityPercentage,
                pressureHpa = forecast.pressureHpa,
                visibilityMeters = forecast.visibilityMeters,
                cloudCoverPercentage = forecast.cloudCoverPercentage,
                condition = forecast.condition,
                source = forecast.source,
                confidence = 0.7,
                uncertainty = 0.3,
                retrievedAt = Instant.now(),
                freshness = WeatherFreshness.REASONABLY_FRESH
            )
            
            WeatherContext(
                observation = observation,
                forecast = null,
                confidence = 0.7,
                uncertainty = 0.3,
                freshness = Freshness.FRESH,
                provenance = observation.source
            )
        }
        
        return SpatialMappingResult(
            segmentIndex = 0,
            startCoordinate = segment.startCoordinate,
            endCoordinate = segment.endCoordinate,
            weatherContext = weatherContext,
            confidence = confidence,
            uncertainty = uncertainty,
            source = relevantForecast?.source ?: "none"
        )
    }
    
    /**
     * Finds the most relevant forecast for a route segment.
     */
    private fun findMostRelevantForecast(
        segment: RouteSegment,
        forecastsByCoordinate: Map<GeoCoordinate, List<ForecastWeatherPoint>>
    ): ForecastWeatherPoint? {
        // Find the forecast that is closest to the segment midpoint
        val segmentCenter = calculateSegmentCenter(segment)
        
        var bestMatch: ForecastWeatherPoint? = null
        var minDistance = Double.MAX_VALUE
        
        for ((coordinate, points) in forecastsByCoordinate) {
            val distance = calculateDistance(segmentCenter, coordinate)
            if (distance < minDistance) {
                minDistance = distance
                // Return the most recent forecast if multiple exist at same location
                bestMatch = points.maxByOrNull { it.validTime }
            }
        }
        
        return bestMatch
    }
    
    /**
     * Creates route segments from coordinates.
     */
    private fun createRouteSegments(coordinates: List<GeoCoordinate>): List<RouteSegment> {
        val segments = mutableListOf<RouteSegment>()
        if (coordinates.size < 2) return segments
        for (i in 0 until coordinates.size - 1) {
            val dist = calculateDistance(coordinates[i], coordinates[i + 1]).coerceAtLeast(1.0)
            segments.add(RouteSegment(
                id = "seg_$i",
                roadId = "road_0",
                startCoordinate = coordinates[i],
                endCoordinate = coordinates[i + 1],
                lengthMeters = dist,
                gradientPercent = 0.0
            ))
        }
        return segments
    }
    
    /**
     * Calculates the center point of a route segment.
     */
    private fun calculateSegmentCenter(segment: RouteSegment): GeoCoordinate {
        return GeoCoordinate(
            latitude = (segment.startCoordinate.latitude + segment.endCoordinate.latitude) / 2,
            longitude = (segment.startCoordinate.longitude + segment.endCoordinate.longitude) / 2
        )
    }
    
    /**
     * Calculates the distance between two coordinates.
     */
    private fun calculateDistance(start: GeoCoordinate, end: GeoCoordinate): Double {
        // Simplified distance calculation - in production this would use Haversine formula
        val latDiff = Math.toRadians(end.latitude - start.latitude)
        val lonDiff = Math.toRadians(end.longitude - start.longitude)
        val a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(Math.toRadians(start.latitude)) * Math.cos(Math.toRadians(end.latitude)) *
                Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371000.0 * c // Earth radius in meters
    }
    
    /**
     * Batch processes weather mapping for better performance with long routes.
     *
     * @param routeCoordinates The coordinates representing the route
     * @param weatherObservations The weather observations to map
     * @param batchSize The number of segments to process at once
     * @param journeyStartTime The departure time of the journey for ETA progression
     * @param averageSpeedMps The average vehicle speed in m/s used to calculate segment travel time
     * @return List of spatial mapping results for each segment
     */
    fun batchMapWeatherToRouteSegments(
        routeCoordinates: List<GeoCoordinate>,
        weatherObservations: List<WeatherObservation>,
        batchSize: Int = 100,
        journeyStartTime: Instant = Instant.now(),
        averageSpeedMps: Double = 25.0
    ): List<SpatialMappingResult> {
        if (routeCoordinates.isEmpty() || weatherObservations.isEmpty()) {
            return emptyList()
        }
        
        val segments = createRouteSegments(routeCoordinates)
        val result = mutableListOf<SpatialMappingResult>()
        val weatherByCoordinate = weatherObservations.groupBy { it.coordinate }
        val safeSpeed = if (averageSpeedMps > 0.0 && averageSpeedMps.isFinite()) averageSpeedMps else 25.0
        
        var cumulativeDistanceMeters = 0.0
        
        // Process in batches to avoid memory issues with long routes
        for (i in 0 until segments.size step batchSize) {
            val batchEnd = min(i + batchSize, segments.size)
            val batchSegments = segments.subList(i, batchEnd)
            
            for (segment in batchSegments) {
                val segLength = segment.lengthMeters
                val startSec = (cumulativeDistanceMeters / safeSpeed).toLong()
                val endSec = ((cumulativeDistanceMeters + segLength) / safeSpeed).toLong()
                val segStartTime = journeyStartTime.plusSeconds(startSec)
                val segEndTime = journeyStartTime.plusSeconds(endSec.coerceAtLeast(startSec + 1))
                
                val mappingResult = mapSegmentToWeather(
                    segment,
                    weatherByCoordinate,
                    segStartTime,
                    segEndTime
                )
                result.add(mappingResult)
                cumulativeDistanceMeters += segLength
            }
        }
        
        return result
    }
    
    /**
     * Determines if interpolation should be used for spatial mapping.
     *
     * @param segment The route segment to evaluate
     * @param weatherObservations The available weather observations
     * @return true if interpolation is recommended, false otherwise
     */
    fun shouldInterpolate(
        segment: RouteSegment,
        weatherObservations: List<WeatherObservation>
    ): Boolean {
        // If we have weather data for the start and end points of the segment,
        // interpolation may be appropriate
        if (weatherObservations.size >= 2) {
            val segmentCenter = calculateSegmentCenter(segment)
            
            // Check if there's weather data close to both ends of the segment
            val startDistance = calculateDistance(segment.startCoordinate, segmentCenter)
            val endDistance = calculateDistance(segment.endCoordinate, segmentCenter)
            
            // If we have nearby weather data for both ends, interpolation might be reasonable
            return (startDistance < 5000.0 || endDistance < 5000.0)
        }
        
        return false
    }
}