package com.evnavigator.iran.domain.model.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * CacheArchitectureImpl - Production implementation of cache architecture
 * 
 * This implementation provides:
 * - Multi-layer cache (memory and persistent)
 * - Cache statistics tracking
 * - Cache invalidation by dataset version
 * - Cache invalidation by geographic region
 * - Cache invalidation by age
 * - Cache eviction with various strategies
 * - Cache key generation
 * - Freshness tracking
 * - Cache hit/miss tracking
 * - Prevention of cache contamination across dataset versions
 */
class CacheArchitectureImpl(
    private val memoryCacheCapacity: Int = 1000,
    private val memoryCacheSize: Long = 100 * 1024 * 1024L, // 100 MB
    private val persistentCacheCapacity: Int = 10000,
    private val persistentCacheSize: Long = 500 * 1024 * 1024L // 500 MB
) : CacheArchitecture {
    
    // Memory cache (in-memory)
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private var memoryCacheSizeBytes = AtomicLong(0)
    
    // Persistent cache (simulated with ConcurrentHashMap)
    private val persistentCache = ConcurrentHashMap<String, CacheEntry>()
    private var persistentCacheSizeBytes = AtomicLong(0)
    
    // Cache statistics
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    
    // Cache statistics flow
    private val _cacheStatistics = MutableStateFlow<CacheStatistics>(CacheStatistics(
        totalEntries = 0,
        memoryEntries = 0,
        persistentEntries = 0,
        totalSize = 0,
        memorySize = 0,
        persistentSize = 0,
        hitCount = 0,
        missCount = 0,
        hitRate = 0.0,
        missRate = 0.0,
        oldestEntryAge = 0,
        newestEntryAge = 0,
        averageEntryAge = 0
    ))
    
    override fun getCacheStatisticsFlow(): Flow<CacheStatistics> {
        return _cacheStatistics.asStateFlow()
    }
    
    override suspend fun getCacheStatistics(): CacheStatistics = calculateCacheStatistics()

    private fun calculateCacheStatistics(): CacheStatistics {
        val now = System.currentTimeMillis()
        
        val memoryEntries = memoryCache.size
        val persistentEntries = persistentCache.size
        val totalEntries = memoryEntries + persistentEntries
        
        val memorySize = memoryCacheSizeBytes.get()
        val persistentSize = persistentCacheSizeBytes.get()
        val totalSize = memorySize + persistentSize
        
        val oldestEntryAge = if (totalEntries > 0) {
            memoryCache.values.minOfOrNull { now - it.createdAt } ?: 
            persistentCache.values.minOfOrNull { now - it.createdAt } ?: 0
        } else {
            0
        }
        
        val newestEntryAge = if (totalEntries > 0) {
            memoryCache.values.maxOfOrNull { now - it.createdAt } ?: 
            persistentCache.values.maxOfOrNull { now - it.createdAt } ?: 0
        } else {
            0
        }
        
        val averageEntryAge = if (totalEntries > 0) {
            (memoryCache.values.sumOf { now - it.createdAt } + 
             persistentCache.values.sumOf { now - it.createdAt }) / totalEntries
        } else {
            0
        }
        
        val totalHits = hitCount.get()
        val totalMisses = missCount.get()
        val hitRate = if (totalHits + totalMisses > 0) {
            totalHits.toDouble() / (totalHits + totalMisses)
        } else {
            0.0
        }
        val missRate = if (totalHits + totalMisses > 0) {
            totalMisses.toDouble() / (totalHits + totalMisses)
        } else {
            0.0
        }
        
        return CacheStatistics(
            totalEntries = totalEntries,
            memoryEntries = memoryEntries,
            persistentEntries = persistentEntries,
            totalSize = totalSize,
            memorySize = memorySize,
            persistentSize = persistentSize,
            hitCount = totalHits,
            missCount = totalMisses,
            hitRate = hitRate,
            missRate = missRate,
            oldestEntryAge = oldestEntryAge,
            newestEntryAge = newestEntryAge,
            averageEntryAge = averageEntryAge
        )
    }
    
    override suspend fun getCacheEntry(key: String): CacheEntry? {
        // Check memory cache first
        val memoryEntry = memoryCache[key]
        if (memoryEntry != null) {
            // Update last accessed monotonically
            val now = System.currentTimeMillis()
            val nextAccess = if (now > memoryEntry.lastAccessed) now else memoryEntry.lastAccessed + 1
            val updatedEntry = memoryEntry.copy(lastAccessed = nextAccess)
            memoryCache[key] = updatedEntry
            hitCount.incrementAndGet()
            return updatedEntry
        }
        
        // Check persistent cache
        val persistentEntry = persistentCache[key]
        if (persistentEntry != null) {
            // Move to memory cache monotonically
            val now = System.currentTimeMillis()
            val nextAccess = if (now > persistentEntry.lastAccessed) now else persistentEntry.lastAccessed + 1
            val updatedEntry = persistentEntry.copy(lastAccessed = nextAccess)
            memoryCache[key] = updatedEntry
            memoryCacheSizeBytes.addAndGet(updatedEntry.size)
            persistentCache.remove(key)
            persistentCacheSizeBytes.addAndGet(-updatedEntry.size)
            
            // Update last accessed
            hitCount.incrementAndGet()
            return updatedEntry
        }
        
        // Cache miss
        missCount.incrementAndGet()
        return null
    }
    
    override suspend fun putCacheEntry(
        key: String,
        value: CacheValue,
        ttl: Long,
        freshness: Freshness
    ) {
        val now = System.currentTimeMillis()
        val entry = CacheEntry(
            key = key,
            value = value,
            datasetId = value.metadata["datasetId"] as? String ?: "",
            version = value.metadata["version"] as? String ?: "",
            region = value.metadata["region"] as? String ?: "",
            createdAt = now,
            lastAccessed = now,
            ttl = ttl,
            freshness = freshness,
            isUserCreated = value.metadata["isUserCreated"] as? Boolean ?: false,
            size = value.data.size.toLong()
        )
        
        // Check if entry already exists
        val existingEntry = memoryCache[key] ?: persistentCache[key]
        
        if (existingEntry != null) {
            // Update existing entry
            if (memoryCache.containsKey(key)) {
                memoryCacheSizeBytes.addAndGet(entry.size - existingEntry.size)
            } else {
                persistentCacheSizeBytes.addAndGet(entry.size - existingEntry.size)
            }
            memoryCache[key] = entry
            persistentCache[key] = entry
        } else {
            // Add new entry
            if (memoryCache.size < memoryCacheCapacity && memoryCacheSizeBytes.get() + entry.size <= memoryCacheSize) {
                // Add to memory cache
                memoryCache[key] = entry
                memoryCacheSizeBytes.addAndGet(entry.size)
            } else {
                // Add to persistent cache
                persistentCache[key] = entry
                persistentCacheSizeBytes.addAndGet(entry.size)
            }
        }
        
        updateCacheStatistics()
    }
    
    override suspend fun invalidateCacheEntry(key: String) {
        memoryCache.remove(key)
        persistentCache.remove(key)
        updateCacheStatistics()
    }
    
    override suspend fun invalidateCacheByDatasetVersion(datasetId: String, version: String) {
        val keysToRemove = memoryCache.keys.filter { it.startsWith("${datasetId}_v${version}") }
        keysToRemove.forEach { key ->
            memoryCache.remove(key)
            persistentCache.remove(key)
        }
        updateCacheStatistics()
    }
    
    override suspend fun invalidateCacheByRegion(region: String) {
        val keysToRemove = memoryCache.keys.filter { it.contains("_region_${region}") }
        keysToRemove.forEach { key ->
            memoryCache.remove(key)
            persistentCache.remove(key)
        }
        updateCacheStatistics()
    }
    
    override suspend fun invalidateCacheByAge(maxAge: Long): Int {
        val now = System.currentTimeMillis()
        var invalidatedCount = 0
        
        // Invalidate memory cache entries
        val memoryKeysToRemove = memoryCache.keys.filter { now - memoryCache[it]!!.createdAt > maxAge }
        memoryKeysToRemove.forEach { key ->
            memoryCache.remove(key)
            invalidatedCount++
        }
        
        // Invalidate persistent cache entries
        val persistentKeysToRemove = persistentCache.keys.filter { now - persistentCache[it]!!.createdAt > maxAge }
        persistentKeysToRemove.forEach { key ->
            persistentCache.remove(key)
            invalidatedCount++
        }
        
        updateCacheStatistics()
        return invalidatedCount
    }
    
    override suspend fun evictCacheEntries(policy: EvictionPolicy): EvictionResult {
        val now = System.currentTimeMillis()
        var evictedCount = 0
        var freedBytes = 0L
        val errors = mutableListOf<String>()
        
        when (policy.strategy) {
            EvictionStrategy.LRU -> {
                // Evict least recently used entries
                val sortedEntries = memoryCache.entries.sortedBy { it.value.lastAccessed }
                for ((key, entry) in sortedEntries) {
                    if (evictedCount >= policy.maxEntries) break
                    if (freedBytes >= policy.maxSize) break
                    if (policy.preserveUserCreatedData && entry.isUserCreated) continue
                    
                    memoryCache.remove(key)
                    freedBytes += entry.size
                    evictedCount++
                }
            }
            EvictionStrategy.LFU -> {
                // Evict least frequently used entries (simulated by age)
                val sortedEntries = memoryCache.entries.sortedBy { it.value.createdAt }
                for ((key, entry) in sortedEntries) {
                    if (evictedCount >= policy.maxEntries) break
                    if (freedBytes >= policy.maxSize) break
                    if (policy.preserveUserCreatedData && entry.isUserCreated) continue
                    
                    memoryCache.remove(key)
                    freedBytes += entry.size
                    evictedCount++
                }
            }
            EvictionStrategy.TTL -> {
                // Evict expired entries
                val expiredEntries = memoryCache.values.filter { now - it.createdAt > it.ttl }
                for (entry in expiredEntries) {
                    if (evictedCount >= policy.maxEntries) break
                    if (freedBytes >= policy.maxSize) break
                    
                    memoryCache.remove(entry.key)
                    freedBytes += entry.size
                    evictedCount++
                }
            }
            EvictionStrategy.SIZE -> {
                // Evict based on size
                val sortedEntries = memoryCache.entries.sortedByDescending { it.value.size }
                for ((key, entry) in sortedEntries) {
                    if (evictedCount >= policy.maxEntries) break
                    if (freedBytes >= policy.maxSize) break
                    if (policy.preserveUserCreatedData && entry.isUserCreated) continue
                    
                    memoryCache.remove(key)
                    freedBytes += entry.size
                    evictedCount++
                }
            }
            EvictionStrategy.AGE -> {
                // Evict oldest entries
                val sortedEntries = memoryCache.entries.sortedBy { it.value.createdAt }
                for ((key, entry) in sortedEntries) {
                    if (evictedCount >= policy.maxEntries) break
                    if (freedBytes >= policy.maxSize) break
                    if (policy.preserveUserCreatedData && entry.isUserCreated) continue
                    
                    memoryCache.remove(key)
                    freedBytes += entry.size
                    evictedCount++
                }
            }
            EvictionStrategy.RANDOM -> {
                // Evict random entries
                val entries = memoryCache.entries.toList().shuffled()
                for ((key, entry) in entries) {
                    if (evictedCount >= policy.maxEntries) break
                    if (freedBytes >= policy.maxSize) break
                    if (policy.preserveUserCreatedData && entry.isUserCreated) continue
                    
                    memoryCache.remove(key)
                    freedBytes += entry.size
                    evictedCount++
                }
            }
        }
        
        updateCacheStatistics()
        
        return EvictionResult(
            success = evictedCount > 0,
            evictedCount = evictedCount,
            freedBytes = freedBytes,
            errors = errors,
            policy = policy
        )
    }
    
    override suspend fun getCacheKeysByDataset(datasetId: String): List<String> {
        return memoryCache.keys.filter { it.startsWith(datasetId) } +
               persistentCache.keys.filter { it.startsWith(datasetId) }
    }
    
    override suspend fun getCacheKeysByVersion(version: String): List<String> {
        return memoryCache.keys.filter { it.contains("_v${version}") } +
               persistentCache.keys.filter { it.contains("_v${version}") }
    }
    
    override suspend fun getCacheKeysByRegion(region: String): List<String> {
        return memoryCache.keys.filter { it.contains("_region_${region}") } +
               persistentCache.keys.filter { it.contains("_region_${region}") }
    }
    
    override suspend fun getCacheSizeByDataset(datasetId: String): Long {
        val keys = getCacheKeysByDataset(datasetId)
        return keys.sumOf { key ->
            val entry = memoryCache[key] ?: persistentCache[key]
            entry?.size ?: 0
        }
    }
    
    override suspend fun getCacheSizeByVersion(version: String): Long {
        val keys = getCacheKeysByVersion(version)
        return keys.sumOf { key ->
            val entry = memoryCache[key] ?: persistentCache[key]
            entry?.size ?: 0
        }
    }
    
    override suspend fun getCacheSizeByRegion(region: String): Long {
        val keys = getCacheKeysByRegion(region)
        return keys.sumOf { key ->
            val entry = memoryCache[key] ?: persistentCache[key]
            entry?.size ?: 0
        }
    }
    
    override suspend fun isCacheEntryFresh(key: String): Boolean {
        val entry = memoryCache[key] ?: persistentCache[key] ?: return false
        val now = System.currentTimeMillis()
        return now - entry.createdAt <= entry.ttl
    }
    
    override suspend fun getCacheEntryFreshness(key: String): Freshness {
        val entry = memoryCache[key] ?: persistentCache[key] ?: return Freshness.EXPIRED
        val now = System.currentTimeMillis()
        val age = now - entry.createdAt
        val percentage = if (entry.ttl > 0) {
            (1.0 - (age.toDouble() / entry.ttl)) * 100.0
        } else {
            0.0
        }
        return Freshness.fromPercentage(percentage)
    }
    
    override suspend fun getCacheEntryAge(key: String): Long {
        val entry = memoryCache[key] ?: persistentCache[key] ?: return 0
        return System.currentTimeMillis() - entry.createdAt
    }
    
    override suspend fun clearAllCache(preserveUserCreatedData: Boolean): Int {
        var clearedCount = 0
        
        if (preserveUserCreatedData) {
            // Remove non-user-created entries
            val keysToRemove = memoryCache.keys.filter { !it.startsWith("user_") }
            keysToRemove.forEach { key ->
                memoryCache.remove(key)
                clearedCount++
            }
            val persistentKeysToRemove = persistentCache.keys.filter { !it.startsWith("user_") }
            persistentKeysToRemove.forEach { key ->
                persistentCache.remove(key)
                clearedCount++
            }
        } else {
            // Remove all entries
            clearedCount += memoryCache.size
            memoryCache.clear()
            clearedCount += persistentCache.size
            persistentCache.clear()
        }
        
        updateCacheStatistics()
        return clearedCount
    }
    
    override suspend fun getCacheHitRate(): Double {
        val total = hitCount.get() + missCount.get()
        return if (total > 0) {
            hitCount.get().toDouble() / total
        } else {
            0.0
        }
    }
    
    override suspend fun getCacheMissRate(): Double {
        val total = hitCount.get() + missCount.get()
        return if (total > 0) {
            missCount.get().toDouble() / total
        } else {
            0.0
        }
    }
    
    override suspend fun getCacheHitCount(): Long {
        return hitCount.get()
    }
    
    override suspend fun getCacheMissCount(): Long {
        return missCount.get()
    }
    
    override suspend fun resetCacheStatistics() {
        hitCount.set(0)
        missCount.set(0)
        updateCacheStatistics()
    }
    
    private fun updateCacheStatistics() {
        val stats = calculateCacheStatistics()
        _cacheStatistics.value = stats
    }

    override fun getCurrentSize(): Int = memoryCache.size + persistentCache.size

    override fun getAllEntries(): List<CacheEntry> = (memoryCache.values + persistentCache.values).toList()

    override fun remove(key: String): Boolean {
        val mem = memoryCache.remove(key)
        val pers = persistentCache.remove(key)
        return mem != null || pers != null
    }
}