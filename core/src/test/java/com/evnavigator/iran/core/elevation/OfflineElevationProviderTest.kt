package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.elevation.ElevationQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflineElevationProvider.
 * Verifies bilinear interpolation, boundary clamping, missing data handling, and batch operations.
 */
class OfflineElevationProviderTest {

    private lateinit var metadata: OfflineElevationMetadata

    @Before
    fun setUp() {
        metadata = OfflineElevationMetadata(
            datasetId = "dem_tehran_test",
            datasetVersion = "1.0",
            source = "SRTM3",
            verticalDatum = "EGM96",
            minLatitude = 35.0,
            maxLatitude = 36.0,
            minLongitude = 51.0,
            maxLongitude = 52.0,
            resolutionDegrees = 0.1, // 10 rows x 10 cols grid
            noDataValue = -32768.0
        )
    }

    @Test
    fun `test exact grid point returns sampled elevation`() = runTest {
        val provider = OfflineElevationProvider(metadata) { row, col ->
            1000.0 + (row * 10) + col
        }

        val coord = GeoCoordinate(36.0, 51.0) // row 0, col 0
        val result = provider.getElevationAt(coord)

        assertTrue(result is ElevationProviderResult.Success)
        val point = (result as ElevationProviderResult.Success).points.first()
        assertEquals(1000.0, point.elevationMeters, 0.001)
        assertEquals(ElevationQuality.INTERPOLATED, point.quality)
    }

    @Test
    fun `test bilinear interpolation inside grid cell`() = runTest {
        // Flat ramp from 1000m to 1100m
        val provider = OfflineElevationProvider(metadata) { row, col ->
            1000.0 + col * 100.0
        }

        // Halfway between longitude 51.0 (col 0) and 51.1 (col 1)
        val coord = GeoCoordinate(36.0, 51.05)
        val result = provider.getElevationAt(coord)

        assertTrue(result is ElevationProviderResult.Success)
        val point = (result as ElevationProviderResult.Success).points.first()
        assertEquals(1050.0, point.elevationMeters, 0.1)
    }

    @Test
    fun `test out of coverage returns error`() = runTest {
        val provider = OfflineElevationProvider(metadata) { _, _ -> 1200.0 }
        val outOfBoundsCoord = GeoCoordinate(37.5, 51.5) // Above maxLatitude 36.0

        val result = provider.getElevationAt(outOfBoundsCoord)

        assertTrue(result is ElevationProviderResult.Error)
        assertFalse(provider.canHandle(outOfBoundsCoord))
    }

    @Test
    fun `test missing no-data grid cell does not fabricate zero`() = runTest {
        // All cells return noDataValue
        val provider = OfflineElevationProvider(metadata) { _, _ -> -32768.0 }
        val coord = GeoCoordinate(35.5, 51.5)

        val result = provider.getElevationAt(coord)

        // Must report PartialSuccess with coord in invalidPoints or Error, never 0.0!
        assertTrue(result is ElevationProviderResult.PartialSuccess)
        val partial = result as ElevationProviderResult.PartialSuccess
        assertEquals(1, partial.invalidPoints.size)
        assertEquals(coord, partial.invalidPoints.first())
    }

    @Test
    fun `test batch query handles mix of valid and invalid points`() = runTest {
        val provider = OfflineElevationProvider(metadata) { _, _ -> 1400.0 }

        val validCoord = GeoCoordinate(35.5, 51.5)
        val invalidCoord = GeoCoordinate(38.0, 51.5) // out of bounds

        val result = provider.getElevationBatch(listOf(validCoord, invalidCoord))

        assertTrue(result is ElevationProviderResult.PartialSuccess)
        val partial = result as ElevationProviderResult.PartialSuccess
        assertEquals(1, partial.validPoints.size)
        assertEquals(1, partial.invalidPoints.size)
        assertEquals(1400.0, partial.validPoints.first().elevationMeters, 0.01)
    }
}
