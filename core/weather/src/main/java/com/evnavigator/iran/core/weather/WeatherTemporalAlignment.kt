package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Implements temporal alignment of weather data with route segments.
 *
 * This class ensures that weather information is properly aligned with the time
 * when a vehicle will traverse each segment of the route, preventing incorrect
 * application of weather data across different time periods.
 */
class WeatherTemporalAlignment {
    
    /**
     * Enum representing temporal alignment strategies.
     */
    enum class AlignmentStrategy {
        /**
         * Use exact match - only use weather data that matches the segment time precisely
         */
        EXACT_MATCH,
        
        /**
         * Use nearest valid forecast - find the closest available forecast
         */
        NEAREST_VALID,
        
        /**
         * Use temporal interpolation - interpolate between forecasts when needed
         */
        TEMPORAL_INTERPOLATION,
        
        /**
         * Use fallback strategy when no data is available
         */
        FALLBACK
    }
    
    /**
     * Represents a temporal alignment result for a route segment.
     */
    data class TemporalAlignmentResult(
        val segmentIndex: Int,
        val startCoordinate: GeoCoordinate,
        val endCoordinate: GeoCoordinate,
        val weatherContext: WeatherContext?,
        val alignmentStrategy: AlignmentStrategy,
        val confidence: Double,
        val uncertainty: Double,
        val temporalOffsetSeconds: Long,
        val validity: TemporalValidity
    )
    
    /**
     * Enum representing temporal validity of data.
     */
    enum class TemporalValidity {
        /**
         * Data is temporally valid and appropriate for the segment
         */
        VALID,
        
        /**
         * Data is valid but slightly outside optimal time window
         */
        SLIGHTLY_OUT_OF_TIME,
        
        /**
         * Data is significantly out of time window
         */
        SIGNIFICANTLY_OUT_OF_TIME,
        
        /**
         * Data is completely invalid for temporal alignment
         */
        INVALID,
        
        /**
         * Temporal validity is unknown
         */
        UNKNOWN
    }
    
    /**
     * Aligns weather data with route segments based on temporal information.
     *
     * @param routeCoordinates The coordinates representing the route
     * @param weatherObservations The weather observations to align
     * @param routeStartTime The start time of the route
     * @param routeEndTime The end time of the route
     * @param segmentTraversalTimes The expected traversal times for each segment
     * @return List of temporal alignment results for each segment
     */
    fun alignWeatherToRouteSegments(
        routeCoordinates: List<GeoCoordinate>,
        weatherObservations: List<WeatherObservation>,
        routeStartTime: Instant,
        routeEndTime: Instant,
        segmentTraversalTimes: List<Instant> = emptyList()
    ): List<TemporalAlignmentResult> {
        if (routeCoordinates.isEmpty() || weatherObservations.isEmpty()) {
            return emptyList()
        }
        
        val segments = createRouteSegments(routeCoordinates)
        val result = mutableListOf<TemporalAlignmentResult>()
        
        // If no traversal times provided, generate them based on simple timing
        val effectiveTraversalTimes = if (segmentTraversalTimes.isNotEmpty()) {
            segmentTraversalTimes
        } else {
            generateSegmentTraversalTimes(segments, routeStartTime, routeEndTime)
        }
        
        for ((index, segment) in segments.withIndex()) {
            val traversalTime = effectiveTraversalTimes.getOrNull(index) ?: routeStartTime
            
            val alignmentResult = alignSegmentToWeather(
                segment,
                index,
                weatherObservations,
                traversalTime
            )
            result.add(alignmentResult)
        }
        
        return result
    }
    
    /**
     * Aligns a single route segment to the most appropriate weather data.
     */
    private fun alignSegmentToWeather(
        segment: RouteSegment,
        segmentIndex: Int,
        weatherObservations: List<WeatherObservation>,
        traversalTime: Instant
    ): TemporalAlignmentResult {
        // Find the best weather observation for this segment at the traversal time
        val alignmentInfo = findBestWeatherForSegment(
            segment,
            weatherObservations,
            traversalTime
        )
        
        val (weatherContext, strategy, offsetPair) = alignmentInfo
        val (temporalOffsetSeconds, validity) = offsetPair
        
        // Calculate confidence and uncertainty based on temporal alignment quality
        val (confidence, uncertainty) = calculateTemporalConfidenceAndUncertainty(
            validity,
            temporalOffsetSeconds
        )
        
        return TemporalAlignmentResult(
            segmentIndex = segmentIndex,
            startCoordinate = segment.startCoordinate,
            endCoordinate = segment.endCoordinate,
            weatherContext = weatherContext,
            alignmentStrategy = strategy,
            confidence = confidence,
            uncertainty = uncertainty,
            temporalOffsetSeconds = temporalOffsetSeconds,
            validity = validity
        )
    }
    
    /**
     * Finds the best weather data for a specific route segment at a given time.
     */
    private fun findBestWeatherForSegment(
        segment: RouteSegment,
        weatherObservations: List<WeatherObservation>,
        traversalTime: Instant
    ): Triple<WeatherContext?, AlignmentStrategy, Pair<Long, TemporalValidity>> {
        // Find the most relevant weather observation for this segment at the traversal time
        val relevantWeather = findMostRelevantWeatherAtTime(
            segment,
            weatherObservations,
            traversalTime
        )
        
        if (relevantWeather != null) {
            // Calculate temporal offset
            val timeDiffSeconds = Duration.between(relevantWeather.observation?.timestamp ?: traversalTime, traversalTime).seconds
            
            // Determine validity based on time difference
            val validity = when {
                abs(timeDiffSeconds) <= 300 -> TemporalValidity.VALID  // Within 5 minutes
                abs(timeDiffSeconds) <= 1800 -> TemporalValidity.SLIGHTLY_OUT_OF_TIME  // Within 30 minutes
                abs(timeDiffSeconds) <= 7200 -> TemporalValidity.SIGNIFICANTLY_OUT_OF_TIME  // Within 2 hours
                else -> TemporalValidity.INVALID  // Too far out of time
            }
            
            val obs = relevantWeather.observation
            val weatherConfidence = if (obs != null) calculateWeatherConfidence(obs, traversalTime) else 0.5
            val weatherContext = WeatherContext(
                observation = obs,
                forecast = null,
                confidence = weatherConfidence,
                uncertainty = 0.3,
                freshness = Freshness.FRESH,
                provenance = obs?.source ?: "temporal"
            )
            
            return Triple(
                weatherContext,
                AlignmentStrategy.NEAREST_VALID,
                Pair(timeDiffSeconds, validity)
            )
        }
        
        // No relevant weather found - use fallback strategy
        return Triple(
            null,
            AlignmentStrategy.FALLBACK,
            Pair(0L, TemporalValidity.INVALID)
        )
    }
    
    /**
     * Finds the most relevant weather observation for a segment at a specific time.
     */
    private fun findMostRelevantWeatherAtTime(
        segment: RouteSegment,
        weatherObservations: List<WeatherObservation>,
        traversalTime: Instant
    ): WeatherContext? {
        // Find the weather observation that is closest to the segment midpoint in both space and time
        val segmentCenter = calculateSegmentCenter(segment)
        
        var bestMatch: WeatherObservation? = null
        var minDistance = Double.MAX_VALUE
        var minTimeDiff = Long.MAX_VALUE
        
        for (observation in weatherObservations) {
            val distance = calculateDistance(segmentCenter, observation.coordinate)
            
            // Calculate temporal difference
            val timeDiff = abs(Duration.between(observation.timestamp, traversalTime).seconds)
            
            // Prefer observations that are both spatially and temporally close
            if (distance < minDistance || (distance == minDistance && timeDiff < minTimeDiff)) {
                minDistance = distance
                minTimeDiff = timeDiff
                bestMatch = observation
            }
        }
        
        return bestMatch?.let { 
            WeatherContext(
                observation = it,
                forecast = null,
                confidence = 0.8, // Default for now
                uncertainty = 0.2,
                freshness = Freshness.FRESH,
                provenance = it.source
            )
        }
    }
    
    /**
     * Calculates weather confidence based on temporal alignment.
     */
    private fun calculateWeatherConfidence(observation: WeatherObservation, traversalTime: Instant): Double {
        val timeDiffSeconds = abs(Duration.between(observation.timestamp, traversalTime).seconds)
        
        // Confidence decreases with temporal distance
        return when {
            timeDiffSeconds <= 300 -> 1.0 // Within 5 minutes - perfect
            timeDiffSeconds <= 1800 -> max(0.7, 1.0 - (timeDiffSeconds - 300) / 1500.0) // Within 30 minutes
            timeDiffSeconds <= 7200 -> max(0.3, 1.0 - (timeDiffSeconds - 1800) / 5400.0) // Within 2 hours
            else -> 0.0 // Too far out of time
        }
    }
    
    /**
     * Calculates confidence and uncertainty based on temporal validity.
     */
    private fun calculateTemporalConfidenceAndUncertainty(
        validity: TemporalValidity,
        temporalOffsetSeconds: Long
    ): Pair<Double, Double> {
        val confidence = when (validity) {
            TemporalValidity.VALID -> 0.95
            TemporalValidity.SLIGHTLY_OUT_OF_TIME -> 0.8
            TemporalValidity.SIGNIFICANTLY_OUT_OF_TIME -> 0.6
            TemporalValidity.INVALID -> 0.2
            TemporalValidity.UNKNOWN -> 0.4
        }
        
        val uncertainty = when (validity) {
            TemporalValidity.VALID -> 0.05
            TemporalValidity.SLIGHTLY_OUT_OF_TIME -> 0.2
            TemporalValidity.SIGNIFICANTLY_OUT_OF_TIME -> 0.4
            TemporalValidity.INVALID -> 0.8
            TemporalValidity.UNKNOWN -> 0.6
        }
        
        return Pair(confidence, uncertainty)
    }
    
    /**
     * Aligns weather forecasts with route segments based on temporal information.
     *
     * @param routeCoordinates The coordinates representing the route
     * @param forecasts The forecast points to align
     * @param routeStartTime The start time of the route
     * @param routeEndTime The end time of the route
     * @param segmentTraversalTimes The expected traversal times for each segment
     * @return List of temporal alignment results for each segment
     */
    fun alignWeatherForecastsToRouteSegments(
        routeCoordinates: List<GeoCoordinate>,
        forecasts: List<ForecastWeatherPoint>,
        routeStartTime: Instant,
        routeEndTime: Instant,
        segmentTraversalTimes: List<Instant> = emptyList()
    ): List<TemporalAlignmentResult> {
        if (routeCoordinates.isEmpty() || forecasts.isEmpty()) {
            return emptyList()
        }
        
        val segments = createRouteSegments(routeCoordinates)
        val result = mutableListOf<TemporalAlignmentResult>()
        
        // If no traversal times provided, generate them based on simple timing
        val effectiveTraversalTimes = if (segmentTraversalTimes.isNotEmpty()) {
            segmentTraversalTimes
        } else {
            generateSegmentTraversalTimes(segments, routeStartTime, routeEndTime)
        }
        
        for ((index, segment) in segments.withIndex()) {
            val traversalTime = effectiveTraversalTimes.getOrNull(index) ?: routeStartTime
            
            val alignmentResult = alignSegmentToWeatherForecast(
                segment,
                index,
                forecasts,
                traversalTime
            )
            result.add(alignmentResult)
        }
        
        return result
    }
    
    /**
     * Aligns a single route segment to the most appropriate weather forecast.
     */
    private fun alignSegmentToWeatherForecast(
        segment: RouteSegment,
        segmentIndex: Int,
        forecasts: List<ForecastWeatherPoint>,
        traversalTime: Instant
    ): TemporalAlignmentResult {
        // Find the best forecast for this segment at the traversal time
        val alignmentInfo = findBestForecastForSegment(
            segment,
            forecasts,
            traversalTime
        )
        
        val (forecast, strategy, offsetPair) = alignmentInfo
        val (temporalOffsetSeconds, validity) = offsetPair
        
        // Calculate confidence and uncertainty based on temporal alignment quality
        val (confidence, uncertainty) = calculateTemporalConfidenceAndUncertainty(
            validity,
            temporalOffsetSeconds
        )
        
        // Create a WeatherContext from the forecast for consistency
        val weatherContext = forecast?.let { forecast ->
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
        
        return TemporalAlignmentResult(
            segmentIndex = segmentIndex,
            startCoordinate = segment.startCoordinate,
            endCoordinate = segment.endCoordinate,
            weatherContext = weatherContext,
            alignmentStrategy = strategy,
            confidence = confidence,
            uncertainty = uncertainty,
            temporalOffsetSeconds = temporalOffsetSeconds,
            validity = validity
        )
    }
    
    /**
     * Finds the best forecast for a specific route segment at a given time.
     */
    private fun findBestForecastForSegment(
        segment: RouteSegment,
        forecasts: List<ForecastWeatherPoint>,
        traversalTime: Instant
    ): Triple<ForecastWeatherPoint?, AlignmentStrategy, Pair<Long, TemporalValidity>> {
        // Find the forecast that is closest to the segment midpoint in both space and time
        val segmentCenter = calculateSegmentCenter(segment)
        
        var bestMatch: ForecastWeatherPoint? = null
        var minDistance = Double.MAX_VALUE
        var minTimeDiff = Long.MAX_VALUE
        
        for (forecast in forecasts) {
            val distance = calculateDistance(segmentCenter, forecast.coordinate)
            
            // Calculate temporal difference
            val timeDiff = abs(Duration.between(forecast.validTime, traversalTime).seconds)
            
            // Prefer forecasts that are both spatially and temporally close
            if (distance < minDistance || (distance == minDistance && timeDiff < minTimeDiff)) {
                minDistance = distance
                minTimeDiff = timeDiff
                bestMatch = forecast
            }
        }
        
        if (bestMatch != null) {
            // Calculate temporal offset
            val timeDiffSeconds = Duration.between(bestMatch.validTime, traversalTime).seconds
            
            // Determine validity based on time difference
            val validity = when {
                abs(timeDiffSeconds) <= 300 -> TemporalValidity.VALID  // Within 5 minutes
                abs(timeDiffSeconds) <= 1800 -> TemporalValidity.SLIGHTLY_OUT_OF_TIME  // Within 30 minutes
                abs(timeDiffSeconds) <= 7200 -> TemporalValidity.SIGNIFICANTLY_OUT_OF_TIME  // Within 2 hours
                else -> TemporalValidity.INVALID  // Too far out of time
            }
            
            return Triple(
                bestMatch,
                AlignmentStrategy.NEAREST_VALID,
                Pair(timeDiffSeconds, validity)
            )
        }
        
        // No relevant forecast found - use fallback strategy
        return Triple(
            null,
            AlignmentStrategy.FALLBACK,
            Pair(0L, TemporalValidity.INVALID)
        )
    }
    
    /**
     * Generates expected traversal times for route segments.
     *
     * @param segments The route segments
     * @param startTime The start time of the route
     * @param endTime The end time of the route
     * @return List of traversal times for each segment
     */
    private fun generateSegmentTraversalTimes(
        segments: List<RouteSegment>,
        startTime: Instant,
        endTime: Instant
    ): List<Instant> {
        val result = mutableListOf<Instant>()
        
        if (segments.isEmpty()) return result
        
        val totalDuration = Duration.between(startTime, endTime).seconds
        val segmentCount = segments.size.toDouble()
        val segmentDurationSeconds = totalDuration / segmentCount
        
        var currentTime = startTime
        for (i in 0 until segments.size) {
            result.add(currentTime)
            currentTime = currentTime.plusSeconds(segmentDurationSeconds.toLong())
        }
        
        return result
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
     * Calculates absolute value to avoid import conflicts.
     */
    private fun abs(value: Long): Long {
        return if (value < 0) -value else value
    }
    
    /**
     * Determines if temporal alignment is possible for a given set of data.
     *
     * @param weatherObservations The weather observations to evaluate
     * @param routeStartTime The start time of the route
     * @param routeEndTime The end time of the route
     * @return true if temporal alignment can be performed, false otherwise
     */
    fun canPerformTemporalAlignment(
        weatherObservations: List<WeatherObservation>,
        routeStartTime: Instant,
        routeEndTime: Instant
    ): Boolean {
        // Need at least one valid weather observation with timestamp
        return weatherObservations.isNotEmpty() && 
               weatherObservations.any { it.timestamp != null } &&
               routeStartTime != null && routeEndTime != null &&
               !routeStartTime.isAfter(routeEndTime)
    }
    
    /**
     * Determines the temporal coverage of weather data.
     *
     * @param weatherObservations The weather observations to analyze
     * @return Temporal coverage information
     */
    fun analyzeTemporalCoverage(weatherObservations: List<WeatherObservation>): TemporalCoverage {
        if (weatherObservations.isEmpty()) {
            return TemporalCoverage(
                minTime = null,
                maxTime = null,
                count = 0,
                coverageSeconds = 0L
            )
        }
        
        val validObservations = weatherObservations.filter { it.timestamp != null }
        if (validObservations.isEmpty()) {
            return TemporalCoverage(
                minTime = null,
                maxTime = null,
                count = 0,
                coverageSeconds = 0L
            )
        }
        
        val minTime = validObservations.minOf { it.timestamp }
        val maxTime = validObservations.maxOf { it.timestamp }
        
        val coverageSeconds = if (minTime != null && maxTime != null) {
            Duration.between(minTime, maxTime).seconds
        } else {
            0L
        }
        
        return TemporalCoverage(
            minTime = minTime,
            maxTime = maxTime,
            count = validObservations.size,
            coverageSeconds = coverageSeconds
        )
    }
    
    /**
     * Represents temporal coverage information.
     */
    data class TemporalCoverage(
        val minTime: Instant?,
        val maxTime: Instant?,
        val count: Int,
        val coverageSeconds: Long
    )
    
    /**
     * Determines if a weather observation is valid for temporal alignment.
     *
     * @param observation The weather observation to validate
     * @param routeStartTime The start time of the route
     * @param routeEndTime The end time of the route
     * @return true if the observation is valid for alignment, false otherwise
     */
    fun isValidForTemporalAlignment(
        observation: WeatherObservation,
        routeStartTime: Instant,
        routeEndTime: Instant
    ): Boolean {
        return observation.timestamp != null &&
               !observation.timestamp.isBefore(routeStartTime) &&
               !observation.timestamp.isAfter(routeEndTime)
    }
}