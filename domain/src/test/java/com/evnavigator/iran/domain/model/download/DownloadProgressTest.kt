package com.evnavigator.iran.domain.model.download

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for DownloadProgress data class.
 */
class DownloadProgressTest {

    @Test
    fun `Progress should be calculated correctly`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5,
            currentChunkIndex = 5,
            estimatedTimeRemainingMs = 30000,
            downloadSpeed = 100000.0
        )

        assertEquals(50.0, progress.percentage, 0.001)
        assertEquals(10, progress.totalChunks)
        assertEquals(5, progress.downloadedChunks)
        assertEquals(5, progress.currentChunkIndex)
        assertEquals(30000L, progress.estimatedTimeRemainingMs)
        assertEquals(100000.0, progress.downloadSpeed, 0.001)
    }

    @Test
    fun `Progress should be 0% when not started`() {
        val progress = DownloadProgress(
            totalBytes = 0,
            downloadedBytes = 0,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertEquals(0.0, progress.percentage, 0.001)
        assertFalse(progress.hasStarted)
        assertFalse(progress.isComplete)
        assertFalse(progress.isChunked)
        assertEquals(0.0, progress.chunkPercentage, 0.001)
    }

    @Test
    fun `Progress should be 100% when complete`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 1000,
            totalChunks = 10,
            downloadedChunks = 10,
            currentChunkIndex = 10,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertEquals(100.0, progress.percentage, 0.001)
        assertTrue(progress.hasStarted)
        assertTrue(progress.isComplete)
        assertTrue(progress.isChunked)
        assertEquals(100.0, progress.chunkPercentage, 0.001)
    }

    @Test
    fun `Progress should have started when downloadedBytes greater than 0`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 100,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertTrue(progress.hasStarted)
    }

    @Test
    fun `Progress should not be chunked when totalChunks is 0`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertFalse(progress.isChunked)
    }

    @Test
    fun `Progress should be chunked when totalChunks greater than 0`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5,
            currentChunkIndex = 5,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertTrue(progress.isChunked)
    }

    @Test
    fun `Chunk percentage should be calculated correctly`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5,
            currentChunkIndex = 5,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertEquals(50.0, progress.chunkPercentage, 0.001)
    }

    @Test
    fun `Progress string should be formatted correctly for non-chunked download`() {
        val progress = DownloadProgress(
            totalBytes = 1000000,
            downloadedBytes = 500000,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        val progressString = progress.getProgressString()
        assertTrue(progressString.contains("500.00 KB"))
        assertTrue(progressString.contains("1.00 MB"))
        assertTrue(progressString.contains("50.0%"))
    }

    @Test
    fun `Progress string should be formatted correctly for chunked download`() {
        val progress = DownloadProgress(
            totalBytes = 1000000,
            downloadedBytes = 500000,
            totalChunks = 10,
            downloadedChunks = 5,
            currentChunkIndex = 5,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        val progressString = progress.getProgressString()
        assertTrue(progressString.contains("500.00 KB"))
        assertTrue(progressString.contains("1.00 MB"))
        assertTrue(progressString.contains("50.0%"))
        assertTrue(progressString.contains("5/10 chunks"))
    }

    @Test
    fun `Progress string should be formatted correctly for small bytes`() {
        val progress = DownloadProgress(
            totalBytes = 500,
            downloadedBytes = 250,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        val progressString = progress.getProgressString()
        assertTrue(progressString.contains("250 B"))
        assertTrue(progressString.contains("500 B"))
        assertTrue(progressString.contains("50.0%"))
    }

    @Test
    fun `Progress string should be formatted correctly for large bytes`() {
        val progress = DownloadProgress(
            totalBytes = 1073741824L, // 1 GB
            downloadedBytes = 536870912L, // 512 MB
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        val progressString = progress.getProgressString()
        assertTrue(progressString.contains("512.00 MB"))
        assertTrue(progressString.contains("1.00 GB"))
        assertTrue(progressString.contains("50.0%"))
    }

    @Test
    fun `Speed string should be formatted correctly for B per second`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 500.0
        )

        val speedString = progress.getSpeedString()
        assertTrue(speedString.contains("500.00 B/s"))
    }

    @Test
    fun `Speed string should be formatted correctly for KB per second`() {
        val progress = DownloadProgress(
            totalBytes = 1000000,
            downloadedBytes = 500000,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 1024.0
        )

        val speedString = progress.getSpeedString()
        assertTrue(speedString.contains("1.00 KB/s"))
    }

    @Test
    fun `Speed string should be formatted correctly for MB per second`() {
        val progress = DownloadProgress(
            totalBytes = 1000000000,
            downloadedBytes = 500000000,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 1024.0 * 1024.0
        )

        val speedString = progress.getSpeedString()
        assertTrue(speedString.contains("1.00 MB/s"))
    }

    @Test
    fun `Speed string should be formatted correctly for GB per second`() {
        val progress = DownloadProgress(
            totalBytes = 1000000000000L,
            downloadedBytes = 500000000000L,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 1024.0 * 1024.0 * 1024.0
        )

        val speedString = progress.getSpeedString()
        assertTrue(speedString.contains("1.00 GB/s"))
    }

    @Test
    fun `Speed string should be unknown for zero speed`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        val speedString = progress.getSpeedString()
        assertEquals("Unknown", speedString)
    }

    @Test
    fun `Time remaining string should be formatted correctly for seconds`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = 30000,
            downloadSpeed = 100000.0
        )

        val timeString = progress.getTimeRemainingString()
        assertTrue(timeString.contains("30s"))
    }

    @Test
    fun `Time remaining string should be formatted correctly for minutes`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = 120000,
            downloadSpeed = 100000.0
        )

        val timeString = progress.getTimeRemainingString()
        assertTrue(timeString.contains("2.0m"))
    }

    @Test
    fun `Time remaining string should be formatted correctly for hours`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = 3600000L,
            downloadSpeed = 100000.0
        )

        val timeString = progress.getTimeRemainingString()
        assertTrue(timeString.contains("1.0h"))
    }

    @Test
    fun `Time remaining string should be formatted correctly for days`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = 86400000,
            downloadSpeed = 100000.0
        )

        val timeString = progress.getTimeRemainingString()
        assertTrue(timeString.contains("1.0d"))
    }

    @Test
    fun `Time remaining string should be unknown when not estimated`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        val timeString = progress.getTimeRemainingString()
        assertEquals("Unknown", timeString)
    }

    @Test
    fun `Time remaining string should be unknown when speed is zero`() {
        val progress = DownloadProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = 30000,
            downloadSpeed = 0.0
        )

        val timeString = progress.getTimeRemainingString()
        assertEquals("Unknown", timeString)
    }

    @Test
    fun `Format bytes should handle B correctly`() {
        assertEquals("500 B", DownloadProgress.formatBytes(500))
    }

    @Test
    fun `Format bytes should handle KB correctly`() {
        assertEquals("1.00 KB", DownloadProgress.formatBytes(1024))
    }

    @Test
    fun `Format bytes should handle MB correctly`() {
        assertEquals("1.00 MB", DownloadProgress.formatBytes(1024 * 1024))
    }

    @Test
    fun `Format bytes should handle GB correctly`() {
        assertEquals("1.00 GB", DownloadProgress.formatBytes(1024 * 1024 * 1024))
    }

    @Test
    fun `Format bytes should handle very small bytes correctly`() {
        assertEquals("0.50 B", DownloadProgress.formatBytes(0.5))
    }

    @Test
    fun `Format bytes should handle very large bytes correctly`() {
        assertEquals("1024.00 GB", DownloadProgress.formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `Completed progress should have zero speed`() {
        val progress = DownloadProgress.completed(1000, 1000)

        assertEquals(0.0, progress.downloadSpeed, 0.001)
    }

    @Test
    fun `Completed progress should have zero estimated time`() {
        val progress = DownloadProgress.completed(1000, 1000)

        assertNull(progress.estimatedTimeRemainingMs)
    }

    @Test
    fun `From download state should convert NotStarted correctly`() {
        val state = DownloadState.notStarted()
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(0, progress.totalBytes)
        assertEquals(0, progress.downloadedBytes)
        assertFalse(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `From download state should convert InProgress correctly`() {
        val state = DownloadState.inProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5,
            resumeToken = "token"
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(500, progress.downloadedBytes)
        assertEquals(10, progress.totalChunks)
        assertEquals(5, progress.downloadedChunks)
        assertEquals(5, progress.currentChunkIndex)
        assertTrue(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `From download state should convert Paused correctly`() {
        val state = DownloadState.paused(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token"
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(500, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `From download state should convert Interrupted correctly`() {
        val state = DownloadState.interrupted(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token",
            error = DownloadError.timeout()
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(500, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `From download state should convert Cancelled correctly`() {
        val state = DownloadState.cancelled(1000, 500)
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(500, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `From download state should convert Completed correctly`() {
        val state = DownloadState.completed(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(1000, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `From download state should convert Verified correctly`() {
        val state = DownloadState.verified(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256"
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(1000, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `From download state should convert Staged correctly`() {
        val state = DownloadState.staged(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            stagedPath = "/path/to/staged"
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(1000, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `From download state should convert Failed correctly`() {
        val state = DownloadState.failed(
            totalBytes = 1000,
            downloadedBytes = 500,
            error = DownloadError.network("Error")
        )
        val progress = DownloadProgress.fromDownloadState(state)

        assertEquals(1000, progress.totalBytes)
        assertEquals(500, progress.downloadedBytes)
        assertTrue(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `Progress should handle zero total bytes correctly`() {
        val progress = DownloadProgress(
            totalBytes = 0,
            downloadedBytes = 0,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertEquals(0.0, progress.percentage, 0.001)
        assertFalse(progress.hasStarted)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `Progress should handle downloaded equals total bytes correctly`() {
        val progress = DownloadProgress(
            totalBytes = 100,
            downloadedBytes = 100,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertEquals(100.0, progress.percentage, 0.001)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `Progress should handle downloaded less than total bytes correctly`() {
        val progress = DownloadProgress(
            totalBytes = 100,
            downloadedBytes = 50,
            totalChunks = 0,
            downloadedChunks = 0,
            currentChunkIndex = 0,
            estimatedTimeRemainingMs = null,
            downloadSpeed = 0.0
        )

        assertEquals(50.0, progress.percentage, 0.001)
        assertTrue(progress.hasStarted)
        assertFalse(progress.isComplete)
    }
}
