package com.evnavigator.iran.core.weather.model

/**
 * Status of a traffic incident.
 */
enum class IncidentStatus {
    /**
     * Incident is currently active and affecting traffic
     */
    ACTIVE,
    
    /**
     * Incident has been resolved and no longer affects traffic
     */
    RESOLVED,
    
    /**
     * Incident was cancelled or does not occur
     */
    CANCELLED,
    
    /**
     * Incident status is unknown or cannot be determined
     */
    UNKNOWN
}