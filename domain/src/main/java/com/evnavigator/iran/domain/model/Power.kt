package com.evnavigator.iran.domain.model

/**
 * Represents power in kilowatts (kW).
 *
 * @param value The power in kW
 */
data class Power(val value: Double) {
    init {
        require(value >= 0.0) {
            "Power must be non-negative, got: $value"
        }
    }

    /**
     * Converts the power to watts.
     */
    fun toWatts(): Double = value * 1000.0

    /**
     * Converts the power to horsepower.
     */
    fun toHorsepower(): Double = value * 1.34102

    companion object {
        /**
         * Creates a Power from kW.
         */
        fun ofKilowatts(value: Double): Power {
            return Power(value)
        }

        /**
         * Creates a Power from W.
         */
        fun ofWatts(value: Double): Power {
            return Power(value / 1000.0)
        }

        /**
         * Creates a Power from horsepower.
         */
        fun ofHorsepower(value: Double): Power {
            return Power(value / 1.34102)
        }
    }
}