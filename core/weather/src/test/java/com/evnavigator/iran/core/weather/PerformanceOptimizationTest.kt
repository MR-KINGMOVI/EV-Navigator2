package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PerformanceOptimizationTest {
    
    @Test
    fun testCreateWeatherBatches() {
        val coordinates = List(25) { 
            GeoCoordinate(35.0 + it * 0.1, 51.0 + it * 0.1) 
        }
        
        val performance = PerformanceOptimization()
        val batches = performance.createWeatherBatches(coordinates, 5)
        
        assertEquals(5, batches.size) // 25 coordinates / 5 = 5 batches
        assertEquals(5, batches[0].size) // Each batch should have 5 items
        assertEquals(5, batches[4].size) // Last batch should also have 5 items
    }
    
    @Test
    fun testCreateTrafficBatches() {
        val coordinates = List(12) { 
            GeoCoordinate(35.0 + it * 0.1, 51.0 + it * 0.1) 
        }
        
        val performance = PerformanceOptimization()
        val batches = performance.createTrafficBatches(coordinates, 4)
        
        assertEquals(3, batches.size) // 12 coordinates / 4 = 3 batches
        assertEquals(4, batches[0].size) // Each batch should have 4 items
        assertEquals(4, batches[2].size) // Last batch should also have 4 items
    }
    
    @Test
    fun testFindClosestWeatherObservation() {
        val observations = listOf(
            WeatherObservation(
                coordinate = GeoCoordinate(35.0, 51.0),
                timestamp = Instant.now(),
                temperatureCelsius = 20.0,
                apparentTemperatureCelsius = 22.0,
                windSpeedMps = 2.0,
                windDirectionDegrees = 0.0,
                precipitationMm = 0.0,
                precipitationProbability = 0.0,
                humidityPercentage = 50.0,
                pressureHpa = 1013.0,
                visibilityMeters = 10000.0,
                cloudCoverPercentage = 0.0,
                condition = WeatherCondition.CLEAR,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = WeatherFreshness.FRESH,
                confidence = 0.9,
                uncertainty = 0.1
            ),
            WeatherObservation(
                coordinate = GeoCoordinate(35.1, 51.1),
                timestamp = Instant.now(),
                temperatureCelsius = 22.0,
                apparentTemperatureCelsius = 24.0,
                windSpeedMps = 3.0,
                windDirectionDegrees = 90.0,
                precipitationMm = 0.0,
                precipitationProbability = 0.0,
                humidityPercentage = 55.0,
                pressureHpa = 1012.0,
                visibilityMeters = 8000.0,
                cloudCoverPercentage = 10.0,
                condition = WeatherCondition.PARTLY_CLOUDY,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = WeatherFreshness.FRESH,
                confidence = 0.9,
                uncertainty = 0.1
            )
        )
        
        val performance = PerformanceOptimization()
        val targetCoord = GeoCoordinate(35.05, 51.05)
        
        val result = performance.findClosestWeatherObservation(
            observations = observations,
            targetCoordinate = targetCoord,
            maxDistanceMeters = 10000.0
        )
        
        assertNotNull(result)
        assertEquals(observations[0].coordinate.latitude, result.coordinate.latitude)
        assertEquals(observations[0].coordinate.longitude, result.coordinate.longitude)
    }
    
    @Test
    fun testFindClosestWeatherObservationNotFound() {
        val observations = listOf(
            WeatherObservation(
                coordinate = GeoCoordinate(35.0, 51.0),
                timestamp = Instant.now(),
                temperatureCelsius = 20.0,
                apparentTemperatureCelsius = 22.0,
                windSpeedMps = 2.0,
                windDirectionDegrees = 0.0,
                precipitationMm = 0.0,
                precipitationProbability = 0.0,
                humidityPercentage = 50.0,
                pressureHpa = 1013.0,
                visibilityMeters = 10000.0,
                cloudCoverPercentage = 0.0,
                condition = WeatherCondition.CLEAR,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = WeatherFreshness.FRESH,
                confidence = 0.9,
                uncertainty = 0.1
            )
        )
        
        val performance = PerformanceOptimization()
        val targetCoord = GeoCoordinate(50.0, 60.0) // Far away coordinate
        
        val result = performance.findClosestWeatherObservation(
            observations = observations,
            targetCoordinate = targetCoord,
            maxDistanceMeters = 1000.0 // Too small distance
        )
        
        assertNull(result)
    }
    
    @Test
    fun testCombineRouteEnvironment() {
        val weatherData = listOf(
            WeatherObservation(
                coordinate = GeoCoordinate(35.0, 51.0),
                timestamp = Instant.now(),
                temperatureCelsius = 20.0,
                apparentTemperatureCelsius = 22.0,
                windSpeedMps = 2.0,
                windDirectionDegrees = 0.0,
                precipitationMm = 0.0,
                precipitationProbability = 0.0,
                humidityPercentage = 50.0,
                pressureHpa = 1013.0,
                visibilityMeters = 10000.0,
                cloudCoverPercentage = 0.0,
                condition = WeatherCondition.CLEAR,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = WeatherFreshness.FRESH,
                confidence = 0.9,
                uncertainty = 0.1
            )
        )
        
        val trafficData = listOf(
            TrafficObservation(
                coordinate = GeoCoordinate(35.0, 51.0),
                timestamp = Instant.now(),
                speedMps = 15.0,
                freeFlowSpeedMps = 25.0,
                congestionRatio = 0.6,
                trafficClass = TrafficClass.MODERATE,
                travelTimeMultiplier = 1.4,
                confidence = 0.85,
                uncertainty = 0.15,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = TrafficFreshness.FRESH
            )
        )
        
        val performance = PerformanceOptimization()
        val segmentCoordinates = listOf(
            Pair(GeoCoordinate(35.0, 51.0), GeoCoordinate(35.1, 51.1))
        )
        
        val result = performance.combineRouteEnvironment(
            weatherData = weatherData,
            trafficData = trafficData,
            segmentCoordinates = segmentCoordinates
        )
        
        assertEquals(1, result.size)
        assertNotNull(result[0].weatherContext)
        assertNotNull(result[0].trafficContext)
    }
    
    @Test
    fun testEstimateProcessingTime() {
        val performance = PerformanceOptimization()
        
        val time = performance.estimateProcessingTime(
            routeLength = 100,
            dataPointsPerSegment = 5
        )
        
        // Should be a reasonable estimate: 100*5 + 100*5*2 = 500 + 1000 = 1500ms
        assertEquals(1500L, time)
    }
}