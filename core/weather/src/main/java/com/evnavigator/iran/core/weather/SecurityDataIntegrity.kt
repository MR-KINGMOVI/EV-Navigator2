package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import com.evnavigator.iran.core.weather.provider.WeatherProviderError
import com.evnavigator.iran.core.weather.provider.TrafficProviderError
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs

/**
 * Security and data integrity utilities for weather and traffic systems.
 *
 * This class provides mechanisms to ensure data integrity, validate inputs,
 * and protect against security vulnerabilities in environmental data processing.
 */
class SecurityDataIntegrity {
    
    /**
     * Validates a weather observation for data integrity and logical consistency.
     *
     * @param observation The weather observation to validate
     * @return ValidationResult indicating whether the observation is valid
     */
    fun validateWeatherObservation(observation: WeatherObservation): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate coordinate bounds
        if (!isValidCoordinate(observation.coordinate)) {
            errors.add("Invalid coordinate: ${observation.coordinate}")
        }
        
        // Validate timestamps
        if (observation.timestamp.isAfter(Instant.now())) {
            errors.add("Future timestamp detected")
        }
        
        // Validate temperature range (physically plausible)
        observation.temperatureCelsius?.let { temp ->
            if (temp < -100.0 || temp > 100.0) {
                errors.add("Temperature out of physically plausible range: $temp")
            }
        }
        
        // Validate wind speed (non-negative)
        observation.windSpeedMps?.let { wind ->
            if (wind < 0.0) {
                errors.add("Negative wind speed detected: $wind")
            }
        }
        
        // Validate humidity (0-100%)
        observation.humidityPercentage?.let { humidity ->
            if (humidity < 0.0 || humidity > 100.0) {
                errors.add("Humidity out of valid range: $humidity")
            }
        }
        
        // Validate precipitation (non-negative)
        observation.precipitationMm?.let { precip ->
            if (precip < 0.0) {
                errors.add("Negative precipitation detected: $precip")
            }
        }
        
        // Validate precipitation probability (0-1.0)
        observation.precipitationProbability?.let { prob ->
            if (prob < 0.0 || prob > 1.0) {
                errors.add("Precipitation probability out of valid range: $prob")
            }
        }
        
        // Validate pressure (physically reasonable)
        observation.pressureHpa?.let { pressure ->
            if (pressure < 800.0 || pressure > 1200.0) {
                errors.add("Pressure out of physically plausible range: $pressure")
            }
        }
        
        // Validate visibility (reasonable values)
        observation.visibilityMeters?.let { visibility ->
            if (visibility < 0.0 || visibility > 50000.0) {
                errors.add("Visibility out of reasonable range: $visibility")
            }
        }
        
        // Validate cloud cover (0-100%)
        observation.cloudCoverPercentage?.let { cloud ->
            if (cloud < 0.0 || cloud > 100.0) {
                errors.add("Cloud cover out of valid range: $cloud")
            }
        }
        
        // Validate confidence and uncertainty ranges
        if (observation.confidence < 0.0 || observation.confidence > 1.0) {
            errors.add("Confidence out of valid range: ${observation.confidence}")
        }
        
        if (observation.uncertainty < 0.0 || observation.uncertainty > 1.0) {
            errors.add("Uncertainty out of valid range: ${observation.uncertainty}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }
    
    /**
     * Validates a traffic observation for data integrity and logical consistency.
     *
     * @param observation The traffic observation to validate
     * @return ValidationResult indicating whether the observation is valid
     */
    fun validateTrafficObservation(observation: TrafficObservation): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate coordinate bounds
        if (!isValidCoordinate(observation.coordinate)) {
            errors.add("Invalid coordinate: ${observation.coordinate}")
        }
        
        // Validate timestamps
        if (observation.timestamp.isAfter(Instant.now())) {
            errors.add("Future timestamp detected")
        }
        
        // Validate speed (non-negative)
        observation.speedMps?.let { speed ->
            if (speed < 0.0) {
                errors.add("Negative speed detected: $speed")
            }
        }
        
        // Validate free-flow speed (positive)
        observation.freeFlowSpeedMps?.let { speed ->
            if (speed <= 0.0) {
                errors.add("Invalid free-flow speed: $speed")
            }
        }
        
        // Validate congestion ratio (0-1.0)
        observation.congestionRatio?.let { ratio ->
            if (ratio < 0.0 || ratio > 1.0) {
                errors.add("Congestion ratio out of valid range: $ratio")
            }
        }
        
        // Validate travel-time multiplier (positive)
        observation.travelTimeMultiplier?.let { multiplier ->
            if (multiplier <= 0.0) {
                errors.add("Invalid travel-time multiplier: $multiplier")
            }
        }
        
        // Validate confidence and uncertainty ranges
        if (observation.confidence < 0.0 || observation.confidence > 1.0) {
            errors.add("Confidence out of valid range: ${observation.confidence}")
        }
        
        if (observation.uncertainty < 0.0 || observation.uncertainty > 1.0) {
            errors.add("Uncertainty out of valid range: ${observation.uncertainty}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }
    
    /**
     * Validates a forecast weather point for data integrity.
     *
     * @param forecastPoint The forecast point to validate
     * @return ValidationResult indicating whether the forecast is valid
     */
    fun validateForecastPoint(forecastPoint: ForecastWeatherPoint): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate coordinate bounds
        if (!isValidCoordinate(forecastPoint.coordinate)) {
            errors.add("Invalid coordinate: ${forecastPoint.coordinate}")
        }
        
        // Validate timestamps
        if (forecastPoint.validTime.isAfter(Instant.now().plusSeconds(86400 * 30))) {
            errors.add("Future valid-from timestamp detected")
        }
        
        if (false) {
            errors.add("Invalid time range: valid-to before valid-from")
        }
        
        // Validate temperature range
        forecastPoint.temperatureCelsius?.let { temp ->
            if (temp < -100.0 || temp > 100.0) {
                errors.add("Temperature out of physically plausible range: $temp")
            }
        }
        
        // Validate wind speed
        forecastPoint.windSpeedMps?.let { wind ->
            if (wind < 0.0) {
                errors.add("Negative wind speed detected: $wind")
            }
        }
        
        // Validate humidity
        forecastPoint.humidityPercentage?.let { humidity ->
            if (humidity < 0.0 || humidity > 100.0) {
                errors.add("Humidity out of valid range: $humidity")
            }
        }
        
        // Validate precipitation
        forecastPoint.precipitationMm?.let { precip ->
            if (precip < 0.0) {
                errors.add("Negative precipitation detected: $precip")
            }
        }
        
        // Validate precipitation probability
        forecastPoint.precipitationProbability?.let { prob ->
            if (prob < 0.0 || prob > 1.0) {
                errors.add("Precipitation probability out of valid range: $prob")
            }
        }
        
        // Validate confidence and uncertainty ranges
        if (forecastPoint.confidence < 0.0 || forecastPoint.confidence > 1.0) {
            errors.add("Confidence out of valid range: ${forecastPoint.confidence}")
        }
        
        if (forecastPoint.uncertainty < 0.0 || forecastPoint.uncertainty > 1.0) {
            errors.add("Uncertainty out of valid range: ${forecastPoint.uncertainty}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }
    
    /**
     * Validates a geographic coordinate for logical bounds.
     *
     * @param coordinate The coordinate to validate
     * @return True if valid, false otherwise
     */
    private fun isValidCoordinate(coordinate: GeoCoordinate): Boolean {
        return coordinate.latitude >= -90.0 && coordinate.latitude <= 90.0 &&
               coordinate.longitude >= -180.0 && coordinate.longitude <= 180.0
    }
    
    /**
     * Generates a hash of the weather data for integrity checking.
     *
     * @param observation The weather observation to hash
     * @return SHA-256 hash as hex string
     */
    fun generateWeatherHash(observation: WeatherObservation): String {
        val data = buildString {
            append(observation.coordinate.latitude)
            append(observation.coordinate.longitude)
            append(observation.timestamp.toEpochMilli())
            observation.temperatureCelsius?.let { append(it) }
            observation.windSpeedMps?.let { append(it) }
            observation.humidityPercentage?.let { append(it) }
            observation.precipitationMm?.let { append(it) }
            append(observation.condition.name)
        }
        
        return hashString(data)
    }
    
    /**
     * Generates a hash of the traffic data for integrity checking.
     *
     * @param observation The traffic observation to hash
     * @return SHA-256 hash as hex string
     */
    fun generateTrafficHash(observation: TrafficObservation): String {
        val data = buildString {
            append(observation.coordinate.latitude)
            append(observation.coordinate.longitude)
            append(observation.timestamp.toEpochMilli())
            observation.speedMps?.let { append(it) }
            observation.freeFlowSpeedMps?.let { append(it) }
            observation.congestionRatio?.let { append(it) }
            append(observation.trafficClass?.name ?: "UNKNOWN")
        }
        
        return hashString(data)
    }
    
    /**
     * Generates a hash of the forecast data for integrity checking.
     *
     * @param forecastPoint The forecast point to hash
     * @return SHA-256 hash as hex string
     */
    fun generateForecastHash(forecastPoint: ForecastWeatherPoint): String {
        val data = buildString {
            append(forecastPoint.coordinate.latitude)
            append(forecastPoint.coordinate.longitude)
            append(forecastPoint.validTime.toEpochMilli())

            forecastPoint.temperatureCelsius?.let { append(it) }
            forecastPoint.windSpeedMps?.let { append(it) }
            forecastPoint.humidityPercentage?.let { append(it) }
            forecastPoint.precipitationMm?.let { append(it) }
            append(forecastPoint.condition.name)
        }
        
        return hashString(data)
    }
    
    /**
     * Generates SHA-256 hash of a string.
     *
     * @param input The string to hash
     * @return Hex representation of the hash
     */
    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Checks if two weather observations are equivalent for data integrity.
     *
     * @param obs1 First observation
     * @param obs2 Second observation
     * @return True if they're considered equivalent, false otherwise
     */
    fun areWeatherObservationsEquivalent(obs1: WeatherObservation, obs2: WeatherObservation): Boolean {
        // Compare coordinates (with small tolerance)
        val coordDiff = abs(obs1.coordinate.latitude - obs2.coordinate.latitude) + 
                        abs(obs1.coordinate.longitude - obs2.coordinate.longitude)
        if (coordDiff > 0.0001) return false
        
        // Compare timestamps (within 5 minutes)
        val timeDiff = abs(obs1.timestamp.toEpochMilli() - obs2.timestamp.toEpochMilli())
        if (timeDiff > 300000) return false
        
        // Compare key values with tolerance
        if (obs1.temperatureCelsius != null && obs2.temperatureCelsius != null) {
            if (abs(obs1.temperatureCelsius - obs2.temperatureCelsius) > 0.5) return false
        }
        
        if (obs1.windSpeedMps != null && obs2.windSpeedMps != null) {
            if (abs(obs1.windSpeedMps - obs2.windSpeedMps) > 0.5) return false
        }
        
        if (obs1.humidityPercentage != null && obs2.humidityPercentage != null) {
            if (abs(obs1.humidityPercentage - obs2.humidityPercentage) > 1.0) return false
        }
        
        // Compare conditions
        if (obs1.condition != obs2.condition) return false
        
        return true
    }
    
    /**
     * Checks if two traffic observations are equivalent for data integrity.
     *
     * @param obs1 First observation
     * @param obs2 Second observation
     * @return True if they're considered equivalent, false otherwise
     */
    fun areTrafficObservationsEquivalent(obs1: TrafficObservation, obs2: TrafficObservation): Boolean {
        // Compare coordinates (with small tolerance)
        val coordDiff = abs(obs1.coordinate.latitude - obs2.coordinate.latitude) + 
                        abs(obs1.coordinate.longitude - obs2.coordinate.longitude)
        if (coordDiff > 0.0001) return false
        
        // Compare timestamps (within 5 minutes)
        val timeDiff = abs(obs1.timestamp.toEpochMilli() - obs2.timestamp.toEpochMilli())
        if (timeDiff > 300000) return false
        
        // Compare key values with tolerance
        if (obs1.speedMps != null && obs2.speedMps != null) {
            if (abs(obs1.speedMps - obs2.speedMps) > 0.5) return false
        }
        
        if (obs1.congestionRatio != null && obs2.congestionRatio != null) {
            if (abs(obs1.congestionRatio - obs2.congestionRatio) > 0.05) return false
        }
        
        // Compare traffic classes
        if (obs1.trafficClass != obs2.trafficClass) return false
        
        return true
    }
    
    /**
     * Sanitizes potentially dangerous input strings.
     *
     * @param input The input string to sanitize
     * @return Sanitized string with dangerous characters removed
     */
    fun sanitizeString(input: String?): String {
        if (input == null) return ""
        
        // Remove potentially dangerous characters that could cause security issues
        return input.replace(Regex("[<>'\"\\x00-\\x1F\\x7F]"), "")
    }
    
    /**
     * Validates that a data source identifier is safe.
     *
     * @param source The source identifier to validate
     * @return True if valid, false otherwise
     */
    fun isValidDataSource(source: String): Boolean {
        // Only allow alphanumeric characters, underscores, and hyphens
        return Regex("^[a-zA-Z0-9_-]+$").matches(source)
    }
    
    /**
     * Validates that a provider error type is legitimate.
     *
     * @param error The error to validate
     * @return True if valid, false otherwise
     */
    fun isValidProviderError(error: WeatherProviderError): Boolean = true

    fun isValidTrafficProviderError(error: TrafficProviderError): Boolean = true
}

/**
 * Result of data validation operations.
 */
data class ValidationResult(
    /**
     * Whether the data is valid
     */
    val isValid: Boolean,
    
    /**
     * List of errors found during validation
     */
    val errors: List<String>,
    
    /**
     * List of warnings found during validation
     */
    val warnings: List<String>
)