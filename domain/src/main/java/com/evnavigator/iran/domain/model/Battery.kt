package com.evnavigator.iran.domain.model

/**
 * Represents a vehicle battery.
 *
 * @param capacity Total battery capacity in kWh
 * @param currentCharge Current charge level in kWh
 * @param soc State of Charge (SoC) as percentage (0-100)
 * @param voltage Battery voltage in volts
 * @param current Battery current in amps
 */
data class Battery(
    val capacity: Double, // in kWh
    val currentCharge: Double, // in kWh
    val soc: Double, // State of Charge percentage (0-100)
    val voltage: Double, // in volts
    val current: Double // in amps
) {
    init {
        require(soc in 0.0..100.0) { "SoC must be between 0 and 100" }
        require(currentCharge >= 0.0) { "Current charge must be non-negative" }
        require(currentCharge <= capacity) { "Current charge cannot exceed capacity" }
    }
}