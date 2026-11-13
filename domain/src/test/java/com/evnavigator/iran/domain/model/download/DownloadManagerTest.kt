package com.evnavigator.iran.domain.model.download

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Tests for DownloadManager.
 */
class DownloadManagerTest {

    private lateinit var downloadManager: DownloadManager
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "download_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        downloadManager = DownloadManager(
            downloadStoragePath = tempDir.absolutePath,
            stagingAreaPath = tempDir.absolutePath
        )

        // Load saved downloads
        downloadManager.loadSavedDownloads()
    }

    @After
    fun cleanup() {
        // Delete temp directory
        tempDir.deleteRecursively()
    }

    @Test
    fun `Register download should create new download state`() {
        val state = downloadManager.registerDownload(
            downloadId = "test1",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = "abc123"
        )

        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
        assertNotNull(state.resumeToken)
        assertEquals("abc123", state.expectedChecksum)
    }

    @Test
    fun `Register download with resume token should resume interrupted download`() {
        downloadManager.saveDownloadState("test2", DownloadState.interrupted(1000, 500, "token123", DownloadError.timeout()))
        // Create an interrupted download
        val error = DownloadError.timeout()
        val interruptedState = DownloadState.interrupted(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token123",
            error = error
        )

        downloadManager.registerDownload(
            downloadId = "test2",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = "token123",
            expectedChecksum = null
        )

        val state = downloadManager.getDownloadState("test2")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(1000, state.totalBytes)
        assertEquals(500, state.downloadedBytes)
        assertEquals("token123", state.resumeToken)
    }

    @Test
    fun `Register download with invalid resume token should start fresh`() {
        downloadManager.saveDownloadState("test3", DownloadState.interrupted(1000, 500, "token123", DownloadError.timeout()))
        // Create an interrupted download
        val error = DownloadError.timeout()
        val interruptedState = DownloadState.interrupted(
            totalBytes = 1000,
            downloadedBytes = 500,
            resumeToken = "token123",
            error = error
        )

        downloadManager.registerDownload(
            downloadId = "test3",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = "invalid_token",
            expectedChecksum = null
        )

        val state = downloadManager.getDownloadState("test3")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
        assertNotNull(state.resumeToken)
    }

    @Test
    fun `Update progress should update download state`() {
        downloadManager.registerDownload(
            downloadId = "test4",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.updateProgress(
            downloadId = "test4",
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5
        )

        val state = downloadManager.getDownloadState("test4")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(500, state.downloadedBytes)
        assertEquals(10, state.totalChunks)
        assertEquals(5, state.downloadedChunks)
    }

    @Test
    fun `Pause download should pause the download`() {
        downloadManager.registerDownload(
            downloadId = "test5",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.pauseDownload("test5")

        val state = downloadManager.getDownloadState("test5")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Paused)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
        assertNotNull(state.resumeToken)
    }

    @Test
    fun `Interrupt download should mark as interrupted`() {
        downloadManager.registerDownload(
            downloadId = "test6",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.interruptDownload("test6", DownloadError.timeout())

        val state = downloadManager.getDownloadState("test6")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Interrupted)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
        assertNotNull(state.resumeToken)
        assertTrue(state.error is DownloadError.NetworkError)
    }

    @Test
    fun `Cancel download should cancel the download`() {
        downloadManager.registerDownload(
            downloadId = "test7",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.cancelDownload("test7")

        val state = downloadManager.getDownloadState("test7")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Cancelled)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
    }

    @Test
    fun `Complete download should mark as completed`() {
        downloadManager.registerDownload(
            downloadId = "test8",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.completeDownload(
            downloadId = "test8",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        val state = downloadManager.getDownloadState("test8")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Completed)
        assertEquals(1000, state.totalBytes)
        assertEquals(1000, state.downloadedBytes)
        assertEquals("abc123", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
        assertEquals(1000, state.durationMs)
    }

    @Test
    fun `Fail download should mark as failed`() {
        downloadManager.registerDownload(
            downloadId = "test9",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.failDownload("test9", DownloadError.network("Connection lost"))

        val state = downloadManager.getDownloadState("test9")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Failed)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
        assertTrue(state.error is DownloadError.NetworkError)
    }

    @Test
    fun `Verify download should mark as verified`() {
        downloadManager.registerDownload(
            downloadId = "test10",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.completeDownload(
            downloadId = "test10",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        downloadManager.verifyDownload(
            downloadId = "test10",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256"
        )

        val state = downloadManager.getDownloadState("test10")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Verified)
        assertEquals(1000, state.totalBytes)
        assertEquals(1000, state.downloadedBytes)
        assertEquals("abc123", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
    }

    @Test
    fun `Stage download should mark as staged`() {
        downloadManager.registerDownload(
            downloadId = "test11",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.completeDownload(
            downloadId = "test11",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        downloadManager.verifyDownload(
            downloadId = "test11",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256"
        )

        downloadManager.stageDownload(
            downloadId = "test11",
            stagedPath = "/data/staging/package.zip"
        )

        val state = downloadManager.getDownloadState("test11")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Staged)
        assertEquals(1000, state.totalBytes)
        assertEquals(1000, state.downloadedBytes)
        assertEquals("abc123", state.checksum)
        assertEquals("SHA-256", state.checksumAlgorithm)
        assertEquals("/data/staging/package.zip", state.stagedPath)
    }

    @Test
    fun `Get download progress should return StateFlow`() = runTest {
        downloadManager.registerDownload(
            downloadId = "test12",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        val progressFlow = downloadManager.getDownloadProgress("test12")!!
        assertNotNull(progressFlow)

        val progress = progressFlow.first()
        assertNotNull(progress)
        assertEquals(1000, progress.totalBytes)
        assertEquals(0, progress.downloadedBytes)
    }

    @Test
    fun `Can resume download should return true for interrupted download`() {
        downloadManager.registerDownload(
            downloadId = "test13",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.interruptDownload("test13", DownloadError.timeout())

        assertTrue(downloadManager.canResumeDownload("test13"))
    }

    @Test
    fun `Can resume download should return true for paused download`() {
        downloadManager.registerDownload(
            downloadId = "test14",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.pauseDownload("test14")

        assertTrue(downloadManager.canResumeDownload("test14"))
    }

    @Test
    fun `Can resume download should return false for completed download`() {
        downloadManager.registerDownload(
            downloadId = "test15",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.completeDownload(
            downloadId = "test15",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        assertFalse(downloadManager.canResumeDownload("test15"))
    }

    @Test
    fun `Resume download should resume interrupted download`() {
        downloadManager.registerDownload(
            downloadId = "test16",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.interruptDownload("test16", DownloadError.timeout())

        val resumeResult = downloadManager.resumeDownload("test16")
        assertTrue(resumeResult)

        val state = downloadManager.getDownloadState("test16")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
    }

    @Test
    fun `Resume download should return false for non-resumable state`() {
        downloadManager.registerDownload(
            downloadId = "test17",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.completeDownload(
            downloadId = "test17",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        val resumeResult = downloadManager.resumeDownload("test17")
        assertFalse(resumeResult)
    }

    @Test
    fun `Get all download IDs should return all downloads`() {
        downloadManager.registerDownload(
            downloadId = "test18",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.registerDownload(
            downloadId = "test19",
            url = "https://example.com/package2.zip",
            totalBytes = 2000,
            resumeToken = null,
            expectedChecksum = null
        )

        val ids = downloadManager.getAllDownloadIds()
        assertEquals(2, ids.size)
        assertTrue(ids.contains("test18"))
        assertTrue(ids.contains("test19"))
    }

    @Test
    fun `Get all downloads should return all download states`() {
        downloadManager.registerDownload(
            downloadId = "test20",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.completeDownload(
            downloadId = "test20",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )

        val downloads = downloadManager.getAllDownloads()
        assertEquals(1, downloads.size)
        assertTrue(downloads[0] is DownloadState.Completed)
    }

    @Test
    fun `Remove download should remove download and cleanup files`() {
        downloadManager.registerDownload(
            downloadId = "test21",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.removeDownload("test21")

        val state = downloadManager.getDownloadState("test21")
        assertNull(state)

        val progressFlow = downloadManager.getDownloadProgress("test21")
        assertNull(progressFlow)

        // Verify partial download file is cleaned up
        val partialFile = File("$tempDir/test21.partial")
        assertFalse(partialFile.exists())
    }

    @Test
    fun `Load saved downloads should restore interrupted downloads`() {
        // Create an interrupted download
        downloadManager.registerDownload(
            downloadId = "test22",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.interruptDownload("test22", DownloadError.timeout())

        // Create a new manager instance
        val newManager = DownloadManager(
            downloadStoragePath = tempDir.absolutePath,
            stagingAreaPath = tempDir.absolutePath
        )

        // Load saved downloads
        newManager.loadSavedDownloads()

        val state = newManager.getDownloadState("test22")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Interrupted)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
    }

    @Test
    fun `Calculate checksum should calculate SHA-256 checksum`() {
        val testFile = File(tempDir, "test_checksum.txt")
        testFile.writeText("Hello, World!")

        val checksum = downloadManager.calculateChecksum(testFile.absolutePath, "SHA-256")

        assertNotNull(checksum)
        assertEquals(64, checksum.length) // SHA-256 produces 64 hex characters
    }

    @Test
    fun `Calculate checksum should throw exception for non-existent file`() {
        assertThrows(IllegalArgumentException::class.java) {
            downloadManager.calculateChecksum("/nonexistent/file.txt")
        }
    }

    @Test
    fun `Progress flow should update when state changes`() = runTest {
        downloadManager.registerDownload(
            downloadId = "test23",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        val progressFlow = downloadManager.getDownloadProgress("test23")!!
        var progress = progressFlow.first()
        assertEquals(0, progress.downloadedBytes)

        downloadManager.updateProgress("test23", 500)
        progress = progressFlow.first()
        assertEquals(500, progress.downloadedBytes)

        downloadManager.completeDownload(
            downloadId = "test23",
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            durationMs = 1000
        )
        progress = progressFlow.first()
        assertEquals(1000, progress.downloadedBytes)
    }

    @Test
    fun `State should be persisted to disk`() {
        downloadManager.registerDownload(
            downloadId = "test24",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.interruptDownload("test24", DownloadError.timeout())

        // Verify state file was created
        val stateFile = File("$tempDir/test24.json")
        assertTrue(stateFile.exists())

        // Verify state file contains valid JSON
        val json = stateFile.readText()
        assertTrue(json.contains("\"type\": \"Interrupted\""))
        assertTrue(json.contains("\"totalBytes\": 1000"))
    }

    @Test
    fun `State should be loaded from disk`() {
        // Create an interrupted download
        downloadManager.registerDownload(
            downloadId = "test25",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.interruptDownload("test25", DownloadError.timeout())

        // Create a new manager instance
        val newManager = DownloadManager(
            downloadStoragePath = tempDir.absolutePath,
            stagingAreaPath = tempDir.absolutePath
        )

        // Load saved downloads
        newManager.loadSavedDownloads()

        val state = newManager.getDownloadState("test25")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.Interrupted)
        assertEquals(1000, state.totalBytes)
        assertEquals(0, state.downloadedBytes)
    }

    @Test
    fun `Invalid state file should be skipped`() {
        // Create an invalid state file
        val invalidFile = File("$tempDir/invalid.json")
        invalidFile.writeText("{ invalid json }")

        // Create a new manager instance
        val newManager = DownloadManager(
            downloadStoragePath = tempDir.absolutePath,
            stagingAreaPath = tempDir.absolutePath
        )

        // Load saved downloads
        newManager.loadSavedDownloads()

        // Verify invalid file was skipped
        val state = newManager.getDownloadState("invalid")
        assertNull(state)
    }

    @Test
    fun `Download with chunked progress should track chunks correctly`() {
        downloadManager.registerDownload(
            downloadId = "test26",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null
        )

        downloadManager.updateProgress(
            downloadId = "test26",
            downloadedBytes = 500,
            totalChunks = 10,
            downloadedChunks = 5
        )

        val state = downloadManager.getDownloadState("test26")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(10, state.totalChunks)
        assertEquals(5, state.downloadedChunks)
    }

    @Test
    fun `Download with expected checksum should store it`() {
        downloadManager.registerDownload(
            downloadId = "test27",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = "expected_checksum_123"
        )

        val state = downloadManager.getDownloadState("test27")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals("expected_checksum_123", state.expectedChecksum)
    }

    @Test
    fun `Download with last modified timestamp should store it`() {
        val timestamp = System.currentTimeMillis()
        downloadManager.registerDownload(
            downloadId = "test28",
            url = "https://example.com/package.zip",
            totalBytes = 1000,
            resumeToken = null,
            expectedChecksum = null,
            lastModified = timestamp
        )

        val state = downloadManager.getDownloadState("test28")!!
        assertNotNull(state)
        assertTrue(state is DownloadState.InProgress)
        assertEquals(timestamp, state.lastModified)
    }
}
