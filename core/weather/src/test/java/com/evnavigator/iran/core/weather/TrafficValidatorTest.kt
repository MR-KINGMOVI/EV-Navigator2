package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import com.evnavigator.iran.core.weather.validation.TrafficValidator
import org.junit.Test
import java.time.Instant

class TrafficValidatorTest {
    
    private val validator = TrafficValidator()
    
    @Test
    fun `test valid traffic observation validation`() {
        val observation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890), // Tehran coordinates
            timestamp = Instant.now(),
            speedMps = 15.0,
            freeFlowSpeedMps = 25.0,
            congestionRatio = 0.6,
            trafficClass = TrafficClass.MODERATE,
            travelTimeMultiplier = 1.4,
            confidence = 0.90,
            uncertainty = 0.10,
            source = "test-provider",
            retrievedAt = Instant.now()
        )
        
        val result = validator.validateTrafficObservation(observation)
        
        assert(!result.hasErrors) { "Valid observation should not have errors" }
        assert(!result.hasWarnings) { "Valid observation should not have warnings" }
    }
    
    @Test
    fun `test negative speed validation`() {
        val observation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            speedMps = -5.0, // Negative speed
            freeFlowSpeedMps = 25.0,
            congestionRatio = 0.6,
            trafficClass = TrafficClass.MODERATE,
            travelTimeMultiplier = 1.4,
            confidence = 0.90,
            uncertainty = 0.10,
            source = "test-provider",
            retrievedAt = Instant.now()
        )
        
        val result = validator.validateTrafficObservation(observation)
        
        assert(result.hasErrors) { "Negative speed should generate error" }
    }
    
    @Test
    fun `test invalid congestion ratio validation`() {
        val observation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            speedMps = 15.0,
            freeFlowSpeedMps = 25.0,
            congestionRatio = 2.5, // Invalid ratio
            trafficClass = TrafficClass.MODERATE,
            travelTimeMultiplier = 1.4,
            confidence = 0.90,
            uncertainty = 0.10,
            source = "test-provider",
            retrievedAt = Instant.now()
        )
        
        val result = validator.validateTrafficObservation(observation)
        
        assert(result.hasErrors) { "Invalid congestion ratio should generate error" }
    }
    
    @Test
    fun `test invalid traffic class validation`() {
        val observation = TrafficObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            speedMps = 15.0,
            freeFlowSpeedMps = 25.0,
            congestionRatio = 0.6,
            trafficClass = null, // Missing traffic class
            travelTimeMultiplier = 1.4,
            confidence = 0.90,
            uncertainty = 0.10,
            source = "test-provider",
            retrievedAt = Instant.now()
        )
        
        val result = validator.validateTrafficObservation(observation)
        
        assert(result.hasErrors) { "Missing traffic class should generate error" }
    }
}