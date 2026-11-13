package com.evnavigator.iran.domain.model

import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatasetTest {

    @Test
    fun testDatasetIdValidation() {
        // Valid dataset ID
        val validId = DatasetId("test-dataset")
        assertEquals("test-dataset", validId.value)

        // Invalid dataset ID - blank
        assertFailsWith<IllegalArgumentException> {
            DatasetId("")
        }

        // Invalid dataset ID - null
        assertFailsWith<IllegalArgumentException> {
            DatasetId("   ")
        }
    }

    @Test
    fun testDatasetVersionValidation() {
        // Valid dataset version
        val validVersion = DatasetVersion("1.0.0")
        assertEquals("1.0.0", validVersion.value)

        // Invalid dataset version - blank
        assertFailsWith<IllegalArgumentException> {
            DatasetVersion("")
        }

        // Invalid dataset version - null
        assertFailsWith<IllegalArgumentException> {
            DatasetVersion("   ")
        }
    }

    @Test
    fun testBoundingBoxValidation() {
        // Valid bounding box
        val validBox = BoundingBox(40.0, 30.0, 120.0, 110.0)
        assertEquals(40.0, validBox.north)
        assertEquals(30.0, validBox.south)
        assertEquals(120.0, validBox.east)
        assertEquals(110.0, validBox.west)

        // Invalid bounding box - north < south
        assertFailsWith<IllegalArgumentException> {
            BoundingBox(30.0, 40.0, 120.0, 110.0)
        }

        // Invalid bounding box - longitude out of range
        assertFailsWith<IllegalArgumentException> {
            BoundingBox(40.0, 30.0, 190.0, 110.0)
        }

        // Invalid bounding box - latitude out of range
        assertFailsWith<IllegalArgumentException> {
            BoundingBox(95.0, 30.0, 120.0, 110.0)
        }
    }

    @Test
    fun testDatasetMetadataCreation() {
        val id = DatasetId("test-map-dataset")
        val version = DatasetVersion("1.0.0")
        val schemaVersion = DatasetVersion("1.0")
        val packageVersion = DatasetVersion("1.0.0")
        val source = DatasetSource("Test Provider", "https://example.com", "1.0")
        val creationTimestamp = Instant.now()
        
        val metadata = DatasetMetadata(
            id = id,
            type = DatasetType.MAP,
            version = version,
            schemaVersion = schemaVersion,
            packageVersion = packageVersion,
            source = source,
            creationTimestamp = creationTimestamp
        )
        
        assertEquals(id, metadata.id)
        assertEquals(DatasetType.MAP, metadata.type)
        assertEquals(version, metadata.version)
        assertEquals(schemaVersion, metadata.schemaVersion)
        assertEquals(packageVersion, metadata.packageVersion)
        assertEquals(source, metadata.source)
        assertEquals(creationTimestamp, metadata.creationTimestamp)
    }

    @Test
    fun testDatasetDependencyCreation() {
        val datasetId = DatasetId("dependency-dataset")
        val version = DatasetVersion("1.0.0")
        
        val dependency = DatasetDependency(
            datasetId = datasetId,
            requiredVersion = version
        )
        
        assertEquals(datasetId, dependency.datasetId)
        assertEquals(version, dependency.requiredVersion)
        assertEquals(false, dependency.optional)
        assertEquals(emptyList<DatasetVersion>(), dependency.compatibleVersions)
    }

    @Test
    fun testDatasetFreshness() {
        // Test all freshness values are defined
        val freshnessValues = DatasetFreshness.values()
        assertEquals(7, freshnessValues.size)
        
        val expectedValues = setOf(
            DatasetFreshness.FRESH,
            DatasetFreshness.STALE_BUT_USABLE,
            DatasetFreshness.EXPIRED,
            DatasetFreshness.INVALID,
            DatasetFreshness.OFFLINE,
            DatasetFreshness.PARTIAL,
            DatasetFreshness.UNKNOWN
        )
        
        val actualValues = freshnessValues.toSet()
        assertEquals(expectedValues, actualValues)
    }

    @Test
    fun testDatasetStatus() {
        // Test all status values are defined
        val statusValues = DatasetStatus.values()
        assertEquals(8, statusValues.size)
        
        val expectedValues = setOf(
            DatasetStatus.ACTIVE,
            DatasetStatus.INSTALLED,
            DatasetStatus.DOWNLOADING,
            DatasetStatus.STAGED,
            DatasetStatus.INVALID,
            DatasetStatus.PARTIAL,
            DatasetStatus.AVAILABLE,
            DatasetStatus.REMOVED
        )
        
        val actualValues = statusValues.toSet()
        assertEquals(expectedValues, actualValues)
    }

    @Test
    fun testDatasetAvailability() {
        // Test all availability values are defined
        val availabilityValues = DatasetAvailability.values()
        assertEquals(7, availabilityValues.size)
        
        val expectedValues = setOf(
            DatasetAvailability.LIVE,
            DatasetAvailability.CACHED,
            DatasetAvailability.STALE,
            DatasetAvailability.OFFLINE,
            DatasetAvailability.PARTIAL,
            DatasetAvailability.UNKNOWN,
            DatasetAvailability.INVALID
        )
        
        val actualValues = availabilityValues.toSet()
        assertEquals(expectedValues, actualValues)
    }

    @Test
    fun testDatasetType() {
        // Test all dataset type values are defined
        val typeValues = DatasetType.values()
        assertEquals(15, typeValues.size)
        
        val expectedValues = setOf(
            DatasetType.MAP,
            DatasetType.ROUTING,
            DatasetType.ELEVATION,
            DatasetType.CHARGING,
            DatasetType.VEHICLE,
            DatasetType.ENERGY,
            DatasetType.WEATHER,
            DatasetType.TRAFFIC,
            DatasetType.FAVORITES,
            DatasetType.RECENT_DESTINATIONS,
            DatasetType.TRIPS,
            DatasetType.NAVIGATION_RECOVERY,
            DatasetType.APPLICATION_METADATA,
            DatasetType.DATASET_METADATA,
            DatasetType.USER_CREATED
        )
        
        val actualValues = typeValues.toSet()
        assertEquals(expectedValues, actualValues)
    }
}