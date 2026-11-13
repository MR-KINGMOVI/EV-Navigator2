package com.evnavigator.iran.domain.model

/**
 * Represents a latitude value in degrees.
 *
 * @param value Latitude in degrees (-90 to 90)
 */
data class Latitude(val value: Double) {
    init {
        require(value >= -90.0 && value <= 90.0) {
            "Latitude must be between -90 and 90 degrees, got: $value"
        }
    }

    fun value(): Double = value

    companion object {
        /**
         * Creates a Latitude from a double value.
         */
        fun of(value: Double): Latitude {
            return Latitude(value)
        }
    }
}