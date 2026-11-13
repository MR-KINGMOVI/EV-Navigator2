package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.navigation.WeatherStatus
import com.evnavigator.iran.domain.model.navigation.TrafficStatus

/**
 * Interface for providing environmental information for navigation.
 */
interface NavigationEnvironmentProvider {
    /**
     * Get current weather status.
     */
    fun getWeatherStatus(): WeatherStatus

    /**
     * Get current traffic status.
     */
    fun getTrafficStatus(): TrafficStatus

    /**
     * Get environmental impact on navigation.
     */
    fun getEnvironmentalImpact(): EnvironmentalImpact?
}

/**
 * Represents environmental impact on navigation.
 */
data class EnvironmentalImpact(
    val weatherImpact: Double, // 0.0 to 1.0 - how much weather affects efficiency
    val trafficImpact: Double, // 0.0 to 1.0 - how much traffic affects travel time
    val temperature: Double? // Temperature in Celsius
)