package com.evnavigator.iran.domain.model.units

import kotlin.math.abs

/**
 * Strongly-typed physical unit value classes (V13.0 Code-Only Master Audit — Item 26 & Item 148).
 *
 * Prevents unit-confusion defects (meters vs kilometers, Wh vs kWh, m/s vs km/h, percent vs ratio)
 * across routing, atmospheric physics, longitudinal vehicle dynamics, and charging curve calculation.
 */
@JvmInline
value class Meters(val value: Double) : Comparable<Meters> {
    init {
        require(!value.isNaN()) { "Meters cannot be NaN" }
    }

    fun toKilometers(): Kilometers = Kilometers(value / 1_000.0)
    operator fun plus(other: Meters): Meters = Meters(value + other.value)
    operator fun minus(other: Meters): Meters = Meters(value - other.value)
    operator fun times(factor: Double): Meters = Meters(value * factor)
    operator fun div(divisor: Double): Meters = Meters(value / divisor)
    override fun compareTo(other: Meters): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Meters(0.0)
        fun fromKilometers(km: Double): Meters = Meters(km * 1_000.0)
    }
}

@JvmInline
value class Kilometers(val value: Double) : Comparable<Kilometers> {
    init {
        require(!value.isNaN()) { "Kilometers cannot be NaN" }
    }

    fun toMeters(): Meters = Meters(value * 1_000.0)
    operator fun plus(other: Kilometers): Kilometers = Kilometers(value + other.value)
    operator fun minus(other: Kilometers): Kilometers = Kilometers(value - other.value)
    override fun compareTo(other: Kilometers): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Kilometers(0.0)
    }
}

@JvmInline
value class Seconds(val value: Double) : Comparable<Seconds> {
    init {
        require(!value.isNaN()) { "Seconds cannot be NaN" }
    }

    fun toMinutes(): Double = value / 60.0
    fun toHours(): Double = value / 3_600.0
    operator fun plus(other: Seconds): Seconds = Seconds(value + other.value)
    operator fun minus(other: Seconds): Seconds = Seconds(value - other.value)
    override fun compareTo(other: Seconds): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Seconds(0.0)
        fun fromMinutes(minutes: Double): Seconds = Seconds(minutes * 60.0)
        fun fromHours(hours: Double): Seconds = Seconds(hours * 3_600.0)
    }
}

@JvmInline
value class KilowattHours(val value: Double) : Comparable<KilowattHours> {
    init {
        require(!value.isNaN()) { "KilowattHours cannot be NaN" }
    }

    fun toWattHours(): Double = value * 1_000.0
    operator fun plus(other: KilowattHours): KilowattHours = KilowattHours(value + other.value)
    operator fun minus(other: KilowattHours): KilowattHours = KilowattHours(value - other.value)
    operator fun times(factor: Double): KilowattHours = KilowattHours(value * factor)
    override fun compareTo(other: KilowattHours): Int = value.compareTo(other.value)

    companion object {
        val ZERO = KilowattHours(0.0)
        fun fromWattHours(wh: Double): KilowattHours = KilowattHours(wh / 1_000.0)
    }
}

@JvmInline
value class Kilowatts(val value: Double) : Comparable<Kilowatts> {
    init {
        require(!value.isNaN()) { "Kilowatts cannot be NaN" }
    }

    fun toWatts(): Double = value * 1_000.0
    fun energyOver(duration: Seconds): KilowattHours = KilowattHours(value * duration.toHours())
    override fun compareTo(other: Kilowatts): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Kilowatts(0.0)
    }
}

@JvmInline
value class KilometersPerHour(val value: Double) : Comparable<KilometersPerHour> {
    init {
        require(!value.isNaN()) { "KilometersPerHour cannot be NaN" }
    }

    fun toMetersPerSecond(): Double = value / 3.6
    override fun compareTo(other: KilometersPerHour): Int = value.compareTo(other.value)

    companion object {
        val ZERO = KilometersPerHour(0.0)
        fun fromMetersPerSecond(mps: Double): KilometersPerHour = KilometersPerHour(mps * 3.6)
    }
}

/**
 * Percentage on the [0.0, 100.0] scale (e.g. State of Charge 80.0%).
 */
@JvmInline
value class Percentage(val value: Double) : Comparable<Percentage> {
    init {
        require(!value.isNaN()) { "Percentage cannot be NaN" }
    }

    fun coerceValidSoc(): Percentage = Percentage(value.coerceIn(0.0, 100.0))
    fun toRatio(): Ratio = Ratio(value / 100.0)
    override fun compareTo(other: Percentage): Int = value.compareTo(other.value)
}

/**
 * Dimensionless ratio on the [0.0, 1.0] scale (e.g. confidence, efficiency).
 */
@JvmInline
value class Ratio(val value: Double) : Comparable<Ratio> {
    init {
        require(!value.isNaN()) { "Ratio cannot be NaN" }
    }

    fun coerceUnitInterval(): Ratio = Ratio(value.coerceIn(0.0, 1.0))
    fun toPercentage(): Percentage = Percentage(value * 100.0)
    override fun compareTo(other: Ratio): Int = value.compareTo(other.value)

    companion object {
        fun ofClamped(raw: Double): Ratio = Ratio(if (raw.isNaN()) 0.0 else raw.coerceIn(0.0, 1.0))
        fun approxEquals(a: Double, b: Double, epsilon: Double = 1e-6): Boolean = abs(a - b) <= epsilon
    }
}
