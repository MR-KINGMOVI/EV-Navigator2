package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate


import com.evnavigator.iran.core.weather.model.*
import org.junit.Test
import java.time.Instant

class EnvironmentEnergyAdapterTest {
    
    private val adapter = EnvironmentEnergyAdapter()
    
    @Test
    fun `test temperature modifier calculation`() {
        // Create a weather observation with moderate temperature
        val weather = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 10.0,
            apparentTemperatureCelsius = 8.0,
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
        
        // Create a vehicle profile that supports HVAC
        val vehicleProfile = VehicleProfile(
            id = "test-vehicle",
            massKg = 2000.0,
            aerodynamicDragCoefficient = 0.23,
            rollingResistanceCoefficient = 0.01,
            batteryCapacityKwh = 60.0,
            supportsHvac = true,
            hvacPowerWatts = 1500.0
        )
        
        // Test the temperature modifier calculation
        val modifiers = adapter.createWeatherModifiers(weather, vehicleProfile)
        
        // Should have at least one modifier (HVAC effect)
        assert(modifiers.isNotEmpty()) { "Should have HVAC modifier" }
        
        // Find the HVAC modifier
        val hvacModifier = modifiers.find { it.name == "temperature_hvac" }
        assert(hvacModifier != null) { "Should find HVAC modifier" }
        
        // Should have a factor greater than 1.0 (increased consumption)
        assert(hvacModifier!!.factor > 1.0) { "HVAC should increase consumption" }
    }
    
    @Test
    fun `test wind modifier calculation`() {
        // Create a weather observation with wind
        val weather = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 20.0,
            apparentTemperatureCelsius = 20.0,
            windSpeedMps = 5.0,
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
        
        // Create a vehicle profile with aerodynamic data
        val vehicleProfile = VehicleProfile(
            id = "test-vehicle",
            massKg = 2000.0,
            aerodynamicDragCoefficient = 0.23,
            rollingResistanceCoefficient = 0.01,
            batteryCapacityKwh = 60.0,
            supportsHvac = false, // No HVAC
            hvacPowerWatts = 1500.0
        )
        
        // Test the wind modifier calculation
        val modifiers = adapter.createWeatherModifiers(weather, vehicleProfile)
        
        // Should have a wind modifier since vehicle has aerodynamic data
        assert(modifiers.isNotEmpty()) { "Should have wind modifier" }
        
        // Find the wind modifier
        val windModifier = modifiers.find { it.name == "wind_drag" }
        assert(windModifier != null) { "Should find wind modifier" }
        
        // Should have a factor greater than 1.0 (increased drag)
        assert(windModifier!!.factor > 1.0) { "Wind should increase drag" }
    }
    
    @Test
    fun `test precipitation modifier calculation`() {
        // Create a weather observation with light rain
        val weather = WeatherObservation(
            coordinate = GeoCoordinate(35.6895, 51.3890),
            timestamp = Instant.now(),
            temperatureCelsius = 15.0,
            apparentTemperatureCelsius = 14.0,
            windSpeedMps = 2.0,
            windDirectionDegrees = 180.0,
            precipitationMm = 2.0,
            precipitationProbability = 0.8,
            humidityPercentage = 80.0,
            pressureHpa = 1013.0,
            visibilityMeters = 5000.0,
            cloudCoverPercentage = 70.0,
            condition = WeatherCondition.LIGHT_RAIN,
            source = "test-provider",
            retrievedAt = Instant.now(),
            freshness = WeatherFreshness.FRESH,
            confidence = 0.90,
            uncertainty = 0.1
        )
        
        // Create a vehicle profile (no special precipitation handling)
        val vehicleProfile = VehicleProfile(
            id = "test-vehicle",
            massKg = 2000.0,
            aerodynamicDragCoefficient = 0.23,
            rollingResistanceCoefficient = 0.01,
            batteryCapacityKwh = 60.0,
            supportsHvac = false,
            hvacPowerWatts = 1500.0
        )
        
        // Test the precipitation modifier calculation
        val modifiers = adapter.createWeatherModifiers(weather, vehicleProfile)
        
        // Should have at least one modifier (precipitation effect)
        assert(modifiers.isNotEmpty()) { "Should have precipitation modifier" }
        
        // Find the precipitation modifier
        val precipModifier = modifiers.find { it.name == "precipitation_rolling" }
        assert(precipModifier != null) { "Should find precipitation modifier" }
        
        // Should have a factor greater than 1.0 (increased rolling resistance)
        assert(precipModifier!!.factor > 1.0) { "Precipitation should increase rolling resistance" }
    }
    
    @Test
    fun `test combine modifiers`() {
        // Create test modifiers
        val modifiers = listOf(
            EnergyConsumptionModifier(
                name = "modifier1",
                description = "Test modifier 1",
                factor = 1.05,
                uncertainty = 0.05,
                source = "test1",
                provenance = "test1"
            ),
            EnergyConsumptionModifier(
                name = "modifier2", 
                description = "Test modifier 2",
                factor = 1.10,
                uncertainty = 0.08,
                source = "test2",
                provenance = "test2"
            )
        )
        
        // Combine the modifiers
        val combined = adapter.combineWeatherModifiers(modifiers)
        
        // Should have a combined modifier
        assert(combined != null) { "Should have combined modifier" }
        
        // Combined factor should be product of individual factors (1.05 * 1.10 = 1.155)
        assert(Math.abs(combined!!.factor - 1.155) < 0.001) { "Combined factor should be correct" }
    }
}