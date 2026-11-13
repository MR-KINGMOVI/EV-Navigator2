package com.evnavigator.iran.domain.model.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * CacheArchitecture - Production-grade cache architecture
 * 
 * Responsibilities:
 * - Define cache layers and hierarchy
 * - Manage memory cache
 * - Manage persistent cache
 * - Provide dataset-backed data access
 * - Track data freshness
 * - Implement cache invalidation
 * - Generate cache keys
 * - Prevent cache contamination across dataset versions
 * - Support version-based cache keys
 * - Support geographic-based cache keys
 * - Support temporal-based cache keys
 * 
 * Architecture:
 * - Source of truth - Remote data source
 * - Cache layers - Multiple cache layers for performance
 * - Memory cache - Fast in-memory cache
 * - Persistent cache - Disk-based cache
 * - Dataset-backed data - Data backed by offline datasets
 * - Live data - Data from remote source
 * - Freshness - Data freshness tracking
 * - Invalidation - Cache invalidation mechanisms
 * - Cache keys - Cache key generation strategies
 * - Version keys - Version-based cache keys
 * - Geographic keys - Geographic-based cache keys
 * - Temporal keys - Temporal-based cache keys
 */
interface CacheArchitecture {
    
    /**
     * Get cache statistics
     * 
     * @return Cache statistics
     */
    suspend fun getCacheStatistics(): CacheStatistics = CacheStatistics()
    
    /**
     * Get cache entry
     * 
     * @param key Cache key
     * @return Cache entry if found, null otherwise
     */
    suspend fun getCacheEntry(key: String): CacheEntry? = null
    
    /**
     * Put cache entry
     * 
     * @param key Cache key
     * @param value Cache value
     * @param ttl Time to live in milliseconds
     * @param freshness Freshness level
     */
    suspend fun putCacheEntry(key: String, value: CacheValue, ttl: Long, freshness: Freshness) {}
    
    /**
     * Invalidate cache entry
     * 
     * @param key Cache key
     */
    suspend fun invalidateCacheEntry(key: String) {}
    
    /**
     * Invalidate cache by dataset version
     * 
     * @param datasetId Dataset identifier
     * @param version Dataset version
     */
    suspend fun invalidateCacheByDatasetVersion(datasetId: String, version: String) {}
    
    /**
     * Invalidate cache by geographic region
     * 
     * @param region Geographic region identifier
     */
    suspend fun invalidateCacheByRegion(region: String) {}
    
    /**
     * Invalidate cache by age
     * 
     * @param maxAge Maximum age in milliseconds
     * @return Number of entries invalidated
     */
    suspend fun invalidateCacheByAge(maxAge: Long): Int = 0
    
    /**
     * Evict cache entries
     * 
     * @param policy Eviction policy
     * @return Eviction result
     */
    suspend fun evictCacheEntries(policy: EvictionPolicy): EvictionResult =
        EvictionResult(success = true, evictedCount = 0, freedBytes = 0L, errors = emptyList(), policy = policy)
    
    /**
     * Get cache keys by dataset
     * 
     * @param datasetId Dataset identifier
     * @return List of cache keys
     */
    suspend fun getCacheKeysByDataset(datasetId: String): List<String> = emptyList()
    
    /**
     * Get cache keys by version
     * 
     * @param version Dataset version
     * @return List of cache keys
     */
    suspend fun getCacheKeysByVersion(version: String): List<String> = emptyList()
    
    /**
     * Get cache keys by region
     * 
     * @param region Geographic region identifier
     * @return List of cache keys
     */
    suspend fun getCacheKeysByRegion(region: String): List<String> = emptyList()
    
    /**
     * Get cache size by dataset
     * 
     * @param datasetId Dataset identifier
     * @return Cache size in bytes
     */
    suspend fun getCacheSizeByDataset(datasetId: String): Long = 0L
    
    /**
     * Get cache size by version
     * 
     * @param version Dataset version
     * @return Cache size in bytes
     */
    suspend fun getCacheSizeByVersion(version: String): Long = 0L
    
    /**
     * Get cache size by region
     * 
     * @param region Geographic region identifier
     * @return Cache size in bytes
     */
    suspend fun getCacheSizeByRegion(region: String): Long = 0L
    
    /**
     * Get cache statistics as flow
     * 
     * @return Flow of cache statistics
     */
    fun getCacheStatisticsFlow(): Flow<CacheStatistics> = flowOf(CacheStatistics())
    
    /**
     * Check if cache entry is fresh
     * 
     * @param key Cache key
     * @return Whether the entry is fresh
     */
    suspend fun isCacheEntryFresh(key: String): Boolean = true
    
    /**
     * Get cache entry freshness
     * 
     * @param key Cache key
     * @return Freshness level
     */
    suspend fun getCacheEntryFreshness(key: String): Freshness = Freshness.FRESH
    
    /**
     * Get cache entry age
     * 
     * @param key Cache key
     * @return Age in milliseconds
     */
    suspend fun getCacheEntryAge(key: String): Long = 0L
    
    /**
     * Clear all cache
     * 
     * @param preserveUserCreatedData Whether to preserve user-created data
     * @return Number of entries cleared
     */
    suspend fun clearAllCache(preserveUserCreatedData: Boolean = true): Int = 0
    
    /**
     * Get cache hit rate
     * 
     * @return Cache hit rate (0.0 to 1.0)
     */
    suspend fun getCacheHitRate(): Double = 0.0
    
    /**
     * Get cache miss rate
     * 
     * @return Cache miss rate (0.0 to 1.0)
     */
    suspend fun getCacheMissRate(): Double = 0.0
    
    /**
     * Get cache hit count
     * 
     * @return Cache hit count
     */
    suspend fun getCacheHitCount(): Long = 0L
    
    /**
     * Get cache miss count
     * 
     * @return Cache miss count
     */
    suspend fun getCacheMissCount(): Long = 0L
    
    /**
     * Reset cache statistics
     */
    suspend fun resetCacheStatistics() {}

    /**
     * Get current number of entries in the cache
     */
    fun getCurrentSize(): Int = 0

    /**
     * Get all entries across all cache tiers
     */
    fun getAllEntries(): List<CacheEntry> = emptyList()

    /**
     * Remove entry by key
     */
    fun remove(key: String): Boolean = false

    /**
     * Get entry by CacheKey
     */
    fun get(key: CacheKey): CacheEntry? = null

    /**
     * Put entry by CacheKey
     */
    fun put(key: CacheKey, value: CacheValue): CacheEntry = CacheEntry(key = key, value = value)

    /**
     * Remove entry by CacheKey
     */
    fun remove(key: CacheKey): Boolean = false

    /**
     * Clear all cache entries
     */
    fun clear() {}
}

/**
 * Cache statistics
 * 
 * @property totalEntries Total number of cache entries
 * @property memoryEntries Number of entries in memory cache
 * @property persistentEntries Number of entries in persistent cache
 * @property totalSize Total cache size in bytes
 * @property memorySize Memory cache size in bytes
 * @property persistentSize Persistent cache size in bytes
 * @property hitCount Cache hit count
 * @property missCount Cache miss count
 * @property hitRate Cache hit rate (0.0 to 1.0)
 * @property missRate Cache miss rate (0.0 to 1.0)
 * @property oldestEntryAge Age of oldest entry in milliseconds
 * @property newestEntryAge Age of newest entry in milliseconds
 * @property averageEntryAge Average entry age in milliseconds
 */
data class CacheStatistics(
    val totalEntries: Int = 0,
    val memoryEntries: Int = 0,
    val persistentEntries: Int = 0,
    val totalSize: Long = 0L,
    val memorySize: Long = 0L,
    val persistentSize: Long = 0L,
    val hitCount: Long = 0L,
    val missCount: Long = 0L,
    val hitRate: Double = 0.0,
    val missRate: Double = 0.0,
    val oldestEntryAge: Long = 0L,
    val newestEntryAge: Long = 0L,
    val averageEntryAge: Long = 0L,
    val evictionCount: Long = 0L,
    val preservedCount: Long = 0L,
    val evictedByStrategy: Map<String, Long> = emptyMap()
)

/**
 * Cache entry
 * 
 * @property key Cache key
 * @property value Cache value
 * @property datasetId Dataset identifier
 * @property version Dataset version
 * @property region Geographic region identifier
 * @property createdAt Creation timestamp
 * @property lastAccessed Last accessed timestamp
 * @property ttl Time to live in milliseconds
 * @property freshness Freshness level
 * @property isUserCreated Whether this entry is user-created
 * @property size Size in bytes
 */
data class CacheEntry(
    val key: String,
    val value: CacheValue,
    val datasetId: String = "",
    val version: String = "",
    val region: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessed: Long = System.currentTimeMillis(),
    val ttl: Long = 0L,
    val freshness: Freshness = Freshness.FRESH,
    val isUserCreated: Boolean = false,
    val size: Long = 0L,
    val isCritical: Boolean = false,
    val isUserData: Boolean = false,
    val isNavigationState: Boolean = false,
    val isActiveDataset: Boolean = false,
    val isRollbackRequired: Boolean = false
) {
    constructor(
        key: CacheKey,
        value: CacheValue,
        isCritical: Boolean = false,
        isUserData: Boolean = false,
        isNavigationState: Boolean = false,
        isActiveDataset: Boolean = false,
        isRollbackRequired: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        lastAccessedAt: Long = System.currentTimeMillis()
    ) : this(
        key = "${key.datasetVersion}:${key.key}",
        value = value,
        version = key.datasetVersion,
        region = key.region ?: "",
        createdAt = createdAt,
        lastAccessed = lastAccessedAt,
        isCritical = isCritical,
        isUserData = isUserData,
        isNavigationState = isNavigationState,
        isActiveDataset = isActiveDataset,
        isRollbackRequired = isRollbackRequired
    )

    var lastAccessedAt: Long
        get() = lastAccessed
        set(v) { lastAccessed = v }

    fun isExpired(ttlOverride: Long? = null): Boolean {
        val effectiveTtl = ttlOverride ?: ttl
        if (effectiveTtl <= 0L) return false
        return System.currentTimeMillis() - createdAt > effectiveTtl
    }
}

/**
 * Cache value
 * 
 * @property data Cache data
 * @property metadata Cache metadata
 */
data class CacheValue(
    val data: ByteArray,
    val metadata: Map<String, Any> = emptyMap()
) {
    val value: ByteArray get() = data

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CacheValue
        if (!data.contentEquals(other.data)) return false
        if (metadata != other.metadata) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Freshness level
 * 
 * @property level Freshness level
 * @property percentage Percentage threshold
 * @property description Human-readable description
 */
enum class Freshness(
    val level: Int,
    val percentage: Double,
    val description: String
) {
    FRESH(0, 100.0, "Fresh"),
    RECENT(1, 90.0, "Recent"),
    STALE(2, 70.0, "Stale"),
    EXPIRED(3, 0.0, "Expired");
    
    companion object {
        fun fromPercentage(percentage: Double): Freshness {
            return when {
                percentage > 90.0 -> FRESH
                percentage > 70.0 -> RECENT
                percentage > 0.0 -> STALE
                else -> EXPIRED
            }
        }
    }
}

/**
 * Eviction policy
 * 
 * @property strategy Eviction strategy
 * @property maxSize Maximum size in bytes
 * @property maxEntries Maximum number of entries
 * @property maxAge Maximum age in milliseconds
 * @property preserveUserCreatedData Whether to preserve user-created data
 */
data class EvictionPolicy(
    val strategy: EvictionStrategy,
    val maxSize: Long = 100 * 1024 * 1024L, // 100 MB default
    val maxEntries: Int = 1000,
    val maxAge: Long = 7 * 24 * 60 * 60 * 1000L, // 7 days default
    val preserveUserCreatedData: Boolean = true
)

/**
 * Eviction strategy
 * 
 * @property strategy Eviction strategy
 * @property description Human-readable description
 */
enum class EvictionStrategy(
    val strategy: String,
    val description: String
) {
    LRU("LRU", "Least recently used"),
    LFU("LFU", "Least frequently used"),
    TTL("TTL", "Time to live"),
    SIZE("SIZE", "Size-based"),
    AGE("AGE", "Age-based"),
    RANDOM("RANDOM", "Random");
    
    companion object {
        fun fromStrategy(strategy: String): EvictionStrategy {
            return when {
                strategy.equals("LRU", ignoreCase = true) -> LRU
                strategy.equals("LFU", ignoreCase = true) -> LFU
                strategy.equals("TTL", ignoreCase = true) -> TTL
                strategy.equals("SIZE", ignoreCase = true) -> SIZE
                strategy.equals("AGE", ignoreCase = true) -> AGE
                strategy.equals("RANDOM", ignoreCase = true) -> RANDOM
                else -> LRU
            }
        }
    }
}

/**
 * Eviction result
 * 
 * @property success Whether eviction succeeded
 * @property evictedCount Number of entries evicted
 * @property freedBytes Bytes freed
 * @property errors List of error messages
 * @property policy Applied policy
 */
data class EvictionResult(
    val success: Boolean,
    val evictedCount: Int,
    val freedBytes: Long,
    val errors: List<String>,
    val policy: EvictionPolicy
)

/**
 * Cache key generator
 * 
 * Generates cache keys based on different strategies
 */
object CacheKeyGenerator {
    
    /**
     * Generate version-based cache key
     * 
     * @param datasetId Dataset identifier
     * @param version Dataset version
     * @param keySuffix Optional key suffix
     * @return Cache key
     */
    fun generateVersionKey(datasetId: String, version: String, keySuffix: String? = null): String {
        return if (keySuffix != null) {
            "${datasetId}_v${version}_${keySuffix}"
        } else {
            "${datasetId}_v${version}"
        }
    }
    
    /**
     * Generate geographic cache key
     * 
     * @param datasetId Dataset identifier
     * @param region Geographic region identifier
     * @param keySuffix Optional key suffix
     * @return Cache key
     */
    fun generateGeographicKey(datasetId: String, region: String, keySuffix: String? = null): String {
        return if (keySuffix != null) {
            "${datasetId}_region_${region}_${keySuffix}"
        } else {
            "${datasetId}_region_${region}"
        }
    }
    
    /**
     * Generate temporal cache key
     * 
     * @param datasetId Dataset identifier
     * @param timestamp Timestamp
     * @param keySuffix Optional key suffix
     * @return Cache key
     */
    fun generateTemporalKey(datasetId: String, timestamp: Long, keySuffix: String? = null): String {
        return if (keySuffix != null) {
            "${datasetId}_temp_${timestamp}_${keySuffix}"
        } else {
            "${datasetId}_temp_${timestamp}"
        }
    }
    
    /**
     * Generate composite cache key
     * 
     * @param datasetId Dataset identifier
     * @param version Dataset version
     * @param region Geographic region
     * @param keySuffix Optional key suffix
     * @return Cache key
     */
    fun generateCompositeKey(
        datasetId: String,
        version: String,
        region: String,
        keySuffix: String? = null
    ): String {
        return if (keySuffix != null) {
            "${datasetId}_v${version}_region_${region}_${keySuffix}"
        } else {
            "${datasetId}_v${version}_region_${region}"
        }
    }
    
    /**
     * Generate user-created cache key
     * 
     * @param userId User identifier
     * @param keySuffix Key suffix
     * @return Cache key
     */
    fun generateUserKey(userId: String, keySuffix: String): String {
        return "user_${userId}_${keySuffix}"
    }
    
    /**
     * Generate navigation cache key
     * 
     * @param routeId Route identifier
     * @param keySuffix Optional key suffix
     * @return Cache key
     */
    fun generateNavigationKey(routeId: String, keySuffix: String? = null): String {
        return if (keySuffix != null) {
            "nav_${routeId}_${keySuffix}"
        } else {
            "nav_${routeId}"
        }
    }
}