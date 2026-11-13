package com.evnavigator.iran.core.charging

/**
 * Geographic coordinate representation for charging stations.
 *
 * This model supports:
 * - Latitude and longitude coordinates
 * - Coordinate validation
 * - Geographic precision
 * - Location provenance
 * - Optional address information
 */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
    val precision: Double? = null, // Precision in meters or degrees
    val source: String? = null, // Source of the coordinate (e.g., "GPS", "MapProvider")
    val sourceReference: String? = null, // Reference identifier from source
    val enforceBounds: Boolean = true
) {
    
    init {
        if (enforceBounds) {
            require(latitude >= -90.0 && latitude <= 90.0) { 
                "Latitude must be between -90 and 90 degrees" 
            }
            require(longitude >= -180.0 && longitude <= 180.0) {
                "Longitude must be between -180 and 180 degrees"
            }
        }
        precision?.let {
            require(it >= 0.0) { "Precision cannot be negative" }
        }
    }
    
    /**
     * Creates a GeoCoordinate with default values
     */
    constructor(latitude: Double, longitude: Double) : this(
        latitude = latitude,
        longitude = longitude,
        precision = null,
        source = null,
        sourceReference = null
    )
    
    /**
     * Gets the coordinate as a string representation
     */
    fun toCoordinateString(): String {
        return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
    }
    
    /**
     * Checks if this coordinate is valid (within geographic bounds)
     */
    val isValid: Boolean
        get() = latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0
}
