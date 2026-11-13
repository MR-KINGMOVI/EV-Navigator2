package com.evnavigator.iran.core.charging



/**
 * Represents the compatibility status between vehicle and charging station power capabilities.
 */
enum class PowerCompatibility {
    COMPATIBLE,     // Power levels are compatible
    INCOMPATIBLE,   // Power levels are incompatible (vehicle cannot charge at this rate)
    UNKNOWN         // Power capability is unknown, so compatibility cannot be determined
}

/**
 * Result of power compatibility evaluation between vehicle and station.
 */
data class PowerCompatibilityResult(
    val status: PowerCompatibility,
    val vehiclePowerKw: Double?,   // Vehicle's maximum charging power (if known)
    val stationPowerKw: Double?,   // Station's rated power (if known)
    val effectivePowerKw: Double?  // The effective power that can be achieved
)

/**
 * Utility class for determining power compatibility between vehicle and charging station.
 */
object PowerCompatibilityEngine {
    
    /**
     * Determines the effective power capability when a vehicle charges at a station.
     *
     * This evaluates the minimum of:
     * - Vehicle's maximum charging capability
     * - Station's rated charging power
     *
     * @param vehicleMaxPowerKw The vehicle's maximum charging power in kW (nullable)
     * @param stationPowerKw The station's rated power in kW (nullable)
     * @return Result with compatibility status and effective power
     */
    fun determineEffectivePower(
        vehicleMaxPowerKw: Double?,
        stationPowerKw: Double?
    ): PowerCompatibilityResult {
        // If either is unknown, we cannot determine the effective power
        if (vehicleMaxPowerKw == null || stationPowerKw == null) {
            return PowerCompatibilityResult(
                status = PowerCompatibility.UNKNOWN,
                vehiclePowerKw = vehicleMaxPowerKw,
                stationPowerKw = stationPowerKw,
                effectivePowerKw = null
            )
        }
        
        // If both are known, the effective power is the minimum of the two
        val effectivePower = minOf(vehicleMaxPowerKw, stationPowerKw)
        
        // Determine compatibility based on whether effective power is reasonable
        val status = if (effectivePower > 0) {
            PowerCompatibility.COMPATIBLE
        } else {
            PowerCompatibility.INCOMPATIBLE
        }
        
        return PowerCompatibilityResult(
            status = status,
            vehiclePowerKw = vehicleMaxPowerKw,
            stationPowerKw = stationPowerKw,
            effectivePowerKw = effectivePower
        )
    }
    
    /**
     * Determines if the power capabilities are compatible.
     *
     * @param vehicleMaxPowerKw The vehicle's maximum charging power in kW (nullable)
     * @param stationPowerKw The station's rated power in kW (nullable)
     * @return True if there is a compatible effective power, false otherwise
     */
    fun isCompatible(
        vehicleMaxPowerKw: Double?,
        stationPowerKw: Double?
    ): Boolean {
        val result = determineEffectivePower(vehicleMaxPowerKw, stationPowerKw)
        return result.status == PowerCompatibility.COMPATIBLE
    }
}