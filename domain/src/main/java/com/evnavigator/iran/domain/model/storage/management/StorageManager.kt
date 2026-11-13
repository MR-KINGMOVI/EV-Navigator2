package com.evnavigator.iran.domain.model.storage.management

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * StorageManager - Production-grade storage accounting and management
 * 
 * Responsibilities:
 * - Track active datasets
 * - Track previous datasets
 * - Track staging packages
 * - Track partial downloads
 * - Track cache
 * - Track database
 * - Track metadata
 * - Calculate storage sizes
 * - Check free space
 * - Implement cleanup policy
 * - Implement retention policy
 * - Safe deletion of obsolete non-active data
 * 
 * Safety Rules:
 * - NEVER delete active data
 * - NEVER delete required rollback data without validating policy
 * - NEVER delete user-created data without explicit policy
 * - NEVER delete critical navigation state
 * 
 * Architecture:
 * - Active datasets: Currently in use by the application
 * - Previous datasets: Previously active datasets available for rollback
 * - Staging packages: Downloaded packages awaiting installation
 * - Partial downloads: Incomplete downloads that can be resumed
 * - Cache: Application cache data
 * - Database: Room database files
 * - Metadata: Dataset metadata and application metadata
 */
interface StorageManager {
    
    /**
     * Get storage usage statistics
     * 
     * @return Flow of storage usage statistics
     */
    fun getStorageUsage(): Flow<StorageUsageStatistics>
    
    /**
     * Calculate total storage size for a dataset
     * 
     * @param datasetId Dataset identifier
     * @param datasetType Dataset type
     * @return Total storage size in bytes
     */
    suspend fun calculateDatasetSize(datasetId: String, datasetType: String): Long
    
    /**
     * Check if sufficient storage is available for a dataset
     * 
     * @param requiredSize Required size in bytes
     * @return StorageCheckResult indicating availability
     */
    suspend fun hasSufficientStorage(requiredSize: Long): StorageCheckResult
    
    /**
     * Get available storage in bytes
     * 
     * @return Available storage in bytes
     */
    suspend fun getAvailableStorage(): Long
    
    /**
     * Get total storage in bytes
     * 
     * @return Total storage in bytes
     */
    suspend fun getTotalStorage(): Long
    
    /**
     * Get used storage in bytes
     * 
     * @return Used storage in bytes
     */
    suspend fun getUsedStorage(): Long
    
    /**
     * Get storage usage percentage
     * 
     * @return Used storage percentage (0-100)
     */
    suspend fun getStorageUsagePercentage(): Double
    
    /**
     * Clean up obsolete storage
     * 
     * @param maxSize Maximum size to keep in bytes
     * @param policy Cleanup policy to apply
     * @return CleanupResult with details
     */
    suspend fun cleanObsoleteStorage(maxSize: Long, policy: CleanupPolicy): CleanupResult
    
    /**
     * Delete obsolete non-active data
     * 
     * @param policy Cleanup policy
     * @return CleanupResult with details
     */
    suspend fun deleteObsoleteData(policy: CleanupPolicy): CleanupResult
    
    /**
     * Get storage breakdown by category
     * 
     * @return Map of category to size in bytes
     */
    suspend fun getStorageBreakdown(): Map<String, Long>
    
    /**
     * Get active datasets
     * 
     * @return List of active dataset identifiers
     */
    suspend fun getActiveDatasets(): List<String>
    
    /**
     * Get previous datasets
     * 
     * @return List of previous dataset identifiers
     */
    suspend fun getPreviousDatasets(): List<String>
    
    /**
     * Get staging packages
     * 
     * @return List of staging package identifiers
     */
    suspend fun getStagingPackages(): List<String>
    
    /**
     * Get partial downloads
     * 
     * @return List of partial download identifiers
     */
    suspend fun getPartialDownloads(): List<String>
    
    /**
     * Get cache size
     * 
     * @return Cache size in bytes
     */
    suspend fun getCacheSize(): Long
    
    /**
     * Get database size
     * 
     * @return Database size in bytes
     */
    suspend fun getDatabaseSize(): Long
    
    /**
     * Get metadata size
     * 
     * @return Metadata size in bytes
     */
    suspend fun getMetadataSize(): Long
    
    /**
     * Get storage pressure level
     * 
     * @return StoragePressureLevel indicating pressure level
     */
    suspend fun getStoragePressureLevel(): StoragePressureLevel
    
    /**
     * Get storage pressure threshold
     * 
     * @return Storage pressure threshold in bytes
     */
    suspend fun getStoragePressureThreshold(): Long
    
    /**
     * Set storage pressure threshold
     * 
     * @param threshold Storage pressure threshold in bytes
     */
    suspend fun setStoragePressureThreshold(threshold: Long)
    
    /**
     * Get retention policy
     * 
     * @return RetentionPolicy
     */
    suspend fun getRetentionPolicy(): RetentionPolicy
    
    /**
     * Set retention policy
     * 
     * @param policy RetentionPolicy
     */
    suspend fun setRetentionPolicy(policy: RetentionPolicy)
    
    /**
     * Get cleanup policy
     * 
     * @return CleanupPolicy
     */
    suspend fun getCleanupPolicy(): CleanupPolicy

    /**
     * Set cleanup policy
     * 
     * @param policy CleanupPolicy
     */
    suspend fun setCleanupPolicy(policy: CleanupPolicy)
    

}

/**
 * Storage usage statistics
 * 
 * @property totalBytes Total storage capacity
 * @property availableBytes Available storage
 * @property usedBytes Used storage
 * @property availablePercentage Available percentage (0-100)
 * @property usedPercentage Used percentage (0-100)
 * @property pressureLevel Storage pressure level
 */
data class StorageUsageStatistics(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val availablePercentage: Double,
    val usedPercentage: Double,
    val pressureLevel: StoragePressureLevel
)

/**
 * Storage check result
 * 
 * @property available Whether storage is available
 * @property availableBytes Available storage in bytes
 * @property requiredBytes Required storage in bytes
 * @property reason Reason for unavailability (if not available)
 * @property thresholdPercentage Threshold percentage for pressure
 */
data class StorageCheckResult(
    val available: Boolean,
    val availableBytes: Long,
    val requiredBytes: Long,
    val reason: String? = null,
    val thresholdPercentage: Double = 85.0
)

/**
 * Cleanup result
 * 
 * @property success Whether cleanup succeeded
 * @property freedBytes Bytes freed
 * @property deletedCount Number of items deleted
 * @property failedCount Number of items that failed to delete
 * @property errors List of error messages
 * @property policy Applied policy
 */
data class CleanupResult(
    val success: Boolean,
    val freedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int,
    val errors: List<String>,
    val policy: CleanupPolicy
)

/**
 * Storage pressure level
 * 
 * @property level Pressure level
 * @property percentage Percentage threshold
 * @property description Human-readable description
 */
enum class StoragePressureLevel(
    val level: Int,
    val percentage: Double,
    val description: String
) {
    LOW(0, 70.0, "Low storage pressure"),
    MEDIUM(1, 85.0, "Medium storage pressure"),
    HIGH(2, 90.0, "High storage pressure"),
    CRITICAL(3, 95.0, "Critical storage pressure");
    
    companion object {
        fun fromPercentage(percentage: Double): StoragePressureLevel {
            return when {
                percentage >= 95.0 -> CRITICAL
                percentage >= 90.0 -> HIGH
                percentage >= 85.0 -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * Cleanup policy
 * 
 * @property priority Cleanup priority
 * @property maxAge Maximum age in milliseconds for cache entries
 * @property maxSize Maximum size in bytes for cache
 * @property preserveActiveDatasets Whether to preserve active datasets
 * @property preservePreviousDatasets Whether to preserve previous datasets
 * @property preserveRollbackData Whether to preserve rollback data
 * @property preserveUserCreatedData Whether to preserve user-created data
 * @property preserveNavigationState Whether to preserve navigation state
 */
data class CleanupPolicy(
    val priority: CleanupPriority = CleanupPriority.LOW,
    val maxAge: Long = 7 * 24 * 60 * 60 * 1000L, // 7 days default
    val maxSize: Long = 100 * 1024 * 1024L, // 100 MB default
    val preserveActiveDatasets: Boolean = true,
    val preservePreviousDatasets: Boolean = true,
    val preserveRollbackData: Boolean = true,
    val preserveUserCreatedData: Boolean = true,
    val preserveNavigationState: Boolean = true,
    val thresholdPercentage: Double = 85.0,
    val maxPartialDownloads: Int = 5,
    val maxStagingPackages: Int = 2,
    val maxMetadataSize: Long = 50 * 1024 * 1024L
)

/**
 * Cleanup priority
 * 
 * @property priority Priority value
 * @property description Human-readable description
 */
enum class CleanupPriority(
    val priority: Int,
    val description: String
) {
    LOW(1, "Low priority"),
    MEDIUM(2, "Medium priority"),
    HIGH(3, "High priority"),
    CRITICAL(4, "Critical priority");
    
    companion object {
        fun fromPriority(priority: Int): CleanupPriority {
            return when {
                priority >= 4 -> CRITICAL
                priority >= 3 -> HIGH
                priority >= 2 -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * Retention policy
 * 
 * @property maxPreviousVersions Maximum number of previous versions to retain
 * @property maxStagingPackages Maximum number of staging packages to retain
 * @property maxPartialDownloads Maximum number of partial downloads to retain
 * @property maxCacheSize Maximum cache size in bytes
 * @property maxDatabaseSize Maximum database size in bytes
 * @property maxMetadataSize Maximum metadata size in bytes
 * @property preserveUserCreatedData Whether to preserve user-created data
 * @property preserveNavigationState Whether to preserve navigation state
 */
data class RetentionPolicy(
    val maxPreviousVersions: Int = 3,
    val maxStagingPackages: Int = 2,
    val maxPartialDownloads: Int = 5,
    val maxCacheSize: Long = 200 * 1024 * 1024L, // 200 MB default
    val maxDatabaseSize: Long = 500 * 1024 * 1024L, // 500 MB default
    val maxMetadataSize: Long = 50 * 1024 * 1024L, // 50 MB default
    val preserveUserCreatedData: Boolean = true,
    val preserveNavigationState: Boolean = true
)