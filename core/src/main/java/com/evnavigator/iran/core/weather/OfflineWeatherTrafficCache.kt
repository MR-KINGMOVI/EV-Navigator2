package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.WeatherSnapshot
import com.evnavigator.iran.domain.model.weather.TrafficData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Freshness state of cached environmental conditions.
 */
enum class EnvironmentalFreshnessState {
    LIVE,
    CACHED_FRESH,
    CACHED_STALE,
    EXPIRED_OR_OFFLINE
}

/**
 * Result wrapper for offline cache lookups.
 */
sealed class CacheLookupResult<out T> {
    data class Fresh<T>(val data: T, val ageMillis: Long) : CacheLookupResult<T>()
    data class Stale<T>(val data: T, val ageMillis: Long, val warning: String) : CacheLookupResult<T>()
    object UnavailableOffline : CacheLookupResult<Nothing>()
}

/**
 * Cache entry holding metadata and payload.
 */
private data class CacheEntry<T>(
    val payload: T,
    val observationTimeMillis: Long,
    val retrievalTimeMillis: Long
)

/**
 * Thread-safe, bounded offline cache for weather and traffic conditions.
 *
 * Implements strict degradation rules:
 * - Stale data is explicitly tagged as STALE and never promoted to LIVE
 * - Missing traffic is NEVER assumed to be free-flow/clear roads
 * - Missing weather is NEVER assumed to be ideal/standard conditions
 * - Bounded memory using max capacity eviction
 */
class OfflineWeatherTrafficCache(
    private val maxWeatherEntries: Int = 100,
    private val maxTrafficEntries: Int = 500,
    private val weatherFreshTtlMillis: Long = 30 * 60 * 1000L,       // 30 min fresh
    private val weatherStaleTtlMillis: Long = 3 * 3600 * 1000L,      // 3 hours max stale
    private val trafficFreshTtlMillis: Long = 5 * 60 * 1000L,        // 5 min fresh
    private val trafficStaleTtlMillis: Long = 20 * 60 * 1000L        // 20 min max stale
) {

    private val weatherCache = ConcurrentHashMap<String, CacheEntry<WeatherSnapshot>>()
    private val trafficCache = ConcurrentHashMap<String, CacheEntry<TrafficData>>()

    private fun coordKey(coord: GeoCoordinate): String {
        // Round to ~0.05 degrees (~5 km spatial resolution)
        val latBucket = (coord.latitude * 20).toInt()
        val lonBucket = (coord.longitude * 20).toInt()
        return "$latBucket:$lonBucket"
    }

    /**
     * Stores weather snapshot with current retrieval timestamp.
     */
    fun putWeather(coordinate: GeoCoordinate, weather: WeatherSnapshot, currentTimeMillis: Long = System.currentTimeMillis()) {
        if (weatherCache.size >= maxWeatherEntries) {
            val oldestKey = weatherCache.minByOrNull { it.value.retrievalTimeMillis }?.key
            if (oldestKey != null) weatherCache.remove(oldestKey)
        }
        val key = coordKey(coordinate)
        weatherCache[key] = CacheEntry(
            payload = weather,
            observationTimeMillis = weather.timestamp,
            retrievalTimeMillis = currentTimeMillis
        )
    }

    /**
     * Retrieves weather for coordinate with explicit freshness classification.
     */
    fun getWeather(
        coordinate: GeoCoordinate,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): CacheLookupResult<WeatherSnapshot> {
        val key = coordKey(coordinate)
        val entry = weatherCache[key] ?: return CacheLookupResult.UnavailableOffline

        val age = currentTimeMillis - entry.retrievalTimeMillis
        return when {
            age < 0 -> CacheLookupResult.UnavailableOffline // Clock skew / invalid
            age <= weatherFreshTtlMillis -> CacheLookupResult.Fresh(entry.payload, age)
            age <= weatherStaleTtlMillis -> CacheLookupResult.Stale(
                entry.payload,
                age,
                "Weather data is stale (${age / 60000}m old). Predictions may have higher uncertainty."
            )
            else -> {
                weatherCache.remove(key)
                CacheLookupResult.UnavailableOffline
            }
        }
    }

    /**
     * Stores traffic data for a road segment.
     */
    fun putTraffic(segmentId: String, traffic: TrafficData, currentTimeMillis: Long = System.currentTimeMillis()) {
        if (trafficCache.size >= maxTrafficEntries) {
            val oldestKey = trafficCache.minByOrNull { it.value.retrievalTimeMillis }?.key
            if (oldestKey != null) trafficCache.remove(oldestKey)
        }
        trafficCache[segmentId] = CacheEntry(
            payload = traffic,
            observationTimeMillis = traffic.timestamp.time,
            retrievalTimeMillis = currentTimeMillis
        )
    }

    /**
     * Retrieves traffic data with explicit degradation on expiry.
     */
    fun getTraffic(
        segmentId: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): CacheLookupResult<TrafficData> {
        val entry = trafficCache[segmentId] ?: return CacheLookupResult.UnavailableOffline

        val age = currentTimeMillis - entry.retrievalTimeMillis
        return when {
            age < 0 -> CacheLookupResult.UnavailableOffline
            age <= trafficFreshTtlMillis -> CacheLookupResult.Fresh(entry.payload, age)
            age <= trafficStaleTtlMillis -> CacheLookupResult.Stale(
                entry.payload,
                age,
                "Traffic data is stale (${age / 60000}m old). Speed estimates may differ from actual road conditions."
            )
            else -> {
                trafficCache.remove(segmentId)
                CacheLookupResult.UnavailableOffline
            }
        }
    }

    fun clear() {
        weatherCache.clear()
        trafficCache.clear()
    }
}
