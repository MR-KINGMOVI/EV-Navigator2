package com.evnavigator.iran.domain.model.cache.eviction

import com.evnavigator.iran.domain.model.cache.CacheArchitecture
import com.evnavigator.iran.domain.model.cache.CacheEntry
import com.evnavigator.iran.domain.model.cache.CacheKey
import com.evnavigator.iran.domain.model.cache.CacheStatistics
import kotlinx.coroutines.flow.Flow

/**
 * Statistics for cache eviction operations.
 */
data class EvictionStatistics(
    val evictionCount: Long = 0,
    val preservedCount: Long = 0,
    val evictedByStrategy: Map<String, Long> = emptyMap()
)

/**
 * Manager for cache eviction operations.
 *
 * This interface defines the contract for managing cache eviction, including:
 * - Deterministic eviction based on strategy
 * - Preservation of critical data
 * - Cache size management
 * - Eviction statistics tracking
 *
 * The eviction manager must never evict:
 * - Active navigation state
 * - User-created data (vehicles, favorites, recent destinations)
 * - Active datasets
 * - Required rollback data
 *
 * @property policy The eviction policy to use
 */
interface CacheEvictionManager {
    /**
     * Evict entries from the cache until the cache size is within limits.
     *
     * @param cache The cache to evict from
     * @param preserveCriticalData Whether to preserve critical data (navigation, user data, active datasets)
     * @return The number of entries evicted
     */
    suspend fun evictToSize(
        cache: CacheArchitecture,
        preserveCriticalData: Boolean = true
    ): Int

    /**
     * Evict entries based on the specified strategy.
     *
     * @param cache The cache to evict from
     * @param count The number of entries to evict
     * @param preserveCriticalData Whether to preserve critical data
     * @return The number of entries evicted
     */
    suspend fun evict(
        cache: CacheArchitecture,
        count: Int,
        preserveCriticalData: Boolean = true
    ): Int

    /**
     * Evict entries that have exceeded their TTL.
     *
     * @param cache The cache to evict from
     * @return The number of entries evicted
     */
    suspend fun evictExpiredEntries(
        cache: CacheArchitecture
    ): Int

    /**
     * Evict entries that are no longer needed based on dataset version.
     *
     * @param cache The cache to evict from
     * @param datasetVersion The dataset version to evict entries for
     * @return The number of entries evicted
     */
    suspend fun evictByDatasetVersion(
        cache: CacheArchitecture,
        datasetVersion: String
    ): Int

    /**
     * Evict entries that are outside the specified geographic region.
     *
     * @param cache The cache to evict from
     * @param region The geographic region to preserve
     * @return The number of entries evicted
     */
    suspend fun evictByRegion(
        cache: CacheArchitecture,
        region: String
    ): Int

    /**
     * Get the eviction statistics.
     *
     * @return The eviction statistics
     */
    fun getEvictionStatistics(): Flow<EvictionStatistics>

    /**
     * Reset eviction statistics.
     */
    fun resetEvictionStatistics()

    /**
     * Check if an entry should be preserved during eviction.
     *
     * @param entry The cache entry to check
     * @return true if the entry should be preserved, false otherwise
     */
    fun shouldPreserveEntry(entry: CacheEntry): Boolean

    /**
     * Get the eviction policy.
     *
     * @return The eviction policy
     */
    fun getEvictionPolicy(): CacheEvictionPolicy
}
