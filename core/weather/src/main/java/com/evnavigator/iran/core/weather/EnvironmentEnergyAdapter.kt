package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Instant

data class EnergyConsumptionModifier(
    val name: String,
    val description: String,
    val factor: Double,
    val uncertainty: Double,
    val source: String,
    val provenance: String
)

data class VehicleProfile(
    val id: String = "",
    val massKg: Double = 0.0,
    val aerodynamicDragCoefficient: Double? = 0.3,
    val rollingResistanceCoefficient: Double = 0.01,
    val batteryCapacityKwh: Double = 60.0,
    val supportsHvac: Boolean = true,
    val hvacPowerWatts: Double = 1500.0
)


/**
 * Adapter for integrating weather data with the energy consumption engine.
 * 
 * This class provides the necessary interface to convert environmental conditions
 * into modifiers that can be applied to energy consumption calculations.
 */
class EnvironmentEnergyAdapter {
    
    /**
     * Converts weather conditions into energy consumption modifiers.
     * 
     * @param weather The current weather conditions
     * @param vehicleProfile The vehicle profile for which to calculate modifiers
     * @return List of energy consumption modifiers based on environmental conditions
     */
    fun createWeatherModifiers(
        weather: WeatherObservation,
        vehicleProfile: VehicleProfile
    ): List<EnergyConsumptionModifier> {
        val modifiers = mutableListOf<EnergyConsumptionModifier>()
        
        // Temperature effect (if supported by vehicle profile)
        weather.temperatureCelsius?.let { temp ->
            if (temp != 0.0) { // Only apply if temperature is known and not zero
                val tempModifier = calculateTemperatureModifier(temp, vehicleProfile)
                if (tempModifier != null) {
                    modifiers.add(tempModifier)
                }
            }
        }
        
        // Wind effect (if supported by vehicle profile)
        weather.windSpeedMps?.let { wind ->
            if (wind > 0.0) { // Only apply if wind is known and positive
                val windModifier = calculateWindModifier(wind, weather.windDirectionDegrees, vehicleProfile)
                if (windModifier != null) {
                    modifiers.add(windModifier)
                }
            }
        }
        
        // Precipitation effect (if supported by vehicle profile)
        weather.precipitationMm?.let { precip ->
            if (precip > 0.0) { // Only apply if precipitation is known and positive
                val precipModifier = calculatePrecipitationModifier(precip, weather.condition, vehicleProfile)
                if (precipModifier != null) {
                    modifiers.add(precipModifier)
                }
            }
        }
        
        return modifiers
    }
    
    /**
     * Calculates temperature effect on energy consumption.
     * 
     * @param temperatureCelsius The ambient temperature in Celsius
     * @param vehicleProfile The vehicle profile
     * @return Temperature modifier or null if not applicable
     */
    private fun calculateTemperatureModifier(
        temperatureCelsius: Double,
        vehicleProfile: VehicleProfile
    ): EnergyConsumptionModifier? {
        // Check if this vehicle supports HVAC energy consumption
        if (!vehicleProfile.supportsHvac) {
            return null // No HVAC, so no temperature effect on energy consumption
        }
        
        // Calculate HVAC load based on temperature difference from comfortable range
        val comfortableTemp = 22.0 // Assume comfortable indoor temperature is 22°C
        val tempDiff = Math.abs(temperatureCelsius - comfortableTemp)
        
        // Simple linear model for HVAC effect (this would be more complex in real implementation)
        val hvacEffect = when {
            tempDiff < 5.0 -> 0.0 // Minimal HVAC usage
            tempDiff < 10.0 -> 0.05 // Moderate HVAC usage
            tempDiff < 15.0 -> 0.15 // High HVAC usage  
            else -> 0.30 // Very high HVAC usage
        }
        
        return EnergyConsumptionModifier(
            name = "temperature_hvac",
            description = "HVAC demand due to temperature difference",
            factor = 1.0 + hvacEffect,
            uncertainty = 0.1, // 10% uncertainty in HVAC effect calculation
            source = "weather",
            provenance = "temperature=$temperatureCelsius°C"
        )
    }
    
    /**
     * Calculates wind effect on energy consumption.
     * 
     * @param windSpeedMps The wind speed in meters per second
     * @param windDirectionDegrees The wind direction in degrees
     * @param vehicleProfile The vehicle profile
     * @return Wind modifier or null if not applicable
     */
    private fun calculateWindModifier(
        windSpeedMps: Double,
        windDirectionDegrees: Double?,
        vehicleProfile: VehicleProfile
    ): EnergyConsumptionModifier? {
        // Check if this vehicle supports aerodynamic drag calculation
        if (vehicleProfile.aerodynamicDragCoefficient == null) {
            return null // No aerodynamic data, so no wind effect
        }
        
        // Wind effect is based on relative wind speed and direction to vehicle movement
        // For simplicity, we'll use a basic model where wind increases drag
        val windEffect = windSpeedMps * 0.01 // Simple linear relationship
        
        return EnergyConsumptionModifier(
            name = "wind_drag",
            description = "Aerodynamic drag due to wind conditions",
            factor = 1.0 + windEffect,
            uncertainty = 0.15, // 15% uncertainty in wind effect calculation
            source = "weather",
            provenance = "windSpeed=${windSpeedMps}m/s"
        )
    }
    
    /**
     * Calculates precipitation effect on energy consumption.
     * 
     * @param precipitationMm The precipitation amount in millimeters
     * @param condition The weather condition
     * @param vehicleProfile The vehicle profile
     * @return Precipitation modifier or null if not applicable
     */
    private fun calculatePrecipitationModifier(
        precipitationMm: Double,
        condition: WeatherCondition,
        vehicleProfile: VehicleProfile
    ): EnergyConsumptionModifier? {
        val precipEffect = when (condition) {
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.MODERATE_RAIN -> 0.02
            
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.SLEET -> 0.05
            
            WeatherCondition.LIGHT_SNOW,
            WeatherCondition.MODERATE_SNOW -> 0.01
            
            WeatherCondition.HEAVY_SNOW -> 0.03
            
            else -> 0.0 // No precipitation effect
        }
        
        return EnergyConsumptionModifier(
            name = "precipitation_rolling",
            description = "Rolling resistance increase due to precipitation",
            factor = 1.0 + precipEffect,
            uncertainty = 0.2, // 20% uncertainty in precipitation effect calculation
            source = "weather",
            provenance = "precipitation=${precipitationMm}mm, condition=$condition"
        )
    }
    
    /**
     * Combines multiple weather effects into a single energy consumption modifier.
     * 
     * @param modifiers List of individual modifiers from different environmental factors
     * @return Combined modifier or null if no applicable modifiers exist
     */
    fun combineWeatherModifiers(modifiers: List<EnergyConsumptionModifier>): EnergyConsumptionModifier? {
        if (modifiers.isEmpty()) return null
        
        // Calculate combined factor as product of all factors
        val combinedFactor = modifiers.fold(1.0) { acc: Double, modifier -> acc * modifier.factor }
        
        // Combine uncertainties using root sum square method
        val combinedUncertainty = Math.sqrt(
            modifiers.fold(0.0) { acc: Double, modifier -> 
                acc + (modifier.uncertainty * modifier.uncertainty) 
            }
        )
        
        // Create a combined modifier with all sources
        val sourceList = modifiers.joinToString(", ") { it.source }
        val provenanceList = modifiers.joinToString("; ") { it.provenance }
        
        return EnergyConsumptionModifier(
            name = "combined_environmental",
            description = "Combined environmental effects on energy consumption",
            factor = combinedFactor,
            uncertainty = combinedUncertainty,
            source = sourceList,
            provenance = provenanceList
        )
    }
    
    /**
     * Creates an environmental context for route segments.
     * 
     * @param routeSegments Route segments with associated weather data
     * @param vehicleProfile Vehicle profile for which to calculate modifiers
     * @return List of environmental contexts for each segment
     */
    fun createRouteEnvironmentalContext(
        routeSegments: List<RouteSegmentWeather>,
        vehicleProfile: VehicleProfile
    ): List<EnvironmentalSegmentContext> {
        return routeSegments.map { segment ->
            val modifiers = if (segment.weather != null) {
                createWeatherModifiers(segment.weather, vehicleProfile)
            } else {
                emptyList()
            }
            
            EnvironmentalSegmentContext(
                startCoordinate = segment.startCoordinate,
                endCoordinate = segment.endCoordinate,
                startTime = segment.startTime,
                endTime = segment.endTime,
                weather = segment.weather,
                modifiers = modifiers
            )
        }
    }
}

/**
 * Contextual information for a route segment with environmental data and modifiers.
 */
data class EnvironmentalSegmentContext(
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val startTime: Instant,
    val endTime: Instant,
    val weather: WeatherObservation?,
    val modifiers: List<EnergyConsumptionModifier>
)
