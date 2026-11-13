package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import com.evnavigator.iran.core.weather.validation.WeatherValidator
import org.junit.Test
import java.time.Instant

class WeatherValidatorTest {
    
    private val validator = WeatherValidator()
    
    @Test
    fun `test valid weather observation validation`() {
        val observation = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890), // Tehran coordinates
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
            source = "test-provider",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.95,
            uncertainty = 0.05
        )
        
        val result = validator.validateWeatherObservation(observation)
        
        assert(!result.hasErrors) { "Valid observation should not have errors" }
        assert(!result.hasWarnings) { "Valid observation should not have warnings" }
    }
    
    @Test
    fun `test invalid temperature validation`() {
        val observation = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 200.0, // Out of reasonable range
            apparentTemperatureCelsius = 200.0,
            windSpeedMps = 3.0,
            windDirectionDegrees = 180.0,
            precipitationMm = 0.0,
            precipitationProbability = 0.1,
            humidityPercentage = 60.0,
            pressureHpa = 1013.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 20.0,
            condition = WeatherCondition.CLEAR,
            source = "test-provider",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.95,
            uncertainty = 0.05
        )
        
        val result = validator.validateWeatherObservation(observation)
        
        assert(result.hasWarnings) { "High temperature should generate warning" }
    }
    
    @Test
    fun `test negative precipitation validation`() {
        val observation = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 25.0,
            apparentTemperatureCelsius = 27.0,
            windSpeedMps = 3.0,
            windDirectionDegrees = 180.0,
            precipitationMm = -1.0, // Negative precipitation
            precipitationProbability = 0.1,
            humidityPercentage = 60.0,
            pressureHpa = 1013.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 20.0,
            condition = WeatherCondition.CLEAR,
            source = "test-provider",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.95,
            uncertainty = 0.05
        )
        
        val result = validator.validateWeatherObservation(observation)
        
        assert(result.hasErrors) { "Negative precipitation should generate error" }
    }
    
    @Test
    fun `test invalid coordinate validation`() {
        val observation = WeatherObservation(
            coordinate = GeoCoordinate(200.0, 51.3890), // Invalid latitude
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
            source = "test-provider",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.95,
            uncertainty = 0.05
        )
        
        val result = validator.validateWeatherObservation(observation)
        
        assert(result.hasErrors) { "Invalid coordinate should generate error" }
    }
}