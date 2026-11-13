package com.evnavigator.iran.core.instrumentation

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Sanitized metric record that preserves user privacy (no exact coordinates or personal routes).
 */
data class MetricEvent(
    val operation: String,
    val durationMs: Long,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thread-safe, bounded metrics collector for offline operations.
 *
 * Guarantees:
 * - Constant bounded memory (circular buffer of 200 events)
 * - Zero privacy leaks (no raw coordinates, tokens, or personal identifiers)
 * - Atomic counters for high-frequency operations
 */
class OfflineOperationMetricsCollector(
    private val maxEventHistory: Int = 200
) {

    val routingExecutions = AtomicInteger(0)
    val totalRoutingDurationMs = AtomicLong(0)
    val elevationQueries = AtomicInteger(0)
    val cacheHits = AtomicInteger(0)
    val cacheMisses = AtomicInteger(0)

    private val eventHistory = ConcurrentLinkedQueue<MetricEvent>()

    /**
     * Executes block and records latency and outcome.
     */
    inline fun <T> measure(operationName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        var success = true
        try {
            return block()
        } catch (e: Throwable) {
            success = false
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            recordEvent(operationName, duration, success)
        }
    }

    fun recordEvent(operation: String, durationMs: Long, success: Boolean) {
        when (operation) {
            "ROUTING" -> {
                routingExecutions.incrementAndGet()
                totalRoutingDurationMs.addAndGet(durationMs)
            }
            "ELEVATION" -> elevationQueries.incrementAndGet()
            "CACHE_HIT" -> cacheHits.incrementAndGet()
            "CACHE_MISS" -> cacheMisses.incrementAndGet()
        }

        while (eventHistory.size >= maxEventHistory) {
            eventHistory.poll()
        }
        eventHistory.offer(MetricEvent(operation, durationMs, success))
    }

    fun getAverageRoutingDurationMs(): Double {
        val count = routingExecutions.get()
        return if (count > 0) totalRoutingDurationMs.get().toDouble() / count else 0.0
    }

    fun getRecentEvents(): List<MetricEvent> = eventHistory.toList()

    fun reset() {
        routingExecutions.set(0)
        totalRoutingDurationMs.set(0)
        elevationQueries.set(0)
        cacheHits.set(0)
        cacheMisses.set(0)
        eventHistory.clear()
    }
}
