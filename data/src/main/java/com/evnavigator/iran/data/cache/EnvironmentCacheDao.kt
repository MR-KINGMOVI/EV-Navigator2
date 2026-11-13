package com.evnavigator.iran.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for environment cache with real TTL expiration semantics.
 */
@Dao
interface EnvironmentCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: EnvironmentCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<EnvironmentCacheEntity>)

    @Query("SELECT * FROM environment_cache WHERE `key` = :key AND expiresAt > :now LIMIT 1")
    suspend fun getValidEntry(key: String, now: Long = System.currentTimeMillis()): EnvironmentCacheEntity?

    @Query("SELECT * FROM environment_cache WHERE cacheType = :cacheType AND expiresAt > :now AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getValidEntriesInBounds(
        cacheType: String,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        now: Long = System.currentTimeMillis()
    ): List<EnvironmentCacheEntity>

    @Query("DELETE FROM environment_cache WHERE expiresAt <= :now")
    suspend fun pruneExpired(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM environment_cache WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    @Query("DELETE FROM environment_cache WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun deleteInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Int

    @Query("DELETE FROM environment_cache")
    suspend fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM environment_cache")
    suspend fun count(): Int
}
