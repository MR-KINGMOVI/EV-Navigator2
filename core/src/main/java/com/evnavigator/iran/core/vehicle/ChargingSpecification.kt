package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Power

/**
 * Charging capability specification for electric vehicles.
 *
 * This represents static vehicle-side charging capabilities, not infrastructure or network data.
 */
data class ChargingSpecification(
    // AC charging capabilities
    val acChargingMaxPowerKw: Power?,  // Maximum AC charging power in kW
    
    // DC charging capabilities  
    val dcChargingMaxPowerKw: Power?,  // Maximum DC charging power in kW
    
    // Charging voltage and current ranges (if known)
    val chargingVoltageRangeV: IntRange?, // Voltage range in volts
    val chargingCurrentRangeA: IntRange?, // Current range in amperes
    
    // Connector information
    val connectorTypes: Set<ConnectorType>, // Supported connector types
    
    // Physical characteristics
    val chargePortCount: Int, // Number of charging ports
    
    // Additional charging parameters (if known)
    val chargingEfficiency: Double?, // Charging efficiency as fraction 0.0-1.0
    val chargingTimeEstimate: String?, // Estimated charging time information
    
    // Charging infrastructure compatibility (vehicle-side capabilities only)
    val isFastChargingCapable: Boolean?, // Whether the vehicle supports fast charging
    val isV2GCompatible: Boolean?         // Vehicle-to-grid compatibility
) {
    init {
        acChargingMaxPowerKw?.let { require(it.watts >= 0) { "AC charging power cannot be negative" } }
        dcChargingMaxPowerKw?.let { require(it.watts >= 0) { "DC charging power cannot be negative" } }
        
        // Validate ranges
        chargingVoltageRangeV?.let { 
            require(it.first <= it.last) { "Invalid voltage range" } 
        }
        chargingCurrentRangeA?.let { 
            require(it.first <= it.last) { "Invalid current range" } 
        }
        
        // Validate efficiency
        chargingEfficiency?.let { require(it >= 0.0 && it <= 1.0) { "Charging efficiency must be between 0.0 and 1.0" } }
        
        // Validate port count
        require(chargePortCount >= 0) { "Charge port count cannot be negative" }
    }
    
    /**
     * Determines if this vehicle has AC charging capability
     */
    val hasAcCharging: Boolean
        get() = acChargingMaxPowerKw != null && acChargingMaxPowerKw.watts > 0
    
    /**
     * Determines if this vehicle has DC charging capability
     */
    val hasDcCharging: Boolean  
        get() = dcChargingMaxPowerKw != null && dcChargingMaxPowerKw.watts > 0
    
    /**
     * Gets the maximum charging power available (AC or DC)
     */
    val maxChargingPowerKw: Power
        get() {
            val acPower = acChargingMaxPowerKw?.watts ?: 0.0
            val dcPower = dcChargingMaxPowerKw?.watts ?: 0.0
            return Power(maxOf(acPower, dcPower))
        }
    
    /**
     * Determines if this vehicle has any charging capability
     */
    val hasAnyCharging: Boolean
        get() = hasAcCharging || hasDcCharging
    
    /**
     * Gets the most common connector type (if available)
     */
    val primaryConnectorType: ConnectorType?
        get() = if (connectorTypes.isNotEmpty()) {
            connectorTypes.first()
        } else {
            null
        }
}