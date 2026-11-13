package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a complete trip with start and end points, route information, and energy consumption.
 *
 * @param id Unique identifier for the trip
 * @param name Name of the trip
 * @param startTime Unix timestamp when the trip started
 * @param endTime Unix timestamp when the trip ended
 * @param startCoordinate Start geographic coordinate
 * @param endCoordinate End geographic coordinate
 * @param route Route taken for this trip
 * @param vehicle Vehicle used for this trip
 * @param energyResult Energy consumption and efficiency results
 * @param weatherSnapshot Weather conditions during the trip
 * @param isCompleted Whether the trip has been completed
 */
@Parcelize
data class Trip(
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val route: Route,
    val vehicle: Vehicle,
    val energyResult: EnergyResult,
    val weatherSnapshot: WeatherSnapshot?,
    val isCompleted: Boolean = false
) : Parcelable {
    
    init {
        require(endTime >= startTime) { "End time must be after or equal to start time" }
        require(energyResult.totalDistanceMeters >= 0) { "Distance cannot be negative" }
    }

    /**
     * Calculates the duration of the trip in seconds
     */
    val durationSeconds: Long
        get() = endTime - startTime

    /**
     * Gets the duration of the trip in minutes
     */
    val durationMinutes: Double
        get() = durationSeconds / 60.0

    /**
     * Calculates the average speed during the trip (km/h)
     */
    val averageSpeedKmh: Double
        get() = if (durationSeconds > 0) {
            (route.totalDistanceMeters / 1000.0) / (durationSeconds / 3600.0)
        } else {
            0.0
        }

    /**
     * Gets the total distance traveled in kilometers
     */
    val distanceKm: Double
        get() = route.totalDistanceMeters / 1000.0

    /**
     * Calculates the energy consumption per kilometer (kWh/km)
     */
    val energyPerKilometerKWh: Double
        get() = energyResult.energyPerKilometerKWh

    /**
     * Gets the start coordinate of the trip
     */
    val startPoint: GeoCoordinate
        get() = startCoordinate

    /**
     * Gets the end coordinate of the trip
     */
    val endPoint: GeoCoordinate
        get() = endCoordinate

    /**
     * Checks if this was a long trip (more than 100km)
     */
    val isLongTrip: Boolean
        get() = distanceKm > 100.0

    /**
     * Calculates the cost per kilometer in Iranian Rials
     */
    val costPerKilometerRials: Double
        get() = energyResult.costPerKilometerRials
}