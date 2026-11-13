package com.evnavigator.iran.core.charging

/**
 * Canonical charging station model representing a complete charging infrastructure entity.
 *
 * This is the primary domain model for charging stations in the EV Navigator Iran system.
 * It combines all essential information about a charging station including:
 * - Identity and unique identification
 * - Geographic location
 * - Metadata and descriptive information
 * - Connector capabilities
 * - Power specifications
 * - Operational status
 * - Access and payment information
 * - Source provenance and confidence metrics
 *
 * The model is designed to be immutable and serializable for persistence and network transfer.
 */
data class ChargingStation(
    // === Identity ===
    val id: String, // Unique identifier for this station
    
    // === Geographic Location ===
    val location: StationLocation, // Complete geographic and address information
    
    // === Metadata ===
    val metadata: StationMetadata, // Descriptive and operational information
    
    // === Connector Configuration ===
    val ports: List<ChargingPort>, // All charging ports available at this station
    
    // === Power Capabilities ===
    val powerCapabilities: List<PowerCapability>, // Overall power capabilities of the station
    
    // === Status Information ===
    val status: StationStatus, // Current operational status (available, in use, etc.)
    
    // === Operational Details ===
    val isOperational: Boolean = true, // Whether the station is currently operational
    
    // === Source and Provenance ===
    val provenance: Provenance, // Data source tracking and confidence information
    
    // === Versioning ===
    val version: String = "1.0", // Version of this station record
    
    // === Timestamps ===
    val createdAt: Long = System.currentTimeMillis(), // When this record was created
    val updatedAt: Long = System.currentTimeMillis(), // When this record was last updated
    
    // === Validation Status ===
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN
) {
    
    
    val externalId: String? get() = metadata.sourceReference
    val name: String get() = metadata.canonicalName
    val stationId: ChargingStationId get() = ChargingStationId.fromString(id)

    constructor(
        id: ChargingStationId,
        location: StationLocation,
        metadata: StationMetadata,
        ports: List<ChargingPort>,
        powerCapabilities: List<PowerCapability> = emptyList(),
        status: StationStatus = StationStatus.OPERATIONAL,
        isOperational: Boolean = true,
        provenance: Provenance = Provenance.createMinimal("UNKNOWN", System.currentTimeMillis()),
        version: String = "1.0",
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
        validationStatus: ValidationStatus = provenance.validationStatus
    ) : this(
        id = id.id,
        location = location,
        metadata = metadata,
        ports = ports,
        powerCapabilities = if (powerCapabilities.isNotEmpty()) powerCapabilities else ports.map { PowerCapability(powerKw = it.power ?: 50.0, isAc = it.isAc, isDc = it.isDc, enforceStrictConsistency = false) },
        status = status,
        isOperational = isOperational,
        provenance = provenance,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        validationStatus = validationStatus
    )

    constructor(
        id: String,
        externalId: String? = null,
        location: StationLocation,
        metadata: StationMetadata,
        ports: List<ChargingPort>,
        powerCapabilities: List<PowerCapability> = emptyList(),
        status: StationStatus = StationStatus.OPERATIONAL,
        isOperational: Boolean = true,
        provenance: Provenance = Provenance.createMinimal("UNKNOWN", System.currentTimeMillis()),
        version: String = "1.0",
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
        validationStatus: ValidationStatus = provenance.validationStatus
    ) : this(
        id = id,
        location = location,
        metadata = if (externalId != null && metadata.sourceReference == null) metadata.copy(sourceReference = externalId) else metadata,
        ports = ports,
        powerCapabilities = if (powerCapabilities.isNotEmpty()) powerCapabilities else ports.map { PowerCapability(powerKw = it.power ?: 50.0, isAc = it.isAc, isDc = it.isDc, enforceStrictConsistency = false) },
        status = status,
        isOperational = isOperational,
        provenance = provenance,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        validationStatus = validationStatus
    )

    init {
        // Validate that we have at least one port
        require(ports.isNotEmpty()) { "A charging station must have at least one charging port" }
        
        // Validate that all ports have unique IDs
        val portIds = ports.map { it.id }
        require(portIds.distinct().size == portIds.size) { 
            "All charging ports must have unique IDs" 
        }
        
        // Validate that we have at least one power capability or default to unknown
        require(powerCapabilities.isNotEmpty() || validationStatus == ValidationStatus.UNKNOWN) { 
            "Station must have at least one power capability or be marked as unknown" 
        }
    }
    
    /**
     * Gets the total number of charging connectors at this station
     */
    val totalConnectorCount: Int
        get() = ports.sumOf { it.connectorCount }
    
    /**
     * Gets all connector types available at this station
     */
    val connectorTypes: Set<ConnectorType>
        get() = ports.map { it.connectorType }.toSet()
    
    /**
     * Checks if this station has a specific connector type
     */
    fun hasConnectorType(type: ConnectorType): Boolean {
        return ports.any { it.connectorType == type }
    }
    
    /**
     * Gets the maximum power capability of this station
     */
    val maxPowerKw: Double?
        get() = powerCapabilities.mapNotNull { it.powerKw }.maxOrNull()
    
    /**
     * Gets the minimum power capability of this station
     */
    val minPowerKw: Double?
        get() = powerCapabilities.mapNotNull { it.powerKw }.minOrNull()
    
    /**
     * Checks if this station supports AC charging
     */
    val supportsAc: Boolean
        get() = ports.any { it.isAc }
    
    /**
     * Checks if this station supports DC charging
     */
    val supportsDc: Boolean
        get() = ports.any { it.isDc }
    
    /**
     * Gets the count of AC charging ports
     */
    val acPortCount: Int
        get() = ports.count { it.isAc }
    
    /**
     * Gets the count of DC charging ports
     */
    val dcPortCount: Int
        get() = ports.count { it.isDc }
    
    /**
     * Gets all available AC power capabilities (in kW)
     */
    val acPowerCapabilities: List<PowerCapability>
        get() = powerCapabilities.filter { it.isAc }
    
    /**
     * Gets all available DC power capabilities (in kW)
     */
    val dcPowerCapabilities: List<PowerCapability>
        get() = powerCapabilities.filter { it.isDc }
    
    /**
     * Gets the station's operator information
     */
    val operatorInfo: OperatorInfo?
        get() = if (metadata.operatorId != null) {
            OperatorInfo(
                id = metadata.operatorId,
                name = metadata.operatorName ?: "Unknown"
            )
        } else {
            null
        }
    
    /**
     * Checks if this station is accessible to the public
     */
    val isPublic: Boolean
        get() = metadata.accessType == AccessType.PUBLIC
    
    /**
     * Gets the station's name for display purposes
     */
    val displayName: String
        get() = metadata.canonicalName
    
    /**
     * Creates a copy of this station with updated information
     */
    fun copy(
        id: String = this.id,
        location: StationLocation = this.location,
        metadata: StationMetadata = this.metadata,
        ports: List<ChargingPort> = this.ports,
        powerCapabilities: List<PowerCapability> = this.powerCapabilities,
        status: StationStatus = this.status,
        isOperational: Boolean = this.isOperational,
        provenance: Provenance = this.provenance,
        version: String = this.version,
        updatedAt: Long = System.currentTimeMillis(),
        validationStatus: ValidationStatus = this.validationStatus
    ): ChargingStation {
        return ChargingStation(
            id = id,
            location = location,
            metadata = metadata,
            ports = ports,
            powerCapabilities = powerCapabilities,
            status = status,
            isOperational = isOperational,
            provenance = provenance,
            version = version,
            createdAt = this.createdAt,
            updatedAt = updatedAt,
            validationStatus = validationStatus
        )
    }
    
    /**
     * Creates a new station with updated provenance information
     */
    fun withUpdatedProvenance(newProvenance: Provenance): ChargingStation {
        return copy(provenance = newProvenance, updatedAt = System.currentTimeMillis())
    }
}

/**
 * Operator information for charging stations
 */
data class OperatorInfo(
    val id: String,
    val name: String
)

/**
 * Status of a charging station
 */
enum class StationStatus {
    /**
     * Station is operational and available
     */
    OPERATIONAL,
    
    /**
     * Station is temporarily out of service
     */
    OUT_OF_SERVICE,
    
    /**
     * Station is under maintenance
     */
    MAINTENANCE,
    
    /**
     * Station is in use (currently charging)
     */
    IN_USE,
    
    /**
     * Station status is unknown or not specified
     */
    UNKNOWN
}
