package com.evnavigator.iran.core.weather.model

/**
 * Traffic congestion classification levels.
 * Used to categorize traffic conditions for route planning and energy calculations.
 */
enum class TrafficClass {
    /**
     * Free-flow traffic with minimal delays
     */
    FREE_FLOW,
    
    /**
     * Light traffic with slight delays
     */
    LIGHT,
    
    /**
     * Moderate traffic with noticeable delays
     */
    MODERATE,
    
    /**
     * Heavy traffic with significant delays
     */
    HEAVY,
    
    /**
     * Severe traffic with very long delays or gridlock
     */
    SEVERE,
    
    /**
     * Traffic condition is unknown or cannot be determined
     */
    UNKNOWN
}