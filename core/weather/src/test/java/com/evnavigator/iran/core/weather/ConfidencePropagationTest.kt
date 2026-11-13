package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.model.WeatherCondition
import com.evnavigator.iran.core.weather.model.TrafficClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class ConfidencePropagationTest {
    
    private val confidencePropagation = ConfidencePropagation()
    
    @Test
    fun `test propagateConfidence with fresh data`() {
        // When
        val result = confidencePropagation.propagateConfidence(
            sourceConfidence = 0.9,
            sourceFreshness = Freshness.FRESH,
            reliabilityFactor = 1.0
        )
        
        // Then
        assertEquals(0.9, result)
    }
    
    @Test
    fun `test propagateConfidence with stale data`() {
        // When
        val result = confidencePropagation.propagateConfidence(
            sourceConfidence = 0.9,
            sourceFreshness = Freshness.STALE_BUT_USABLE,
            reliabilityFactor = 1.0
        )
        
        // Then
        assertEquals(0.72, result) // 0.9 * 0.8
    }
    
    @Test
    fun `test propagateConfidence with low reliability`() {
        // When
        val result = confidencePropagation.propagateConfidence(
            sourceConfidence = 0.9,
            sourceFreshness = Freshness.FRESH,
            reliabilityFactor = 0.7
        )
        
        // Then
        assertEquals(0.63, result) // 0.9 * 1.0 * 0.7
    }
    
    @Test
    fun `test propagateUncertainty with fresh data`() {
        // When
        val result = confidencePropagation.propagateUncertainty(
            sourceUncertainty = 0.1,
            sourceFreshness = Freshness.FRESH,
            processingUncertainty = 0.05
        )
        
        // Then
        assertEquals(0.15, result) // 0.1 * 1.0 + 0.05
    }
    
    @Test
    fun `test propagateUncertainty with expired data`() {
        // When
        val result = confidencePropagation.propagateUncertainty(
            sourceUncertainty = 0.1,
            sourceFreshness = Freshness.EXPIRED,
            processingUncertainty = 0.05
        )
        
        // Then
        assertEquals(0.25, result) // 0.1 * 2.0 + 0.05
    }
    
    @Test
    fun `test combineConfidence`() {
        // When
        val result = confidencePropagation.combineConfidence(
            confidences = listOf(0.9, 0.8),
            freshnessStates = listOf(Freshness.FRESH, Freshness.STALE_BUT_USABLE)
        )
        
        // Then
        assertEquals(0.85, result) // (0.9 * 1.0 + 0.8 * 0.8) / 2 = (0.9 + 0.64) / 2 = 0.77
    }
    
    @Test
    fun `test combineUncertainty`() {
        // When
        val result = confidencePropagation.combineUncertainty(
            uncertainties = listOf(0.1, 0.2),
            freshnessStates = listOf(Freshness.FRESH, Freshness.STALE_BUT_USABLE)
        )
        
        // Then
        assertEquals(0.15, result) // (0.1 * 1.0 + 0.2 * 1.2) / 2 = (0.1 + 0.24) / 2 = 0.17
    }
    
    @Test
    fun `test processEnvironmentalData`() {
        // Given
        val sourceData = EnvironmentalDataWithConfidence(
            data = "Test Data",
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            provenance = "Original Source"
        )
        
        // When
        val result = confidencePropagation.processEnvironmentalData(
            sourceData = sourceData,
            processingStepDescription = "Processing Step",
            reliabilityFactor = 0.9,
            additionalProcessingUncertainty = 0.02
        )
        
        // Then
        assertEquals("Original Source -> Processing Step", result.provenance)
        assertEquals(0.81, result.confidence) // 0.9 * 1.0 * 0.9
        assertEquals(0.12, result.uncertainty) // 0.1 * 1.0 + 0.02
    }
    
    @Test
    fun `test combineWeatherAndTrafficConfidence`() {
        // When
        val result = confidencePropagation.combineWeatherAndTrafficConfidence(
            weatherConfidence = 0.9,
            weatherUncertainty = 0.1,
            trafficConfidence = 0.8,
            trafficUncertainty = 0.2,
            weatherFreshness = Freshness.FRESH,
            trafficFreshness = Freshness.STALE_BUT_USABLE
        )
        
        // Then
        assertEquals(0.85, result.confidence) // (0.9 * 1.0 + 0.8 * 0.8) / 2 = 0.85
        assertEquals(0.15, result.uncertainty) // (0.1 * 1.0 + 0.2 * 1.2) / 2 = 0.17
    }
    
    @Test
    fun `test ConfidenceUtils validate methods`() {
        assertTrue(ConfidenceUtils.validateConfidence(0.5))
        assertFalse(ConfidenceUtils.validateConfidence(-0.1))
        assertFalse(ConfidenceUtils.validateConfidence(1.1))
        
        assertTrue(ConfidenceUtils.validateUncertainty(0.5))
        assertFalse(ConfidenceUtils.validateUncertainty(-0.1))
        assertFalse(ConfidenceUtils.validateUncertainty(1.1))
    }
    
    @Test
    fun `test ConfidenceUtils normalize methods`() {
        assertEquals(0.0, ConfidenceUtils.normalizeConfidence(-0.5))
        assertEquals(1.0, ConfidenceUtils.normalizeConfidence(1.5))
        assertEquals(0.5, ConfidenceUtils.normalizeConfidence(0.5))
        
        assertEquals(0.0, ConfidenceUtils.normalizeUncertainty(-0.5))
        assertEquals(1.0, ConfidenceUtils.normalizeUncertainty(1.5))
        assertEquals(0.5, ConfidenceUtils.normalizeUncertainty(0.5))
    }
    
    @Test
    fun `test EnvironmentalDataWithConfidence unknown`() {
        // When
        val result = ConfidenceUncertaintyPair.unknown()
        
        // Then
        assertEquals(0.0, result.confidence)
        assertEquals(1.0, result.uncertainty)
    }
    
    @Test
    fun `test ConfidenceQualityModel calculateProviderConfidence`() {
        // When
        val qualityModel = ConfidenceQualityModel()
        val result = qualityModel.calculateProviderConfidence(
            providerReliability = 0.9,
            dataFreshness = Freshness.FRESH,
            validationAccuracy = 0.8
        )
        
        // Then
        assertEquals(0.72, result) // 0.9 * 0.8 * 1.0
    }
    
    @Test
    fun `test ConfidenceQualityModel calculateProviderUncertainty`() {
        // When
        val qualityModel = ConfidenceQualityModel()
        val result = qualityModel.calculateProviderUncertainty(
            providerReliability = 0.7,
            dataFreshness = Freshness.EXPIRED,
            validationAccuracy = 0.9
        )
        
        // Then
        assertEquals(0.63, result) // (1.0 - 0.7) * (1.0 - 0.9) * 2.0 = 0.3 * 0.1 * 2.0 = 0.06
    }
}