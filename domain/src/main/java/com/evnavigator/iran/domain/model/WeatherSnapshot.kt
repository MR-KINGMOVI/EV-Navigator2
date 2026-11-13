package com.evnavigator.iran.domain.model

/**
 * Represents a snapshot of weather conditions at a specific location and time.
 *
 * @param coordinate Geographic coordinate where the weather was measured
 * @param timestamp Time when the weather data was recorded
 * @param temperature Current temperature
 * @param humidity Relative humidity percentage
 * @param windSpeed Wind speed
 * @param windDirection Wind direction in degrees
 * @param precipitation Precipitation amount (mm)
 * @param visibility Visibility distance
 * @param pressure Atmospheric pressure
 * @param condition Weather condition description
 */
data class WeatherSnapshot(
    val coordinate: GeoCoordinate,
    val timestamp: Long, // Unix timestamp
    val temperature: Temperature,
    val humidity: Double, // percentage
    val windSpeed: Speed,
    val windDirection: Double, // in degrees
    val precipitation: Double, // in mm
    val visibility: Distance,
    val pressure: Double, // in hPa
    val condition: String
)