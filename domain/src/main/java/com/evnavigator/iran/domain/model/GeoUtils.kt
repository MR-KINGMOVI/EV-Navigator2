package com.evnavigator.iran.domain.model

import kotlin.math.*

/**
 * Utility class for geographic calculations.
 */
object GeoUtils {
    /**
     * Earth's radius in meters
     */
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the distance between two geographic coordinates using the Haversine formula.
     *
     * @param from The starting coordinate
     * @param to The destination coordinate
     * @return Distance in meters
     */
    fun calculateDistance(from: GeoCoordinate, to: GeoCoordinate): Distance {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return Distance.ofMeters(EARTH_RADIUS_METERS * c)
    }

    /**
     * Calculates the bearing from one coordinate to another.
     *
     * @param from The starting coordinate
     * @param to The destination coordinate
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(from: GeoCoordinate, to: GeoCoordinate): Bearing {
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        val bearing = atan2(y, x)
        val degrees = Math.toDegrees(bearing)
        return Bearing.ofDegrees((degrees + 360) % 360)
    }

    /**
     * Calculates the destination coordinate given a starting point, bearing, and distance.
     *
     * @param from The starting coordinate
     * @param bearing The bearing in degrees (0-360)
     * @param distance The distance to travel in meters
     * @return The destination coordinate
     */
    fun calculateDestination(
        from: GeoCoordinate,
        bearing: Bearing,
        distance: Distance
    ): GeoCoordinate {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val bearingRad = Math.toRadians(bearing.value)
        val distanceRad = distance.value / EARTH_RADIUS_METERS
        
        val lat2 = asin(
            sin(lat1) * cos(distanceRad) +
            cos(lat1) * sin(distanceRad) * cos(bearingRad)
        )
        
        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(distanceRad) * cos(lat1),
            cos(distanceRad) - sin(lat1) * sin(lat2)
        )
        
        return GeoCoordinate(
            Math.toDegrees(lat2),
            (Math.toDegrees(lon2) + 540) % 360 - 180 // Normalize to [-180, 180]
        )
    }
}