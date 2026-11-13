package com.evnavigator.iran.domain.model.weather

import java.util.*

/**
 * Represents a snapshot of weather conditions at a specific time and location.
 *
 * This model captures the complete set of current or forecasted weather data
 * that could affect EV navigation, including temperature, precipitation,
 * wind patterns, visibility, and other environmental factors.
 */
data class WeatherSnapshot(
    /**
     * The geographic coordinate where this weather condition applies
     */
    val coordinate: com.evnavigator.iran.domain.model.GeoCoordinate,

    /**
     * Timestamp when the snapshot was taken or forecast time
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
     * The time the data was retrieved or last updated
     */
    val retrievedAt: Date? = null,

    /**
     * How long ago this snapshot is valid (in seconds) 
     */
    val validityPeriodSeconds: Long? = null,

    /**
     * Indicates if the weather data is currently being used in a trip/route planning
     */
    val inUse: Boolean = false,

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
            retrievedAt: Date? = null,
            validityPeriodSeconds: Long? = null,
            inUse: Boolean = false,
            additionalProperties: Map<String, Any>? = null
        ): WeatherSnapshot {
            return WeatherSnapshot(
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
                retrievedAt = retrievedAt,
                validityPeriodSeconds = validityPeriodSeconds,
                inUse = inUse,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Checks if this weather snapshot is still valid/usable.
     *
     * @param currentTime Current time for comparison (defaults to now)
     * @return True if the snapshot is within its validity period, false otherwise
     */
    fun isValid(currentTime: Date = Date()): Boolean {
        return if (validityPeriodSeconds != null && retrievedAt != null) {
            val ageMillis = Math.abs(currentTime.time - retrievedAt.time)
            ageMillis < (validityPeriodSeconds!! * 1000.0).toLong()
        } else {
            // If no validity period is specified, assume it's always valid
            true
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