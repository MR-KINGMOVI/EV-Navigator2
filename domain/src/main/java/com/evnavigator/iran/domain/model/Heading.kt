package com.evnavigator.iran.domain.model

/**
 * Represents a heading (direction) in degrees.
 *
 * @param value The heading value in degrees (0-360)
 */
data class Heading(val value: Double) {
    init {
        require(value >= 0.0 && value < 360.0) {
            "Heading must be between 0 and 360 degrees, got: $value"
        }
    }

    companion object {
        /**
         * Creates a Heading from a value in degrees.
         */
        fun of(value: Double): Heading {
            return Heading(value)
        }

        /**
         * Creates a Heading from a compass direction string.
         */
        fun fromCompassDirection(direction: String): Heading {
            val directions = mapOf(
                "N" to 0.0,
                "NNE" to 22.5,
                "NE" to 45.0,
                "ENE" to 67.5,
                "E" to 90.0,
                "ESE" to 112.5,
                "SE" to 135.0,
                "SSE" to 157.5,
                "S" to 180.0,
                "SSW" to 202.5,
                "SW" to 225.0,
                "WSW" to 247.5,
                "W" to 270.0,
                "WNW" to 292.5,
                "NW" to 315.0,
                "NNW" to 337.5
            )
            return Heading(directions[direction.uppercase()] ?: 0.0)
        }
    }
}