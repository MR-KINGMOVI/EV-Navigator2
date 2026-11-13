package com.evnavigator.iran.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class OfflinePackageTest {

    @Test
    fun `test OfflinePackage creation with valid data`() {
        // Create test data
        val manifest = createTestManifest()
        val payload = PackagePayload(
            data = byteArrayOf(1, 2, 3, 4, 5),
            size = 5,
            compression = "gzip"
        )
        val metadata = PackageMetadata(
            packageVersion = DatasetVersion("1.0.0"),
            formatIdentifier = "EVN-STD",
            creationTimestamp = Instant.now(),
            lastModified = Instant.now(),
            totalSize = 1024
        )
        
        // Create package
        val pkg = OfflinePackage(manifest, payload, metadata)
        
        // Verify properties are set correctly
        assertEquals(manifest, pkg.manifest)
        assertEquals(payload, pkg.payload)
        assertEquals(metadata, pkg.packageMetadata)
        assertNull(pkg.signature)
    }
    
    @Test
    fun `test OfflinePackage creation with signature`() {
        // Create test data
        val manifest = createTestManifest()
        val payload = PackagePayload(
            data = byteArrayOf(1, 2, 3, 4, 5),
            size = 5
        )
        val metadata = PackageMetadata(
            packageVersion = DatasetVersion("1.0.0"),
            formatIdentifier = "EVN-STD",
            creationTimestamp = Instant.now(),
            lastModified = Instant.now(),
            totalSize = 1024
        )
        val signature = PackageSignature(
            value = "test-signature-value",
            algorithm = "SHA-256",
            signatureTimestamp = Instant.now()
        )
        
        // Create package with signature
        val pkg = OfflinePackage(manifest, payload, metadata, signature)
        
        // Verify properties are set correctly
        assertEquals(manifest, pkg.manifest)
        assertEquals(payload, pkg.payload)
        assertEquals(metadata, pkg.packageMetadata)
        assertEquals(signature, pkg.signature)
    }
    
    @Test
    fun `test PackagePayload creation with valid data`() {
        val payload = PackagePayload(
            data = byteArrayOf(1, 2, 3, 4, 5),
            size = 5,
            compression = "gzip",
            encryption = "AES-256-GCM"
        )
        
        // Verify properties are set correctly
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), payload.data)
        assertEquals(5, payload.size)
        assertEquals("gzip", payload.compression)
        assertEquals("AES-256-GCM", payload.encryption)
    }
    
    @Test
    fun `test PackagePayload creation with empty data`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackagePayload(data = byteArrayOf())
        }
    }
    
    @Test
    fun `test PackageMetadata creation with valid data`() {
        val metadata = PackageMetadata(
            packageVersion = DatasetVersion("1.0.0"),
            formatIdentifier = "EVN-STD",
            creationTimestamp = Instant.now(),
            lastModified = Instant.now(),
            totalSize = 1024,
            packageCompression = "gzip",
            packageEncryption = "AES-256-GCM",
            packageChecksum = "test-checksum",
            packageChecksumAlgorithm = "SHA-256"
        )
        
        // Verify properties are set correctly
        assertEquals(DatasetVersion("1.0.0"), metadata.packageVersion)
        assertEquals("EVN-STD", metadata.formatIdentifier)
        assertNotNull(metadata.creationTimestamp)
        assertNotNull(metadata.lastModified)
        assertEquals(1024, metadata.totalSize)
        assertEquals("gzip", metadata.packageCompression)
        assertEquals("AES-256-GCM", metadata.packageEncryption)
        assertEquals("test-checksum", metadata.packageChecksum)
        assertEquals("SHA-256", metadata.packageChecksumAlgorithm)
    }
    
    @Test
    fun `test PackageMetadata creation with negative size`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageMetadata(
                packageVersion = DatasetVersion("1.0.0"),
                formatIdentifier = "EVN-STD",
                creationTimestamp = Instant.now(),
                lastModified = Instant.now(),
                totalSize = -1
            )
        }
    }
    
    @Test
    fun `test PackageMetadata creation with blank format identifier`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageMetadata(
                packageVersion = DatasetVersion("1.0.0"),
                formatIdentifier = "",
                creationTimestamp = Instant.now(),
                lastModified = Instant.now(),
                totalSize = 1024
            )
        }
    }
    
    @Test
    fun `test PackageSignature creation with valid data`() {
        val signature = PackageSignature(
            value = "test-signature",
            algorithm = "SHA-256",
            keyId = "test-key-id",
            certificateChain = listOf("cert1", "cert2"),
            signatureTimestamp = Instant.now()
        )
        
        // Verify properties are set correctly
        assertEquals("test-signature", signature.value)
        assertEquals("SHA-256", signature.algorithm)
        assertEquals("test-key-id", signature.keyId)
        assertEquals(listOf("cert1", "cert2"), signature.certificateChain)
        assertNotNull(signature.signatureTimestamp)
    }
    
    @Test
    fun `test PackageSignature creation with blank value`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageSignature(
                value = "",
                algorithm = "SHA-256",
                signatureTimestamp = Instant.now()
            )
        }
    }
    
    @Test
    fun `test PackageSignature creation with blank algorithm`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageSignature(
                value = "test-signature",
                algorithm = "",
                signatureTimestamp = Instant.now()
            )
        }
    }
    
    @Test
    fun `test OfflinePackageBuilder with all fields`() {
        val manifest = createTestManifest()
        val payload = PackagePayload(
            data = byteArrayOf(1, 2, 3, 4, 5)
        )
        val metadata = PackageMetadata(
            packageVersion = DatasetVersion("1.0.0"),
            formatIdentifier = "EVN-STD",
            creationTimestamp = Instant.now(),
            lastModified = Instant.now(),
            totalSize = 1024
        )
        val signature = PackageSignature(
            value = "test-signature",
            algorithm = "SHA-256",
            signatureTimestamp = Instant.now()
        )
        
        // Use builder
        val packageBuilder = OfflinePackageBuilder()
            .setManifest(manifest)
            .setPayload(payload)
            .setPackageMetadata(metadata)
            .setSignature(signature)
        
        val packageResult = packageBuilder.build()
        
        // Verify all fields are set correctly
        assertEquals(manifest, packageResult.manifest)
        assertEquals(payload, packageResult.payload)
        assertEquals(metadata, packageResult.packageMetadata)
        assertEquals(signature, packageResult.signature)
    }
    
    @Test
    fun `test OfflinePackageBuilder missing required fields`() {
        val payload = PackagePayload(
            data = byteArrayOf(1, 2, 3, 4, 5)
        )
        val metadata = PackageMetadata(
            packageVersion = DatasetVersion("1.0.0"),
            formatIdentifier = "EVN-STD",
            creationTimestamp = Instant.now(),
            lastModified = Instant.now(),
            totalSize = 1024
        )
        
        // Try to build without required manifest - should fail
        val builder = OfflinePackageBuilder()
            .setPayload(payload)
            .setPackageMetadata(metadata)
        
        assertThrows(IllegalStateException::class.java) {
            builder.build()
        }
    }
    
    @Test
    fun `test PackageFormatVersions constants`() {
        assertEquals("1.0", PackageFormatVersions.V1_0)
        assertEquals("1.1", PackageFormatVersions.V1_1)
        assertEquals("2.0", PackageFormatVersions.V2_0)
    }
    
    @Test
    fun `test PackageFormats constants`() {
        assertEquals("EVN-STD", PackageFormats.STANDARD_FORMAT)
        assertEquals("EVN-CMP", PackageFormats.COMPRESSED_FORMAT)
        assertEquals("EVN-ENC", PackageFormats.ENCRYPTED_FORMAT)
        assertEquals("EVN-SEC", PackageFormats.SECURE_FORMAT)
    }
    
    @Test
    fun `test PackageCompression constants`() {
        assertEquals("none", PackageCompression.NONE)
        assertEquals("gzip", PackageCompression.GZIP)
        assertEquals("zstd", PackageCompression.ZSTD)
        assertEquals("bzip2", PackageCompression.BZIP2)
    }
    
    @Test
    fun `test PackageEncryption constants`() {
        assertEquals("none", PackageEncryption.NONE)
        assertEquals("AES-256-GCM", PackageEncryption.AES_256_GCM)
        assertEquals("RSA-OAEP", PackageEncryption.RSA_OAEP)
    }
    
    @Test
    fun `test PackageChecksumAlgorithms constants`() {
        assertEquals("SHA-256", PackageChecksumAlgorithms.SHA_256)
        assertEquals("SHA-384", PackageChecksumAlgorithms.SHA_384)
        assertEquals("SHA-512", PackageChecksumAlgorithms.SHA_512)
        assertEquals("BLAKE2B-256", PackageChecksumAlgorithms.BLAKE2B_256)
        assertEquals("BLAKE2B-512", PackageChecksumAlgorithms.BLAKE2B_512)
    }
    
    private fun createTestManifest(): DatasetManifest {
        return DatasetManifest(
            id = DatasetId("test-dataset"),
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now(),
            dependencies = emptyList()
        )
    }
}