package com.evnavigator.iran.domain.model

/**
 * Represents elevation data for a geographic point.
 *
 * @param altitude Altitude in meters above sea level
 * @param accuracy Accuracy of the elevation measurement in meters
 */
data class Elevation(
    val altitude: Altitude,
    val accuracy: Distance? = null
) {
    companion object {
        /**
         * Creates an Elevation with altitude and optional accuracy.
         */
        fun of(altitude: Altitude, accuracy: Distance? = null): Elevation {
            return Elevation(altitude, accuracy)
        }
    }
}