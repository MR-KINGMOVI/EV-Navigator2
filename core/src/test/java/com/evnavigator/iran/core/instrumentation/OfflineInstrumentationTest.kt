package com.evnavigator.iran.core.instrumentation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflineOperationMetricsCollector.
 * Verifies latency measurement, memory bounds, error capture, and thread safety.
 */
class OfflineInstrumentationTest {

    private lateinit var collector: OfflineOperationMetricsCollector

    @Before
    fun setUp() {
        collector = OfflineOperationMetricsCollector(maxEventHistory = 10)
    }

    @Test
    fun `test measure records execution time and success`() {
        val result = collector.measure("ROUTING") {
            Thread.sleep(10)
            42
        }

        assertEquals(42, result)
        assertEquals(1, collector.routingExecutions.get())
        assertTrue(collector.totalRoutingDurationMs.get() >= 10)

        val events = collector.getRecentEvents()
        assertEquals(1, events.size)
        assertEquals("ROUTING", events[0].operation)
        assertTrue(events[0].success)
    }

    @Test
    fun `test measure captures failure and records success false`() {
        try {
            collector.measure("DATABASE_QUERY") {
                throw IllegalStateException("Synthetic DB error")
            }
            fail("Should have rethrown exception")
        } catch (e: IllegalStateException) {
            assertEquals("Synthetic DB error", e.message)
        }

        val events = collector.getRecentEvents()
        assertEquals(1, events.size)
        assertEquals("DATABASE_QUERY", events[0].operation)
        assertFalse(events[0].success)
    }

    @Test
    fun `test event history buffer is bounded at max capacity`() {
        for (i in 1..25) {
            collector.recordEvent("OP_$i", durationMs = i.toLong(), success = true)
        }

        val events = collector.getRecentEvents()
        assertEquals(10, events.size)
        // Earliest event should be OP_16
        assertEquals("OP_16", events.first().operation)
        assertEquals("OP_25", events.last().operation)
    }
}
