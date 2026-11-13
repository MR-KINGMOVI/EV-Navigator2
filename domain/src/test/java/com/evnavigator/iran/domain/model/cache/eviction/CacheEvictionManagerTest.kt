package com.evnavigator.iran.domain.model.cache.eviction

import com.evnavigator.iran.domain.model.cache.CacheArchitecture
import com.evnavigator.iran.domain.model.cache.CacheEntry
import com.evnavigator.iran.domain.model.cache.CacheKey
import com.evnavigator.iran.domain.model.cache.CacheStatistics
import com.evnavigator.iran.domain.model.cache.CacheValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests for CacheEvictionManager.
 */
class CacheEvictionManagerTest {

    private lateinit var evictionManager: CacheEvictionManager
    private lateinit var cache: CacheArchitecture

    @BeforeEach
    fun setup() {
        evictionManager = CacheEvictionManagerImpl(
            CacheEvictionPolicy(
                strategy = CacheEvictionStrategy.LRU(),
                maxSize = 10
            )
        )
        cache = createTestCache()
    }

    @Test
    fun `evictToSize - no eviction when cache is within limits`() = runTest {
        val evicted = evictionManager.evictToSize(cache, preserveCriticalData = true)
        assertEquals(0, evicted)
    }

    @Test
    fun `evictToSize - evicts entries when cache exceeds limits`() = runTest {
        // Add 15 entries
        repeat(15) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = evictionManager.evictToSize(cache, preserveCriticalData = true)
        assertTrue(evicted > 0)
        assertTrue(cache.getCurrentSize() <= 10)
    }

    @Test
    fun `evict - evicts specified number of entries`() = runTest {
        // Add 15 entries
        repeat(15) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = evictionManager.evict(cache, 5, preserveCriticalData = true)
        assertEquals(5, evicted)
        assertEquals(10, cache.getCurrentSize())
    }

    @Test
    fun `evict - preserves critical entries`() = runTest {
        // Add 15 entries including critical entries
        repeat(5) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "critical_$i"
                ),
                CacheValue(
                    data = "critical_$i".toByteArray(),
                    metadata = mapOf("isCritical" to true)
                )
            )
        }

        repeat(10) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "normal_$i"
                ),
                CacheValue(
                    data = "normal_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = evictionManager.evict(cache, 5, preserveCriticalData = true)
        assertTrue(evicted <= 5) // Should not evict critical entries
        assertTrue(cache.getCurrentSize() >= 10) // Should preserve critical entries
    }

    @Test
    fun `evictExpiredEntries - evicts expired entries`() = runTest {
        // Add 10 entries with TTL of 1000ms
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.LRU(),
            maxSize = 10,
            ttl = 1000L
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        repeat(10) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        // Wait for entries to expire
        kotlinx.coroutines.delay(1100)

        val evicted = manager.evictExpiredEntries(testCache)
        assertTrue(evicted > 0)
    }

    @Test
    fun `evictByDatasetVersion - evicts entries for specific version`() = runTest {
        // Add entries for multiple versions
        repeat(5) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_v1_$i"
                ),
                CacheValue(
                    data = "value_v1_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        repeat(5) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v2",
                    key = "key_v2_$i"
                ),
                CacheValue(
                    data = "value_v2_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = evictionManager.evictByDatasetVersion(cache, "v1")
        assertEquals(5, evicted)
        assertEquals(5, cache.getCurrentSize())
    }

    @Test
    fun `evictByRegion - evicts entries outside specified region`() = runTest {
        // Add entries for multiple regions
        repeat(5) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    region = "region1",
                    key = "key_region1_$i"
                ),
                CacheValue(
                    data = "value_region1_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        repeat(5) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    region = "region2",
                    key = "key_region2_$i"
                ),
                CacheValue(
                    data = "value_region2_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = evictionManager.evictByRegion(cache, "region1")
        assertEquals(5, evicted)
        assertEquals(5, cache.getCurrentSize())
    }

    @Test
    fun `getEvictionStatistics - returns correct statistics`() = runTest {
        // Add and evict some entries
        repeat(15) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        evictionManager.evictToSize(cache, preserveCriticalData = true)

        val stats = evictionManager.getEvictionStatistics().first()
        assertTrue(stats.evictionCount > 0)
        assertTrue(stats.preservedCount >= 0)
        assertTrue(stats.evictedByStrategy.isNotEmpty())
    }

    @Test
    fun `resetEvictionStatistics - resets statistics`() = runTest {
        // Add and evict some entries
        repeat(15) { i ->
            cache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        evictionManager.evictToSize(cache, preserveCriticalData = true)

        var stats = evictionManager.getEvictionStatistics().first()
        assertTrue(stats.evictionCount > 0)

        evictionManager.resetEvictionStatistics()

        stats = evictionManager.getEvictionStatistics().first()
        assertEquals(0, stats.evictionCount)
        assertEquals(0, stats.preservedCount)
        assertTrue(stats.evictedByStrategy.isEmpty())
    }

    @Test
    fun `shouldPreserveEntry - preserves critical entries`() {
        val entry = CacheEntry(
            key = CacheKey(
                datasetVersion = "v1",
                key = "critical"
            ),
            value = CacheValue(
                data = "value".toByteArray(),
                metadata = mapOf("isCritical" to true)
            ),
            isCritical = true,
            isUserData = false,
            isNavigationState = false,
            isActiveDataset = false,
            isRollbackRequired = false,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        assertTrue(evictionManager.shouldPreserveEntry(entry))
    }

    @Test
    fun `shouldPreserveEntry - preserves user data`() {
        val entry = CacheEntry(
            key = CacheKey(
                datasetVersion = "v1",
                key = "favorite"
            ),
            value = CacheValue(
                data = "value".toByteArray(),
                metadata = emptyMap()
            ),
            isCritical = false,
            isUserData = true,
            isNavigationState = false,
            isActiveDataset = false,
            isRollbackRequired = false,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        assertTrue(evictionManager.shouldPreserveEntry(entry))
    }

    @Test
    fun `shouldPreserveEntry - preserves navigation state`() {
        val entry = CacheEntry(
            key = CacheKey(
                datasetVersion = "v1",
                key = "navigation"
            ),
            value = CacheValue(
                data = "value".toByteArray(),
                metadata = emptyMap()
            ),
            isCritical = false,
            isUserData = false,
            isNavigationState = true,
            isActiveDataset = false,
            isRollbackRequired = false,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        assertTrue(evictionManager.shouldPreserveEntry(entry))
    }

    @Test
    fun `shouldPreserveEntry - preserves active dataset`() {
        val entry = CacheEntry(
            key = CacheKey(
                datasetVersion = "v1",
                key = "active"
            ),
            value = CacheValue(
                data = "value".toByteArray(),
                metadata = emptyMap()
            ),
            isCritical = false,
            isUserData = false,
            isNavigationState = false,
            isActiveDataset = true,
            isRollbackRequired = false,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        assertTrue(evictionManager.shouldPreserveEntry(entry))
    }

    @Test
    fun `shouldPreserveEntry - preserves rollback required entries`() {
        val entry = CacheEntry(
            key = CacheKey(
                datasetVersion = "v1",
                key = "rollback"
            ),
            value = CacheValue(
                data = "value".toByteArray(),
                metadata = emptyMap()
            ),
            isCritical = false,
            isUserData = false,
            isNavigationState = false,
            isActiveDataset = false,
            isRollbackRequired = true,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        assertTrue(evictionManager.shouldPreserveEntry(entry))
    }

    @Test
    fun `shouldPreserveEntry - does not preserve expired entries`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.LRU(),
            maxSize = 10,
            ttl = 1000L
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        testCache.put(
            CacheKey(
                datasetVersion = "v1",
                key = "expired"
            ),
            CacheValue(
                data = "value".toByteArray(),
                metadata = emptyMap()
            )
        )

        // Wait for entry to expire
        kotlinx.coroutines.delay(1100)

        val entry = testCache.get(CacheKey(datasetVersion = "v1", key = "expired"))
        assertNotNull(entry)
        assertTrue(entry!!.isExpired(policy.ttl))
        assertFalse(evictionManager.shouldPreserveEntry(entry!!))
    }

    @Test
    fun `shouldPreserveEntry - does not preserve unknown dataset version`() {
        val entry = CacheEntry(
            key = CacheKey(
                datasetVersion = "",
                key = "unknown"
            ),
            value = CacheValue(
                data = "value".toByteArray(),
                metadata = emptyMap()
            ),
            isCritical = false,
            isUserData = false,
            isNavigationState = false,
            isActiveDataset = false,
            isRollbackRequired = false,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        assertFalse(evictionManager.shouldPreserveEntry(entry!!))
    }

    @Test
    fun `getEvictionPolicy - returns correct policy`() {
        val policy = evictionManager.getEvictionPolicy()
        assertNotNull(policy)
        assertEquals(CacheEvictionStrategy.LRU(), policy.strategy)
        assertEquals(10, policy.maxSize)
    }

    @Test
    fun `evictWithLFUStrategy - evicts least frequently used entries`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.LFU(),
            maxSize = 10
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        // Add 15 entries
        repeat(15) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        // Access some entries multiple times
        repeat(5) {
            testCache.get(CacheKey(datasetVersion = "v1", key = "key_0"))
            testCache.get(CacheKey(datasetVersion = "v1", key = "key_1"))
        }

        val evicted = manager.evict(testCache, 5, preserveCriticalData = true)
        assertTrue(evicted > 0)
        assertTrue(testCache.getCurrentSize() <= 10)
    }

    @Test
    fun `evictWithTTLStrategy - evicts expired entries`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.TTL(),
            maxSize = 10,
            ttl = 1000L
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        // Add 10 entries
        repeat(10) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        // Wait for entries to expire
        kotlinx.coroutines.delay(1100)

        val evicted = manager.evictExpiredEntries(testCache)
        assertTrue(evicted > 0)
    }

    @Test
    fun `evictWithAGEStrategy - evicts old entries`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.AGE(),
            maxSize = 10,
            ttl = null
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        // Add 10 entries with different ages
        repeat(10) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        // Wait for some entries to become old
        kotlinx.coroutines.delay(1100)

        val evicted = manager.evict(testCache, 5, preserveCriticalData = true)
        assertTrue(evicted > 0)
    }

    @Test
    fun `evictWithCompositeStrategy - applies multiple strategies`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.Composite(
                strategies = listOf(
                    CacheEvictionStrategy.LRU(),
                    CacheEvictionStrategy.TTL()
                ),
                name = "Composite(LRU,TTL)"
            ),
            maxSize = 10,
            ttl = 1000L
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        // Add 15 entries
        repeat(15) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        // Wait for entries to expire
        kotlinx.coroutines.delay(1100)

        val evicted = manager.evict(testCache, 5, preserveCriticalData = true)
        assertTrue(evicted > 0)
        assertTrue(testCache.getCurrentSize() <= 10)
    }

    @Test
    fun `evictWithRandomStrategy - evicts random entries`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.RANDOM(),
            maxSize = 10
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        // Add 15 entries
        repeat(15) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = manager.evict(testCache, 5, preserveCriticalData = true)
        assertTrue(evicted > 0)
        assertTrue(testCache.getCurrentSize() <= 10)
    }

    @Test
    fun `evictWithSIZEStrategy - evicts based on size`() = runTest {
        val policy = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.SIZE(),
            maxSize = 10
        )
        val manager = CacheEvictionManagerImpl(policy)
        val testCache = createTestCache()

        // Add 15 entries
        repeat(15) { i ->
            testCache.put(
                CacheKey(
                    datasetVersion = "v1",
                    key = "key_$i"
                ),
                CacheValue(
                    data = "value_$i".toByteArray(),
                    metadata = emptyMap()
                )
            )
        }

        val evicted = manager.evict(testCache, 5, preserveCriticalData = true)
        assertEquals(5, evicted)
        assertEquals(10, testCache.getCurrentSize())
    }

    private fun createTestCache(): CacheArchitecture {
        return object : CacheArchitecture {
            private val entries = mutableMapOf<CacheKey, CacheEntry>()
            private val accessOrder = mutableListOf<CacheKey>()

            override fun get(key: CacheKey): CacheEntry? {
                val entry = entries[key]
                if (entry != null) {
                    entry.lastAccessedAt = System.currentTimeMillis()
                    accessOrder.remove(key)
                    accessOrder.add(key)
                }
                return entry
            }

            override fun put(key: CacheKey, value: CacheValue): CacheEntry {
                val entry = CacheEntry(
                    key = key,
                    value = value,
                    isCritical = value.metadata["isCritical"] == true,
                    isUserData = value.metadata["isUserData"] == true,
                    isNavigationState = value.metadata["isNavigationState"] == true,
                    isActiveDataset = value.metadata["isActiveDataset"] == true,
                    isRollbackRequired = value.metadata["isRollbackRequired"] == true,
                    createdAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis()
                )
                entries[key] = entry
                accessOrder.add(key)
                return entry
            }

            override fun remove(key: CacheKey): Boolean {
                accessOrder.remove(key)
                return entries.remove(key) != null
            }

            override fun getAllEntries(): List<CacheEntry> {
                return entries.values.toList()
            }

            override fun getCurrentSize(): Int {
                return entries.size
            }

            override fun clear() {
                entries.clear()
                accessOrder.clear()
            }
        }
    }
}
