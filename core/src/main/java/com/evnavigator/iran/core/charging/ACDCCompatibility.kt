package com.evnavigator.iran.core.charging

/**
 * Represents the AC/DC compatibility status between vehicle and charging station.
 */
enum class ACDCCompatibility {
    COMPATIBLE,     // AC/DC modes are compatible
    INCOMPATIBLE,   // AC/DC modes are incompatible
    UNKNOWN         // AC/DC mode is unknown, so compatibility cannot be determined
}

/**
 * Result of AC/DC compatibility evaluation between vehicle and station.
 */
data class ACDCCompatibilityResult(
    val status: ACDCCompatibility,
    val vehicleIsAc: Boolean?,   // Vehicle supports AC charging (if known)
    val vehicleIsDc: Boolean?,   // Vehicle supports DC charging (if known)
    val stationIsAc: Boolean?,   // Station provides AC charging (if known)
    val stationIsDc: Boolean?    // Station provides DC charging (if known)
)

/**
 * Utility class for determining AC/DC compatibility between vehicle and charging station.
 */
object ACDCCompatibilityEngine {
    
    /**
     * Determines if the AC/DC capabilities are compatible.
     *
     * This evaluates whether a vehicle can charge at a station based on AC/DC modes.
     *
     * @param vehicleIsAc Whether the vehicle supports AC charging (nullable)
     * @param vehicleIsDc Whether the vehicle supports DC charging (nullable)
     * @param stationIsAc Whether the station provides AC charging (nullable)
     * @param stationIsDc Whether the station provides DC charging (nullable)
     * @return Result with compatibility status and details
     */
    fun determineCompatibility(
        vehicleIsAc: Boolean?,
        vehicleIsDc: Boolean?,
        stationIsAc: Boolean?,
        stationIsDc: Boolean?
    ): ACDCCompatibilityResult {
        // If we don't have sufficient information, we cannot determine compatibility
        if (vehicleIsAc == null || vehicleIsDc == null || 
            stationIsAc == null || stationIsDc == null) {
            return ACDCCompatibilityResult(
                status = ACDCCompatibility.UNKNOWN,
                vehicleIsAc = vehicleIsAc,
                vehicleIsDc = vehicleIsDc,
                stationIsAc = stationIsAc,
                stationIsDc = stationIsDc
            )
        }
        
        // Both must be known for a proper compatibility check
        if (!vehicleIsAc && !vehicleIsDc) {
            // Vehicle supports neither AC nor DC - impossible case
            return ACDCCompatibilityResult(
                status = ACDCCompatibility.INCOMPATIBLE,
                vehicleIsAc = vehicleIsAc,
                vehicleIsDc = vehicleIsDc,
                stationIsAc = stationIsAc,
                stationIsDc = stationIsDc
            )
        }
        
        if (!stationIsAc && !stationIsDc) {
            // Station provides neither AC nor DC - impossible case  
            return ACDCCompatibilityResult(
                status = ACDCCompatibility.INCOMPATIBLE,
                vehicleIsAc = vehicleIsAc,
                vehicleIsDc = vehicleIsDc,
                stationIsAc = stationIsAc,
                stationIsDc = stationIsDc
            )
        }
        
        // Check if there's any overlap in capabilities
        val canChargeWithAc = vehicleIsAc && stationIsAc
        val canChargeWithDc = vehicleIsDc && stationIsDc
        
        val status = when {
            canChargeWithAc || canChargeWithDc -> ACDCCompatibility.COMPATIBLE
            else -> ACDCCompatibility.INCOMPATIBLE
        }
        
        return ACDCCompatibilityResult(
            status = status,
            vehicleIsAc = vehicleIsAc,
            vehicleIsDc = vehicleIsDc,
            stationIsAc = stationIsAc,
            stationIsDc = stationIsDc
        )
    }
    
    /**
     * Determines if AC/DC capabilities are compatible.
     *
     * @param vehicleIsAc Whether the vehicle supports AC charging (nullable)
     * @param vehicleIsDc Whether the vehicle supports DC charging (nullable)
     * @param stationIsAc Whether the station provides AC charging (nullable)
     * @param stationIsDc Whether the station provides DC charging (nullable)
     * @return True if compatible, false otherwise
     */
    fun isCompatible(
        vehicleIsAc: Boolean?,
        vehicleIsDc: Boolean?,
        stationIsAc: Boolean?,
        stationIsDc: Boolean?
    ): Boolean {
        val result = determineCompatibility(vehicleIsAc, vehicleIsDc, stationIsAc, stationIsDc)
        return result.status == ACDCCompatibility.COMPATIBLE
    }
}