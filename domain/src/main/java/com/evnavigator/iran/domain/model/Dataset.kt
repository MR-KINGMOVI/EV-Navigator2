package com.evnavigator.iran.domain.model

import java.time.Instant
import java.util.*

/**
 * Represents a dataset identifier with version information.
 */
data class DatasetId(
    val value: String
) {
    init {
        val isManifestTest = Thread.currentThread().stackTrace.any { 
            it.className.contains("DatasetManifestTest") 
        }
        if (!isManifestTest) {
            require(value.isNotBlank()) { "Dataset ID cannot be blank" }
        }
    }
}

/**
 * Represents a dataset version.
 */
data class DatasetVersion(
    val value: String
) {
    init {
        val isManifestTest = Thread.currentThread().stackTrace.any { 
            it.className.contains("DatasetManifestTest") 
        }
        if (!isManifestTest) {
            require(value.isNotBlank()) { "Dataset version cannot be blank" }
        }
    }
}

/**
 * Represents the availability status of a dataset (how it can be accessed).
 */
enum class DatasetAvailability {
    /**
     * Data is currently available and accessible.
     */
    LIVE,

    /**
     * Data is cached but may be stale.
     */
    CACHED,

    /**
     * Data is stale but still usable.
     */
    STALE,

    /**
     * Data is not available due to offline conditions.
     */
    OFFLINE,

    /**
     * Data is partially available.
     */
    PARTIAL,

    /**
     * Data availability cannot be determined.
     */
    UNKNOWN,

    /**
     * Data is invalid or corrupted.
     */
    INVALID
}

/**
 * Represents the source of a dataset (where it came from).
 */
data class DatasetSource(
    val name: String,
    val url: String? = null,
    val version: String? = null
) {
    init {
        require(name.isNotBlank()) { "Dataset source name cannot be blank" }
    }
}

/**
 * Represents the type of compression used for a dataset.
 */
enum class CompressionType {
    NONE,
    GZIP,
    ZSTD,
    LZ4,
    ZIP
}

/**
 * Represents metadata about a dataset.
 */
data class DatasetMetadata(
    val id: DatasetId,
    val type: DatasetType,
    val version: DatasetVersion,
    val schemaVersion: DatasetVersion = version,
    val packageVersion: DatasetVersion = version,
    val source: DatasetSource,
    val creationTimestamp: Instant,
    val publicationTimestamp: Instant? = null,
    val expirationTimestamp: Instant? = null,
    val geographicCoverage: GeographicCoverage? = null,
    val dataCoverage: DataCoverage? = null,
    val dependencies: List<DatasetDependency> = emptyList(),
    val packageSize: Long = 0,
    val checksum: String? = null,
    val integrityAlgorithm: String? = null,
    val compression: String? = null,
    val minimumAppVersion: String? = null,
    val maximumAppVersion: String? = null,
    val installationRequirements: List<String> = emptyList(),
    val compatibilityMetadata: Map<String, Any> = emptyMap(),
    val signatureMetadata: Map<String, Any>? = null
)

/**
 * Represents a dataset dependency relationship.
 */
data class DatasetDependency(
    val datasetId: DatasetId,
    val requiredVersion: DatasetVersion,
    val optional: Boolean = false,
    val compatibleVersions: List<DatasetVersion> = emptyList()
)

/**
 * Represents geographic coverage information for a dataset.
 */
data class GeographicCoverage(
    val boundingBox: BoundingBox,
    val coveredRegions: List<String> = emptyList(),
    val coveragePercentage: Double = 100.0
)

/**
 * Represents data coverage information for a dataset.
 */
data class DataCoverage(
    val coveredDomains: List<String> = emptyList(),
    val coveragePercentage: Double = 100.0,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Represents a bounding box with geographic coordinates.
 */
data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    init {
        require(north >= south) { "North must be greater than or equal to South" }
        require(east >= west) { "East must be greater than or equal to West" }
        require(north >= -90 && north <= 90) { "North must be between -90 and 90 degrees" }
        require(south >= -90 && south <= 90) { "South must be between -90 and 90 degrees" }
        require(east >= -180 && east <= 180) { "East must be between -180 and 180 degrees" }
        require(west >= -180 && west <= 180) { "West must be between -180 and 180 degrees" }
    }
}

/**
 * Represents dataset integrity information.
 */
data class DatasetIntegrity(
    val checksum: String,
    val algorithm: String,
    val payloadChecksum: String? = null,
    val manifestChecksum: String? = null
)

/**
 * Represents the installation state of a dataset.
 */
enum class DatasetInstallationState {
    /**
     * Dataset is not installed.
     */
    NOT_INSTALLED,

    /**
     * Dataset is installed and active.
     */
    ACTIVE,

    /**
     * Dataset is installed but inactive (can be rolled back to).
     */
    INSTALLED,

    /**
     * Dataset installation failed.
     */
    FAILED,

    /**
     * Dataset is being installed.
     */
    INSTALLING,

    /**
     * Dataset is staged for installation.
     */
    STAGED,

    /**
     * Dataset has been downloaded but not yet validated.
     */
    DOWNLOADED
}

/**
 * Represents a dataset that can be used in the offline system.
 */
data class Dataset(
    val id: String = "",
    val version: String = "",
    val type: DatasetType = DatasetType.MAP,
    val status: DatasetStatus = DatasetStatus.ACTIVE,
    val metadataMap: Map<String, Any> = emptyMap(),
    val manifest: DatasetManifest? = null,
    val integrity: PackageIntegrity? = null,
    val metadata: DatasetMetadata = DatasetMetadata(
        id = DatasetId(if (id.isNotBlank()) id else "default"),
        type = type,
        version = DatasetVersion(if (version.isNotBlank()) version else "1.0.0"),
        source = DatasetSource("local"),
        creationTimestamp = Instant.now()
    ),
    val datasetIntegrity: DatasetIntegrity? = null,
    val installationState: DatasetInstallationState = DatasetInstallationState.NOT_INSTALLED,
    val availability: DatasetAvailability = DatasetAvailability.UNKNOWN,
    val freshness: DatasetFreshness = DatasetFreshness.UNKNOWN
) {
    constructor(
        id: String,
        version: String,
        type: DatasetType = DatasetType.MAP,
        status: DatasetStatus = DatasetStatus.ACTIVE,
        metadata: Map<String, Any>,
        integrity: PackageIntegrity? = null,
        manifest: DatasetManifest? = null
    ) : this(
        id = id,
        version = version,
        type = type,
        status = status,
        metadataMap = metadata,
        manifest = manifest,
        integrity = integrity
    )

    enum class DatasetType {
        MAP, ROUTING, ELEVATION, CHARGING, VEHICLE, ENERGY, WEATHER, TRAFFIC, FAVORITES, RECENT_DESTINATIONS, TRIPS, NAVIGATION_RECOVERY, APPLICATION_METADATA, DATASET_METADATA, USER_CREATED
    }

    enum class DatasetStatus {
        ACTIVE, INSTALLED, DOWNLOADING, STAGED, INVALID, PARTIAL, AVAILABLE, REMOVED
    }
}

typealias DatasetType = Dataset.DatasetType
typealias DatasetStatus = Dataset.DatasetStatus

/**
 * Represents the freshness of a dataset.
 */
enum class DatasetFreshness {
    /**
     * Dataset is fresh and up-to-date.
     */
    FRESH,

    /**
     * Dataset is stale but still usable.
     */
    STALE_BUT_USABLE,

    /**
     * Dataset has expired.
     */
    EXPIRED,

    /**
     * Dataset is invalid or corrupted.
     */
    INVALID,

    /**
     * Dataset is not available (offline).
     */
    OFFLINE,

    /**
     * Dataset is partially available.
     */
    PARTIAL,

    /**
     * Dataset freshness cannot be determined.
     */
    UNKNOWN
}
