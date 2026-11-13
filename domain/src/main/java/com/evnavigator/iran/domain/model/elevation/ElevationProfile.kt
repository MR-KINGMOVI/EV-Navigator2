package com.evnavigator.iran.domain.model.elevation

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Elevation
import com.evnavigator.iran.domain.model.ElevationQuality
import com.evnavigator.iran.domain.model.GeoCoordinate
import java.util.*

/**
 * Represents an elevation profile along a route or path.
 *
 * This model captures elevation variations over a geographic path,
 * including both the elevation data points and metadata about the profile itself.
 */
data class ElevationProfile(
    val samples: List<ElevationSample>,
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val totalDistance: Distance,
    val minElevation: Double,  // meters
    val maxElevation: Double,  // meters
    val elevationGain: Double,  // meters
    val elevationLoss: Double,  // meters
    val timeToComplete: Long? = null,  // seconds
    val source: String,
    val quality: ElevationQuality,
    val createdAt: Date = Date()
) {
    /**
     * Gets the average slope of the route in degrees.
     */
    val averageSlope: Double
        get() = if (totalDistance.value > 0) {
            // Simplified calculation - real implementation may need more complex trigonometry
            val elevationDiff = maxElevation - minElevation
            val groundDistance = Math.sqrt(
                Math.pow(endCoordinate.latitude - startCoordinate.latitude, 2.0)
                        + Math.pow(endCoordinate.longitude - startCoordinate.longitude, 2.0)
            )
            if (groundDistance > 0) {
                Math.atan(elevationDiff / groundDistance) * 180.0 / Math.PI
            } else {
                0.0
            }
        } else {
            0.0
        }

    /**
     * Gets the elevation change per kilometer.
     */
    val elevationPerKilometer: Double
        get() = if (totalDistance.value > 0) {
            val distanceInKm = totalDistance.inKilometers()
            if (distanceInKm > 0) {
                (maxElevation - minElevation) / distanceInKm
            } else {
                0.0
            }
        } else {
            0.0
        }

    companion object {
        /**
         * Creates a new ElevationProfile.
         */
        fun create(
            samples: List<ElevationSample>,
            startCoordinate: GeoCoordinate,
            endCoordinate: GeoCoordinate,
            totalDistance: Distance,
            timeToComplete: Long? = null,
            source: String,
            quality: ElevationQuality
        ): ElevationProfile {
            if (samples.isEmpty()) {
                throw IllegalArgumentException("Samples list cannot be empty")
            }

            val elevations = samples.map { it.elevation }
            val minElevation = elevations.minOrNull() ?: 0.0
            val maxElevation = elevations.maxOrNull() ?: 0.0

            // Calculate elevation gain and loss (simplified)
            var elevationGain = 0.0
            var elevationLoss = 0.0
            
            for (i in 1 until samples.size) {
                val diff = samples[i].elevation - samples[i-1].elevation
                if (diff > 0) {
                    elevationGain += diff
                } else {
                    elevationLoss -= diff
                }
            }

            return ElevationProfile(
                samples,
                startCoordinate,
                endCoordinate,
                totalDistance,
                minElevation,
                maxElevation,
                elevationGain,
                elevationLoss,
                timeToComplete,
                source,
                quality
            )
        }
    }
}