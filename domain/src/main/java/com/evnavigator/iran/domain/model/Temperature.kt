package com.evnavigator.iran.domain.model

/**
 * Represents a temperature in Celsius.
 *
 * @param value The temperature in Celsius
 */
data class Temperature(val value: Double) {
    init {
        require(value >= -273.15) {
            "Temperature must be above absolute zero (-273.15°C), got: $value"
        }
    }

    /**
     * Converts the temperature to Fahrenheit.
     */
    fun toFahrenheit(): Double = value * 9.0 / 5.0 + 32.0

    /**
     * Converts the temperature to Kelvin.
     */
    fun toKelvin(): Double = value + 273.15

    companion object {
        /**
         * Creates a Temperature from Celsius.
         */
        fun ofCelsius(value: Double): Temperature {
            return Temperature(value)
        }

        /**
         * Creates a Temperature from Fahrenheit.
         */
        fun ofFahrenheit(value: Double): Temperature {
            return Temperature((value - 32.0) * 5.0 / 9.0)
        }

        /**
         * Creates a Temperature from Kelvin.
         */
        fun ofKelvin(value: Double): Temperature {
            return Temperature(value - 273.15)
        }
    }
}