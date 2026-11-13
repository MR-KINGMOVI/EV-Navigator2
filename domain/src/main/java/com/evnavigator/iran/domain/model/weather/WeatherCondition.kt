package com.evnavigator.iran.domain.model.weather

import java.util.*

/**
 * Represents weather conditions at a specific time and location.
 *
 * This model captures the essential elements of current or forecasted weather
 * that could affect EV navigation, including visibility, precipitation,
 * temperature, and other environmental factors.
 */
data class WeatherCondition(
    /**
     * The geographic coordinate where this weather condition applies
     */
    val coordinate: com.evnavigator.iran.domain.model.GeoCoordinate,

    /**
     * Timestamp when the weather data was recorded or forecasted
     */
    val timestamp: Date,

    /**
     * Current temperature in degrees Celsius
     */
    val temperatureCelsius: Double,

    /**
     * Feels like temperature in degrees Celsius (apparent temperature)
     */
    val feelsLikeTemperatureCelsius: Double? = null,

    /**
     * Humidity percentage (0-100%)
     */
    val humidityPercent: Double? = null,

    /**
     * Atmospheric pressure at sea level in hPa
     */
    val pressureHpa: Double? = null,

    /**
     * Wind speed in meters per second
     */
    val windSpeedMps: Double? = null,

    /**
     * Wind direction in degrees (0-360)
     */
    val windDirectionDegrees: Double? = null,

    /**
     * Precipitation intensity in mm/hour
     */
    val precipitationIntensityMmPerHr: Double? = null,

    /**
     * Type of precipitation (if any) - e.g., "rain", "snow", "sleet"
     */
    val precipitationType: String? = null, 

    /**
     * Cloud cover percentage (0-100%)
     */
    val cloudCoverPercent: Double? = null,

    /**
     * Visibility in meters
     */
    val visibilityMeters: Double? = null,

    /**
     * Description of the weather condition
     */
    val description: String? = null,

    /**
     * Icon code for representing this weather condition (for UI)
     */
    val iconCode: String? = null,

    /**
     * Source of the weather data
     */
    val source: String? = null,

    /**
     * Confidence level in the accuracy of this reading (0.0 to 1.0)
     */
    val confidence: Double? = null,

    /**
     * Whether it's currently raining/snowing or not
     */
    val isRaining: Boolean = false,

    /**
     * Whether there are any weather-related impacts on driving conditions
     */
    val hasDrivingImpact: Boolean = false,

    /**
     * Precipitation probability for the period (0-100%)
     */
    val precipitationProbabilityPercent: Double? = null,

    /**
     * Dew point in degrees Celsius 
     * (temperature at which air becomes saturated with water vapor)
     */
    val dewPointCelsius: Double? = null,

    /**
     * Atmospheric pressure trend - increasing, decreasing or steady
     */
    val pressureTrend: PressureTrend? = null,

    /**
     * UV index level (0-12+ scale)
     */
    val uvIndex: Int? = null,

    /**
     * Air quality index (0-500+, with 0 being excellent and 500+ being hazardous)
     */
    val aqi: Int? = null,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            coordinate: com.evnavigator.iran.domain.model.GeoCoordinate,
            timestamp: Date,
            temperatureCelsius: Double,
            feelsLikeTemperatureCelsius: Double? = null,
            humidityPercent: Double? = null,
            pressureHpa: Double? = null,
            windSpeedMps: Double? = null,
            windDirectionDegrees: Double? = null,
            precipitationIntensityMmPerHr: Double? = null,
            precipitationType: String? = null,
            cloudCoverPercent: Double? = null,
            visibilityMeters: Double? = null,
            description: String? = null,
            iconCode: String? = null,
            source: String? = null,
            confidence: Double? = null,
            isRaining: Boolean = false,
            hasDrivingImpact: Boolean = false,
            precipitationProbabilityPercent: Double? = null,
            dewPointCelsius: Double? = null,
            pressureTrend: PressureTrend? = null,
            uvIndex: Int? = null,
            aqi: Int? = null,
            additionalProperties: Map<String, Any>? = null
        ): WeatherCondition {
            return WeatherCondition(
                coordinate = coordinate,
                timestamp = timestamp,
                temperatureCelsius = temperatureCelsius,
                feelsLikeTemperatureCelsius = feelsLikeTemperatureCelsius,
                humidityPercent = humidityPercent,
                pressureHpa = pressureHpa,
                windSpeedMps = windSpeedMps,
                windDirectionDegrees = windDirectionDegrees,
                precipitationIntensityMmPerHr = precipitationIntensityMmPerHr,
                precipitationType = precipitationType,
                cloudCoverPercent = cloudCoverPercent,
                visibilityMeters = visibilityMeters,
                description = description,
                iconCode = iconCode,
                source = source,
                confidence = confidence,
                isRaining = isRaining,
                hasDrivingImpact = hasDrivingImpact,
                precipitationProbabilityPercent = precipitationProbabilityPercent,
                dewPointCelsius = dewPointCelsius,
                pressureTrend = pressureTrend,
                uvIndex = uvIndex,
                aqi = aqi,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Checks if the weather condition has significant impact on EV driving.
     *
     * @return True if conditions are likely to affect EV performance significantly
     */
    val isSignificantlyImpactingForEV: Boolean
        get() {
            // Wind speed over 20 km/h (5.5 m/s) or precipitation intensity > 10mm/hr 
            return when {
                windSpeedMps != null && windSpeedMps!! > 5.5 -> true
                precipitationIntensityMmPerHr != null && precipitationIntensityMmPerHr!! > 10.0 -> true
                cloudCoverPercent != null && cloudCoverPercent!! > 70.0 -> true // Overcast skies
                else -> false
            }
        }

    /**
     * Gets the effective temperature in Celsius considering wind chill.
     *
     * @return Effective temperature or null if not enough data
     */
    val effectiveTemperatureCelsius: Double?
        get() {
            return when {
                feelsLikeTemperatureCelsius != null -> feelsLikeTemperatureCelsius
                windSpeedMps != null && windSpeedMps!! > 2.0 && temperatureCelsius != null -> {
                    // Simple wind chill calculation (simplified)
                    val windKmh = windSpeedMps!! * 3.6
                    if (windKmh > 5.0) {
                        13.12 + 0.6215 * temperatureCelsius - 11.37 * Math.pow(windKmh * 0.36, 0.16) +
                                0.3965 * temperatureCelsius * Math.pow(windKmh * 0.36, 0.16)
                    } else {
                        temperatureCelsius
                    }
                }
                else -> null
            }
        }

    /**
     * Gets the weather impact factor for EV driving.
     *
     * @return Impact score from 0 (no effect) to 1 (highly impactful)
     */
    val evImpactFactor: Double
        get() {
            var impact = 0.0

            // Wind impact - higher when >25 km/h wind speed 
            if (windSpeedMps != null && windSpeedMps!! > 7) {
                impact += Math.min(1.0, (windSpeedMps!! - 7) / 18)
            }

            // Precipitation impact
            if (precipitationIntensityMmPerHr != null && precipitationIntensityMmPerHr!! > 5) {
                impact += Math.min(1.0, precipitationIntensityMmPerHr!! / 20)
            }

            // Temperature impact - extreme cold or heat can reduce battery efficiency
            if (temperatureCelsius != null) {
                val tempDiff = when {
                    temperatureCelsius < 0 -> -temperatureCelsius
                    temperatureCelsius > 35 -> temperatureCelsius - 35.0
                    else -> 0.0
                }
                impact += Math.min(1.0, tempDiff / 20)
            }

            return Math.min(1.0, impact)
        }
}