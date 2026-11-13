package com.evnavigator.iran.core.vehicle

import java.util.*

/**
 * Canonical vehicle identifier that uniquely distinguishes vehicles by all relevant dimensions.
 *
 * A vehicle must be distinguishable by:
 * - manufacturer (make)
 * - model  
 * - generation
 * - model year
 * - trim
 * - market/region
 * - powertrain variant
 *
 * This prevents confusion between different variants of the same vehicle name.
 */
data class VehicleId(
    val manufacturer: String,
    val model: String,
    val generation: String,
    val modelYear: Int,
    val trim: String,
    val market: String = "IR", // Default to Iran market
    val powertrainType: PowertrainType = PowertrainType.BEV
) {
    
    /**
     * Unique stable identifier for this specific vehicle variant
     */
    val id: String by lazy {
        "${manufacturer.lowercase()}_${model.lowercase()}_${generation.lowercase()}_${modelYear}_${trim.lowercase()}_${market.lowercase()}_${powertrainType.name.lowercase()}"
    }
    
    init {
        require(manufacturer.isNotBlank()) { "Manufacturer must not be blank" }
        require(model.isNotBlank()) { "Model must not be blank" }
        require(generation.isNotBlank()) { "Generation must not be blank" }
        require(modelYear > 1900 && modelYear <= 2030) { "Invalid model year" }
        require(trim.isNotBlank()) { "Trim must not be blank" }
    }
    
    companion object {
        /**
         * Creates a vehicle ID from a stable string representation
         */
        fun fromString(id: String): VehicleId {
            val parts = id.split("_")
            if (parts.size < 6) {
                throw IllegalArgumentException("Invalid vehicle ID format: $id")
            }
            
            return VehicleId(
                manufacturer = parts[0],
                model = parts[1],
                generation = parts[2],
                modelYear = parts[3].toInt(),
                trim = parts[4],
                market = if (parts.size > 5) parts[5] else "IR",
                powertrainType = if (parts.size > 6) {
                    try {
                        PowertrainType.valueOf(parts[6].uppercase())
                    } catch (e: IllegalArgumentException) {
                        PowertrainType.BEV
                    }
                } else {
                    PowertrainType.BEV
                }
            )
        }
    }
}

/**
 * Represents the type of powertrain for a vehicle
 */
enum class PowertrainType {
    BEV,      // Battery Electric Vehicle
    PHEV,     // Plug-in Hybrid Electric Vehicle  
    HEV,      // Hybrid Electric Vehicle
    ICE       // Internal Combustion Engine
}