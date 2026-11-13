package com.evnavigator.iran.domain.model.update

import com.evnavigator.iran.domain.model.download.DownloadManager
import com.evnavigator.iran.domain.model.download.DownloadProgress
import com.evnavigator.iran.domain.model.rollback.RollbackManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import com.evnavigator.iran.domain.model.download.DownloadState
import java.util.UUID

/**
 * Manager for background dataset updates.
 * 
 * This class coordinates the entire background update lifecycle:
 * - Checking for updates
 * - Evaluating constraints
 * - Scheduling updates
 * - Managing download state
 * - Handling installation
 * - Managing rollback
 * 
 * The manager respects Android platform constraints and user preferences.
 */
class BackgroundUpdateManager(
    private val updateCheckStrategy: UpdateCheckStrategy,
    private val downloadManager: DownloadManager,
    private val packageInstaller: PackageInstaller,
    private val rollbackManager: RollbackManager
) {
    /**
     * State of background update operations.
     */
    sealed class UpdateState {
        /**
         * No update operations are currently active.
         */
        data object Idle : UpdateState()

        /**
         * Update check is in progress.
         */
        data object Checking : UpdateState()

        /**
         * Update is being downloaded.
         */
        data class Downloading(
            val datasetId: String,
            val version: String,
            val progress: DownloadProgress
        ) : UpdateState()

        /**
         * Update is being prepared for installation.
         */
        data class Preparing(
            val datasetId: String,
            val version: String
        ) : UpdateState()

        /**
         * Update is being installed.
         */
        data class Installing(
            val datasetId: String,
            val version: String
        ) : UpdateState()

        /**
         * Update has been successfully installed.
         */
        data class Installed(
            val datasetId: String,
            val version: String,
            val timestamp: Long
        ) : UpdateState()

        /**
         * Update installation failed.
         */
        data class Failed(
            val datasetId: String,
            val version: String,
            val error: String
        ) : UpdateState()

        /**
         * Update was cancelled.
         */
        data class Cancelled(
            val datasetId: String,
            val version: String
        ) : UpdateState()

        /**
         * Update is waiting for constraints to be satisfied.
         */
        data class WaitingForConstraints(
            val datasetId: String,
            val version: String,
            val reason: String
        ) : UpdateState()
    }

    /**
     * Flow of update state changes.
     */
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /**
     * Flow of update progress.
     */
    private val _updateProgress = MutableStateFlow<UpdateProgress?>(null)
    val updateProgress: StateFlow<UpdateProgress?> = _updateProgress.asStateFlow()

    /**
     * Flow of update events.
     */
    private val _updateEvents = MutableStateFlow<List<UpdateEvent>>(emptyList())
    val updateEvents: StateFlow<List<UpdateEvent>> = _updateEvents.asStateFlow()

    /**
     * Check for updates for a specific dataset.
     * 
     * @param datasetId The dataset ID to check
     * @param constraints Constraints for the update check
     * @return UpdateCheckResult indicating whether an update is available
     */
    suspend fun checkForUpdates(
        datasetId: String,
        constraints: UpdateConstraint = UpdateConstraint()
    ): UpdateCheckResult {
        _updateState.value = UpdateState.Checking

        return try {
            val result = updateCheckStrategy.checkForUpdates(datasetId, constraints)
            _updateState.value = UpdateState.Idle
            result ?: UpdateCheckResult.noUpdateAvailable(datasetId, "1.0.0")
        } catch (e: Exception) {
            _updateState.value = UpdateState.Idle
            UpdateCheckResult.updateCheckFailed(datasetId, "", e.message ?: "Unknown error").copy(
                availabilityReason = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Schedule an update for a specific dataset.
     * 
     * @param datasetId The dataset ID to update
     * @param constraints Constraints for the update
     * @return UpdateId for tracking the update
     */
    suspend fun scheduleUpdate(
        datasetId: String,
        constraints: UpdateConstraint = UpdateConstraint()
    ): String {
        val updateId = UUID.randomUUID().toString()

        // Check for updates first
        val checkResult = checkForUpdates(datasetId, constraints)

        if (!checkResult.isUpdateAvailable) {
            addEvent(UpdateEvent.UpdateNotAvailable(datasetId, checkResult.availabilityReason ?: "No update available"))
            return updateId
        }

        if (checkResult.isBlocked()) {
            addEvent(UpdateEvent.UpdateBlocked(datasetId, checkResult.getBlockReason() ?: "Unknown reason"))
            return updateId
        }

        // Start the update process
        startUpdateProcess(updateId, datasetId, checkResult, constraints)

        return updateId
    }

    /**
     * Start the update process for a dataset.
     */
    private suspend fun startUpdateProcess(
        updateId: String,
        datasetId: String,
        checkResult: UpdateCheckResult,
        constraints: UpdateConstraint
    ) {
        val version = checkResult.getVersion()

        // Start download
        _updateState.value = UpdateState.Downloading(datasetId, version, DownloadProgress(0L, 0L))
        _updateProgress.value = UpdateProgress(
            datasetId = datasetId,
            version = version,
            progress = DownloadProgress(0L, 0L),
            priority = checkResult.getPriority()
        )
        addEvent(UpdateEvent.UpdateStarted(updateId, datasetId, version))

        // Register download with download manager
        val downloadId = downloadManager.registerDownload(
            datasetId = datasetId,
            url = checkResult.availableVersion ?: "",
            resumeToken = null
        )

        // Monitor download progress
        val progressFlow = downloadManager.getDownloadProgress(downloadId)
        if (progressFlow != null) {
            progressFlow.takeWhile { !it.isComplete }
                .collect { progress ->
                    _updateProgress.value = UpdateProgress(
                        datasetId = datasetId,
                        version = version,
                        progress = progress,
                        priority = checkResult.getPriority()
                    )
                }
        }

        // Check final download state
        val downloadState = downloadManager.waitForCompletion(downloadId)

        if (downloadState is DownloadState.Completed) {
            // Prepare for installation
            _updateState.value = UpdateState.Preparing(datasetId, version)
            addEvent(UpdateEvent.UpdatePreparing(updateId, datasetId, version))

            // Install the package
            installPackage(updateId, datasetId, version, downloadId, checkResult, constraints)
        } else {
            // Download failed
            val error = when (downloadState) {
                is DownloadState.Failed -> downloadState.error.message
                is DownloadState.Cancelled -> "Download cancelled"
                else -> "Download failed with state: $downloadState"
            }

            _updateState.value = UpdateState.Failed(datasetId, version, error)
            addEvent(UpdateEvent.UpdateFailed(updateId, datasetId, version, error))
        }
    }

    /**
     * Install a package.
     */
    private suspend fun installPackage(
        updateId: String,
        datasetId: String,
        version: String,
        downloadId: String,
        checkResult: UpdateCheckResult,
        constraints: UpdateConstraint
    ) {
        try {
            _updateState.value = UpdateState.Installing(datasetId, version)

            // Install the package using the package installer
            val installResult = packageInstaller.installPackage(
                datasetId = datasetId,
                version = version,
                downloadId = downloadId // This would be passed from the download
            )

            if (installResult.success) {
                _updateState.value = UpdateState.Installed(datasetId, version, System.currentTimeMillis())
                addEvent(UpdateEvent.UpdateCompleted(updateId, datasetId, version))
            } else {
                throw Exception(installResult.error ?: "Installation failed")
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed(datasetId, version, e.message ?: "Installation failed")
            addEvent(UpdateEvent.UpdateFailed(updateId, datasetId, version, e.message ?: "Installation failed"))
        }
    }

    /**
     * Cancel an ongoing update.
     * 
     * @param updateId The update ID to cancel
     */
    suspend fun cancelUpdate(updateId: String) {
        // Cancel downloads if in progress
        downloadManager.cancelAllDownloads()

        // Clear state
        _updateState.value = UpdateState.Idle
        _updateProgress.value = null

        addEvent(UpdateEvent.UpdateCancelled(updateId))
    }

    /**
     * Get the current update state.
     */
    fun getCurrentState(): UpdateState {
        return _updateState.value
    }

    /**
     * Get the current update progress.
     */
    fun getCurrentProgress(): UpdateProgress? {
        return _updateProgress.value
    }

    /**
     * Add an update event.
     */
    private fun addEvent(event: UpdateEvent) {
        val currentEvents = _updateEvents.value.toMutableList()
        currentEvents.add(event)
        _updateEvents.value = currentEvents

        // Keep only the last 100 events
        if (currentEvents.size > 100) {
            _updateEvents.value = currentEvents.takeLast(100)
        }
    }

    /**
     * Update progress data.
     */
    data class UpdateProgress(
        val datasetId: String,
        val version: String,
        val progress: DownloadProgress,
        val priority: UpdatePriority
    )
}

/**
 * Update event types.
 */
sealed class UpdateEvent {
    data class UpdateStarted(
        val updateId: String,
        val datasetId: String,
        val version: String
    ) : UpdateEvent()

    data class UpdatePreparing(
        val updateId: String,
        val datasetId: String,
        val version: String
    ) : UpdateEvent()

    data class UpdateCompleted(
        val updateId: String,
        val datasetId: String,
        val version: String
    ) : UpdateEvent()

    data class UpdateFailed(
        val updateId: String,
        val datasetId: String,
        val version: String,
        val error: String
    ) : UpdateEvent()

    data class UpdateCancelled(
        val updateId: String
    ) : UpdateEvent()

    data class UpdateNotAvailable(
        val datasetId: String,
        val reason: String
    ) : UpdateEvent()

    data class UpdateBlocked(
        val datasetId: String,
        val reason: String
    ) : UpdateEvent()
}

interface PackageInstaller {
    suspend fun installPackage(
        datasetId: String,
        version: String,
        downloadId: String
    ): InstallResult

    data class InstallResult(
        val success: Boolean,
        val error: String? = null
    )
}

typealias PackageInstallResult = PackageInstaller.InstallResult
