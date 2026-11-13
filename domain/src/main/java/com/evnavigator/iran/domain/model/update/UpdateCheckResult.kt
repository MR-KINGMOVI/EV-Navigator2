package com.evnavigator.iran.domain.model.update

/**
 * Result of checking for available dataset updates.
 * 
 * This class represents the outcome of an update check operation,
 * including whether an update is available, its details, and any constraints.
 */
data class UpdateCheckResult(
    /**
     * Dataset ID that was checked for updates.
     */
    val datasetId: String,

    /**
     * Whether an update is available.
     */
    val isUpdateAvailable: Boolean,

    /**
     * Current installed version.
     */
    val currentVersion: String,

    /**
     * Available update version.
     * Null if no update is available.
     */
    val availableVersion: String?,

    /**
     * Priority of the available update.
     * Null if no update is available.
     */
    @get:JvmName("getRawPriority")
    val priority: UpdatePriority?,

    /**
     * Size of the available update in bytes.
     * Null if size is not available or no update is available.
     */
    val downloadSizeBytes: Long?,

    /**
     * Release notes for the update.
     * Null if not available.
     */
    val releaseNotes: String?,

    /**
     * Publication timestamp of the update.
     * Null if not available.
     */
    val publicationTimestamp: Long?,

    /**
     * Expiration timestamp for the update.
     * Null if not available or no expiration.
     */
    val expirationTimestamp: Long?,

    /**
     * Whether the update is mandatory.
     * Mandatory updates must be installed.
     */
    val isMandatory: Boolean,

    /**
     * Constraints for updating this dataset.
     * Null if no constraints apply.
     */
    val constraints: UpdateConstraint?,

    /**
     * Whether the update can be deferred.
     * False for mandatory updates.
     */
    val canBeDeferred: Boolean,

    /**
     * Reason for update availability.
     * Null if update is available.
     */
    val availabilityReason: String?,

    /**
     * Whether the update is blocked by system constraints.
     * Null if not blocked.
     */
    @get:JvmName("getCustomBlockReason")
    val blockReason: String?,

    /**
     * Whether the update is blocked due to storage constraints.
     */
    val isStorageBlocked: Boolean = false,
    val isNetworkBlocked: Boolean = false,
    val isBatteryBlocked: Boolean = false,
    val isNavigationBlocked: Boolean = false,
    val isDeviceStateBlocked: Boolean = false,
    val isOtherBlocked: Boolean = false,
    val errorMessage: String? = null,
    val checkTimestamp: Long = System.currentTimeMillis(),
    @get:JvmName("getCustomIsBlocked")
    val isBlocked: Boolean = false,
    val isDeferred: Boolean = false,
    val isAvailable: Boolean = true,
    val isDownloadable: Boolean = true,
    @get:JvmName("getCustomIsInstallable")
    val isInstallable: Boolean = true,
    val isCompatible: Boolean = true,
    val isFresh: Boolean = true,
    val isStale: Boolean = false,
    val isExpired: Boolean = false,
    val isCorrupted: Boolean = false,
    val isPartial: Boolean = false,
    val isOffline: Boolean = false,
    val isLive: Boolean = false,
    val isCached: Boolean = false,
    val isUnknown: Boolean = false,
    val isInvalid: Boolean = false,
    val freshness: String = "FRESH",
    val degradation: String = "NONE",
    val coverage: String = "FULL",
    val integrity: String = "VALID",
    val installState: String = "INSTALLED",
    val source: String = "REMOTE",
    val freshnessTimestamp: Long? = null,
    val checksum: String? = null,
    val checksumAlgorithm: String? = null,
    val compression: String? = null,
    val schemaVersion: Any? = null,
    val packageVersion: String? = null,
    val datasetVersion: String? = null,
    val dependencies: List<Any> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Get the update version if available, otherwise return current version.
     */
    fun getVersion(): String = availableVersion ?: currentVersion

    /**
     * Get the priority if available, otherwise return LOW.
     */
    fun getPriority(): UpdatePriority = priority ?: UpdatePriority.LOW

    /**
     * Get the size in human-readable format.
     */
    fun getDownloadSizeFormatted(): String {
        return when {
            downloadSizeBytes == null -> "Unknown"
            downloadSizeBytes < 1024 -> "$downloadSizeBytes B"
            downloadSizeBytes < 1024 * 1024 -> "${String.format("%.1f", downloadSizeBytes / 1024.0)} KB"
            downloadSizeBytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", downloadSizeBytes / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.1f", downloadSizeBytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    /**
     * Check if the update is blocked.
     */
    fun isBlocked(): Boolean {
        return isStorageBlocked || isNetworkBlocked || isBatteryBlocked ||
               isNavigationBlocked || isDeviceStateBlocked || isOtherBlocked
    }

    /**
     * Get the block reason if blocked.
     */
    fun getBlockReason(): String? {
        return when {
            isStorageBlocked -> "Insufficient storage"
            isNetworkBlocked -> "Network constraints"
            isBatteryBlocked -> "Battery constraints"
            isNavigationBlocked -> "Navigation active"
            isDeviceStateBlocked -> "Device state constraints"
            isOtherBlocked -> blockReason ?: "Other constraints"
            else -> null
        }
    }

    /**
     * Check if the update is available and can be installed.
     */
    fun isInstallable(): Boolean {
        return isUpdateAvailable && !isBlocked() && errorMessage == null
    }

    /**
     * Get the age of the current version in hours.
     */
    fun getCurrentVersionAgeHours(): Long? {
        if (publicationTimestamp == null) return null
        return (System.currentTimeMillis() - publicationTimestamp) / (1000 * 60 * 60)
    }

    /**
     * Get the age of the available version in hours.
     */
    fun getAvailableVersionAgeHours(): Long? {
        if (publicationTimestamp == null) return null
        return (System.currentTimeMillis() - publicationTimestamp) / (1000 * 60 * 60)
    }

    companion object {
        /**
         * Create a result indicating no update is available.
         */
        fun noUpdateAvailable(
            datasetId: String,
            currentVersion: String,
            reason: String = "No update available"
        ): UpdateCheckResult {
            return UpdateCheckResult(
                datasetId = datasetId,
                isUpdateAvailable = false,
                currentVersion = currentVersion,
                availableVersion = null,
                priority = null,
                downloadSizeBytes = null,
                releaseNotes = null,
                publicationTimestamp = null,
                expirationTimestamp = null,
                isMandatory = false,
                constraints = null,
                canBeDeferred = true,
                availabilityReason = reason,
                blockReason = null,
                isStorageBlocked = false,
                isNetworkBlocked = false,
                isBatteryBlocked = false,
                isNavigationBlocked = false,
                isDeviceStateBlocked = false,
                isOtherBlocked = false,
                errorMessage = null
            )
        }

        /**
         * Create a result indicating an update is available.
         */
        fun updateAvailable(
            datasetId: String,
            currentVersion: String,
            availableVersion: String,
            priority: UpdatePriority,
            downloadSizeBytes: Long,
            releaseNotes: String? = null,
            publicationTimestamp: Long? = null,
            expirationTimestamp: Long? = null,
            isMandatory: Boolean = false,
            constraints: UpdateConstraint? = null
        ): UpdateCheckResult {
            return UpdateCheckResult(
                datasetId = datasetId,
                isUpdateAvailable = true,
                currentVersion = currentVersion,
                availableVersion = availableVersion,
                priority = priority,
                downloadSizeBytes = downloadSizeBytes,
                releaseNotes = releaseNotes,
                publicationTimestamp = publicationTimestamp,
                expirationTimestamp = expirationTimestamp,
                isMandatory = isMandatory,
                constraints = constraints,
                canBeDeferred = !isMandatory,
                availabilityReason = "Update available",
                blockReason = null,
                isStorageBlocked = false,
                isNetworkBlocked = false,
                isBatteryBlocked = false,
                isNavigationBlocked = false,
                isDeviceStateBlocked = false,
                isOtherBlocked = false,
                errorMessage = null
            )
        }

        /**
         * Create a result indicating an update check failed.
         */
        fun updateCheckFailed(
            datasetId: String,
            currentVersion: String,
            errorMessage: String
        ): UpdateCheckResult {
            return UpdateCheckResult(
                datasetId = datasetId,
                isUpdateAvailable = false,
                currentVersion = currentVersion,
                availableVersion = null,
                priority = null,
                downloadSizeBytes = null,
                releaseNotes = null,
                publicationTimestamp = null,
                expirationTimestamp = null,
                isMandatory = false,
                constraints = null,
                canBeDeferred = true,
                availabilityReason = null,
                blockReason = null,
                isStorageBlocked = false,
                isNetworkBlocked = false,
                isBatteryBlocked = false,
                isNavigationBlocked = false,
                isDeviceStateBlocked = false,
                isOtherBlocked = false,
                errorMessage = errorMessage
            )
        }
    }
}
