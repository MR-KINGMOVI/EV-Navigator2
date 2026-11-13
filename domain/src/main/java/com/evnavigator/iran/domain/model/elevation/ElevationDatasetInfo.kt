package com.evnavigator.iran.domain.model.elevation

import java.util.*

/**
 * Information about an elevation dataset.
 *
 * Provides metadata and status information for a specific elevation dataset,
 * including its properties, coverage, quality metrics, and installation state.
 */
data class ElevationDatasetInfo(
    val id: String,
    val name: String,
    val version: String,
    val source: String,
    val sourceType: String,
    val description: String? = null,
    val coverage: String,
    val coverageType: String,
    val coverageLevel: String,
    val installationState: String,  // e.g., INSTALLED, INSTALLING, FAILED
    val activeVersion: String? = null,
    val createdAt: Date,
    val retrievedDate: Long,
    val validatedDate: Long?,
    val confidence: Double,
    val validationStatus: String?,
    val gridVersion: String?,
    val boundingBox: ElevationBoundingBox?,
    val crs: String,
    val metadata: String?,
    val isBaseElevation: Boolean,
    val crsVersion: String?,
    val resolution: Double?,
    val lastCheckedAt: Long?,
    val inUse: Boolean,
    val additionalProperties: Map<String, Any>?
) {
    companion object {
        /**
         * Creates a new ElevationDatasetInfo.
         */
        fun create(
            id: String,
            name: String,
            version: String,
            source: String,
            sourceType: String,
            description: String? = null,
            coverage: String,
            coverageType: String,
            coverageLevel: String,
            installationState: String,
            activeVersion: String? = null,
            createdAt: Date = Date(),
            retrievedDate: Long = System.currentTimeMillis(),
            validatedDate: Long? = null,
            confidence: Double = 0.5,
            validationStatus: String? = null,
            gridVersion: String? = null,
            boundingBox: ElevationBoundingBox? = null,
            crs: String = "EPSG:4326",
            metadata: String? = null,
            isBaseElevation: Boolean = false,
            crsVersion: String? = null,
            resolution: Double? = null,
            lastCheckedAt: Long? = null,
            inUse: Boolean = false,
            additionalProperties: Map<String, Any>? = null
        ): ElevationDatasetInfo {
            return ElevationDatasetInfo(
                id = id,
                name = name,
                version = version,
                source = source,
                sourceType = sourceType,
                description = description,
                coverage = coverage,
                coverageType = coverageType,
                coverageLevel = coverageLevel,
                installationState = installationState,
                activeVersion = activeVersion,
                createdAt = createdAt,
                retrievedDate = retrievedDate,
                validatedDate = validatedDate,
                confidence = confidence,
                validationStatus = validationStatus,
                gridVersion = gridVersion,
                boundingBox = boundingBox,
                crs = crs,
                metadata = metadata,
                isBaseElevation = isBaseElevation,
                crsVersion = crsVersion,
                resolution = resolution,
                lastCheckedAt = lastCheckedAt,
                inUse = inUse,
                additionalProperties = additionalProperties
            )
        }
    }
}