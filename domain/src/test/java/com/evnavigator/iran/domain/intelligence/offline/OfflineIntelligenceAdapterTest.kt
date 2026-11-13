package com.evnavigator.iran.domain.intelligence.offline

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.IntelligenceCoverageStatus
import org.junit.Assert.*
import org.junit.Test

class OfflineIntelligenceAdapterTest {

    private val adapter = OfflineIntelligenceAdapter(maxWeatherCacheValidityMs = 4 * 3600 * 1000L)

    @Test
    fun testOnlineFreshWeather() {
        val now = System.currentTimeMillis()
        val cache = CachedEnvironmentSnapshot(temperatureCelsius = 24.0, windSpeedMps = 4.0, timestampEpochMs = now)

        val (temp, wind) = adapter.resolveEnvironmentalInputs(isOnline = true, cachedWeather = cache, currentEpochMs = now)

        assertEquals(InputOrigin.MEASURED_TELEMETRY, temp.origin)
        assertEquals(24.0, temp.value!!, 0.01)
        assertFalse(temp.isStale)
    }

    @Test
    fun testOfflineRecentCache() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (3600 * 1000L)
        val cache = CachedEnvironmentSnapshot(temperatureCelsius = 18.0, windSpeedMps = 2.5, timestampEpochMs = oneHourAgo)

        val (temp, wind) = adapter.resolveEnvironmentalInputs(isOnline = false, cachedWeather = cache, currentEpochMs = now)

        assertEquals(InputOrigin.ESTIMATED_MODEL, temp.origin)
        assertEquals(18.0, temp.value!!, 0.01)
        assertFalse(temp.isStale)
        assertTrue(temp.assumptionNote!!.contains("recent offline cached"))
    }

    @Test
    fun testOfflineStaleCacheFlagged() {
        val now = System.currentTimeMillis()
        val sixHoursAgo = now - (6 * 3600 * 1000L)
        val cache = CachedEnvironmentSnapshot(temperatureCelsius = 15.0, windSpeedMps = 3.0, timestampEpochMs = sixHoursAgo)

        val (temp, wind) = adapter.resolveEnvironmentalInputs(isOnline = false, cachedWeather = cache, currentEpochMs = now)

        assertTrue(temp.isStale)
        assertTrue(temp.confidence <= 0.40)
        assertTrue(temp.assumptionNote!!.contains("stale"))
    }

    @Test
    fun testOfflineNoCacheAssumedDefault() {
        val now = System.currentTimeMillis()
        val (temp, wind) = adapter.resolveEnvironmentalInputs(isOnline = false, cachedWeather = null, currentEpochMs = now)

        assertEquals(InputOrigin.ASSUMED_DEFAULT, temp.origin)
        assertEquals(20.0, temp.value!!, 0.01)
        assertTrue(temp.assumptionNote!!.contains("zero cached data"))
    }

    @Test
    fun testCoverageStatusResolution() {
        val full = OfflineDatasetAvailability(hasMapRoutingDataset = true, hasElevationDataset = true, hasChargingRegistryDataset = true)
        assertEquals(IntelligenceCoverageStatus.FULL_COVERAGE, adapter.determineCoverageStatus(full))

        val missingElev = full.copy(hasElevationDataset = false)
        assertEquals(IntelligenceCoverageStatus.ELEVATION_MISSING, adapter.determineCoverageStatus(missingElev))

        val missingMap = full.copy(hasMapRoutingDataset = false)
        assertEquals(IntelligenceCoverageStatus.UNAVAILABLE, adapter.determineCoverageStatus(missingMap))
    }
}
