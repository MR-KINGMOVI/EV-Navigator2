package com.evnavigator.iran.domain.model

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomicInstallerTest {

    @Test
    fun `test atomic installer creation`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        val installer = AtomicInstaller(stagingManager, activeDatasetManager)
        
        assertNotNull(installer)
    }

    @Test
    fun `test install package with valid staged package`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        val installer = AtomicInstaller(stagingManager, activeDatasetManager)
        
        // Create a test manifest
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        // Create test package data
        val packageData = "Test package content".toByteArray()
        
        // Stage the package
        val stagingResult = stagingManager.stagePackage(manifest, packageData)
        
        assertTrue(stagingResult is StagingResult.Success)
        val stagedPackage = (stagingResult as StagingResult.Success).stagedPackage
        
        // Install the package
        val installResult = installer.installPackage(stagedPackage)
        
        assertTrue(installResult is InstallationResult.Success)
        val successResult = (installResult as InstallationResult.Success)
        
        assertEquals(DatasetId("test-dataset"), successResult.datasetId)
        assertEquals(DatasetVersion("1.0.0"), successResult.version)
        assertNotNull(successResult.installedAt)
        
        // Verify the package is no longer in staging
        val remainingPackages = stagingManager.listStagedPackages()
        assertEquals(0, remainingPackages.size)
    }

    @Test
    fun `test install package with missing file`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        val installer = AtomicInstaller(stagingManager, activeDatasetManager)
        
        // Create a test manifest
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        // Create a staged package with a non-existent file path
        val stagedPackage = StagedPackage(
            id = "test-package",
            manifest = manifest,
            packagePath = Paths.get("/nonexistent/path/package.dat"),
            sizeBytes = 100,
            isPartial = false
        )
        
        // Try to install the package
        val installResult = installer.installPackage(stagedPackage)
        
        assertTrue(installResult is InstallationResult.Error)
        val errorResult = (installResult as InstallationResult.Error)
        assertTrue(errorResult.message.contains("Package file does not exist"))
    }

    @Test
    fun `test install package with integrity validation`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        val installer = AtomicInstaller(stagingManager, activeDatasetManager)
        
        // Create a test manifest with checksum
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now(),
            checksum = "abc123",
            integrityAlgorithm = "SHA-256"
        )
        
        // Create test package data
        val packageData = "Test package content".toByteArray()
        
        // Stage the package
        val stagingResult = stagingManager.stagePackage(manifest, packageData)
        
        assertTrue(stagingResult is StagingResult.Success)
        val stagedPackage = (stagingResult as StagingResult.Success).stagedPackage
        
        // Install the package
        val installResult = installer.installPackage(stagedPackage)
        
        assertTrue(installResult is InstallationResult.Success)
    }

    @Test
    fun `test active dataset manager`() {
        val activeDatasetManager = ActiveDatasetManager()
        
        // Initially no active dataset
        assertFalse(activeDatasetManager.isDatasetActive(DatasetId("test-dataset")))
        assertNull(activeDatasetManager.getActiveDataset())
        assertNull(activeDatasetManager.getActiveVersion())
        
        // Set an active dataset
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        activeDatasetManager.setActiveDataset(manifest)
        
        // Verify the dataset is now active
        assertTrue(activeDatasetManager.isDatasetActive(DatasetId("test-dataset")))
        assertNotNull(activeDatasetManager.getActiveDataset())
        assertEquals(DatasetVersion("1.0.0"), activeDatasetManager.getActiveVersion())
        
        // Check with wrong ID
        assertFalse(activeDatasetManager.isDatasetActive(DatasetId("wrong-dataset")))
    }

    @Test
    fun `test rollback to previous dataset`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        
        // Set an initial active dataset
        val initialManifest = DatasetManifest(
            id = DatasetId("initial-dataset"),
            version = DatasetVersion("0.9.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        activeDatasetManager.setActiveDataset(initialManifest)
        
        // Create a new manifest
        val newManifest = DatasetManifest(
            id = DatasetId("new-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        // Create and stage the new package
        val packageData = "Test package content".toByteArray()
        val stagingResult = stagingManager.stagePackage(newManifest, packageData)
        
        assertTrue(stagingResult is StagingResult.Success)
        val stagedPackage = (stagingResult as StagingResult.Success).stagedPackage
        
        // Install the package (this would normally fail in a real scenario)
        val installResult = installer.installPackage(stagedPackage)
        
        // Verify rollback occurred
        assertTrue(installResult is InstallationResult.RolledBack)
        val rollbackResult = (installResult as InstallationResult.RolledBack)
        
        assertEquals(DatasetVersion("0.9.0"), rollbackResult.previousVersion)
        assertTrue(rollbackResult.rollbackReason.contains("failed"))
        
        // Verify the previous dataset is still active
        assertTrue(activeDatasetManager.isDatasetActive(DatasetId("initial-dataset")))
    }

    @Test
    fun `test install package with dependencies`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        val installer = AtomicInstaller(stagingManager, activeDatasetManager)
        
        // Create a manifest with dependencies
        val manifest = DatasetManifest(
            id = DatasetId("dependent-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now(),
            dependencies = listOf(
                DatasetDependency(
                    datasetId = DatasetId("dependency-dataset"),
                    requiredVersion = DatasetVersion("1.0.0"),
                    optional = false
                )
            )
        )
        
        // Create test package data
        val packageData = "Test package content".toByteArray()
        
        // Stage the package
        val stagingResult = stagingManager.stagePackage(manifest, packageData)
        
        assertTrue(stagingResult is StagingResult.Success)
        val stagedPackage = (stagingResult as StagingResult.Success).stagedPackage
        
        // Install the package
        val installResult = installer.installPackage(stagedPackage)
        
        // Should succeed since dependencies are satisfied in the staging area
        assertTrue(installResult is InstallationResult.Success)
    }

    @Test
    fun `test install package with invalid checksum`() {
        val tempDir = Files.createTempDirectory("atomic_test")
        val stagingManager = StagingManager(
            StagingArea(
                id = "test-staging",
                path = tempDir,
                maxSizeBytes = 1024 * 1024 * 1024
            )
        )
        
        val activeDatasetManager = ActiveDatasetManager()
        val installer = AtomicInstaller(stagingManager, activeDatasetManager)
        
        // Create a manifest with wrong checksum
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now(),
            checksum = "wrongchecksum1234567890abcdef01234567890abcdef0",
            integrityAlgorithm = "SHA-256"
        )
        
        // Create test package data
        val packageData = "Test package content".toByteArray()
        
        // Stage the package
        val stagingResult = stagingManager.stagePackage(manifest, packageData)
        
        assertTrue(stagingResult is StagingResult.Success)
        val stagedPackage = (stagingResult as StagingResult.Success).stagedPackage
        
        // Install the package - should fail due to invalid checksum
        val installResult = installer.installPackage(stagedPackage)
        
        assertTrue(installResult is InstallationResult.Error)
        val errorResult = (installResult as InstallationResult.Error)
        assertTrue(errorResult.message.contains("Package integrity validation failed"))
    }

    companion object {
        private lateinit var installer: AtomicInstaller
    }
}