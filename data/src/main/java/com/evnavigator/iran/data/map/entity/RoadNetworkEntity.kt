package com.evnavigator.iran.data.map.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.Index
import com.evnavigator.iran.data.map.converters.*
import com.evnavigator.iran.domain.model.DatasetInstallationState

/**
 * Room database entity for offline road network dataset.
 *
 * This entity represents a complete offline road network dataset that can be used
 * for routing, navigation, and map display when network access is unavailable.
 *
 * Optimized with appropriate indexes for fast queries.
 */
@Entity(
    tableName = "road_networks",
    indices = [
        Index(value = ["datasetVersion"]),
        Index(value = ["source"]),
        Index(value = ["sourceType"]),
        Index(value = ["coverage"]),
        Index(value = ["coverageType"]),
        Index(value = ["coverageLevel"]),
        Index(value = ["graphVersion"]),
        Index(value = ["roadRestrictionVersion"]),
        Index(value = ["installationState"]),
        Index(value = ["activeVersion"]),
        Index(value = ["datasetVersion", "installationState"]),
        Index(value = ["source", "sourceType"]),
        Index(value = ["coverage", "coverageType"])
    ]
)
@TypeConverters(
    SourceTypeConverter::class,
    CoverageTypeConverter::class,
    CoverageLevelConverter::class,
    InstallationStateConverter::class,
    RoadRestrictionVersionConverter::class
)
data class RoadNetworkEntity(
    /**
     * Primary key for the road network dataset
     */
    @PrimaryKey
    val id: String,

    /**
     * Dataset version identifier
     */
    val datasetVersion: String,

    /**
     * Source of this road network data
     */
    val source: String,

    /**
     * Type of source (NETWORK_PROVIDER, GOVERNMENT, etc.)
     */
    val sourceType: SourceType,

    /**
     * Geographic coverage for this dataset
     */
    val coverage: String,

    /**
     * Type of coverage (POINT, AREA, ROUTE, REGION, COUNTRY)
     */
    val coverageType: CoverageType,

    /**
     * Level of coverage (FULL, PARTIAL, NONE)
     */
    val coverageLevel: CoverageLevel,

    /**
     * Version of the road graph structure
     */
    val graphVersion: String,

    /**
     * Version of road restrictions
     */
    val roadRestrictionVersion: String,

    /**
     * Installation state of this dataset
     */
    val installationState: DatasetInstallationState,

    /**
     * Active version identifier (if this dataset is currently active)
     */
    val activeVersion: Boolean? = null,

    /**
     * Timestamp when this dataset was created
     */
    val createdAt: Long,

    /**
     * Timestamp when this dataset was retrieved or last updated
     */
    val retrievedDate: Long,

    /**
     * Timestamp when this dataset was last validated or verified
     */
    val validatedDate: Long? = null,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,

    /**
     * Validation status of this information
     */
    val validationStatus: String? = null,

    /**
     * Version identifier for the road graph structure
     */
    val graphVersionId: String? = null,

    /**
     * Bounding box coordinates (minLat, minLng, maxLat, maxLng)
     */
    val boundingBox: String? = null,

    /**
     * Coordinate reference system used by this dataset
     */
    val crs: String? = "EPSG:4326",

    /**
     * Metadata about the dataset
     */
    val metadata: String? = null,

    /**
     * Indicates if this is a base map (for versioning)
     */
    val isBaseMap: Boolean = false,

    /**
     * Version of the coordinate system transformation, if applicable
     */
    val crsVersion: String? = null,

    /**
     * List of supported road types for routing purposes
     */
    val supportedRoadTypes: String? = null,

    /**
     * Timestamp when this dataset was last checked for integrity
     */
    val lastCheckedAt: Long? = null,

    /**
     * Indicates if the data is currently being used by any process
     */
    val inUse: Boolean = false,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: String? = null
) {

    val minLatitude: Double? get() = boundingBox?.split(",")?.getOrNull(0)?.toDoubleOrNull() ?: 35.5


    @androidx.room.Ignore
    constructor(
        id: String,
        datasetVersion: String,
        source: String,
        sourceType: Any,
        coverage: String,
        coverageType: Any,
        coverageLevel: Any,
        graphVersion: String,
        roadRestrictionVersion: String,
        installationState: Any,
        installedAt: Long? = null,
        sizeBytes: Long? = null,
        checksum: String? = null,
        minLatitude: Double? = null,
        maxLatitude: Double? = null,
        minLongitude: Double? = null,
        maxLongitude: Double? = null,
        nodeCount: Int? = null,
        segmentCount: Int? = null,
        activeVersion: Any? = null,
        createdAt: Long = installedAt ?: System.currentTimeMillis(),
        retrievedDate: Long = installedAt ?: System.currentTimeMillis()
    ) : this(
        id = id,
        datasetVersion = datasetVersion,
        source = source,
        sourceType = when(sourceType) {
            is SourceType -> sourceType
            is String -> try { SourceType.valueOf(sourceType) } catch (e: Exception) { SourceType.UNKNOWN }
            else -> SourceType.UNKNOWN
        },
        coverage = coverage,
        coverageType = when(coverageType) {
            is CoverageType -> coverageType
            is String -> try { CoverageType.valueOf(coverageType) } catch (e: Exception) { CoverageType.POINT }
            else -> CoverageType.POINT
        },
        coverageLevel = when(coverageLevel) {
            is CoverageLevel -> coverageLevel
            is String -> try { CoverageLevel.valueOf(coverageLevel) } catch (e: Exception) { CoverageLevel.FULL }
            else -> CoverageLevel.FULL
        },
        graphVersion = graphVersion,
        roadRestrictionVersion = roadRestrictionVersion,
        installationState = when(installationState) {
            is com.evnavigator.iran.domain.model.DatasetInstallationState -> installationState
            is String -> try { com.evnavigator.iran.domain.model.DatasetInstallationState.valueOf(installationState) } catch (e: Exception) { com.evnavigator.iran.domain.model.DatasetInstallationState.INSTALLED }
            else -> com.evnavigator.iran.domain.model.DatasetInstallationState.INSTALLED
        },
        activeVersion = when(activeVersion) {
            is Boolean -> activeVersion
            is String -> activeVersion.equals("true", ignoreCase = true) || activeVersion == "1"
            is Number -> activeVersion.toInt() != 0
            else -> null
        },
        createdAt = createdAt,
        retrievedDate = retrievedDate,
        boundingBox = if (minLatitude != null) "$minLatitude,$minLongitude,$maxLatitude,$maxLongitude" else null
    )

}
