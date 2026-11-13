package com.evnavigator.iran.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for VersionSelector
 */
class VersionSelectorTest {

    @Test
    fun `selectBestVersion returns active version if compatible`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "2.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "abc123",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = "2.0.0",
            appVersion = "5.0.0"
        )

        val selected = selector.selectBestVersion()
        assertNotNull(selected)
        assertEquals("2.0.0", selected?.version)
    }

    @Test
    fun `selectBestVersion returns newest compatible version`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "2.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "abc123",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val selected = selector.selectBestVersion()
        assertNotNull(selected)
        assertEquals("2.0.0", selected?.version)
    }

    @Test
    fun `selectBestVersion returns null if no compatible version`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "10.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val selected = selector.selectBestVersion()
        assertNull(selected)
    }

    @Test
    fun `selectNewestVersion returns newest compatible version`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "2.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "abc123",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val selected = selector.selectNewestVersion()
        assertNotNull(selected)
        assertEquals("2.0.0", selected?.version)
    }

    @Test
    fun `selectOldestVersion returns oldest compatible version`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "2.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "abc123",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val selected = selector.selectOldestVersion()
        assertNotNull(selected)
        assertEquals("1.0.0", selected?.version)
    }

    @Test
    fun `isVersionCompatible returns true for compatible version`() {
        val versionInfo = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        val selector = VersionSelector(
            availableVersions = listOf(versionInfo),
            activeVersion = null,
            appVersion = "5.0.0"
        )

        assertTrue(selector.isVersionCompatible(versionInfo))
    }

    @Test
    fun `isVersionCompatible returns false for incompatible version`() {
        val versionInfo = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "10.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        val selector = VersionSelector(
            availableVersions = listOf(versionInfo),
            activeVersion = null,
            appVersion = "5.0.0"
        )

        assertFalse(selector.isVersionCompatible(versionInfo))
    }

    @Test
    fun `getCompatibleVersions returns all compatible versions`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "2.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "abc123",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "test-dataset",
                version = "3.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "ghi789",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val compatible = selector.getCompatibleVersions()
        assertEquals(3, compatible.size)
    }

    @Test
    fun `selectBestVersionForDataset returns best version for specific dataset`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "dataset1",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            ),
            DatasetVersionInfo(
                datasetId = "dataset2",
                version = "2.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis(),
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "abc123",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val bestForDataset1 = selector.selectBestVersionForDataset("dataset1")
        assertNotNull(bestForDataset1)
        assertEquals("1.0.0", bestForDataset1?.version)

        val bestForDataset2 = selector.selectBestVersionForDataset("dataset2")
        assertNotNull(bestForDataset2)
        assertEquals("2.0.0", bestForDataset2?.version)
    }

    @Test
    fun `selectBestVersionForDataset returns null for non-existent dataset`() {
        val versions = listOf(
            DatasetVersionInfo(
                datasetId = "dataset1",
                version = "1.0.0",
                schemaVersion = 1,
                formatVersion = 1,
                source = "test-source",
                publicationTimestamp = System.currentTimeMillis() - 100000,
                expirationTimestamp = null,
                coverage = DatasetCoverage(regions = listOf("region1")),
                dependencies = emptyList(),
                packageSize = 1000,
                checksum = "def456",
                checksumAlgorithm = "SHA-256",
                compression = CompressionType.NONE,
                compatibility = CompatibilityMetadata(
                    minimumAppVersion = "1.0.0",
                    maximumAppVersion = "10.0.0"
                ),
                installationRequirements = emptyList(),
                optionalSignature = null
            )
        )

        val selector = VersionSelector(
            availableVersions = versions,
            activeVersion = null,
            appVersion = "5.0.0"
        )

        val bestForNonExistent = selector.selectBestVersionForDataset("dataset2")
        assertNull(bestForNonExistent)
    }

    @Test
    fun `DatasetVersionInfo isExpired returns true for expired version`() {
        val expiredTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000) // 25 hours ago
        val versionInfo = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = expiredTimestamp,
            expirationTimestamp = expiredTimestamp + (24 * 60 * 60 * 1000), // Expires in 24 hours
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        assertTrue(versionInfo.isExpired())
    }

    @Test
    fun `DatasetVersionInfo isExpired returns false for non-expired version`() {
        val versionInfo = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        assertFalse(versionInfo.isExpired())
    }

    @Test
    fun `DatasetVersionInfo isFresh returns true for fresh version`() {
        val versionInfo = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis() - (12 * 60 * 60 * 1000), // 12 hours ago
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        assertTrue(versionInfo.isFresh())
    }

    @Test
    fun `DatasetVersionInfo isFresh returns false for stale version`() {
        val versionInfo = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000), // 25 hours ago
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        assertFalse(versionInfo.isFresh())
    }

    @Test
    fun `DatasetVersionInfo compareTo works correctly`() {
        val version1 = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "1.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis() - 100000,
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "def456",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        val version2 = DatasetVersionInfo(
            datasetId = "test-dataset",
            version = "2.0.0",
            schemaVersion = 1,
            formatVersion = 1,
            source = "test-source",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            coverage = DatasetCoverage(regions = listOf("region1")),
            dependencies = emptyList(),
            packageSize = 1000,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = CompressionType.NONE,
            compatibility = CompatibilityMetadata(
                minimumAppVersion = "1.0.0",
                maximumAppVersion = "10.0.0"
            ),
            installationRequirements = emptyList(),
            optionalSignature = null
        )

        assertTrue(version2.compareTo(version1) > 0)
        assertTrue(version1.compareTo(version2) < 0)
        assertEquals(0, version1.compareTo(version1))
    }

    @Test
    fun `DatasetCoverage isRegionCovered works correctly`() {
        val coverage = DatasetCoverage(
            regions = listOf("region1", "region2"),
            missingRegions = listOf("region3")
        )

        assertTrue(coverage.isRegionCovered("region1"))
        assertTrue(coverage.isRegionCovered("region2"))
        assertFalse(coverage.isRegionCovered("region3"))
        assertFalse(coverage.isRegionCovered("region4"))
    }

    @Test
    fun `DatasetCoverage getCoveragePercentage works correctly`() {
        val coverage = DatasetCoverage(
            regions = listOf("region1", "region2"),
            missingRegions = listOf("region3"),
            domains = listOf("domain1"),
            missingDomains = listOf("domain2")
        )

        val percentage = coverage.getCoveragePercentage()
        assertEquals(58.33, percentage, 0.01)
    }

    @Test
    fun `CompatibilityMetadata isAppVersionCompatible works correctly`() {
        val compatibility = CompatibilityMetadata(
            minimumAppVersion = "1.0.0",
            maximumAppVersion = "10.0.0"
        )

        assertTrue(compatibility.isAppVersionCompatible("5.0.0"))
        assertTrue(compatibility.isAppVersionCompatible("1.0.0"))
        assertTrue(compatibility.isAppVersionCompatible("10.0.0"))
        assertFalse(compatibility.isAppVersionCompatible("0.9.0"))
        assertFalse(compatibility.isAppVersionCompatible("10.1.0"))
    }
}
