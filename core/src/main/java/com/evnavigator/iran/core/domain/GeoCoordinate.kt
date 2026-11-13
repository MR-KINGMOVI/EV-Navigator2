package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a geographic coordinate with latitude and longitude.
 *
 * @param latitude Latitude in degrees (-90 to 90)
 * @param longitude Longitude in degrees (-180 to 180)
 */
@Parcelize
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double
) : Parcelable {
    
    val isValid: Boolean get() = CoordinateValidator.isValidCoordinate(this)

    /**
     * Creates a new GeoCoordinate with the specified latitude and longitude
     */
    constructor(latitude: Float, longitude: Float) : this(latitude.toDouble(), longitude.toDouble())

    /**
     * Calculates the distance to another coordinate in meters using Haversine formula
     */
    fun distanceTo(other: GeoCoordinate): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(other.latitude)
        val lon2 = Math.toRadians(other.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        // Earth's radius in meters
        val earthRadius = 6371000.0

        return earthRadius * c
    }

    /**
     * Calculates the bearing (azimuth) to another coordinate in degrees
     */
    fun bearingTo(other: GeoCoordinate): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(other.latitude)
        val lon2 = Math.toRadians(other.longitude)

        val dLon = lon2 - lon1

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

        val bearing = Math.atan2(y, x)
        return (Math.toDegrees(bearing) + 360) % 360
    }

    /**
     * Checks if this coordinate is within a certain distance of another coordinate
     */
    fun isNearby(other: GeoCoordinate, maxDistanceMeters: Double): Boolean {
        return distanceTo(other) <= maxDistanceMeters
    }

    /**
     * Gets the string representation of the coordinate
     */
    override fun toString(): String {
        return "GeoCoordinate(lat=$latitude, lon=$longitude)"
    }
}