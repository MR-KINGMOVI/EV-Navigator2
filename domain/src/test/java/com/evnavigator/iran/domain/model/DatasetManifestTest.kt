package com.evnavigator.iran.domain.model

import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatasetManifestTest {

    @Test
    fun testDatasetManifestCreation() {
        val id = DatasetId("test-map-dataset")
        val version = DatasetVersion("1.0.0")
        val schemaVersion = DatasetVersion("1.0")
        val packageVersion = DatasetVersion("1.0.0")
        val source = DatasetSource("Test Provider", "https://example.com", "1.0")
        val creationTimestamp = Instant.now()
        
        val manifest = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        
        assertEquals(id, manifest.id)
        assertEquals(version, manifest.version)
        assertEquals(schemaVersion, manifest.schemaVersion)
        assertEquals(packageVersion, manifest.packageVersion)
        assertEquals(source, manifest.source)
        assertEquals(creationTimestamp, manifest.creationTimestamp)
        assertTrue(manifest.isValid())
    }

    @Test
    fun testDatasetManifestValidation() {
        val id = DatasetId("test-map-dataset")
        val version = DatasetVersion("1.0.0")
        val schemaVersion = DatasetVersion("1.0")
        val packageVersion = DatasetVersion("1.0.0")
        val source = DatasetSource("Test Provider", "https://example.com", "1.0")
        val creationTimestamp = Instant.now()
        
        // Valid manifest
        val validManifest = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        assertTrue(validManifest.isValid())
        
        // Invalid manifest - missing ID
        val invalidManifest = DatasetManifest(
            id = DatasetId(""),
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        assertFalse(invalidManifest.isValid())
    }

    @Test
    fun testDatasetManifestFreshness() {
        val id = DatasetId("test-map-dataset")
        val version = DatasetVersion("1.0.0")
        val schemaVersion = DatasetVersion("1.0")
        val packageVersion = DatasetVersion("1.0.0")
        val source = DatasetSource("Test Provider", "https://example.com", "1.0")
        val creationTimestamp = Instant.now()
        
        // Fresh manifest (not expired)
        val freshManifest = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        assertEquals(DatasetFreshness.FRESH, freshManifest.getFreshness())
        
        // Manifest that is expired (this would need to be set up with an expiration date)
        val futureTime = Instant.now().plusSeconds(3600)  // 1 hour in the future
        val expiredManifest = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp,
            expirationTimestamp = futureTime
        )
        assertEquals(DatasetFreshness.FRESH, expiredManifest.getFreshness()) // Should still be fresh since we set future expiration
        
        // Test with past expiration date (this is hard to test without mocking time)
    }

    @Test
    fun testDatasetManifestCompatibility() {
        val id = DatasetId("test-map-dataset")
        val version = DatasetVersion("1.0.0")
        val schemaVersion = DatasetVersion("1.0")
        val packageVersion = DatasetVersion("1.0.0")
        val source = DatasetSource("Test Provider", "https://example.com", "1.0")
        val creationTimestamp = Instant.now()
        
        // Manifest with no version requirements
        val manifestNoRequirements = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        assertTrue(manifestNoRequirements.isCompatibleWithAppVersion("1.0.0"))
        
        // Manifest with minimum version requirement
        val manifestMinRequirement = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp,
            minimumAppVersion = "1.0.0"
        )
        assertTrue(manifestMinRequirement.isCompatibleWithAppVersion("2.0.0"))
        assertFalse(manifestMinRequirement.isCompatibleWithAppVersion("0.5.0"))
        
        // Manifest with maximum version requirement
        val manifestMaxRequirement = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp,
            maximumAppVersion = "2.0.0"
        )
        assertTrue(manifestMaxRequirement.isCompatibleWithAppVersion("1.5.0"))
        assertFalse(manifestMaxRequirement.isCompatibleWithAppVersion("3.0.0"))
        
        // Manifest with both version requirements
        val manifestBothRequirements = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp,
            minimumAppVersion = "1.0.0",
            maximumAppVersion = "2.0.0"
        )
        assertTrue(manifestBothRequirements.isCompatibleWithAppVersion("1.5.0"))
        assertFalse(manifestBothRequirements.isCompatibleWithAppVersion("0.5.0"))
        assertFalse(manifestBothRequirements.isCompatibleWithAppVersion("3.0.0"))
    }

    @Test
    fun testParseVersionString() {
        val id = DatasetId("test-map-dataset")
        val version = DatasetVersion("1.0.0")
        val schemaVersion = DatasetVersion("1.0")
        val packageVersion = DatasetVersion("1.0.0")
        val source = DatasetSource("Test Provider", "https://example.com", "1.0")
        val creationTimestamp = Instant.now()
        
        val manifest = DatasetManifest(
            id = id,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        
        // Test parsing various version strings
        assertEquals(1000000L, manifest.parseVersionString("1.0.0"))
        assertEquals(2001000L, manifest.parseVersionString("2.1.0"))
        assertEquals(1000005L, manifest.parseVersionString("1.0.5"))
        assertEquals(3000000L, manifest.parseVersionString("3.0.0"))
    }
}