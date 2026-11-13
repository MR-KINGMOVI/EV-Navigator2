package com.evnavigator.iran.core.charging

/**
 * Represents the compatibility status between vehicle and charging station connectors.
 */
enum class ConnectorCompatibility {
    COMPATIBLE,     // Connectors are compatible
    INCOMPATIBLE,   // Connectors are incompatible
    UNKNOWN         // Compatibility cannot be determined (unknown connector types)
}

/**
 * Utility class for determining connector compatibility.
 *
 * This class provides methods to determine if a vehicle's charging capability
 * is compatible with a station's connector type.
 */
object ConnectorCompatibilityEngine {
    
    /**
     * Determines if a vehicle connector type is compatible with a station connector type.
     *
     * @param vehicleConnectorType The connector type supported by the vehicle
     * @param stationConnectorType The connector type available at the charging station
     * @return The compatibility status
     */
    fun determineCompatibility(
        vehicleConnectorType: ConnectorType,
        stationConnectorType: ConnectorType
    ): ConnectorCompatibility {
        // If either is unknown, we cannot determine compatibility
        if (vehicleConnectorType == ConnectorType.UNKNOWN || 
            stationConnectorType == ConnectorType.UNKNOWN) {
            return ConnectorCompatibility.UNKNOWN
        }
        
        // Direct match is compatible
        if (vehicleConnectorType == stationConnectorType) {
            return ConnectorCompatibility.COMPATIBLE
        }
        
        // For now, we'll consider basic compatibility - in future implementations
        // this would include more sophisticated adapter compatibility logic
        
        // In real implementation, we might have:
        // - CCS2 vehicle with CCS2 station = COMPATIBLE
        // - CCS2 vehicle with CCS1 station = UNKNOWN (depends on adapter)
        // - Tesla vehicle with Tesla station = COMPATIBLE  
        // - Type 2 vehicle with CCS2 station = INCOMPATIBLE
        
        return ConnectorCompatibility.INCOMPATIBLE
    }
    
    /**
     * Determines if a set of vehicle connector types is compatible with any of the 
     * station's available connectors.
     *
     * @param vehicleConnectorTypes The connector types supported by the vehicle
     * @param stationConnectorTypes The connector types available at the charging station
     * @return True if there's at least one compatible pair, false otherwise
     */
    fun isCompatible(
        vehicleConnectorTypes: Set<ConnectorType>,
        stationConnectorTypes: Set<ConnectorType>
    ): Boolean {
        return vehicleConnectorTypes.any { vehicleType ->
            stationConnectorTypes.any { stationType ->
                determineCompatibility(vehicleType, stationType) == ConnectorCompatibility.COMPATIBLE
            }
        }
    }
}