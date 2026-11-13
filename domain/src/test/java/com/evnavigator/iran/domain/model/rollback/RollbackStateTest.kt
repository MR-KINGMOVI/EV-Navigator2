package com.evnavigator.iran.domain.model.rollback

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for RollbackState sealed class.
 */
class RollbackStateTest {

    @Test
    fun `Idle state is not active`() {
        val state = RollbackState.Idle()
        assertFalse(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertTrue(state.isError())
    }

    @Test
    fun `RollbackInitiated state is active`() {
        val state = RollbackState.RollbackInitiated(
            reason = "Test rollback",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertFalse(state.isError())
    }

    @Test
    fun `PreparingRollback state is active`() {
        val state = RollbackState.PreparingRollback(
            reason = "Test rollback",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertFalse(state.isError())
    }

    @Test
    fun `VerifyingPrevious state is active`() {
        val state = RollbackState.VerifyingPrevious(
            reason = "Test rollback",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertFalse(state.isError())
    }

    @Test
    fun `RollbackInProgress state is active`() {
        val state = RollbackState.RollbackInProgress(
            reason = "Test rollback",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertFalse(state.isError())
    }

    @Test
    fun `RollbackComplete state is successful`() {
        val state = RollbackState.RollbackComplete(
            reason = "Test rollback",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertFalse(state.isActive())
        assertTrue(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertFalse(state.isError())
    }

    @Test
    fun `RollbackFailed state is failed`() {
        val state = RollbackState.RollbackFailed(
            reason = "Test rollback failed",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertFalse(state.isActive())
        assertFalse(state.isSuccessful())
        assertTrue(state.isFailed())
        assertFalse(state.isRequired())
        assertTrue(state.isError())
    }

    @Test
    fun `RollbackAborted state is error`() {
        val state = RollbackState.RollbackAborted(
            reason = "Test rollback aborted",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertFalse(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertTrue(state.isError())
    }

    @Test
    fun `RollbackRequired state is required`() {
        val state = RollbackState.RollbackRequired(
            reason = "Test rollback required",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertFalse(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertTrue(state.isRequired())
        assertFalse(state.isError())
    }

    @Test
    fun `RollbackLoopDetected state is error`() {
        val state = RollbackState.RollbackLoopDetected(
            reason = "Test rollback loop detected",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertFalse(state.isActive())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.isRequired())
        assertTrue(state.isError())
    }

    @Test
    fun `getRollbackDirection returns correct pair`() {
        val state = RollbackState.RollbackInProgress(
            reason = "Test",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )

        val (from, to) = state.getRollbackDirection()
        assertEquals("v1.0.0", from)
        assertEquals("v2.0.0", to)
    }

    @Test
    fun `toMap serializes state correctly`() {
        val state = RollbackState.RollbackInProgress(
            reason = "Test rollback",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )

        val map = state.toMap()

        assertEquals("ROLLBACK_IN_PROGRESS", map["state"])
        assertEquals("Test rollback", map["reason"])
        assertEquals("v1.0.0", map["previousVersion"])
        assertEquals("v2.0.0", map["targetVersion"])
        assertTrue(map["timestamp"] is Long)
    }

    @Test
    fun `fromMap deserializes state correctly`() {
        val map = mapOf(
            "state" to "ROLLBACK_IN_PROGRESS",
            "reason" to "Test rollback",
            "previousVersion" to "v1.0.0",
            "targetVersion" to "v2.0.0",
            "timestamp" to System.currentTimeMillis()
        )

        val state = RollbackState.fromMap(map)

        assertTrue(state is RollbackState.RollbackInProgress)
        assertEquals("Test rollback", state.reason)
        assertEquals("v1.0.0", state.previousVersion)
        assertEquals("v2.0.0", state.targetVersion)
    }

    @Test
    fun `fromMap handles missing fields`() {
        val map = mapOf(
            "state" to "ROLLBACK_IN_PROGRESS",
            "reason" to "Test rollback"
        )

        val state = RollbackState.fromMap(map)

        assertTrue(state is RollbackState.RollbackInProgress)
        assertEquals("Test rollback", state.reason)
        assertNull(state.previousVersion)
        assertNull(state.targetVersion)
    }

    @Test
    fun `state transitions are valid`() {
        // Start from Idle
        var state: RollbackState = RollbackState.Idle()
        assertFalse(state.isActive())

        // Initiate rollback
        state = RollbackState.RollbackInitiated(
            reason = "Test",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())

        // Prepare rollback
        state = RollbackState.PreparingRollback(
            reason = "Test",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())

        // Verify previous
        state = RollbackState.VerifyingPrevious(
            reason = "Test",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())

        // Execute rollback
        state = RollbackState.RollbackInProgress(
            reason = "Test",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertTrue(state.isActive())

        // Complete rollback
        state = RollbackState.RollbackComplete(
            reason = "Test",
            previousVersion = "v1.0.0",
            targetVersion = "v2.0.0"
        )
        assertFalse(state.isActive())
        assertTrue(state.isSuccessful())
    }
}
