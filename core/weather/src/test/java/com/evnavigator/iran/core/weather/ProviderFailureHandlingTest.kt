package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.provider.WeatherProviderError
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderFailureHandlingTest {

    private val providerFailureHandling = ProviderFailureHandling()

    @Test
    fun `test successful operation`() = runTest {
        val result = providerFailureHandling.executeWithTimeout {
            "success"
        }

        assertTrue(result is ProviderResult.Success)
        assertEquals("success", (result as ProviderResult.Success).data)
    }

    @Test
    fun `test timeout operation`() = runTest {
        val result = providerFailureHandling.executeWithTimeout(timeoutMillis = 1) {
            // Simulate long running operation that will timeout
            kotlinx.coroutines.delay(1000)
            "should not reach here"
        }

        assertTrue(result is ProviderResult.Failure<*>)
        assertEquals(WeatherProviderError.TIMEOUT, (result as ProviderResult.Failure<*>).error)
    }

    @Test
    fun `test retry with success`() = runTest {
        var attemptCount = 0
        val result = providerFailureHandling.executeWithRetry<String>(maxRetries = 2) {
            attemptCount++
            if (attemptCount < 3) {
                // Simulate failure on first two attempts, success on third
                ProviderResult.Failure(
                    error = WeatherProviderError.NETWORK_ERROR,
                    message = "Simulated network error"
                )
            } else {
                ProviderResult.Success("success after retries")
            }
        }

        assertTrue(result is ProviderResult.Success)
        assertEquals("success after retries", (result as ProviderResult.Success).data)
        assertEquals(3, attemptCount) // Should have attempted 3 times
    }

    @Test
    fun `test retry exhausted`() = runTest {
        val result = providerFailureHandling.executeWithRetry<String>(maxRetries = 2) {
            ProviderResult.Failure(
                error = WeatherProviderError.NETWORK_ERROR,
                message = "Network error"
            )
        }

        assertTrue(result is ProviderResult.Failure<*>)
        assertEquals(WeatherProviderError.NETWORK_ERROR, (result as ProviderResult.Failure<*>).error)
    }

    @Test
    fun `test should retry returns true for retryable errors`() {
        assertTrue(providerFailureHandling.shouldRetry(WeatherProviderError.TIMEOUT))
        assertTrue(providerFailureHandling.shouldRetry(WeatherProviderError.NETWORK_ERROR))
        assertTrue(providerFailureHandling.shouldRetry(WeatherProviderError.SERVER_ERROR))
        assertTrue(providerFailureHandling.shouldRetry(WeatherProviderError.RATE_LIMITED))
    }

    @Test
    fun `test should retry returns false for non-retryable errors`() {
        assertFalse(providerFailureHandling.shouldRetry(WeatherProviderError.AUTHENTICATION_ERROR))
        assertFalse(providerFailureHandling.shouldRetry(WeatherProviderError.INVALID_RESPONSE))
        assertFalse(providerFailureHandling.shouldRetry(WeatherProviderError.NO_DATA))
        assertFalse(providerFailureHandling.shouldRetry(WeatherProviderError.PARSE_ERROR))
    }

    @Test
    fun `test process partial failure with all successes`() = runTest {
        val results = listOf<ProviderResult<EnvironmentContext>>(
            ProviderResult.Success(EnvironmentContext(null, null, null, emptyList(), CachePersistence.CacheState.LIVE, Instant.now())),
            ProviderResult.Success(EnvironmentContext(null, null, null, emptyList(), CachePersistence.CacheState.LIVE, Instant.now()))
        )

        val result = providerFailureHandling.processPartialFailure(results)

        assertTrue(result is ProviderResult.Success)
        assertEquals(2, (result as ProviderResult.Success).data.size)
    }

    @Test
    fun `test process partial failure with mixed results`() = runTest {
        val results = listOf<ProviderResult<EnvironmentContext>>(
            ProviderResult.Success(EnvironmentContext(null, null, null, emptyList(), CachePersistence.CacheState.LIVE, Instant.now())),
            ProviderResult.Failure(WeatherProviderError.NO_DATA, "No data available")
        )

        val result = providerFailureHandling.processPartialFailure(results)

        assertTrue(result is ProviderResult.Success)
        assertEquals(1, (result as ProviderResult.Success).data.size)
    }

    @Test
    fun `test process partial failure with all failures`() = runTest {
        val results = listOf<ProviderResult<EnvironmentContext>>(
            ProviderResult.Failure(WeatherProviderError.NO_DATA, "No data available"),
            ProviderResult.Failure(WeatherProviderError.TIMEOUT, "Timeout")
        )

        val result = providerFailureHandling.processPartialFailure(results)

        assertTrue(result is ProviderResult.Failure<*>)
        assertEquals(WeatherProviderError.NO_DATA, (result as ProviderResult.Failure<*>).error)
    }

    @Test
    fun `test determine degradation status live`() {
        val status = providerFailureHandling.determineDegradationStatus(
            providerError = null,
            cacheState = CachePersistence.CacheState.LIVE,
            isOnline = true
        )
        
        assertEquals(DegradationStatus.LIVE, status)
    }

    @Test
    fun `test determine degradation status cached`() {
        val status = providerFailureHandling.determineDegradationStatus(
            providerError = null,
            cacheState = CachePersistence.CacheState.CACHED,
            isOnline = true
        )
        
        assertEquals(DegradationStatus.CACHED, status)
    }

    @Test
    fun `test determine degradation status offline`() {
        val status = providerFailureHandling.determineDegradationStatus(
            providerError = null,
            cacheState = CachePersistence.CacheState.UNKNOWN,
            isOnline = false
        )
        
        assertEquals(DegradationStatus.OFFLINE, status)
    }

    @Test
    fun `test determine degradation status with error`() {
        val status = providerFailureHandling.determineDegradationStatus(
            providerError = WeatherProviderError.NETWORK_ERROR,
            cacheState = CachePersistence.CacheState.CACHED,
            isOnline = true
        )
        
        assertEquals(DegradationStatus.CACHED, status)
    }
}