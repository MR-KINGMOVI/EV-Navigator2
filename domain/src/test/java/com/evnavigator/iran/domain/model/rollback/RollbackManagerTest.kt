package com.evnavigator.iran.domain.model.rollback

import com.evnavigator.iran.domain.model.Dataset
import com.evnavigator.iran.domain.model.DatasetManifest
import com.evnavigator.iran.domain.model.DatasetVersionInfo
import com.evnavigator.iran.domain.model.PackageIntegrity
import com.evnavigator.iran.domain.model.StagingArea
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for RollbackManager.
 */
class RollbackManagerTest {

    private lateinit var stagingArea: StagingArea
    private lateinit var rollbackManager: RollbackManager
    private lateinit var tempDir: Path

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("rollback_test_")
        stagingArea = StagingArea(tempDir.toString())
        rollbackManager = RollbackManager(stagingArea)
        runTest { rollbackManager.initialize() }
    }

    @After
    fun teardown() {
        runTest {
            rollbackManager.clearState()
            stagingArea.cleanup()
        }
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `initiateRollback returns false when rollback is already active`() = runTest {
        // Start a rollback
        val success1 = rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        assertTrue(success1)
        assertTrue(rollbackManager.rollbackState.first().isActive())

        // Try to initiate another rollback
        val success2 = rollbackManager.initiateRollback(
            previousVersion = "v2.0.0",
            reason = "Another rollback"
        )
        assertFalse(success2)
    }

    @Test
    fun `initiateRollback returns true when no rollback is active`() = runTest {
        val success = rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        assertTrue(success)
        assertTrue(rollbackManager.rollbackState.first().isActive())
    }

    @Test
    fun `prepareRollback returns false when rollback is not initiated`() = runTest {
        val success = rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        assertFalse(success)
    }

    @Test
    fun `prepareRollback returns true when rollback is initiated`() = runTest {
        // Initiate rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Prepare rollback
        val success = rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        assertTrue(success)
        assertTrue(rollbackManager.rollbackState.first().state == RollbackState.RollbackStateType.PREPARING_ROLLBACK)
    }

    @Test
    fun `verifyPreviousVersion returns false when version not found`() = runTest {
        // Prepare rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Verify non-existent version
        val success = rollbackManager.verifyPreviousVersion("nonexistent")
        assertFalse(success)
        assertTrue(rollbackManager.rollbackState.first().isFailed())
    }

    @Test
    fun `verifyPreviousVersion returns false when manifest not found`() = runTest {
        // Create a dataset without manifest
        val dataset = Dataset(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            status = Dataset.DatasetStatus.ACTIVE,
            metadata = emptyMap(),
            integrity = null
        )

        stagingArea.stageDataset(dataset)

        // Prepare rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Verify dataset without manifest
        val success = rollbackManager.verifyPreviousVersion("v1.0.0")
        assertFalse(success)
        assertTrue(rollbackManager.rollbackState.first().isFailed())
    }

    @Test
    fun `verifyPreviousVersion returns false when integrity check fails`() = runTest {
        // Create a dataset with invalid integrity
        val manifest = DatasetManifest(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            schemaVersion = 1,
            formatVersion = 1,
            source = "test",
            creationTimestamp = System.currentTimeMillis(),
            publicationTimestamp = System.currentTimeMillis(),
            packageSize = 1024,
            checksum = "invalid_checksum",
            checksumAlgorithm = "SHA-256",
            coverage = emptyMap()
        )

        val integrity = PackageIntegrity(
            checksum = "invalid_checksum",
            algorithm = "SHA-256",
            isValid = false
        )

        val dataset = Dataset(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            status = Dataset.DatasetStatus.ACTIVE,
            metadata = emptyMap(),
            manifest = manifest,
            integrity = integrity
        )

        stagingArea.stageDataset(dataset)

        // Prepare rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Verify dataset with invalid integrity
        val success = rollbackManager.verifyPreviousVersion("v1.0.0")
        assertFalse(success)
        assertTrue(rollbackManager.rollbackState.first().isFailed())
    }

    @Test
    fun `verifyPreviousVersion returns true when version is valid`() = runTest {
        // Create a valid dataset
        val manifest = DatasetManifest(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            schemaVersion = 1,
            formatVersion = 1,
            source = "test",
            creationTimestamp = System.currentTimeMillis(),
            publicationTimestamp = System.currentTimeMillis(),
            packageSize = 1024,
            checksum = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2",
            checksumAlgorithm = "SHA-256",
            coverage = emptyMap()
        )

        val integrity = PackageIntegrity(
            checksum = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2",
            algorithm = "SHA-256",
            isValid = true
        )

        val dataset = Dataset(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            status = Dataset.DatasetStatus.ACTIVE,
            metadata = emptyMap(),
            manifest = manifest,
            integrity = integrity
        )

        stagingArea.stageDataset(dataset)

        // Prepare rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Verify valid dataset
        val success = rollbackManager.verifyPreviousVersion("v1.0.0")
        assertTrue(success)
        assertTrue(rollbackManager.rollbackState.first().state == RollbackState.RollbackStateType.VERIFYING_PREVIOUS)
    }

    @Test
    fun `executeRollback returns false when rollback is not verified`() = runTest {
        // Prepare rollback without verification
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Try to execute rollback without verification
        val success = rollbackManager.executeRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        assertFalse(success)
    }

    @Test
    fun `executeRollback returns true when rollback is successful`() = runTest {
        // Create a valid dataset
        val manifest = DatasetManifest(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            schemaVersion = 1,
            formatVersion = 1,
            source = "test",
            creationTimestamp = System.currentTimeMillis(),
            publicationTimestamp = System.currentTimeMillis(),
            packageSize = 1024,
            checksum = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2",
            checksumAlgorithm = "SHA-256",
            coverage = emptyMap()
        )

        val integrity = PackageIntegrity(
            checksum = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2",
            algorithm = "SHA-256",
            isValid = true
        )

        val dataset = Dataset(
            id = "test_dataset",
            version = "v1.0.0",
            type = Dataset.DatasetType.MAP,
            status = Dataset.DatasetStatus.ACTIVE,
            metadata = emptyMap(),
            manifest = manifest,
            integrity = integrity
        )

        stagingArea.stageDataset(dataset)

        // Prepare and verify rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.verifyPreviousVersion("v1.0.0")

        // Execute rollback
        val success = rollbackManager.executeRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        assertTrue(success)
        assertTrue(rollbackManager.rollbackState.first().isSuccessful())
    }

    @Test
    fun `abortRollback aborts active rollback`() = runTest {
        // Initiate rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )
        rollbackManager.prepareRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Abort rollback
        rollbackManager.abortRollback("Rollback aborted by user")

        val state = rollbackManager.rollbackState.first()
        assertTrue(state is RollbackState.RollbackAborted)
        assertEquals("Rollback aborted by user", state.reason)
    }

    @Test
    fun `detectRollbackLoop returns false when history is insufficient`() {
        val loopDetected = rollbackManager.detectRollbackLoop("v1.0.0")
        assertFalse(loopDetected)
    }

    @Test
    fun `detectRollbackLoop returns true when loop is detected`() {
        // Simulate rollback history
        rollbackManager.recordRollbackOperation(
            previousVersion = "v2.0.0",
            targetVersion = "v1.0.0",
            reason = "Test",
            success = true
        )
        rollbackManager.recordRollbackOperation(
            previousVersion = "v3.0.0",
            targetVersion = "v2.0.0",
            reason = "Test",
            success = true
        )
        rollbackManager.recordRollbackOperation(
            previousVersion = "v1.0.0",
            targetVersion = "v3.0.0",
            reason = "Test",
            success = true
        )

        val loopDetected = rollbackManager.detectRollbackLoop("v2.0.0")
        assertTrue(loopDetected)
    }

    @Test
    fun `getRollbackStatistics returns correct statistics`() {
        // Add some rollback operations
        rollbackManager.recordRollbackOperation(
            previousVersion = "v2.0.0",
            targetVersion = "v1.0.0",
            reason = "Test",
            success = true
        )
        rollbackManager.recordRollbackOperation(
            previousVersion = "v3.0.0",
            targetVersion = "v2.0.0",
            reason = "Test",
            success = true
        )
        rollbackManager.recordRollbackOperation(
            previousVersion = "v4.0.0",
            targetVersion = "v3.0.0",
            reason = "Test",
            success = false
        )
        rollbackManager.recordRollbackOperation(
            previousVersion = "v5.0.0",
            targetVersion = "v4.0.0",
            reason = "Test",
            success = false
        )

        val stats = rollbackManager.getRollbackStatistics()

        assertEquals(4, stats.totalRollbacks)
        assertEquals(2, stats.successfulRollbacks)
        assertEquals(2, stats.failedRollbacks)
        assertEquals(2, stats.recentFailures.size)
    }

    @Test
    fun `history is immutable`() {
        val initialHistory = rollbackManager.history.toList()
        assertEquals(0, initialHistory.size)

        rollbackManager.recordRollbackOperation(
            previousVersion = "v2.0.0",
            targetVersion = "v1.0.0",
            reason = "Test",
            success = true
        )

        val updatedHistory = rollbackManager.history.toList()
        assertEquals(1, updatedHistory.size)
        assertEquals(0, initialHistory.size)
    }

    @Test
    fun `state is persisted to disk`() = runTest {
        // Initiate rollback
        rollbackManager.initiateRollback(
            previousVersion = "v1.0.0",
            reason = "Test rollback"
        )

        // Verify state is persisted
        val stateFile = rollbackManager.rollbackStateFile.toFile()
        assertTrue(stateFile.exists())

        // Create new manager and verify state is loaded
        val newManager = RollbackManager(stagingArea)
        newManager.initialize()

        val loadedState = newManager.rollbackState.first()
        assertTrue(loadedState is RollbackState.RollbackInitiated)
        assertEquals("Test rollback", loadedState.reason)
    }

    @Test
    fun `history is persisted to disk`() = runTest {
        // Add rollback operations
        rollbackManager.recordRollbackOperation(
            previousVersion = "v2.0.0",
            targetVersion = "v1.0.0",
            reason = "Test",
            success = true
        )

        // Verify history is persisted
        val historyFile = rollbackManager.rollbackHistoryFile.toFile()
        assertTrue(historyFile.exists())

        // Create new manager and verify history is loaded
        val newManager = RollbackManager(stagingArea)
        newManager.initialize()

        val loadedHistory = newManager.history
        assertEquals(1, loadedHistory.size)
    }

    @Test
    fun `clearState clears all state`() = runTest {
        // Add rollback operations
        rollbackManager.recordRollbackOperation(
            previousVersion = "v2.0.0",
            targetVersion = "v1.0.0",
            reason = "Test",
            success = true
        )

        // Clear state
        rollbackManager.clearState()

        val state = rollbackManager.rollbackState.first()
        assertTrue(state is RollbackState.Idle)

        val history = rollbackManager.history
        assertEquals(0, history.size)
    }
}
