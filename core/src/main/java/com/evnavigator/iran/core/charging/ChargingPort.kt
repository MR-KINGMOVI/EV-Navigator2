package com.evnavigator.iran.core.charging

/**
 * Canonical charging port/EVSE (Electric Vehicle Supply Equipment) representation.
 *
 * This model represents a single charging port at a station:
 * - EVSE identity
 * - Connector type
 * - Connector count
 * - Nominal power
 * - Voltage and current specifications
 * - AC/DC capabilities
 * - Status semantics
 *
 * The model supports the relationship:
 * Station
 * └── EVSE (1 or more)
 *     └── Connector(s) (1 or more)
 */
data class ChargingPort(
    // Identity
    val id: String, // Unique identifier for this charging port
    
    // Connection details
    val connectorType: ConnectorType, // Type of connector
    val connectorCount: Int = 1, // Number of connectors of this type at this EVSE
    
    // Electrical specifications  
    val voltage: Double? = null, // Nominal voltage (V) 
    val current: Double? = null, // Nominal current (A)
    val power: Double? = null, // Nominal power (kW)
    
    // AC/DC capabilities
    val isAc: Boolean, // Whether this is AC charging
    val isDc: Boolean, // Whether this is DC charging
    
    // Physical and operational details
    val phaseCount: Int? = null, // Number of phases (1 or 3 for AC)
    val protocol: ChargingProtocol? = null, // Charging protocol if known
    
    // Status information
    val status: PortStatus = PortStatus.UNKNOWN, // Current operational status
    val pricePerKwh: Double? = null, // Price per kWh in currency units
    
    // Source and provenance
    val source: String? = null, // Source of this port data
    val sourceReference: String? = null, // Reference ID from source system
    val retrievedDate: Long? = null, // When this information was retrieved
    val confidence: Double = 0.5, // Confidence level 0.0 to 1.0
    
    // Validation status
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN,
    val enforceStrict: Boolean = true,
    val rawConnectorTypes: Set<Any>? = null
) {
    
    init {
        require(id.isNotBlank()) { "Port ID cannot be blank" }
        require(connectorCount >= 0) { "Connector count cannot be negative" }
        
        // Validate voltage and current if provided
        voltage?.let { 
            require(it > 0) { "Voltage must be positive if provided" } 
        }
        current?.let { 
            require(it > 0) { "Current must be positive if provided" } 
        }
        
        // Validate power if provided
        if (enforceStrict) {
            power?.let { 
                require(it >= 0) { "Power cannot be negative if provided" } 
            }
        }
        
        // Validate confidence level
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        
        // Ensure that AC/DC flags are consistent
        require(isAc || isDc) { "Port must support either AC or DC charging" }
    }
    
    /**
     * Gets the effective power in kilowatts
     */
    
    val stationId: String get() = sourceReference ?: id
    val connectorTypes: Set<ConnectorType>
        get() = rawConnectorTypes?.mapNotNull { item ->
            when (item) {
                is ConnectorType -> item
                is com.evnavigator.iran.core.vehicle.ConnectorType -> try { ConnectorType.valueOf(item.name) } catch (e: Exception) { null }
                else -> try { ConnectorType.valueOf(item.toString()) } catch (e: Exception) { null }
            }
        }?.toSet() ?: setOf(connectorType)
    val acPowerKw: Double? get() = if (isAc) power else null
    val dcPowerKw: Double? get() = if (isDc) power else null

    constructor(
        id: String,
        stationId: String? = null,
        connectorTypes: Set<Any>? = null,
        connectorType: ConnectorType = (connectorTypes?.firstOrNull() as? ConnectorType)
            ?: (connectorTypes?.firstOrNull()?.let {
                try { ConnectorType.valueOf(it.toString()) } catch (e: Exception) { null }
            }) ?: ConnectorType.CCS_COMBO_2,
        acPowerKw: Double? = null,
        dcPowerKw: Double? = null,
        isAc: Boolean = acPowerKw != null,
        isDc: Boolean = dcPowerKw != null,
        voltageV: Double? = null,
        currentA: Double? = null,
        phaseCount: Int? = null,
        protocol: Any? = null,
        status: PortStatus = PortStatus.UNKNOWN
    ) : this(
        id = id,
        connectorType = connectorType,
        power = dcPowerKw ?: acPowerKw,
        isAc = isAc || (acPowerKw != null),
        isDc = isDc || (dcPowerKw != null),
        voltage = voltageV,
        current = currentA,
        phaseCount = phaseCount,
        sourceReference = stationId,
        status = status,
        enforceStrict = false,
        rawConnectorTypes = connectorTypes
    )

    val effectivePowerKw: Double?
        get() = when {
            power != null -> power
            voltage != null && current != null -> (voltage * current) / 1000.0
            else -> null
        }
    
    /**
     * Checks if this port is compatible with a vehicle connector type
     */
    fun isCompatibleWith(vehicleConnectorType: ConnectorType): Boolean {
        // For now, basic compatibility check - in future implementations 
        // we'll have more sophisticated logic for adapter compatibility
        return this.connectorType == vehicleConnectorType
    }
    
    /**
     * Gets the human-readable description of this port
     */
    fun getHumanReadableDescription(): String {
        val parts = mutableListOf<String>()
        
        if (connectorCount > 1) {
            parts.add("$connectorCount x $connectorType")
        } else {
            parts.add(connectorType.name)
        }
        
        voltage?.let { parts.add("${String.format("%.0f", it)}V") }
        current?.let { parts.add("${String.format("%.0f", it)}A") }
        power?.let { parts.add("${String.format("%.1f", it)}kW") }
        
        return parts.joinToString(" ")
    }
    
    companion object {
        /**
         * Creates a simple AC charging port
         */
        fun createAcPort(
            id: String,
            connectorType: ConnectorType,
            voltage: Double,
            current: Double,
            phaseCount: Int = 1
        ): ChargingPort {
            return ChargingPort(
                id = id,
                connectorType = connectorType,
                connectorCount = 1,
                voltage = voltage,
                current = current,
                power = (voltage * current * phaseCount) / 1000.0,
                isAc = true,
                isDc = false,
                phaseCount = phaseCount
            )
        }
        
        /**
         * Creates a simple DC charging port
         */
        fun createDcPort(
            id: String,
            connectorType: ConnectorType,
            voltage: Double,
            current: Double
        ): ChargingPort {
            return ChargingPort(
                id = id,
                connectorType = connectorType,
                connectorCount = 1,
                voltage = voltage,
                current = current,
                power = (voltage * current) / 1000.0,
                isAc = false,
                isDc = true
            )
        }
    }
}

/**
 * Represents charging protocol types for EVSEs
 */
enum class ChargingProtocol {
    CCS,           // Combined Charging System  
    CHAdeMO,       // Japanese standard
    GB_T,          // Chinese standard
    TESLA,         // Tesla proprietary
    OTHER          // Other unspecified protocol
}

/**
 * Represents the operational status of a charging port
 */
enum class PortStatus {
    AVAILABLE,      // Port is available for use
    IN_USE,         // Port is currently in use
    OUT_OF_SERVICE, // Port is out of service (broken, maintenance)
    FAULTED,        // Port has a fault condition
    UNKNOWN         // Status unknown or not reported
}