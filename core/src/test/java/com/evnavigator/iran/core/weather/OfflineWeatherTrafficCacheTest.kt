package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.WeatherSnapshot
import com.evnavigator.iran.domain.model.weather.TrafficData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Unit tests for OfflineWeatherTrafficCache.
 * Verifies freshness degradation, stale transitions, bounded size, and absence of fabricated defaults.
 */
class OfflineWeatherTrafficCacheTest {

    private lateinit var cache: OfflineWeatherTrafficCache

    @Before
    fun setUp() {
        cache = OfflineWeatherTrafficCache(
            maxWeatherEntries = 5,
            maxTrafficEntries = 5,
            weatherFreshTtlMillis = 1000L,
            weatherStaleTtlMillis = 3000L,
            trafficFreshTtlMillis = 500L,
            trafficStaleTtlMillis = 1500L
        )
    }

    private fun createWeather(coord: GeoCoordinate, time: Long) = WeatherSnapshot(
        coordinate = coord,
        timestamp = time,
        temperatureCelsius = 25.0,
        humidityPercentage = 30,
        windSpeedKmh = 12.0,
        windDirectionDegrees = 180,
        precipitationMm = 0.0,
        visibilityMeters = 10000.0,
        pressureHpa = 1013.0
    )

    private fun createTraffic(coord: GeoCoordinate, time: Long) = TrafficData(
        coordinate = com.evnavigator.iran.domain.model.GeoCoordinate(coord.latitude, coord.longitude),
        timestamp = Date(time),
        currentSpeedKmh = 45.0,
        speedLimitKmh = 80.0,
        congestionLevelPercent = 45.0
    )

    @Test
    fun `test fresh weather lookup returns Fresh result`() {
        val coord = GeoCoordinate(35.6892, 51.3890)
        val now = 10000L
        val weather = createWeather(coord, now)

        cache.putWeather(coord, weather, currentTimeMillis = now)
        val result = cache.getWeather(coord, currentTimeMillis = now + 500L) // +0.5s (within 1s fresh TTL)

        assertTrue(result is CacheLookupResult.Fresh)
        val fresh = result as CacheLookupResult.Fresh
        assertEquals(25.0, fresh.data.temperatureCelsius, 0.01)
        assertEquals(500L, fresh.ageMillis)
    }

    @Test
    fun `test stale weather lookup returns Stale result with warning`() {
        val coord = GeoCoordinate(35.6892, 51.3890)
        val now = 10000L
        val weather = createWeather(coord, now)

        cache.putWeather(coord, weather, currentTimeMillis = now)
        val result = cache.getWeather(coord, currentTimeMillis = now + 2000L) // +2s (between 1s and 3s stale TTL)

        assertTrue(result is CacheLookupResult.Stale)
        val stale = result as CacheLookupResult.Stale
        assertEquals(2000L, stale.ageMillis)
        assertTrue(stale.warning.contains("stale"))
    }

    @Test
    fun `test expired weather returns UnavailableOffline without fabricating default weather`() {
        val coord = GeoCoordinate(35.6892, 51.3890)
        val now = 10000L
        val weather = createWeather(coord, now)

        cache.putWeather(coord, weather, currentTimeMillis = now)
        val result = cache.getWeather(coord, currentTimeMillis = now + 5000L) // +5s (exceeds 3s stale TTL)

        assertEquals(CacheLookupResult.UnavailableOffline, result)
    }

    @Test
    fun `test missing traffic returns UnavailableOffline and never assumes clear roads`() {
        val result = cache.getTraffic("unknown_segment_id", currentTimeMillis = 10000L)
        assertEquals(CacheLookupResult.UnavailableOffline, result)
    }

    @Test
    fun `test cache eviction bounds memory when capacity is reached`() {
        val baseTime = 1000L
        for (i in 1..6) {
            val coord = GeoCoordinate(35.0 + (i * 0.1), 51.0 + (i * 0.1))
            cache.putWeather(coord, createWeather(coord, baseTime + i), currentTimeMillis = baseTime + i)
        }

        // Oldest item (i=1) should be evicted because maxWeatherEntries = 5
        val oldestCoord = GeoCoordinate(35.1, 51.1)
        val result = cache.getWeather(oldestCoord, currentTimeMillis = baseTime + 7)
        assertEquals(CacheLookupResult.UnavailableOffline, result)
    }
}
