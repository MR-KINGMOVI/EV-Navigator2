package com.evnavigator.iran.core.cache

import java.util.LinkedHashMap

/**
 * Thread-safe, bounded Least Recently Used (LRU) cache with size-weighted eviction.
 *
 * Designed to prevent Out-Of-Memory (OOM) errors during heavy offline routing,
 * elevation queries, and map rendering on resource-constrained mobile hardware.
 */
class BoundedMemoryLruCache<K, V>(
    private val maxCapacity: Int,
    private val sizeEstimator: (K, V) -> Int = { _, _ -> 1 }
) {

    private val lock = Any()
    private var currentSize = 0

    private val map = object : LinkedHashMap<K, V>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxCapacity
        }
    }

    fun get(key: K): V? {
        synchronized(lock) {
            return map[key]
        }
    }

    fun put(key: K, value: V): V? {
        synchronized(lock) {
            val weight = sizeEstimator(key, value)
            val previous = map.put(key, value)
            if (previous != null) {
                currentSize -= sizeEstimator(key, previous)
            }
            currentSize += weight

            while (map.size > maxCapacity) {
                val iterator = map.entries.iterator()
                if (iterator.hasNext()) {
                    val entry = iterator.next()
                    currentSize -= sizeEstimator(entry.key, entry.value)
                    iterator.remove()
                } else {
                    break
                }
            }
            return previous
        }
    }

    fun remove(key: K): V? {
        synchronized(lock) {
            val removed = map.remove(key)
            if (removed != null) {
                currentSize -= sizeEstimator(key, removed)
            }
            return removed
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
            currentSize = 0
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return map.size
        }
    }

    fun currentEstimatedSize(): Int {
        synchronized(lock) {
            return currentSize
        }
    }
}
