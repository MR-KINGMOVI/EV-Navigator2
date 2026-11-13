package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a complete route from start to end point.
 *
 * @param id Unique identifier for the route
 * @param name Name of the route
 * @param startCoordinate Start geographic coordinate
 * @param endCoordinate End geographic coordinate
 * @param segments List of road segments that make up this route
 * @param totalDistanceMeters Total distance of the route in meters
 * @param estimatedDurationSeconds Estimated travel time in seconds
 * @param elevationGainMeters Total elevation gain in meters
 * @param isChargingRequired Whether charging is required along the route
 */
@Parcelize
data class Route(
    val id: String,
    val name: String,
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val segments: List<RouteSegment>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Long,
    val elevationGainMeters: Double,
    val isChargingRequired: Boolean = false
) : Parcelable {
    
    init {
        require(totalDistanceMeters > 0) { "Total distance must be positive" }
        require(estimatedDurationSeconds >= 0) { "Duration cannot be negative" }
        require(elevationGainMeters >= 0) { "Elevation gain cannot be negative" }
        require(segments.isNotEmpty()) { "Route must have at least one segment" }
    }

    /**
     * Gets the start coordinate of the route
     */
    val startPoint: GeoCoordinate
        get() = startCoordinate

    /**
     * Gets the end coordinate of the route
     */
    val endPoint: GeoCoordinate
        get() = endCoordinate

    /**
     * Calculates the average speed for the entire route (km/h)
     */
    val averageSpeedKmh: Double
        get() = if (estimatedDurationSeconds > 0) {
            (totalDistanceMeters / 1000.0) / (estimatedDurationSeconds / 3600.0)
        } else {
            0.0
        }

    /**
     * Gets the total number of segments in the route
     */
    val segmentCount: Int
        get() = segments.size

    /**
     * Gets the total elevation loss in meters (negative value)
     */
    val elevationLossMeters: Double
        get() = segments.sumOf { it.gradientPercent * it.lengthMeters / 100.0 }
            .takeIf { it < 0 } ?: 0.0

    /**
     * Checks if the route is suitable for electric vehicles (no extreme elevation changes)
     */
    val isEVCompatible: Boolean
        get() = kotlin.math.abs(elevationGainMeters) < 1000 && // Less than 1km elevation change
                segments.all { it.gradientPercent < 20.0 } // No steep inclines
}