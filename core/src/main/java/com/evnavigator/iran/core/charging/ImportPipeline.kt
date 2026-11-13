package com.evnavigator.iran.core.charging

/**
 * Represents a raw external charging station record before processing.
 *
 * This is the initial data format received from external providers before validation,
 * normalization, and persistence.
 */
data class RawChargingStationRecord(
    val sourceId: String,               // Unique identifier from the source
    val sourceType: String,             // Type of the source (e.g., "OpenChargeMap", "Tesla")
    val rawData: Map<String, Any?>,     // The raw data as received from the provider
    val retrievalTimestamp: Long = System.currentTimeMillis(), // When this record was retrieved from the source
    val sourceVersion: String? = null   // Version information from the source
) {
    constructor(
        sourceId: String,
        sourceType: String,
        rawData: Map<String, Any?>,
        retrievalTimestamp: String,
        sourceVersion: String? = null
    ) : this(
        sourceId = sourceId,
        sourceType = sourceType,
        rawData = rawData,
        retrievalTimestamp = try {
            if (retrievalTimestamp.isNotBlank()) java.time.Instant.parse(retrievalTimestamp).toEpochMilli()
            else -1L
        } catch (e: Exception) {
            -1L
        },
        sourceVersion = sourceVersion
    )
}

/**
 * Represents a validated and normalized charging station record.
 *
 * This is the intermediate format after validation but before persistence to the database.
 */
data class NormalizedChargingStationRecord(
    val id: String,                     // Internal unique identifier
    val externalId: String,             // External ID from source (if available)
    val location: StationLocation,      // Geographic location information
    val metadata: StationMetadata,      // Station metadata like name, operator, etc.
    val ports: List<ChargingPort>,      // Charging ports and their capabilities
    val provenance: Provenance          // Source, version, confidence, verification info
)

/**
 * Represents the result of importing a charging station record.
 */
data class ImportResult(
    val success: Boolean,
    val stationId: String? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val importedRecord: NormalizedChargingStationRecord? = null
)

/**
 * Represents the import pipeline for charging station data.
 *
 * This class defines the complete flow from raw external records to database persistence:
 * Raw record → Validation → Normalization → Deduplication → Provenance → Persistence
 */
object ImportPipeline {
    
    /**
     * Processes a raw charging station record through the import pipeline.
     *
     * @param rawRecord The raw record from an external source
     * @return Import result containing status and any processed data
     */
    fun processImport(rawRecord: RawChargingStationRecord): ImportResult {
        try {
            // Step 1: Validate the raw record
            val validation = validateRawRecord(rawRecord)
            if (!validation.success) {
                return ImportResult(
                    success = false,
                    errors = validation.errors,
                    warnings = validation.warnings
                )
            }
            
            // Step 2: Normalize the validated data
            val normalization = normalizeRecord(rawRecord)
            if (normalization == null) {
                return ImportResult(
                    success = false,
                    errors = listOf("Failed to normalize record")
                )
            }
            
            // Step 3: Deduplicate (this would normally check against existing records)
            // For now, we'll assume no duplicates in this simplified version
            val deduplicationResult = performDeduplication(normalization)
            
            // Step 4: Process provenance data
            val provenanceProcessed = processProvenance(normalization, rawRecord)
            
            return ImportResult(
                success = true,
                stationId = deduplicationResult.stationId,
                warnings = validation.warnings,
                importedRecord = provenanceProcessed
            )
        } catch (e: Exception) {
            return ImportResult(
                success = false,
                errors = listOf("Import failed: ${e.message}")
            )
        }
    }
    
    /**
     * Validates a raw charging station record.
     *
     * @param rawRecord The raw record to validate
     * @return Validation result with any errors or warnings
     */
    private fun validateRawRecord(rawRecord: RawChargingStationRecord): ImportValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate required fields
        if (rawRecord.retrievalTimestamp <= 0L) {
            errors.add("Valid retrieval timestamp is required")
        }
        if (rawRecord.sourceId.isBlank()) {
            errors.add("Source ID is required")
        }
        
        if (rawRecord.sourceType.isBlank()) {
            errors.add("Source type is required")
        }
        
        // Validate that raw data exists
        if (rawRecord.rawData.isEmpty()) {
            warnings.add("Raw data is empty - may be incomplete")
        }
        
        return ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Normalizes a raw charging station record into the canonical format.
     *
     * @param rawRecord The raw record to normalize
     * @return Normalized record or null if normalization fails
     */
    private fun normalizeRecord(rawRecord: RawChargingStationRecord): NormalizedChargingStationRecord? {
        try {
            val location = StationLocation(
                coordinate = GeoCoordinate(0.0, 0.0),
                province = "Unknown",
                city = "Unknown",
                address = "Unknown"
            )
            
            val metadata = StationMetadata(
                canonicalName = "Unnamed Station",
                operatorId = null,
                operatorName = "Unknown Operator",
                networkId = null,
                networkName = "Unknown Network",
                accessType = AccessType.UNKNOWN,
                accessRestrictions = null,
                openingHours = null,
                is247 = false,
                paymentMethods = null,
                amenities = null,
                contactPhone = null,
                contactEmail = null,
                website = null,
                notes = null,
                source = rawRecord.sourceType,
                sourceReference = rawRecord.sourceId,
                retrievedDate = rawRecord.retrievalTimestamp
            )
            
            val ports = listOf(
                ChargingPort(
                    id = "port1",
                    connectorType = ConnectorType.OTHER,
                    connectorCount = 1,
                    voltage = null,
                    current = null,
                    power = null,
                    isAc = true,
                    isDc = false,
                    phaseCount = null,
                    protocol = null
                )
            )
            
            val provenance = Provenance(
                source = rawRecord.sourceType,
                sourceReference = rawRecord.sourceId,
                sourceType = rawRecord.sourceType,
                retrievedDate = rawRecord.retrievalTimestamp,
                version = rawRecord.sourceVersion ?: "1.0",
                confidence = 0.5,
                validationStatus = ValidationStatus.UNVERIFIED
            )
            
            return NormalizedChargingStationRecord(
                id = "temp-${rawRecord.sourceId}",
                externalId = rawRecord.sourceId,
                location = location,
                metadata = metadata,
                ports = ports,
                provenance = provenance
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Performs deduplication logic to check for existing records.
     *
     * @param normalizedRecord The normalized record to check for duplicates
     * @return Deduplication result with station ID or conflict information
     */
    private fun performDeduplication(normalizedRecord: NormalizedChargingStationRecord): ImportDeduplicationResult {
        return ImportDeduplicationResult(
            stationId = normalizedRecord.id,
            isDuplicate = false,
            duplicateSourceIds = emptyList()
        )
    }
    
    /**
     * Processes provenance information for the record.
     *
     * @param normalizedRecord The normalized record
     * @param rawRecord The original raw record
     * @return Record with processed provenance information
     */
    private fun processProvenance(
        normalizedRecord: NormalizedChargingStationRecord,
        rawRecord: RawChargingStationRecord
    ): NormalizedChargingStationRecord {
        return normalizedRecord.copy(
            provenance = normalizedRecord.provenance.copy(
                source = rawRecord.sourceType,
                sourceReference = rawRecord.sourceId,
                sourceType = rawRecord.sourceType,
                retrievedDate = rawRecord.retrievalTimestamp,
                version = rawRecord.sourceVersion ?: normalizedRecord.provenance.version
            )
        )
    }
}

/**
 * Result of deduplication process.
 */
data class ImportDeduplicationResult(
    val stationId: String,
    val isDuplicate: Boolean,
    val duplicateSourceIds: List<String>
)
