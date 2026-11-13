package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityDataIntegrityTest {
    
    @Test
    fun testValidateWeatherObservationValid() = runTest {
        val observation = WeatherObservation(
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
        
        val security = SecurityDataIntegrity()
        val result = security.validateWeatherObservation(observation)
        
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun testValidateWeatherObservationInvalidCoordinate() = runTest {
        val observation = WeatherObservation(
            coordinate = GeoCoordinate(-100.0, 200.0), // Invalid coordinates
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
        
        val security = SecurityDataIntegrity()
        val result = security.validateWeatherObservation(observation)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun testValidateWeatherObservationOutOfRangeTemperature() = runTest {
        val observation = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 200.0, // Out of physically plausible range
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
        
        val security = SecurityDataIntegrity()
        val result = security.validateWeatherObservation(observation)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun testValidateTrafficObservationValid() = runTest {
        val observation = TrafficObservation(
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
        
        val security = SecurityDataIntegrity()
        val result = security.validateTrafficObservation(observation)
        
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun testValidateTrafficObservationNegativeSpeed() = runTest {
        val observation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            speedMps = -5.0, // Invalid negative speed
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
        
        val security = SecurityDataIntegrity()
        val result = security.validateTrafficObservation(observation)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun testGenerateWeatherHash() = runTest {
        val observation = WeatherObservation(
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
        
        val security = SecurityDataIntegrity()
        val hash = security.generateWeatherHash(observation)
        
        // Should be a valid SHA-256 hash (64 characters)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-fA-F0-9]{64}$")))
    }
    
    @Test
    fun testGenerateTrafficHash() = runTest {
        val observation = TrafficObservation(
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
        
        val security = SecurityDataIntegrity()
        val hash = security.generateTrafficHash(observation)
        
        // Should be a valid SHA-256 hash (64 characters)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-fA-F0-9]{64}$")))
    }
    
    @Test
    fun testAreWeatherObservationsEquivalent() = runTest {
        val obs1 = WeatherObservation(
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
        
        val obs2 = WeatherObservation(
            coordinate = GeoCoordinate(35.6896, 51.3891), // Slightly different coordinates
            timestamp = Instant.now().plusSeconds(300),
            temperatureCelsius = 25.2, // Slightly different temp
            apparentTemperatureCelsius = 27.2,
            windSpeedMps = 3.1,
            windDirectionDegrees = 180.1,
            precipitationMm = 0.0,
            precipitationProbability = 0.1,
            humidityPercentage = 60.1,
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
        
        val security = SecurityDataIntegrity()
        val result = security.areWeatherObservationsEquivalent(obs1, obs2)
        
        assertTrue(result) // Should be equivalent within tolerance
    }
    
    @Test
    fun testIsValidDataSource() {
        val security = SecurityDataIntegrity()
        
        assertTrue(security.isValidDataSource("openweathermap"))
        assertTrue(security.isValidDataSource("iran-weather-api_v1"))
        assertTrue(security.isValidDataSource("test_source_123"))
        assertFalse(security.isValidDataSource("bad<source>")) // Contains invalid character
        assertFalse(security.isValidDataSource("")) // Empty string
    }
}