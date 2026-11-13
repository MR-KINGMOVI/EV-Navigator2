package com.evnavigator.iran.core.weather.model

import com.evnavigator.iran.core.domain.GeoCoordinate

import java.time.Instant

/**
 * A weather forecast point at a specific time and location.
 *
 * This represents a forecasted weather condition for a future time at a specific coordinate.
 */

data class ForecastWeatherPoint(
    /**
     * The geographic coordinate where the weather is forecasted
     */
    val coordinate: GeoCoordinate,
    
    /**
     * The valid time for this forecast point (when it applies)
     */
    val validTime: Instant,
    
    /**
     * The timestamp when this forecast was generated or retrieved
     */
    val forecastGeneratedAt: Instant,
    
    /**
     * The temperature in degrees Celsius
     * UNKNOWN if not available or cannot be predicted
     */
    val temperatureCelsius: Double?,
    
    /**
     * The apparent temperature (feels-like) in degrees Celsius  
     * UNKNOWN if not available or cannot be calculated
     */
    val apparentTemperatureCelsius: Double?,
    
    /**
     * Wind speed in meters per second
     * UNKNOWN if not available or cannot be predicted
     */
    val windSpeedMps: Double?,
    
    /**
     * Wind direction in degrees (0 = North, 90 = East, etc.)
     * UNKNOWN if not available or cannot be predicted
     */
    val windDirectionDegrees: Double?,
    
    /**
     * Precipitation amount in millimeters
     * UNKNOWN if no precipitation or not forecastable
     */
    val precipitationMm: Double?,
    
    /**
     * Probability of precipitation (0.0 to 1.0)
     * UNKNOWN if not forecastable or not available
     */
    val precipitationProbability: Double?,
    
    /**
     * Relative humidity percentage (0-100)
     * UNKNOWN if not available or cannot be predicted
     */
    val humidityPercentage: Double?,
    
    /**
     * Atmospheric pressure in hectopascals
     * UNKNOWN if not available or cannot be predicted
     */
    val pressureHpa: Double?,
    
    /**
     * Visibility in meters
     * UNKNOWN if not available or cannot be predicted
     */
    val visibilityMeters: Double?,
    
    /**
     * Cloud cover percentage (0-100)
     * UNKNOWN if not available or cannot be predicted
     */
    val cloudCoverPercentage: Double?,
    
    /**
     * The weather condition classification
     * UNKNOWN if condition cannot be classified
     */
    val condition: WeatherCondition,
    
    /**
     * Source identifier for where this forecast data came from
     * Useful for tracking provenance and quality
     */
    val source: String,
    
    /**
     * Forecast horizon in hours (how far in advance the forecast is)
     */
    val forecastHorizonHours: Double,
    
    /**
     * Confidence level in the accuracy of this forecast (0.0 to 1.0)
     * Higher values indicate more reliable data
     */
    val confidence: Double,
    
    /**
     * Uncertainty measure for this forecast point (0.0 to 1.0)
     * Higher values indicate higher uncertainty in the predictions
     */
    val uncertainty: Double
)