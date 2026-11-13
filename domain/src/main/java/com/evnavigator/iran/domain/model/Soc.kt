package com.evnavigator.iran.domain.model

import kotlinx.serialization.Serializable

/**
 * State of Charge (SoC) representation for EV battery percentage.
 * Pure Kotlin/JVM domain value object (independent of Android framework).
 */
@Serializable
data class Soc(
    val percentage: Double
) {
    init {
        require(percentage.isFinite() && percentage in 0.0..100.0) {
            "State of Charge percentage must be finite and in [0.0, 100.0], was $percentage"
        }
    }

    val fraction: Double get() = percentage / 100.0

    companion object {
        val ZERO = Soc(0.0)
        val FULL = Soc(100.0)

        fun fromFraction(fraction: Double): Soc {
            return Soc(fraction * 100.0)
        }
    }
}
