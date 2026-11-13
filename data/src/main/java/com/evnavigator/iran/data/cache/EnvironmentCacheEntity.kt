package com.evnavigator.iran.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Room entity representing cached environment (weather/traffic) observations and forecasts.
 * Enforces real TTL expiration timestamps to avoid serving expired context.
 */
@Entity(
    tableName = "environment_cache",
    indices = [
        Index(value = ["cacheType"]),
        Index(value = ["expiresAt"]),
        Index(value = ["latitude", "longitude"])
    ]
)
data class EnvironmentCacheEntity(
    @PrimaryKey
    val key: String,
    val cacheType: String,
    val latitude: Double,
    val longitude: Double,
    val payloadJson: String,
    val cachedAt: Long,
    val expiresAt: Long
)
