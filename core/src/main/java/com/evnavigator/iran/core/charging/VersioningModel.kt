package com.evnavigator.iran.core.charging

/**
 * Enum representing the different types of versioning that can apply to charging data.
 *
 * This helps categorize and manage how versions are applied across different components.
 */
enum class VersionType {
    /**
     * Dataset version - applies to entire datasets or collections of records
     */
    DATASET,

    /**
     * Record version - applies to individual records within a dataset
     */
    RECORD,

    /**
     * Source version - version of the original source data
     */
    SOURCE,

    /**
     * Schema version - version of the data schema or structure
     */
    SCHEMA,

    /**
     * Application version - version of the application that processed/created this data
     */
    APPLICATION,

    /**
     * Unknown or unspecified version type
     */
    UNKNOWN
}

/**
 * Represents a complete versioning system for charging infrastructure data.
 *
 * This model provides a comprehensive approach to tracking:
 * - Dataset versions (entire collections)
 * - Record versions (individual entries)
 * - Schema versions (data structure changes)
 * - Source versions (original provider versions)
 * - Application versions (processing tool versions)
 *
 * The versioning system supports deterministic behavior and proper change tracking.
 */
data class ChargingDataVersion(
    /**
     * Type of version this represents
     */
    val versionType: VersionType = VersionType.UNKNOWN,

    /**
     * Version identifier (e.g., "1.2.3", "2023-Q4")
     */
    val version: String? = null,

    /**
     * Timestamp when this version was applied or created
     */
    val timestamp: Long? = null,

    /**
     * Description of what changed in this version
     */
    val changeDescription: String? = null,

    /**
     * Reference to the original source if applicable (e.g., dataset name, file name)
     */
    val sourceReference: String? = null,

    /**
     * Version of the schema that this data conforms to
     */
    val schemaVersion: String? = null,

    /**
     * Version of the application that generated or processed this data
     */
    val applicationVersion: String? = null,

    /**
     * Indicates whether this version represents a major change
     */
    val isMajorVersion: Boolean = false,

    /**
     * Optional parent version (for tracking version hierarchies)
     */
    val parentVersion: String? = null,

    /**
     * Metadata about the versioning process
     */
    val metadata: Map<String, Any?>? = null
) {
    init {
        timestamp?.let { 
            require(it > 0) { "Timestamp must be a valid positive timestamp" } 
        }
        
        // If version is provided, it should not be blank
        version?.let { 
            require(it.isNotBlank()) { "Version cannot be blank if provided" } 
        }
        
        // Schema version should not be blank if provided
        schemaVersion?.let { 
            require(it.isNotBlank()) { "Schema version cannot be blank if provided" } 
        }
        
        // Application version should not be blank if provided
        applicationVersion?.let { 
            require(it.isNotBlank()) { "Application version cannot be blank if provided" } 
        }
    }

    /**
     * Checks if this version represents a significant change
     */
    val isSignificant: Boolean
        get() = isMajorVersion || (version?.contains("alpha") == false && version?.contains("beta") == false)

    companion object {
        /**
         * Creates a dataset version
         */
        fun createDatasetVersion(
            version: String,
            changeDescription: String? = null,
            sourceReference: String? = null,
            schemaVersion: String? = null,
            applicationVersion: String? = null,
            isMajorVersion: Boolean = false
        ): ChargingDataVersion {
            return ChargingDataVersion(
                versionType = VersionType.DATASET,
                version = version,
                timestamp = System.currentTimeMillis(),
                changeDescription = changeDescription,
                sourceReference = sourceReference,
                schemaVersion = schemaVersion,
                applicationVersion = applicationVersion,
                isMajorVersion = isMajorVersion
            )
        }

        /**
         * Creates a record version
         */
        fun createRecordVersion(
            version: String,
            changeDescription: String? = null,
            parentVersion: String? = null,
            metadata: Map<String, Any?>? = null
        ): ChargingDataVersion {
            return ChargingDataVersion(
                versionType = VersionType.RECORD,
                version = version,
                timestamp = System.currentTimeMillis(),
                changeDescription = changeDescription,
                parentVersion = parentVersion,
                metadata = metadata
            )
        }

        /**
         * Creates a schema version
         */
        fun createSchemaVersion(
            version: String,
            changeDescription: String? = null,
            applicationVersion: String? = null
        ): ChargingDataVersion {
            return ChargingDataVersion(
                versionType = VersionType.SCHEMA,
                version = version,
                timestamp = System.currentTimeMillis(),
                changeDescription = changeDescription,
                applicationVersion = applicationVersion
            )
        }

        /**
         * Creates a minimal version (useful for testing or defaults)
         */
        fun createMinimal(
            version: String
        ): ChargingDataVersion {
            return ChargingDataVersion(
                versionType = VersionType.UNKNOWN,
                version = version,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

/**
 * Represents the overall versioning state of a charging infrastructure dataset.
 *
 * This aggregates information about dataset versions, record versions, and
 * provides a comprehensive view of how data has been tracked and managed.
 */
data class DatasetVersioningState(
    /**
     * Current dataset version
     */
    val currentDatasetVersion: ChargingDataVersion? = null,

    /**
     * Current schema version
     */
    val currentSchemaVersion: ChargingDataVersion? = null,

    /**
     * Total number of records in the dataset
     */
    val totalRecords: Long = 0,

    /**
     * Number of verified records
     */
    val verifiedRecords: Long = 0,

    /**
     * Number of user-reported records
     */
    val userReportedRecords: Long = 0,

    /**
     * Timestamp when the dataset was last updated
     */
    val lastUpdated: Long? = null,

    /**
     * Source of the dataset (if known)
     */
    val dataSource: String? = null,

    /**
     * Overall confidence level for the dataset
     */
    val datasetConfidence: Double = 0.5,

    /**
     * Indicates whether this dataset has been validated
     */
    val isDatasetValidated: Boolean = false
) {
    init {
        require(datasetConfidence in 0.0..1.0) { "Dataset confidence must be between 0.0 and 1.0" }
        require(totalRecords >= 0) { "Total records cannot be negative" }
        require(verifiedRecords >= 0) { "Verified records cannot be negative" }
        require(userReportedRecords >= 0) { "User reported records cannot be negative" }
        require(verifiedRecords + userReportedRecords <= totalRecords) { 
            "Verified and user-reported records cannot exceed total records" 
        }
    }

    /**
     * Gets the percentage of verified records
     */
    val verifiedPercentage: Double
        get() = if (totalRecords > 0) {
            (verifiedRecords.toDouble() / totalRecords) * 100.0
        } else {
            0.0
        }

    /**
     * Gets the percentage of user-reported records
     */
    val userReportedPercentage: Double
        get() = if (totalRecords > 0) {
            (userReportedRecords.toDouble() / totalRecords) * 100.0
        } else {
            0.0
        }

    companion object {
        /**
         * Creates a minimal dataset versioning state
         */
        fun createMinimal(
            totalRecords: Long = 0,
            lastUpdated: Long? = null
        ): DatasetVersioningState {
            return DatasetVersioningState(
                totalRecords = totalRecords,
                lastUpdated = lastUpdated
            )
        }

        /**
         * Creates a dataset versioning state with full information
         */
        fun createFull(
            currentDatasetVersion: ChargingDataVersion? = null,
            currentSchemaVersion: ChargingDataVersion? = null,
            totalRecords: Long = 0,
            verifiedRecords: Long = 0,
            userReportedRecords: Long = 0,
            lastUpdated: Long? = null,
            dataSource: String? = null,
            datasetConfidence: Double = 0.5,
            isDatasetValidated: Boolean = false
        ): DatasetVersioningState {
            return DatasetVersioningState(
                currentDatasetVersion = currentDatasetVersion,
                currentSchemaVersion = currentSchemaVersion,
                totalRecords = totalRecords,
                verifiedRecords = verifiedRecords,
                userReportedRecords = userReportedRecords,
                lastUpdated = lastUpdated,
                dataSource = dataSource,
                datasetConfidence = datasetConfidence,
                isDatasetValidated = isDatasetValidated
            )
        }
    }
}