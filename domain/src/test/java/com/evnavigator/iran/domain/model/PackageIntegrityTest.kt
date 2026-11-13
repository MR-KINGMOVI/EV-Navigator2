package com.evnavigator.iran.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageIntegrityTest {

    @Test
    fun `test package integrity validation with valid checksum`() {
        val testData = "Hello, World!".toByteArray()
        val algorithm = "SHA-256"
        
        // Calculate expected checksum
        val expectedChecksum = PackageIntegrityValidator.calculateChecksum(testData, algorithm)
        
        // Validate the checksum
        val isValid = PackageIntegrityValidator.validatePackageIntegrity(
            testData, 
            expectedChecksum, 
            algorithm
        )
        
        assertTrue(isValid, "Valid checksum should pass validation")
    }

    @Test
    fun `test package integrity validation with invalid checksum`() {
        val testData = "Hello, World!".toByteArray()
        val algorithm = "SHA-256"
        
        // Use wrong checksum
        val wrongChecksum = "wrongchecksum1234567890abcdef01234567890abcdef0"
        
        // Validate with wrong checksum
        val isValid = PackageIntegrityValidator.validatePackageIntegrity(
            testData, 
            wrongChecksum, 
            algorithm
        )
        
        assertFalse(isValid, "Invalid checksum should fail validation")
    }

    @Test
    fun `test package integrity with manifest`() {
        val testData = "Hello, World!".toByteArray()
        val algorithm = "SHA-256"
        val expectedChecksum = PackageIntegrityValidator.calculateChecksum(testData, algorithm)
        
        // Create a manifest with checksum information
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now(),
            checksum = expectedChecksum,
            integrityAlgorithm = algorithm
        )
        
        // Validate package against manifest
        val isValid = PackageIntegrityValidator.validatePackageAgainstManifest(testData, manifest)
        
        assertTrue(isValid, "Package should be valid against manifest with correct checksum")
    }

    @Test
    fun `test package integrity with manifest missing checksum`() {
        val testData = "Hello, World!".toByteArray()
        
        // Create a manifest without checksum information
        val manifest = DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "test-provider"),
            creationTimestamp = Instant.now()
            // No checksum or integrityAlgorithm
        )
        
        // Validate package against manifest (should fail)
        val isValid = PackageIntegrityValidator.validatePackageAgainstManifest(testData, manifest)
        
        assertFalse(isValid, "Package should be invalid when manifest has no checksum")
    }

    @Test
    fun `test component integrity validation`() {
        val manifestData = """{"id":"test","version":"1.0.0"}""".toByteArray()
        val payloadData = "payload data".toByteArray()
        val metadataData = """{"format":"1.0"}""".toByteArray()
        
        val algorithm = "SHA-256"
        
        // Calculate expected checksums
        val manifestChecksum = PackageIntegrityValidator.calculateChecksum(manifestData, algorithm)
        val payloadChecksum = PackageIntegrityValidator.calculateChecksum(payloadData, algorithm)
        val metadataChecksum = PackageIntegrityValidator.calculateChecksum(metadataData, algorithm)
        
        // Validate components
        val isValid = PackageIntegrityValidator.validateComponentIntegrity(
            manifestData,
            payloadData,
            metadataData,
            manifestChecksum,
            payloadChecksum,
            metadataChecksum,
            algorithm
        )
        
        assertTrue(isValid, "All component checksums should match")
    }

    @Test
    fun `test component integrity validation with mismatch`() {
        val manifestData = """{"id":"test","version":"1.0.0"}""".toByteArray()
        val payloadData = "payload data".toByteArray()
        val metadataData = """{"format":"1.0"}""".toByteArray()
        
        val algorithm = "SHA-256"
        
        // Use wrong checksum for one component
        val manifestChecksum = PackageIntegrityValidator.calculateChecksum(manifestData, algorithm)
        val payloadChecksum = PackageIntegrityValidator.calculateChecksum(payloadData, algorithm)
        val wrongMetadataChecksum = "wrongchecksum1234567890abcdef01234567890abcdef0"
        
        // Validate components with one mismatch
        val isValid = PackageIntegrityValidator.validateComponentIntegrity(
            manifestData,
            payloadData,
            metadataData,
            manifestChecksum,
            payloadChecksum,
            wrongMetadataChecksum,
            algorithm
        )
        
        assertFalse(isValid, "Validation should fail when component checksums don't match")
    }
}