package com.evnavigator.iran.domain.model

/**
 * Represents a longitude value in degrees.
 *
 * @param value Longitude in degrees (-180 to 180)
 */
data class Longitude(val value: Double) {
    init {
        require(value >= -180.0 && value <= 180.0) {
            "Longitude must be between -180 and 180 degrees, got: $value"
        }
    }

    fun value(): Double = value

    companion object {
        /**
         * Creates a Longitude from a double value.
         */
        fun of(value: Double): Longitude {
            return Longitude(value)
        }
    }
}