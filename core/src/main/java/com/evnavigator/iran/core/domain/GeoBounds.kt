package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a geographic bounding box defined by minimum and maximum coordinates.
 *
 * @param southWest The southwest corner of the bounding box
 * @param northEast The northeast corner of the bounding box
 */
@Parcelize
data class GeoBounds(
    val southWest: GeoCoordinate,
    val northEast: GeoCoordinate
) : Parcelable {
    
    init {
        require(southWest.latitude <= northEast.latitude) {
            "Southwest latitude must be less than or equal to northeast latitude"
        }
        require(southWest.longitude <= northEast.longitude) {
            "Southwest longitude must be less than or equal to northeast longitude"
        }
    }

    /**
     * Gets the center coordinate of the bounding box
     */
    val center: GeoCoordinate
        get() = GeoCoordinate(
            (southWest.latitude + northEast.latitude) / 2.0,
            (southWest.longitude + northEast.longitude) / 2.0
        )

    /**
     * Checks if a coordinate is within the bounds
     */
    fun contains(coordinate: GeoCoordinate): Boolean {
        return coordinate.latitude >= southWest.latitude &&
                coordinate.latitude <= northEast.latitude &&
                coordinate.longitude >= southWest.longitude &&
                coordinate.longitude <= northEast.longitude
    }

    /**
     * Checks if another bounds object intersects with this one
     */
    fun intersects(other: GeoBounds): Boolean {
        return southWest.latitude <= other.northEast.latitude &&
                northEast.latitude >= other.southWest.latitude &&
                southWest.longitude <= other.northEast.longitude &&
                northEast.longitude >= other.southWest.longitude
    }
}