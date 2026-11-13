package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a road segment with its characteristics.
 *
 * @param id Unique identifier for the road
 * @param name Name of the road
 * @param type Type of road (e.g., highway, street, rural)
 * @param polyline The geometric representation of the road
 * @param lengthMeters Length of the road in meters
 * @param speedLimitKmh Speed limit on this road segment in km/h
 * @param isTollRoad Whether this road requires toll payment
 */
@Parcelize
data class Road(
    val id: String,
    val name: String,
    val type: RoadType,
    val polyline: GeoPolyline,
    val lengthMeters: Double,
    val speedLimitKmh: Int,
    val isTollRoad: Boolean = false
) : Parcelable {
    
    init {
        require(lengthMeters > 0) { "Length must be positive" }
        require(speedLimitKmh >= 0) { "Speed limit cannot be negative" }
    }

    /**
     * Gets the start coordinate of the road
     */
    val startCoordinate: GeoCoordinate
        get() = polyline.startCoordinate

    /**
     * Gets the end coordinate of the road
     */
    val endCoordinate: GeoCoordinate
        get() = polyline.endCoordinate

    /**
     * Gets the average width of the road in meters (estimated)
     */
    val estimatedWidthMeters: Double
        get() = when (type) {
            RoadType.HIGHWAY -> 12.0
            RoadType.MOTORWAY -> 12.0
            RoadType.STREET -> 8.0
            RoadType.RURAL -> 6.0
            RoadType.PEDESTRIAN -> 4.0
        }
}

/**
 * Enum representing different types of roads
 */
enum class RoadType {
    HIGHWAY,      // Highway (e.g., Interstate)
    MOTORWAY,     // Motorway (e.g., Freeway)
    STREET,       // Regular street
    RURAL,        // Rural/remote road
    PEDESTRIAN    // Pedestrian walkway
}