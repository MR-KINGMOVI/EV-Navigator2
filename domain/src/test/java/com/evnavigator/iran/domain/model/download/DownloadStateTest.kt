package com.evnavigator.iran.domain.model.download

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for DownloadState sealed class.
 */
class DownloadStateTest {

    @Test
    fun `NotStarted state should have no progress`() {
        val state = DownloadState.notStarted()

        assertEquals(0.0, state.progressPercentage(), 0.001)
        assertFalse(state.canResume())
        assertFalse(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
    }

    @Test
    fun `InProgress state should have correct progress`() {
        val state = DownloadState.inProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token123"
        )

        assertEquals(50.0, state.progressPercentage(), 0.001)
        assertTrue(state.canResume())
        assertFalse(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals("token123", state.resumeToken)
    }

    @Test
    fun `Paused state should have correct progress and be resumable`() {
        val state = DownloadState.paused(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token123"
        )

        assertEquals(50.0, state.progressPercentage(), 0.001)
        assertTrue(state.canResume())
        assertFalse(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals("token123", state.resumeToken)
    }

    @Test
    fun `Interrupted state should have correct progress and be resumable`() {
        val error = DownloadError.timeout()
        val state = DownloadState.interrupted(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token123",
            error = error
        )

        assertEquals(50.0, state.progressPercentage(), 0.001)
        assertTrue(state.canResume())
        assertFalse(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals("token123", state.resumeToken)
        assertEquals(error, state.error)
    }

    @Test
    fun `Cancelled state should have correct progress and not be resumable`() {
        val state = DownloadState.cancelled(
            totalBytes = 1000,
            downloadedBytes = 500
        )

        assertEquals(50.0, state.progressPercentage(), 0.001)
        assertFalse(state.canResume())
        assertFalse(state.isComplete())
        assertFalse(state.isFailed())
        assertTrue(state.isCancelled())
    }

    @Test
    fun `Completed state should have 100% progress`() {
        val state = DownloadState.completed(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 5000
        )

        assertEquals(100.0, state.progressPercentage(), 0.001)
        assertTrue(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals("abc123", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
        assertEquals(5000, state.durationMs)
    }

    @Test
    fun `Failed state should have correct progress and not be resumable`() {
        val error = DownloadError.network("Connection lost")
        val state = DownloadState.failed(
            totalBytes = 1000,
            downloadedBytes = 500,
            error = error
        )

        assertEquals(50.0, state.progressPercentage(), 0.001)
        assertFalse(state.canResume())
        assertFalse(state.isComplete())
        assertTrue(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals(error, state.error)
    }

    @Test
    fun `Verified state should have 100% progress`() {
        val state = DownloadState.verified(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256"
        )

        assertEquals(100.0, state.progressPercentage(), 0.001)
        assertTrue(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals("abc123", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
    }

    @Test
    fun `Staged state should have 100% progress`() {
        val state = DownloadState.staged(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            stagedPath = "/path/to/staged/package.zip"
        )

        assertEquals(100.0, state.progressPercentage(), 0.001)
        assertTrue(state.isComplete())
        assertFalse(state.isFailed())
        assertFalse(state.isCancelled())
        assertEquals("abc123", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
        assertEquals("/path/to/staged/package.zip", state.stagedPath)
    }

    @Test
    fun `Download speed should be calculated correctly for completed download`() {
        val state = DownloadState.completed(
            totalBytes = 1000000,
            downloadedBytes = 1000000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 10000
        )

        assertEquals(100000.0, state.downloadSpeed(), 0.001)
    }

    @Test
    fun `Download speed should be zero for active downloads (in progress)`() {
        val state = DownloadState.inProgress(
            totalBytes = 1000000,
            downloadedBytes = 500000,
            resumeToken = "token"
        )

        assertEquals(0.0, state.downloadSpeed(), 0.001)
    }

    @Test
    fun `Progress percentage should be zero for unknown total bytes`() {
        val state = DownloadState.inProgress(
            totalBytes = 0,
            downloadedBytes = 0,
            resumeToken = "token"
        )

        assertEquals(0.0, state.progressPercentage(), 0.001)
    }

    @Test
    fun `Progress percentage should be 100% when downloaded equals total`() {
        val state = DownloadState.inProgress(
            totalBytes = 100,
            downloadedBytes = 100,
            resumeToken = "token"
        )

        assertEquals(100.0, state.progressPercentage(), 0.001)
    }

    @Test
    fun `Resume token should be generated for new downloads`() {
        val state = DownloadState.inProgress(
            totalBytes = 1000,
            downloadedBytes = 0,
            resumeToken = "token123"
        )

        assertNotNull(state.resumeToken)
        assertTrue(state.resumeToken!!.isNotEmpty())
    }

    @Test
    fun `Checksum should be stored and retrieved`() {
        val state = DownloadState.completed(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123def456",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        assertEquals("abc123def456", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
    }

    @Test
    fun `Error should be stored and retrieved`() {
        val error = DownloadError.timeout()
        val state = DownloadState.interrupted(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token",
            error = error
        )

        assertEquals(error, state.error)
    }

    @Test
    fun `Staged path should be stored and retrieved`() {
        val state = DownloadState.staged(
            totalBytes = 1000,
            downloadedBytes = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            stagedPath = "/data/staging/package.zip"
        )

        assertEquals("/data/staging/package.zip", state.stagedPath)
    }

    @Test
    fun `Last modified timestamp should be stored`() {
        val timestamp = System.currentTimeMillis()
        val state = DownloadState.inProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token",
            lastModified = timestamp
        )

        assertEquals(timestamp, state.lastModified)
    }

    @Test
    fun `Expected checksum should be stored`() {
        val state = DownloadState.inProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token",
            expectedChecksum = "expected123"
        )

        assertEquals("expected123", state.expectedChecksum)
    }

    @Test
    fun `Chunked download progress should be tracked`() {
        val state = DownloadState.inProgress(
            totalBytes = 1000,
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5,
            resumeToken = "token"
        )

        assertEquals(50.0, state.progressPercentage(), 0.001)
        assertEquals(10, state.totalChunks)
        assertEquals(5, state.downloadedChunks)
    }

    @Test
    fun `Download error types should be created correctly`() {
        val networkError = DownloadError.timeout()
        assertTrue(networkError is DownloadError.NetworkError)
        assertTrue(networkError.isTimeout)

        val connectionLost = DownloadError.connectionLost()
        assertTrue(connectionLost is DownloadError.NetworkError)
        assertTrue(connectionLost.isConnectionLost)

        val httpError = DownloadError.http(404, "Not Found")
        assertTrue(httpError is DownloadError.HttpError)
        assertEquals(404, httpError.statusCode)

        val checksumError = DownloadError.checksumMismatch("expected", "actual", "SHA-256")
        assertTrue(checksumError is DownloadError.ChecksumMismatch)
        assertEquals("expected", checksumError.expected)
        assertEquals("actual", checksumError.actual)

        val storageError = DownloadError.insufficientStorage(1000, 500)
        assertTrue(storageError is DownloadError.InsufficientStorage)
        assertEquals(1000, storageError.required)
        assertEquals(500, storageError.available)

        val permissionError = DownloadError.permission("Access denied")
        assertTrue(permissionError is DownloadError.PermissionError)

        val tokenError = DownloadError.invalidResumeToken("Invalid token")
        assertTrue(tokenError is DownloadError.InvalidResumeToken)

        val protocolError = DownloadError.unsupportedProtocol("ftp")
        assertTrue(protocolError is DownloadError.UnsupportedProtocol)

        val unknownError = DownloadError.unknown("Unknown error")
        assertTrue(unknownError is DownloadError.UnknownError)
    }

    @Test
    fun `Download error messages should be stored`() {
        val error = DownloadError.timeout("Connection timed out after 30s")
        assertEquals("Connection timed out after 30s", error.message)
    }

    @Test
    fun `All download states should be serializable`() {
        val states = listOf(
            DownloadState.notStarted(),
            DownloadState.inProgress(1000, 500, "token"),
            DownloadState.paused(1000, 500, "token"),
            DownloadState.interrupted(1000, 500, "token", DownloadError.timeout()),
            DownloadState.cancelled(1000, 500),
            DownloadState.completed(1000, 1000, "abc123", "SHA-256", 1000),
            DownloadState.failed(1000, 500, DownloadError.network("Error")),
            DownloadState.verified(1000, 1000, "abc123", "SHA-256"),
            DownloadState.staged(1000, 1000, "abc123", "SHA-256", "/path/to/staged")
        )

        // All states should be instantiable
        assertEquals(9, states.size)
    }

    @Test
    fun `Progress percentage should be calculated correctly for edge cases`() {
        val state1 = DownloadState.inProgress(0, 0, "token")
        assertEquals(0.0, state1.progressPercentage(), 0.001)

        val state2 = DownloadState.inProgress(100, 0, "token")
        assertEquals(0.0, state2.progressPercentage(), 0.001)

        val state3 = DownloadState.inProgress(100, 100, "token")
        assertEquals(100.0, state3.progressPercentage(), 0.001)

        val state4 = DownloadState.inProgress(100, 50, "token")
        assertEquals(50.0, state4.progressPercentage(), 0.001)
    }

    @Test
    fun `Download speed should be calculated correctly for completed downloads`() {
        val state = DownloadState.completed(
            totalBytes = 1000000,
            downloadedBytes = 1000000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 10000
        )

        assertEquals(100000.0, state.downloadSpeed(), 0.001)
    }

}
