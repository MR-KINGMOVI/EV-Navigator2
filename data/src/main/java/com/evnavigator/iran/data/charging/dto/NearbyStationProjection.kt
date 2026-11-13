package com.evnavigator.iran.data.charging.dto

/**
 * Projection DTO for nearby charging station queries.
 *
 * This DTO is used to reduce memory usage and unnecessary data transfer
 * when querying charging stations for nearby search, radius search, and
 * geographic bounding box queries.
 *
 * Contains only the fields needed for spatial queries and display.
 */
data class NearbyStationProjection(
    /**
     * Station unique identifier
     */
    val id: String,

    /**
     * Canonical name of the station
     */
    val canonicalName: String,

    /**
     * Latitude coordinate (WGS84)
     */
    val latitude: Double,

    /**
     * Longitude coordinate (WGS84)
     */
    val longitude: Double,

    /**
     * Operator name
     */
    val operatorName: String? = null,

    /**
     * Province where the station is located
     */
    val province: String? = null,

    /**
     * City where the station is located
     */
    val city: String? = null,

    /**
     * Access type for the station
     */
    val accessType: String,

    /**
     * Number of connectors at the station
     */
    val connectorCount: Int,

    /**
     * Maximum power in kW
     */
    val powerKw: Double,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double,

    /**
     * Validation status of this information
     */
    val validationStatus: String,

    /**
     * Timestamp when this data was retrieved
     */
    val retrievedDate: Long
) {
    /**
     * Calculate distance from this station to a target location in meters.
     *
     * Uses Haversine formula for accurate distance calculation.
     *
     * @param targetLat Target latitude
     * @param targetLng Target longitude
     * @return Distance in meters
     */
    fun distanceTo(targetLat: Double, targetLng: Double): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters

        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(targetLat)
        val deltaLat = Math.toRadians(targetLat - latitude)
        val deltaLng = Math.toRadians(targetLng - longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Check if this station is expired based on confidence threshold.
     *
     * @param confidenceThreshold Minimum confidence required (default 0.5)
     * @return true if confidence is below threshold
     */
    fun isExpired(confidenceThreshold: Double = 0.5): Boolean {
        return confidence < confidenceThreshold
    }

    /**
     * Check if this station is valid based on validation status.
     *
     * @return true if validation status is VALID
     */
    fun isValid(): Boolean {
        return validationStatus == "VALID" || validationStatus == "VALIDATED" || validationStatus == "VERIFIED"
    }

    /**
     * Check if this station is stale based on age.
     *
     * @param maxAgeMs Maximum age in milliseconds (default 30 days)
     * @return true if station is older than max age
     */
    fun isStale(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000): Boolean {
        val age = System.currentTimeMillis() - retrievedDate
        return age > maxAgeMs
    }

    /**
     * Get the age of this station in milliseconds.
     *
     * @return Age in milliseconds
     */
    fun getAge(): Long {
        return System.currentTimeMillis() - retrievedDate
    }
}
