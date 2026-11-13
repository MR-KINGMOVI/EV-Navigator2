package com.evnavigator.iran.domain.model.download

/**
 * Download state machine for resumable dataset downloads.
 *
 * This sealed class defines all possible states in the download lifecycle,
 * including states for interrupted downloads, resumed downloads, and completed downloads.
 *
 * States are designed to be deterministic and safe for persistence.
 */
sealed class DownloadState {
    abstract val totalBytes: Long
    abstract val downloadedBytes: Long
    open val resumeToken: String? get() = null
    open val lastModified: Long? get() = null
    open val error: DownloadError? get() = null
    open val expectedChecksum: String? get() = null
    open val totalChunks: Int get() = 0
    open val downloadedChunks: Int get() = 0
    open val checksum: String? get() = null
    open val checksumAlgorithm: String? get() = null
    open val durationMs: Long get() = 0L
    open val stagedPath: String? get() = null

    /**
     * Download has not started yet.
     */
    data object NotStarted : DownloadState() {
        override val totalBytes: Long = 0
        override val downloadedBytes: Long = 0
    }

    /**
     * Download is in progress.
     *
     * @param totalBytes Total size of the download in bytes (0 if unknown)
     * @param downloadedBytes Number of bytes already downloaded
     * @param totalChunks Total number of chunks (if chunked)
     * @param downloadedChunks Number of chunks already downloaded
     * @param resumeToken Resume token for resuming the download
     * @param lastModified Last modification timestamp of the resource
     * @param expectedChecksum Expected checksum for validation after completion
     */
    data class InProgress(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val totalChunks: Int = 0,
        override val downloadedChunks: Int = 0,
        override val resumeToken: String? = null,
        override val lastModified: Long? = null,
        override val expectedChecksum: String? = null
    ) : DownloadState()

    /**
     * Download has been paused by the user.
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes already downloaded
     * @param resumeToken Resume token for resuming the download
     */
    data class Paused(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val resumeToken: String
    ) : DownloadState()

    /**
     * Download has been interrupted (network failure, process death, etc.).
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes already downloaded
     * @param resumeToken Resume token for resuming the download
     * @param error Error that caused the interruption
     */
    data class Interrupted(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val resumeToken: String,
        override val error: DownloadError
    ) : DownloadState()

    /**
     * Download has been cancelled by the user.
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes already downloaded
     */
    data class Cancelled(
        override val totalBytes: Long,
        override val downloadedBytes: Long
    ) : DownloadState()

    /**
     * Download has completed successfully.
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes downloaded
     * @param checksum Actual checksum of the downloaded data
     * @param checksumAlgorithm Algorithm used for checksum (e.g., SHA-256)
     * @param durationMs Total download duration in milliseconds
     */
    data class Completed(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val checksum: String,
        override val checksumAlgorithm: String,
        override val durationMs: Long
    ) : DownloadState() {
        constructor(
            datasetId: String? = null,
            version: String? = null,
            sizeBytes: Long = 0L,
            checksum: String = "",
            checksumAlgorithm: String = "SHA-256",
            downloadId: String? = null,
            downloadUrl: String? = null,
            downloadStartTime: Long = 0L,
            downloadEndTime: Long = 0L,
            downloadDurationMs: Long = 0L,
            downloadSpeedBps: Long = 0L,
            resumeToken: String? = null
        ) : this(
            totalBytes = sizeBytes,
            downloadedBytes = sizeBytes,
            checksum = checksum,
            checksumAlgorithm = checksumAlgorithm,
            durationMs = downloadDurationMs
        )
    }

    /**
     * Download has failed permanently.
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes already downloaded
     * @param error Error that caused the failure
     */
    data class Failed(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val error: DownloadError
    ) : DownloadState() {
        constructor(
            datasetId: String? = null,
            version: String? = null,
            error: String = "Download failed",
            downloadId: String? = null,
            downloadUrl: String? = null,
            downloadStartTime: Long = 0L,
            downloadEndTime: Long = 0L,
            totalBytes: Long = 0L,
            downloadedBytes: Long = 0L
        ) : this(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            error = DownloadError.NetworkError(message = error)
        )
    }

    /**
     * Download has been verified and is ready for installation.
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes downloaded
     * @param checksum Actual checksum of the downloaded data
     * @param checksumAlgorithm Algorithm used for checksum
     */
    data class Verified(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val checksum: String,
        override val checksumAlgorithm: String
    ) : DownloadState()

    /**
     * Download has been staged and is ready for installation.
     *
     * @param totalBytes Total size of the download in bytes
     * @param downloadedBytes Number of bytes downloaded
     * @param checksum Actual checksum of the downloaded data
     * @param checksumAlgorithm Algorithm used for checksum
     * @param stagedPath Path to the staged package file
     */
    data class Staged(
        override val totalBytes: Long,
        override val downloadedBytes: Long,
        override val checksum: String,
        override val checksumAlgorithm: String,
        override val stagedPath: String
    ) : DownloadState()

    companion object {
        /**
         * Create a NotStarted state.
         */
        fun notStarted(): DownloadState = NotStarted

        /**
         * Create an InProgress state.
         */
        fun inProgress(
            totalBytes: Long,
            downloadedBytes: Long,
            resumeToken: String?
        ): DownloadState = InProgress(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            resumeToken = resumeToken
        )

        fun inProgress(
            totalBytes: Long,
            downloadedBytes: Long,
            totalChunks: Int = 0,
            downloadedChunks: Int = 0,
            resumeToken: String? = null,
            lastModified: Long? = null,
            expectedChecksum: String? = null
        ): DownloadState = InProgress(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            totalChunks = totalChunks,
            downloadedChunks = downloadedChunks,
            resumeToken = resumeToken,
            lastModified = lastModified,
            expectedChecksum = expectedChecksum
        )

        /**
         * Create a Paused state.
         */
        fun paused(
            totalBytes: Long,
            downloadedBytes: Long,
            resumeToken: String? = null
        ): DownloadState = Paused(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            resumeToken = resumeToken ?: ""
        )

        /**
         * Create an Interrupted state.
         */
        fun interrupted(
            totalBytes: Long,
            downloadedBytes: Long,
            resumeToken: String? = null,
            error: DownloadError? = null
        ): DownloadState = Interrupted(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            resumeToken = resumeToken ?: "",
            error = error ?: DownloadError.unknown("Interrupted")
        )

        /**
         * Create a Cancelled state.
         */
        fun cancelled(
            totalBytes: Long,
            downloadedBytes: Long
        ): DownloadState = Cancelled(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes
        )

        /**
         * Create a Completed state.
         */
        fun completed(
            totalBytes: Long,
            downloadedBytes: Long,
            checksum: String,
            checksumAlgorithm: String,
            durationMs: Long
        ): DownloadState = Completed(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            checksum = checksum,
            checksumAlgorithm = checksumAlgorithm,
            durationMs = durationMs
        )

        /**
         * Create a Failed state.
         */
        fun failed(
            totalBytes: Long,
            downloadedBytes: Long,
            error: DownloadError
        ): DownloadState = Failed(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            error = error
        )

        /**
         * Create a Verified state.
         */
        fun verified(
            totalBytes: Long,
            downloadedBytes: Long,
            checksum: String,
            checksumAlgorithm: String
        ): DownloadState = Verified(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            checksum = checksum,
            checksumAlgorithm = checksumAlgorithm
        )

        /**
         * Create a Staged state.
         */
        fun staged(
            totalBytes: Long,
            downloadedBytes: Long,
            checksum: String,
            checksumAlgorithm: String,
            stagedPath: String
        ): DownloadState = Staged(
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            checksum = checksum,
            checksumAlgorithm = checksumAlgorithm,
            stagedPath = stagedPath
        )
    }
}

/**
 * Download error types.
 */
sealed class DownloadError(open val message: String = "") {
    /**
     * Network-related error.
     */
    data class NetworkError(
        override val message: String,
        val isTimeout: Boolean = false,
        val isConnectionLost: Boolean = false
    ) : DownloadError(message)

    /**
     * HTTP error response.
     */
    data class HttpError(
        val statusCode: Int,
        override val message: String
    ) : DownloadError(message)

    /**
     * Checksum mismatch error.
     */
    data class ChecksumMismatch(
        val expected: String,
        val actual: String,
        val algorithm: String
    ) : DownloadError("Checksum mismatch: expected $expected, actual $actual")

    /**
     * Insufficient storage error.
     */
    data class InsufficientStorage(
        val required: Long,
        val available: Long
    ) : DownloadError("Insufficient storage: required $required, available $available")

    /**
     * Permission error.
     */
    data class PermissionError(
        override val message: String
    ) : DownloadError(message)

    /**
     * Invalid resume token error.
     */
    data class InvalidResumeToken(
        override val message: String
    ) : DownloadError(message)

    /**
     * Unsupported protocol error.
     */
    data class UnsupportedProtocol(
        val protocol: String
    ) : DownloadError("Unsupported protocol: $protocol")

    /**
     * Unknown error.
     */
    data class UnknownError(
        override val message: String
    ) : DownloadError(message)

    companion object {
        fun network(message: String): NetworkError = NetworkError(message = message)
        /**
         * Create a network timeout error.
         */
        fun timeout(message: String = "Download timeout"): NetworkError =
            NetworkError(message = message, isTimeout = true)

        /**
         * Create a connection lost error.
         */
        fun connectionLost(message: String = "Connection lost"): NetworkError =
            NetworkError(message = message, isConnectionLost = true)

        /**
         * Create an HTTP error.
         */
        fun http(statusCode: Int, message: String = "HTTP error"): HttpError =
            HttpError(statusCode = statusCode, message = message)

        /**
         * Create a checksum mismatch error.
         */
        fun checksumMismatch(expected: String, actual: String, algorithm: String): ChecksumMismatch =
            ChecksumMismatch(expected = expected, actual = actual, algorithm = algorithm)

        /**
         * Create an insufficient storage error.
         */
        fun insufficientStorage(required: Long, available: Long): InsufficientStorage =
            InsufficientStorage(required = required, available = available)

        /**
         * Create a permission error.
         */
        fun permission(message: String): PermissionError =
            PermissionError(message = message)

        /**
         * Create an invalid resume token error.
         */
        fun invalidResumeToken(message: String): InvalidResumeToken =
            InvalidResumeToken(message = message)

        /**
         * Create an unsupported protocol error.
         */
        fun unsupportedProtocol(protocol: String): UnsupportedProtocol =
            UnsupportedProtocol(protocol = protocol)

        /**
         * Create an unknown error.
         */
        fun unknown(message: String): UnknownError =
            UnknownError(message = message)
    }
}

/**
 * Extension function to check if a download can be resumed.
 */
fun DownloadState.canResume(): Boolean {
    return when (this) {
        is DownloadState.Interrupted -> true
        is DownloadState.Paused -> true
        is DownloadState.InProgress -> true
        else -> false
    }
}

/**
 * Extension function to check if a download is complete.
 */
fun DownloadState.isComplete(): Boolean {
    return this is DownloadState.Completed || this is DownloadState.Verified || this is DownloadState.Staged
}

/**
 * Extension function to check if a download is failed.
 */
fun DownloadState.isFailed(): Boolean {
    return this is DownloadState.Failed
}

/**
 * Extension function to check if a download is cancelled.
 */
fun DownloadState.isCancelled(): Boolean {
    return this is DownloadState.Cancelled
}

/**
 * Extension function to get the percentage complete.
 */
fun DownloadState.progressPercentage(): Double {
    return when (this) {
        is DownloadState.NotStarted -> 0.0
        is DownloadState.InProgress -> if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes) * 100.0 else 0.0
        is DownloadState.Paused -> if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes) * 100.0 else 0.0
        is DownloadState.Interrupted -> if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes) * 100.0 else 0.0
        is DownloadState.Cancelled -> if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes) * 100.0 else 0.0
        is DownloadState.Failed -> if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes) * 100.0 else 0.0
        is DownloadState.Completed,
        is DownloadState.Verified,
        is DownloadState.Staged -> 100.0
    }
}

/**
 * Extension function to get the download speed in bytes per second.
 */
fun DownloadState.downloadSpeed(): Double {
    return when (this) {
        is DownloadState.Completed -> {
            if (durationMs > 0) {
                downloadedBytes.toDouble() / (durationMs / 1000.0)
            } else {
                0.0
            }
        }
        else -> 0.0
    }
}
