package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertFailsWith as assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersioningModelTest {

    @Test
    fun `test version type enum`() {
        assertEquals(VersionType.DATASET, VersionType.DATASET)
        assertEquals(VersionType.RECORD, VersionType.RECORD)
        assertEquals(VersionType.SOURCE, VersionType.SOURCE)
        assertEquals(VersionType.SCHEMA, VersionType.SCHEMA)
        assertEquals(VersionType.APPLICATION, VersionType.APPLICATION)
        assertEquals(VersionType.UNKNOWN, VersionType.UNKNOWN)
    }

    @Test
    fun `test charging data version creation`() {
        val version = ChargingDataVersion(
            versionType = VersionType.DATASET,
            version = "v1.2.3",
            timestamp = 1634567890000L,
            changeDescription = "Initial release",
            sourceReference = "dataset_123.json",
            schemaVersion = "schema_v1",
            applicationVersion = "app_v2.0",
            isMajorVersion = true,
            parentVersion = "v1.1.0",
            metadata = mapOf("key" to "value")
        )

        assertEquals(VersionType.DATASET, version.versionType)
        assertEquals("v1.2.3", version.version)
        assertEquals(1634567890000L, version.timestamp)
        assertEquals("Initial release", version.changeDescription)
        assertEquals("dataset_123.json", version.sourceReference)
        assertEquals("schema_v1", version.schemaVersion)
        assertEquals("app_v2.0", version.applicationVersion)
        assertTrue(version.isMajorVersion)
        assertEquals("v1.1.0", version.parentVersion)
        assertEquals(mapOf("key" to "value"), version.metadata)
    }

    @Test
    fun `test charging data version with minimal fields`() {
        val version = ChargingDataVersion.createMinimal("v1.0")

        assertEquals(VersionType.UNKNOWN, version.versionType)
        assertEquals("v1.0", version.version)
        assertNotNull(version.timestamp)
    }

    @Test
    fun `test dataset version creation`() {
        val version = ChargingDataVersion.createDatasetVersion(
            version = "v1.2.3",
            changeDescription = "Initial release",
            sourceReference = "dataset_123.json",
            schemaVersion = "schema_v1",
            applicationVersion = "app_v2.0",
            isMajorVersion = true
        )

        assertEquals(VersionType.DATASET, version.versionType)
        assertEquals("v1.2.3", version.version)
        assertNotNull(version.timestamp)
        assertEquals("Initial release", version.changeDescription)
        assertEquals("dataset_123.json", version.sourceReference)
        assertEquals("schema_v1", version.schemaVersion)
        assertEquals("app_v2.0", version.applicationVersion)
        assertTrue(version.isMajorVersion)
    }

    @Test
    fun `test record version creation`() {
        val version = ChargingDataVersion.createRecordVersion(
            version = "r2",
            changeDescription = "Updated connector types",
            parentVersion = "r1",
            metadata = mapOf("updated_fields" to listOf("connector_type"))
        )

        assertEquals(VersionType.RECORD, version.versionType)
        assertEquals("r2", version.version)
        assertNotNull(version.timestamp)
        assertEquals("Updated connector types", version.changeDescription)
        assertEquals("r1", version.parentVersion)
        assertEquals(mapOf("updated_fields" to listOf("connector_type")), version.metadata)
    }

    @Test
    fun `test schema version creation`() {
        val version = ChargingDataVersion.createSchemaVersion(
            version = "schema_v2",
            changeDescription = "Added new fields",
            applicationVersion = "app_v2.1"
        )

        assertEquals(VersionType.SCHEMA, version.versionType)
        assertEquals("schema_v2", version.version)
        assertNotNull(version.timestamp)
        assertEquals("Added new fields", version.changeDescription)
        assertEquals("app_v2.1", version.applicationVersion)
    }

    @Test
    fun `test significant version detection`() {
        // Major version should be significant
        val majorVersion = ChargingDataVersion.createDatasetVersion(
            version = "v2.0.0",
            isMajorVersion = true
        )
        assertTrue(majorVersion.isSignificant)

        // Minor version should be significant
        val minorVersion = ChargingDataVersion.createDatasetVersion(
            version = "v1.2.3"
        )
        assertTrue(minorVersion.isSignificant)

        // Alpha version should not be significant
        val alphaVersion = ChargingDataVersion.createDatasetVersion(
            version = "v1.0.0-alpha"
        )
        assertFalse(alphaVersion.isSignificant)
    }

    @Test
    fun `test invalid timestamp`() {
        assertThrows<IllegalArgumentException> {
            ChargingDataVersion(
                versionType = VersionType.DATASET,
                version = "v1.0",
                timestamp = -1000L
            )
        }
    }

    @Test
    fun `test invalid blank version`() {
        assertThrows<IllegalArgumentException> {
            ChargingDataVersion(
                versionType = VersionType.DATASET,
                version = "   ",
                timestamp = 1634567890000L
            )
        }
    }

    @Test
    fun `test invalid blank schema version`() {
        assertThrows<IllegalArgumentException> {
            ChargingDataVersion(
                versionType = VersionType.DATASET,
                version = "v1.0",
                timestamp = 1634567890000L,
                schemaVersion = "   "
            )
        }
    }

    @Test
    fun `test invalid blank application version`() {
        assertThrows<IllegalArgumentException> {
            ChargingDataVersion(
                versionType = VersionType.DATASET,
                version = "v1.0",
                timestamp = 1634567890000L,
                applicationVersion = "   "
            )
        }
    }

    @Test
    fun `test dataset versioning state creation`() {
        val versioningState = DatasetVersioningState(
            currentDatasetVersion = ChargingDataVersion.createDatasetVersion("v1.2.3"),
            currentSchemaVersion = ChargingDataVersion.createSchemaVersion("schema_v1"),
            totalRecords = 1000,
            verifiedRecords = 800,
            userReportedRecords = 200,
            lastUpdated = 1634567890000L,
            dataSource = "OpenChargeMap",
            datasetConfidence = 0.9,
            isDatasetValidated = true
        )

        assertEquals(1000, versioningState.totalRecords)
        assertEquals(800, versioningState.verifiedRecords)
        assertEquals(200, versioningState.userReportedRecords)
        assertEquals(1634567890000L, versioningState.lastUpdated)
        assertEquals("OpenChargeMap", versioningState.dataSource)
        assertEquals(0.9, versioningState.datasetConfidence)
        assertTrue(versioningState.isDatasetValidated)
        assertEquals(80.0, versioningState.verifiedPercentage)
        assertEquals(20.0, versioningState.userReportedPercentage)
    }

    @Test
    fun `test dataset versioning state with minimal fields`() {
        val versioningState = DatasetVersioningState.createMinimal(
            totalRecords = 500,
            lastUpdated = 1634567890000L
        )

        assertEquals(500, versioningState.totalRecords)
        assertEquals(1634567890000L, versioningState.lastUpdated)
        assertEquals(0.0, versioningState.verifiedPercentage)
        assertEquals(0.0, versioningState.userReportedPercentage)
    }

    @Test
    fun `test dataset versioning state with full information`() {
        val versioningState = DatasetVersioningState.createFull(
            currentDatasetVersion = ChargingDataVersion.createDatasetVersion("v1.2.3"),
            currentSchemaVersion = ChargingDataVersion.createSchemaVersion("schema_v1"),
            totalRecords = 1500,
            verifiedRecords = 1200,
            userReportedRecords = 300,
            lastUpdated = 1634567890000L,
            dataSource = "ChargePoint",
            datasetConfidence = 0.85,
            isDatasetValidated = true
        )

        assertEquals(1500, versioningState.totalRecords)
        assertEquals(1200, versioningState.verifiedRecords)
        assertEquals(300, versioningState.userReportedRecords)
        assertEquals("ChargePoint", versioningState.dataSource)
        assertEquals(0.85, versioningState.datasetConfidence)
        assertTrue(versioningState.isDatasetValidated)
        assertEquals(80.0, versioningState.verifiedPercentage)
        assertEquals(20.0, versioningState.userReportedPercentage)
    }

    @Test
    fun `test invalid dataset confidence`() {
        assertThrows<IllegalArgumentException> {
            DatasetVersioningState(
                totalRecords = 100,
                datasetConfidence = 1.5
            )
        }
    }

    @Test
    fun `test invalid negative total records`() {
        assertThrows<IllegalArgumentException> {
            DatasetVersioningState(
                totalRecords = -100
            )
        }
    }

    @Test
    fun `test invalid negative verified records`() {
        assertThrows<IllegalArgumentException> {
            DatasetVersioningState(
                totalRecords = 100,
                verifiedRecords = -50
            )
        }
    }

    @Test
    fun `test invalid negative user reported records`() {
        assertThrows<IllegalArgumentException> {
            DatasetVersioningState(
                totalRecords = 100,
                userReportedRecords = -25
            )
        }
    }

    @Test
    fun `test invalid record count overflow`() {
        assertThrows<IllegalArgumentException> {
            DatasetVersioningState(
                totalRecords = 100,
                verifiedRecords = 60,
                userReportedRecords = 50 // This would exceed total records
            )
        }
    }

    @Test
    fun `test zero record percentage calculations`() {
        val versioningState = DatasetVersioningState.createMinimal(totalRecords = 0)
        
        assertEquals(0.0, versioningState.verifiedPercentage)
        assertEquals(0.0, versioningState.userReportedPercentage)
    }
}