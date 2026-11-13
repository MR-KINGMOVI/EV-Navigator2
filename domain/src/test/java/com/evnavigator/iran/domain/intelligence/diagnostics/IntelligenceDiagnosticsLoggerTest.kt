package com.evnavigator.iran.domain.intelligence.diagnostics

import org.junit.Assert.*
import org.junit.Test

class IntelligenceDiagnosticsLoggerTest {

    private val logger = IntelligenceDiagnosticsLogger()

    @Test
    fun testSuccessfulExecutionLogged() {
        val result = logger.runSafely(CalculationStage.PHYSICS_ORCHESTRATION) {
            42 * 2
        }

        assertTrue(result.isSuccess)
        assertEquals(84, result.getOrNull())

        val events = logger.getRecentEvents()
        assertEquals(1, events.size)
        assertTrue(events[0].isSuccess)
        assertEquals(CalculationStage.PHYSICS_ORCHESTRATION, events[0].stage)
        assertNull(events[0].failureCategory)
    }

    @Test
    fun testFailureClassificationForInvalidRange() {
        val result = logger.runSafely(CalculationStage.INPUT_VALIDATION) {
            throw IllegalArgumentException("Battery capacity cannot be negative (-50)")
        }

        assertTrue(result.isFailure)
        val events = logger.getRecentEvents()
        assertEquals(1, events.size)
        assertFalse(events[0].isSuccess)
        assertEquals(FailureCategory.PHYSICALLY_INVALID_RANGE, events[0].failureCategory)
        assertTrue(events[0].sanitizedSummary.contains("cannot be negative"))
    }

    @Test
    fun testRedactionCleansesCoordinatesAndSecrets() {
        val rawEvent = IntelligenceDiagnosticEvent(
            stage = CalculationStage.INPUT_VALIDATION,
            isSuccess = false,
            durationMicros = 100,
            sanitizedSummary = "Failed at location 35.689197, 51.388974 with key: secret12345"
        )

        logger.logEvent(rawEvent)
        val stored = logger.getRecentEvents().first()

        assertFalse("Raw coordinate must not be logged", stored.sanitizedSummary.contains("35.689197"))
        assertFalse("Secret key must not be logged", stored.sanitizedSummary.contains("secret12345"))
        assertTrue(stored.sanitizedSummary.contains("[COORDINATE]"))
        assertTrue(stored.sanitizedSummary.contains("[REDACTED]"))
    }

    @Test
    fun testDisabledDiagnosticsRecordsZeroEvents() {
        logger.isEnabled = false
        logger.runSafely(CalculationStage.ROUTE_SCORING) {
            "success"
        }

        assertTrue("Disabled logger must have empty buffer", logger.getRecentEvents().isEmpty())
    }
}
