package com.evnavigator.iran.data.download

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/**
 * Unit tests for OfflinePackageDownloadManager.
 * Verifies Zip-Slip path traversal rejection, checksum validation, and HTTP range response alignment.
 */
class OfflinePackageDownloaderTest {

    private lateinit var manager: OfflinePackageDownloadManager
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        manager = OfflinePackageDownloadManager()
        tempDir = File(System.getProperty("java.io.tmpdir"), "ev_test_staging_" + System.currentTimeMillis())
        tempDir.mkdirs()
    }

    @Test
    fun `test path traversal attack is detected and rejected`() {
        assertFalse(manager.isExtractionPathSafe(tempDir, "../../../etc/passwd"))
        assertFalse(manager.isExtractionPathSafe(tempDir, "data/../../secret.txt"))
        assertFalse(manager.isExtractionPathSafe(tempDir, "..\\windows\\system32"))
    }

    @Test
    fun `test legitimate extraction path is accepted`() {
        assertTrue(manager.isExtractionPathSafe(tempDir, "roads/network.bin"))
        assertTrue(manager.isExtractionPathSafe(tempDir, "manifest.json"))
        assertTrue(manager.isExtractionPathSafe(tempDir, "elevation/dem_35_51.bin"))
    }

    @Test
    fun `test sha256 checksum verification detects tampering`() {
        val originalPayload = "VALID_OFFLINE_PACKAGE_CONTENT".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val correctHash = digest.digest(originalPayload).joinToString("") { "%02x".format(it) }

        // Valid payload matches
        val validStream = ByteArrayInputStream(originalPayload)
        assertTrue(manager.verifyChecksum(validStream, correctHash))

        // Tampered payload fails
        val tamperedPayload = "CORRUPTED_PACKAGE_CONTENT".toByteArray(Charsets.UTF_8)
        val tamperedStream = ByteArrayInputStream(tamperedPayload)
        assertFalse(manager.verifyChecksum(tamperedStream, correctHash))
    }

    @Test
    fun `test range response header alignment validation`() {
        // Correct range alignment: expected 1024, received "bytes 1024-2047/5000"
        assertTrue(manager.isRangeResponseValid(1024L, "bytes 1024-2047/5000"))

        // Misaligned range: expected 1024, received start 0
        assertFalse(manager.isRangeResponseValid(1024L, "bytes 0-1023/5000"))

        // Malformed header
        assertFalse(manager.isRangeResponseValid(1024L, "invalid_header"))
        assertFalse(manager.isRangeResponseValid(1024L, null))
    }
}
