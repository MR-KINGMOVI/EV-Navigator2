package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Composite elevation provider that tries a primary provider (e.g. online DEM service)
 * and falls back to a secondary provider (e.g. offline raster grid) if the primary fails
 * or provides incomplete data.
 *
 * Adheres strictly to the core rule: Unknown elevations remain UNKNOWN (Double.NaN),
 * never fabricating a synthetic 1200m plateau.
 */
class CompositeElevationProvider(
    private val primaryProvider: ElevationProvider,
    private val fallbackProvider: ElevationProvider? = null
) : ElevationProvider {

    override fun canHandle(coordinate: GeoCoordinate): Boolean {
        return primaryProvider.canHandle(coordinate) || fallbackProvider?.canHandle(coordinate) == true
    }

    override fun getName(): String {
        val fallbackName = fallbackProvider?.getName() ?: "None"
        return "CompositeElevationProvider(primary=${primaryProvider.getName()}, fallback=$fallbackName)"
    }

    override suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult {
        val primaryResult = primaryProvider.getElevationAt(coordinate)
        if (primaryResult is ElevationProviderResult.Success && primaryResult.points.isNotEmpty()) {
            return primaryResult
        }

        if (fallbackProvider != null && fallbackProvider.canHandle(coordinate)) {
            val fallbackResult = fallbackProvider.getElevationAt(coordinate)
            if (fallbackResult is ElevationProviderResult.Success && fallbackResult.points.isNotEmpty()) {
                return fallbackResult
            }
        }

        return primaryResult
    }

    override suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        return getElevationBatch(coordinates)
    }

    override suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        if (coordinates.isEmpty()) {
            return ElevationProviderResult.Success(emptyList())
        }

        val primaryResult = primaryProvider.getElevationBatch(coordinates)
        if (primaryResult is ElevationProviderResult.Success) {
            return primaryResult
        }

        if (fallbackProvider == null) {
            return primaryResult
        }

        val validPointsMap = mutableMapOf<GeoCoordinate, ElevationPoint>()
        val missingCoordinates = mutableListOf<GeoCoordinate>()

        when (primaryResult) {
            is ElevationProviderResult.PartialSuccess -> {
                primaryResult.validPoints.forEach { validPointsMap[it.coordinate] = it }
                missingCoordinates.addAll(primaryResult.invalidPoints)
            }
            is ElevationProviderResult.Error -> {
                missingCoordinates.addAll(coordinates)
            }
            is ElevationProviderResult.Success -> {
                return primaryResult
            }
        }

        // Query fallback provider for missing coordinates
        val fallbackResult = fallbackProvider.getElevationBatch(missingCoordinates)
        val stillInvalidCoordinates = mutableListOf<GeoCoordinate>()

        when (fallbackResult) {
            is ElevationProviderResult.Success -> {
                fallbackResult.points.forEach { validPointsMap[it.coordinate] = it }
            }
            is ElevationProviderResult.PartialSuccess -> {
                fallbackResult.validPoints.forEach { validPointsMap[it.coordinate] = it }
                stillInvalidCoordinates.addAll(fallbackResult.invalidPoints)
            }
            is ElevationProviderResult.Error -> {
                stillInvalidCoordinates.addAll(missingCoordinates)
            }
        }

        val orderedPoints = coordinates.mapNotNull { validPointsMap[it] }

        return when {
            stillInvalidCoordinates.isEmpty() -> ElevationProviderResult.Success(orderedPoints)
            orderedPoints.isEmpty() -> ElevationProviderResult.Error("Both primary and fallback elevation providers failed.")
            else -> ElevationProviderResult.PartialSuccess(
                validPoints = orderedPoints,
                invalidPoints = stillInvalidCoordinates
            )
        }
    }
}
