package com.evnavigator.iran.core.weather.model

/**
 * Canonical weather condition classifications for EV navigation purposes.
 */
enum class WeatherCondition {
    /**
     * Clear skies, no precipitation
     */
    CLEAR,
    
    /**
     * Partly cloudy conditions
     */
    PARTLY_CLOUDY,
    
    /**
     * Overcast with low cloud cover
     */
    CLOUDY,
    
    /**
     * Light rain
     */
    LIGHT_RAIN,
    
    /**
     * Moderate rain
     */
    MODERATE_RAIN,
    
    /**
     * Heavy rain
     */
    HEAVY_RAIN,
    
    /**
     * Light snow
     */
    LIGHT_SNOW,
    
    /**
     * Moderate snow
     */
    MODERATE_SNOW,
    
    /**
     * Heavy snow
     */
    HEAVY_SNOW,
    
    /**
     * Sleet conditions
     */
    SLEET,
    
    /**
     * Thunderstorm with lightning
     */
    THUNDERSTORM,
    
    /**
     * Dense fog
     */
    FOGGY,
    
    /**
     * Dust or sand storm
     */
    DUST_STORM,
    
    /**
     * Hail conditions
     */
    HAIL,
    
    /**
     * Windy conditions
     */
    WINDY,
    
    /**
     * Storm conditions with high winds and precipitation
     */
    STORM,
    RAIN,
    SNOW,
    
    /**
     * Unknown or unavailable weather condition
     */
    UNKNOWN
}