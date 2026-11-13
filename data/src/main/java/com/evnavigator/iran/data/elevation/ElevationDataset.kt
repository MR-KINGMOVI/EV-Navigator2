package com.evnavigator.iran.data.elevation

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.Index
import com.evnavigator.iran.data.map.converters.CoverageLevelConverter
import com.evnavigator.iran.data.map.converters.CoverageTypeConverter
import com.evnavigator.iran.data.map.converters.SourceTypeConverter

/**
 * Room database entity for offline elevation dataset.
 *
 * This entity represents a complete offline elevation dataset that can be used
 * for elevation sampling and profile generation when network access is unavailable.
 */
@Entity(
    tableName = "elevation_datasets",
    indices = [
        Index(value = ["datasetVersion"]),
        Index(value = ["source"]),
        Index(value = ["coverage"]),
        Index(value = ["coverageType"]),
        Index(value = ["coverageLevel"]),
        Index(value = ["installationState"]),
        Index(value = ["activeVersion"])
    ]
)
@TypeConverters(
    SourceTypeConverter::class,
    CoverageTypeConverter::class,
    CoverageLevelConverter::class
)
data class ElevationDataset(
    /**
     * Primary key for the elevation dataset
     */
    @PrimaryKey
    val id: String,

    /**
     * Dataset version identifier
     */
    val datasetVersion: String,

    /**
     * Source of this elevation data
     */
    val source: String,

    /**
     * Type of source (SATELLITE, SURVEYED, etc.)
     */
    val sourceType: String,

    /**
     * Geographic coverage for this dataset
     */
    val coverage: String,

    /**
     * Type of coverage (POINT, AREA, ROUTE, REGION, COUNTRY)
     */
    val coverageType: String,

    /**
     * Level of coverage (FULL, PARTIAL, NONE)
     */
    val coverageLevel: String,

    /**
     * Installation state of this dataset
     */
    val installationState: String,

    /**
     * Active version identifier (if this dataset is currently active)
     */
    val activeVersion: String? = null,

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
     * Version identifier for the elevation grid structure
     */
    val gridVersion: String? = null,

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
     * Indicates if this is a base elevation dataset (for versioning)
     */
    val isBaseElevation: Boolean = false,

    /**
     * Version of the coordinate system transformation, if applicable
     */
    val crsVersion: String? = null,

    /**
     * Resolution of the elevation data in degrees or meters
     */
    val resolution: Double? = null,

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
)