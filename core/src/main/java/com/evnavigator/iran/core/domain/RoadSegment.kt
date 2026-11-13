package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a segment of a road with specific characteristics.
 *
 * @param id Unique identifier for the road segment
 * @param roadId ID of the parent road
 * @param startCoordinate Start geographic coordinate
 * @param endCoordinate End geographic coordinate
 * @param lengthMeters Length in meters
 * @param gradientPercent Gradient percentage (positive = uphill, negative = downhill)
 * @param surfaceType Type of road surface
 * @param isTollSection Whether this segment requires toll payment
 */
@Parcelize
data class RoadSegment(
    val id: String,
    val roadId: String,
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val lengthMeters: Double,
    val gradientPercent: Double,
    val surfaceType: SurfaceType = SurfaceType.ASPHALT,
    val isTollSection: Boolean = false
) : Parcelable {
    
    init {
        require(lengthMeters > 0) { "Length must be positive" }
    }

    /**
     * Calculates the distance between start and end coordinates
     */
    val distanceMeters: Double
        get() = lengthMeters

    /**
     * Gets the midpoint coordinate of this segment
     */
    val midpoint: GeoCoordinate
        get() = GeoCoordinate(
            (startCoordinate.latitude + endCoordinate.latitude) / 2.0,
            (startCoordinate.longitude + endCoordinate.longitude) / 2.0
        )

    /**
     * Calculates the elevation change in meters
     */
    val elevationChangeMeters: Double
        get() = gradientPercent * lengthMeters / 100.0
}

/**
 * Enum representing different types of road surfaces
 */
enum class SurfaceType {
    ASPHALT,      // Asphalt surface
    CONCRETE,     // Concrete surface
    GRAVEL,       // Gravel surface
    DIRT,         // Dirt surface
    PAVED,        // Paved surface
    UNPAVED       // Unpaved surface
}