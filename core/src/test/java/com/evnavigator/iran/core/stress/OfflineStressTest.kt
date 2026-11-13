package com.evnavigator.iran.core.stress

import com.evnavigator.iran.core.cache.BoundedMemoryLruCache
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.WeatherSnapshot
import com.evnavigator.iran.core.weather.CacheLookupResult
import com.evnavigator.iran.core.weather.OfflineWeatherTrafficCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Step AN: Stress Testing for Offline Engines.
 *
 * Verifies stability, thread safety, and lack of deadlocks or memory leaks
 * under concurrent multi-threaded read/write stress.
 */
class OfflineStressTest {

    @Test
    fun `test concurrent multi-threaded cache reads and writes under stress`() = runTest {
        val maxEntries = 200
        val cache = OfflineWeatherTrafficCache(maxWeatherEntries = maxEntries, maxTrafficEntries = 200)
        val concurrency = 40
        val operationsPerJob = 200

        val deferredList = (0 until concurrency).map { workerId ->
            async(Dispatchers.Default) {
                for (i in 0 until operationsPerJob) {
                    val lat = 35.0 + ((workerId + i) % 50) * 0.01
                    val lon = 51.0 + ((workerId + i) % 50) * 0.01
                    val coord = GeoCoordinate(lat, lon)

                    if (i % 2 == 0) {
                        cache.putWeather(
                            coord,
                            WeatherSnapshot(
                                coordinate = coord,
                                timestamp = System.currentTimeMillis(),
                                temperatureCelsius = 20.0 + (workerId % 10),
                                humidityPercentage = 40,
                                windSpeedKmh = 10.0,
                                windDirectionDegrees = 90,
                                precipitationMm = 0.0,
                                visibilityMeters = 8000.0,
                                pressureHpa = 1010.0
                            )
                        )
                    } else {
                        cache.getWeather(coord)
                    }
                }
            }
        }

        deferredList.awaitAll()

        // Real verification: verify data consistency and retrieval under stress
        // Insert a known checkpoint item and read it back
        val testCoord = GeoCoordinate(35.5, 51.5)
        cache.putWeather(
            testCoord,
            WeatherSnapshot(
                coordinate = testCoord,
                timestamp = System.currentTimeMillis(),
                temperatureCelsius = 25.0,
                humidityPercentage = 50,
                windSpeedKmh = 15.0,
                windDirectionDegrees = 180,
                precipitationMm = 0.0,
                visibilityMeters = 10000.0,
                pressureHpa = 1013.25
            )
        )

        val retrieved = cache.getWeather(testCoord)
        assertTrue("Newly put entry must be retrieved as Fresh", retrieved is CacheLookupResult.Fresh)
        val snapshot = (retrieved as CacheLookupResult.Fresh).data
        assertEquals(25.0, snapshot.temperatureCelsius, 0.001)
        assertEquals(50, snapshot.humidityPercentage)
        assertEquals(15.0, snapshot.windSpeedKmh, 0.001)
        assertEquals(1013.25, snapshot.pressureHpa, 0.001)

        // Verify that expired lookups return UnavailableOffline
        val farFuture = System.currentTimeMillis() + 4 * 3600 * 1000L
        val expired = cache.getWeather(testCoord, currentTimeMillis = farFuture)
        assertEquals(CacheLookupResult.UnavailableOffline, expired)
    }

    @Test
    fun `test LRU cache concurrent thrashing stress`() = runTest {
        val maxCapacity = 100
        val cache = BoundedMemoryLruCache<Int, String>(maxCapacity = maxCapacity)
        val concurrency = 20

        val deferredList = (0 until concurrency).map { workerId ->
            async(Dispatchers.Default) {
                for (i in 0 until 500) {
                    val key = (workerId * 50) + (i % 80)
                    cache.put(key, "val_${workerId}_$i")
                    cache.get(key)
                }
            }
        }

        deferredList.awaitAll()

        // Bounded capacity assertion: must strictly never exceed maxCapacity
        val finalSize = cache.size()
        assertTrue("Cache size must be positive after stress insertions", finalSize > 0)
        assertTrue("Cache size ($finalSize) must not exceed max capacity ($maxCapacity)", finalSize <= maxCapacity)

        // Verify recent read/write works accurately
        cache.put(9999, "sentinel_value")
        assertEquals("sentinel_value", cache.get(9999))
    }
}
