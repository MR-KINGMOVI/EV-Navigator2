package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.energy.model.*

/**
 * Represents the state of a battery system with energy and charge information
 */
data class BatteryState(
    val usableCapacityWh: Energy,      // Usable battery capacity in Wh
    val currentSoEWh: Energy,          // Current state of energy in Wh
    val minimumOperationalSoCPercent: Double,  // Minimum safe SoC percentage
    val maximumUsableSoCPercent: Double,       // Maximum safe SoC percentage
    val reserveWh: Energy = Energy(0.0) // Reserve energy to maintain safety margin
) {
    init {
        require(currentSoEWh.wattHours >= 0.0) {
            "State of Energy cannot be negative"
        }
        require(currentSoEWh.wattHours <= usableCapacityWh.wattHours) {
            "State of Energy cannot exceed usable capacity"
        }
        require(minimumOperationalSoCPercent >= 0.0 && minimumOperationalSoCPercent <= 100.0) {
            "Minimum operational SoC must be between 0 and 100"
        }
        require(maximumUsableSoCPercent >= 0.0 && maximumUsableSoCPercent <= 100.0) {
            "Maximum usable SoC must be between 0 and 100"
        }
        require(minimumOperationalSoCPercent <= maximumUsableSoCPercent) {
            "Minimum operational SoC cannot exceed maximum usable SoC"
        }
    }
    
    /**
     * Gets the current state of charge as a percentage
     */
    val currentSoCPercent: Double
        get() {
            if (usableCapacityWh.wattHours <= 0.0) return 0.0
            val soc = (currentSoEWh.wattHours / usableCapacityWh.wattHours) * 100.0
            // Clamp to valid range
            return soc.coerceIn(0.0, 100.0)
        }
    
    /**
     * Gets the available energy for consumption (excluding reserve)
     */
    val availableEnergyWh: Energy
        get() = Energy(currentSoEWh.wattHours - reserveWh.wattHours)
    
    /**
     * Checks if the battery is in a safe operational range
     */
    val isOperational: Boolean
        get() = currentSoCPercent >= minimumOperationalSoCPercent
    
    /**
     * Gets the remaining capacity percentage (available energy / usable capacity)
     */
    val remainingCapacityPercent: Double
        get() {
            if (usableCapacityWh.wattHours <= 0.0) return 0.0
            val remaining = (availableEnergyWh.wattHours / usableCapacityWh.wattHours) * 100.0
            return remaining.coerceIn(0.0, 100.0)
        }
    
    /**
     * Updates the battery state with energy consumption or recovery
     */
    fun updateWithEnergyDelta(deltaWh: Double): BatteryState {
        val newSoE = currentSoEWh.wattHours + deltaWh
        val clampedSoE = newSoE.coerceIn(0.0, usableCapacityWh.wattHours)
        
        return copy(
            currentSoEWh = Energy(clampedSoE)
        )
    }
    
    /**
     * Updates the battery state with a specific energy value
     */
    fun updateWithEnergy(energyWh: Double): BatteryState {
        val clampedEnergy = energyWh.coerceIn(0.0, usableCapacityWh.wattHours)
        return copy(
            currentSoEWh = Energy(clampedEnergy)
        )
    }
    
    companion object {
        /**
         * Creates a battery state from initial SoC percentage
         */
        fun fromInitialSoC(
            usableCapacityWh: Energy,
            initialSoCPercent: Double,
            minimumOperationalSoCPercent: Double = 10.0,
            maximumUsableSoCPercent: Double = 90.0,
            reserveWh: Energy = Energy(0.0)
        ): BatteryState {
            val initialEnergy = (usableCapacityWh.wattHours * (initialSoCPercent / 100.0))
            return BatteryState(
                usableCapacityWh = usableCapacityWh,
                currentSoEWh = Energy(initialEnergy),
                minimumOperationalSoCPercent = minimumOperationalSoCPercent,
                maximumUsableSoCPercent = maximumUsableSoCPercent,
                reserveWh = reserveWh
            )
        }
    }
}