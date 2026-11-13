package com.evnavigator.iran.domain.model.weather

import com.evnavigator.iran.domain.model.GeoCoordinate
import java.util.*

/**
 * Represents current weather conditions at a specific location.
 *
 * This data class captures comprehensive weather information that can be used for 
 * offline navigation decisions, including temperature, precipitation, visibility,
 * wind conditions, and other environmental factors that may affect EV performance.
 */
data class WeatherConditions(
    // Time and location
    val timestamp: Long,
    val coordinate: GeoCoordinate,
    
    // Temperature and atmospheric pressure (essential for EV range estimation)
    val temperatureCelsius: Double?,
    val feelsLikeTemperatureCelsius: Double?,
    val humidityPercent: Double?,  // Relative humidity percentage
    val atmosphericPressureHpa: Double?,  // Atmospheric pressure in hPa
    
    // Precipitation data  
    val precipitationMm: Double?,   // Total precipitation in mm
    val precipitationProbabilityPercent: Double?,  // Chance of precipitation (0-100%)
    
    // Wind conditions
    val windSpeedKmh: Double?,      // Wind speed in km/h
    val windDirectionDegrees: Double?,  // Wind direction in degrees (0-360)
    val gustSpeedKmh: Double?,       // Peak wind gusts in km/h
    
    // Visibility and atmospheric effects  
    val visibilityMeters: Double?,   // Horizontal visibility in meters
    val cloudCoverPercent: Double?,  // Cloud cover percentage (0-100%)
    
    // Precipitation type and intensity
    val precipitationType: PrecipitationType?,
    val weatherCode: String?,        // Numerical code for weather conditions
    
    // Special atmospheric effects that impact EV performance
    val airDensityKgPerCubicMeter: Double?,  // Affects aerodynamic drag (kg/m³)
    
    // Data source and quality indicators
    val dataSource: WeatherDataSource,
    val dataQuality: WeatherDataQuality,   // Quality score for the reading
    
    // Offline-specific fields for navigation decisions
    val isPrecipitationHeavy: Boolean = false,  // For quick decision making
    val hasFog: Boolean = false,                // Impact visibility and safety
    val hasSnow: Boolean = false,               // Affects traction and efficiency  
    val hasIce: Boolean = false,                // Critical for route planning
    val hasHighWindConditions: Boolean = false,  // Affects aerodynamic drag
    
    // EV-specific weather impacts
    val estimatedRangeReductionPercent: Double?,  // Estimated % reduction in driving range due to conditions
    val impactOnChargingTime: ChargingImpactType?,   // How it affects charging speed
    val recommendedSpeedLimitKmh: Double?,        // Recommended speed limit under these conditions
    
    // Metadata for offline caching and validation
    val sourceSystem: String = "WEATHER_ENGINE",
    val cacheTimestamp: Long? = null,
    val validityPeriodSeconds: Int = 3600,   // How long this data is valid (seconds)
    
    // Additional fields that help with EV-specific navigation decisions
    val roadCondition: RoadConditionType? = null,     // Wet, icy, dry, etc.
    val tireGripFactor: Double? = null,            // 0.0 to 1.0 scale for traction estimation
    
    // Time-based weather effects (important for trip planning)
    val sunriseTime: Long?,      // When the sun rises at this location
    val sunsetTime: Long?,       // When the sun sets at this location
    
    // Environmental factors affecting EV efficiency
    val thermalComfortIndex: Double? = null,   // Affects heating/cooling needs
    val dewPointCelsius: Double? = null,        // Affects visibility and battery performance
    
    // Data validation fields for offline reliability
    val isDataValid: Boolean = true,
    val dataConfidenceScore: Double? = 1.0,   // Confidence in the reading (0.0 - 1.0)
    
    // Additional EV-specific parameters that can be derived from weather
    val batteryHeatingRequired: Boolean = false,  // If heating is needed due to cold
    val climateControlPowerConsumptionWatts: Double? = null,  // Estimated power for cabin comfort
    
    // Alternative representations of the same data (for offline processing)
    val temperatureFahrenheit: Double? = null,
    val windSpeedMph: Double? = null,
    
    // Metadata and system information
    val version: String = "1.0",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Gets the weather impact score for EV navigation.
     *
     * @return A composite score from 0-100 indicating how much this will affect EV performance
     */
    fun getEVEfficiencyImpactScore(): Int? {
        if (temperatureCelsius == null || windSpeedKmh == null) return null
        
        // Simple scoring based on temperature and wind effects
        var impact = 0.0
        var tempFactor = Math.abs(temperatureCelsius - 20.0).toDouble() / 20.0  // Normalized to 20°C
        if (tempFactor > 1) tempFactor = 1.0
        
        // Wind effect on efficiency  
        val windFactor = when {
            windSpeedKmh != null && windSpeedKmh > 60 -> 0.8
            windSpeedKmh != null && windSpeedKmh > 40 -> 0.5 
            windSpeedKmh != null && windSpeedKmh > 20 -> 0.2
            else -> 0.0
        }
        
        return (100 - (tempFactor * 30 + windFactor * 70)).toInt()
    }

    /**
     * Checks if current weather conditions are safe for EV operation.
     */
    fun isEVSafeForDriving(): Boolean {
        // Check for dangerous conditions that could affect EV performance
        return when {
            hasIce -> false
            precipitationMm?.let { it > 50.0 } == true -> false  // Heavy rain/snow
            windSpeedKmh?.let { it > 100.0 } == true -> false   // Extremely windy  
            else -> isDataValid && temperatureCelsius != null
        }
    }

    /**
     * Gets the recommended driving behavior for EVs under these conditions.
     */
    fun getRecommendedDrivingBehavior(): DrivingRecommendation {
        return when {
            hasIce -> DrivingRecommendation.SLOW_DOWN_AND_AVOID_SUSTAINED_ACCELERATION
            else -> DrivingRecommendation.NORMAL
        }
    }

    /**
     * Determines if the weather will impact charging speed.
     */
    fun affectsChargingEfficiency(): Boolean {
        return (temperatureCelsius?.let { it < -10.0 || it > 40.0 } == true) ||
                hasIce ||
                precipitationType === PrecipitationType.SNOW
    }
    
    companion object {
        
        /**
         * Creates default weather conditions for offline use.
         */
        @JvmStatic
        fun createDefault(
            coordinate: GeoCoordinate,
            timestamp: Long = System.currentTimeMillis()
        ): WeatherConditions {
            return WeatherConditions(
                timestamp = timestamp,
                coordinate = coordinate,
                temperatureCelsius = 20.0,
                feelsLikeTemperatureCelsius = 20.0, 
                humidityPercent = 60.0,
                atmosphericPressureHpa = 1013.25,
                precipitationMm = 0.0,
                precipitationProbabilityPercent = 0.0,
                windSpeedKmh = 10.0,
                windDirectionDegrees = 0.0,
                gustSpeedKmh = 15.0,
                visibilityMeters = 10000.0,
                cloudCoverPercent = 20.0,
                precipitationType = PrecipitationType.NONE,
                weatherCode = "0",
                airDensityKgPerCubicMeter = 1.225,
                dataSource = WeatherDataSource.OFFLINE_DATABASE,
                dataQuality = WeatherDataQuality.HIGH,
                isPrecipitationHeavy = false,
                hasFog = false,
                hasSnow = false,
                hasIce = false,
                hasHighWindConditions = false,
                estimatedRangeReductionPercent = 0.0,
                impactOnChargingTime = ChargingImpactType.NORMAL,
                recommendedSpeedLimitKmh = 120.0,
                sourceSystem = "DEFAULT_WEATHER_ENGINE",
                cacheTimestamp = timestamp,
                validityPeriodSeconds = 3600,
                roadCondition = RoadConditionType.DRY,
                tireGripFactor = 0.85,
                sunriseTime = null,
                sunsetTime = null,
                thermalComfortIndex = 12.0,
                dewPointCelsius = 10.0,
                isDataValid = true,
                dataConfidenceScore = 1.0,
                batteryHeatingRequired = false,
                climateControlPowerConsumptionWatts = 500.0,
                temperatureFahrenheit = 68.0,
                windSpeedMph = 6.2,
                version = "1.0",
                lastUpdated = timestamp
            )
        }
    }
}

enum class PrecipitationType {
    NONE, RAIN, SNOW, SLEET, HAIL, DRIZZLE
}

enum class WeatherDataSource {
    OFFLINE_DATABASE, SENSOR, FORECAST, CACHE, NETWORK
}

enum class WeatherDataQuality {
    LOW, MEDIUM, HIGH, EXCELLENT
}

enum class ChargingImpactType {
    NONE, NORMAL, SLOWER, SIGNIFICANTLY_SLOWER, SEVERELY_REDUCED
}

enum class RoadConditionType {
    DRY, WET, ICY, SNOWY, FLOODED
}

enum class DrivingRecommendation {
    NORMAL, SLOW_DOWN, SLOW_DOWN_AND_AVOID_SUSTAINED_ACCELERATION, CAUTION, DO_NOT_DRIVE
}