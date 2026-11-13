package com.evnavigator.iran.domain.model

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StagingAreaTest {

    @Test
    fun `test staging area creation`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 1024 * 1024 * 1024 // 1GB
        )
        
        assertEquals("test-staging", stagingArea.id)
        assertEquals(tempDir, stagingArea.path)
        assertEquals(1024 * 1024 * 1024, stagingArea.maxSizeBytes)
        assertNotNull(stagingArea.createdAt)
        assertNotNull(stagingArea.lastModified)
    }

    @Test
    fun `test staging manager creation`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 1024 * 1024 * 1024
        )
        
        val manager = StagingManager(stagingArea)
        assertNotNull(manager)
        assertEquals(stagingArea, manager.getStagingArea())
    }

    @Test
    fun `test staging package`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 1024 * 1024 * 1024
        )
        
        val manager = StagingManager(stagingArea)
        
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
        val result = manager.stagePackage(manifest, packageData)
        
        assertTrue(result is StagingResult.Success)
        val stagedPackage = (result as StagingResult.Success).stagedPackage
        
        assertNotNull(stagedPackage)
        assertEquals(manifest.id, stagedPackage.manifest.id)
        assertEquals(packageData.size.toLong(), stagedPackage.sizeBytes)
        assertEquals(StagingStatus.STAGED, stagedPackage.status)
        assertFalse(stagedPackage.isPartial)
    }

    @Test
    fun `test get staged package`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 1024 * 1024 * 1024
        )
        
        val manager = StagingManager(stagingArea)
        
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
        val result = manager.stagePackage(manifest, packageData)
        
        assertTrue(result is StagingResult.Success)
        val stagedPackage = (result as StagingResult.Success).stagedPackage
        
        // Retrieve the staged package
        val retrievedPackage = manager.getStagedPackage(stagedPackage.id)
        assertNotNull(retrievedPackage)
        assertEquals(stagedPackage.id, retrievedPackage.id)
    }

    @Test
    fun `test remove staged package`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 1024 * 1024 * 1024
        )
        
        val manager = StagingManager(stagingArea)
        
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
        val result = manager.stagePackage(manifest, packageData)
        
        assertTrue(result is StagingResult.Success)
        val stagedPackage = (result as StagingResult.Success).stagedPackage
        
        // Remove the staged package
        val removed = manager.removeStagedPackage(stagedPackage.id)
        assertTrue(removed)
        
        // Verify it's no longer in the registry
        val retrievedPackage = manager.getStagedPackage(stagedPackage.id)
        assertNull(retrievedPackage)
    }

    @Test
    fun `test list staged packages`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 1024 * 1024 * 1024
        )
        
        val manager = StagingManager(stagingArea)
        
        // Create test manifests and data
        val manifest1 = DatasetManifest(
            id = DatasetId("test-dataset-1"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        val manifest2 = DatasetManifest(
            id = DatasetId("test-dataset-2"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        val packageData = "Test package content".toByteArray()
        
        // Stage two packages
        val result1 = manager.stagePackage(manifest1, packageData)
        val result2 = manager.stagePackage(manifest2, packageData)
        
        assertTrue(result1 is StagingResult.Success)
        assertTrue(result2 is StagingResult.Success)
        
        // List all staged packages
        val packages = manager.listStagedPackages()
        assertEquals(2, packages.size)
    }

    @Test
    fun `test staging area with insufficient space`() {
        val tempDir = Files.createTempDirectory("staging_test")
        val stagingArea = StagingArea(
            id = "test-staging",
            path = tempDir,
            maxSizeBytes = 10 // Very small size for testing
        )
        
        val manager = StagingManager(stagingArea)
        
        // Create a manifest and large package data
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
        )
        
        // Create package data larger than available space
        val largePackageData = ByteArray(100) { 'A'.code.toByte() }
        
        // Try to stage the package - should fail due to insufficient space
        val result = manager.stagePackage(manifest, largePackageData)
        
        assertTrue(result is StagingResult.Error)
    }

    companion object {
        fun assertNull(value: Any?) {
            if (value != null) {
                throw AssertionError("Expected null but was $value")
            }
        }
    }
}