package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a polyline (sequence of coordinates) on the map.
 *
 * @param coordinates List of geographic coordinates forming the polyline
 */
@Parcelize
data class GeoPolyline(
    val coordinates: List<GeoCoordinate>
) : Parcelable {
    
    init {
        require(coordinates.isNotEmpty()) { "Coordinates list cannot be empty" }
    }

    /**
     * Gets the number of coordinates in the polyline
     */
    val size: Int
        get() = coordinates.size

    /**
     * Gets the first coordinate in the polyline
     */
    val startCoordinate: GeoCoordinate
        get() = coordinates.first()

    /**
     * Gets the last coordinate in the polyline
     */
    val endCoordinate: GeoCoordinate
        get() = coordinates.last()
}