package com.evnavigator.iran.domain.model.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Download manager for resumable dataset downloads.
 *
 * This class manages the lifecycle of dataset downloads, including:
 * - Download state tracking
 * - Resume token generation and validation
 * - Progress tracking
 * - Error handling
 * - Cleanup of invalid partial downloads
 * - Integration with staging area
 *
 * Downloads are persisted to disk to survive process death and device restarts.
 */
class DownloadManager(
    private val downloadStoragePath: String,
    private val stagingAreaPath: String
) {
    private val downloads = mutableMapOf<String, DownloadState>()
    private val progressFlows = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()

    /**
     * Register a new download.
     *
     * @param downloadId Unique download identifier
     * @param url Download URL
     * @param totalBytes Total size of the download in bytes (0 if unknown)
     * @param resumeToken Resume token for resuming the download (null if new download)
     * @param expectedChecksum Expected checksum for validation after completion
     * @return The initial download state
     */
    fun registerDownload(
        downloadId: String,
        url: String,
        totalBytes: Long,
        resumeToken: String? = null,
        expectedChecksum: String? = null,
        lastModified: Long? = null
    ): DownloadState {
        val state = if (resumeToken != null) {
            // Try to resume an interrupted download
            val savedState = downloads[downloadId] ?: loadDownloadState(downloadId)
            if (savedState != null && savedState.canResume()) {
                val savedResumeToken = when (savedState) {
                    is DownloadState.InProgress -> savedState.resumeToken
                    is DownloadState.Paused -> savedState.resumeToken
                    is DownloadState.Interrupted -> savedState.resumeToken
                    else -> null
                }
                
                // Validate resume token
                if (savedResumeToken != null && savedResumeToken == resumeToken) {
                    // Restore interrupted download to inProgress
                    DownloadState.inProgress(
                        totalBytes = savedState.totalBytes,
                        downloadedBytes = savedState.downloadedBytes,
                        resumeToken = savedResumeToken,
                        lastModified = lastModified,
                        expectedChecksum = expectedChecksum
                    )
                } else {
                    // Invalid resume token, start fresh
                    startNewDownload(downloadId, url, totalBytes, expectedChecksum, lastModified)
                }
            } else {
                // No valid saved state, start fresh
                startNewDownload(downloadId, url, totalBytes, expectedChecksum, lastModified)
            }
        } else {
            // New download
            startNewDownload(downloadId, url, totalBytes, expectedChecksum, lastModified)
        }

        downloads[downloadId] = state
        progressFlows[downloadId] = MutableStateFlow(DownloadProgress.fromDownloadState(state))

        return state
    }

    open fun registerDownload(
        datasetId: String,
        url: String,
        resumeToken: String? = null
    ): String {
        val downloadId = java.util.UUID.randomUUID().toString()
        registerDownload(
            downloadId = downloadId,
            url = url,
            totalBytes = 0L,
            resumeToken = resumeToken
        )
        return downloadId
    }

    open suspend fun waitForCompletion(downloadId: String): DownloadState {
        return getDownloadState(downloadId) ?: DownloadState.notStarted()
    }

    open fun cancelAllDownloads() {
        getAllDownloadIds().forEach { cancelDownload(it) }
    }

    /**
     * Start a new download.
     */
    private fun startNewDownload(
        downloadId: String,
        url: String,
        totalBytes: Long,
        expectedChecksum: String?,
        lastModified: Long? = null
    ): DownloadState {
        val resumeToken = generateResumeToken()

        return DownloadState.inProgress(
            totalBytes = totalBytes,
            downloadedBytes = 0,
            resumeToken = resumeToken,
            lastModified = lastModified,
            expectedChecksum = expectedChecksum
        )
    }

    /**
     * Update download progress.
     *
     * @param downloadId Download identifier
     * @param downloadedBytes Number of bytes downloaded
     * @param totalChunks Total number of chunks (if chunked)
     * @param downloadedChunks Number of chunks downloaded
     */
    fun updateProgress(
        downloadId: String,
        downloadedBytes: Long,
        totalChunks: Int = 0,
        downloadedChunks: Int = 0
    ) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.InProgress) {
            return
        }

        val newState = state.copy(
            downloadedBytes = downloadedBytes,
            totalChunks = totalChunks,
            downloadedChunks = downloadedChunks
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)
    }

    /**
     * Mark download as paused.
     *
     * @param downloadId Download identifier
     */
    fun pauseDownload(downloadId: String) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.InProgress) {
            return
        }

        val newState = DownloadState.paused(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes,
            resumeToken = state.resumeToken ?: ""
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)
    }

    /**
     * Mark download as interrupted.
     *
     * @param downloadId Download identifier
     * @param error Error that caused the interruption
     */
    fun interruptDownload(downloadId: String, error: DownloadError) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.InProgress) {
            return
        }

        val newState = DownloadState.interrupted(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes,
            resumeToken = state.resumeToken ?: "",
            error = error
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)
    }

    /**
     * Mark download as cancelled.
     *
     * @param downloadId Download identifier
     */
    fun cancelDownload(downloadId: String) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.InProgress && state !is DownloadState.Interrupted) {
            return
        }

        val newState = DownloadState.cancelled(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)

        // Cleanup partial download
        cleanupPartialDownload(downloadId)
    }

    /**
     * Mark download as completed.
     *
     * @param downloadId Download identifier
     * @param checksum Actual checksum of the downloaded data
     * @param checksumAlgorithm Algorithm used for checksum
     * @param durationMs Total download duration in milliseconds
     */
    fun completeDownload(
        downloadId: String,
        checksum: String,
        checksumAlgorithm: String,
        durationMs: Long
    ) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.InProgress) {
            return
        }

        val downloadedBytes = if (state.totalBytes > 0) state.totalBytes else state.downloadedBytes
        val newState = DownloadState.completed(
            totalBytes = state.totalBytes,
            downloadedBytes = downloadedBytes,
            checksum = checksum,
            checksumAlgorithm = checksumAlgorithm,
            durationMs = durationMs
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)
    }

    /**
     * Mark download as failed.
     *
     * @param downloadId Download identifier
     * @param error Error that caused the failure
     */
    fun failDownload(downloadId: String, error: DownloadError) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.InProgress) {
            return
        }

        val newState = DownloadState.failed(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes,
            error = error
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)

        // Cleanup partial download
        cleanupPartialDownload(downloadId)
    }

    /**
     * Mark download as verified.
     *
     * @param downloadId Download identifier
     * @param checksum Actual checksum of the downloaded data
     * @param checksumAlgorithm Algorithm used for checksum
     */
    fun verifyDownload(
        downloadId: String,
        checksum: String,
        checksumAlgorithm: String
    ) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.Completed) {
            return
        }

        val newState = DownloadState.verified(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes,
            checksum = checksum,
            checksumAlgorithm = checksumAlgorithm
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)
    }

    /**
     * Mark download as staged.
     *
     * @param downloadId Download identifier
     * @param stagedPath Path to the staged package file
     */
    fun stageDownload(downloadId: String, stagedPath: String) {
        val state = downloads[downloadId] ?: return

        if (state !is DownloadState.Verified) {
            return
        }

        val newState = DownloadState.staged(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes,
            checksum = state.checksum,
            checksumAlgorithm = state.checksumAlgorithm,
            stagedPath = stagedPath
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)
    }

    /**
     * Get the current download state.
     *
     * @param downloadId Download identifier
     * @return The current download state, or null if not found
     */
    fun getDownloadState(downloadId: String): DownloadState? {
        return downloads[downloadId]
    }

    /**
     * Get the download progress as a StateFlow.
     *
     * @param downloadId Download identifier
     * @return StateFlow of download progress, or null if not found
     */
    fun getDownloadProgress(downloadId: String): StateFlow<DownloadProgress>? {
        return progressFlows[downloadId]?.asStateFlow()
    }

    /**
     * Check if a download can be resumed.
     *
     * @param downloadId Download identifier
     * @return true if the download can be resumed, false otherwise
     */
    fun canResumeDownload(downloadId: String): Boolean {
        val state = downloads[downloadId] ?: return false
        return state.canResume()
    }

    /**
     * Resume an interrupted download.
     *
     * @param downloadId Download identifier
     * @return true if resume was successful, false otherwise
     */
    fun resumeDownload(downloadId: String): Boolean {
        val state = downloads[downloadId] ?: return false

        if (!state.canResume()) {
            return false
        }

        if (state !is DownloadState.Interrupted) {
            return false
        }

        // Validate resume token
        if (state.resumeToken.isEmpty()) {
            return false
        }

        // Resume the download
        val newState = DownloadState.inProgress(
            totalBytes = state.totalBytes,
            downloadedBytes = state.downloadedBytes,
            resumeToken = state.resumeToken,
            expectedChecksum = null
        )

        downloads[downloadId] = newState
        progressFlows[downloadId]?.value = DownloadProgress.fromDownloadState(newState)

        // Persist state
        saveDownloadState(downloadId, newState)

        return true
    }

    /**
     * Get all download IDs.
     *
     * @return List of all download IDs
     */
    fun getAllDownloadIds(): List<String> {
        return downloads.keys.toList()
    }

    /**
     * Get all downloads.
     *
     * @return List of all download states
     */
    fun getAllDownloads(): List<DownloadState> {
        return downloads.values.toList()
    }

    /**
     * Remove a download.
     *
     * @param downloadId Download identifier
     */
    fun removeDownload(downloadId: String) {
        downloads.remove(downloadId)
        progressFlows.remove(downloadId)

        // Cleanup partial download
        cleanupPartialDownload(downloadId)

        // Remove persisted state
        File("$downloadStoragePath/$downloadId.json").delete()
    }

    /**
     * Load all saved downloads from disk.
     */
    fun loadSavedDownloads() {
        val downloadDir = File(downloadStoragePath)

        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
            return
        }

        downloadDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".json")) {
                try {
                    val downloadId = file.name.removeSuffix(".json")
                    val state = loadDownloadState(downloadId)

                    if (state != null) {
                        downloads[downloadId] = state
                        progressFlows[downloadId] = MutableStateFlow(DownloadProgress.fromDownloadState(state))
                    }
                } catch (e: Exception) {
                    // Skip corrupted state files
                }
            }
        }
    }

    /**
     * Generate a resume token.
     */
    private fun generateResumeToken(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Save download state to disk.
     */
    internal fun saveDownloadState(downloadId: String, state: DownloadState) {
        val file = File("$downloadStoragePath/$downloadId.json")

        try {
            val json = serializeDownloadState(state)
            file.writeText(json)
        } catch (e: Exception) {
            // Failed to persist state
        }
    }

    /**
     * Load download state from disk.
     */
    private fun loadDownloadState(downloadId: String): DownloadState? {
        val file = File("$downloadStoragePath/$downloadId.json")

        if (!file.exists()) {
            return null
        }

        try {
            val json = file.readText()
            return deserializeDownloadState(json)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Serialize download state to JSON.
     */
    private fun serializeDownloadState(state: DownloadState): String {
        // Simple JSON serialization
        return when (state) {
            is DownloadState.NotStarted -> """
                {
                    "type": "NotStarted"
                }
            """.trimIndent()

            is DownloadState.InProgress -> """
                {
                    "type": "InProgress",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "totalChunks": ${state.totalChunks},
                    "downloadedChunks": ${state.downloadedChunks},
                    "resumeToken": "${state.resumeToken}",
                    "lastModified": ${state.lastModified ?: 0},
                    "expectedChecksum": "${state.expectedChecksum ?: ""}"
                }
            """.trimIndent()

            is DownloadState.Paused -> """
                {
                    "type": "Paused",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "resumeToken": "${state.resumeToken}"
                }
            """.trimIndent()

            is DownloadState.Interrupted -> """
                {
                    "type": "Interrupted",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "resumeToken": "${state.resumeToken}",
                    "error": {
                        "type": "${state.error.javaClass.simpleName}",
                        "message": "${state.error.message}"
                    }
                }
            """.trimIndent()

            is DownloadState.Cancelled -> """
                {
                    "type": "Cancelled",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes}
                }
            """.trimIndent()

            is DownloadState.Completed -> """
                {
                    "type": "Completed",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "checksum": "${state.checksum}",
                    "checksumAlgorithm": "${state.checksumAlgorithm}",
                    "durationMs": ${state.durationMs}
                }
            """.trimIndent()

            is DownloadState.Failed -> """
                {
                    "type": "Failed",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "error": {
                        "type": "${state.error.javaClass.simpleName}",
                        "message": "${state.error.message}"
                    }
                }
            """.trimIndent()

            is DownloadState.Verified -> """
                {
                    "type": "Verified",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "checksum": "${state.checksum}",
                    "checksumAlgorithm": "${state.checksumAlgorithm}"
                }
            """.trimIndent()

            is DownloadState.Staged -> """
                {
                    "type": "Staged",
                    "totalBytes": ${state.totalBytes},
                    "downloadedBytes": ${state.downloadedBytes},
                    "checksum": "${state.checksum}",
                    "checksumAlgorithm": "${state.checksumAlgorithm}",
                    "stagedPath": "${state.stagedPath}"
                }
            """.trimIndent()
        }
    }

    /**
     * Deserialize download state from JSON.
     */
    private fun deserializeDownloadState(json: String): DownloadState {
        // Simple JSON deserialization
        val type = extractJsonValue(json, "type")

        return when (type) {
            "NotStarted" -> DownloadState.notStarted()
            "InProgress" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val totalChunks = extractJsonValue(json, "totalChunks").toInt()
                val downloadedChunks = extractJsonValue(json, "downloadedChunks").toInt()
                val resumeToken = extractJsonValue(json, "resumeToken")
                val lastModified = extractJsonValue(json, "lastModified").toLongOrNull()
                val expectedChecksum = extractJsonValue(json, "expectedChecksum")

                DownloadState.inProgress(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    resumeToken = resumeToken,
                    lastModified = lastModified,
                    expectedChecksum = expectedChecksum
                )
            }
            "Paused" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val resumeToken = extractJsonValue(json, "resumeToken")

                DownloadState.paused(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    resumeToken = resumeToken
                )
            }
            "Interrupted" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val resumeToken = extractJsonValue(json, "resumeToken")
                val errorJson = extractJsonValue(json, "error")

                val error = deserializeDownloadError(errorJson)

                DownloadState.interrupted(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    resumeToken = resumeToken,
                    error = error
                )
            }
            "Cancelled" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()

                DownloadState.cancelled(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes
                )
            }
            "Completed" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val checksum = extractJsonValue(json, "checksum")
                val checksumAlgorithm = extractJsonValue(json, "checksumAlgorithm")
                val durationMs = extractJsonValue(json, "durationMs").toLong()

                DownloadState.completed(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    checksum = checksum,
                    checksumAlgorithm = checksumAlgorithm,
                    durationMs = durationMs
                )
            }
            "Failed" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val errorJson = extractJsonValue(json, "error")

                val error = deserializeDownloadError(errorJson)

                DownloadState.failed(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    error = error
                )
            }
            "Verified" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val checksum = extractJsonValue(json, "checksum")
                val checksumAlgorithm = extractJsonValue(json, "checksumAlgorithm")

                DownloadState.verified(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    checksum = checksum,
                    checksumAlgorithm = checksumAlgorithm
                )
            }
            "Staged" -> {
                val totalBytes = extractJsonValue(json, "totalBytes").toLong()
                val downloadedBytes = extractJsonValue(json, "downloadedBytes").toLong()
                val checksum = extractJsonValue(json, "checksum")
                val checksumAlgorithm = extractJsonValue(json, "checksumAlgorithm")
                val stagedPath = extractJsonValue(json, "stagedPath")

                DownloadState.staged(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    checksum = checksum,
                    checksumAlgorithm = checksumAlgorithm,
                    stagedPath = stagedPath
                )
            }
            else -> throw IllegalArgumentException("Unknown or invalid download state type: '$type'")
        }
    }

    /**
     * Deserialize download error from JSON.
     */
    private fun deserializeDownloadError(json: String): DownloadError {
        val type = extractJsonValue(json, "type")
        val message = extractJsonValue(json, "message")

        return when (type) {
            "NetworkError" -> DownloadError.network(message)
            "HttpError" -> DownloadError.http(0, message)
            "ChecksumMismatch" -> DownloadError.checksumMismatch("", "", "")
            "InsufficientStorage" -> DownloadError.insufficientStorage(0, 0)
            "PermissionError" -> DownloadError.permission(message)
            "InvalidResumeToken" -> DownloadError.invalidResumeToken(message)
            "UnsupportedProtocol" -> DownloadError.unsupportedProtocol("")
            "UnknownError" -> DownloadError.unknown(message)
            else -> DownloadError.unknown(message)
        }
    }

    /**
     * Extract JSON value by key.
     */
    private fun extractJsonValue(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9A-Za-z_.-]+)|(\\{[^}]*\\}))"
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return ""

        return match.groups[1]?.value
            ?: match.groups[2]?.value
            ?: match.groups[3]?.value
            ?: ""
    }

    /**
     * Cleanup partial download files.
     */
    private fun cleanupPartialDownload(downloadId: String) {
        val state = downloads[downloadId] ?: return

        // Only cleanup if download is not complete or verified
        if (state.isComplete() || state is DownloadState.Verified || state is DownloadState.Staged) {
            return
        }

        // Delete partial download file if it exists
        val partialFile = File("$downloadStoragePath/$downloadId.partial")
        if (partialFile.exists()) {
            partialFile.delete()
        }
    }

    /**
     * Calculate checksum of a file.
     */
    fun calculateChecksum(filePath: String, algorithm: String = "SHA-256"): String {
        val file = File(filePath)

        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
