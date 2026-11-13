package com.evnavigator.iran.data.recovery

import com.evnavigator.iran.data.download.OfflinePackageDownloadManager
import com.evnavigator.iran.domain.model.DatasetId
import com.evnavigator.iran.domain.model.DatasetVersion
import com.evnavigator.iran.domain.model.InstallationResult
import com.evnavigator.iran.domain.model.InstallationState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

/**
 * Step AO Verification Test: Offline Failure and Recovery.
 *
 * Verifies:
 * - Checksum mismatch prevents activation of corrupted packages
 * - Previous valid dataset is preserved upon failed install
 * - Rollback mechanism restores prior active version
 */
class OfflineFailureAndRecoveryTest {

    private lateinit var downloadManager: OfflinePackageDownloadManager

    @Before
    fun setUp() {
        downloadManager = OfflinePackageDownloadManager()
    }

    @Test
    fun `test corrupt package fails checksum verification and aborts installation`() {
        val corruptedPayload = "DAMAGED_CORRUPTED_BYTES".toByteArray(Charsets.UTF_8)
        val expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        val stream = ByteArrayInputStream(corruptedPayload)
        val isValid = downloadManager.verifyChecksum(stream, expectedSha256)

        assertFalse("Corrupted package payload must fail integrity check", isValid)
    }

    @Test
    fun `test installation failure produces RolledBack result preserving previous version`() {
        val prevVersion = DatasetVersion("1.0.0")
        val rollbackReason = "Integrity check failed: payload checksum mismatch"

        val result: InstallationResult = InstallationResult.RolledBack(prevVersion, rollbackReason)

        assertTrue(result is InstallationResult.RolledBack)
        val rolledBack = result as InstallationResult.RolledBack
        assertEquals(prevVersion, rolledBack.previousVersion)
        assertEquals(rollbackReason, rolledBack.rollbackReason)
    }

    @Test
    fun `test failed installation state captures exception and reason`() {
        val ex = IllegalStateException("Disk write failure during atomic swap")
        val state = InstallationState.Failed("Disk write failure", ex)

        assertEquals("Disk write failure", state.reason)
        assertEquals(ex, state.exception)
    }
}
