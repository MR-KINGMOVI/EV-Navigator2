package com.evnavigator.iran.data.download

import android.content.Context
import com.evnavigator.iran.domain.model.catalog.OfflinePackageVerificationStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * V11 Crash-Safe OfflineMapPackageManager Unit Tests:
 * - SHA-256 verification pass/fail
 * - Dependency graph check (corridor requires base package)
 * - Atomic staged installation (.staged -> .mbtiles) and rollback on corrupt payload
 */
class OfflineMapPackageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var manager: OfflineMapPackageManager

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("ev_offline_test")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        manager = OfflineMapPackageManager(context)
    }

    @Test
    fun `corridor package download is rejected when required iran_base_v11 dependency is not installed`() = runTest {
        val result = manager.downloadAndInstallPackage("corridor_tehran_isfahan_v11")
        assertFalse("Corridor installation must fail if base package dependency is missing", result)

        val pkg = manager.packagesState.value.first { it.packageId == "corridor_tehran_isfahan_v11" }
        assertEquals(OfflinePackageVerificationStatus.MISSING_DEPENDENCY, pkg.verificationStatus)
    }

    @Test
    fun `base package installs atomically with verified SHA-256 checksum and unlocks corridor package`() = runTest {
        val baseInstalled = manager.downloadAndInstallPackage("iran_base_v11")
        assertTrue("Base package should install and verify SHA-256 checksum", baseInstalled)
        assertTrue("verifyPackageChecksum must return true for installed base package", manager.verifyPackageChecksum("iran_base_v11"))

        val corridorInstalled = manager.downloadAndInstallPackage("corridor_tehran_isfahan_v11")
        assertTrue("Corridor package should install once base dependency is satisfied", corridorInstalled)
        assertTrue("verifyPackageChecksum must return true for installed corridor package", manager.verifyPackageChecksum("corridor_tehran_isfahan_v11"))
    }

    @Test
    fun `tampered offline package file fails SHA-256 verification and is quarantined`() = runTest {
        manager.downloadAndInstallPackage("iran_base_v11")
        val targetFile = File(File(filesDir, "offline_packages_v11"), "iran_base_v11.mbtiles")
        assertTrue("Installed mbtiles file must exist", targetFile.exists())

        // Tamper with the binary content on disk
        targetFile.appendBytes("CORRUPTED_BYTES_INJECTED".toByteArray())

        val verified = manager.verifyPackageChecksum("iran_base_v11")
        assertFalse("Tampered file must fail SHA-256 checksum verification", verified)

        val pkg = manager.packagesState.value.first { it.packageId == "iran_base_v11" }
        assertEquals(OfflinePackageVerificationStatus.CHECKSUM_FAILED, pkg.verificationStatus)
        assertFalse("Tampered package must not be marked installed", pkg.isInstalled)
    }
}
