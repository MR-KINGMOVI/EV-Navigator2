package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.Route
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.*
import java.time.Duration
import java.time.Instant

/**
 * Integrates weather and traffic data with route segmentation for navigation purposes.
 *
 * This class ensures that environmental data (weather and traffic) is properly aligned
 * with route segments to provide accurate, segment-specific information for navigation.
 */
class RouteSegmentationIntegration(
    private val spatialMapper: SpatialMapper,
    private val temporalModel: TemporalModel,
    private val freshnessStateMachine: FreshnessStateMachine
) {
    
    data class WeatherSegmentContext(
        val observation: WeatherObservation?,
        val forecast: ForecastWeatherPoint?,
        val timestamp: Instant,
        val confidence: Double,
        val uncertainty: Double,
        val source: String,
        val freshness: Freshness
    )

    data class TrafficSegmentContext(
        val observations: List<TrafficObservation>,
        val incidents: List<TrafficIncident>,
        val averageSpeed: Double?,
        val averageCongestion: Double?,
        val timestamp: Instant,
        val confidence: Double,
        val uncertainty: Double,
        val source: String,
        val freshness: Freshness
    ) {
        fun calculateTravelTimeMultiplier(): Double {
            return if (averageCongestion != null) 1.0 + (averageCongestion * 2.0) else 1.0
        }
    }

    /**
     * Creates a route environment profile that associates environmental data with route segments.
     *
     * @param route The route to create environment profile for
     * @param weatherData Weather observations and forecasts for the route area
     * @param trafficData Traffic observations and incidents for the route area
     * @param startTime The start time of the route
     * @param endTime The end time of the route
     * @return RouteEnvironmentProfile with segment-specific environmental data
     */
    suspend fun createRouteEnvironmentProfile(
        route: Route,
        weatherData: RouteWeatherData,
        trafficData: RouteTrafficData,
        startTime: Instant,
        endTime: Instant
    ): RouteEnvironmentProfile {
        val segments = route.segments
        val environmentSegments = mutableListOf<RouteSegmentEnvironment>()
        
        // Process each segment in the route
        for ((index, segment) in segments.withIndex()) {
            val segmentEnv = createSegmentEnvironment(
                segment = segment,
                weatherData = weatherData,
                trafficData = trafficData,
                segmentStartTime = calculateSegmentStartTime(startTime, segment, index),
                segmentEndTime = calculateSegmentEndTime(startTime, segment, index)
            )
            environmentSegments.add(segmentEnv)
        }
        
        return RouteEnvironmentProfile(
            segments = environmentSegments,
            startTime = startTime,
            endTime = endTime,
            totalDistance = route.totalDistanceMeters,
            totalTimeEstimate = Duration.between(startTime, endTime),
            confidence = calculateOverallConfidence(environmentSegments),
            uncertainty = calculateOverallUncertainty(environmentSegments)
        )
    }
    
    /**
     * Creates environment data for a specific route segment.
     */
    private suspend fun createSegmentEnvironment(
        segment: RouteSegment,
        weatherData: RouteWeatherData,
        trafficData: RouteTrafficData,
        segmentStartTime: Instant,
        segmentEndTime: Instant
    ): RouteSegmentEnvironment {
        val weatherContext = getWeatherForSegment(segment, weatherData, segmentStartTime, segmentEndTime)
        val trafficContext = getTrafficForSegment(segment, trafficData, segmentStartTime, segmentEndTime)
        
        val weatherCtx = weatherContext?.let {
            WeatherContext(
                observation = it.observation,
                forecast = it.forecast?.let { f -> listOf(f) },
                confidence = it.confidence,
                uncertainty = it.uncertainty,
                freshness = it.freshness,
                provenance = it.source
            )
        }
        val trafficDataForSeg = trafficContext?.let {
            TrafficDataForSegment(
                trafficClass = it.observations.firstOrNull()?.trafficClass ?: TrafficClass.UNKNOWN,
                speed = it.averageSpeed,
                freeFlowSpeed = null,
                congestionRatio = it.averageCongestion,
                travelTimeMultiplier = it.calculateTravelTimeMultiplier(),
                incidents = it.incidents,
                confidence = it.confidence,
                uncertainty = it.uncertainty,
                freshness = it.freshness,
                observation = it.observations.firstOrNull()
            )
        }
        return RouteSegmentEnvironment(
            segment = segment,
            weatherContext = weatherCtx,
            trafficContext = trafficDataForSeg,
            timestamp = Instant.now(),
            confidence = calculateSegmentConfidence(weatherContext, trafficContext),
            uncertainty = calculateSegmentUncertainty(weatherContext, trafficContext),
            freshness = weatherContext?.freshness ?: trafficContext?.freshness ?: Freshness.UNKNOWN,
            provenance = "RouteSegmentationIntegration"
        )
    }
    
    /**
     * Gets weather data for a specific route segment.
     */
    private suspend fun getWeatherForSegment(
        segment: RouteSegment,
        weatherData: RouteWeatherData,
        startTime: Instant,
        endTime: Instant
    ): WeatherSegmentContext? {
        val segmentMidpoint = segment.midpoint
        val nearbyWeather = spatialMapper.mapToNearestWeather(segmentMidpoint, weatherData.observations)
        val forecastData = temporalModel.selectAppropriateForecast(segmentMidpoint, startTime, weatherData.forecasts)
        
        if (nearbyWeather != null) {
            return WeatherSegmentContext(
                observation = nearbyWeather,
                forecast = forecastData,
                timestamp = nearbyWeather.timestamp,
                confidence = nearbyWeather.confidence,
                uncertainty = nearbyWeather.uncertainty,
                source = nearbyWeather.source,
                freshness = Freshness.FRESH
            )
        }
        if (forecastData != null) {
            return WeatherSegmentContext(
                observation = null,
                forecast = forecastData,
                timestamp = forecastData.validTime,
                confidence = forecastData.confidence,
                uncertainty = forecastData.uncertainty,
                source = forecastData.source,
                freshness = Freshness.FRESH
            )
        }
        return null
    }
    
    /**
     * Gets traffic data for a specific route segment.
     */
    private suspend fun getTrafficForSegment(
        segment: RouteSegment,
        trafficData: RouteTrafficData,
        startTime: Instant,
        endTime: Instant
    ): TrafficSegmentContext? {
        val segmentMidpoint = segment.midpoint
        val nearbyTraffic = spatialMapper.mapToNearestTraffic(segmentMidpoint, trafficData.observations)
        val trafficObservations = listOfNotNull(nearbyTraffic)
        val incidents = trafficData.incidents
        
        if (trafficObservations.isNotEmpty()) {
            val avgSpeed = trafficObservations.mapNotNull { it.speedMps }.average().takeIf { !it.isNaN() }
            val avgCongestion = trafficObservations.mapNotNull { it.congestionRatio }.average().takeIf { !it.isNaN() }
            return TrafficSegmentContext(
                observations = trafficObservations,
                incidents = incidents,
                averageSpeed = avgSpeed,
                averageCongestion = avgCongestion,
                timestamp = startTime,
                confidence = calculateTrafficConfidence(trafficObservations),
                uncertainty = calculateTrafficUncertainty(trafficObservations),
                source = trafficObservations.firstOrNull()?.source ?: "unknown",
                freshness = Freshness.FRESH
            )
        }
        if (incidents.isNotEmpty()) {
            return TrafficSegmentContext(
                observations = emptyList(),
                incidents = incidents,
                averageSpeed = null,
                averageCongestion = null,
                timestamp = startTime,
                confidence = 0.6,
                uncertainty = 0.4,
                source = "incidents",
                freshness = Freshness.FRESH
            )
        }
        return null
    }
    
    /**
     * Calculates the estimated traversal time for a segment based on traffic conditions.
     */
    private fun calculateEstimatedTraversalTime(
        segment: RouteSegment,
        trafficContext: TrafficSegmentContext?
    ): Long {
        val baseTimeSeconds = segment.lengthMeters / 15.0
        if (trafficContext == null) {
            return baseTimeSeconds.toLong()
        }
        val multiplier = trafficContext.calculateTravelTimeMultiplier()
        return (baseTimeSeconds * multiplier).toLong()
    }
    
    /**
     * Calculates the start time for a route segment.
     */
    private fun calculateSegmentStartTime(
        routeStartTime: Instant,
        segment: RouteSegment,
        segmentIndex: Int
    ): Instant {
        val estimatedTravelTime = segmentIndex * 60L
        return routeStartTime.plusSeconds(estimatedTravelTime)
    }
    
    /**
     * Calculates the end time for a route segment.
     */
    private fun calculateSegmentEndTime(
        routeStartTime: Instant,
        segment: RouteSegment,
        segmentIndex: Int
    ): Instant {
        val segmentStartTime = calculateSegmentStartTime(routeStartTime, segment, segmentIndex)
        val estimatedTravelTime = segment.lengthMeters / 15.0
        return segmentStartTime.plusSeconds(estimatedTravelTime.toLong())
    }
    
    /**
     * Calculates overall confidence for the route environment profile.
     */
    private fun calculateOverallConfidence(segments: List<RouteSegmentEnvironment>): Double {
        if (segments.isEmpty()) return 0.0
        val totalConfidence = segments.sumOf { it.confidence }
        return totalConfidence / segments.size
    }
    
    /**
     * Calculates overall uncertainty for the route environment profile.
     */
    private fun calculateOverallUncertainty(segments: List<RouteSegmentEnvironment>): Double {
        if (segments.isEmpty()) return 1.0
        val totalUncertainty = segments.sumOf { it.uncertainty }
        return totalUncertainty / segments.size
    }
    
    private fun calculateSegmentConfidence(
        weatherContext: WeatherSegmentContext?,
        trafficContext: TrafficSegmentContext?
    ): Double {
        var confidence = 0.5
        if (weatherContext != null) {
            confidence += 0.2 * weatherContext.confidence
        }
        if (trafficContext != null) {
            confidence += 0.3 * trafficContext.confidence
        }
        return minOf(1.0, confidence)
    }
    
    private fun calculateSegmentUncertainty(
        weatherContext: WeatherSegmentContext?,
        trafficContext: TrafficSegmentContext?
    ): Double {
        var uncertainty = 0.5
        if (weatherContext != null) {
            uncertainty += 0.2 * weatherContext.uncertainty
        }
        if (trafficContext != null) {
            uncertainty += 0.3 * trafficContext.uncertainty
        }
        return minOf(1.0, uncertainty)
    }
    
    private fun calculateTrafficConfidence(observations: List<TrafficObservation>): Double {
        if (observations.isEmpty()) return 0.0
        return observations.map { it.confidence }.average()
    }
    
    private fun calculateTrafficUncertainty(observations: List<TrafficObservation>): Double {
        if (observations.isEmpty()) return 1.0
        return observations.map { it.uncertainty }.average()
    }
    
    data class RouteWeatherData(
        val observations: List<WeatherObservation>,
        val forecasts: List<ForecastWeatherPoint>
    )
    
    data class RouteTrafficData(
        val observations: List<TrafficObservation>,
        val incidents: List<TrafficIncident>
    )
    
    data class DataTimestamps(
        val weatherTimestamp: Instant,
        val trafficTimestamp: Instant
    )
    
    data class Provenance(
        val weatherSource: String?,
        val trafficSource: String?,
        val retrievedAt: Instant
    )
}
