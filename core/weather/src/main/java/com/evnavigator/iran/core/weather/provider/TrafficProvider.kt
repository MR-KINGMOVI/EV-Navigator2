package com.evnavigator.iran.core.weather.provider

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.Freshness
import com.evnavigator.iran.core.weather.model.TrafficClass
import com.evnavigator.iran.core.weather.model.TrafficIncident
import com.evnavigator.iran.core.weather.model.TrafficObservation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Type of traffic information provider.
 */
enum class TrafficProviderType {
    REAL_SERVICE,
    OFFLINE_ESTIMATION,
    MOCK_TEST,
    COMPOSITE
}

/**
 * Operational availability status of the traffic provider.
 */
enum class TrafficProviderStatus {
    AVAILABLE,
    DEGRADED,
    UNAVAILABLE,
    ERROR
}

/**
 * Provenance tracking of the underlying data source for traffic measurements.
 */
enum class TrafficDataSource {
    LIVE_ONLINE_API,
    OFFLINE_HISTORICAL_ESTIMATION,
    UNKNOWN_NO_DATA,
    TEST_MOCK
}

/**
 * Standard traffic query result returned by providers.
 */
data class TrafficProviderResult(
    val observations: List<TrafficObservation>,
    val incidents: List<TrafficIncident> = emptyList(),
    val providerType: TrafficProviderType,
    val providerStatus: TrafficProviderStatus,
    val timestamp: Instant = Instant.now(),
    val fetchedAt: Instant = timestamp,
    val expiresAt: Instant = timestamp.plusSeconds(300),
    val confidence: Double = 0.0,
    val warnings: List<String> = emptyList(),
    val isOfflineFallback: Boolean = false,
    val dataSource: TrafficDataSource = TrafficDataSource.UNKNOWN_NO_DATA
)

/**
 * Provider interface for live and offline traffic observations and incidents.
 */
interface TrafficProvider {
    val providerType: TrafficProviderType
    suspend fun getTrafficForSegments(segments: List<RouteSegment>): TrafficProviderResult
    suspend fun getTrafficIncidents(coordinates: List<GeoCoordinate>): List<TrafficIncident>
    fun isAvailable(): Boolean
}

/**
 * Real online traffic provider interfacing with real-time municipal or commercial traffic telemetry.
 * When network or live service is unreachable or unconfigured, accurately reports UNAVAILABLE status
 * without fabricating data.
 */
class RealTrafficProvider(
    private val isServiceReachable: Boolean = false,
    private val liveObservationsFetcher: (suspend (List<RouteSegment>) -> List<TrafficObservation>)? = null
) : TrafficProvider {
    override val providerType: TrafficProviderType = TrafficProviderType.REAL_SERVICE

    override fun isAvailable(): Boolean = isServiceReachable

    override suspend fun getTrafficForSegments(segments: List<RouteSegment>): TrafficProviderResult {
        val now = Instant.now()
        if (!isServiceReachable || liveObservationsFetcher == null) {
            return TrafficProviderResult(
                observations = emptyList(),
                incidents = emptyList(),
                providerType = providerType,
                providerStatus = TrafficProviderStatus.UNAVAILABLE,
                timestamp = now,
                fetchedAt = now,
                expiresAt = now,
                confidence = 0.0,
                warnings = listOf("سرویس برخط اطلاعات ترافیک پیکربندی نشده یا در دسترس نیست (Live traffic service unavailable)."),
                isOfflineFallback = false,
                dataSource = TrafficDataSource.UNKNOWN_NO_DATA
            )
        }

        val liveObservations = liveObservationsFetcher.invoke(segments)
        val status = if (liveObservations.isNotEmpty()) TrafficProviderStatus.AVAILABLE else TrafficProviderStatus.UNAVAILABLE
        val confidence = if (liveObservations.isNotEmpty()) 1.0 else 0.0
        val dataSource = if (liveObservations.isNotEmpty()) TrafficDataSource.LIVE_ONLINE_API else TrafficDataSource.UNKNOWN_NO_DATA
        val warnings = if (liveObservations.isNotEmpty()) emptyList() else listOf("هیچ داده ترافیکی زنده دریافت نشد (No live observations available).")

        return TrafficProviderResult(
            observations = liveObservations,
            incidents = emptyList(),
            providerType = providerType,
            providerStatus = status,
            timestamp = now,
            fetchedAt = now,
            expiresAt = now.plusSeconds(180),
            confidence = confidence,
            warnings = warnings,
            isOfflineFallback = false,
            dataSource = dataSource
        )
    }

    override suspend fun getTrafficIncidents(coordinates: List<GeoCoordinate>): List<TrafficIncident> {
        return emptyList()
    }
}

/**
 * Offline traffic estimator that provides baseline road-class reference speeds
 * without misrepresenting speed limits as live real-time traffic conditions.
 * Explicitly reports DEGRADED status (never AVAILABLE live traffic) so UI/ETA engines
 * distinguish "no live traffic data" from "free-flow traffic".
 */
class OfflineTrafficEstimator : TrafficProvider {
    override val providerType: TrafficProviderType = TrafficProviderType.OFFLINE_ESTIMATION

    override fun isAvailable(): Boolean = true

    override suspend fun getTrafficForSegments(segments: List<RouteSegment>): TrafficProviderResult {
        val now = Instant.now()
        val observations = segments.map { segment ->
            val midLat = (segment.startCoordinate.latitude + segment.endCoordinate.latitude) / 2.0
            val midLon = (segment.startCoordinate.longitude + segment.endCoordinate.longitude) / 2.0
            val speedLimitMps = segment.speedLimitKmh.takeIf { it > 0 }?.let { it / 3.6 }

            TrafficObservation(
                coordinate = GeoCoordinate(midLat, midLon),
                timestamp = now,
                speed = speedLimitMps,
                freeFlowSpeed = speedLimitMps,
                congestionRatio = null, // Strictly null: speed limit is NOT live traffic congestion
                trafficClass = TrafficClass.UNKNOWN, // Honest unknown state: offline has no live traffic flow sensor
                travelTimeMultiplier = 1.0,
                incidents = emptyList(),
                confidence = 0.0, // Zero live confidence
                freshness = Freshness.CACHED
            )
        }

        return TrafficProviderResult(
            observations = observations,
            incidents = emptyList(),
            providerType = providerType,
            providerStatus = TrafficProviderStatus.DEGRADED,
            timestamp = now,
            fetchedAt = now,
            expiresAt = now.plusSeconds(600),
            confidence = 0.0,
            warnings = listOf("ترافیک زنده در دسترس نیست؛ صرفاً سرعت مجاز معبر مورد استفاده قرار گرفت."),
            isOfflineFallback = true,
            dataSource = TrafficDataSource.OFFLINE_HISTORICAL_ESTIMATION
        )
    }

    override suspend fun getTrafficIncidents(coordinates: List<GeoCoordinate>): List<TrafficIncident> {
        return emptyList()
    }
}

// Backwards compatibility alias for existing references
typealias OfflineTrafficProvider = OfflineTrafficEstimator

/**
 * Mock traffic provider strictly dedicated for unit and integration testing.
 */
class MockTrafficProvider(
    private val mockObservations: List<TrafficObservation> = emptyList(),
    private val mockIncidents: List<TrafficIncident> = emptyList(),
    private var isSimulatedAvailable: Boolean = true
) : TrafficProvider {
    override val providerType: TrafficProviderType = TrafficProviderType.MOCK_TEST

    override fun isAvailable(): Boolean = isSimulatedAvailable

    override suspend fun getTrafficForSegments(segments: List<RouteSegment>): TrafficProviderResult {
        return TrafficProviderResult(
            observations = mockObservations,
            incidents = mockIncidents,
            providerType = providerType,
            providerStatus = if (isSimulatedAvailable) TrafficProviderStatus.AVAILABLE else TrafficProviderStatus.UNAVAILABLE,
            timestamp = Instant.now(),
            confidence = if (isSimulatedAvailable && mockObservations.isNotEmpty()) 1.0 else 0.0,
            isOfflineFallback = false,
            dataSource = TrafficDataSource.TEST_MOCK
        )
    }

    override suspend fun getTrafficIncidents(coordinates: List<GeoCoordinate>): List<TrafficIncident> {
        return mockIncidents
    }
}

/**
 * Composite traffic provider that attempts a primary real service,
 * and if unavailable or on error, falls back to offline estimation with explicit warnings.
 */
class CompositeTrafficProvider(
    private val realProvider: TrafficProvider? = null,
    private val offlineFallback: OfflineTrafficEstimator = OfflineTrafficEstimator()
) : TrafficProvider {
    override val providerType: TrafficProviderType = TrafficProviderType.COMPOSITE

    override fun isAvailable(): Boolean = realProvider?.isAvailable() == true || offlineFallback.isAvailable()

    override suspend fun getTrafficForSegments(segments: List<RouteSegment>): TrafficProviderResult {
        if (realProvider != null && realProvider.isAvailable()) {
            try {
                val realResult = realProvider.getTrafficForSegments(segments)
                if (realResult.providerStatus == TrafficProviderStatus.AVAILABLE && realResult.observations.isNotEmpty()) {
                    return realResult
                }
            } catch (_: Exception) {
                // Return offline fallback on real provider error
            }
        }

        return offlineFallback.getTrafficForSegments(segments)
    }

    override suspend fun getTrafficIncidents(coordinates: List<GeoCoordinate>): List<TrafficIncident> {
        if (realProvider != null && realProvider.isAvailable()) {
            try {
                return realProvider.getTrafficIncidents(coordinates)
            } catch (_: Exception) {
                // fallback
            }
        }
        return offlineFallback.getTrafficIncidents(coordinates)
    }
}

/**
 * Production TrafficRepository with bounded LRU cache, request timeout, exponential backoff retry,
 * circuit breaker protection, and explicit Freshness (LIVE / CACHED / STALE / UNKNOWN) provenance tracking.
 */
class TrafficRepository(
    private val primaryProvider: TrafficProvider = CompositeTrafficProvider(),
    private val cacheTtlMillis: Long = 180_000L, // 3 minutes LIVE -> CACHED TTL
    private val staleThresholdMillis: Long = 900_000L, // 15 minutes STALE threshold
    private val minRequestIntervalMillis: Long = 2_000L, // Rate-limit protection
    private val maxRetries: Int = 2,
    private val maxCacheEntries: Int = 256,
    private val requestTimeoutMillis: Long = 5_000L,
    private val circuitBreakerFailureThreshold: Int = 3,
    private val circuitBreakerCooldownMillis: Long = 60_000L
) {
    private data class CachedTrafficEntry(
        val result: TrafficProviderResult,
        val cachedAtEpochMs: Long
    )

    // Bounded thread-safe LRU cache preventing unbounded memory growth
    private val segmentCache: MutableMap<String, CachedTrafficEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedTrafficEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTrafficEntry>?): Boolean {
                return size > maxCacheEntries
            }
        }
    )

    @Volatile
    private var lastNetworkCallEpochMs: Long = 0L

    @Volatile
    private var consecutiveFailures: Int = 0

    @Volatile
    private var circuitOpenUntilEpochMs: Long = 0L

    suspend fun getTraffic(
        segments: List<RouteSegment>,
        forceRefresh: Boolean = false
    ): TrafficProviderResult {
        if (segments.isEmpty()) {
            return TrafficProviderResult(
                observations = emptyList(),
                incidents = emptyList(),
                providerType = primaryProvider.providerType,
                providerStatus = TrafficProviderStatus.AVAILABLE,
                timestamp = Instant.now(),
                confidence = 0.0,
                dataSource = TrafficDataSource.UNKNOWN_NO_DATA
            )
        }

        val cacheKey = segments.joinToString("|") {
            "${it.id}:${"%.4f".format(it.startCoordinate.latitude)},${"%.4f".format(it.startCoordinate.longitude)}"
        }
        val nowMs = System.currentTimeMillis()
        val cached = segmentCache[cacheKey]

        if (!forceRefresh && cached != null) {
            val ageMs = nowMs - cached.cachedAtEpochMs
            if (ageMs <= cacheTtlMillis) {
                val updatedObs = cached.result.observations.map { obs ->
                    obs.copy(freshness = Freshness.CACHED)
                }
                return cached.result.copy(observations = updatedObs)
            }
        }

        // Circuit breaker check: avoid hammering an unavailable upstream service
        if (nowMs < circuitOpenUntilEpochMs) {
            if (cached != null) {
                val ageMs = nowMs - cached.cachedAtEpochMs
                val freshnessState = if (ageMs <= staleThresholdMillis) Freshness.CACHED else Freshness.STALE
                return cached.result.copy(
                    observations = cached.result.observations.map { it.copy(freshness = freshnessState) },
                    providerStatus = TrafficProviderStatus.DEGRADED,
                    warnings = cached.result.warnings + "مدار محافظت ترافیک باز است (Circuit Breaker Open)؛ استفاده از داده ذخیره‌شده."
                )
            }
            return TrafficProviderResult(
                observations = emptyList(),
                incidents = emptyList(),
                providerType = primaryProvider.providerType,
                providerStatus = TrafficProviderStatus.UNAVAILABLE,
                timestamp = Instant.now(),
                confidence = 0.0,
                warnings = listOf("سرویس ترافیک به‌طور موقت در حالت محافظت (Circuit Breaker) قرار دارد."),
                isOfflineFallback = false,
                dataSource = TrafficDataSource.UNKNOWN_NO_DATA
            )
        }

        // Enforce rate-limiting if a network request was made very recently
        val elapsedSinceLastCall = nowMs - lastNetworkCallEpochMs
        if (elapsedSinceLastCall in 0 until minRequestIntervalMillis && cached != null) {
            return cached.result.copy(
                observations = cached.result.observations.map { it.copy(freshness = Freshness.CACHED) }
            )
        }

        var attempt = 0
        var lastError: Exception? = null
        while (attempt <= maxRetries) {
            try {
                lastNetworkCallEpochMs = System.currentTimeMillis()
                val freshResult = withTimeout(requestTimeoutMillis) {
                    primaryProvider.getTrafficForSegments(segments)
                }
                consecutiveFailures = 0
                circuitOpenUntilEpochMs = 0L
                segmentCache[cacheKey] = CachedTrafficEntry(freshResult, lastNetworkCallEpochMs)
                return freshResult
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt <= maxRetries) {
                    val backoffMs = (150L * (1 shl (attempt - 1))).coerceAtMost(1_500L)
                    delay(backoffMs)
                }
            }
        }

        consecutiveFailures++
        if (consecutiveFailures >= circuitBreakerFailureThreshold) {
            circuitOpenUntilEpochMs = System.currentTimeMillis() + circuitBreakerCooldownMillis
        }

        // If all retries failed and we have a stale cache entry, return it marked as STALE / DEGRADED
        if (cached != null) {
            val ageMs = System.currentTimeMillis() - cached.cachedAtEpochMs
            val freshnessState = if (ageMs <= staleThresholdMillis) Freshness.CACHED else Freshness.STALE
            return cached.result.copy(
                observations = cached.result.observations.map { it.copy(freshness = freshnessState) },
                providerStatus = TrafficProviderStatus.DEGRADED,
                warnings = cached.result.warnings + "استفاده از داده ذخیره شده به علت خطای ارتباطی (${lastError?.message ?: "timeout"})"
            )
        }

        return TrafficProviderResult(
            observations = emptyList(),
            incidents = emptyList(),
            providerType = primaryProvider.providerType,
            providerStatus = TrafficProviderStatus.ERROR,
            timestamp = Instant.now(),
            confidence = 0.0,
            warnings = listOf("خطا در دریافت اطلاعات ترافیک: ${lastError?.message ?: "نامشخص"}"),
            isOfflineFallback = false,
            dataSource = TrafficDataSource.UNKNOWN_NO_DATA
        )
    }

    fun getCacheSize(): Int = segmentCache.size

    fun clearCache() {
        segmentCache.clear()
        consecutiveFailures = 0
        circuitOpenUntilEpochMs = 0L
    }
}
