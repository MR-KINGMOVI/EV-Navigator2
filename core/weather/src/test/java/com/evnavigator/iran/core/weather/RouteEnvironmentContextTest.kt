package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class RouteEnvironmentContextTest {
    
    @Test
    fun `test createRouteEnvironmentContext with weather and traffic data`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val segments = listOf(segment)
        
        val weatherContext = WeatherContext(
            observation = WeatherObservation(
                coordinate = null,
                timestamp = Instant.now(),
                temperature = 25.0,
                apparentTemperature = 27.0,
                windSpeed = 3.0,
                windDirection = 180.0,
                precipitationAmount = 0.0,
                precipitationProbability = 0.0,
                humidity = 60.0,
                pressure = 1013.0,
                visibility = 10000.0,
                cloudCoverage = 20.0,
                condition = WeatherCondition.CLEAR,
                source = "TestProvider",
                freshness = Freshness.FRESH,
                confidence = 0.9,
                uncertainty = 0.1
            ),
            forecast = emptyList(),
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            provenance = "Test"
        )
        
        val trafficData = mapOf(
            segment to TrafficDataForSegment(
                trafficClass = TrafficClass.HEAVY,
                speed = 15.0,
                freeFlowSpeed = 25.0,
                congestionRatio = 0.4,
                travelTimeMultiplier = 1.67,
                incidents = emptyList(),
                confidence = 0.8,
                uncertainty = 0.2,
                freshness = Freshness.FRESH
            )
        )
        
        val weatherMap = mapOf(segment to weatherContext)
        
        // When
        val result = createRouteEnvironmentContext(segments, weatherMap, trafficData)
        
        // Then
        assertNotNull(result)
        assertEquals(1, result.segments.size)
        assertEquals(0.85, result.confidence) // Average of 0.9 and 0.8
        assertEquals(0.15, result.uncertainty) // Average of 0.1 and 0.2
        assertEquals(Freshness.FRESH, result.freshness)
        
        val segmentEnv = result.segments[0]
        assertNotNull(segmentEnv.weatherContext)
        assertNotNull(segmentEnv.trafficContext)
        assertEquals(TrafficClass.HEAVY, segmentEnv.trafficContext!!.trafficClass)
        assertEquals(0.85, segmentEnv.confidence) // Average of weather and traffic confidences
        assertEquals(0.15, segmentEnv.uncertainty) // Average of weather and traffic uncertainties
        assertEquals(Freshness.FRESH, segmentEnv.freshness)
    }
    
    @Test
    fun `test createRouteEnvironmentContext with only traffic data`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val segments = listOf(segment)
        
        val trafficData = mapOf(
            segment to TrafficDataForSegment(
                trafficClass = TrafficClass.LIGHT,
                speed = 20.0,
                freeFlowSpeed = 30.0,
                congestionRatio = 0.33,
                travelTimeMultiplier = 1.5,
                incidents = emptyList(),
                confidence = 0.8,
                uncertainty = 0.2,
                freshness = Freshness.STALE_BUT_USABLE
            )
        )
        
        // When
        val result = createRouteEnvironmentContext(segments, null, trafficData)
        
        // Then
        assertNotNull(result)
        assertEquals(1, result.segments.size)
        assertEquals(0.8, result.confidence)
        assertEquals(0.2, result.uncertainty)
        assertEquals(Freshness.STALE_BUT_USABLE, result.freshness)
        
        val segmentEnv = result.segments[0]
        assertNull(segmentEnv.weatherContext)
        assertNotNull(segmentEnv.trafficContext)
        assertEquals(TrafficClass.LIGHT, segmentEnv.trafficContext!!.trafficClass)
        assertEquals(0.8, segmentEnv.confidence)
        assertEquals(0.2, segmentEnv.uncertainty)
        assertEquals(Freshness.STALE_BUT_USABLE, segmentEnv.freshness)
    }
    
    @Test
    fun `test createRouteEnvironmentContext with only weather data`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val segments = listOf(segment)
        
        val weatherContext = WeatherContext(
            observation = WeatherObservation(
                coordinate = null,
                timestamp = Instant.now(),
                temperature = 25.0,
                apparentTemperature = 27.0,
                windSpeed = 3.0,
                windDirection = 180.0,
                precipitationAmount = 0.0,
                precipitationProbability = 0.0,
                humidity = 60.0,
                pressure = 1013.0,
                visibility = 10000.0,
                cloudCoverage = 20.0,
                condition = WeatherCondition.CLEAR,
                source = "TestProvider",
                freshness = Freshness.FRESH,
                confidence = 0.9,
                uncertainty = 0.1
            ),
            forecast = emptyList(),
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            provenance = "Test"
        )
        
        val weatherMap = mapOf(segment to weatherContext)
        
        // When
        val result = createRouteEnvironmentContext(segments, weatherMap, emptyMap())
        
        // Then
        assertNotNull(result)
        assertEquals(1, result.segments.size)
        assertEquals(0.9, result.confidence)
        assertEquals(0.1, result.uncertainty)
        assertEquals(Freshness.FRESH, result.freshness)
        
        val segmentEnv = result.segments[0]
        assertNotNull(segmentEnv.weatherContext)
        assertNull(segmentEnv.trafficContext)
        assertEquals(0.9, segmentEnv.confidence)
        assertEquals(0.1, segmentEnv.uncertainty)
        assertEquals(Freshness.FRESH, segmentEnv.freshness)
    }
    
    @Test
    fun `test RouteEnvironmentContext empty`() {
        // When
        val result = RouteEnvironmentContext.empty()
        
        // Then
        assertNotNull(result)
        assertTrue(result.segments.isEmpty())
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
        assertEquals(Freshness.UNKNOWN, result.freshness)
        assertEquals("Empty", result.provenance)
    }
    
    @Test
    fun `test RouteSegmentEnvironment unknown`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        
        // When
        val result = RouteSegmentEnvironment.unknown(segment)
        
        // Then
        assertNotNull(result)
        assertEquals(segment, result.segment)
        assertNull(result.weatherContext)
        assertNull(result.trafficContext)
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
        assertEquals(Freshness.UNKNOWN, result.freshness)
        assertEquals("Unknown", result.provenance)
    }
}