package com.evnavigator.iran.domain.model.cache.eviction

import com.evnavigator.iran.domain.model.cache.CacheEntry

/**
 * Cache eviction strategy for determining which cache entries should be removed.
 *
 * This interface defines the contract for cache eviction strategies that determine
 * which cache entries to evict when the cache reaches capacity or needs to make space.
 *
 * Eviction strategies must be deterministic and must never evict critical data:
 * - Active navigation state
 * - User-created data (vehicles, favorites, recent destinations)
 * - Active datasets
 * - Required rollback data
 *
 * @property name The name of the eviction strategy
 */
sealed class CacheEvictionStrategy(
    open val name: String
) {
    open fun shouldEvict(entry: CacheEntry): Boolean = true

    /**
     * LRU (Least Recently Used) - Evict the least recently used entries.
     *
     * This is the default strategy for most cache types. Entries that haven't been
     * accessed recently are evicted first.
     */
    data class LRU(
        override val name: String = "LRU"
    ) : CacheEvictionStrategy(name)

    /**
     * LFU (Least Frequently Used) - Evict the least frequently used entries.
     *
     * This strategy considers both access frequency and recency. Entries that are
     * rarely accessed are evicted first.
     */
    data class LFU(
        override val name: String = "LFU"
    ) : CacheEvictionStrategy(name)

    /**
     * TTL (Time To Live) - Evict entries that have exceeded their TTL.
     *
     * Each cache entry has an expiration time. Entries that have been in the cache
     * longer than their TTL are evicted regardless of access patterns.
     */
    data class TTL(
        override val name: String = "TTL"
    ) : CacheEvictionStrategy(name)

    /**
     * SIZE - Evict based on cache size.
     *
     * When the cache exceeds a configured size threshold, entries are evicted
     * according to the specified strategy (LRU, LFU, etc.).
     */
    data class SIZE(
        override val name: String = "SIZE"
    ) : CacheEvictionStrategy(name)

    /**
     * AGE - Evict entries based on age.
     *
     * Entries that have been in the cache for a specified duration are evicted.
     * This is similar to TTL but considers the entry's age rather than expiration.
     */
    data class AGE(
        override val name: String = "AGE"
    ) : CacheEvictionStrategy(name)

    /**
     * RANDOM - Evict entries randomly.
     *
     * This strategy randomly selects entries to evict. It's useful for testing
     * and for scenarios where all entries are equally important.
     */
    data class RANDOM(
        override val name: String = "RANDOM"
    ) : CacheEvictionStrategy(name)

    /**
     * Composite strategy that applies multiple eviction criteria.
     *
     * For example, an entry might be evicted if it's both old AND rarely used.
     */
    data class Composite(
        val strategies: List<CacheEvictionStrategy>,
        override val name: String = "Composite(${strategies.joinToString { it.name }})"
    ) : CacheEvictionStrategy(name)

    companion object {
        /**
         * Default eviction strategy for most cache types.
         */
        val DEFAULT = LRU()

        /**
         * Eviction strategy for user-created data (vehicles, favorites, etc.).
         * These should never be evicted unless explicitly requested by the user.
         */
        val USER_DATA_PRESERVED = Composite(
            strategies = listOf(
                LRU(),
                TTL()
            ),
            name = "USER_DATA_PRESERVED"
        )

        /**
         * Eviction strategy for navigation state.
         * These entries must never be evicted during active navigation.
         */
        val NAVIGATION_PRESERVED = Composite(
            strategies = listOf(
                LRU(),
                TTL()
            ),
            name = "NAVIGATION_PRESERVED"
        )

        /**
         * Eviction strategy for active datasets.
         * These entries must never be evicted.
         */
        val ACTIVE_DATASET_PRESERVED = Composite(
            strategies = listOf(
                LRU(),
                TTL()
            ),
            name = "ACTIVE_DATASET_PRESERVED"
        )
    }
}
