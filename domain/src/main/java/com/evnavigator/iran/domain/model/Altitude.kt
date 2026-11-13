package com.evnavigator.iran.domain.model

/**
 * Represents an altitude in meters above sea level.
 *
 * @param value Altitude in meters
 */
data class Altitude(private val value: Double) {
    /**
     * Gets the altitude value in meters.
     */
    fun value(): Double = value

    companion object {
        /**
         * Creates an Altitude from meters.
         */
        fun ofMeters(value: Double): Altitude {
            return Altitude(value)
        }
    }
}