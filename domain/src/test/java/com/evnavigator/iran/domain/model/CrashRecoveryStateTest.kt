package com.evnavigator.iran.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CrashRecoveryState
 */
class CrashRecoveryStateTest {

    @Test
    fun `NOT_STARTED state is not incomplete`() {
        val state = CrashRecoveryState.notStarted("test-dataset", "1.0.0")
        assertFalse(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `DOWNLOADING state is incomplete`() {
        val state = CrashRecoveryState.downloading("test-dataset", "1.0.0")
        assertTrue(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `STAGED state is incomplete`() {
        val state = CrashRecoveryState.staged("test-dataset", "1.0.0")
        assertTrue(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `VERIFYING state is incomplete`() {
        val state = CrashRecoveryState.verifying("test-dataset", "1.0.0")
        assertTrue(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `READY state is incomplete`() {
        val state = CrashRecoveryState.ready("test-dataset", "1.0.0")
        assertTrue(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `INSTALLING state is incomplete`() {
        val state = CrashRecoveryState.installing("test-dataset", "1.0.0")
        assertTrue(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `ACTIVE state is successful`() {
        val state = CrashRecoveryState.active("test-dataset", "1.0.0")
        assertFalse(state.isIncomplete())
        assertTrue(state.isSuccessful())
        assertFalse(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `FAILED state is failed`() {
        val state = CrashRecoveryState.failed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Test error"
        )
        assertFalse(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertTrue(state.isFailed())
        assertFalse(state.requiresRollback())
    }

    @Test
    fun `ROLLBACK_REQUIRED state requires rollback`() {
        val state = CrashRecoveryState.rollbackRequired(
            datasetId = "test-dataset",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        assertFalse(state.isIncomplete())
        assertFalse(state.isSuccessful())
        assertFalse(state.isFailed())
        assertTrue(state.requiresRollback())
    }

    @Test
    fun `state with timestamp updates timestamp`() {
        val state = CrashRecoveryState.notStarted("test-dataset", "1.0.0")
        val originalTimestamp = state.timestamp

        val updatedState = state.withTimestamp(originalTimestamp + 1000)

        assertEquals(originalTimestamp + 1000, updatedState.timestamp)
    }

    @Test
    fun `state with error updates error`() {
        val state = CrashRecoveryState.notStarted("test-dataset", "1.0.0")
        val error = "Test error message"

        val updatedState = state.withError(error)

        assertEquals(error, updatedState.error)
    }

    @Test
    fun `state with previous version updates previous version`() {
        val state = CrashRecoveryState.notStarted("test-dataset", "1.0.0")
        val previousVersion = "0.9.0"

        val updatedState = state.withPreviousVersion(previousVersion)

        assertEquals(previousVersion, updatedState.previousVersion)
    }

    @Test
    fun `factory methods create correct states`() {
        val notStarted = CrashRecoveryState.notStarted("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.NOT_STARTED, notStarted.state)
        assertEquals("dataset-1", notStarted.datasetId)
        assertEquals("1.0.0", notStarted.version)
        assertEquals(CrashRecoveryState.OperationType.INSTALL, notStarted.operationType)

        val downloading = CrashRecoveryState.downloading("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.DOWNLOADING, downloading.state)

        val staged = CrashRecoveryState.staged("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.STAGED, staged.state)

        val verifying = CrashRecoveryState.verifying("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.VERIFYING, verifying.state)

        val ready = CrashRecoveryState.ready("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.READY, ready.state)

        val installing = CrashRecoveryState.installing("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.INSTALLING, installing.state)

        val active = CrashRecoveryState.active("dataset-1", "1.0.0")
        assertEquals(CrashRecoveryState.RecoveryState.ACTIVE, active.state)

        val failed = CrashRecoveryState.failed(
            datasetId = "dataset-1",
            version = "1.0.0",
            error = "Test error",
            previousVersion = "0.9.0"
        )
        assertEquals(CrashRecoveryState.RecoveryState.FAILED, failed.state)
        assertEquals("Test error", failed.error)
        assertEquals("0.9.0", failed.previousVersion)

        val rollbackRequired = CrashRecoveryState.rollbackRequired(
            datasetId = "dataset-1",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        assertEquals(CrashRecoveryState.RecoveryState.ROLLBACK_REQUIRED, rollbackRequired.state)
        assertEquals("0.9.0", rollbackRequired.previousVersion)
    }

    @Test
    fun `json serialization and deserialization`() {
        val original = CrashRecoveryState.active("test-dataset", "1.0.0")
        val json = original.toJson()
        val deserialized = CrashRecoveryState.fromJson(json)

        assertEquals(original.state, deserialized.state)
        assertEquals(original.datasetId, deserialized.datasetId)
        assertEquals(original.version, deserialized.version)
        assertEquals(original.operationType, deserialized.operationType)
        assertEquals(original.timestamp, deserialized.timestamp)
    }

    @Test
    fun `json serialization with error`() {
        val original = CrashRecoveryState.failed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Test error message"
        )
        val json = original.toJson()
        val deserialized = CrashRecoveryState.fromJson(json)

        assertEquals(original.state, deserialized.state)
        assertEquals(original.error, deserialized.error)
    }

    @Test
    fun `json serialization with previous version`() {
        val original = CrashRecoveryState.rollbackRequired(
            datasetId = "test-dataset",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        val json = original.toJson()
        val deserialized = CrashRecoveryState.fromJson(json)

        assertEquals(original.state, deserialized.state)
        assertEquals(original.previousVersion, deserialized.previousVersion)
    }

    @Test
    fun `json serialization with all fields`() {
        val original = CrashRecoveryState.failed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Test error",
            previousVersion = "0.9.0"
        )
        val json = original.toJson()
        val deserialized = CrashRecoveryState.fromJson(json)

        assertEquals(original.state, deserialized.state)
        assertEquals(original.datasetId, deserialized.datasetId)
        assertEquals(original.version, deserialized.version)
        assertEquals(original.operationType, deserialized.operationType)
        assertEquals(original.timestamp, deserialized.timestamp)
        assertEquals(original.error, deserialized.error)
        assertEquals(original.previousVersion, deserialized.previousVersion)
    }

    @Test
    fun `json deserialization handles missing fields`() {
        val json = """{"state":"ACTIVE","datasetId":"test-dataset","version":"1.0.0"}"""
        val deserialized = CrashRecoveryState.fromJson(json)

        assertEquals(CrashRecoveryState.RecoveryState.ACTIVE, deserialized.state)
        assertEquals("test-dataset", deserialized.datasetId)
        assertEquals("1.0.0", deserialized.version)
        assertNull(deserialized.error)
        assertNull(deserialized.previousVersion)
    }
}
