package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.vehicle.ConnectorType

/**
 * Represents the result of validating a charging station record.
 */
data class ImportValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val success: Boolean get() = isValid
}
/**
 * Utility class for validating charging station data during import.
 *
 * This validation ensures that only valid, well-formed records are persisted to the database
 * and prevents invalid or potentially harmful data from entering the system.
 */
object ImportValidation {
    
    /**
     * Validates a normalized charging station record.
     *
     * @param record The normalized record to validate
     * @return Validation result with any errors or warnings
     */
    fun validateRecord(record: NormalizedChargingStationRecord): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate station ID
        if (record.id.isBlank()) {
            errors.add("Station ID is required")
        }
        
        // Validate external ID
        if (record.externalId.isBlank()) {
            warnings.add("External ID is missing - may impact deduplication")
        }
        
        // Validate location
        val locationValidation = validateLocation(record.location)
        errors.addAll(locationValidation.errors)
        warnings.addAll(locationValidation.warnings)
        
        // Validate metadata
        val metadataValidation = validateMetadata(record.metadata)
        errors.addAll(metadataValidation.errors)
        warnings.addAll(metadataValidation.warnings)
        
        // Validate ports
        val portsValidation = validatePorts(record.ports)
        errors.addAll(portsValidation.errors)
        warnings.addAll(portsValidation.warnings)
        
        // Validate provenance
        val provenanceValidation = validateProvenance(record.provenance)
        errors.addAll(provenanceValidation.errors)
        warnings.addAll(provenanceValidation.warnings)
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates the geographic location information.
     *
     * @param location The location to validate
     * @return Validation result with any errors or warnings
     */
    private fun validateLocation(location: StationLocation): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate latitude range
        if (location.coordinate.latitude < -90.0 || location.coordinate.latitude > 90.0) {
            errors.add("Latitude must be between -90 and 90 degrees")
        }
        
        // Validate longitude range
        if (location.coordinate.longitude < -180.0 || location.coordinate.longitude > 180.0) {
            errors.add("Longitude must be between -180 and 180 degrees")
        }
        
        // Validate that coordinates are not zero (unless explicitly allowed)
        if (location.coordinate.latitude == 0.0 && location.coordinate.longitude == 0.0) {
            warnings.add("Coordinates are at zero point - may indicate missing data")
        }
        
        // Validate province and city
        if (location.province.isNullOrBlank()) {
            warnings.add("Province information is missing")
        }
        
        if (location.city.isNullOrBlank()) {
            warnings.add("City information is missing")
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates the station metadata.
     *
     * @param metadata The metadata to validate
     * @return Validation result with any errors or warnings
     */
    private fun validateMetadata(metadata: StationMetadata): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate station name
        if (metadata.canonicalName.isNullOrBlank()) {
            warnings.add("Station name is missing - will use default")
        }
        
        // Validate operator
        if (metadata.operatorName.isNullOrBlank()) {
            warnings.add("Operator information is missing")
        }
        
        // Validate access type
        if (metadata.accessType == AccessType.UNKNOWN) {
            warnings.add("Access type is marked as UNKNOWN - may need verification")
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates charging ports.
     *
     * @param ports The list of ports to validate
     * @return Validation result with any errors or warnings
     */
    private fun validatePorts(ports: List<ChargingPort>): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate that we have at least one port
        if (ports.isEmpty()) {
            errors.add("Station must have at least one charging port")
        }
        
        // Validate each port
        for ((index, port) in ports.withIndex()) {
            val portValidation = validatePort(port)
            errors.addAll(portValidation.errors)
            warnings.addAll(portValidation.warnings)
            
            val cTypes = port.connectorTypes
            if (cTypes.isEmpty()) {
                warnings.add("No connector types specified for port - may affect compatibility")
            } else if (cTypes.any { it.name == "OTHER" }) {
                warnings.add("Contains 'OTHER' connector type - should be replaced with specific type")
            }
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates a single charging port.
     *
     * @param port The port to validate
     * @return Validation result with any errors or warnings
     */
    private fun validatePort(port: ChargingPort): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate port ID
        if (port.id.isBlank()) {
            errors.add("Port ID is required")
        }
        
        // Validate power values
        if (port.power != null) {
            if (port.power < 0) {
                if (port.isDc) {
                    errors.add("DC power cannot be negative")
                } else if (port.isAc) {
                    errors.add("AC power cannot be negative")
                } else {
                    errors.add("Power cannot be negative")
                }
            }
            if (port.power > 1000.0) {
                warnings.add("Power value seems unusually high")
            }
        }
        
        // Validate voltage and current
        port.voltage?.let { v ->
            if (v <= 0) {
                errors.add("Invalid voltage")
            }
        }
        port.current?.let { c ->
            if (c <= 0) {
                errors.add("Invalid current")
            }
        }
        
        // Validate that at least one of AC or DC is specified
        if (!port.isAc && !port.isDc) {
            warnings.add("Port has neither AC nor DC capability - may be incomplete")
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates connector types in a set.
     *
     * @param connectorTypes The set of connector types to validate
     * @return Validation result with any errors or warnings
     */
    private fun validateConnectorTypes(connectorTypes: Set<ConnectorType>): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate that we have at least one connector type
        if (connectorTypes.isEmpty()) {
            warnings.add("No connector types specified")
        }
        
        // Check for unknown connector types in the set
        val hasUnknown = connectorTypes.contains(ConnectorType.OTHER)
        if (hasUnknown) {
            warnings.add("Contains 'OTHER' connector type - should be replaced with specific type")
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates provenance information.
     *
     * @param provenance The provenance to validate
     * @return Validation result with any errors or warnings
     */
    private fun validateProvenance(provenance: Provenance): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (provenance.source.isBlank()) {
            errors.add("Source is required")
        }
        if (provenance.sourceReference.isNullOrBlank()) {
            warnings.add("Source Reference ID is missing - may impact deduplication")
        }
        if (provenance.confidence !in 0.0..1.0) {
            errors.add("Confidence must be between 0.0 and 1.0")
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
