package com.evnavigator.iran.domain.model

/**
 * Represents a bearing in degrees (0-360).
 *
 * @param value The bearing in degrees
 */
data class Bearing(val value: Double) {
    init {
        require(value >= 0.0 && value < 360.0) {
            "Bearing must be between 0 and 360 degrees, got: $value"
        }
    }

    companion object {
        /**
         * Calculates the bearing from one coordinate to another.
         */
        fun calculate(from: GeoCoordinate, to: GeoCoordinate): Bearing {
            val lat1Rad = Math.toRadians(from.latitude)
            val lat2Rad = Math.toRadians(to.latitude)
            val deltaLonRad = Math.toRadians(to.longitude - from.longitude)

            val y = Math.sin(deltaLonRad) * Math.cos(lat2Rad)
            val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                    Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad)
            val bearingRad = Math.atan2(y, x)

            val bearingDeg = Math.toDegrees(bearingRad)
            return Bearing((bearingDeg + 360.0) % 360.0)
        }

        /**
         * Creates a Bearing from degrees.
         */
        fun ofDegrees(value: Double): Bearing {
            return Bearing(value)
        }
    }
}