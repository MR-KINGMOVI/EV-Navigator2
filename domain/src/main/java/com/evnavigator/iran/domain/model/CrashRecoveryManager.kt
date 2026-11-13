package com.evnavigator.iran.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Crash-safe recovery manager for dataset operations.
 * 
 * This manager persists recovery state across process death and device restart,
 * enabling safe recovery from interrupted downloads, installations, and activations.
 * 
 * Key responsibilities:
 * - Persist recovery state to disk
 * - Detect incomplete operations on startup
 * - Validate current state before resuming
 * - Trigger safe rollback when needed
 * - Provide recovery state to consumers
 * 
 * @property storagePath Path to storage directory for recovery state file
 */
class CrashRecoveryManager(
    private val storagePath: String
) {
    private val _recoveryState = MutableStateFlow<CrashRecoveryState?>(null)
    val recoveryState: StateFlow<CrashRecoveryState?> = _recoveryState.asStateFlow()

    private val recoveryFile = File(storagePath, "recovery_state.json")

    /**
     * Initializes the recovery manager and loads any existing recovery state
     */
    suspend fun initialize() {
        if (recoveryFile.exists()) {
            val loadedState = loadRecoveryState()
            if (loadedState != null) {
                _recoveryState.value = loadedState
            }
        }
    }

    /**
     * Handles recovery based on loaded state
     */
    private suspend fun handleRecovery(state: CrashRecoveryState) {
        when (state.state) {
            CrashRecoveryState.RecoveryState.DOWNLOADING,
            CrashRecoveryState.RecoveryState.STAGED,
            CrashRecoveryState.RecoveryState.VERIFYING,
            CrashRecoveryState.RecoveryState.READY,
            CrashRecoveryState.RecoveryState.INSTALLING -> {
                // Incomplete operation detected - mark as failed
                val failedState = CrashRecoveryState.failed(
                    datasetId = state.datasetId,
                    version = state.version,
                    error = "Operation interrupted by crash or process death",
                    previousVersion = state.previousVersion
                )
                _recoveryState.value = failedState
                persistRecoveryState(failedState)
            }

            CrashRecoveryState.RecoveryState.ACTIVE -> {
                // Operation completed successfully
                // Clean up recovery state
                _recoveryState.value = null
                persistRecoveryState(null)
            }

            CrashRecoveryState.RecoveryState.FAILED -> {
                // Previous operation failed - may need rollback
                if (state.previousVersion != null) {
                    val rollbackState = CrashRecoveryState.rollbackRequired(
                        datasetId = state.datasetId,
                        version = state.version,
                        previousVersion = state.previousVersion
                    )
                    _recoveryState.value = rollbackState
                    persistRecoveryState(rollbackState)
                } else {
                    // No previous version to rollback to - clean up
                    _recoveryState.value = null
                    persistRecoveryState(null)
                }
            }

            CrashRecoveryState.RecoveryState.ROLLBACK_REQUIRED -> {
                // Rollback was previously required - ensure it's handled
                _recoveryState.value = state
                persistRecoveryState(state)
            }

            CrashRecoveryState.RecoveryState.NOT_STARTED -> {
                // No operation in progress
                _recoveryState.value = null
                persistRecoveryState(null)
            }
        }
    }

    /**
     * Starts a new dataset installation operation
     */
    suspend fun startInstallation(datasetId: String, version: String) {
        val newState = CrashRecoveryState.notStarted(datasetId, version)
        _recoveryState.value = newState
        persistRecoveryState(newState)
    }

    /**
     * Updates state to DOWNLOADING
     */
    suspend fun markDownloading(datasetId: String, version: String) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.downloading(datasetId, version)
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Updates state to STAGED
     */
    suspend fun markStaged(datasetId: String, version: String) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.staged(datasetId, version)
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Updates state to VERIFYING
     */
    suspend fun markVerifying(datasetId: String, version: String) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.verifying(datasetId, version)
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Updates state to READY
     */
    suspend fun markReady(datasetId: String, version: String) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.ready(datasetId, version)
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Updates state to INSTALLING
     */
    suspend fun markInstalling(datasetId: String, version: String) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.installing(datasetId, version)
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Marks installation as successful (ACTIVE)
     */
    suspend fun markActive(datasetId: String, version: String) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.active(datasetId, version)
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Marks installation as failed
     */
    suspend fun markFailed(
        datasetId: String,
        version: String,
        error: String,
        previousVersion: String? = null
    ) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.failed(
                datasetId = datasetId,
                version = version,
                error = error,
                previousVersion = previousVersion
            )
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Marks rollback as required
     */
    suspend fun markRollbackRequired(
        datasetId: String,
        version: String,
        previousVersion: String
    ) {
        val currentState = _recoveryState.value
        if (currentState != null && 
            currentState.datasetId == datasetId && 
            currentState.version == version) {
            val newState = CrashRecoveryState.rollbackRequired(
                datasetId = datasetId,
                version = version,
                previousVersion = previousVersion
            )
            _recoveryState.value = newState
            persistRecoveryState(newState)
        }
    }

    /**
     * Clears recovery state (operation completed successfully)
     */
    suspend fun clearRecoveryState() {
        _recoveryState.value = null
        persistRecoveryState(null)
    }

    /**
     * Persists recovery state to disk
     */
    private suspend fun persistRecoveryState(state: CrashRecoveryState?) {
        try {
            if (state != null && state.state != CrashRecoveryState.RecoveryState.ACTIVE) {
                val json = state.toJson()
                recoveryFile.writeText(json)
            } else {
                recoveryFile.delete()
            }
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }

    /**
     * Loads recovery state from disk
     */
    private fun loadRecoveryState(): CrashRecoveryState? {
        return try {
            if (recoveryFile.exists()) {
                val json = recoveryFile.readText()
                CrashRecoveryState.fromJson(json)
            } else {
                null
            }
        } catch (e: Exception) {
            // Corrupted recovery file - treat as no state
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets the current recovery state
     */
    fun getCurrentState(): CrashRecoveryState? {
        return _recoveryState.value
    }

    /**
     * Checks if there's an incomplete operation that needs recovery
     */
    fun hasIncompleteOperation(): Boolean {
        val state = _recoveryState.value
        return state != null && (state.isIncomplete() || state.state == CrashRecoveryState.RecoveryState.NOT_STARTED)
    }

    /**
     * Checks if rollback is required
     */
    fun requiresRollback(): Boolean {
        val state = _recoveryState.value
        return state != null && state.requiresRollback()
    }

    /**
     * Gets the previous version for rollback
     */
    fun getPreviousVersion(): String? {
        val state = _recoveryState.value
        return state?.previousVersion
    }
}

/**
 * JSON serialization support for CrashRecoveryState
 */
fun CrashRecoveryState.toJson(): String {
    return buildString {
        append("{")
        append("\"state\":\"${state.name}\",")
        append("\"datasetId\":\"$datasetId\",")
        append("\"version\":\"$version\",")
        append("\"operationType\":\"${operationType.name}\",")
        append("\"timestamp\":$timestamp")
        if (error != null) {
            append(",\"error\":\"$error\"")
        }
        if (previousVersion != null) {
            append(",\"previousVersion\":\"$previousVersion\"")
        }
        append("}")
    }
}

fun CrashRecoveryState.Companion.fromJson(json: String): CrashRecoveryState {
    // Simple JSON parsing - in production use a proper JSON library
    val stateName = extractJsonValue(json, "state")
    val datasetId = extractJsonValue(json, "datasetId")
    val version = extractJsonValue(json, "version")
    val operationTypeName = extractJsonValue(json, "operationType")
    val timestampStr = extractJsonValue(json, "timestamp")
    val errorStr = extractJsonValue(json, "error")
    val previousVersionStr = extractJsonValue(json, "previousVersion")

    val state = CrashRecoveryState.RecoveryState.valueOf(stateName)
    val operationType = if (operationTypeName.isNotEmpty()) {
        CrashRecoveryState.OperationType.valueOf(operationTypeName)
    } else {
        CrashRecoveryState.OperationType.INSTALL
    }
    val timestamp = timestampStr.toLongOrNull() ?: System.currentTimeMillis()
    val error = errorStr.ifEmpty { null }
    val previousVersion = previousVersionStr.ifEmpty { null }

    return CrashRecoveryState(
        state = state,
        datasetId = datasetId,
        version = version,
        operationType = operationType,
        timestamp = timestamp,
        error = error,
        previousVersion = previousVersion
    )
}

private fun extractJsonValue(json: String, key: String): String {
    val pattern = "\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9A-Za-z_]+))"
    val regex = Regex(pattern)
    val match = regex.find(json)
    return match?.groupValues?.get(1)?.ifEmpty { null }
        ?: match?.groupValues?.get(2)?.ifEmpty { null }
        ?: ""
}
