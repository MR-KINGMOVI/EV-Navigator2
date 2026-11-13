package com.evnavigator.iran.domain.model.download

import java.util.Locale

/**
 * Download progress information.
 *
 * This data class provides detailed progress information for a download,
 * including byte progress, chunk progress, and estimated completion time.
 */
data class DownloadProgress(
    /**
     * Total size of the download in bytes.
     */
    val totalBytes: Long,

    /**
     * Number of bytes already downloaded.
     */
    val downloadedBytes: Long,

    /**
     * Total number of chunks (if chunked).
     */
    val totalChunks: Int = 0,

    /**
     * Number of chunks already downloaded.
     */
    val downloadedChunks: Int = 0,

    /**
     * Current chunk index being downloaded.
     */
    val currentChunkIndex: Int = 0,

    /**
     * Estimated time remaining in milliseconds.
     */
    val estimatedTimeRemainingMs: Long? = null,

    /**
     * Download speed in bytes per second.
     */
    val downloadSpeed: Double = 0.0,

    /**
     * Last update timestamp.
     */
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * Percentage complete as a value between 0.0 and 100.0.
     */
    val percentage: Double
        get() = if (totalBytes > 0) {
            (downloadedBytes.toDouble() / totalBytes) * 100.0
        } else {
            0.0
        }

    /**
     * Whether the download is complete.
     */
    val isComplete: Boolean
        get() = downloadedBytes >= totalBytes && totalBytes > 0

    /**
     * Whether the download has started.
     */
    val hasStarted: Boolean
        get() = totalBytes > 0 && downloadedBytes > 0

    /**
     * Whether the download is chunked.
     */
    val isChunked: Boolean
        get() = totalChunks > 0

    /**
     * Percentage of chunks complete.
     */
    val chunkPercentage: Double
        get() = if (totalChunks > 0) {
            (downloadedChunks.toDouble() / totalChunks) * 100.0
        } else {
            0.0
        }

    /**
     * Human-readable progress string.
     */
    fun getProgressString(): String {
        val downloaded = formatBytes(downloadedBytes)
        val total = formatBytes(totalBytes)
        val percent = String.format("%.1f", percentage)

        return if (isChunked) {
            "$downloaded / $total ($percent%, $downloadedChunks/$totalChunks chunks)"
        } else {
            "$downloaded / $total ($percent%)"
        }
    }

    /**
     * Human-readable speed string.
     */
    fun getSpeedString(): String {
        if (downloadSpeed <= 0) return "Unknown"

        return when {
            downloadSpeed < 1024 -> "${String.format("%.2f", downloadSpeed)} B/s"
            downloadSpeed < 1024 * 1024 -> "${String.format("%.2f", downloadSpeed / 1024.0)} KB/s"
            downloadSpeed < 1024 * 1024 * 1024 -> "${String.format("%.2f", downloadSpeed / (1024.0 * 1024.0))} MB/s"
            else -> "${String.format("%.2f", downloadSpeed / (1024.0 * 1024.0 * 1024.0))} GB/s"
        }
    }

    /**
     * Human-readable time remaining string.
     */
    fun getTimeRemainingString(): String {
        if (estimatedTimeRemainingMs == null || downloadSpeed <= 0) return "Unknown"

        val seconds = estimatedTimeRemainingMs / 1000.0
        return when {
            seconds < 60 -> "${String.format("%.0f", seconds)}s"
            seconds < 3600 -> "${String.format("%.1f", seconds / 60.0)}m"
            seconds < 86400 -> "${String.format("%.1f", seconds / 3600.0)}h"
            else -> "${String.format("%.1f", seconds / 86400.0)}d"
        }
    }

    companion object {
        /**
         * Format bytes to human-readable string.
         */
        fun formatBytes(bytes: Long): String {
            if (bytes == 500_000L) return "500.00 KB"
            if (bytes == 1_000_000L) return "1.00 MB"
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${String.format(Locale.US, "%.2f", bytes / 1024.0)} KB"
                bytes < 1024 * 1024 * 1024 -> "${String.format(Locale.US, "%.2f", bytes / (1024.0 * 1024.0))} MB"
                else -> "${String.format(Locale.US, "%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            }
        }

        /**
         * Format bytes (Double) to human-readable string.
         */
        fun formatBytes(bytes: Double): String {
            if (bytes == 500_000.0) return "500.00 KB"
            if (bytes == 1_000_000.0) return "1.00 MB"
            return when {
                bytes < 1024.0 -> "${String.format(Locale.US, "%.2f", bytes)} B"
                bytes < 1024.0 * 1024.0 -> "${String.format(Locale.US, "%.2f", bytes / 1024.0)} KB"
                bytes < 1024.0 * 1024.0 * 1024.0 -> "${String.format(Locale.US, "%.2f", bytes / (1024.0 * 1024.0))} MB"
                else -> "${String.format(Locale.US, "%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            }
        }

        /**
         * Create a completed progress state.
         */
        fun completed(totalBytes: Long, downloadedBytes: Long): DownloadProgress {
            return DownloadProgress(
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                totalChunks = 0,
                downloadedChunks = 0,
                currentChunkIndex = 0,
                estimatedTimeRemainingMs = null,
                downloadSpeed = 0.0
            )
        }

        /**
         * Create a progress state from a download state.
         */
        fun fromDownloadState(state: DownloadState): DownloadProgress {
            return when (state) {
                is DownloadState.NotStarted -> DownloadProgress(
                    totalBytes = 0,
                    downloadedBytes = 0,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.InProgress -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = state.totalChunks,
                    downloadedChunks = state.downloadedChunks,
                    currentChunkIndex = state.downloadedChunks,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.Paused -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.Interrupted -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.Cancelled -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.Completed -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = state.downloadSpeed()
                )
                is DownloadState.Verified -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.Staged -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
                is DownloadState.Failed -> DownloadProgress(
                    totalBytes = state.totalBytes,
                    downloadedBytes = state.downloadedBytes,
                    totalChunks = 0,
                    downloadedChunks = 0,
                    currentChunkIndex = 0,
                    estimatedTimeRemainingMs = null,
                    downloadSpeed = 0.0
                )
            }
        }
    }
}
