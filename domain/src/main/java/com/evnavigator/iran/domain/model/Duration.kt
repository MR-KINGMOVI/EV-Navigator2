package com.evnavigator.iran.domain.model

/**
 * Represents a duration of time.
 */
data class Duration(val seconds: Long) {
    companion object {
        val ZERO = Duration(0L)
        fun ofSeconds(seconds: Long): Duration = Duration(seconds.coerceAtLeast(0L))
        fun fromSeconds(seconds: Long): Duration = Duration(seconds.coerceAtLeast(0L))
        fun fromMinutes(minutes: Long): Duration = Duration((minutes * 60L).coerceAtLeast(0L))
    }

    /**
     * Convert to minutes.
     */
    fun toMinutes(): Double = seconds / 60.0

    /**
     * Convert to hours.
     */
    fun toHours(): Double = seconds / 3600.0

    /**
     * Check if duration is zero.
     */
    fun isZero(): Boolean = seconds == 0L

    /**
     * Add another duration.
     */
    operator fun plus(other: Duration): Duration = Duration(this.seconds + other.seconds)

    /**
     * Subtract another duration.
     */
    operator fun minus(other: Duration): Duration = Duration(this.seconds - other.seconds)

    override fun toString(): String = "${seconds}s"
}