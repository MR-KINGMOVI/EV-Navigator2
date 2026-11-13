package com.evnavigator.iran.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a geographic coordinate point with latitude, longitude, and optional altitude.
 * Pure Kotlin/JVM domain model (independent of Android framework).
 */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees, was $latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees, was $longitude" }
    }

    fun toGeoCoordinate(): GeoCoordinate = GeoCoordinate(latitude, longitude)

    companion object {
        fun fromGeoCoordinate(coord: GeoCoordinate, altitude: Double? = null): GeoPoint {
            return GeoPoint(coord.latitude, coord.longitude, altitude)
        }
    }
}

typealias Point = GeoPoint
