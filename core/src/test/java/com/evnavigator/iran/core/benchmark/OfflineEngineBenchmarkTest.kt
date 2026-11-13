package com.evnavigator.iran.core.benchmark

import com.evnavigator.iran.core.cache.BoundedMemoryLruCache
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.elevation.OfflineElevationMetadata
import com.evnavigator.iran.core.elevation.OfflineElevationProvider
import com.evnavigator.iran.core.util.PersianTextNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Step AM: Offline Engine Benchmarks.
 *
 * Microbenchmarks measuring throughput on synthetic workloads:
 * - Persian text normalization
 * - 2D bilinear elevation interpolation
 * - LRU cache eviction and lookup
 */
class OfflineEngineBenchmarkTest {

    @Test
    fun `benchmark Persian text normalizer throughput`() {
        val testStrings = listOf(
            "ايستگاه شارژ سريع آزادي ۱۲۳",
            "بزرگراه شهيد همت غرب به شرق",
            "خودروی برقی بي واي دي يوان پلاس",
            "ميدان انقلاب اسلامی تهران",
            "پمپ بنزين و ايستگاه شارژ مپنا"
        )

        val iterations = 5000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val s = testStrings[i % testStrings.size]
            val norm = PersianTextNormalizer.normalize(s)
            assertNotNull(norm)
        }

        val elapsedNs = System.nanoTime() - startTime
        val opsPerSec = (iterations.toDouble() / elapsedNs) * 1_000_000_000.0

        println("[BENCHMARK] Persian normalizer throughput: %.0f ops/sec".format(opsPerSec))
        assertTrue("Throughput must be at least 10,000 ops/sec", opsPerSec > 10_000.0)
    }

    @Test
    fun `benchmark elevation bilinear interpolation throughput`() = runBlocking {
        val metadata = OfflineElevationMetadata(
            datasetId = "bench_dem",
            datasetVersion = "1.0",
            source = "SYNTHETIC_BENCH",
            minLatitude = 35.0,
            maxLatitude = 36.0,
            minLongitude = 51.0,
            maxLongitude = 52.0,
            resolutionDegrees = 0.001
        )

        val provider = OfflineElevationProvider(metadata) { r, c -> 1000.0 + (r * 0.5) + (c * 0.2) }

        val iterations = 2000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val lat = 35.1 + ((i % 800) * 0.001)
            val lon = 51.1 + ((i % 800) * 0.001)
            val res = provider.getElevationAt(GeoCoordinate(lat, lon))
            assertTrue(res is com.evnavigator.iran.core.elevation.ElevationProviderResult.Success)
        }

        val elapsedNs = System.nanoTime() - startTime
        val opsPerSec = (iterations.toDouble() / elapsedNs) * 1_000_000_000.0

        println("[BENCHMARK] Bilinear elevation interpolation: %.0f ops/sec".format(opsPerSec))
        assertTrue("Throughput must exceed 5,000 ops/sec", opsPerSec > 5_000.0)
    }

    @Test
    fun `benchmark bounded LRU cache operations`() {
        val cache = BoundedMemoryLruCache<Int, String>(maxCapacity = 1000)
        val iterations = 10_000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            cache.put(i % 1500, "item_$i")
            cache.get((i * 3) % 1500)
        }

        val elapsedNs = System.nanoTime() - startTime
        val opsPerSec = (iterations.toDouble() / elapsedNs) * 1_000_000_000.0

        println("[BENCHMARK] Bounded LRU cache: %.0f ops/sec".format(opsPerSec))
        assertTrue(cache.size() <= 1000)
        assertTrue(opsPerSec > 10_000.0)
    }
}
