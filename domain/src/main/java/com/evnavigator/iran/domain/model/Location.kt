package com.evnavigator.iran.domain.model

import kotlinx.serialization.Serializable

/**
 * Geographic location with coordinates, optional altitude, accuracy, and timestamp.
 * Pure Kotlin/JVM domain model (independent of Android framework).
 */
@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null,
    val accuracyMeters: Float? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    fun toGeoCoordinate(): GeoCoordinate = GeoCoordinate(latitude, longitude)
}
