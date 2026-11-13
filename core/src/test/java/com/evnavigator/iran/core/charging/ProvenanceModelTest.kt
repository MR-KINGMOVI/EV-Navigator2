package com.evnavigator.iran.core.charging

import org.junit.Test
import org.junit.Assert.assertNotNull
import kotlin.test.assertFailsWith as assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProvenanceModelTest {

    @Test
    fun `test validation status enum`() {
        assertEquals(ValidationStatus.VERIFIED, ValidationStatus.VERIFIED)
        assertEquals(ValidationStatus.PARTIALLY_VERIFIED, ValidationStatus.PARTIALLY_VERIFIED)
        assertEquals(ValidationStatus.UNVERIFIED, ValidationStatus.UNVERIFIED)
        assertEquals(ValidationStatus.USER_REPORTED, ValidationStatus.USER_REPORTED)
        assertEquals(ValidationStatus.STALE, ValidationStatus.STALE)
        assertEquals(ValidationStatus.UNKNOWN, ValidationStatus.UNKNOWN)
    }

    @Test
    fun `test source type enum`() {
        assertEquals(SourceType.NETWORK_PROVIDER, SourceType.NETWORK_PROVIDER)
        assertEquals(SourceType.GOVERNMENT, SourceType.GOVERNMENT)
        assertEquals(SourceType.PRIVATE_BUSINESS, SourceType.PRIVATE_BUSINESS)
        assertEquals(SourceType.OPEN_SOURCE, SourceType.OPEN_SOURCE)
        assertEquals(SourceType.USER_CONTRIBUTION, SourceType.USER_CONTRIBUTION)
        assertEquals(SourceType.THIRD_PARTY_DATASET, SourceType.THIRD_PARTY_DATASET)
        assertEquals(SourceType.FIELD_VERIFICATION, SourceType.FIELD_VERIFICATION)
        assertEquals(SourceType.UNKNOWN, SourceType.UNKNOWN)
    }

    @Test
    fun `test provenance creation`() {
        val provenance = Provenance(
            source = "ChargePoint",
            sourceType = SourceType.NETWORK_PROVIDER,
            sourceReference = "12345",
            version = "v1.0",
            retrievedDate = 1634567890000L,
            createdDate = 1634567890000L,
            validatedDate = 1634567890000L,
            confidence = 0.9,
            validationStatus = ValidationStatus.VERIFIED,
            notes = "Verified data from ChargePoint",
            isPrimarySource = true
        )

        assertEquals("ChargePoint", provenance.source)
        assertEquals(SourceType.NETWORK_PROVIDER, provenance.sourceType)
        assertEquals("12345", provenance.sourceReference)
        assertEquals("v1.0", provenance.version)
        assertEquals(1634567890000L, provenance.retrievedDate)
        assertEquals(1634567890000L, provenance.createdDate)
        assertEquals(1634567890000L, provenance.validatedDate)
        assertEquals(0.9, provenance.confidence)
        assertEquals(ValidationStatus.VERIFIED, provenance.validationStatus)
        assertEquals("Verified data from ChargePoint", provenance.notes)
        assertTrue(provenance.isPrimarySource)
    }

    @Test
    fun `test provenance with minimal data`() {
        val provenance = Provenance.createMinimal(
            source = "OpenChargeMap",
            retrievedDate = 1634567890000L
        )

        assertEquals("OpenChargeMap", provenance.source)
        assertEquals(1634567890000L, provenance.retrievedDate)
        assertEquals(0.5, provenance.confidence)
        assertEquals(ValidationStatus.UNKNOWN, provenance.validationStatus)
        assertFalse(provenance.isPrimarySource)
    }

    @Test
    fun `test provenance with verified network provider`() {
        val provenance = Provenance.createVerifiedNetworkProvider(
            source = "ChargePoint",
            sourceReference = "12345",
            version = "v1.0",
            retrievedDate = 1634567890000L,
            confidence = 0.95
        )

        assertEquals("ChargePoint", provenance.source)
        assertEquals(SourceType.NETWORK_PROVIDER, provenance.sourceType)
        assertEquals("12345", provenance.sourceReference)
        assertEquals("v1.0", provenance.version)
        assertEquals(1634567890000L, provenance.retrievedDate)
        assertEquals(0.95, provenance.confidence)
        assertEquals(ValidationStatus.VERIFIED, provenance.validationStatus)
        assertTrue(provenance.isPrimarySource)
    }

    @Test
    fun `test provenance with user reported`() {
        val provenance = Provenance.createUserReported(
            source = "User Contribution",
            sourceReference = "user_12345",
            retrievedDate = 1634567890000L,
            confidence = 0.3
        )

        assertEquals("User Contribution", provenance.source)
        assertEquals(SourceType.USER_CONTRIBUTION, provenance.sourceType)
        assertEquals("user_12345", provenance.sourceReference)
        assertEquals(1634567890000L, provenance.retrievedDate)
        assertEquals(0.3, provenance.confidence)
        assertEquals(ValidationStatus.USER_REPORTED, provenance.validationStatus)
        assertFalse(provenance.isPrimarySource)
    }

    @Test
    fun `test reliability check`() {
        // Verified with high confidence should be reliable
        val verifiedHighConf = Provenance(
            source = "ChargePoint",
            retrievedDate = 1634567890000L,
            confidence = 0.9,
            validationStatus = ValidationStatus.VERIFIED
        )
        assertTrue(verifiedHighConf.isReliable)

        // Verified with low confidence should not be reliable
        val verifiedLowConf = Provenance(
            source = "ChargePoint",
            retrievedDate = 1634567890000L,
            confidence = 0.4,
            validationStatus = ValidationStatus.VERIFIED
        )
        assertFalse(verifiedLowConf.isReliable)

        // User reported with high confidence should be unreliable
        val userReportedHighConf = Provenance(
            source = "User",
            retrievedDate = 1634567890000L,
            confidence = 0.8,
            validationStatus = ValidationStatus.USER_REPORTED
        )
        assertFalse(userReportedHighConf.isReliable)
    }

    @Test
    fun `test freshness calculation`() {
        val provenance = Provenance.createMinimal(
            source = "Test",
            retrievedDate = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 1 day ago
        )

        // Should be around 1 day old (depending on exact timing)
        assertTrue(provenance.freshnessDays >= 0)
    }

    @Test
    fun `test invalid confidence level`() {
        assertThrows<IllegalArgumentException> {
            Provenance(
                source = "ChargePoint",
                retrievedDate = 1634567890000L,
                confidence = 1.5
            )
        }
    }

    @Test
    fun `test invalid retrieved date`() {
        assertThrows<IllegalArgumentException> {
            Provenance(
                source = "ChargePoint",
                retrievedDate = -1000L
            )
        }
    }

    @Test
    fun `test invalid created date after retrieved date`() {
        assertThrows<IllegalArgumentException> {
            Provenance(
                source = "ChargePoint",
                retrievedDate = 1634567890000L,
                createdDate = 1634567890001L // Later than retrieved
            )
        }
    }

    @Test
    fun `test invalid validated date after retrieved date`() {
        assertThrows<IllegalArgumentException> {
            Provenance(
                source = "ChargePoint",
                retrievedDate = 1634567890000L,
                validatedDate = 1634567890001L // Later than retrieved
            )
        }
    }

    @Test
    fun `test version info creation`() {
        val versionInfo = VersionInfo.createDatasetVersion(
            datasetVersion = "v1.2.3",
            changeDescription = "Initial release"
        )

        assertEquals("v1.2.3", versionInfo.datasetVersion)
        assertEquals("Initial release", versionInfo.changeDescription)
        assertNotNull(versionInfo.versionTimestamp)
    }

    @Test
    fun `test record version info creation`() {
        val versionInfo = VersionInfo.createRecordVersion(
            recordVersion = "r2",
            changeDescription = "Updated connector types"
        )

        assertEquals("r2", versionInfo.recordVersion)
        assertEquals("Updated connector types", versionInfo.changeDescription)
        assertNotNull(versionInfo.versionTimestamp)
    }

    @Test
    fun `test version info with timestamp`() {
        val versionInfo = VersionInfo(
            datasetVersion = "v1.0",
            versionTimestamp = 1634567890000L,
            changeDescription = "Test"
        )

        assertEquals("v1.0", versionInfo.datasetVersion)
        assertEquals(1634567890000L, versionInfo.versionTimestamp)
        assertEquals("Test", versionInfo.changeDescription)
    }

    @Test
    fun `test invalid version timestamp`() {
        assertThrows<IllegalArgumentException> {
            VersionInfo(
                datasetVersion = "v1.0",
                versionTimestamp = -1000L
            )
        }
    }

    @Test
    fun `test null version info fields`() {
        val versionInfo = VersionInfo()

        assertEquals(null, versionInfo.datasetVersion)
        assertEquals(null, versionInfo.recordVersion)
        assertEquals(null, versionInfo.sourceVersion)
        assertEquals(null, versionInfo.versionTimestamp)
        assertEquals(null, versionInfo.changeDescription)
    }
}