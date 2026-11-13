package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
// import models

/**
 * Test implementation of ElevationProvider for unit and integration testing.
 */
class TestElevationProvider : ElevationProvider {

    override suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult {
        val elevation = when {
            coordinate.latitude in 35.0..36.0 && coordinate.longitude in 51.0..52.0 -> 1200.0 // Tehran area
            coordinate.latitude in 37.0..38.0 && coordinate.longitude in 49.0..50.0 -> 1500.0 // Isfahan area
            else -> 100.0
        }

        val point = ElevationPoint.known(
            coordinate = coordinate,
            elevation = elevation,
            accuracy = 5.0,
            provenance = ElevationProvenance.SYNTHETIC
        )

        return ElevationProviderResult.Success(listOf(point))
    }

    override suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult {
        val points = coordinates.map { coordinate ->
            val elevation = when {
                coordinate.latitude in 35.0..36.0 && coordinate.longitude in 51.0..52.0 -> 1200.0 // Tehran area
                coordinate.latitude in 37.0..38.0 && coordinate.longitude in 49.0..50.0 -> 1500.0 // Isfahan area
                else -> 100.0
            }

            ElevationPoint.known(
                coordinate = coordinate,
                elevation = elevation,
                accuracy = 5.0,
                provenance = ElevationProvenance.SYNTHETIC
            )
        }

        return ElevationProviderResult.Success(points)
    }

    override suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult = getElevationBatch(coordinates)
}
