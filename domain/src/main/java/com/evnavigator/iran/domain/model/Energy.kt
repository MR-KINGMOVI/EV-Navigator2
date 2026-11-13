package com.evnavigator.iran.domain.model

/**
 * Represents energy in kilowatt-hours (kWh).
 *
 * @param value The energy in kWh
 */
data class Energy(val value: Double) {
    init {
        require(value >= 0.0) {
            "Energy must be non-negative, got: $value"
        }
    }

    /**
     * Converts the energy to watt-hours.
     */
    fun toWattHours(): Double = value * 1000.0

    /**
     * Converts the energy to joules.
     */
    fun toJoules(): Double = value * 3.6e6

    companion object {
        /**
         * Creates an Energy from kWh.
         */
        fun ofKilowattHours(value: Double): Energy {
            return Energy(value)
        }

        /**
         * Creates an Energy from Wh.
         */
        fun ofWattHours(value: Double): Energy {
            return Energy(value / 1000.0)
        }

        /**
         * Creates an Energy from joules.
         */
        fun ofJoules(value: Double): Energy {
            return Energy(value / 3.6e6)
        }
    }
}