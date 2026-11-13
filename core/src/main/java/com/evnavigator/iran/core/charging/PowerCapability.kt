package com.evnavigator.iran.core.charging

import kotlin.math.abs

/**
 * Represents electrical power capabilities for charging infrastructure.
 *
 * This model provides a normalized representation of charging power that:
 * - Supports both AC and DC charging capabilities
 * - Handles various units consistently (kW, V, A)
 * - Provides explicit handling of unknown/missing values
 * - Validates physically impossible values
 * - Enables deterministic power calculations
 */
data class PowerCapability(
    // Primary power specification in kilowatts
    val powerKw: Double?, // Nominal power in kilowatts
    
    // Voltage and current specifications for AC/DC
    val voltage: Double? = null, // Voltage in volts (V)
    val current: Double? = null, // Current in amperes (A)
    
    // AC/DC characteristics  
    val isAc: Boolean, // Whether this represents AC charging capability
    val isDc: Boolean, // Whether this represents DC charging capability
    
    // Phase information for AC
    val phaseCount: Int? = null, // Number of phases (1 or 3)
    
    // Power range specification for variable power capabilities  
    val minPowerKw: Double? = null, // Minimum power in kilowatts
    val maxPowerKw: Double? = null, // Maximum power in kilowatts
    
    // Source and provenance
    val source: String? = null, // Source of this power data
    val sourceReference: String? = null, // Reference ID from source system
    val retrievedDate: Long? = null, // When this information was retrieved
    val confidence: Double = 0.5, // Confidence level 0.0 to 1.0
    
    // Validation status
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN,
    val enforceStrictConsistency: Boolean = true
) {
    
    init {
        // Validate that power is non-negative if provided
        powerKw?.let { 
            require(it >= 0) { "Power cannot be negative if provided" } 
        }
        
        // Validate voltage and current if provided
        voltage?.let { 
            require(it > 0) { "Voltage must be positive if provided" } 
        }
        current?.let { 
            require(it > 0) { "Current must be positive if provided" } 
        }
        
        // Validate confidence level
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        
        // Ensure that AC/DC flags are consistent
        if (powerKw != null || voltage != null || current != null || minPowerKw != null || maxPowerKw != null) {
            require(isAc || isDc) { "Power capability must support either AC or DC charging" }
        }
        
        // Validate power range if both min and max are provided
        minPowerKw?.let { min ->
            maxPowerKw?.let { max ->
                require(min <= max) { "Minimum power cannot exceed maximum power" }
            }
        }
        
        // Validate that if we have a specific power value, it's consistent with voltage/current
        if (enforceStrictConsistency && powerKw != null && voltage != null && current != null) {
            val calculatedPower = (voltage * current * (phaseCount ?: 1)) / 1000.0
            require(abs(powerKw - calculatedPower) < 0.1) {
                "Provided power ($powerKw kW) is inconsistent with voltage ($voltage V) and current ($current A)"
            }
        }
    }
    
    /**
     * Gets the effective power in kilowatts
     */
    val effectivePowerKw: Double?
        get() = when {
            powerKw != null -> powerKw
            voltage != null && current != null -> (voltage * current * (phaseCount ?: 1)) / 1000.0
            else -> null
        }
    
    /**
     * Gets the minimum possible power in kilowatts (falls back to effective power if min is unknown)
     */
    val minPossiblePowerKw: Double?
        get() = minPowerKw ?: effectivePowerKw
    
    /**
     * Gets the maximum possible power in kilowatts (falls back to effective power if max is unknown)
     */
    val maxPossiblePowerKw: Double?
        get() = maxPowerKw ?: effectivePowerKw
    
    companion object {
        /**
         * Creates a simple AC power capability
         */
        fun createAcPower(
            powerKw: Double,
            voltage: Double,
            current: Double,
            phaseCount: Int = 1
        ): PowerCapability {
            return PowerCapability(
                powerKw = powerKw,
                voltage = voltage,
                current = current,
                isAc = true,
                isDc = false,
                phaseCount = phaseCount,
                enforceStrictConsistency = false
            )
        }
        
        /**
         * Creates a simple DC power capability
         */
        fun createDcPower(
            powerKw: Double,
            voltage: Double,
            current: Double
        ): PowerCapability {
            return PowerCapability(
                powerKw = powerKw,
                voltage = voltage,
                current = current,
                isAc = false,
                isDc = true
            )
        }
        
        /**
         * Creates a variable power capability (e.g., for DC charging with range)
         */
        fun createVariablePower(
            minPowerKw: Double,
            maxPowerKw: Double,
            voltage: Double? = null,
            current: Double? = null,
            isAc: Boolean = false,
            isDc: Boolean = true
        ): PowerCapability {
            return PowerCapability(
                powerKw = null, // Not specified for variable range
                voltage = voltage,
                current = current,
                isAc = isAc,
                isDc = isDc,
                minPowerKw = minPowerKw,
                maxPowerKw = maxPowerKw
            )
        }
        
        /**
         * Creates an unknown power capability
         */
        fun createUnknown(): PowerCapability {
            return PowerCapability(
                powerKw = null,
                voltage = null,
                current = null,
                isAc = false,
                isDc = false
            )
        }
    }
}
