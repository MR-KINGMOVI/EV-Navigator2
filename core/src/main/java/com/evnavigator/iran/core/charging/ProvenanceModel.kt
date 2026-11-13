package com.evnavigator.iran.core.charging

/**

 * Enum representing the source type of charging data.
 *
 * This helps categorize where data comes from for provenance tracking.
 */
enum class SourceType {
    /**
     * Data from a commercial charging network provider (e.g., ChargePoint, EVgo)
     */
    NETWORK_PROVIDER,

    /**
     * Data from a government or public infrastructure source
     */
    GOVERNMENT,

    /**
     * Data from a private business or organization
     */
    PRIVATE_BUSINESS,

    /**
     * Data from an open-source or community project
     */
    OPEN_SOURCE,

    /**
     * Data from user contributions or crowdsourcing
     */
    USER_CONTRIBUTION,

    /**
     * Data imported from a third-party dataset
     */
    THIRD_PARTY_DATASET,

    /**
     * Data from direct field verification or manual inspection
     */
    FIELD_VERIFICATION,
    OPERATOR_DIRECT,
    OFFLINE_EXTRACT,
    CROWDSOURCED,

    /**
     * Source type unknown or not specified
     */
    UNKNOWN
}

/**
 * Represents provenance information for charging infrastructure data.
 *
 * This model captures all the metadata needed to track where data came from,
 * when it was retrieved, and how reliable it is. It's crucial for maintaining
 * data quality and allowing users to understand the trustworthiness of information.
 */
data class Provenance(
    /**
     * The source of this data (e.g., "ChargePoint", "Shell", "OpenChargeMap")
     */
    val source: String,

    /**
     * Type of source (NETWORK_PROVIDER, GOVERNMENT, etc.)
     */
    val sourceType: Any = SourceType.UNKNOWN,

    /**
     * Reference ID from the source system (if available)
     */
    val sourceReference: String? = null,

    /**
     * Version of the dataset or record (if applicable)
     */
    val version: String? = null,

    /**
     * Timestamp when this data was retrieved or last updated
     */
    val retrievedDate: Long = System.currentTimeMillis(),

    /**
     * Timestamp when this data was originally created or first reported
     */
    val createdDate: Long? = null,

    /**
     * Timestamp when this data was last validated or verified
     */
    val validatedDate: Long? = null,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,

    /**
     * Validation status of this information
     */
    val validationStatus: ValidationStatus = ValidationStatus.UNKNOWN,

    /**
     * Notes about the source or data quality
     */
    val notes: String? = null,

    /**
     * Indicates whether this is a primary or secondary source
     */
    val isPrimarySource: Boolean = false,
    val enforceValidation: Boolean = true
) {
    init {
        if (enforceValidation) {
            require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        require(retrievedDate > 0) { "Retrieved date must be a valid timestamp" }
        createdDate?.let { 
            require(it <= retrievedDate) { "Created date cannot be after retrieved date" } 
        }
        validatedDate?.let { 
            require(it <= retrievedDate) { "Validated date cannot be after retrieved date" } 
        }
        }
    }

    /**
     * Checks if this data is considered reliable based on validation status and confidence
     */
    val isReliable: Boolean
        get() = when (validationStatus) {
            ValidationStatus.VERIFIED,
            ValidationStatus.PARTIALLY_VERIFIED -> confidence >= 0.7
            ValidationStatus.UNVERIFIED -> confidence >= 0.5
            ValidationStatus.USER_REPORTED -> false
            ValidationStatus.STALE -> false
            ValidationStatus.UNKNOWN -> false
            else -> false
        }

    /**
     * Gets the freshness of this data in days
     */
    
    val sourceId: String? get() = sourceReference
    val retrievalTimestamp: String get() = try { java.time.Instant.ofEpochMilli(retrievedDate).toString() } catch (e: Exception) { "2023-01-01T00:00:00Z" }
    val datasetVersion: String? get() = version

    constructor(
        source: String,
        sourceId: String? = null,
        sourceType: Any? = null,
        retrievalTimestamp: Any? = null,
        datasetVersion: String? = null,
        recordVersion: String? = null,
        confidence: Double = 0.5,
        verificationState: Any? = null
    ) : this(
        source = source,
        sourceReference = sourceId,
        sourceType = if (sourceType is SourceType) sourceType else (sourceType ?: SourceType.UNKNOWN),
        version = datasetVersion,
        confidence = confidence,
            enforceValidation = false,
        validationStatus = if (verificationState == ValidationStatus.VERIFIED || verificationState?.toString() == "VERIFIED") ValidationStatus.VERIFIED else if (verificationState == ValidationStatus.UNVERIFIED || verificationState?.toString() == "UNVERIFIED") ValidationStatus.UNVERIFIED else ValidationStatus.UNKNOWN,
        retrievedDate = if (retrievalTimestamp is Long && retrievalTimestamp > 0) retrievalTimestamp else System.currentTimeMillis()
    )

    val freshnessDays: Long
        get() {
            val now = System.currentTimeMillis()
            return (now - retrievedDate) / (24 * 60 * 60 * 1000)
        }

        val retrievedAt: Long get() = retrievedDate

    constructor(
        source: String,
        retrievedAt: Long,
        version: String? = null
    ) : this(
        source = source,
        retrievedDate = retrievedAt,
        version = version
    )

        companion object {
        object VerificationState { val VERIFIED = ValidationStatus.VERIFIED; val UNVERIFIED = ValidationStatus.UNVERIFIED }
        /**
         * Creates a provenance record for verified data from a network provider
         */
        fun createVerifiedNetworkProvider(
            source: String,
            sourceReference: String?,
            version: String? = null,
            retrievedDate: Long,
            confidence: Double = 0.9
        ): Provenance {
            return Provenance(
                source = source,
                sourceType = SourceType.NETWORK_PROVIDER,
                sourceReference = sourceReference,
                version = version,
                retrievedDate = retrievedDate,
                confidence = confidence,
                validationStatus = ValidationStatus.VERIFIED,
                isPrimarySource = true
            )
        }

        /**
         * Creates a provenance record for user-reported data
         */
        fun createUserReported(
            source: String,
            sourceReference: String?,
            retrievedDate: Long,
            confidence: Double = 0.4
        ): Provenance {
            return Provenance(
                source = source,
                sourceType = SourceType.USER_CONTRIBUTION,
                sourceReference = sourceReference,
                retrievedDate = retrievedDate,
                confidence = confidence,
                validationStatus = ValidationStatus.USER_REPORTED,
                isPrimarySource = false
            )
        }

        /**
         * Creates a minimal provenance record
         */
        fun createMinimal(
            source: String,
            retrievedDate: Long
        ): Provenance {
            return Provenance(
                source = source,
                retrievedDate = retrievedDate
            )
        }
    }
}

/**
 * Represents versioning information for charging infrastructure data.
 *
 * This model provides a way to track dataset versions and record versions
 * for maintaining consistency and tracking updates.
 */
data class VersionInfo(
    /**
     * Dataset version (e.g., "v1.2.3", "2023-Q4")
     */
    val datasetVersion: String? = null,

    /**
     * Record version (e.g., "r2" for record revision 2)
     */
    val recordVersion: String? = null,

    /**
     * Source version if the data comes from a specific source with versioning
     */
    val sourceVersion: String? = null,

    /**
     * Timestamp when this version was applied
     */
    val versionTimestamp: Long? = null,

    /**
     * Description of what changed in this version
     */
    val changeDescription: String? = null
) {
    init {
        versionTimestamp?.let { 
            require(it > 0) { "Version timestamp must be a valid timestamp" } 
        }
    }

        companion object {
        /**
         * Creates a version info for a new dataset
         */
        fun createDatasetVersion(
            datasetVersion: String,
            changeDescription: String? = null
        ): VersionInfo {
            return VersionInfo(
                datasetVersion = datasetVersion,
                changeDescription = changeDescription,
                versionTimestamp = System.currentTimeMillis()
            )
        }

        /**
         * Creates a version info for a record update
         */
        fun createRecordVersion(
            recordVersion: String,
            changeDescription: String? = null
        ): VersionInfo {
            return VersionInfo(
                recordVersion = recordVersion,
                changeDescription = changeDescription,
                versionTimestamp = System.currentTimeMillis()
            )
        }
    }
}





object ProvenanceModel {
    object VerificationState {
        val VERIFIED = ValidationStatus.VERIFIED
        val UNVERIFIED = ValidationStatus.UNVERIFIED
    }

    operator fun invoke(
        source: String,
        sourceId: String? = null,
        sourceType: Any? = null,
        retrievalTimestamp: Any? = null,
        datasetVersion: String? = null,
        recordVersion: String? = null,
        confidence: Double = 0.5,
        verificationState: Any? = null,
        retrievedAt: Long = if (retrievalTimestamp is Long && retrievalTimestamp > 0) retrievalTimestamp else System.currentTimeMillis(),
        version: String? = datasetVersion
    ): Provenance {
        return Provenance(
            source = source,
            sourceReference = sourceId,
            sourceType = if (sourceType is SourceType) sourceType else (sourceType ?: SourceType.UNKNOWN),
            version = version ?: datasetVersion,
            confidence = confidence,
            enforceValidation = false,
            validationStatus = if (verificationState == ValidationStatus.VERIFIED || verificationState?.toString() == "VERIFIED") ValidationStatus.VERIFIED else if (verificationState == ValidationStatus.UNVERIFIED || verificationState?.toString() == "UNVERIFIED") ValidationStatus.UNVERIFIED else ValidationStatus.UNKNOWN,
            retrievedDate = retrievedAt
        )
    }
}
