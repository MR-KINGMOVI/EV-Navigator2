package com.evnavigator.iran.core.weather.model

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.Freshness
import java.time.Instant

/**
 * A weather observation at a specific point in time and location.
 *
 * This represents a single moment of weather data at a specific coordinate.
 */
data class WeatherObservation(
    /**
     * The geographic coordinate where the weather was observed
     */
    val coordinate: GeoCoordinate = GeoCoordinate(35.6892, 51.3890),
    
    /**
     * The timestamp when this weather observation was made
     */
    val timestamp: Instant = Instant.now(),
    
    /**
     * The temperature in degrees Celsius
     * UNKNOWN if not available or cannot be determined
     */
    val temperatureCelsius: Double? = null,
    
    /**
     * The apparent temperature (feels-like) in degrees Celsius  
     * UNKNOWN if not available or cannot be calculated
     */
    val apparentTemperatureCelsius: Double? = null,
    
    /**
     * Wind speed in meters per second
     * UNKNOWN if not available or cannot be measured
     */
    val windSpeedMps: Double? = null,
    
    /**
     * Wind direction in degrees (0 = North, 90 = East, etc.)
     * UNKNOWN if not available or cannot be determined
     */
    val windDirectionDegrees: Double? = null,
    
    /**
     * Precipitation amount in millimeters
     * UNKNOWN if no precipitation or not measurable
     */
    val precipitationMm: Double? = null,
    
    /**
     * Probability of precipitation (0.0 to 1.0)
     * UNKNOWN if not forecastable or not available
     */
    val precipitationProbability: Double? = null,
    
    /**
     * Relative humidity percentage (0-100)
     * UNKNOWN if not available or cannot be measured
     */
    val humidityPercentage: Double? = null,
    
    /**
     * Atmospheric pressure in hectopascals
     * UNKNOWN if not available or cannot be measured
     */
    val pressureHpa: Double? = null,
    
    /**
     * Visibility in meters
     * UNKNOWN if not available or cannot be measured
     */
    val visibilityMeters: Double? = null,
    
    /**
     * Cloud cover percentage (0-100)
     * UNKNOWN if not available or cannot be determined
     */
    val cloudCoverPercentage: Double? = null,
    
    /**
     * The weather condition classification
     * UNKNOWN if condition cannot be classified
     */
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    
    /**
     * Source identifier for where this weather data came from
     * Useful for tracking provenance and quality
     */
    val source: String = "OBSERVED",
    
    /**
     * Timestamp when the data was retrieved or generated
     * This can differ from observation time if data is cached or delayed
     */
    val retrievedAt: Instant = timestamp,
    
    /**
     * Freshness indicator of this weather data
     * Indicates how current the information is
     */
    val freshness: WeatherFreshness = WeatherFreshness.FRESH,
    
    /**
     * Confidence level in the accuracy of this observation (0.0 to 1.0)
     * Higher values indicate more reliable data
     */
    val confidence: Double = 1.0,
    
    /**
     * Uncertainty measure for this weather observation (0.0 to 1.0)
     * Higher values indicate higher uncertainty in the measurements
     */
    val uncertainty: Double = 0.0
) {
    constructor(
        coordinate: GeoCoordinate? = null,
        timestamp: Instant = Instant.now(),
        temperature: Double? = null,
        apparentTemperature: Double? = null,
        windSpeed: Double? = null,
        windDirection: Double? = null,
        precipitationAmount: Double? = null,
        precipitationProbability: Double? = null,
        humidity: Double? = null,
        pressure: Double? = null,
        visibility: Double? = null,
        cloudCoverage: Double? = null,
        condition: WeatherCondition = WeatherCondition.CLEAR,
        source: String = "OBSERVED",
        retrievedAt: Instant = timestamp,
        freshness: Freshness = Freshness.FRESH,
        confidence: Double = 1.0,
        uncertainty: Double = 0.0
    ) : this(
        coordinate = coordinate ?: GeoCoordinate(35.6892, 51.3890),
        timestamp = timestamp,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = apparentTemperature,
        windSpeedMps = windSpeed,
        windDirectionDegrees = windDirection,
        precipitationMm = precipitationAmount,
        precipitationProbability = precipitationProbability,
        humidityPercentage = humidity,
        pressureHpa = pressure,
        visibilityMeters = visibility,
        cloudCoverPercentage = cloudCoverage,
        condition = condition,
        source = source,
        retrievedAt = retrievedAt,
        freshness = when (freshness) {
            Freshness.FRESH -> WeatherFreshness.FRESH
            Freshness.CACHED -> WeatherFreshness.REASONABLY_FRESH
            Freshness.STALE_BUT_USABLE -> WeatherFreshness.SOMEWHAT_STALE
            Freshness.EXPIRED -> WeatherFreshness.EXPIRED
            else -> WeatherFreshness.UNKNOWN
        },
        confidence = confidence,
        uncertainty = uncertainty
    )
}
