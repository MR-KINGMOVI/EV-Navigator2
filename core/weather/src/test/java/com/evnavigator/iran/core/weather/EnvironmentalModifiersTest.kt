package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.model.WeatherCondition
import com.evnavigator.iran.core.weather.model.TrafficClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class EnvironmentalModifiersTest {
    
    @Test
    fun `test EnvironmentalModifiers empty`() {
        // When
        val result = EnvironmentalModifiers.empty()
        
        // Then
        assertNotNull(result)
        assertNull(result.temperatureModifier)
        assertNull(result.windModifier)
        assertNull(result.precipitationModifier)
        assertNull(result.roadConditionModifier)
        assertNull(result.trafficModifier)
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
        assertEquals(Freshness.UNKNOWN, result.freshness)
        assertEquals("Empty", result.provenance)
    }
    
    @Test
    fun `test EnvironmentalModifiers fromWeather with temperature`() {
        // When
        val result = EnvironmentalModifiers.fromWeather(
            temperature = -5.0,
            windSpeed = null,
            precipitationAmount = null,
            condition = null,
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH
        )
        
        // Then
        assertNotNull(result)
        assertEquals(1.2, result.temperatureModifier) // Cold weather modifier
        assertNull(result.windModifier)
        assertNull(result.precipitationModifier)
        assertNull(result.roadConditionModifier)
        assertNull(result.trafficModifier)
        assertEquals(0.9, result.confidence)
        assertEquals(0.1, result.uncertainty)
        assertEquals(Freshness.FRESH, result.freshness)
        assertEquals("WeatherBasedModifiers", result.provenance)
    }
    
    @Test
    fun `test EnvironmentalModifiers fromWeather with wind`() {
        // When
        val result = EnvironmentalModifiers.fromWeather(
            temperature = null,
            windSpeed = 15.0,
            precipitationAmount = null,
            condition = null,
            confidence = 0.8,
            uncertainty = 0.2,
            freshness = Freshness.STALE_BUT_USABLE
        )
        
        // Then
        assertNotNull(result)
        assertNull(result.temperatureModifier)
        assertEquals(1.15, result.windModifier) // Wind modifier (1.05 + 15/100)
        assertNull(result.precipitationModifier)
        assertNull(result.roadConditionModifier)
        assertNull(result.trafficModifier)
        assertEquals(0.8, result.confidence)
        assertEquals(0.2, result.uncertainty)
        assertEquals(Freshness.STALE_BUT_USABLE, result.freshness)
    }
    
    @Test
    fun `test EnvironmentalModifiers fromWeather with precipitation`() {
        // When
        val result = EnvironmentalModifiers.fromWeather(
            temperature = null,
            windSpeed = null,
            precipitationAmount = 3.0,
            condition = WeatherCondition.RAIN,
            confidence = 0.7,
            uncertainty = 0.3,
            freshness = Freshness.EXPIRED
        )
        
        // Then
        assertNotNull(result)
        assertNull(result.temperatureModifier)
        assertNull(result.windModifier)
        assertEquals(1.1, result.precipitationModifier) // Light rain modifier
        assertEquals(1.1, result.roadConditionModifier) // Rain condition modifier
        assertNull(result.trafficModifier)
        assertEquals(0.7, result.confidence)
        assertEquals(0.3, result.uncertainty)
        assertEquals(Freshness.EXPIRED, result.freshness)
    }
    
    @Test
    fun `test EnvironmentalModifiers fromTraffic`() {
        // When
        val result = EnvironmentalModifiers.fromTraffic(
            trafficClass = TrafficClass.HEAVY,
            congestionRatio = null,
            confidence = 0.85,
            uncertainty = 0.15,
            freshness = Freshness.FRESH
        )
        
        // Then
        assertNotNull(result)
        assertNull(result.temperatureModifier)
        assertNull(result.windModifier)
        assertNull(result.precipitationModifier)
        assertNull(result.roadConditionModifier)
        assertEquals(1.1, result.trafficModifier) // Heavy traffic modifier
        assertEquals(0.85, result.confidence)
        assertEquals(0.15, result.uncertainty)
        assertEquals(Freshness.FRESH, result.freshness)
    }
    
    @Test
    fun `test combineEnvironmentalModifiers with both`() {
        // Given
        val weatherModifiers = EnvironmentalModifiers(
            temperatureModifier = 1.2,
            windModifier = 1.1,
            precipitationModifier = null,
            roadConditionModifier = null,
            trafficModifier = null,
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            provenance = "Weather"
        )
        
        val trafficModifiers = EnvironmentalModifiers(
            temperatureModifier = null,
            windModifier = null,
            precipitationModifier = null,
            roadConditionModifier = null,
            trafficModifier = 1.15,
            confidence = 0.8,
            uncertainty = 0.2,
            freshness = Freshness.STALE_BUT_USABLE,
            provenance = "Traffic"
        )
        
        // When
        val result = combineEnvironmentalModifiers(weatherModifiers, trafficModifiers)
        
        // Then
        assertNotNull(result)
        assertEquals(1.2, result.temperatureModifier) // From weather only
        assertEquals(1.1, result.windModifier) // From weather only
        assertNull(result.precipitationModifier)
        assertNull(result.roadConditionModifier)
        assertEquals(1.15, result.trafficModifier) // From traffic only
        
        // Combined confidence should be average
        assertEquals(0.85, result.confidence) // (0.9 + 0.8) / 2
        
        // Combined uncertainty should be average  
        assertEquals(0.15, result.uncertainty) // (0.1 + 0.2) / 2
        
        // Freshness should be the fresher of the two
        assertEquals(Freshness.FRESH, result.freshness)
    }
    
    @Test
    fun `test combineEnvironmentalModifiers with one null`() {
        // Given
        val weatherModifiers = EnvironmentalModifiers(
            temperatureModifier = 1.2,
            windModifier = 1.1,
            precipitationModifier = null,
            roadConditionModifier = null,
            trafficModifier = null,
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            provenance = "Weather"
        )
        
        // When
        val result = combineEnvironmentalModifiers(weatherModifiers, null)
        
        // Then
        assertNotNull(result)
        assertEquals(1.2, result.temperatureModifier)
        assertEquals(1.1, result.windModifier)
        assertNull(result.precipitationModifier)
        assertNull(result.roadConditionModifier)
        assertNull(result.trafficModifier)
        assertEquals(0.9, result.confidence)
        assertEquals(0.1, result.uncertainty)
        assertEquals(Freshness.FRESH, result.freshness)
    }
    
    @Test
    fun `test applyEnvironmentalModifiers`() {
        // Given
        val baseConsumption = 100.0
        val modifiers = EnvironmentalModifiers(
            temperatureModifier = 1.2,
            windModifier = 1.1,
            precipitationModifier = 1.05,
            roadConditionModifier = 1.1,
            trafficModifier = 1.15,
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            provenance = "Test"
        )
        
        // When
        val result = applyEnvironmentalModifiers(baseConsumption, modifiers)
        
        // Then
        assertEquals(100.0 * 1.2 * 1.1 * 1.05 * 1.1 * 1.15, result)
    }
    
    @Test
    fun `test applyEnvironmentalModifiers with null modifiers`() {
        // Given
        val baseConsumption = 100.0
        
        // When
        val result = applyEnvironmentalModifiers(baseConsumption, null)
        
        // Then
        assertEquals(baseConsumption, result)
    }
    
    @Test
    fun `test calculateCombinedConfidence`() {
        // Test both present
        assertEquals(0.85, calculateCombinedConfidence(0.9, 0.8))
        
        // Test one null
        assertEquals(0.9, calculateCombinedConfidence(0.9, null))
        assertEquals(0.8, calculateCombinedConfidence(null, 0.8))
        
        // Test both null
        assertEquals(0.0, calculateCombinedConfidence(null, null))
    }
    
    @Test
    fun `test calculateCombinedUncertainty`() {
        // Test both present
        assertEquals(0.15, calculateCombinedUncertainty(0.2, 0.1))
        
        // Test one null
        assertEquals(0.2, calculateCombinedUncertainty(0.2, null))
        assertEquals(0.1, calculateCombinedUncertainty(null, 0.1))
        
        // Test both null
        assertEquals(1.0, calculateCombinedUncertainty(null, null))
    }
}