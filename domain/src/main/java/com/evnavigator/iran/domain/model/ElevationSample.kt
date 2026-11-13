package com.evnavigator.iran.domain.model

/**
 * Represents a sample of elevation data along a route.
 *
 * @param coordinate The geographic coordinate
 * @param elevation The elevation at this point
 * @param distanceAlongRoute The cumulative distance from the start of the route
 * @param timestamp Optional timestamp if provider data is temporal
 * @param source The source of this elevation data
 * @param quality The quality of this elevation sample
 */
data class ElevationSample(
    val coordinate: GeoCoordinate,
    val elevation: Elevation,
    val distanceAlongRoute: Distance,
    val timestamp: Long? = null,
    val source: String,
    val quality: ElevationQuality
) {
    init {
        require(distanceAlongRoute.value >= 0.0) {
            "Distance along route must be non-negative, got: ${distanceAlongRoute.value}"
        }
    }
}