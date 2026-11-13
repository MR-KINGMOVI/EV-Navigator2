package com.evnavigator.iran.core.weather.provider

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Provider interface for weather data.
 * 
 * This abstraction allows the system to work with different weather data sources
 * while maintaining a consistent domain model interface.
 */
interface WeatherProvider {
    
    /**
     * Get current weather observation at a specific coordinate.
     * 
     * @param coordinate The geographic location to query
     * @return Flow of WeatherObservation or null if no data available
     */
    suspend fun getCurrentWeather(coordinate: GeoCoordinate): WeatherObservation?
    
    /**
     * Get weather forecast for a specific coordinate and time range.
     * 
     * @param coordinate The geographic location to query  
     * @param startTime The start of the forecast period
     * @param endTime The end of the forecast period
     * @return Flow of ForecastWeatherPoint list or empty list if no data available
     */
    suspend fun getForecast(
        coordinate: GeoCoordinate,
        startTime: Instant,
        endTime: Instant
    ): List<ForecastWeatherPoint>
    
    /**
     * Get weather data for a route (multiple coordinates).
     * 
     * @param coordinates The route coordinates to query
     * @param startTime The start time of the route
     * @param endTime The end time of the route  
     * @return Flow of WeatherObservation list or empty list if no data available
     */
    suspend fun getRouteWeather(
        coordinates: List<GeoCoordinate>,
        startTime: Instant,
        endTime: Instant
    ): List<WeatherObservation>
    
    /**
     * Get weather data for a specific point in time and space.
     * 
     * @param coordinate The geographic location to query
     * @param time The time of the observation
     * @return WeatherObservation or null if no data available
     */
    suspend fun getWeatherAtTime(
        coordinate: GeoCoordinate,
        time: Instant
    ): WeatherObservation?
    
    /**
     * Get weather forecast for a specific point in time and space.
     * 
     * @param coordinate The geographic location to query
     * @param time The time of the forecast
     * @return ForecastWeatherPoint or null if no data available
     */
    suspend fun getForecastAtTime(
        coordinate: GeoCoordinate,
        time: Instant
    ): ForecastWeatherPoint?
    
    /**
     * Get weather data for a route segment.
     * 
     * @param startCoordinate The starting coordinate of the segment
     * @param endCoordinate The ending coordinate of the segment
     * @param startTime The start time of the segment
     * @param endTime The end time of the segment
     * @return WeatherObservation or null if no data available
     */
    suspend fun getSegmentWeather(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        startTime: Instant,
        endTime: Instant
    ): WeatherObservation?
}