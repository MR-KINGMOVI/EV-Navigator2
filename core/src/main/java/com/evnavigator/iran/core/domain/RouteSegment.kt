package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a segment of a route with specific characteristics.
 *
 * @param id Unique identifier for the route segment
 * @param roadId ID of the parent road
 * @param startCoordinate Start geographic coordinate
 * @param endCoordinate End geographic coordinate
 * @param lengthMeters Length in meters
 * @param gradientPercent Gradient percentage (positive = uphill, negative = downhill)
 * @param surfaceType Type of road surface
 * @param isTollSection Whether this segment requires toll payment
 * @param estimatedDurationSeconds Estimated travel time in seconds
 * @param speedLimitKmh Speed limit on this segment
 */
@Parcelize
data class RouteSegment(
    val id: String,
    val roadId: String,
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val lengthMeters: Double,
    val gradientPercent: Double,
    val surfaceType: SurfaceType = SurfaceType.ASPHALT,
    val isTollSection: Boolean = false,
    val estimatedDurationSeconds: Long = 0L,
    val speedLimitKmh: Int = 0
) : Parcelable {

    constructor(
        id: String,
        distance: Double,
        duration: Double
    ) : this(
        id = id,
        roadId = "road_$id",
        startCoordinate = GeoCoordinate(35.6892, 51.3890),
        endCoordinate = GeoCoordinate(35.7000, 51.4000),
        lengthMeters = distance,
        gradientPercent = 0.0,
        estimatedDurationSeconds = duration.toLong()
    )
    
    init {
        require(lengthMeters > 0) { "Length must be positive" }
        require(estimatedDurationSeconds >= 0) { "Duration cannot be negative" }
        require(speedLimitKmh >= 0) { "Speed limit cannot be negative" }
    }

    /**
     * Calculates the distance between start and end coordinates
     */
    val distanceMeters: Double
        get() = lengthMeters

    val distance: com.evnavigator.iran.domain.model.Distance
        get() = com.evnavigator.iran.domain.model.Distance.fromMeters(lengthMeters)

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

    /**
     * Calculates the average speed for this segment (km/h)
     */
    val averageSpeedKmh: Double
        get() = if (estimatedDurationSeconds > 0) {
            (lengthMeters / 1000.0) / (estimatedDurationSeconds / 3600.0)
        } else {
            speedLimitKmh.toDouble()
        }

    val duration: Double
        get() = estimatedDurationSeconds.toDouble()
}
