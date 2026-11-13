package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnlineDemElevationProviderTest {

    @Test
    fun testOpenMeteoResponseParsing() = runTest {
        val mockJson = """{"elevation": [1180.5, 1205.0]}"""
        val provider = OnlineDemElevationProvider(
            httpTransport = { mockJson }
        )

        val coords = listOf(
            GeoCoordinate(35.6997, 51.3380),
            GeoCoordinate(35.7000, 51.3400)
        )

        val result = provider.getElevationBatch(coords)
        assertTrue("Expected Success result", result is ElevationProviderResult.Success)
        val success = result as ElevationProviderResult.Success
        assertEquals(2, success.points.size)
        assertEquals(1180.5, success.points[0].elevationMeters, 0.01)
        assertEquals(1205.0, success.points[1].elevationMeters, 0.01)
        assertEquals(ElevationProvenance.CUSTOM_API, success.points[0].provenance)
        assertEquals(ElevationQuality.KNOWN, success.points[0].quality)
    }

    @Test
    fun testOpenTopoDataResponseParsing() = runTest {
        val mockJson = """{"results": [{"elevation": 950.0}, {"elevation": 965.5}]}"""
        val provider = OnlineDemElevationProvider(
            httpTransport = { mockJson }
        )

        val coords = listOf(
            GeoCoordinate(34.6401, 50.8764),
            GeoCoordinate(34.6500, 50.8800)
        )

        val result = provider.getElevationBatch(coords)
        assertTrue(result is ElevationProviderResult.Success)
        val points = (result as ElevationProviderResult.Success).points
        assertEquals(2, points.size)
        assertEquals(950.0, points[0].elevationMeters, 0.01)
        assertEquals(965.5, points[1].elevationMeters, 0.01)
    }

    @Test
    fun testCacheHitPreventsRepeatedNetworkCalls() = runTest {
        var callCount = 0
        val provider = OnlineDemElevationProvider(
            httpTransport = {
                callCount++
                """{"elevation": [1300.0]}"""
            }
        )

        val coord = GeoCoordinate(35.6997, 51.3380)

        // First call
        val r1 = provider.getElevationAt(coord)
        assertTrue(r1 is ElevationProviderResult.Success)
        assertEquals(1, callCount)

        // Second call with same coordinate should hit LRU cache
        val r2 = provider.getElevationAt(coord)
        assertTrue(r2 is ElevationProviderResult.Success)
        assertEquals(1, callCount) // callCount remains 1 due to cache
    }

    @Test
    fun testRejectsCoordinatesOutsideIran() = runTest {
        val provider = OnlineDemElevationProvider(
            httpTransport = { """{"elevation": [100.0]}""" }
        )

        // Paris coordinate
        val paris = GeoCoordinate(48.8566, 2.3522)
        assertFalse("Paris is outside Iran boundary", provider.canHandle(paris))

        val result = provider.getElevationAt(paris)
        assertTrue("Query for coordinate outside Iran must fail", result is ElevationProviderResult.Error)
    }

    @Test
    fun testCompositeElevationProviderFallsBackToOfflineWhenOnlineFails() = runTest {
        // Failing online provider
        val onlineFailing = OnlineDemElevationProvider(
            httpTransport = { null } // Simulates network failure
        )

        // Working offline provider
        val offlineMetadata = OfflineElevationMetadata(
            datasetId = "dem_tehran_qom",
            datasetVersion = "1.0",
            source = "SRTM",
            minLatitude = 34.0,
            maxLatitude = 36.0,
            minLongitude = 50.0,
            maxLongitude = 52.0
        )
        val offlineProvider = OfflineElevationProvider(offlineMetadata) { _, _ -> 1180.0 }

        val composite = CompositeElevationProvider(
            primaryProvider = onlineFailing,
            fallbackProvider = offlineProvider
        )

        val coord = GeoCoordinate(35.6997, 51.3380)
        val result = composite.getElevationAt(coord)

        assertTrue("Composite must fall back to offline DEM", result is ElevationProviderResult.Success)
        val point = (result as ElevationProviderResult.Success).points.first()
        assertEquals(1180.0, point.elevationMeters, 0.01)
    }

    @Test
    fun testCompositeElevationProviderPropagatesErrorWhenBothFailWithoutFabricatingData() = runTest {
        val onlineFailing = OnlineDemElevationProvider(httpTransport = { null })
        val offlineFailing = object : ElevationProvider {
            override suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult =
                ElevationProviderResult.Error("Offline DEM not installed")
            override suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult =
                ElevationProviderResult.Error("Offline DEM not installed")
            override suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult =
                ElevationProviderResult.Error("Offline DEM not installed")
        }

        val composite = CompositeElevationProvider(
            primaryProvider = onlineFailing,
            fallbackProvider = offlineFailing
        )

        val coord = GeoCoordinate(35.6997, 51.3380)
        val result = composite.getElevationAt(coord)

        assertTrue("Must report error when both fail, never returning 1200.0", result is ElevationProviderResult.Error)
    }
}
