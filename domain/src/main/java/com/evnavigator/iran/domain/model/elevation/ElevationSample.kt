package com.evnavigator.iran.domain.model.elevation

import com.evnavigator.iran.domain.model.*
import java.util.*

/**
 * Represents a sample of elevation data along a route or at a point.
 *
 * @param coordinate The geographic coordinate
 * @param elevation The elevation value (in meters)
 * @param accuracy Accuracy of the elevation measurement in meters
 * @param distanceAlongRoute The cumulative distance from the start of the route
 * @param timestamp Optional timestamp if provider data is temporal
 * @param source The source of this elevation data
 * @param quality The quality of this elevation sample
 */
data class ElevationSample(
    val coordinate: GeoCoordinate,
    val elevation: Double,  // meters above sea level
    val accuracy: Distance? = null,
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

    companion object {
        /**
         * Creates an ElevationSample with elevation and optional accuracy.
         */
        fun of(
            coordinate: GeoCoordinate,
            elevation: Double,
            accuracy: Distance? = null,
            distanceAlongRoute: Distance,
            timestamp: Long? = null,
            source: String,
            quality: ElevationQuality
        ): ElevationSample {
            return ElevationSample(coordinate, elevation, accuracy, distanceAlongRoute, timestamp, source, quality)
        }
    }
}