package com.evnavigator.iran.core.charging

/**
 * Enum representing the distinction between static and live status of charging infrastructure.
 *
 * This separation is crucial for maintaining data integrity and ensuring that:
 * - Static station records never claim live availability
 * - Live operational state information is properly isolated from canonical infrastructure data
 * - Freshness and confidence levels are correctly applied to each type of data
 */
enum class StaticVsLiveStatus {
    /**
     * Static status - represents the canonical, unchanging characteristics of a charging station.
     * This includes:
     * - Station existence and location
     * - Connector types and physical specifications  
     * - Rated power capabilities
     * - Operator information
     * - Metadata (name, address, etc.)
     *
     * Static data should never be confused with live operational state.
     */
    STATIC,

    /**
     * Live status - represents current operational state that changes over time.
     * This includes:
     * - Availability (available, in use, faulted, out of service)
     * - Real-time charging status
     * - Current operational faults
     * - Temporary closures or maintenance
     *
     * Live data should never be stored in static station records.
     */
    LIVE
}

/**
 * Represents freshness information for charging infrastructure data.
 *
 * Freshness captures when and from where data was retrieved, along with confidence
 * about its accuracy and timeliness.
 */
data class Freshness(
    /**
     * Timestamp when this data was last retrieved or updated
     */
    val timestamp: Long,
    
    /**
     * Source of this data (e.g., "ChargePoint", "Shell", "user_contribution")
     */
    val source: String,
    
    /**
     * Age of the data in milliseconds (calculated from current time)
     */
    val ageMillis: Long,
    
    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,
    
    /**
     * Version identifier for this dataset or record
     */
    val version: String? = null,
    
    /**
     * Indicates whether this is fresh data or potentially stale
     */
    val isFresh: Boolean = true
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        require(timestamp >= 0) { "Timestamp cannot be negative" }
        require(ageMillis >= 0) { "Age cannot be negative" }
    }
}

/**
 * Represents the operational status of a charging station or port.
 *
 * This is distinct from StaticVsLiveStatus which indicates data type,
 * while this represents actual operational state.
 */
enum class OperationalStatus {
    /**
     * Station/Port is available for use
     */
    AVAILABLE,

    /**
     * Station/Port is currently in use by an EV
     */
    IN_USE,

    /**
     * Station/Port is out of service (maintenance, broken, etc.)
     */
    OUT_OF_SERVICE,

    /**
     * Station/Port has a fault condition that prevents charging
     */
    FAULTED,

    /**
     * Status unknown or not reported
     */
    UNKNOWN
}

/**
 * Represents the distinction between canonical static infrastructure data and live operational state.
 *
 * This separation ensures:
 * 1. Canonical station records contain only static, unchanging facts
 * 2. Live status information is stored separately to avoid confusion
 * 3. Freshness and confidence are properly applied to each data type
 * 4. Data integrity is maintained by preventing live data from being written to static records
 */
data class StaticLiveSeparation(
    /**
     * The type of data (STATIC or LIVE) this represents
     */
    val statusType: StaticVsLiveStatus,
    
    /**
     * Freshness information for this data
     */
    val freshness: Freshness? = null,
    
    /**
     * Operational status when applicable (LIVE data only)
     */
    val operationalStatus: OperationalStatus? = null,
    
    /**
     * Additional metadata that depends on the data type
     */
    val metadata: Map<String, Any?>? = null
) {
    init {
        // If we have operational status, this must be LIVE data
        if (operationalStatus != null && statusType != StaticVsLiveStatus.LIVE) {
            throw IllegalArgumentException("Operational status can only be set for LIVE data")
        }
        
        // If we have live data but no freshness, it's questionable
        if (statusType == StaticVsLiveStatus.LIVE && freshness == null) {
            throw IllegalArgumentException("LIVE data must include freshness information")
        }
    }
}