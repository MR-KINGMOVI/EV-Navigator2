package com.evnavigator.iran.core.weather.model

/**
 * Types of traffic incidents that can affect route planning and navigation.
 */
enum class IncidentType {
    /**
     * Traffic accident involving vehicles
     */
    ACCIDENT,
    
    /**
     * Road closure due to construction or maintenance
     */
    ROAD_CLOSURE,
    
    /**
     * Construction zone with road work
     */
    CONSTRUCTION,
    
    /**
     * Hazardous conditions (e.g., debris, oil spill)
     */
    HAZARD,
    
    /**
     * Weather-related hazard (e.g., flooding, severe weather)
     */
    WEATHER_HAZARD,
    
    /**
     * Event or special occasion affecting traffic (e.g., parade, concert)
     */
    EVENT,
    
    /**
     * Lane restriction due to traffic management
     */
    LANE_RESTRICTION,
    
    /**
     * Other types of incidents not covered by the above categories
     */
    OTHER,
    
    /**
     * Incident type is unknown or cannot be classified
     */
    UNKNOWN
}