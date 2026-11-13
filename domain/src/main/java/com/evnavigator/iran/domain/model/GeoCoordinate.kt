package com.evnavigator.iran.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic coordinate (WGS-84 latitude/longitude in degrees).
 * Pure Kotlin/JVM domain model (independent of Android framework).
 */
@Serializable
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    fun distanceTo(other: GeoCoordinate): Distance {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(other.latitude)
        val lon2 = Math.toRadians(other.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val earthRadius = 6371000.0 // meters

        return Distance(earthRadius * c)
    }

    companion object {
        val ZERO = GeoCoordinate(0.0, 0.0)
    }
}
