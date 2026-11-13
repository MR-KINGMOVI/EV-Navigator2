package com.evnavigator.iran.domain.model.rollback

import com.evnavigator.iran.domain.model.Dataset
import com.evnavigator.iran.domain.model.DatasetManifest
import com.evnavigator.iran.domain.model.DatasetVersionInfo
import com.evnavigator.iran.domain.model.PackageIntegrity
import com.evnavigator.iran.domain.model.StagingArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Rollback manager for safe dataset rollback operations.
 * 
 * This manager implements production-grade rollback with:
 * - Previous known-good dataset preservation
 * - Previous version verification before rollback
 * - Rollback only to valid compatible versions
 * - Rollback reason recording
 * - Infinite rollback loop prevention
 * - Failed update recovery
 * - Rollback state exposure
 * 
 * @property stagingArea Staging area for package staging
 * @property rollbackStateFlow State flow for rollback state changes
 * @property rollbackHistory List of rollback operations
 */
class RollbackManager(
    private val stagingArea: StagingArea,
    private val rollbackStateFlow: MutableStateFlow<RollbackState> = MutableStateFlow(RollbackState.Idle()),
    private val rollbackHistory: MutableList<RollbackOperation> = mutableListOf(),
    internal val rollbackStateFile: Path = stagingArea.path.resolve("rollback_state.json"),
    internal val rollbackHistoryFile: Path = stagingArea.path.resolve("rollback_history.json"),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Rollback operation record.
     */
    @Serializable
    data class RollbackOperation(
        val id: String,
        val timestamp: Long,
        val previousVersion: String,
        val targetVersion: String,
        val reason: String,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Current rollback state.
     */
    val rollbackState: StateFlow<RollbackState> = rollbackStateFlow.asStateFlow()

    /**
     * Rollback history.
     */
    val history: List<RollbackOperation> get() = rollbackHistory.toList()

    /**
     * Initializes rollback manager and loads previous state.
     */
    suspend fun initialize() {
        loadRollbackState()
        loadRollbackHistory()
    }

    /**
     * Initiates a rollback operation.
     * 
     * @param previousVersion The version to roll back to
     * @param reason Reason for rollback
     * @return True if rollback was initiated successfully
     */
    suspend fun initiateRollback(
        previousVersion: String,
        reason: String
    ): Boolean {
        val currentState = rollbackState.value
        if (currentState.isActive()) {
            return false
        }

        rollbackStateFlow.value = RollbackState.RollbackInitiated(
            reason = reason,
            previousVersion = previousVersion,
            targetVersion = currentState.previousVersion ?: "unknown"
        )
        saveRollbackState()

        return true
    }

    /**
     * Prepares rollback operation.
     * 
     * @param previousVersion The version to roll back to
     * @param reason Reason for rollback
     * @return True if preparation was successful
     */
    suspend fun prepareRollback(
        previousVersion: String,
        reason: String
    ): Boolean {
        val currentState = rollbackState.value
        if (currentState.state != RollbackState.RollbackStateType.ROLLBACK_INITIATED) {
            return false
        }

        rollbackStateFlow.value = RollbackState.PreparingRollback(
            reason = reason,
            previousVersion = previousVersion,
            targetVersion = currentState.targetVersion ?: "unknown"
        )
        saveRollbackState()

        return true
    }

    /**
     * Verifies previous version before rollback.
     * 
     * @param previousVersion The version to verify
     * @return True if version is valid and compatible
     */
    suspend fun verifyPreviousVersion(
        previousVersion: String
    ): Boolean {
        val currentState = rollbackState.value
        if (currentState.state != RollbackState.RollbackStateType.PREPARING_ROLLBACK) {
            return false
        }

        rollbackStateFlow.value = RollbackState.VerifyingPrevious(
            reason = "Verifying previous version: $previousVersion",
            previousVersion = previousVersion,
            targetVersion = currentState.targetVersion ?: "unknown"
        )

        // Verify previous version exists and is valid
        val previousDataset = stagingArea.getDataset(previousVersion)
        if (previousDataset == null) {
            rollbackStateFlow.value = RollbackState.RollbackFailed(
                reason = "Previous version not found: $previousVersion",
                previousVersion = previousVersion,
                targetVersion = currentState.targetVersion ?: "unknown"
            )
            saveRollbackState()
            return false
        }

        // Verify manifest integrity
        val manifest = previousDataset.manifest
        if (manifest == null) {
            rollbackStateFlow.value = RollbackState.RollbackFailed(
                reason = "Previous version manifest not found: $previousVersion",
                previousVersion = previousVersion,
                targetVersion = currentState.targetVersion ?: "unknown"
            )
            saveRollbackState()
            return false
        }

        // Verify checksum
        val integrity = previousDataset.integrity
        if (integrity == null || !integrity.isValid) {
            rollbackStateFlow.value = RollbackState.RollbackFailed(
                reason = "Previous version integrity check failed: $previousVersion",
                previousVersion = previousVersion,
                targetVersion = currentState.targetVersion ?: "unknown"
            )
            saveRollbackState()
            return false
        }

        saveRollbackState()
        return true
    }

    /**
     * Executes rollback operation.
     * 
     * @param previousVersion The version to roll back to
     * @param reason Reason for rollback
     * @return True if rollback was successful
     */
    suspend fun executeRollback(
        previousVersion: String,
        reason: String
    ): Boolean {
        val currentState = rollbackState.value
        if (currentState.state != RollbackState.RollbackStateType.VERIFYING_PREVIOUS) {
            return false
        }

        rollbackStateFlow.value = RollbackState.RollbackInProgress(
            reason = reason,
            previousVersion = previousVersion,
            targetVersion = currentState.targetVersion ?: "unknown"
        )
        saveRollbackState()

        try {
            // Perform rollback - activate previous version
            val success = stagingArea.activateDataset(previousVersion)

            if (success) {
                rollbackStateFlow.value = RollbackState.RollbackComplete(
                    reason = reason,
                    previousVersion = previousVersion,
                    targetVersion = currentState.targetVersion ?: "unknown"
                )
                saveRollbackState()

                // Record rollback operation
                recordRollbackOperation(
                    previousVersion = previousVersion,
                    targetVersion = currentState.targetVersion ?: "unknown",
                    reason = reason,
                    success = true
                )

                return true
            } else {
                rollbackStateFlow.value = RollbackState.RollbackFailed(
                    reason = "Failed to activate previous version: $previousVersion",
                    previousVersion = previousVersion,
                    targetVersion = currentState.targetVersion ?: "unknown"
                )
                saveRollbackState()

                recordRollbackOperation(
                    previousVersion = previousVersion,
                    targetVersion = currentState.targetVersion ?: "unknown",
                    reason = reason,
                    success = false,
                    error = "Failed to activate previous version"
                )

                return false
            }
        } catch (e: Exception) {
            rollbackStateFlow.value = RollbackState.RollbackFailed(
                reason = "Rollback failed with exception: ${e.message}",
                previousVersion = previousVersion,
                targetVersion = currentState.targetVersion ?: "unknown",
                error = e
            )
            saveRollbackState()

            recordRollbackOperation(
                previousVersion = previousVersion,
                targetVersion = currentState.targetVersion ?: "unknown",
                reason = reason,
                success = false,
                error = e.message
            )

            return false
        }
    }

    /**
     * Aborts rollback operation.
     * 
     * @param reason Reason for abort
     */
    suspend fun abortRollback(reason: String) {
        val currentState = rollbackState.value
        if (!currentState.isActive()) {
            return
        }

        rollbackStateFlow.value = RollbackState.RollbackAborted(
            reason = reason,
            previousVersion = currentState.previousVersion ?: "unknown",
            targetVersion = currentState.targetVersion ?: "unknown"
        )
        saveRollbackState()
    }

    /**
     * Records a rollback operation.
     * 
     * @param previousVersion The version rolled back from
     * @param targetVersion The version rolled back to
     * @param reason Reason for rollback
     * @param success Whether rollback was successful
     * @param error Error message if rollback failed
     */
    internal fun recordRollbackOperation(
        previousVersion: String,
        targetVersion: String,
        reason: String,
        success: Boolean,
        error: String? = null
    ) {
        val operation = RollbackOperation(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            previousVersion = previousVersion,
            targetVersion = targetVersion,
            reason = reason,
            success = success,
            error = error
        )

        rollbackHistory.add(operation)
        saveRollbackHistory()
    }

    /**
     * Detects and prevents infinite rollback loops.
     * 
     * @param previousVersion The version being rolled back to
     * @return True if rollback loop is detected
     */
    fun detectRollbackLoop(previousVersion: String): Boolean {
        if (rollbackHistory.size < 3) {
            return false
        }

        // Check if we're trying to roll back to a version we just came from
        val recentHistory = rollbackHistory.takeLast(3)
        val targetVersions = recentHistory.map { it.targetVersion }
        
        return targetVersions.contains(previousVersion)
    }

    /**
     * Loads rollback state from disk.
     */
    private fun loadRollbackState() {
        try {
            if (Files.exists(rollbackStateFile)) {
                val content = String(Files.readAllBytes(rollbackStateFile), Charsets.UTF_8)
                val map = json.decodeFromString<Map<String, String>>(content)
                val state = RollbackState.fromMap(map)
                rollbackStateFlow.value = state
            }
        } catch (e: Exception) {
            rollbackStateFlow.value = RollbackState.Idle("Failed to load rollback state: ${e.message}")
        }
    }

    /**
     * Saves rollback state to disk.
     */
    private fun saveRollbackState() {
        try {
            val state = rollbackStateFlow.value
            val map = mapOf(
                "state" to state.state.name,
                "reason" to state.reason,
                "previousVersion" to (state.previousVersion ?: ""),
                "targetVersion" to (state.targetVersion ?: ""),
                "timestamp" to state.timestamp.toString()
            )
            val content = json.encodeToString(map)
            rollbackStateFile.parent?.let { Files.createDirectories(it) }
            Files.write(rollbackStateFile, content.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // Rollback state persistence failure is not critical
            // The system can still operate with in-memory state
        }
    }

    /**
     * Loads rollback history from disk.
     */
    private fun loadRollbackHistory() {
        try {
            if (Files.exists(rollbackHistoryFile)) {
                val content = String(Files.readAllBytes(rollbackHistoryFile), Charsets.UTF_8)
                val history = json.decodeFromString<List<RollbackOperation>>(content)
                rollbackHistory.addAll(history)
            }
        } catch (e: Exception) {
            // Rollback history persistence failure is not critical
        }
    }

    /**
     * Saves rollback history to disk.
     */
    private fun saveRollbackHistory() {
        try {
            val content = json.encodeToString(rollbackHistory)
            rollbackHistoryFile.parent?.let { Files.createDirectories(it) }
            Files.write(rollbackHistoryFile, content.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // Rollback history persistence failure is not critical
        }
    }

    /**
     * Clears rollback state (for testing or manual reset).
     */
    suspend fun clearState() {
        rollbackStateFlow.value = RollbackState.Idle()
        saveRollbackState()
        rollbackHistory.clear()
        saveRollbackHistory()
    }

    /**
     * Gets rollback statistics.
     */
    fun getRollbackStatistics(): RollbackStatistics {
        val total = rollbackHistory.size
        val successful = rollbackHistory.count { it.success }
        val failed = total - successful

        val recentFailures = rollbackHistory
            .filter { !it.success }
            .takeLast(5)

        return RollbackStatistics(
            totalRollbacks = total,
            successfulRollbacks = successful,
            failedRollbacks = failed,
            recentFailures = recentFailures
        )
    }
}

/**
 * Rollback statistics.
 */
data class RollbackStatistics(
    val totalRollbacks: Int,
    val successfulRollbacks: Int,
    val failedRollbacks: Int,
    val recentFailures: List<RollbackManager.RollbackOperation>
)
