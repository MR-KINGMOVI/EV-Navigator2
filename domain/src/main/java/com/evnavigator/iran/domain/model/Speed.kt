package com.evnavigator.iran.domain.model

/**
 * Represents a speed measurement.
 *
 * @param value The speed in km/h
 */
data class Speed(val value: Double) : Comparable<Speed> {
    init {
        require(value >= 0.0) {
            "Speed must be non-negative, got: $value"
        }
    }

    /**
     * Speed in meters per second.
     */
    val metersPerSecond: Double get() = value / 3.6

    /**
     * Converts the speed to meters per second.
     */
    fun toMetersPerSecond(): Double = value / 3.6

    /**
     * Converts to km/h.
     */
    fun toKmh(): Double = value

    /**
     * Converts to kilometers per hour.
     */
    fun toKilometersPerHour(): Double = value

    /**
     * Converts the speed to miles per hour.
     */
    fun toMilesPerHour(): Double = value / 1.609344

    /**
     * Converts to mph.
     */
    fun toMph(): Double = value / 1.609344

    /**
     * Check if speed is zero.
     */
    fun isZero(): Boolean = value == 0.0

    override fun compareTo(other: Speed): Int = this.value.compareTo(other.value)

    override fun toString(): String = "${String.format("%.2f", value)} km/h"

    companion object {
        val ZERO = Speed(0.0)

        fun ofKilometersPerHour(value: Double): Speed = Speed(value)
        fun ofMetersPerSecond(value: Double): Speed = Speed(value * 3.6)
        fun ofMilesPerHour(value: Double): Speed = Speed(value * 1.609344)
    }
}
