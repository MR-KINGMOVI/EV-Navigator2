package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataProvenanceTest {
    
    @Test
    fun testCreateWeatherProvenance() = runTest {
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
        
        val provenance = DataProvenance()
        val result = provenance.createWeatherProvenance(
            observation = observation,
            source = "test-source",
            retrievedAt = Instant.now(),
            validFrom = Instant.now(),
            validUntil = Instant.now().plusSeconds(3600)
        )
        
        assertEquals("test-source", result.source)
        assertEquals(observation.confidence, result.confidence)
        assertEquals(observation.uncertainty, result.uncertainty)
    }
    
    @Test
    fun testCreateTrafficProvenance() = runTest {
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
        
        val provenance = DataProvenance()
        val result = provenance.createTrafficProvenance(
            observation = observation,
            source = "test-source",
            retrievedAt = Instant.now(),
            validFrom = Instant.now(),
            validUntil = Instant.now().plusSeconds(3600)
        )
        
        assertEquals("test-source", result.source)
        assertEquals(observation.confidence, result.confidence)
        assertEquals(observation.uncertainty, result.uncertainty)
    }
    
    @Test
    fun testIsValid() = runTest {
        val provenance = DataProvenance()
        val now = Instant.now()
        
        // Create a valid provenance record
        val validProvenance = WeatherProvenance(
            source = "test-source",
            retrievedAt = now,
            validFrom = now.minusSeconds(300),
            validUntil = now.plusSeconds(300),
            schemaVersion = "1.0",
            confidence = 0.9,
            uncertainty = 0.1
        )
        
        // Should be valid at current time
        assertTrue(provenance.isValid(validProvenance, now))
        
        // Should not be valid if in the future
        assertFalse(provenance.isValid(validProvenance, now.plusSeconds(1000)))
        
        // Should not be valid if in the past
        assertFalse(provenance.isValid(validProvenance, now.minusSeconds(1000)))
    }
    
    @Test
    fun testGetFreshnessState() = runTest {
        val provenance = DataProvenance()
        val now = Instant.now()
        
        // Test fresh data (within 30 minutes)
        val freshProvenance = WeatherProvenance(
            source = "test-source",
            retrievedAt = now.minusSeconds(1800), // 30 minutes ago
            validFrom = now.minusSeconds(300),
            validUntil = now.plusSeconds(300),
            schemaVersion = "1.0",
            confidence = 0.9,
            uncertainty = 0.1
        )
        
        val freshness = provenance.getFreshnessState(freshProvenance, now)
        assertEquals(WeatherFreshness.FRESH, freshness)
        
        // Test stale data (within 2 hours)
        val staleProvenance = WeatherProvenance(
            source = "test-source",
            retrievedAt = now.minusSeconds(7200), // 2 hours ago
            validFrom = now.minusSeconds(300),
            validUntil = now.plusSeconds(300),
            schemaVersion = "1.0",
            confidence = 0.9,
            uncertainty = 0.1
        )
        
        val staleFreshness = provenance.getFreshnessState(staleProvenance, now)
        assertEquals(WeatherFreshness.STALE_BUT_USABLE, staleFreshness)
    }
}