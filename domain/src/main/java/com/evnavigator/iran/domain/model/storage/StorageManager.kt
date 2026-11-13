package com.evnavigator.iran.domain.model.storage

/**
 * Manager for storage operations and accounting.
 * 
 * This class provides:
 * - Storage availability checking
 * - Storage size calculations
 * - Storage cleanup policies
 * - Storage usage tracking
 */
interface StorageManager {
    /**
     * Get available storage in bytes.
     * 
     * @return Available storage in bytes
     */
    suspend fun getAvailableStorage(): Long

    /**
     * Get total storage in bytes.
     * 
     * @return Total storage in bytes
     */
    suspend fun getTotalStorage(): Long

    /**
     * Get used storage in bytes.
     * 
     * @return Used storage in bytes
     */
    suspend fun getUsedStorage(): Long

    /**
     * Check if there is sufficient storage for a given size.
     * 
     * @param requiredSize Required storage in bytes
     * @return true if sufficient storage is available
     */
    suspend fun hasSufficientStorage(requiredSize: Long): Boolean

    /**
     * Get storage usage statistics.
     * 
     * @return Storage usage statistics
     */
    suspend fun getStorageUsage(): StorageUsage

    /**
     * Clean up obsolete storage.
     * 
     * @param maxSize Maximum allowed storage size
     * @return Amount of storage freed in bytes
     */
    suspend fun cleanObsoleteStorage(maxSize: Long): Long

    /**
     * Get storage information for a specific path.
     * 
     * @param path Storage path
     * @return Storage information
     */
    suspend fun getStorageInfo(path: String): StorageInfo
}

/**
 * Storage usage statistics.
 */
data class StorageUsage(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val availablePercentage: Double,
    val usedPercentage: Double
)

/**
 * Storage information.
 */
data class StorageInfo(
    val path: String,
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val isExternal: Boolean = false,
    val isEmulated: Boolean = false
)
