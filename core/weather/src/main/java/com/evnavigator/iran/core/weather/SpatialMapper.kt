package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Instant

/**
 * Spatial mapping utilities for weather and traffic data.
 * 
 * This class handles mapping weather and traffic observations to route segments
 * and performing spatial interpolation where appropriate.
 */
class SpatialMapper {
    
    /**
     * Maps a coordinate to the nearest valid weather observation point.
     * 
     * @param coordinate The geographic coordinate to map
     * @param weatherPoints List of available weather points in vicinity
     * @return The most appropriate weather observation or null if none found
     */
    fun mapToNearestWeather(
        coordinate: GeoCoordinate,
        weatherPoints: List<WeatherObservation>
    ): WeatherObservation? {
        // Simple implementation - find closest point
        return weatherPoints.minByOrNull { 
            calculateDistance(coordinate, it.coordinate) 
        }
    }
    
    /**
     * Maps coordinates to route segments with appropriate weather context.
     * 
     * @param routeCoordinates List of coordinates representing the route
     * @param weatherPoints List of available weather points
     * @return List of WeatherObservation mapped to route segments
     */
    fun mapRouteToWeatherSegments(
        routeCoordinates: List<GeoCoordinate>,
        weatherPoints: List<WeatherObservation>
    ): List<WeatherObservation> {
        // For each point in the route, find the nearest weather point
        return routeCoordinates.mapNotNull { coordinate ->
            mapToNearestWeather(coordinate, weatherPoints)
        }
    }
    
    /**
     * Maps a coordinate to the nearest valid traffic observation.
     * 
     * @param coordinate The geographic coordinate to map
     * @param trafficObservations List of available traffic observations in vicinity
     * @return The most appropriate traffic observation or null if none found
     */
    fun mapToNearestTraffic(
        coordinate: GeoCoordinate,
        trafficObservations: List<TrafficObservation>
    ): TrafficObservation? {
        // Simple implementation - find closest point
        return trafficObservations.minByOrNull { 
            calculateDistance(coordinate, it.coordinate) 
        }
    }
    
    /**
     * Maps coordinates to route segments with appropriate traffic context.
     * 
     * @param routeCoordinates List of coordinates representing the route
     * @param trafficObservations List of available traffic observations
     * @return List of TrafficObservation mapped to route segments
     */
    fun mapRouteToTrafficSegments(
        routeCoordinates: List<GeoCoordinate>,
        trafficObservations: List<TrafficObservation>
    ): List<TrafficObservation> {
        // For each point in the route, find the nearest traffic observation
        return routeCoordinates.mapNotNull { coordinate ->
            mapToNearestTraffic(coordinate, trafficObservations)
        }
    }
    
    /**
     * Performs spatial interpolation of weather data between points.
     * 
     * @param startCoordinate Start coordinate
     * @param endCoordinate End coordinate  
     * @param startTime Start time
     * @param endTime End time
     * @param startWeather Weather at start point
     * @param endWeather Weather at end point
     * @param interpolationTime Time to interpolate for
     * @return Interpolated weather observation or null if unable to interpolate
     */
    fun interpolateWeather(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        startTime: Instant,
        endTime: Instant,
        startWeather: WeatherObservation,
        endWeather: WeatherObservation,
        interpolationTime: Instant
    ): WeatherObservation? {
        // Simple linear interpolation between points in time and space
        val timeRatio = calculateTimeRatio(startTime, endTime, interpolationTime)
        
        if (timeRatio < 0.0 || timeRatio > 1.0) return null
        
        val interpolatedCoordinate = interpolateCoordinate(
            startCoordinate, endCoordinate, timeRatio
        )
        
        // For each weather parameter, interpolate linearly
        val interpolatedTemp = interpolateDouble(
            startWeather.temperatureCelsius, endWeather.temperatureCelsius, timeRatio
        )
        
        val interpolatedWindSpeed = interpolateDouble(
            startWeather.windSpeedMps, endWeather.windSpeedMps, timeRatio
        )
        
        // For categorical values like condition, use the start value (simplified)
        val interpolatedCondition = startWeather.condition
        
        return WeatherObservation(
            coordinate = interpolatedCoordinate,
            timestamp = interpolationTime,
            temperatureCelsius = interpolatedTemp,
            apparentTemperatureCelsius = interpolateDouble(
                startWeather.apparentTemperatureCelsius, 
                endWeather.apparentTemperatureCelsius, 
                timeRatio
            ),
            windSpeedMps = interpolatedWindSpeed,
            windDirectionDegrees = interpolateDouble(
                startWeather.windDirectionDegrees, 
                endWeather.windDirectionDegrees, 
                timeRatio
            ),
            precipitationMm = interpolateDouble(
                startWeather.precipitationMm, 
                endWeather.precipitationMm, 
                timeRatio
            ),
            precipitationProbability = interpolateDouble(
                startWeather.precipitationProbability, 
                endWeather.precipitationProbability, 
                timeRatio
            ),
            humidityPercentage = interpolateDouble(
                startWeather.humidityPercentage, 
                endWeather.humidityPercentage, 
                timeRatio
            ),
            pressureHpa = interpolateDouble(
                startWeather.pressureHpa, 
                endWeather.pressureHpa, 
                timeRatio
            ),
            visibilityMeters = interpolateDouble(
                startWeather.visibilityMeters, 
                endWeather.visibilityMeters, 
                timeRatio
            ),
            cloudCoverPercentage = interpolateDouble(
                startWeather.cloudCoverPercentage, 
                endWeather.cloudCoverPercentage, 
                timeRatio
            ),
            condition = interpolatedCondition,
            source = "interpolated",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.REASONABLY_FRESH,
            confidence = (startWeather.confidence + endWeather.confidence) / 2.0,
            uncertainty = (startWeather.uncertainty + endWeather.uncertainty) / 2.0
        )
    }
    
    /**
     * Performs spatial interpolation of traffic data between points.
     * 
     * @param startCoordinate Start coordinate
     * @param endCoordinate End coordinate  
     * @param startTime Start time
     * @param endTime End time
     * @param startTraffic Traffic at start point
     * @param endTraffic Traffic at end point
     * @param interpolationTime Time to interpolate for
     * @return Interpolated traffic observation or null if unable to interpolate
     */
    fun interpolateTraffic(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        startTime: Instant,
        endTime: Instant,
        startTraffic: TrafficObservation,
        endTraffic: TrafficObservation,
        interpolationTime: Instant
    ): TrafficObservation? {
        // Simple linear interpolation between points in time and space
        val timeRatio = calculateTimeRatio(startTime, endTime, interpolationTime)
        
        if (timeRatio < 0.0 || timeRatio > 1.0) return null
        
        val interpolatedCoordinate = interpolateCoordinate(
            startCoordinate, endCoordinate, timeRatio
        )
        
        // For each traffic parameter, interpolate linearly
        val interpolatedSpeed = interpolateDouble(
            startTraffic.speedMps, endTraffic.speedMps, timeRatio
        )
        
        val interpolatedFreeFlowSpeed = interpolateDouble(
            startTraffic.freeFlowSpeedMps, endTraffic.freeFlowSpeedMps, timeRatio
        )
        
        val interpolatedCongestion = interpolateDouble(
            startTraffic.congestionRatio, endTraffic.congestionRatio, timeRatio
        )
        
        val interpolatedMultiplier = interpolateDouble(
            startTraffic.travelTimeMultiplier, endTraffic.travelTimeMultiplier, timeRatio
        )
        
        return TrafficObservation(
            coordinate = interpolatedCoordinate,
            timestamp = interpolationTime,
            speedMps = interpolatedSpeed,
            freeFlowSpeedMps = interpolatedFreeFlowSpeed,
            congestionRatio = interpolatedCongestion,
            trafficClass = determineTrafficClass(interpolatedCongestion),
            travelTimeMultiplier = interpolatedMultiplier,
            confidence = (startTraffic.confidence + endTraffic.confidence) / 2.0,
            uncertainty = (startTraffic.uncertainty + endTraffic.uncertainty) / 2.0,
            source = "interpolated",
            retrievedAt = Instant.now(),
            freshness = TrafficFreshness.FRESH
        )
    }
    
    /**
     * Calculates the distance between two geographic coordinates in meters.
     */
    private fun calculateDistance(
        coord1: GeoCoordinate,
        coord2: GeoCoordinate
    ): Double {
        // Simple Haversine formula approximation for small distances
        val R = 6371000.0 // Earth radius in meters
        val lat1Rad = Math.toRadians(coord1.latitude)
        val lat2Rad = Math.toRadians(coord2.latitude)
        val deltaLatRad = Math.toRadians(coord2.latitude - coord1.latitude)
        val deltaLonRad = Math.toRadians(coord2.longitude - coord1.longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return R * c
    }
    
    /**
     * Calculates the time ratio between start and end times for interpolation.
     */
    private fun calculateTimeRatio(
        startTime: Instant,
        endTime: Instant,
        interpolationTime: Instant
    ): Double {
        val totalDuration = endTime.toEpochMilli() - startTime.toEpochMilli()
        if (totalDuration <= 0) return 0.0
        
        val elapsed = interpolationTime.toEpochMilli() - startTime.toEpochMilli()
        return elapsed.toDouble() / totalDuration.toDouble()
    }
    
    /**
     * Interpolates between two double values.
     */
    private fun interpolateDouble(
        start: Double?,
        end: Double?,
        ratio: Double
    ): Double? {
        if (start == null || end == null) return null
        return start + (end - start) * ratio
    }
    
    /**
     * Interpolates between two geographic coordinates.
     */
    private fun interpolateCoordinate(
        start: GeoCoordinate,
        end: GeoCoordinate,
        ratio: Double
    ): GeoCoordinate {
        return GeoCoordinate(
            latitude = start.latitude + (end.latitude - start.latitude) * ratio,
            longitude = start.longitude + (end.longitude - start.longitude) * ratio
        )
    }
    
    /**
     * Determines traffic class based on congestion ratio.
     */
    private fun determineTrafficClass(congestionRatio: Double?): TrafficClass {
        if (congestionRatio == null) return TrafficClass.UNKNOWN
        
        return when {
            congestionRatio <= 0.5 -> TrafficClass.FREE_FLOW
            congestionRatio <= 0.7 -> TrafficClass.LIGHT
            congestionRatio <= 0.9 -> TrafficClass.MODERATE
            congestionRatio <= 1.2 -> TrafficClass.HEAVY
            else -> TrafficClass.SEVERE
        }
    }
}