package com.evnavigator.iran.domain.model

/**
 * Represents a distance measurement.
 *
 * @param value The distance in meters
 */
data class Distance(val value: Double) : Comparable<Distance> {
    init {
        require(value >= 0.0) {
            "Distance must be non-negative, got: $value"
        }
    }

    /**
     * Distance in meters (alias for value).
     */
    val meters: Double get() = value

    /**
     * Convert to kilometers.
     */
    fun toKilometers(): Double = value / 1000.0

    /**
     * Alias for toKilometers.
     */
    fun inKilometers(): Double = toKilometers()

    /**
     * Convert to miles.
     */
    fun toMiles(): Double = value / 1609.344

    /**
     * Check if distance is zero.
     */
    fun isZero(): Boolean = value == 0.0

    /**
     * Add another distance.
     */
    operator fun plus(other: Distance): Distance = Distance(this.value + other.value)

    /**
     * Subtract another distance.
     */
    operator fun minus(other: Distance): Distance = Distance(this.value - other.value)

    override fun compareTo(other: Distance): Int = this.value.compareTo(other.value)

    override fun toString(): String = "${String.format("%.2f", value)}m"

    companion object {
        val ZERO = Distance(0.0)

        fun ofMeters(value: Double): Distance = Distance(value)
        fun fromMeters(value: Double): Distance = Distance(value)
        fun ofKilometers(value: Double): Distance = Distance(value * 1000.0)
        fun ofMiles(value: Double): Distance = Distance(value * 1609.344)
    }
}
