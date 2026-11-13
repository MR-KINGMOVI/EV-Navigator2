package com.evnavigator.iran.core.cache

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BoundedMemoryLruCache.
 * Verifies strict capacity bounding, LRU eviction order, and memory release on clear.
 */
class BoundedMemoryLruCacheTest {

    @Test
    fun `test lru eviction discards least recently accessed entry`() {
        val cache = BoundedMemoryLruCache<String, String>(maxCapacity = 3)

        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.put("k3", "v3")

        // Access k1 so k2 becomes the oldest/least recently accessed
        assertEquals("v1", cache.get("k1"))

        // Insert k4 -> should evict k2
        cache.put("k4", "v4")

        assertNotNull(cache.get("k1"))
        assertNull(cache.get("k2"))
        assertNotNull(cache.get("k3"))
        assertNotNull(cache.get("k4"))
        assertEquals(3, cache.size())
    }

    @Test
    fun `test clear releases all entries and resets size`() {
        val cache = BoundedMemoryLruCache<Int, String>(maxCapacity = 5)
        for (i in 1..5) cache.put(i, "val_$i")

        assertEquals(5, cache.size())
        cache.clear()

        assertEquals(0, cache.size())
        assertEquals(0, cache.currentEstimatedSize())
        assertNull(cache.get(1))
    }
}
