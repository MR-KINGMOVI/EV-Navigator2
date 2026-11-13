package com.evnavigator.iran.core.domain

import kotlin.math.*

/**
 * Utility class for validating geographic coordinates
 */
object CoordinateValidator {
    
    /**
     * Validates that a latitude value is within valid range (-90 to 90)
     * @param latitude The latitude value to validate
     * @return true if valid, false otherwise
     */
    fun isValidLatitude(latitude: Double): Boolean {
        return !latitude.isNaN() && 
               !latitude.isInfinite() && 
               latitude >= -90.0 && 
               latitude <= 90.0
    }
    
    /**
     * Validates that a longitude value is within valid range (-180 to 180)
     * @param longitude The longitude value to validate
     * @return true if valid, false otherwise
     */
    fun isValidLongitude(longitude: Double): Boolean {
        return !longitude.isNaN() && 
               !longitude.isInfinite() && 
               longitude >= -180.0 && 
               longitude <= 180.0
    }
    
    /**
     * Validates that a geographic coordinate is within valid ranges
     * @param coordinate The coordinate to validate
     * @return true if valid, false otherwise
     */
    fun isValidCoordinate(coordinate: GeoCoordinate): Boolean {
        return isValidLatitude(coordinate.latitude) && 
               isValidLongitude(coordinate.longitude)
    }
    
    /**
     * Validates that a list of coordinates are all within valid ranges
     * @param coordinates The list of coordinates to validate
     * @return true if all are valid, false otherwise
     */
    fun isValidCoordinateList(coordinates: List<GeoCoordinate>): Boolean {
        return coordinates.all { isValidCoordinate(it) }
    }
    
    /**
     * Throws an exception for invalid coordinates (useful for debugging)
     * @param coordinate The invalid coordinate
     * @throws IllegalArgumentException if the coordinate is invalid
     */
    fun validateCoordinateOrThrow(coordinate: GeoCoordinate) {
        if (!isValidCoordinate(coordinate)) {
            throw IllegalArgumentException("Invalid coordinate: latitude=${coordinate.latitude}, longitude=${coordinate.longitude}")
        }
    }
    
    /**
     * Throws an exception for invalid coordinate lists (useful for debugging)
     * @param coordinates The list of coordinates to validate
     * @throws IllegalArgumentException if any coordinate is invalid
     */
    fun validateCoordinateListOrThrow(coordinates: List<GeoCoordinate>) {
        coordinates.forEach { validateCoordinateOrThrow(it) }
    }
}