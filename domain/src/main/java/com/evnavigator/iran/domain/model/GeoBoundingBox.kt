package com.evnavigator.iran.domain.model

/**
 * Represents a geographic bounding box.
 *
 * @param southwest The southwest corner of the bounding box
 * @param northeast The northeast corner of the bounding box
 */
data class GeoBoundingBox(
    val southwest: GeoPoint,
    val northeast: GeoPoint
) {
    init {
        require(southwest.latitude <= northeast.latitude) {
            "Southwest latitude must be less than or equal to northeast latitude"
        }
        require(southwest.longitude <= northeast.longitude) {
            "Southwest longitude must be less than or equal to northeast longitude"
        }
    }

    /**
     * Gets the bounds of this bounding box.
     */
    val bounds: GeoBounds
        get() = GeoBounds(
            minCoordinate = southwest.toGeoCoordinate(),
            maxCoordinate = northeast.toGeoCoordinate()
        )

    /**
     * Checks if a coordinate is within this bounding box.
     */
    fun contains(coordinate: GeoCoordinate): Boolean {
        return bounds.contains(coordinate)
    }

    companion object {
        /**
         * Creates a GeoBoundingBox from southwest and northeast coordinates.
         */
        fun of(southwest: GeoCoordinate, northeast: GeoCoordinate): GeoBoundingBox {
            return GeoBoundingBox(
                GeoPoint.fromGeoCoordinate(southwest),
                GeoPoint.fromGeoCoordinate(northeast)
            )
        }
    }
}