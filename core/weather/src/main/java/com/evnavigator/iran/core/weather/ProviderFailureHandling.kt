package com.evnavigator.iran.core.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import com.evnavigator.iran.core.weather.provider.WeatherProviderError
import java.time.Instant

/**
 * Handles provider failure scenarios and implements resilience mechanisms.
 * This component ensures that the weather/traffic system gracefully handles
 * various failure modes while maintaining deterministic behavior.
 */
class ProviderFailureHandling {
    
    /**
     * Executes a provider operation with timeout and cancellation support
     *
     * @param timeoutMillis Maximum time to wait for operation
     * @param block The actual provider call to execute
     * @return Result of the operation or appropriate failure state
     */
    suspend fun <T> executeWithTimeout(
        timeoutMillis: Long = 10_000,
        block: suspend () -> T
    ): ProviderResult<T> {
        return try {
            val result = withTimeout(timeoutMillis) {
                block()
            }
            ProviderResult.Success(result)
        } catch (e: CancellationException) {
            // Properly handle cancellation - don't convert to failure
            throw e
        } catch (e: Exception) {
            when (e) {
                is TimeoutCancellationException -> {
                    ProviderResult.Failure(
                        error = WeatherProviderError.TIMEOUT,
                        message = "Provider request timed out after $timeoutMillis ms"
                    )
                }
                else -> {
                    ProviderResult.Failure(
                        error = WeatherProviderError.UNKNOWN_ERROR,
                        message = "Provider request failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Handles retry logic with exponential backoff for transient failures
     *
     * @param maxRetries Maximum number of retry attempts
     * @param baseDelayMillis Base delay between retries (ms)
     * @param block The actual provider call to execute
     * @return Result of the operation or failure after all retries exhausted
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        baseDelayMillis: Long = 1000,
        block: suspend () -> ProviderResult<T>
    ): ProviderResult<T> {
        var lastResult: ProviderResult<T> = ProviderResult.Failure(
            error = WeatherProviderError.UNKNOWN_ERROR,
            message = "No attempts made"
        )
        
        for (attempt in 0..maxRetries) {
            val result = block()
            if (result is ProviderResult.Success) {
                return result
            }
            lastResult = result
            if (result is ProviderResult.Failure) {
                if (shouldRetry(result.error) && attempt < maxRetries) {
                    val delayMillis = baseDelayMillis * (1L shl attempt)
                    delay(delayMillis)
                } else {
                    break
                }
            }
        }
        
        return lastResult
    }
    
    /**
     * Determines if an error should be retried based on its type
     *
     * @param error The provider error to check
     * @return True if retry is appropriate, false otherwise
     */
    internal fun shouldRetry(error: WeatherProviderError): Boolean {
        return when (error) {
            WeatherProviderError.TIMEOUT,
            WeatherProviderError.NETWORK_ERROR,
            WeatherProviderError.SERVER_ERROR,
            WeatherProviderError.RATE_LIMITED -> true
            
            WeatherProviderError.AUTHENTICATION_ERROR,
            WeatherProviderError.INVALID_RESPONSE,
            WeatherProviderError.UNSUPPORTED_LOCATION,
            WeatherProviderError.NO_DATA,
            WeatherProviderError.STALE_DATA,
            WeatherProviderError.PARSE_ERROR,
            WeatherProviderError.UNKNOWN_ERROR -> false
        }
    }
    
    /**
     * Implements fallback behavior when providers fail
     *
     * @param cacheState Current cache state
     * @param cachePersistence The cache persistence layer
     * @param fallbackStrategy Strategy for handling fallbacks
     * @return Fallback result or null if no fallback available
     */
    suspend fun <T> handleFallback(
        cacheState: CachePersistence.CacheState,
        cachePersistence: CachePersistence,
        fallbackStrategy: suspend () -> T?
    ): T? {
        return when (cacheState) {
            CachePersistence.CacheState.LIVE -> {
                // Already have live data, no fallback needed
                null
            }
            CachePersistence.CacheState.CACHED,
            CachePersistence.CacheState.STALE -> {
                // Use cached data as fallback
                fallbackStrategy()
            }
            CachePersistence.CacheState.EXPIRED,
            CachePersistence.CacheState.INVALID,
            CachePersistence.CacheState.UNKNOWN -> {
                // No usable fallback available
                null
            }
        }
    }
    
    /**
     * Processes partial failures in route segments
     *
     * @param segmentResults Results for individual segments
     * @return Overall result with appropriate degradation status
     */
    fun <T> processPartialFailure(
        segmentResults: List<ProviderResult<T>>
    ): ProviderResult<List<T>> {
        val successfulResults = mutableListOf<T>()
        var hasPartialData = false
        
        for (result in segmentResults) {
            when (result) {
                is ProviderResult.Success -> {
                    successfulResults.add(result.data)
                }
                is ProviderResult.Failure -> {
                    // For partial failures, we still want to return what we can
                    hasPartialData = true
                }
            }
        }
        
        // If some data succeeded but not all, return partial result
        if (successfulResults.isNotEmpty() && hasPartialData) {
            return ProviderResult.Success(successfulResults)
        } else if (successfulResults.isNotEmpty()) {
            return ProviderResult.Success(successfulResults)
        } else {
            // All failed - return failure
            return ProviderResult.Failure(
                error = WeatherProviderError.NO_DATA,
                message = "All segments failed to retrieve data"
            )
        }
    }
    
    /**
     * Determines the appropriate degradation state based on provider status
     *
     * @param providerError Error from provider if any
     * @param cacheState Current cache state
     * @param isOnline Whether network connection is available
     * @return Appropriate degradation status
     */
    fun determineDegradationStatus(
        providerError: WeatherProviderError?,
        cacheState: CachePersistence.CacheState,
        isOnline: Boolean
    ): DegradationStatus {
        return when {
            providerError == null && cacheState == CachePersistence.CacheState.LIVE -> {
                DegradationStatus.LIVE
            }
            providerError == null && cacheState != CachePersistence.CacheState.LIVE -> {
                // Data available from cache but not fresh
                DegradationStatus.CACHED
            }
            !isOnline -> {
                // No network connection, try cached data
                when (cacheState) {
                    CachePersistence.CacheState.LIVE,
                    CachePersistence.CacheState.CACHED,
                    CachePersistence.CacheState.STALE -> DegradationStatus.CACHED
                    else -> DegradationStatus.OFFLINE
                }
            }
            providerError != null -> {
                // Provider error, try fallback behavior
                when (cacheState) {
                    CachePersistence.CacheState.LIVE,
                    CachePersistence.CacheState.CACHED,
                    CachePersistence.CacheState.STALE -> DegradationStatus.CACHED
                    else -> DegradationStatus.UNKNOWN
                }
            }
            else -> DegradationStatus.UNKNOWN
        }
    }
    
    /**
     * Handles cancellation properly without converting to failure
     *
     * @param block The operation that might be cancelled
     * @return Result of operation or cancellation exception
     */
    suspend fun <T> handleCancellation(
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: CancellationException) {
            // Re-throw cancellation properly to avoid silent failures
            throw e
        }
    }
}

/**
 * Result type for provider operations that may fail
 */
sealed class ProviderResult<out T> {
    /**
     * Success result with data
     */
    data class Success<T>(val data: T) : ProviderResult<T>()
    
    /**
     * Failure result with error information
     */
    data class Failure<T>(
        val error: WeatherProviderError,
        val message: String
    ) : ProviderResult<T>()
}

/**
 * Enum representing different degradation states for navigation data
 */
enum class DegradationStatus {
    /**
     * Data is live and fresh from source
     */
    LIVE,
    
    /**
     * Data is cached but not fresh
     */
    CACHED,
    
    /**
     * Data is stale but still usable
     */
    STALE,
    
    /**
     * Data is expired and should not be used for real-time decisions
     */
    EXPIRED,
    
    /**
     * Data is invalid or corrupted
     */
    INVALID,
    
    /**
     * No data available (offline, provider failure, etc.)
     */
    OFFLINE,
    
    /**
     * Cache state is unknown due to insufficient information
     */
    UNKNOWN
}

/**
 * Extension function to delay execution with proper cancellation handling
 */
suspend fun delay(millis: Long) {
    kotlinx.coroutines.delay(millis)
}

/**
 * Context model for provider partial failure evaluation
 */
data class EnvironmentContext(
    val weather: com.evnavigator.iran.core.weather.model.WeatherObservation? = null,
    val traffic: com.evnavigator.iran.core.weather.model.TrafficObservation? = null,
    val road: Any? = null,
    val incidents: List<com.evnavigator.iran.core.weather.model.TrafficIncident> = emptyList(),
    val cacheState: CachePersistence.CacheState = CachePersistence.CacheState.LIVE,
    val timestamp: Instant = Instant.now()
)