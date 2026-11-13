package com.evnavigator.iran.core.domain

import kotlin.math.*

/**
 * Utility class for calculating distances between geographic coordinates
 */
object DistanceCalculator {
    
    /**
     * Earth radius in meters used for distance calculations
     */
    private const val EARTH_RADIUS_METERS = 6371000.0
    
    private fun toRadians(degrees: Double): Double = Math.toRadians(degrees)
    
    /**
     * Calculates the distance to another geographic coordinate using the Haversine formula
     * @param from The starting geographic coordinate
     * @param to The destination geographic coordinate
     * @return Distance in meters
     */
    fun calculateDistance(from: GeoCoordinate, to: GeoCoordinate): Double {
        // Handle identical points
        if (from.latitude == to.latitude && from.longitude == to.longitude) {
            return 0.0
        }
        
        val lat1Rad = toRadians(from.latitude)
        val lat2Rad = toRadians(to.latitude)
        val deltaLatRad = toRadians(to.latitude - from.latitude)
        val deltaLonRad = toRadians(to.longitude - from.longitude)
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_METERS * c
    }
    
    /**
     * Calculates cumulative distances along a route
     * @param coordinates List of geographic coordinates representing a route
     * @return List of cumulative distances in meters (first value is 0.0)
     */
    fun calculateCumulativeDistances(coordinates: List<GeoCoordinate>): List<Double> {
        if (coordinates.isEmpty()) {
            return emptyList()
        }
        
        val cumulativeDistances = mutableListOf<Double>()
        cumulativeDistances.add(0.0) // First point starts at zero
        
        for (i in 1 until coordinates.size) {
            val prevCoord = coordinates[i - 1]
            val currCoord = coordinates[i]
            
            val distance = calculateDistance(prevCoord, currCoord)
            val lastDistance = cumulativeDistances.last()
            cumulativeDistances.add(lastDistance + distance)
        }
        
        return cumulativeDistances
    }
    
    /**
     * Calculates the total distance of a route
     * @param coordinates List of geographic coordinates representing a route
     * @return Total distance in meters
     */
    fun calculateTotalDistance(coordinates: List<GeoCoordinate>): Double {
        if (coordinates.size < 2) {
            return 0.0
        }
        
        val cumulativeDistances = calculateCumulativeDistances(coordinates)
        return cumulativeDistances.last()
    }
}