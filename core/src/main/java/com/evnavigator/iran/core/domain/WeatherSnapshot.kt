package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a snapshot of weather conditions at a specific location and time.
 *
 * @param coordinate Geographic coordinate where the weather was measured
 * @param timestamp Unix timestamp of when the measurement was taken
 * @param temperatureCelsius Temperature in Celsius
 * @param humidityPercentage Relative humidity percentage
 * @param windSpeedKmh Wind speed in kilometers per hour
 * @param windDirectionDegrees Wind direction in degrees (0 = North)
 * @param precipitationMm Precipitation amount in millimeters
 * @param visibilityMeters Visibility distance in meters
 * @param pressureHpa Atmospheric pressure in hectopascals
 */
@Parcelize
data class WeatherSnapshot(
    val coordinate: GeoCoordinate,
    val timestamp: Long,
    val temperatureCelsius: Double,
    val humidityPercentage: Int,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val precipitationMm: Double,
    val visibilityMeters: Double,
    val pressureHpa: Double
) : Parcelable {
    
    init {
        require(humidityPercentage in 0..100) { "Humidity must be between 0 and 100" }
        require(windSpeedKmh >= 0) { "Wind speed cannot be negative" }
        require(windDirectionDegrees in 0..360) { "Wind direction must be between 0 and 360 degrees" }
        require(precipitationMm >= 0) { "Precipitation cannot be negative" }
        require(visibilityMeters >= 0) { "Visibility cannot be negative" }
        require(pressureHpa > 0) { "Pressure must be positive" }
    }

    /**
     * Gets the weather condition based on temperature and precipitation
     */
    val weatherCondition: WeatherCondition
        get() = when {
            precipitationMm > 5.0 -> WeatherCondition.RAIN
            temperatureCelsius < 0 -> WeatherCondition.SNOW
            temperatureCelsius > 30 -> WeatherCondition.HEAT
            humidityPercentage > 80 -> WeatherCondition.HUMID
            else -> WeatherCondition.CLEAR
        }

    /**
     * Checks if conditions are suitable for driving (no severe weather)
     */
    val isDrivingSafe: Boolean
        get() = when (weatherCondition) {
            WeatherCondition.SNOW,
            WeatherCondition.ICE,
            WeatherCondition.STORM -> false
            else -> visibilityMeters > 1000 && windSpeedKmh < 100
        }

    /**
     * Gets the perceived temperature considering wind chill
     */
    val perceivedTemperatureCelsius: Double
        get() = if (windSpeedKmh > 20 && temperatureCelsius < 10) {
            // Wind chill calculation simplified
            temperatureCelsius - (0.6 * windSpeedKmh / 10)
        } else {
            temperatureCelsius
        }
}

/**
 * Enum representing different weather conditions
 */
enum class WeatherCondition {
    CLEAR,        // Clear skies
    CLOUDY,       // Cloudy
    RAIN,         // Rainy
    SNOW,         // Snowy
    ICE,          // Icy conditions
    STORM,        // Stormy
    HEAT,         // Hot weather
    HUMID,        // Humid conditions
    FOG           // Foggy conditions
}