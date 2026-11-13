package com.evnavigator.iran.core.domain

import com.evnavigator.iran.core.elevation.ElevationProvenance
import com.evnavigator.iran.core.elevation.ElevationQuality

/**
 * Represents an elevation point along a route
 *
 * @param coordinate Geographic coordinate
 * @param elevationMeters Elevation value in meters
 * @param accuracyMeters Accuracy of the elevation measurement in meters (optional)
 * @param quality Quality state of the elevation data
 * @param provenance Source/provenance of the elevation data
 * @param distanceFromStart Distance from start of route in meters
 */
data class ElevationPoint(
    val coordinate: GeoCoordinate,
    val elevationMeters: Double,
    val accuracyMeters: Double? = null,
    val quality: ElevationQuality = ElevationQuality.KNOWN,
    val provenance: ElevationProvenance = ElevationProvenance.UNKNOWN,
    val distanceFromStart: Float = 0f
) {
    /**
     * Checks if elevation data is valid (not unknown or invalid)
     */
    fun isValid(): Boolean {
        return quality != ElevationQuality.UNKNOWN && 
               quality != ElevationQuality.INVALID &&
               !elevationMeters.isNaN()
    }

    /**
     * Checks if elevation data is interpolated
     */
    fun isInterpolated(): Boolean {
        return provenance == ElevationProvenance.SYNTHETIC || 
               quality == ElevationQuality.INTERPOLATED
    }

    /**
     * Calculates the distance to another elevation point in meters
     */
    fun distanceTo(other: ElevationPoint): Double {
        return coordinate.distanceTo(other.coordinate)
    }

    companion object {
        /**
         * Creates an unknown elevation point
         */
        fun unknown(coordinate: GeoCoordinate): ElevationPoint {
            return ElevationPoint(
                coordinate = coordinate,
                elevationMeters = Double.NaN,
                quality = ElevationQuality.UNKNOWN,
                provenance = ElevationProvenance.UNKNOWN
            )
        }

        /**
         * Creates an invalid elevation point
         */
        fun invalid(coordinate: GeoCoordinate): ElevationPoint {
            return ElevationPoint(
                coordinate = coordinate,
                elevationMeters = Double.NaN,
                quality = ElevationQuality.INVALID,
                provenance = ElevationProvenance.UNKNOWN
            )
        }

        /**
         * Creates a known elevation point
         */
        fun known(
            coordinate: GeoCoordinate,
            elevation: Double,
            accuracy: Double? = null,
            provenance: ElevationProvenance = ElevationProvenance.UNKNOWN,
            distanceFromStart: Float = 0f
        ): ElevationPoint {
            return ElevationPoint(
                coordinate = coordinate,
                elevationMeters = elevation,
                accuracyMeters = accuracy,
                quality = ElevationQuality.KNOWN,
                provenance = provenance,
                distanceFromStart = distanceFromStart
            )
        }

        /**
         * Creates an interpolated elevation point
         */
        fun interpolated(
            coordinate: GeoCoordinate,
            elevation: Double,
            accuracy: Double? = null
        ): ElevationPoint {
            return ElevationPoint(
                coordinate = coordinate,
                elevationMeters = elevation,
                accuracyMeters = accuracy,
                quality = ElevationQuality.INTERPOLATED,
                provenance = ElevationProvenance.SYNTHETIC
            )
        }
    }
}
