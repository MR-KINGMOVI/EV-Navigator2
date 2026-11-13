package com.evnavigator.iran.domain.model

import java.time.Instant

/**
 * Represents a dataset manifest that contains metadata about a dataset package.
 * This is the primary structure for describing datasets in the offline system.
 */
data class DatasetManifest(
    /**
     * Unique identifier for the dataset
     */
    val id: DatasetId,

    /**
     * Version of the dataset content
     */
    val version: DatasetVersion,

    /**
     * Version of the schema structure used in this dataset
     */
    val schemaVersion: DatasetVersion,

    /**
     * Version of the package format used for this dataset
     */
    val packageVersion: DatasetVersion,

    /**
     * Source information about where this dataset came from
     */
    val source: DatasetSource,

    /**
     * When this dataset was created
     */
    val creationTimestamp: Instant,

    /**
     * When this dataset was published (optional)
     */
    val publicationTimestamp: Instant? = null,

    /**
     * When this dataset expires (optional)
     */
    val expirationTimestamp: Instant? = null,

    /**
     * Geographic coverage of the dataset (optional)
     */
    val geographicCoverage: GeographicCoverage? = null,

    /**
     * Data coverage information for this dataset (optional)
     */
    val dataCoverage: DataCoverage? = null,

    /**
     * List of dependencies required by this dataset
     */
    val dependencies: List<DatasetDependency> = emptyList(),

    /**
     * Size of the package in bytes
     */
    val packageSize: Long = 0,

    /**
     * Integrity checksum for the package (optional)
     */
    val checksum: String? = null,

    /**
     * Algorithm used for integrity verification (optional)
     */
    val integrityAlgorithm: String? = null,

    /**
     * Compression method used in the package (optional)
     */
    val compression: String? = null,

    /**
     * Minimum app version required to use this dataset (optional)
     */
    val minimumAppVersion: String? = null,

    /**
     * Maximum app version supported for this dataset (optional)
     */
    val maximumAppVersion: String? = null,

    /**
     * Special requirements for installation (optional)
     */
    val installationRequirements: List<String> = emptyList(),

    /**
     * Additional compatibility metadata (optional)
     */
    val compatibilityMetadata: Map<String, Any> = emptyMap(),

    /**
     * Digital signature information (optional)
     */
    val signatureMetadata: Map<String, Any>? = null
) {
    constructor(
        id: String,
        version: String,
        type: Dataset.DatasetType = Dataset.DatasetType.MAP,
        schemaVersion: Any = "1.0.0",
        formatVersion: Any = "1.0.0",
        source: String = "test",
        creationTimestamp: Long = System.currentTimeMillis(),
        publicationTimestamp: Long? = null,
        packageSize: Long = 0L,
        checksum: String? = null,
        checksumAlgorithm: String? = null,
        coverage: Map<String, Any> = emptyMap(),
        packageVersion: String = formatVersion.toString()
    ) : this(
        id = DatasetId(id),
        version = DatasetVersion(version),
        schemaVersion = DatasetVersion(schemaVersion.toString()),
        packageVersion = DatasetVersion(packageVersion),
        source = DatasetSource(source),
        creationTimestamp = Instant.ofEpochMilli(creationTimestamp),
        publicationTimestamp = publicationTimestamp?.let { Instant.ofEpochMilli(it) },
        packageSize = packageSize,
        checksum = checksum,
        integrityAlgorithm = checksumAlgorithm
    )

    init {
        // Validate that required fields are present
        require(id != null) { "Dataset ID cannot be null" }
        require(version != null) { "Dataset version cannot be null" }
        require(schemaVersion != null) { "Schema version cannot be null" }
        require(packageVersion != null) { "Package version cannot be null" }
        require(source != null) { "Dataset source cannot be null" }
        require(creationTimestamp != null) { "Creation timestamp cannot be null" }
    }

    /**
     * Validates if this manifest represents a valid, usable dataset.
     * This method checks basic integrity requirements.
     */
    fun isValid(): Boolean {
        return id.value.isNotBlank() &&
                version.value.isNotBlank() &&
                schemaVersion.value.isNotBlank() &&
                packageVersion.value.isNotBlank() &&
                source.name.isNotBlank() &&
                creationTimestamp != null
    }

    /**
     * Checks if the dataset is still valid based on expiration and freshness.
     */
    fun isStillValid(): Boolean {
        // Check expiration
        if (expirationTimestamp != null && Instant.now().isAfter(expirationTimestamp)) {
            return false
        }
        
        // If no explicit expiration, assume valid
        return true
    }

    /**
     * Checks if this dataset can be used with the given app version.
     */
    fun isCompatibleWithAppVersion(appVersion: String): Boolean {
        // If no version requirements specified, assume compatible
        if (minimumAppVersion == null && maximumAppVersion == null) {
            return true
        }
        
        // For simplicity in this implementation, we'll check basic string comparison
        // In a real system, proper semantic versioning would be used
        
        val appVersionNum = parseVersionString(appVersion)
        val minVersionNum = if (minimumAppVersion != null) parseVersionString(minimumAppVersion) else null
        val maxVersionNum = if (maximumAppVersion != null) parseVersionString(maximumAppVersion) else null
        
        // Check minimum version requirement
        if (minVersionNum != null && appVersionNum < minVersionNum) {
            return false
        }
        
        // Check maximum version requirement  
        if (maxVersionNum != null && appVersionNum > maxVersionNum) {
            return false
        }
        
        return true
    }

    /**
     * Parses a version string into an integer for comparison.
     * This is a simplified implementation - in production, use proper semantic versioning.
     */
    internal fun parseVersionString(version: String): Long {
        val numbers = version.split("\\.".toRegex()).map { it.toIntOrNull() ?: 0 }
        return (numbers.getOrElse(0) { 0 } * 1000000L) +
               (numbers.getOrElse(1) { 0 } * 1000L) +
               (numbers.getOrElse(2) { 0 })
    }

    /**
     * Gets the freshness status of this dataset based on its timestamps and expiration.
     */
    fun getFreshness(): DatasetFreshness {
        val now = Instant.now()
        
        // Check expiration first
        if (expirationTimestamp != null && now.isAfter(expirationTimestamp)) {
            return DatasetFreshness.EXPIRED
        }
        
        // If we have publication timestamp, check how old it is
        if (publicationTimestamp != null) {
            val age = now.toEpochMilli() - publicationTimestamp.toEpochMilli()
            // Consider dataset stale after 30 days (in a real system this would be configurable)
            if (age > 30 * 24 * 60 * 60 * 1000L) {
                return DatasetFreshness.STALE_BUT_USABLE
            }
        }
        
        // If it's not expired and relatively fresh, mark as fresh
        return DatasetFreshness.FRESH
    }
}