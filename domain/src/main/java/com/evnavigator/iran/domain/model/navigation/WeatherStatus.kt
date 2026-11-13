package com.evnavigator.iran.domain.model.navigation

/**
 * Represents current weather conditions affecting navigation.
 */
enum class WeatherStatus {
    /**
     * Clear skies, good visibility.
     */
    CLEAR,

    /**
     * Partly cloudy.
     */
    PARTLY_CLOUDY,

    /**
     * Overcast.
     */
    OVERCAST,

    /**
     * Rainy conditions.
     */
    RAINY,

    /**
     * Snowy conditions.
     */
    SNOWY,

    /**
     * Foggy conditions.
     */
    FOGGY,

    /**
     * Windy conditions.
     */
    WINDY,

    /**
     * Stormy conditions.
     */
    STORMY,

    /**
     * Weather data is unavailable.
     */
    UNAVAILABLE,

    /**
     * Weather status is unknown.
     */
    UNKNOWN
}