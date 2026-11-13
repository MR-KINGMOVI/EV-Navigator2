package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class TrafficTravelTimeModelTest {
    
    private val travelTimeModel = TrafficTravelTimeModel()
    
    @Test
    fun `test calculateTravelTimeWithTraffic with empty segments`() {
        // When
        val result = travelTimeModel.calculateTravelTimeWithTraffic(
            segments = emptyList(),
            trafficData = emptyMap(),
            baseTravelTime = 300.0
        )
        
        // Then
        assertNotNull(result)
        assertEquals(0.0, result.baseDuration)
        assertEquals(0.0, result.trafficAdjustedDuration)
        assertEquals(0.0, result.delay)
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
        assertEquals("EmptyRoute", result.provenance)
    }
    
    @Test
    fun `test calculateTravelTimeWithTraffic with valid data`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val segments = listOf(segment)
        
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
        
        // When
        val result = travelTimeModel.calculateTravelTimeWithTraffic(
            segments = segments,
            trafficData = trafficData,
            baseTravelTime = 120.0
        )
        
        // Then
        assertNotNull(result)
        assertEquals(120.0, result.baseDuration) // Base duration
        assertEquals(200.4, result.trafficAdjustedDuration) // 120 * 1.67
        assertEquals(80.4, result.delay) // 200.4 - 120
        assertEquals(0.8, result.confidence)
        assertEquals(0.2, result.uncertainty)
        assertEquals("TrafficTravelTimeModel", result.provenance)
    }
    
    @Test
    fun `test calculateTravelTimeWithTraffic with no traffic data`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val segments = listOf(segment)
        
        // When
        val result = travelTimeModel.calculateTravelTimeWithTraffic(
            segments = segments,
            trafficData = emptyMap(),
            baseTravelTime = 120.0
        )
        
        // Then
        assertNotNull(result)
        assertEquals(120.0, result.baseDuration)
        assertEquals(120.0, result.trafficAdjustedDuration)
        assertEquals(0.0, result.delay)
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
    }
    
    @Test
    fun `test calculateSegmentTravelTime with valid multiplier`() {
        // When
        val result = travelTimeModel.calculateSegmentTravelTime(
            baseDuration = 120.0,
            trafficMultiplier = 1.5
        )
        
        // Then
        assertNotNull(result)
        assertEquals(120.0, result.baseDuration)
        assertEquals(180.0, result.trafficAdjustedDuration) // 120 * 1.5
        assertEquals(60.0, result.delay) // 180 - 120
        assertEquals(0.8, result.confidence)
        assertEquals(0.2, result.uncertainty)
    }
    
    @Test
    fun `test calculateSegmentTravelTime with null multiplier`() {
        // When
        val result = travelTimeModel.calculateSegmentTravelTime(
            baseDuration = 120.0,
            trafficMultiplier = null
        )
        
        // Then
        assertNotNull(result)
        assertEquals(120.0, result.baseDuration)
        assertEquals(120.0, result.trafficAdjustedDuration)
        assertEquals(0.0, result.delay)
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
    }
    
    @Test
    fun `test analyzeTrafficImpact`() {
        // Given
        val segment = RouteSegment(id = "1", distance = 1000.0, duration = 120.0)
        val segments = listOf(segment)
        
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
        
        // When
        val result = travelTimeModel.analyzeTrafficImpact(
            segments = segments,
            trafficData = trafficData,
            baseTravelTime = 120.0
        )
        
        // Then
        assertNotNull(result)
        assertEquals(120.0, result.totalBaseDuration)
        assertEquals(200.4, result.totalAdjustedDuration)
        assertEquals(80.4, result.totalDelay)
        assertEquals(0.4, result.averageCongestionRatio)
        assertNotNull(result.maxDelaySegment)
        assertEquals("1", result.maxDelaySegment!!.segmentId)
        assertEquals(80.4, result.maxDelaySegment!!.delay)
        assertTrue(result.segmentsWithDelay.isNotEmpty())
        assertEquals(0.85, result.confidence)
        assertEquals(0.15, result.uncertainty)
    }
    
    @Test
    fun `test TravelTimeCalculator calculateFromSpeedAndDistance`() {
        // When
        val result = TravelTimeCalculator.calculateFromSpeedAndDistance(1000.0, 20.0)
        
        // Then
        assertEquals(50.0, result) // 1000 / 20
    }
    
    @Test
    fun `test TravelTimeCalculator calculateFromTrafficMultiplier`() {
        // When
        val result = TravelTimeCalculator.calculateFromTrafficMultiplier(120.0, 1.5)
        
        // Then
        assertEquals(180.0, result) // 120 * 1.5
    }
    
    @Test
    fun `test TravelTimeCalculator calculateTrafficMultiplierFromCongestion`() {
        // When
        val result = TravelTimeCalculator.calculateTrafficMultiplierFromCongestion(0.4)
        
        // Then
        assertEquals(1.8, result) // 1.0 + (0.4 * 2.0)
    }
}