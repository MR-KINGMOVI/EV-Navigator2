package com.evnavigator.iran.domain.model.elevation

import com.evnavigator.iran.domain.model.Latitude
import com.evnavigator.iran.domain.model.Longitude

/**
 * Represents a bounding box for elevation data coverage.
 *
 * Defines the geographic boundaries of an elevation dataset using minimum and maximum coordinates.
 */
data class ElevationBoundingBox(
    val minLat: Latitude,
    val minLng: Longitude,
    val maxLat: Latitude,
    val maxLng: Longitude
) {
    companion object {
        /**
         * Creates a new ElevationBoundingBox.
         *
         * @param minLat Minimum latitude
         * @param minLng Minimum longitude
         * @param maxLat Maximum latitude
         * @param maxLng Maximum longitude
         */
        fun create(
            minLat: Latitude,
            minLng: Longitude,
            maxLat: Latitude,
            maxLng: Longitude
        ): ElevationBoundingBox {
            return ElevationBoundingBox(minLat, minLng, maxLat, maxLng)
        }
    }

    /**
     * Gets the center point of this bounding box.
     */
    val center: com.evnavigator.iran.domain.model.GeoCoordinate
        get() = com.evnavigator.iran.domain.model.GeoCoordinate(
            latitude = (maxLat.value + minLat.value) / 2.0,
            longitude = (maxLng.value + minLng.value) / 2.0
        )

    /**
     * Gets the width of this bounding box in degrees.
     */
    val width: Double
        get() = maxLng.value - minLng.value

    /**
     * Gets the height of this bounding box in degrees.
     */
    val height: Double
        get() = maxLat.value - minLat.value

    /**
     * Checks if a given coordinate is within this bounding box.
     *
     * @param coordinate The coordinate to check
     * @return True if the coordinate is inside or on the boundary of the bbox
     */
    fun contains(coordinate: com.evnavigator.iran.domain.model.GeoCoordinate): Boolean {
        return coordinate.latitude >= minLat.value &&
                coordinate.latitude <= maxLat.value &&
                coordinate.longitude >= minLng.value &&
                coordinate.longitude <= maxLng.value
    }
}