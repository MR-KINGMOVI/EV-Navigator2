package com.evnavigator.iran.domain.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Unit tests for CrashRecoveryManager
 */
class CrashRecoveryManagerTest {

    private lateinit var tempDir: File
    private lateinit var manager: CrashRecoveryManager

    @Before
    fun setup() {
        tempDir = File.createTempFile("recovery-test-", ".tmp")
        tempDir.delete()
        tempDir.mkdirs()
        manager = CrashRecoveryManager(tempDir.absolutePath)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `initialization with no recovery state`() = runTest {
        manager.initialize()
        assertNull(manager.getCurrentState())
        assertFalse(manager.hasIncompleteOperation())
        assertFalse(manager.requiresRollback())
    }

    @Test
    fun `initialization with no recovery file`() = runTest {
        manager.initialize()
        assertNull(manager.getCurrentState())
    }

    @Test
    fun `startInstallation creates new state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.NOT_STARTED, state?.state)
        assertEquals("test-dataset", state?.datasetId)
        assertEquals("1.0.0", state?.version)
    }

    @Test
    fun `markDownloading updates state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markDownloading("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.DOWNLOADING, state?.state)
    }

    @Test
    fun `markStaged updates state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markStaged("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.STAGED, state?.state)
    }

    @Test
    fun `markVerifying updates state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markVerifying("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.VERIFYING, state?.state)
    }

    @Test
    fun `markReady updates state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markReady("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.READY, state?.state)
    }

    @Test
    fun `markInstalling updates state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markInstalling("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.INSTALLING, state?.state)
    }

    @Test
    fun `markActive marks installation successful`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markActive("test-dataset", "1.0.0")
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertTrue(state?.isSuccessful() ?: false)
        assertFalse(state?.isIncomplete() ?: true)
    }

    @Test
    fun `markFailed marks installation failed`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markFailed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Test error",
            previousVersion = "0.9.0"
        )
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertTrue(state?.isFailed() ?: false)
        assertEquals("Test error", state?.error)
        assertEquals("0.9.0", state?.previousVersion)
    }

    @Test
    fun `markRollbackRequired marks rollback needed`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markRollbackRequired(
            datasetId = "test-dataset",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        
        val state = manager.getCurrentState()
        assertNotNull(state)
        assertTrue(state?.requiresRollback() ?: false)
        assertEquals("0.9.0", state?.previousVersion)
    }

    @Test
    fun `clearRecoveryState removes state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markActive("test-dataset", "1.0.0")
        
        manager.clearRecoveryState()
        
        assertNull(manager.getCurrentState())
        assertFalse(manager.hasIncompleteOperation())
        assertFalse(manager.requiresRollback())
    }

    @Test
    fun `hasIncompleteOperation returns true for incomplete states`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        assertTrue(manager.hasIncompleteOperation())
        
        manager.markDownloading("test-dataset", "1.0.0")
        assertTrue(manager.hasIncompleteOperation())
        
        manager.markStaged("test-dataset", "1.0.0")
        assertTrue(manager.hasIncompleteOperation())
        
        manager.markVerifying("test-dataset", "1.0.0")
        assertTrue(manager.hasIncompleteOperation())
        
        manager.markReady("test-dataset", "1.0.0")
        assertTrue(manager.hasIncompleteOperation())
        
        manager.markInstalling("test-dataset", "1.0.0")
        assertTrue(manager.hasIncompleteOperation())
        
        manager.markActive("test-dataset", "1.0.0")
        assertFalse(manager.hasIncompleteOperation())
    }

    @Test
    fun `requiresRollback returns true for rollback state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markRollbackRequired(
            datasetId = "test-dataset",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        
        assertTrue(manager.requiresRollback())
    }

    @Test
    fun `requiresRollback returns false for other states`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markDownloading("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markStaged("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markVerifying("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markReady("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markInstalling("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markActive("test-dataset", "1.0.0")
        assertFalse(manager.requiresRollback())
        
        manager.markFailed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Test error"
        )
        assertFalse(manager.requiresRollback())
    }

    @Test
    fun `getPreviousVersion returns previous version for rollback state`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markRollbackRequired(
            datasetId = "test-dataset",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        
        assertEquals("0.9.0", manager.getPreviousVersion())
    }

    @Test
    fun `getPreviousVersion returns null for other states`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markDownloading("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markStaged("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markVerifying("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markReady("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markInstalling("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markActive("test-dataset", "1.0.0")
        assertNull(manager.getPreviousVersion())
        
        manager.markFailed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Test error"
        )
        assertNull(manager.getPreviousVersion())
    }

    @Test
    fun `state persists to disk`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markDownloading("test-dataset", "1.0.0")
        
        // Create a new manager instance to simulate restart
        val newManager = CrashRecoveryManager(tempDir.absolutePath)
        newManager.initialize()
        
        val state = newManager.getCurrentState()
        assertNotNull(state)
        assertEquals(CrashRecoveryState.RecoveryState.DOWNLOADING, state?.state)
        assertEquals("test-dataset", state?.datasetId)
        assertEquals("1.0.0", state?.version)
    }

    @Test
    fun `state persists with error`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markFailed(
            datasetId = "test-dataset",
            version = "1.0.0",
            error = "Download failed",
            previousVersion = "0.9.0"
        )
        
        val newManager = CrashRecoveryManager(tempDir.absolutePath)
        newManager.initialize()
        
        val state = newManager.getCurrentState()
        assertNotNull(state)
        assertTrue(state?.isFailed() ?: false)
        assertEquals("Download failed", state?.error)
        assertEquals("0.9.0", state?.previousVersion)
    }

    @Test
    fun `state persists with rollback required`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markRollbackRequired(
            datasetId = "test-dataset",
            version = "1.0.0",
            previousVersion = "0.9.0"
        )
        
        val newManager = CrashRecoveryManager(tempDir.absolutePath)
        newManager.initialize()
        
        val state = newManager.getCurrentState()
        assertNotNull(state)
        assertTrue(state?.requiresRollback() ?: false)
        assertEquals("0.9.0", state?.previousVersion)
    }

    @Test
    fun `successful state clears recovery file`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markActive("test-dataset", "1.0.0")
        
        val recoveryFile = File(tempDir, "recovery_state.json")
        assertFalse(recoveryFile.exists())
        
        val newManager = CrashRecoveryManager(tempDir.absolutePath)
        newManager.initialize()
        
        assertNull(newManager.getCurrentState())
    }

    @Test
    fun `handles corrupted recovery file`() = runTest {
        val corruptedFile = File(tempDir, "recovery_state.json")
        corruptedFile.writeText("{invalid json}")
        
        val newManager = CrashRecoveryManager(tempDir.absolutePath)
        newManager.initialize()
        
        assertNull(newManager.getCurrentState())
    }

    @Test
    fun `handles missing recovery file on initialization`() = runTest {
        val newManager = CrashRecoveryManager(tempDir.absolutePath)
        newManager.initialize()
        
        assertNull(newManager.getCurrentState())
    }

    @Test
    fun `state flow emits updates`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        manager.markDownloading("test-dataset", "1.0.0")
        var emittedState: CrashRecoveryState? = null
        
        manager.recoveryState.first { state ->
            emittedState = state
            state?.state == CrashRecoveryState.RecoveryState.DOWNLOADING
        }
        
        assertNotNull(emittedState)
        assertEquals(CrashRecoveryState.RecoveryState.DOWNLOADING, emittedState?.state)
    }

    @Test
    fun `state flow emits null after clear`() = runTest {
        manager.startInstallation("test-dataset", "1.0.0")
        
        manager.recoveryState.first { state ->
            state != null
        }
        
        manager.clearRecoveryState()
        
        manager.recoveryState.first { state ->
            state == null
        }
    }
}
