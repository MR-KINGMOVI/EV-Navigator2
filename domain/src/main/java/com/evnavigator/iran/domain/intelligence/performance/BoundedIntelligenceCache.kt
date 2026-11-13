package com.evnavigator.iran.domain.intelligence.performance

/**
 * Cache key encapsulating all parameters affecting an energy prediction.
 */
data class IntelligenceCacheKey(
    val vehicleId: String,
    val routeId: String,
    val startSocPercentRounded: Int,
    val tempCelsiusRounded: Int,
    val speedKmhRounded: Int,
    val payloadKgRounded: Int,
    val datasetVersion: String?
)

/**
 * Thread-safe, memory-bounded LRU cache for high-frequency intelligence calculations.
 */
class BoundedIntelligenceCache<K : Any, V : Any>(
    private val maxCapacity: Int = 50
) {
    private val map = object : LinkedHashMap<K, V>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxCapacity
        }
    }

    private val lock = Any()
    var hitCount: Long = 0
        private set
    var missCount: Long = 0
        private set

    fun get(key: K): V? = synchronized(lock) {
        val value = map[key]
        if (value != null) {
            hitCount++
        } else {
            missCount++
        }
        value
    }

    fun put(key: K, value: V): Unit = synchronized(lock) {
        map[key] = value
    }

    fun remove(key: K): V? = synchronized(lock) {
        map.remove(key)
    }

    fun clear(): Unit = synchronized(lock) {
        map.clear()
        hitCount = 0
        missCount = 0
    }

    val currentSize: Int get() = synchronized(lock) { map.size }
}
