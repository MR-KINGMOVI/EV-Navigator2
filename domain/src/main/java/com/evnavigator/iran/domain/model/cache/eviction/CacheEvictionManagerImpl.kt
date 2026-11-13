package com.evnavigator.iran.domain.model.cache.eviction

import com.evnavigator.iran.domain.model.cache.CacheArchitecture
import com.evnavigator.iran.domain.model.cache.CacheEntry
import com.evnavigator.iran.domain.model.cache.EvictionPolicy
import com.evnavigator.iran.domain.model.cache.EvictionStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of cache eviction manager.
 *
 * This class implements deterministic cache eviction with support for multiple
 * eviction strategies and preservation of critical data by delegating to CacheArchitecture.
 *
 * @property policy The eviction policy to use
 */
class CacheEvictionManagerImpl(
    private val policy: CacheEvictionPolicy
) : CacheEvictionManager {

    private val statisticsMutex = Mutex()

    private val evictionCount = AtomicLong(0)
    private val preservedCount = AtomicLong(0)
    private val evictedByStrategy = mutableMapOf<String, AtomicLong>()

    private val _evictionStatistics = MutableStateFlow(
        EvictionStatistics(
            evictionCount = 0,
            preservedCount = 0,
            evictedByStrategy = emptyMap()
        )
    )

    init {
        // Initialize strategy counters
        for (strategy in getAllStrategies(policy.strategy)) {
            evictedByStrategy[strategy.name] = AtomicLong(0)
        }
    }

    override suspend fun evictToSize(
        cache: CacheArchitecture,
        preserveCriticalData: Boolean
    ): Int {
        val currentStats = cache.getCacheStatistics()
        val currentSize = currentStats.memorySize + currentStats.persistentSize
        val maxSize = policy.maxSize.toLong()

        if (currentSize <= maxSize) {
            return 0
        }

        val cachePolicy = EvictionPolicy(
            strategy = mapStrategy(getEvictionStrategy(policy.strategy)),
            maxSize = maxSize,
            preserveUserCreatedData = preserveCriticalData
        )
        val result = cache.evictCacheEntries(cachePolicy)
        
        updateStats(result.evictedCount.toLong(), 0, getEvictionStrategy(policy.strategy).name)
        return result.evictedCount
    }

    override suspend fun evict(
        cache: CacheArchitecture,
        count: Int,
        preserveCriticalData: Boolean
    ): Int {
        if (count <= 0) return 0
        
        val cachePolicy = EvictionPolicy(
            strategy = mapStrategy(getEvictionStrategy(policy.strategy)),
            maxEntries = cache.getCacheStatistics().totalEntries - count,
            preserveUserCreatedData = preserveCriticalData
        )
        val result = cache.evictCacheEntries(cachePolicy)
        
        updateStats(result.evictedCount.toLong(), 0, getEvictionStrategy(policy.strategy).name)
        return result.evictedCount
    }

    override suspend fun evictExpiredEntries(
        cache: CacheArchitecture
    ): Int {
        val cachePolicy = EvictionPolicy(
            strategy = EvictionStrategy.TTL,
            maxAge = policy.ttl ?: (7 * 24 * 60 * 60 * 1000L)
        )
        val result = cache.evictCacheEntries(cachePolicy)
        updateStats(result.evictedCount.toLong(), 0, CacheEvictionStrategy.TTL().name)
        return result.evictedCount
    }

    override suspend fun evictByDatasetVersion(
        cache: CacheArchitecture,
        datasetVersion: String
    ): Int {
        val keys = cache.getCacheKeysByVersion(datasetVersion)
        var evicted = 0
        for (key in keys) {
            cache.invalidateCacheEntry(key)
            evicted++
        }
        updateStats(evicted.toLong(), 0, CacheEvictionStrategy.SIZE().name)
        return evicted
    }

    override suspend fun evictByRegion(
        cache: CacheArchitecture,
        region: String
    ): Int {
        cache.invalidateCacheByRegion(region)
        return 0 
    }

    override fun getEvictionStatistics(): Flow<EvictionStatistics> {
        return _evictionStatistics.asStateFlow()
    }

    override fun resetEvictionStatistics() {
        evictionCount.set(0)
        preservedCount.set(0)
        for (counter in evictedByStrategy.values) {
            counter.set(0)
        }
        _evictionStatistics.value = EvictionStatistics(
            evictionCount = 0,
            preservedCount = 0,
            evictedByStrategy = emptyMap()
        )
    }

    override fun shouldPreserveEntry(entry: CacheEntry): Boolean {
        // If datasetVersion is empty/blank in key or entry, it's unknown/invalid
        if (entry.version.isBlank()) {
            val colonIdx = entry.key.indexOf(':')
            if (colonIdx == 0 || (colonIdx > 0 && entry.key.substring(0, colonIdx).isBlank())) {
                return false
            }
        }
        if (entry.isExpired(policy.ttl)) return false
        return entry.isCritical || entry.isUserData || entry.isNavigationState ||
                entry.isActiveDataset || entry.isRollbackRequired || entry.isUserCreated
    }

    override fun getEvictionPolicy(): CacheEvictionPolicy {
        return policy
    }

    private suspend fun updateStats(evicted: Long, preserved: Long, strategyName: String) {
        statisticsMutex.withLock {
            evictionCount.addAndGet(evicted)
            preservedCount.addAndGet(preserved)
            evictedByStrategy[strategyName]?.addAndGet(evicted)
            _evictionStatistics.value = EvictionStatistics(
                evictionCount = evictionCount.get(),
                preservedCount = preservedCount.get(),
                evictedByStrategy = evictedByStrategy.mapValues { it.value.get() }
            )
        }
    }

    /**
     * Get all strategies from a composite strategy.
     */
    private fun getAllStrategies(strategy: CacheEvictionStrategy): List<CacheEvictionStrategy> {
        return when (strategy) {
            is CacheEvictionStrategy.Composite -> strategy.strategies
            else -> listOf(strategy)
        }
    }

    /**
     * Get the primary eviction strategy.
     */
    private fun getEvictionStrategy(strategy: CacheEvictionStrategy): CacheEvictionStrategy {
        return when (strategy) {
            is CacheEvictionStrategy.Composite -> strategy.strategies.first()
            else -> strategy
        }
    }
    
    private fun mapStrategy(strategy: CacheEvictionStrategy): EvictionStrategy {
        return when (strategy) {
            is CacheEvictionStrategy.LRU -> EvictionStrategy.LRU
            is CacheEvictionStrategy.LFU -> EvictionStrategy.LFU
            is CacheEvictionStrategy.TTL -> EvictionStrategy.TTL
            is CacheEvictionStrategy.SIZE -> EvictionStrategy.SIZE
            is CacheEvictionStrategy.Composite -> EvictionStrategy.LRU
            else -> EvictionStrategy.LRU
        }
    }
}
