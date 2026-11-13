package com.evnavigator.iran.core.weather.validation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Instant

/**
 * Validator for weather data to ensure it meets quality standards and physical constraints.
 */
class WeatherValidator {
    
    /**
     * Validate a weather observation for correctness and plausibility.
     *
     * @param observation The weather observation to validate
     * @return ValidationResult with any errors or warnings
     */
    fun validateWeatherObservation(observation: WeatherObservation): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate coordinate
        if (observation.coordinate == null) {
            errors.add("Coordinate is required")
        } else {
            if (!isValidCoordinate(observation.coordinate)) {
                errors.add("Invalid coordinate values")
            }
        }
        
        // Validate timestamp
        if (observation.timestamp == null) {
            errors.add("Timestamp is required")
        } else {
            if (!isValidTimestamp(observation.timestamp)) {
                errors.add("Invalid timestamp")
            }
        }
        
        // Validate retrievedAt timestamp
        if (observation.retrievedAt == null) {
            errors.add("RetrievedAt timestamp is required")
        } else {
            if (!isValidTimestamp(observation.retrievedAt)) {
                errors.add("Invalid retrievedAt timestamp")
            }
        }
        
        // Validate temperature bounds (reasonable physical range)
        observation.temperatureCelsius?.let { temp ->
            if (temp < -100.0 || temp > 100.0) {
                warnings.add("Temperature out of reasonable physical range: $temp°C")
            }
        }
        
        // Validate wind speed (non-negative, reasonable values)
        observation.windSpeedMps?.let { wind ->
            if (wind < 0.0) {
                errors.add("Wind speed cannot be negative: $wind m/s")
            } else if (wind > 100.0) {
                warnings.add("Wind speed seems unusually high: $wind m/s")
            }
            Unit
        }
        
        // Validate humidity bounds
        observation.humidityPercentage?.let { humidity ->
            if (humidity < 0.0 || humidity > 100.0) {
                errors.add("Humidity must be between 0 and 100: $humidity%")
            }
        }
        
        // Validate precipitation (non-negative)
        observation.precipitationMm?.let { precip ->
            if (precip < 0.0) {
                errors.add("Precipitation cannot be negative: $precip mm")
            }
        }
        
        // Validate precipitation probability bounds
        observation.precipitationProbability?.let { prob ->
            if (prob < 0.0 || prob > 1.0) {
                errors.add("Precipitation probability must be between 0 and 1: $prob")
            }
        }
        
        // Validate pressure bounds
        observation.pressureHpa?.let { pressure ->
            if (pressure < 500.0 || pressure > 1100.0) {
                warnings.add("Pressure out of reasonable physical range: $pressure hPa")
            }
        }
        
        // Validate visibility bounds
        observation.visibilityMeters?.let { visibility ->
            if (visibility < 0.0) {
                errors.add("Visibility cannot be negative: $visibility meters")
            }
        }
        
        // Validate cloud cover bounds
        observation.cloudCoverPercentage?.let { cloud ->
            if (cloud < 0.0 || cloud > 100.0) {
                errors.add("Cloud cover must be between 0 and 100: $cloud%")
            }
        }
        
        // Validate freshness
        if (observation.freshness == null) {
            warnings.add("Freshness is unknown")
        }
        
        // Validate confidence bounds
        if (observation.confidence < 0.0 || observation.confidence > 1.0) {
            errors.add("Confidence must be between 0 and 1: ${observation.confidence}")
        }
        
        // Validate uncertainty bounds
        if (observation.uncertainty < 0.0 || observation.uncertainty > 1.0) {
            errors.add("Uncertainty must be between 0 and 1: ${observation.uncertainty}")
        }
        
        return ValidationResult(
            hasErrors = errors.isNotEmpty(),
            hasWarnings = warnings.isNotEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate a forecast weather point for correctness and plausibility.
     *
     * @param forecastPoint The forecast point to validate
     * @return ValidationResult with any errors or warnings
     */
    fun validateForecastWeatherPoint(forecastPoint: ForecastWeatherPoint): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate coordinate
        if (forecastPoint.coordinate == null) {
            errors.add("Coordinate is required")
        } else {
            if (!isValidCoordinate(forecastPoint.coordinate)) {
                errors.add("Invalid coordinate values")
            }
        }
        
        // Validate valid time
        if (forecastPoint.validTime == null) {
            errors.add("Valid time is required")
        } else {
            if (!isValidTimestamp(forecastPoint.validTime)) {
                errors.add("Invalid valid time")
            }
        }
        
        // Validate forecast generated time
        if (forecastPoint.forecastGeneratedAt == null) {
            errors.add("Forecast generated time is required")
        } else {
            if (!isValidTimestamp(forecastPoint.forecastGeneratedAt)) {
                errors.add("Invalid forecast generated time")
            }
        }
        
        // Validate temperature bounds (reasonable physical range)
        forecastPoint.temperatureCelsius?.let { temp ->
            if (temp < -100.0 || temp > 100.0) {
                warnings.add("Temperature out of reasonable physical range: $temp°C")
            }
        }
        
        // Validate wind speed (non-negative, reasonable values)
        forecastPoint.windSpeedMps?.let { wind ->
            if (wind < 0.0) {
                errors.add("Wind speed cannot be negative: $wind m/s")
            } else if (wind > 100.0) {
                warnings.add("Wind speed seems unusually high: $wind m/s")
            }
            Unit
        }
        
        // Validate humidity bounds
        forecastPoint.humidityPercentage?.let { humidity ->
            if (humidity < 0.0 || humidity > 100.0) {
                errors.add("Humidity must be between 0 and 100: $humidity%")
            }
        }
        
        // Validate precipitation (non-negative)
        forecastPoint.precipitationMm?.let { precip ->
            if (precip < 0.0) {
                errors.add("Precipitation cannot be negative: $precip mm")
            }
        }
        
        // Validate precipitation probability bounds
        forecastPoint.precipitationProbability?.let { prob ->
            if (prob < 0.0 || prob > 1.0) {
                errors.add("Precipitation probability must be between 0 and 1: $prob")
            }
        }
        
        // Validate pressure bounds
        forecastPoint.pressureHpa?.let { pressure ->
            if (pressure < 500.0 || pressure > 1100.0) {
                warnings.add("Pressure out of reasonable physical range: $pressure hPa")
            }
        }
        
        // Validate visibility bounds
        forecastPoint.visibilityMeters?.let { visibility ->
            if (visibility < 0.0) {
                errors.add("Visibility cannot be negative: $visibility meters")
            }
        }
        
        // Validate cloud cover bounds
        forecastPoint.cloudCoverPercentage?.let { cloud ->
            if (cloud < 0.0 || cloud > 100.0) {
                errors.add("Cloud cover must be between 0 and 100: $cloud%")
            }
        }
        
        // Validate confidence bounds
        if (forecastPoint.confidence < 0.0 || forecastPoint.confidence > 1.0) {
            errors.add("Confidence must be between 0 and 1: ${forecastPoint.confidence}")
        }
        
        // Validate uncertainty bounds
        if (forecastPoint.uncertainty < 0.0 || forecastPoint.uncertainty > 1.0) {
            errors.add("Uncertainty must be between 0 and 1: ${forecastPoint.uncertainty}")
        }
        
        // Validate forecast horizon
        if (forecastPoint.forecastHorizonHours < 0.0) {
            errors.add("Forecast horizon cannot be negative: ${forecastPoint.forecastHorizonHours} hours")
        }
        
        return ValidationResult(
            hasErrors = errors.isNotEmpty(),
            hasWarnings = warnings.isNotEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate a coordinate for geographic validity.
     */
    private fun isValidCoordinate(coordinate: GeoCoordinate): Boolean {
        return coordinate.latitude >= -90.0 && coordinate.latitude <= 90.0 &&
               coordinate.longitude >= -180.0 && coordinate.longitude <= 180.0
    }
    
    /**
     * Validate a timestamp for logical validity.
     */
    private fun isValidTimestamp(timestamp: Instant): Boolean {
        // Allow timestamps within reasonable range (approx. 100 years from Unix epoch)
        return timestamp.epochSecond >= -3153600000L && timestamp.epochSecond <= 3153600000L
    }
}
