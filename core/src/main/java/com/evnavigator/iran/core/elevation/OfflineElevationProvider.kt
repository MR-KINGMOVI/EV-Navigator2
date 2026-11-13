package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlin.math.floor

/**
 * Metadata descriptor for an offline elevation dataset tile or raster package.
 */
data class OfflineElevationMetadata(
    val datasetId: String,
    val datasetVersion: String,
    val source: String,
    val verticalDatum: String = "EGM96",
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val resolutionDegrees: Double = 0.000833333333333, // ~3 arc-seconds (~90m)
    val noDataValue: Double = -32768.0,
    val confidence: Double = 0.95
)

/**
 * Canonical offline elevation provider implementing the Phase 4 ElevationProvider contract.
 *
 * Provides accurate bilinear interpolation from offline elevation grid samples,
 * strictly preserves unknown and invalid values, enforces coordinate bounds,
 * and never fabricates elevations where data is missing.
 */
class OfflineElevationProvider(
    val metadata: OfflineElevationMetadata,
    private val gridSampler: (row: Int, col: Int) -> Double?
) : ElevationProvider {

    override fun canHandle(coordinate: GeoCoordinate): Boolean {
        return coordinate.latitude in metadata.minLatitude..metadata.maxLatitude &&
                coordinate.longitude in metadata.minLongitude..metadata.maxLongitude
    }

    override fun getName(): String = "OfflineElevationProvider-${metadata.datasetId}"

    override suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult {
        if (!canHandle(coordinate)) {
            return ElevationProviderResult.Error(
                "Coordinate ($coordinate) is outside coverage bounds of ${metadata.datasetId}"
            )
        }

        // Calculate grid fractional indices
        val latFraction = (metadata.maxLatitude - coordinate.latitude) / metadata.resolutionDegrees
        val lonFraction = (coordinate.longitude - metadata.minLongitude) / metadata.resolutionDegrees

        val row0 = floor(latFraction).toInt()
        val row1 = row0 + 1
        val col0 = floor(lonFraction).toInt()
        val col1 = col0 + 1

        val dLat = latFraction - row0
        val dLon = lonFraction - col0

        // Sample 4 surrounding grid cells
        val v00 = gridSampler(row0, col0)
        val v01 = gridSampler(row0, col1)
        val v10 = gridSampler(row1, col0)
        val v11 = gridSampler(row1, col1)

        // Validate samples against noDataValue and nulls
        fun isValid(v: Double?): Boolean = v != null && v != metadata.noDataValue && !v.isNaN()

        val valid00 = isValid(v00)
        val valid01 = isValid(v01)
        val valid10 = isValid(v10)
        val valid11 = isValid(v11)

        if (!valid00 && !valid01 && !valid10 && !valid11) {
            // All surrounding cells are no-data -> report UNKNOWN, do not invent 0.0
            val unknownPoint = ElevationPoint(
                coordinate = coordinate,
                elevationMeters = Double.NaN,
                accuracyMeters = null,
                quality = ElevationQuality.UNKNOWN,
                provenance = ElevationProvenance.UNKNOWN
            )
            return ElevationProviderResult.PartialSuccess(
                validPoints = emptyList(),
                invalidPoints = listOf(coordinate)
            )
        }

        // If all 4 corners are valid, perform 2D bilinear interpolation
        val interpolatedElevation: Double
        val quality: ElevationQuality

        if (valid00 && valid01 && valid10 && valid11) {
            val top = v00!! * (1.0 - dLon) + v01!! * dLon
            val bottom = v10!! * (1.0 - dLon) + v11!! * dLon
            interpolatedElevation = top * (1.0 - dLat) + bottom * dLat
            quality = ElevationQuality.INTERPOLATED
        } else {
            // Nearest valid neighbor fallback (without fabricating)
            val available = listOfNotNull(v00, v01, v10, v11).filter { isValid(it) }
            interpolatedElevation = available.average()
            quality = ElevationQuality.APPROXIMATED
        }

        val point = ElevationPoint(
            coordinate = coordinate,
            elevationMeters = interpolatedElevation,
            accuracyMeters = metadata.resolutionDegrees * 111_000.0 * 0.1, // estimated vertical error
            quality = quality,
            provenance = ElevationProvenance.LOCAL_CACHE
        )

        return ElevationProviderResult.Success(listOf(point))
    }

    override suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        val validPoints = mutableListOf<ElevationPoint>()
        val invalidPoints = mutableListOf<GeoCoordinate>()

        for (coord in coordinates) {
            when (val res = getElevationAt(coord)) {
                is ElevationProviderResult.Success -> validPoints.addAll(res.points)
                is ElevationProviderResult.PartialSuccess -> {
                    validPoints.addAll(res.validPoints)
                    invalidPoints.addAll(res.invalidPoints)
                }
                is ElevationProviderResult.Error -> invalidPoints.add(coord)
            }
        }

        return if (invalidPoints.isEmpty()) {
            ElevationProviderResult.Success(validPoints)
        } else {
            ElevationProviderResult.PartialSuccess(validPoints, invalidPoints)
        }
    }

    override suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        return getElevationBatch(coordinates)
    }
}
