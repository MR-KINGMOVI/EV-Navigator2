package com.evnavigator.iran.core.routing.network

import com.evnavigator.iran.core.routing.model.RoutingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * Circuit breaker state per routing provider (V13.0 Audit — Item 32, 34, 171).
 */
enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

/**
 * Thread-safe circuit breaker for external routing providers.
 */
class RoutingCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val openCooldownMs: Long = 30_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private data class ProviderHealthState(
        val consecutiveFailures: Int = 0,
        val lastFailureTimestampMs: Long = 0L,
        val state: CircuitState = CircuitState.CLOSED
    )

    private val states = ConcurrentHashMap<String, ProviderHealthState>()

    fun currentState(providerName: String): CircuitState {
        val current = states[providerName] ?: return CircuitState.CLOSED
        if (current.state == CircuitState.OPEN) {
            val elapsed = clockMs() - current.lastFailureTimestampMs
            if (elapsed >= openCooldownMs) {
                val halfOpen = current.copy(state = CircuitState.HALF_OPEN)
                states[providerName] = halfOpen
                return CircuitState.HALF_OPEN
            }
        }
        return current.state
    }

    fun allowRequest(providerName: String): Boolean {
        return currentState(providerName) != CircuitState.OPEN
    }

    fun recordSuccess(providerName: String) {
        states[providerName] = ProviderHealthState(
            consecutiveFailures = 0,
            lastFailureTimestampMs = 0L,
            state = CircuitState.CLOSED
        )
    }

    fun recordFailure(providerName: String) {
        val now = clockMs()
        states.compute(providerName) { _, prev ->
            val failures = (prev?.consecutiveFailures ?: 0) + 1
            val nextState = if (failures >= failureThreshold) CircuitState.OPEN else CircuitState.CLOSED
            ProviderHealthState(
                consecutiveFailures = failures,
                lastFailureTimestampMs = now,
                state = nextState
            )
        }
    }

    fun reset(providerName: String) {
        states.remove(providerName)
    }
}

/**
 * Centralized, resilient HTTP transport for routing providers (V13.0 Audit — Items 28–35, 130–133, 168–169).
 *
 * Features:
 * - Safe URL & query-parameter encoding
 * - Secret masking so `key=...` or `api_key=...` never leaks into exception messages or logs
 * - Bounded response stream reading (`maxResponseBytes = 8 MB`) to prevent OOM on malformed payloads
 * - Transparent `gzip` support (`Accept-Encoding: gzip`)
 * - Bounded retry policy with exponential backoff on transient failures (`SocketTimeoutException`, HTTP 429/502/503/504)
 * - Immediate propagation of coroutine [CancellationException]
 */
object NetworkRoutingHttpClient {

    const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
    const val DEFAULT_READ_TIMEOUT_MS = 12_000
    const val DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024 // 8 MB ceiling

    private val SECRET_QUERY_REGEX = Regex(
        pattern = "([?&](?:key|api_key|apikey|token|access_token)=)([^&#\\s]+)",
        option = RegexOption.IGNORE_CASE
    )

    val sharedCircuitBreaker = RoutingCircuitBreaker()

    fun maskSecrets(raw: String): String =
        SECRET_QUERY_REGEX.replace(raw) { matchResult -> "${matchResult.groupValues[1]}***REDACTED***" }

    fun buildSafeUrl(baseUrl: String, path: String, queryParams: List<Pair<String, String>> = emptyList()): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val normalizedPath = if (path.isBlank()) "" else "/" + path.trim().trimStart('/')
        val baseWithPath = normalizedBase + normalizedPath
        // Validate base URL structure via URI
        val parsed = URI.create(baseWithPath)
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "Routing endpoint must use http or https scheme: ${maskSecrets(baseUrl)}"
        }
        if (queryParams.isEmpty()) return baseWithPath

        val encodedQuery = queryParams.joinToString("&") { (k, v) ->
            val ek = URLEncoder.encode(k, StandardCharsets.UTF_8.name())
            val ev = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
            "$ek=$ev"
        }
        val separator = if (baseWithPath.contains('?')) "&" else "?"
        return "$baseWithPath$separator$encodedQuery"
    }

    suspend fun executeGet(
        providerName: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        maxRetries: Int = 1,
        maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
        circuitBreaker: RoutingCircuitBreaker = sharedCircuitBreaker
    ): Result<String> = executeRequest(
        providerName = providerName,
        method = "GET",
        url = url,
        requestBody = null,
        headers = headers,
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs,
        maxRetries = maxRetries,
        maxResponseBytes = maxResponseBytes,
        circuitBreaker = circuitBreaker
    )

    suspend fun executePostJson(
        providerName: String,
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        maxRetries: Int = 1,
        maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
        circuitBreaker: RoutingCircuitBreaker = sharedCircuitBreaker
    ): Result<String> = executeRequest(
        providerName = providerName,
        method = "POST",
        url = url,
        requestBody = jsonBody,
        headers = headers + ("Content-Type" to "application/json; charset=utf-8"),
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs,
        maxRetries = maxRetries,
        maxResponseBytes = maxResponseBytes,
        circuitBreaker = circuitBreaker
    )

    private suspend fun executeRequest(
        providerName: String,
        method: String,
        url: String,
        requestBody: String?,
        headers: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        maxRetries: Int,
        maxResponseBytes: Int,
        circuitBreaker: RoutingCircuitBreaker
    ): Result<String> {
        if (!circuitBreaker.allowRequest(providerName)) {
            return Result.failure(
                RoutingError.ProviderUnavailable(
                    providerName = providerName,
                    reason = "Circuit breaker is OPEN for $providerName due to recent consecutive failures."
                )
            )
        }

        var attempt = 0
        var lastError: Throwable? = null

        while (attempt <= maxRetries) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "gzip")
                    for ((k, v) in headers) {
                        setRequestProperty(k, v)
                    }
                    if (requestBody != null) {
                        doOutput = true
                        outputStream.use { os ->
                            os.write(requestBody.toByteArray(StandardCharsets.UTF_8))
                            os.flush()
                        }
                    }
                }

                val statusCode = connection.responseCode
                if (statusCode in 200..299) {
                    val body = readBoundedResponseBody(connection, isErrorStream = false, maxBytes = maxResponseBytes)
                    circuitBreaker.recordSuccess(providerName)
                    return Result.success(body)
                }

                val errorSnippet = runCatching {
                    readBoundedResponseBody(connection, isErrorStream = true, maxBytes = 4096)
                }.getOrDefault("")
                val safeSnippet = maskSecrets(errorSnippet.take(240))

                if (statusCode == 429 || statusCode in listOf(502, 503, 504)) {
                    circuitBreaker.recordFailure(providerName)
                    lastError = RoutingError.ProviderUnavailable(
                        providerName = providerName,
                        reason = "$providerName HTTP $statusCode: $safeSnippet"
                    )
                    if (attempt < maxRetries) {
                        delay(250L * (attempt + 1))
                        attempt++
                        continue
                    }
                    return Result.failure(lastError)
                }

                // Non-retryable client or server error (e.g. 400, 401, 403, 404, 500)
                if (statusCode >= 500) {
                    circuitBreaker.recordFailure(providerName)
                }
                return Result.failure(
                    RoutingError.NetworkFailure(
                        message = "$providerName HTTP $statusCode (${maskSecrets(url)}): $safeSnippet"
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (ste: SocketTimeoutException) {
                circuitBreaker.recordFailure(providerName)
                lastError = RoutingError.RoutingTimeout(timeoutMs = (connectTimeoutMs + readTimeoutMs).toLong())
                if (attempt < maxRetries) {
                    delay(200L * (attempt + 1))
                    attempt++
                    continue
                }
                return Result.failure(lastError)
            } catch (t: Throwable) {
                circuitBreaker.recordFailure(providerName)
                val safeMsg = maskSecrets(t.message ?: t.javaClass.simpleName)
                return Result.failure(RoutingError.NetworkFailure("$providerName request failed: $safeMsg", t))
            } finally {
                connection?.disconnect()
            }
        }

        return Result.failure(
            lastError ?: RoutingError.NetworkFailure("$providerName request failed after $maxRetries retries")
        )
    }

    internal fun readBoundedResponseBody(
        connection: HttpURLConnection,
        isErrorStream: Boolean,
        maxBytes: Int
    ): String {
        val rawStream: InputStream = (if (isErrorStream) connection.errorStream else connection.inputStream)
            ?: return ""
        val contentEncoding = connection.contentEncoding?.lowercase() ?: ""
        val decodedStream = if (contentEncoding.contains("gzip")) GZIPInputStream(rawStream) else rawStream

        decodedStream.use { input ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var totalBytes = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > maxBytes) {
                    throw RoutingError.MalformedResponse(
                        providerName = connection.url?.host ?: "RoutingProvider",
                        reason = "Response payload exceeded maximum allowed size of $maxBytes bytes"
                    )
                }
                output.write(buffer, 0, read)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }
}
