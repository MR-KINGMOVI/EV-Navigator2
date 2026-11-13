package com.evnavigator.iran.domain.model

/**
 * Represents geographic bounds defined by minimum and maximum coordinates.
 *
 * @param minCoordinate The minimum coordinate (southwest)
 * @param maxCoordinate The maximum coordinate (northeast)
 */
data class GeoBounds(
    val minCoordinate: GeoCoordinate,
    val maxCoordinate: GeoCoordinate
) {
    /**
     * Gets the center coordinate of the bounds.
     */
    val center: GeoCoordinate
        get() = GeoCoordinate(
            (minCoordinate.latitude + maxCoordinate.latitude) / 2,
            (minCoordinate.longitude + maxCoordinate.longitude) / 2
        )

    /**
     * Checks if a coordinate is within these bounds.
     */
    fun contains(coordinate: GeoCoordinate): Boolean {
        return coordinate.latitude >= minCoordinate.latitude &&
                coordinate.latitude <= maxCoordinate.latitude &&
                coordinate.longitude >= minCoordinate.longitude &&
                coordinate.longitude <= maxCoordinate.longitude
    }

    companion object {
        /**
         * Creates GeoBounds from two coordinates.
         */
        fun of(minCoordinate: GeoCoordinate, maxCoordinate: GeoCoordinate): GeoBounds {
            return GeoBounds(minCoordinate, maxCoordinate)
        }
    }
}