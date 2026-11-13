package com.evnavigator.iran.domain.model

/**
 * Represents a point with elevation data.
 *
 * @param coordinate The geographic coordinate
 * @param elevation The elevation at this point
 */
data class ElevationPoint(
    val coordinate: GeoCoordinate,
    val elevation: Elevation
)