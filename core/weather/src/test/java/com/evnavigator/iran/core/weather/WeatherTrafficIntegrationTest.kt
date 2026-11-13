package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeatherTrafficIntegrationTest {
    
    @Test
    fun testCompleteWeatherTrafficDataFlow() = runTest {
        // Create sample data
        val weatherObservation = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 25.0,
            apparentTemperatureCelsius = 27.0,
            windSpeedMps = 3.0,
            windDirectionDegrees = 180.0,
            precipitationMm = 0.0,
            precipitationProbability = 0.1,
            humidityPercentage = 60.0,
            pressureHpa = 1013.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 20.0,
            condition = WeatherCondition.CLEAR,
            source = "test-source",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.9,
            uncertainty = 0.1
        )
        
        val trafficObservation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
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
        
        // Test data provenance
        val provenanceManager = DataProvenance()
        
        val weatherProvenance = provenanceManager.createWeatherProvenance(
            observation = weatherObservation,
            source = "test-source",
            retrievedAt = Instant.now(),
            validFrom = Instant.now().minusSeconds(300),
            validUntil = Instant.now().plusSeconds(3600)
        )
        
        val trafficProvenance = provenanceManager.createTrafficProvenance(
            observation = trafficObservation,
            source = "test-source",
            retrievedAt = Instant.now(),
            validFrom = Instant.now().minusSeconds(300),
            validUntil = Instant.now().plusSeconds(3600)
        )
        
        // Test performance optimization
        val performance = PerformanceOptimization()
        
        // Test batch creation
        val coordinates = List(15) { 
            GeoCoordinate(35.0 + it * 0.1, 51.0 + it * 0.1) 
        }
        val weatherBatches = performance.createWeatherBatches(coordinates, 5)
        assertEquals(3, weatherBatches.size)
        
        // Test data integrity validation
        val security = SecurityDataIntegrity()
        
        val weatherValidation = security.validateWeatherObservation(weatherObservation)
        assertTrue(weatherValidation.isValid)
        
        val trafficValidation = security.validateTrafficObservation(trafficObservation)
        assertTrue(trafficValidation.isValid)
        
        // Test route environment context creation
        val segmentStart = GeoCoordinate(35.6895, 51.3890)
        val segmentEnd = GeoCoordinate(35.7000, 51.4000)
        
        val weatherContext = WeatherEnvironmentContext(weatherObservation, weatherProvenance)
        val trafficContext = TrafficEnvironmentContext(trafficObservation, trafficProvenance)
        
        val environmentContext = RouteEnvironmentContext(
            segmentStartCoordinate = segmentStart,
            segmentEndCoordinate = segmentEnd,
            weatherContext = weatherContext,
            trafficContext = trafficContext
        )
        
        // Verify all components work together
        assertNotNull(environmentContext.weatherContext)
        assertNotNull(environmentContext.trafficContext)
        assertEquals(weatherObservation.condition, environmentContext.weatherContext?.observation?.condition)
        assertEquals(trafficObservation.trafficClass, environmentContext.trafficContext?.observation?.trafficClass)
        
        // Test freshness state determination
        val freshness = provenanceManager.getFreshnessState(weatherProvenance, Instant.now())
        assertEquals(WeatherFreshness.FRESH, freshness)
    }
    
    @Test
    fun testEnvironmentalDataIntegrationWithChargingStrategy() = runTest {
        // Create environmental data for integration with charging strategy
        val weatherObservation = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 25.0,
            apparentTemperatureCelsius = 27.0,
            windSpeedMps = 3.0,
            windDirectionDegrees = 180.0,
            precipitationMm = 0.0,
            precipitationProbability = 0.1,
            humidityPercentage = 60.0,
            pressureHpa = 1013.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 20.0,
            condition = WeatherCondition.CLEAR,
            source = "test-source",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.9,
            uncertainty = 0.1
        )
        
        val trafficObservation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
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
        
        // Create a route environment profile for integration testing
        val weatherProvenance = DataProvenance().createWeatherProvenance(
            observation = weatherObservation,
            source = "test-source",
            retrievedAt = Instant.now(),
            validFrom = Instant.now().minusSeconds(300),
            validUntil = Instant.now().plusSeconds(3600)
        )
        
        val trafficProvenance = DataProvenance().createTrafficProvenance(
            observation = trafficObservation,
            source = "test-source",
            retrievedAt = Instant.now(),
            validFrom = Instant.now().minusSeconds(300),
            validUntil = Instant.now().plusSeconds(3600)
        )
        
        val weatherContext = WeatherEnvironmentContext(weatherObservation, weatherProvenance)
        val trafficContext = TrafficEnvironmentContext(trafficObservation, trafficProvenance)
        
        val environmentProfile = RouteEnvironmentProfile(
            segments = listOf(
                RouteSegmentEnvironment(
                    segmentIndex = 0,
                    segmentStartCoordinate = GeoCoordinate(35.6895, 51.3890),
                    segmentEndCoordinate = GeoCoordinate(35.7000, 51.4000),
                    weatherContext = weatherContext,
                    trafficContext = trafficContext
                )
            ),
            confidence = 0.9,
            uncertainty = 0.1,
            provenance = null
        )
        
        // Create a charging stop for testing integration
        val chargingStop = ChargingStop(
            id = "stop-1",
            coordinate = GeoCoordinate(35.6895, 51.3890),
            stationType = ChargingStationType.AC,
            maxPowerKw = 7.0,
            expectedChargingDuration = java.time.Duration.ofMinutes(30),
            availability = ChargingAvailability.AVAILABLE
        )
        
        // Test that the environmental data can be used in charging strategy context
        // (This would normally be tested with actual ChargingStrategyIntegration logic)
        assertEquals(1, environmentProfile.segments.size)
        assertEquals(weatherObservation.condition, environmentProfile.segments[0].weatherContext?.observation?.condition)
        assertEquals(trafficObservation.trafficClass, environmentProfile.segments[0].trafficContext?.observation?.trafficClass)
        
        // Verify data integrity
        val security = SecurityDataIntegrity()
        val weatherValid = security.validateWeatherObservation(weatherObservation)
        val trafficValid = security.validateTrafficObservation(trafficObservation)
        
        assertTrue(weatherValid.isValid)
        assertTrue(trafficValid.isValid)
    }
    
    @Test
    fun testPerformanceOptimizationIntegration() = runTest {
        // Create sample data for performance testing
        val coordinates = List(100) { 
            GeoCoordinate(35.0 + it * 0.01, 51.0 + it * 0.01) 
        }
        
        val weatherObservations = coordinates.mapIndexed { index, coord ->
            WeatherObservation(
                coordinate = coord,
                timestamp = Instant.now().plusSeconds(index.toLong() * 60),
                temperatureCelsius = 20.0 + (index % 10).toDouble(),
                apparentTemperatureCelsius = 22.0 + (index % 10).toDouble(),
                windSpeedMps = 2.0 + (index % 5).toDouble(),
                windDirectionDegrees = (index * 36) % 360.0,
                precipitationMm = if (index % 20 == 0) 2.0 else 0.0,
                precipitationProbability = if (index % 20 == 0) 0.8 else 0.1,
                humidityPercentage = 50.0 + (index % 30).toDouble(),
                pressureHpa = 1013.0 - (index % 10).toDouble(),
                visibilityMeters = 10000.0 - (index * 100.0),
                cloudCoverPercentage = 20.0 + (index % 50).toDouble(),
                condition = if (index % 20 == 0) WeatherCondition.RAIN else WeatherCondition.CLEAR,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = WeatherFreshness.FRESH,
                confidence = 0.8 + (index % 10).toDouble() / 10.0,
                uncertainty = 0.2 - (index % 10).toDouble() / 10.0
            )
        }
        
        val trafficObservations = coordinates.mapIndexed { index, coord ->
            TrafficObservation(
                coordinate = coord,
                timestamp = Instant.now().plusSeconds(index.toLong() * 60),
                speedMps = 15.0 + (index % 10).toDouble(),
                freeFlowSpeedMps = 25.0 + (index % 10).toDouble(),
                congestionRatio = 0.4 + (index % 10).toDouble() / 10.0,
                trafficClass = TrafficClass.values()[index % TrafficClass.values().size],
                travelTimeMultiplier = 1.2 + (index % 5).toDouble() / 10.0,
                confidence = 0.8 + (index % 10).toDouble() / 10.0,
                uncertainty = 0.2 - (index % 10).toDouble() / 10.0,
                source = "test-source",
                retrievedAt = Instant.now(),
                freshness = TrafficFreshness.FRESH
            )
        }
        
        val performance = PerformanceOptimization()
        
        // Test batch creation for large datasets
        val weatherBatches = performance.createWeatherBatches(coordinates, 10)
        val trafficBatches = performance.createTrafficBatches(coordinates, 10)
        
        assertEquals(10, weatherBatches.size)
        assertEquals(10, trafficBatches.size)
        
        // Test spatial lookup optimization
        val targetCoordinate = GeoCoordinate(35.5, 51.5)
        val closestWeather = performance.findClosestWeatherObservation(
            observations = weatherObservations,
            targetCoordinate = targetCoordinate,
            maxDistanceMeters = 5000.0
        )
        
        assertNotNull(closestWeather)
        
        // Test combined route environment creation
        val segmentCoordinates = coordinates.chunked(2).map { 
            Pair(it[0], it[1]) 
        }.take(10) // Limit for performance
        
        val combinedEnvironment = performance.combineRouteEnvironment(
            weatherData = weatherObservations.take(50),
            trafficData = trafficObservations.take(50),
            segmentCoordinates = segmentCoordinates
        )
        
        assertEquals(10, combinedEnvironment.size)
        
        // Test processing time estimation
        val estimatedTime = performance.estimateProcessingTime(
            routeLength = 100,
            dataPointsPerSegment = 10
        )
        
        assertTrue(estimatedTime > 0)
    }
}