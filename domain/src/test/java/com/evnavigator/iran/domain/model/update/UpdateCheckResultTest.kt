package com.evnavigator.iran.domain.model.update

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for UpdateCheckResult class.
 */
class UpdateCheckResultTest {

    @Test
    fun `no update available result should have correct properties`() {
        val result = UpdateCheckResult.noUpdateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            reason = "No update available"
        )

        assertFalse("isUpdateAvailable should be false", result.isUpdateAvailable)
        assertEquals("datasetId should match", "test-dataset", result.datasetId)
        assertEquals("currentVersion should match", "1.0.0", result.currentVersion)
        assertNull("availableVersion should be null", result.availableVersion)
        assertNull("priority should be null", result.priority)
        assertNull("downloadSizeBytes should be null", result.downloadSizeBytes)
        assertNull("releaseNotes should be null", result.releaseNotes)
        assertNull("publicationTimestamp should be null", result.publicationTimestamp)
        assertFalse("isMandatory should be false", result.isMandatory)
        assertNull("constraints should be null", result.constraints)
        assertTrue("canBeDeferred should be true", result.canBeDeferred)
        assertEquals("availabilityReason should match", "No update available", result.availabilityReason)
        assertNull("blockReason should be null", result.blockReason)
        assertFalse("isStorageBlocked should be false", result.isStorageBlocked)
        assertFalse("isNetworkBlocked should be false", result.isNetworkBlocked)
        assertFalse("isBatteryBlocked should be false", result.isBatteryBlocked)
        assertFalse("isNavigationBlocked should be false", result.isNavigationBlocked)
        assertFalse("isDeviceStateBlocked should be false", result.isDeviceStateBlocked)
        assertFalse("isOtherBlocked should be false", result.isOtherBlocked)
        assertNull("errorMessage should be null", result.errorMessage)
    }

    @Test
    fun `update available result should have correct properties`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024,
            releaseNotes = "New features",
            publicationTimestamp = System.currentTimeMillis(),
            isMandatory = false,
            constraints = UpdateConstraint()
        )

        assertTrue("isUpdateAvailable should be true", result.isUpdateAvailable)
        assertEquals("datasetId should match", "test-dataset", result.datasetId)
        assertEquals("currentVersion should match", "1.0.0", result.currentVersion)
        assertEquals("availableVersion should match", "1.1.0", result.availableVersion)
        assertEquals("priority should match", UpdatePriority.HIGH, result.priority)
        assertEquals("downloadSizeBytes should match", 100L * 1024 * 1024, result.downloadSizeBytes)
        assertEquals("releaseNotes should match", "New features", result.releaseNotes)
        assertNotNull("publicationTimestamp should not be null", result.publicationTimestamp)
        assertFalse("isMandatory should be false", result.isMandatory)
        assertNotNull("constraints should not be null", result.constraints)
        assertTrue("canBeDeferred should be true", result.canBeDeferred)
        assertEquals("availabilityReason should match", "Update available", result.availabilityReason)
        assertNull("blockReason should be null", result.blockReason)
        assertFalse("isStorageBlocked should be false", result.isStorageBlocked)
        assertFalse("isNetworkBlocked should be false", result.isNetworkBlocked)
        assertFalse("isBatteryBlocked should be false", result.isBatteryBlocked)
        assertFalse("isNavigationBlocked should be false", result.isNavigationBlocked)
        assertFalse("isDeviceStateBlocked should be false", result.isDeviceStateBlocked)
        assertFalse("isOtherBlocked should be false", result.isOtherBlocked)
        assertNull("errorMessage should be null", result.errorMessage)
    }

    @Test
    fun `update check failed result should have correct properties`() {
        val result = UpdateCheckResult.updateCheckFailed(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            errorMessage = "Network error"
        )

        assertFalse("isUpdateAvailable should be false", result.isUpdateAvailable)
        assertEquals("datasetId should match", "test-dataset", result.datasetId)
        assertEquals("currentVersion should match", "1.0.0", result.currentVersion)
        assertNull("availableVersion should be null", result.availableVersion)
        assertNull("priority should be null", result.priority)
        assertNull("downloadSizeBytes should be null", result.downloadSizeBytes)
        assertNull("releaseNotes should be null", result.releaseNotes)
        assertNull("publicationTimestamp should be null", result.publicationTimestamp)
        assertFalse("isMandatory should be false", result.isMandatory)
        assertNull("constraints should be null", result.constraints)
        assertTrue("canBeDeferred should be true", result.canBeDeferred)
        assertNull("availabilityReason should be null", result.availabilityReason)
        assertNull("blockReason should be null", result.blockReason)
        assertFalse("isStorageBlocked should be false", result.isStorageBlocked)
        assertFalse("isNetworkBlocked should be false", result.isNetworkBlocked)
        assertFalse("isBatteryBlocked should be false", result.isBatteryBlocked)
        assertFalse("isNavigationBlocked should be false", result.isNavigationBlocked)
        assertFalse("isDeviceStateBlocked should be false", result.isDeviceStateBlocked)
        assertFalse("isOtherBlocked should be false", result.isOtherBlocked)
        assertEquals("errorMessage should match", "Network error", result.errorMessage)
    }

    @Test
    fun `getVersion should return available version if available`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        )

        assertEquals("getVersion should return available version", "1.1.0", result.getVersion())
    }

    @Test
    fun `getVersion should return current version if no update available`() {
        val result = UpdateCheckResult.noUpdateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0"
        )

        assertEquals("getVersion should return current version", "1.0.0", result.getVersion())
    }

    @Test
    fun `getPriority should return available priority if available`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        )

        assertEquals("getPriority should return available priority", UpdatePriority.HIGH, result.getPriority())
    }

    @Test
    fun `getPriority should return LOW if no priority available`() {
        val result = UpdateCheckResult.noUpdateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0"
        )

        assertEquals("getPriority should return LOW", UpdatePriority.LOW, result.getPriority())
    }

    @Test
    fun `getDownloadSizeFormatted should return correct format for bytes`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 500
        )

        assertEquals("getDownloadSizeFormatted should return '500 B'", "500 B", result.getDownloadSizeFormatted())
    }

    @Test
    fun `getDownloadSizeFormatted should return correct format for kilobytes`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024
        )

        assertEquals("getDownloadSizeFormatted should return '1.0 MB'", "1.0 MB", result.getDownloadSizeFormatted())
    }

    @Test
    fun `getDownloadSizeFormatted should return correct format for megabytes`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        )

        assertEquals("getDownloadSizeFormatted should return '100.0 MB'", "100.0 MB", result.getDownloadSizeFormatted())
    }

    @Test
    fun `getDownloadSizeFormatted should return correct format for gigabytes`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 5L * 1024 * 1024 * 1024
        )

        assertEquals("getDownloadSizeFormatted should return '5.0 GB'", "5.0 GB", result.getDownloadSizeFormatted())
    }

    @Test
    fun `getDownloadSizeFormatted should return 'Unknown' for null size`() {
        val result = UpdateCheckResult.noUpdateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0"
        )

        assertEquals("getDownloadSizeFormatted should return 'Unknown'", "Unknown", result.getDownloadSizeFormatted())
    }

    @Test
    fun `isBlocked should return true when any block flag is true`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        )

        // All block flags should be false initially
        assertFalse("isBlocked should be false initially", result.isBlocked())

        // Set one block flag
        val resultWithStorageBlocked = result.copy(isStorageBlocked = true)
        assertTrue("isBlocked should be true when isStorageBlocked is true", resultWithStorageBlocked.isBlocked())

        // Set another block flag
        val resultWithNetworkBlocked = result.copy(isNetworkBlocked = true)
        assertTrue("isBlocked should be true when isNetworkBlocked is true", resultWithNetworkBlocked.isBlocked())

        // Set battery block flag
        val resultWithBatteryBlocked = result.copy(isBatteryBlocked = true)
        assertTrue("isBlocked should be true when isBatteryBlocked is true", resultWithBatteryBlocked.isBlocked())

        // Set navigation block flag
        val resultWithNavigationBlocked = result.copy(isNavigationBlocked = true)
        assertTrue("isBlocked should be true when isNavigationBlocked is true", resultWithNavigationBlocked.isBlocked())

        // Set device state block flag
        val resultWithDeviceStateBlocked = result.copy(isDeviceStateBlocked = true)
        assertTrue("isBlocked should be true when isDeviceStateBlocked is true", resultWithDeviceStateBlocked.isBlocked())

        // Set other block flag
        val resultWithOtherBlocked = result.copy(isOtherBlocked = true)
        assertTrue("isBlocked should be true when isOtherBlocked is true", resultWithOtherBlocked.isBlocked())
    }

    @Test
    fun `getBlockReason should return correct reason when blocked`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        )

        assertNull("getBlockReason should be null when not blocked", result.getBlockReason())

        val resultWithStorageBlocked = result.copy(isStorageBlocked = true)
        assertEquals("getBlockReason should return storage reason", "Insufficient storage", resultWithStorageBlocked.getBlockReason())

        val resultWithNetworkBlocked = result.copy(isNetworkBlocked = true)
        assertEquals("getBlockReason should return network reason", "Network constraints", resultWithNetworkBlocked.getBlockReason())

        val resultWithBatteryBlocked = result.copy(isBatteryBlocked = true)
        assertEquals("getBlockReason should return battery reason", "Battery constraints", resultWithBatteryBlocked.getBlockReason())

        val resultWithNavigationBlocked = result.copy(isNavigationBlocked = true)
        assertEquals("getBlockReason should return navigation reason", "Navigation active", resultWithNavigationBlocked.getBlockReason())

        val resultWithDeviceStateBlocked = result.copy(isDeviceStateBlocked = true)
        assertEquals("getBlockReason should return device state reason", "Device state constraints", resultWithDeviceStateBlocked.getBlockReason())

        val resultWithOtherBlocked = result.copy(isOtherBlocked = true, blockReason = "Custom reason")
        assertEquals("getBlockReason should return custom reason", "Custom reason", resultWithOtherBlocked.getBlockReason())
    }

    @Test
    fun `isInstallable should return true when update is available and not blocked`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        )

        assertTrue("isInstallable should be true when update is available and not blocked", result.isInstallable())
    }

    @Test
    fun `isInstallable should return false when update is not available`() {
        val result = UpdateCheckResult.noUpdateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0"
        )

        assertFalse("isInstallable should be false when update is not available", result.isInstallable())
    }

    @Test
    fun `isInstallable should return false when update is blocked`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024
        ).copy(isStorageBlocked = true)

        assertFalse("isInstallable should be false when update is blocked", result.isInstallable())
    }

    @Test
    fun `isInstallable should return false when update check failed`() {
        val result = UpdateCheckResult.updateCheckFailed(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            errorMessage = "Network error"
        )

        assertFalse("isInstallable should be false when update check failed", result.isInstallable())
    }

    @Test
    fun `getCurrentVersionAgeHours should return age in hours`() {
        val timestamp = System.currentTimeMillis() - (2 * 60 * 60 * 1000) // 2 hours ago
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024,
            publicationTimestamp = timestamp
        )

        assertEquals("getCurrentVersionAgeHours should return 2", 2L, result.getCurrentVersionAgeHours())
    }

    @Test
    fun `getCurrentVersionAgeHours should return null when publication timestamp is null`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024,
            publicationTimestamp = null
        )

        assertNull("getCurrentVersionAgeHours should return null when publication timestamp is null", result.getCurrentVersionAgeHours())
    }

    @Test
    fun `getAvailableVersionAgeHours should return age in hours`() {
        val timestamp = System.currentTimeMillis() - (5 * 60 * 60 * 1000) // 5 hours ago
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024,
            publicationTimestamp = timestamp
        )

        assertEquals("getAvailableVersionAgeHours should return 5", 5L, result.getAvailableVersionAgeHours())
    }

    @Test
    fun `getAvailableVersionAgeHours should return null when publication timestamp is null`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 100 * 1024 * 1024,
            publicationTimestamp = null
        )

        assertNull("getAvailableVersionAgeHours should return null when publication timestamp is null", result.getAvailableVersionAgeHours())
    }

    @Test
    fun `mandatory update should not be deferrable`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.CRITICAL,
            downloadSizeBytes = 100 * 1024 * 1024,
            isMandatory = true
        )

        assertFalse("mandatory update should not be deferrable", result.canBeDeferred)
    }

    @Test
    fun `non-mandatory update should be deferrable`() {
        val result = UpdateCheckResult.updateAvailable(
            datasetId = "test-dataset",
            currentVersion = "1.0.0",
            availableVersion = "1.1.0",
            priority = UpdatePriority.LOW,
            downloadSizeBytes = 100 * 1024 * 1024,
            isMandatory = false
        )

        assertTrue("non-mandatory update should be deferrable", result.canBeDeferred)
    }
}
