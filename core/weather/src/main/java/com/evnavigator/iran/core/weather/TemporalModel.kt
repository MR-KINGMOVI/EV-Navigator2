package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Duration
import java.time.Instant

/**
 * Temporal model for weather and traffic data.
 * 
 * This class handles time-based operations, including forecast validity,
 * temporal alignment, and time-series analysis for environmental data.
 */
class TemporalModel {
    
    /**
     * Determines the appropriate weather forecast point for a given time and location.
     * 
     * @param coordinate The geographic location
     * @param targetTime The time to find forecast for
     * @param forecasts List of available forecasts
     * @return The most relevant forecast or null if none found
     */
    fun selectAppropriateForecast(
        coordinate: GeoCoordinate,
        targetTime: Instant,
        forecasts: List<ForecastWeatherPoint>
    ): ForecastWeatherPoint? {
        // Filter forecasts by location (nearest)
        val nearestForecasts = forecasts.filter { 
            it.coordinate.latitude == coordinate.latitude && 
            it.coordinate.longitude == coordinate.longitude 
        }
        
        if (nearestForecasts.isEmpty()) return null
        
        // Find the forecast that covers the target time
        return nearestForecasts.find { 
            targetTime >= it.validTime && targetTime <= it.validTime.plusSeconds(3600) // 1 hour window
        } ?: nearestForecasts.firstOrNull()
    }
    
    /**
     * Determines if a weather observation is valid for the given time.
     * 
     * @param observation The weather observation to validate
     * @param targetTime The reference time
     * @param maxAge The maximum allowed age in seconds
     * @return True if observation is valid, false otherwise
     */
    fun isValidWeatherObservation(
        observation: WeatherObservation,
        targetTime: Instant,
        maxAge: Duration = Duration.ofMinutes(10)
    ): Boolean {
        val timeDiff = Duration.between(observation.timestamp, targetTime)
        return Math.abs(timeDiff.toSeconds()) <= maxAge.toSeconds()
    }
    
    /**
     * Determines if a traffic observation is valid for the given time.
     * 
     * @param observation The traffic observation to validate
     * @param targetTime The reference time
     * @param maxAge The maximum allowed age in seconds
     * @return True if observation is valid, false otherwise
     */
    fun isValidTrafficObservation(
        observation: TrafficObservation,
        targetTime: Instant,
        maxAge: Duration = Duration.ofMinutes(5)
    ): Boolean {
        val timeDiff = Duration.between(observation.timestamp, targetTime)
        return Math.abs(timeDiff.toSeconds()) <= maxAge.toSeconds()
    }
    
    /**
     * Calculates the forecast horizon for a given weather point.
     * 
     * @param forecastPoint The forecast point
     * @param referenceTime The reference time to calculate from
     * @return Forecast horizon in hours
     */
    fun calculateForecastHorizon(
        forecastPoint: ForecastWeatherPoint,
        referenceTime: Instant
    ): Double {
        val duration = Duration.between(referenceTime, forecastPoint.validTime)
        return duration.toHours().toDouble()
    }
    
    /**
     * Aligns temporal data to a route's time progression.
     * 
     * @param routeCoordinates The route coordinates
     * @param weatherPoints Weather observations along the route
     * @param startTime The start time of the route
     * @param endTime The end time of the route
     * @return Aligned weather context for each route segment
     */
    fun alignWeatherToRoute(
        routeCoordinates: List<GeoCoordinate>,
        weatherPoints: List<WeatherObservation>,
        startTime: Instant,
        endTime: Instant
    ): RouteWeatherContext {
        val totalDuration = Duration.between(startTime, endTime)
        val segments = mutableListOf<RouteSegmentWeather>()
        
        // For each segment in the route, find appropriate weather data
        for (i in 0 until routeCoordinates.size - 1) {
            val startCoord = routeCoordinates[i]
            val endCoord = routeCoordinates[i + 1]
            
            // Calculate approximate time for this segment
            val segmentStartTime = startTime.plus(
                Duration.ofMillis(
                    (totalDuration.toMillis() * i.toDouble() / (routeCoordinates.size - 1)).toLong()
                )
            )
            
            val segmentEndTime = startTime.plus(
                Duration.ofMillis(
                    (totalDuration.toMillis() * (i + 1).toDouble() / (routeCoordinates.size - 1)).toLong()
                )
            )
            
            // Find weather data that corresponds to this segment
            val segmentWeather = weatherPoints.filter { 
                it.timestamp >= segmentStartTime && it.timestamp <= segmentEndTime
            }.firstOrNull()
            
            segments.add(
                RouteSegmentWeather(
                    startCoordinate = startCoord,
                    endCoordinate = endCoord,
                    startTime = segmentStartTime,
                    endTime = segmentEndTime,
                    weather = segmentWeather
                )
            )
        }
        
        return RouteWeatherContext(
            segments = segments,
            startTime = startTime,
            endTime = endTime
        )
    }
    
    /**
     * Aligns temporal traffic data to a route's time progression.
     * 
     * @param routeCoordinates The route coordinates
     * @param trafficObservations Traffic observations along the route
     * @param startTime The start time of the route
     * @param endTime The end time of the route
     * @return Aligned traffic context for each route segment
     */
    fun alignTrafficToRoute(
        routeCoordinates: List<GeoCoordinate>,
        trafficObservations: List<TrafficObservation>,
        startTime: Instant,
        endTime: Instant
    ): RouteTrafficContext {
        val totalDuration = Duration.between(startTime, endTime)
        val segments = mutableListOf<RouteSegmentTraffic>()
        
        // For each segment in the route, find appropriate traffic data
        for (i in 0 until routeCoordinates.size - 1) {
            val startCoord = routeCoordinates[i]
            val endCoord = routeCoordinates[i + 1]
            
            // Calculate approximate time for this segment
            val segmentStartTime = startTime.plus(
                Duration.ofMillis(
                    (totalDuration.toMillis() * i.toDouble() / (routeCoordinates.size - 1)).toLong()
                )
            )
            
            val segmentEndTime = startTime.plus(
                Duration.ofMillis(
                    (totalDuration.toMillis() * (i + 1).toDouble() / (routeCoordinates.size - 1)).toLong()
                )
            )
            
            // Find traffic data that corresponds to this segment
            val segmentTraffic = trafficObservations.filter { 
                it.timestamp >= segmentStartTime && it.timestamp <= segmentEndTime
            }.firstOrNull()
            
            segments.add(
                RouteSegmentTraffic(
                    startCoordinate = startCoord,
                    endCoordinate = endCoord,
                    startTime = segmentStartTime,
                    endTime = segmentEndTime,
                    traffic = segmentTraffic
                )
            )
        }
        
        return RouteTrafficContext(
            segments = segments,
            startTime = startTime,
            endTime = endTime
        )
    }
    
    /**
     * Determines the confidence level of a forecast based on its age and horizon.
     * 
     * @param forecastPoint The forecast point
     * @param referenceTime The time to compare against
     * @return Confidence value between 0.0 and 1.0
     */
    fun calculateForecastConfidence(
        forecastPoint: ForecastWeatherPoint,
        referenceTime: Instant
    ): Double {
        val horizon = calculateForecastHorizon(forecastPoint, referenceTime)
        
        // Confidence decreases with forecast horizon (more time = less certainty)
        val horizonFactor = 1.0 / (1.0 + 0.1 * horizon) // 10% decrease per hour
        
        // Use the forecast's inherent confidence as a base
        return minOf(forecastPoint.confidence * horizonFactor, 1.0)
    }
}

/**
 * Contextual information for route segments with weather data.
 */
data class RouteWeatherContext(
    val segments: List<RouteSegmentWeather>,
    val startTime: Instant,
    val endTime: Instant
)

/**
 * Weather data for a specific route segment.
 */
data class RouteSegmentWeather(
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val startTime: Instant,
    val endTime: Instant,
    val weather: WeatherObservation?
)

/**
 * Contextual information for route segments with traffic data.
 */
data class RouteTrafficContext(
    val segments: List<RouteSegmentTraffic>,
    val startTime: Instant,
    val endTime: Instant
)

/**
 * Traffic data for a specific route segment.
 */
data class RouteSegmentTraffic(
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val startTime: Instant,
    val endTime: Instant,
    val traffic: TrafficObservation?
)