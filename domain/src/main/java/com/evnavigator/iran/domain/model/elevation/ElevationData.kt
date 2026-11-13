package com.evnavigator.iran.domain.model.elevation

import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * Represents elevation data for a specific location.
 */
data class ElevationData(
    val coordinate: GeoCoordinate,
    val elevation: Double, // In meters
    val resolution: Double?, // Resolution in meters (horizontal accuracy)
    val verticalAccuracy: Double?, // Vertical accuracy in meters
    val timestamp: Long? = null,
    val source: String? = null,
    val datasetVersion: String? = null
) {
    companion object {
        /**
         * Creates a new ElevationData instance with the same coordinate but different elevation.
         */
        fun withElevation(elevationData: ElevationData, newElevation: Double): ElevationData {
            return elevationData.copy(elevation = newElevation)
        }

        /**
         * Gets the elevation in meters (ensuring consistency).
         */
        fun getElevationInMeters(elevationData: ElevationData): Double {
            return elevationData.elevation
        }
    }
}

data class ElevationDatasetMetadata(
    val id: String,
    val name: String,
    val version: String,
    val coverage: ElevationBoundingBox? = null,
    val resolutionMeters: Double = 30.0,
    val format: String = "HGT",
    val source: String = "SRTM",
    val description: String = ""
)

data class ElevationDataWithQuality(
    val data: ElevationData,
    val quality: com.evnavigator.iran.domain.model.ElevationQuality = com.evnavigator.iran.domain.model.ElevationQuality.MEDIUM,
    val confidence: Double = 1.0
)

data class ElevationDataWithConfidence(
    val data: ElevationData,
    val confidence: Double
)

data class TimedElevationData(
    val data: ElevationData,
    val timestamp: Long
)

data class ElevationDataWithUncertainty(
    val data: ElevationData,
    val uncertaintyMeters: Double
)

data class ComprehensiveElevationData(
    val data: ElevationData,
    val quality: com.evnavigator.iran.domain.model.ElevationQuality = com.evnavigator.iran.domain.model.ElevationQuality.MEDIUM,
    val confidence: Double = 1.0,
    val slope: Double? = null,
    val aspect: Double? = null
)

data class RichElevationData(
    val coordinate: GeoCoordinate,
    val elevation: Double,
    val quality: com.evnavigator.iran.domain.model.ElevationQuality = com.evnavigator.iran.domain.model.ElevationQuality.MEDIUM,
    val confidence: Double = 1.0,
    val slope: Double? = null
)

data class ElevationDataWithMetadata(
    val data: ElevationData,
    val metadata: ElevationDatasetMetadata? = null
)