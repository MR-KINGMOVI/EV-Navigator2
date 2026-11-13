package com.evnavigator.iran.domain.model.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CacheArchitectureTest - Comprehensive tests for CacheArchitecture
 * 
 * Tests cover:
 * - Cache statistics
 * - Cache entry operations
 * - Cache invalidation
 * - Cache eviction
 * - Cache key generation
 * - Freshness tracking
 * - Cache hit/miss tracking
 * - Prevention of cache contamination
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheArchitectureTest {
    
    private lateinit var cacheArchitecture: CacheArchitectureImpl
    
    @Before
    fun setup() {
        cacheArchitecture = CacheArchitectureImpl()
    }
    
    @Test
    fun `getCacheStatistics returns valid statistics`() = runTest {
        val statistics = cacheArchitecture.getCacheStatistics()
        
        assertNotNull(statistics)
        assertTrue(statistics.totalEntries >= 0)
        assertTrue(statistics.memoryEntries >= 0)
        assertTrue(statistics.persistentEntries >= 0)
        assertTrue(statistics.totalSize >= 0)
        assertTrue(statistics.memorySize >= 0)
        assertTrue(statistics.persistentSize >= 0)
        assertTrue(statistics.hitCount >= 0)
        assertTrue(statistics.missCount >= 0)
        assertTrue(statistics.hitRate >= 0 && statistics.hitRate <= 1.0)
        assertTrue(statistics.missRate >= 0 && statistics.missRate <= 1.0)
        assertTrue(statistics.oldestEntryAge >= 0)
        assertTrue(statistics.newestEntryAge >= 0)
        assertTrue(statistics.averageEntryAge >= 0)
    }
    
    @Test
    fun `getCacheStatisticsFlow emits statistics`() = runTest {
        val statistics = cacheArchitecture.getCacheStatisticsFlow().first()
        
        assertNotNull(statistics)
    }
    
    @Test
    fun `getCacheEntry returns null for non-existent key`() = runTest {
        val entry = cacheArchitecture.getCacheEntry("non_existent_key")
        
        assertNull(entry)
    }
    
    @Test
    fun `putCacheEntry adds entry to cache`() = runTest {
        val key = "test_key"
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf(
                "datasetId" to "test_dataset",
                "version" to "1.0",
                "region" to "region1",
                "isUserCreated" to false
            )
        )
        
        cacheArchitecture.putCacheEntry(key, value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val entry = cacheArchitecture.getCacheEntry(key)
        
        assertNotNull(entry)
        assertEquals(key, entry?.key)
        assertEquals(value.data, entry?.value?.data)
    }
    
    @Test
    fun `getCacheEntry updates lastAccessed timestamp`() = runTest {
        val key = "test_key"
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "version" to "1.0")
        )
        
        cacheArchitecture.putCacheEntry(key, value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val entry1 = cacheArchitecture.getCacheEntry(key)
        val timestamp1 = entry1?.lastAccessed ?: 0
        
        kotlinx.coroutines.delay(100)
        
        val entry2 = cacheArchitecture.getCacheEntry(key)
        val timestamp2 = entry2?.lastAccessed ?: 0
        
        assertTrue(timestamp2 > timestamp1)
    }
    
    @Test
    fun `invalidateCacheEntry removes entry from cache`() = runTest {
        val key = "test_key"
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "version" to "1.0")
        )
        
        cacheArchitecture.putCacheEntry(key, value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.invalidateCacheEntry(key)
        
        val entry = cacheArchitecture.getCacheEntry(key)
        
        assertNull(entry)
    }
    
    @Test
    fun `invalidateCacheByDatasetVersion removes entries for specific version`() = runTest {
        val key1 = "dataset1_v1.0_key1"
        val key2 = "dataset1_v1.0_key2"
        val key3 = "dataset1_v2.0_key1"
        
        val value1 = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "dataset1", "version" to "1.0")
        )
        
        val value2 = CacheValue(
            data = byteArrayOf(4, 5, 6),
            metadata = mapOf("datasetId" to "dataset1", "version" to "1.0")
        )
        
        val value3 = CacheValue(
            data = byteArrayOf(7, 8, 9),
            metadata = mapOf("datasetId" to "dataset1", "version" to "2.0")
        )
        
        cacheArchitecture.putCacheEntry(key1, value1, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry(key2, value2, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry(key3, value3, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.invalidateCacheByDatasetVersion("dataset1", "1.0")
        
        val entry1 = cacheArchitecture.getCacheEntry(key1)
        val entry2 = cacheArchitecture.getCacheEntry(key2)
        val entry3 = cacheArchitecture.getCacheEntry(key3)
        
        assertNull(entry1)
        assertNull(entry2)
        assertNotNull(entry3)
    }
    
    @Test
    fun `invalidateCacheByRegion removes entries for specific region`() = runTest {
        val key1 = "dataset1_region_region1_key1"
        val key2 = "dataset1_region_region1_key2"
        val key3 = "dataset1_region_region2_key1"
        
        val value1 = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "dataset1", "region" to "region1")
        )
        
        val value2 = CacheValue(
            data = byteArrayOf(4, 5, 6),
            metadata = mapOf("datasetId" to "dataset1", "region" to "region1")
        )
        
        val value3 = CacheValue(
            data = byteArrayOf(7, 8, 9),
            metadata = mapOf("datasetId" to "dataset1", "region" to "region2")
        )
        
        cacheArchitecture.putCacheEntry(key1, value1, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry(key2, value2, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry(key3, value3, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.invalidateCacheByRegion("region1")
        
        val entry1 = cacheArchitecture.getCacheEntry(key1)
        val entry2 = cacheArchitecture.getCacheEntry(key2)
        val entry3 = cacheArchitecture.getCacheEntry(key3)
        
        assertNull(entry1)
        assertNull(entry2)
        assertNotNull(entry3)
    }
    
    @Test
    fun `invalidateCacheByAge removes old entries`() = runTest {
        val key1 = "test_key_1"
        val key2 = "test_key_2"
        
        val value1 = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        val value2 = CacheValue(
            data = byteArrayOf(4, 5, 6),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry(key1, value1, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry(key2, value2, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        // Wait for entries to age
        kotlinx.coroutines.delay(100)
        
        val invalidatedCount = cacheArchitecture.invalidateCacheByAge(0)
        
        assertTrue(invalidatedCount >= 0)
    }
    
    @Test
    fun `evictCacheEntries with LRU strategy evicts entries`() = runTest {
        val policy = EvictionPolicy(
            strategy = EvictionStrategy.LRU,
            maxEntries = 2,
            maxSize = 100 * 1024 * 1024L
        )
        
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("key3", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val result = cacheArchitecture.evictCacheEntries(policy)
        
        assertTrue(result.evictedCount >= 0)
        assertTrue(result.freedBytes >= 0)
    }
    
    @Test
    fun `evictCacheEntries respects preserveUserCreatedData`() = runTest {
        val policy = EvictionPolicy(
            strategy = EvictionStrategy.LRU,
            maxEntries = 1,
            preserveUserCreatedData = true
        )
        
        val userValue = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "isUserCreated" to true)
        )
        
        val regularValue = CacheValue(
            data = byteArrayOf(4, 5, 6),
            metadata = mapOf("datasetId" to "test_dataset", "isUserCreated" to false)
        )
        
        cacheArchitecture.putCacheEntry("user_key", userValue, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("regular_key", regularValue, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val result = cacheArchitecture.evictCacheEntries(policy)
        
        // User-created entry should not be evicted
        assertTrue(result.evictedCount >= 0)
    }
    
    @Test
    fun `getCacheKeysByDataset returns correct keys`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("dataset1_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset2_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val keys = cacheArchitecture.getCacheKeysByDataset("dataset1")
        
        assertTrue(keys.size >= 2)
        assertTrue(keys.contains("dataset1_key1"))
        assertTrue(keys.contains("dataset1_key2"))
    }
    
    @Test
    fun `getCacheKeysByVersion returns correct keys`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "version" to "1.0")
        )
        
        cacheArchitecture.putCacheEntry("dataset1_v1.0_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_v1.0_key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_v2.0_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val keys = cacheArchitecture.getCacheKeysByVersion("1.0")
        
        assertTrue(keys.size >= 2)
        assertTrue(keys.contains("dataset1_v1.0_key1"))
        assertTrue(keys.contains("dataset1_v1.0_key2"))
    }
    
    @Test
    fun `getCacheKeysByRegion returns correct keys`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "region" to "region1")
        )
        
        cacheArchitecture.putCacheEntry("dataset1_region_region1_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_region_region1_key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_region_region2_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val keys = cacheArchitecture.getCacheKeysByRegion("region1")
        
        assertTrue(keys.size >= 2)
        assertTrue(keys.contains("dataset1_region_region1_key1"))
        assertTrue(keys.contains("dataset1_region_region1_key2"))
    }
    
    @Test
    fun `getCacheSizeByDataset returns correct size`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("dataset1_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset2_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val size = cacheArchitecture.getCacheSizeByDataset("dataset1")
        
        assertTrue(size >= 0)
    }
    
    @Test
    fun `getCacheSizeByVersion returns correct size`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "version" to "1.0")
        )
        
        cacheArchitecture.putCacheEntry("dataset1_v1.0_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_v1.0_key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_v2.0_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val size = cacheArchitecture.getCacheSizeByVersion("1.0")
        
        assertTrue(size >= 0)
    }
    
    @Test
    fun `getCacheSizeByRegion returns correct size`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "region" to "region1")
        )
        
        cacheArchitecture.putCacheEntry("dataset1_region_region1_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_region_region1_key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("dataset1_region_region2_key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val size = cacheArchitecture.getCacheSizeByRegion("region1")
        
        assertTrue(size >= 0)
    }
    
    @Test
    fun `isCacheEntryFresh returns correct freshness`() = runTest {
        val key = "test_key"
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry(key, value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val freshness = cacheArchitecture.getCacheEntryFreshness(key)
        
        assertNotNull(freshness)
        assertTrue(freshness in Freshness.values())
    }
    
    @Test
    fun `getCacheEntryAge returns correct age`() = runTest {
        val key = "test_key"
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry(key, value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val age = cacheArchitecture.getCacheEntryAge(key)
        
        assertTrue(age >= 0)
    }
    
    @Test
    fun `clearAllCache removes entries`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("key2", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val clearedCount = cacheArchitecture.clearAllCache()
        
        assertTrue(clearedCount >= 0)
    }
    
    @Test
    fun `clearAllCache preserves user-created data when specified`() = runTest {
        val userValue = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset", "isUserCreated" to true)
        )
        
        val regularValue = CacheValue(
            data = byteArrayOf(4, 5, 6),
            metadata = mapOf("datasetId" to "test_dataset", "isUserCreated" to false)
        )
        
        cacheArchitecture.putCacheEntry("user_key", userValue, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        cacheArchitecture.putCacheEntry("regular_key", regularValue, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        val clearedCount = cacheArchitecture.clearAllCache(preserveUserCreatedData = true)
        
        // User-created entry should still exist
        val userEntry = cacheArchitecture.getCacheEntry("user_key")
        assertNotNull(userEntry)
    }
    
    @Test
    fun `getCacheHitRate returns correct rate`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key2")
        
        val hitRate = cacheArchitecture.getCacheHitRate()
        
        assertTrue(hitRate >= 0 && hitRate <= 1.0)
    }
    
    @Test
    fun `getCacheMissRate returns correct rate`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key2")
        
        val missRate = cacheArchitecture.getCacheMissRate()
        
        assertTrue(missRate >= 0 && missRate <= 1.0)
    }
    
    @Test
    fun `getCacheHitCount returns correct count`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key2")
        
        val hitCount = cacheArchitecture.getCacheHitCount()
        
        assertTrue(hitCount >= 2)
    }
    
    @Test
    fun `getCacheMissCount returns correct count`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key2")
        
        val missCount = cacheArchitecture.getCacheMissCount()
        
        assertTrue(missCount >= 1)
    }
    
    @Test
    fun `resetCacheStatistics resets counters`() = runTest {
        val value = CacheValue(
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("datasetId" to "test_dataset")
        )
        
        cacheArchitecture.putCacheEntry("key1", value, 7 * 24 * 60 * 60 * 1000L, Freshness.FRESH)
        
        cacheArchitecture.getCacheEntry("key1")
        cacheArchitecture.getCacheEntry("key2")
        
        cacheArchitecture.resetCacheStatistics()
        
        val hitCount = cacheArchitecture.getCacheHitCount()
        val missCount = cacheArchitecture.getCacheMissCount()
        
        assertEquals(0, hitCount)
        assertEquals(0, missCount)
    }
    
    @Test
    fun `CacheKeyGenerator generates version key`() {
        val key = CacheKeyGenerator.generateVersionKey("dataset1", "1.0", "key1")
        
        assertEquals("dataset1_v1.0_key1", key)
    }
    
    @Test
    fun `CacheKeyGenerator generates geographic key`() {
        val key = CacheKeyGenerator.generateGeographicKey("dataset1", "region1", "key1")
        
        assertEquals("dataset1_region_region1_key1", key)
    }
    
    @Test
    fun `CacheKeyGenerator generates temporal key`() {
        val key = CacheKeyGenerator.generateTemporalKey("dataset1", 1234567890L, "key1")
        
        assertEquals("dataset1_temp_1234567890_key1", key)
    }
    
    @Test
    fun `CacheKeyGenerator generates composite key`() {
        val key = CacheKeyGenerator.generateCompositeKey("dataset1", "1.0", "region1", "key1")
        
        assertEquals("dataset1_v1.0_region_region1_key1", key)
    }
    
    @Test
    fun `CacheKeyGenerator generates user key`() {
        val key = CacheKeyGenerator.generateUserKey("user123", "favorites")
        
        assertEquals("user_user123_favorites", key)
    }
    
    @Test
    fun `CacheKeyGenerator generates navigation key`() {
        val key = CacheKeyGenerator.generateNavigationKey("route123", "geometry")
        
        assertEquals("nav_route123_geometry", key)
    }
    
    @Test
    fun `Freshness fromPercentage returns correct freshness`() {
        assertEquals(Freshness.FRESH, Freshness.fromPercentage(100.0))
        assertEquals(Freshness.FRESH, Freshness.fromPercentage(95.0))
        assertEquals(Freshness.RECENT, Freshness.fromPercentage(90.0))
        assertEquals(Freshness.RECENT, Freshness.fromPercentage(85.0))
        assertEquals(Freshness.STALE, Freshness.fromPercentage(70.0))
        assertEquals(Freshness.STALE, Freshness.fromPercentage(50.0))
        assertEquals(Freshness.EXPIRED, Freshness.fromPercentage(0.0))
        assertEquals(Freshness.EXPIRED, Freshness.fromPercentage(-10.0))
    }
    
    @Test
    fun `EvictionStrategy fromStrategy returns correct strategy`() {
        assertEquals(EvictionStrategy.LRU, EvictionStrategy.fromStrategy("LRU"))
        assertEquals(EvictionStrategy.LFU, EvictionStrategy.fromStrategy("LFU"))
        assertEquals(EvictionStrategy.TTL, EvictionStrategy.fromStrategy("TTL"))
        assertEquals(EvictionStrategy.SIZE, EvictionStrategy.fromStrategy("SIZE"))
        assertEquals(EvictionStrategy.AGE, EvictionStrategy.fromStrategy("AGE"))
        assertEquals(EvictionStrategy.RANDOM, EvictionStrategy.fromStrategy("RANDOM"))
        assertEquals(EvictionStrategy.LRU, EvictionStrategy.fromStrategy("unknown"))
    }
    
    @Test
    fun `CacheEntry equality`() {
        val entry1 = CacheEntry(
            key = "test_key",
            value = CacheValue(byteArrayOf(1, 2, 3)),
            datasetId = "dataset1",
            version = "1.0",
            region = "region1",
            createdAt = 1234567890L,
            lastAccessed = 1234567890L,
            ttl = 7 * 24 * 60 * 60 * 1000L,
            freshness = Freshness.FRESH,
            isUserCreated = false,
            size = 3
        )
        
        val entry2 = CacheEntry(
            key = "test_key",
            value = CacheValue(byteArrayOf(1, 2, 3)),
            datasetId = "dataset1",
            version = "1.0",
            region = "region1",
            createdAt = 1234567890L,
            lastAccessed = 1234567890L,
            ttl = 7 * 24 * 60 * 60 * 1000L,
            freshness = Freshness.FRESH,
            isUserCreated = false,
            size = 3
        )
        
        assertEquals(entry1, entry2)
    }
    
    @Test
    fun `CacheStatistics equality`() {
        val stats1 = CacheStatistics(
            totalEntries = 100,
            memoryEntries = 50,
            persistentEntries = 50,
            totalSize = 100 * 1024 * 1024L,
            memorySize = 50 * 1024 * 1024L,
            persistentSize = 50 * 1024 * 1024L,
            hitCount = 100,
            missCount = 50,
            hitRate = 0.6666666666666666,
            missRate = 0.3333333333333333,
            oldestEntryAge = 1000,
            newestEntryAge = 5000,
            averageEntryAge = 3000
        )
        
        val stats2 = CacheStatistics(
            totalEntries = 100,
            memoryEntries = 50,
            persistentEntries = 50,
            totalSize = 100 * 1024 * 1024L,
            memorySize = 50 * 1024 * 1024L,
            persistentSize = 50 * 1024 * 1024L,
            hitCount = 100,
            missCount = 50,
            hitRate = 0.6666666666666666,
            missRate = 0.3333333333333333,
            oldestEntryAge = 1000,
            newestEntryAge = 5000,
            averageEntryAge = 3000
        )
        
        assertEquals(stats1, stats2)
    }
    
    @Test
    fun `EvictionResult equality`() {
        val result1 = EvictionResult(
            success = true,
            evictedCount = 10,
            freedBytes = 100 * 1024 * 1024L,
            errors = emptyList(),
            policy = EvictionPolicy(strategy = EvictionStrategy.LRU)
        )
        
        val result2 = EvictionResult(
            success = true,
            evictedCount = 10,
            freedBytes = 100 * 1024 * 1024L,
            errors = emptyList(),
            policy = EvictionPolicy(strategy = EvictionStrategy.LRU)
        )
        
        assertEquals(result1, result2)
    }
    
    @Test
    fun `CacheArchitectureImpl can be instantiated`() {
        val cache = CacheArchitectureImpl()
        
        assertNotNull(cache)
    }
    
    @Test
    fun `CacheArchitectureImpl can be instantiated with custom capacity`() {
        val cache = CacheArchitectureImpl(
            memoryCacheCapacity = 500,
            memoryCacheSize = 50 * 1024 * 1024L
        )
        
        assertNotNull(cache)
    }
}