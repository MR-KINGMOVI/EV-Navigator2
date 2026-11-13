package com.evnavigator.iran.core.energy.model

/**
 * Modifier representing the effect of environmental or operational factors
 * on EV energy consumption (e.g. ambient temperature, wind, HVAC load).
 */
data class EnergyConsumptionModifier(
    val name: String,
    val description: String,
    val factor: Double,
    val uncertainty: Double = 0.0,
    val source: String = "unknown",
    val provenance: String = ""
) {
    init {
        require(factor >= 0.0) { "Energy consumption factor cannot be negative: $factor" }
        require(uncertainty >= 0.0) { "Uncertainty cannot be negative: $uncertainty" }
    }
}
