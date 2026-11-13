package com.evnavigator.iran.core.weather.model

/**
 * Freshness levels for weather data.
 * Indicates how current the weather information is.
 */
enum class WeatherFreshness {
    /**
     * Data is extremely fresh (less than 1 minute old)
     */
    FRESH,
    STALE_BUT_USABLE,
    EXPIRED,
    EXTREMELY_FRESH,
    
    /**
     * Data is very fresh (less than 5 minutes old)
     */
    VERY_FRESH,
    
    /**
     * Data is reasonably fresh (less than 10 minutes old)
     */
    REASONABLY_FRESH,
    
    /**
     * Data is somewhat stale (less than 30 minutes old)
     */
    SOMEWHAT_STALE,
    
    /**
     * Data is stale (more than 30 minutes old)
     */
    STALE,
    
    /**
     * Weather data is unknown or cannot be determined
     */
    UNKNOWN
}