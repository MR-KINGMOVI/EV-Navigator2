package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy

/**
 * Battery specification for electric vehicles.
 *
 * This model distinguishes between:
 * - gross/nominal battery capacity (total)
 * - usable battery capacity (available for driving)
 *
 * These are kept separate to prevent confusion and ensure proper energy calculations.
 */
data class BatterySpecification(
    // Gross/Total battery capacity
    val nominalCapacityWh: Energy,
    
    // Usable battery capacity (what's actually available for driving)
    val usableCapacityWh: Energy,
    
    // Battery chemistry information (if known)
    val chemistry: String?, // e.g., "Lithium Iron Phosphate", "Nickel Manganese Cobalt"
    
    // Electrical characteristics
    val nominalVoltageV: Double?, // Nominal voltage of the battery pack
    val batteryConfiguration: String?, // Configuration like "48S10P", "60S8P" 
    
    // Thermal and performance characteristics (if known)
    val thermalCharacteristics: String?, // e.g., "Air-cooled", "Liquid-cooled"
    
    // Energy limits (if known)
    val maximumDischargePowerWh: Energy?, // Maximum power that can be discharged
    val maximumChargePowerWh: Energy?,     // Maximum power that can be charged
    
    // Battery health related information (if available)
    val batteryAgeYears: Int?, // Age of the battery if known
    val degradationRate: Double? // Annual degradation rate (0.0 to 1.0)  
) {
    init {
        // Validate capacity relationships
        require(nominalCapacityWh.wattHours > 0) { "Nominal capacity must be positive" }
        require(usableCapacityWh.wattHours > 0) { "Usable capacity must be positive" }
        require(usableCapacityWh.wattHours <= nominalCapacityWh.wattHours) { 
            "Usable capacity cannot exceed nominal capacity" 
        }
        
        // Validate electrical characteristics
        nominalVoltageV?.let { require(it > 0) { "Nominal voltage must be positive" } }
        
        // Validate battery age and degradation
        batteryAgeYears?.let { require(it >= 0) { "Battery age cannot be negative" } }
        degradationRate?.let { 
            require(it >= 0.0 && it <= 1.0) { "Degradation rate must be between 0.0 and 1.0" } 
        }
    }
    
    /**
     * Gets the percentage of usable capacity relative to nominal capacity
     */
    val usableCapacityPercentage: Double
        get() = (usableCapacityWh.wattHours / nominalCapacityWh.wattHours) * 100.0
    
    /**
     * Calculates remaining usable capacity based on age and degradation
     */
    fun calculateRemainingUsableCapacity(currentAgeYears: Int? = null): Energy {
        val age = currentAgeYears ?: batteryAgeYears ?: 0
        val degradation = degradationRate ?: 0.0
        
        if (age <= 0 || degradation <= 0) {
            return usableCapacityWh
        }
        
        // Simple linear degradation model
        val degradationFactor = (degradation * age).coerceAtMost(1.0)
        val remainingPercentage = (1.0 - degradationFactor).coerceAtLeast(0.0)
        
        val remainingWh = usableCapacityWh.wattHours * remainingPercentage
        return Energy(remainingWh)
    }
}