package com.evnavigator.iran.domain.model.cache.eviction

import com.evnavigator.iran.domain.model.cache.CacheArchitecture

/**
 * Policy for determining which cache entries should be evicted.
 *
 * This interface defines the contract for cache eviction policies that determine
 * which entries to evict when the cache needs to make space.
 *
 * Policies must be deterministic and must never evict critical data:
 * - Active navigation state
 * - User-created data (vehicles, favorites, recent destinations)
 * - Active datasets
 * - Required rollback data
 *
 * @property strategy The eviction strategy to use
 * @property maxSize Maximum number of entries in the cache
 * @property ttl Time-to-live for cache entries (null means no TTL)
 */
data class CacheEvictionPolicy(
    val strategy: CacheEvictionStrategy,
    val maxSize: Int,
    val ttl: Long? = null // milliseconds
) {
    /**
     * Validate the eviction policy.
     *
     * @return true if the policy is valid, false otherwise
     */
    fun isValid(): Boolean {
        return maxSize > 0 &&
               (strategy is CacheEvictionStrategy.LRU ||
                strategy is CacheEvictionStrategy.LFU ||
                strategy is CacheEvictionStrategy.TTL ||
                strategy is CacheEvictionStrategy.SIZE ||
                strategy is CacheEvictionStrategy.AGE ||
                strategy is CacheEvictionStrategy.RANDOM ||
                strategy is CacheEvictionStrategy.Composite)
    }

    companion object {
        /**
         * Default eviction policy for most cache types.
         */
        val DEFAULT = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.DEFAULT,
            maxSize = 1000,
            ttl = null
        )

        /**
         * Eviction policy for user-created data.
         */
        val USER_DATA = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.USER_DATA_PRESERVED,
            maxSize = 1000,
            ttl = null
        )

        /**
         * Eviction policy for navigation state.
         */
        val NAVIGATION = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.NAVIGATION_PRESERVED,
            maxSize = 1000,
            ttl = null
        )

        /**
         * Eviction policy for active datasets.
         */
        val ACTIVE_DATASET = CacheEvictionPolicy(
            strategy = CacheEvictionStrategy.ACTIVE_DATASET_PRESERVED,
            maxSize = 1000,
            ttl = null
        )
    }
}
