package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.TrafficClass
import com.evnavigator.iran.core.weather.model.TrafficIncident
import com.evnavigator.iran.core.weather.model.TrafficObservation
import com.evnavigator.iran.core.weather.model.TravelTimeEstimate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class TrafficRouteIntegrationTest {
    
    private val trafficRouteIntegration = TrafficRouteIntegration()
    
    @Test
    fun `test mapTrafficToRouteSegments with no traffic`() {
        // Given
        val segments = listOf(RouteSegment(id = "1", distance = 1000.0, duration = 120.0))
        val trafficObservations = emptyList<TrafficObservation>()
        
        // When
        val result = trafficRouteIntegration.mapTrafficToRouteSegments(segments, trafficObservations)
        
        // Then
        assertEquals(1, result.size)
        val segmentData = result[segments[0]]
        assertNotNull(segmentData)
        assertEquals(TrafficClass.UNKNOWN, segmentData!!.trafficClass)
        assertNull(segmentData.speed)
        assertNull(segmentData.freeFlowSpeed)
        assertNull(segmentData.congestionRatio)
        assertNull(segmentData.travelTimeMultiplier)
        assertTrue(segmentData.incidents.isEmpty())
        assertEquals(0.0, segmentData.confidence)
        assertEquals(1.0, segmentData.uncertainty)
        assertEquals(Freshness.UNKNOWN, segmentData.freshness)
    }
    
    @Test
    fun `test mapTrafficToRouteSegments with traffic data`() {
        // Given
        val segments = listOf(RouteSegment(id = "1", distance = 1000.0, duration = 120.0))
        val trafficObservations = listOf(
            TrafficObservation(
                coordinate = null,
                timestamp = Instant.now(),
                speed = 15.0, // m/s
                freeFlowSpeed = 25.0, // m/s
                congestionRatio = 0.4,
                trafficClass = TrafficClass.HEAVY,
                travelTimeMultiplier = 1.67,
                incidents = emptyList(),
                confidence = 0.9,
                freshness = Freshness.FRESH
            )
        )
        
        // When
        val result = trafficRouteIntegration.mapTrafficToRouteSegments(segments, trafficObservations)
        
        // Then
        assertEquals(1, result.size)
        val segmentData = result[segments[0]]
        assertNotNull(segmentData)
        assertEquals(TrafficClass.HEAVY, segmentData!!.trafficClass)
        assertEquals(15.0, segmentData.speed)
        assertEquals(25.0, segmentData.freeFlowSpeed)
        assertEquals(0.4, segmentData.congestionRatio)
        assertEquals(1.67, segmentData.travelTimeMultiplier)
        assertTrue(segmentData.incidents.isEmpty())
        assertEquals(0.9, segmentData.confidence)
        assertEquals(0.1, segmentData.uncertainty) // Assuming default uncertainty
        assertEquals(Freshness.FRESH, segmentData.freshness)
    }
    
    @Test
    fun `test calculateTravelTimeWithTraffic`() {
        // Given
        val segments = listOf(
            RouteSegment(id = "1", distance = 1000.0, duration = 120.0),
            RouteSegment(id = "2", distance = 1000.0, duration = 180.0)
        )
        val baseTravelTime = 300.0 // seconds
        
        // When no traffic observations are provided, confidence must be 0.0 (never fabricated 0.85)
        val unknownResult = trafficRouteIntegration.calculateTravelTimeWithTraffic(segments, baseTravelTime)
        assertNotNull(unknownResult)
        assertEquals(baseTravelTime, unknownResult.baseDuration)
        assertEquals(baseTravelTime, unknownResult.trafficAdjustedDuration)
        assertEquals(0.0, unknownResult.confidence)
        assertEquals(1.0, unknownResult.uncertainty)
        assertEquals(Freshness.UNKNOWN, unknownResult.freshness)

        // When verified traffic observations are provided, confidence reflects actual observations
        val liveObservations = listOf(
            TrafficObservation(
                coordinate = null,
                timestamp = Instant.now(),
                speed = 15.0,
                freeFlowSpeed = 25.0,
                congestionRatio = 0.4,
                trafficClass = TrafficClass.HEAVY,
                travelTimeMultiplier = 1.5,
                incidents = emptyList(),
                confidence = 0.85,
                freshness = Freshness.FRESH
            )
        )
        val verifiedResult = trafficRouteIntegration.calculateTravelTimeWithTraffic(segments, baseTravelTime, liveObservations)
        assertTrue(verifiedResult.trafficAdjustedDuration > baseTravelTime)
        assertEquals(0.85, verifiedResult.confidence, 1e-4)
        assertEquals(0.15, verifiedResult.uncertainty, 1e-4)
    }
    
    @Test
    fun `test processTrafficForSegment with multiple observations`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val trafficObservations = listOf(
            TrafficObservation(
                coordinate = null,
                timestamp = Instant.now(),
                speed = 15.0,
                freeFlowSpeed = 25.0,
                congestionRatio = 0.4,
                trafficClass = TrafficClass.HEAVY,
                travelTimeMultiplier = 1.67,
                incidents = emptyList(),
                confidence = 0.9,
                freshness = Freshness.FRESH
            ),
            TrafficObservation(
                coordinate = null,
                timestamp = Instant.now(),
                speed = 20.0,
                freeFlowSpeed = 30.0,
                congestionRatio = 0.33,
                trafficClass = TrafficClass.MODERATE,
                travelTimeMultiplier = 1.5,
                incidents = emptyList(),
                confidence = 0.8,
                freshness = Freshness.FRESH
            )
        )
        
        // When
        val result = trafficRouteIntegration.processTrafficForSegment(segment, trafficObservations)
        
        // Then
        assertNotNull(result)
        assertEquals(0.85, result.confidence) // Average of 0.9 and 0.8
        assertEquals(0.15, result.uncertainty) // Default uncertainty
    }
}