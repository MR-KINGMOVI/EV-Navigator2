package com.evnavigator.iran.domain.model.weather

/**
 * Enum representing atmospheric pressure trends.
 *
 * Used to indicate whether pressure is rising, falling, or steady,
 * which can help in weather prediction and assessing driving conditions.
 */
enum class PressureTrend {
    /**
     * Pressure is increasing
     */
    INCREASING,

    /**
     * Pressure is decreasing  
     */
    DECREASING,

    /**
     * Pressure is relatively stable
     */
    STEADY,

    /**
     * Unknown pressure trend
     */
    UNKNOWN
}