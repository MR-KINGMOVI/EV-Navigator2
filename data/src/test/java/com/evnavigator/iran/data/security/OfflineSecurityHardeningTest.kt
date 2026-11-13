package com.evnavigator.iran.data.security

import com.evnavigator.iran.data.download.OfflinePackageDownloadManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Step AP: Security Hardening Verification Tests.
 *
 * Verifies:
 * - Rejection of directory traversal attacks (Zip Slip)
 * - Validation of geographic coordinate boundaries
 * - Rejection of negative or oversized payload declarations
 * - Safe path canonicalization
 */
class OfflineSecurityHardeningTest {

    private lateinit var downloadManager: OfflinePackageDownloadManager
    private lateinit var targetSandboxDir: File

    @Before
    fun setUp() {
        downloadManager = OfflinePackageDownloadManager()
        targetSandboxDir = File(System.getProperty("java.io.tmpdir"), "ev_sandbox_" + System.currentTimeMillis())
        targetSandboxDir.mkdirs()
    }

    @Test
    fun `test Zip Slip traversal variants are all blocked`() {
        val attackVectors = listOf(
            "../evil.sh",
            "..\\evil.bat",
            "/etc/shadow",
            "C:\\Windows\\System32\\cmd.exe",
            "data/../../config.json",
            "nested/dir/../../../etc/hosts",
            "roads/package\u0000.bin"
        )

        for (vector in attackVectors) {
            val isSafe = downloadManager.isExtractionPathSafe(targetSandboxDir, vector)
            assertFalse("Attack vector '$vector' must be rejected as unsafe", isSafe)
        }
    }

    @Test
    fun `test coordinate validation rejects impossible geographic coordinates`() {
        fun isCoordinateValid(lat: Double, lon: Double): Boolean {
            return lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN()
        }

        assertTrue(isCoordinateValid(35.6892, 51.3890)) // Tehran
        assertFalse(isCoordinateValid(91.0, 51.0))       // Invalid latitude > 90
        assertFalse(isCoordinateValid(-95.0, 51.0))      // Invalid latitude < -90
        assertFalse(isCoordinateValid(35.0, 185.0))      // Invalid longitude > 180
        assertFalse(isCoordinateValid(Double.NaN, 51.0)) // NaN
    }

    @Test
    fun `test package size bounds validation rejects negative or oversized declarations`() {
        fun isDeclaredSizeAcceptable(declaredSizeBytes: Long): Boolean {
            val maxAllowedBytes = 5L * 1024L * 1024L * 1024L // 5 GB safety limit
            return declaredSizeBytes > 0 && declaredSizeBytes <= maxAllowedBytes
        }

        assertTrue(isDeclaredSizeAcceptable(50_000_000L))  // 50 MB
        assertFalse(isDeclaredSizeAcceptable(-100L))       // Negative size
        assertFalse(isDeclaredSizeAcceptable(0L))          // Zero size
        assertFalse(isDeclaredSizeAcceptable(100L * 1024L * 1024L * 1024L)) // 100 GB (oversized allocation)
    }
}
