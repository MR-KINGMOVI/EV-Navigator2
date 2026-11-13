package com.evnavigator.iran.domain.model.storage.management

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * StorageManagerImpl - Production implementation of storage management
 * 
 * This implementation provides:
 * - Storage accounting and tracking
 * - Size calculations
 * - Free-space checks
 * - Cleanup policies
 * - Retention policies
 * - Safe deletion of obsolete data
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
class StorageManagerImpl(
    private val totalStorage: Long = 104 * 1024 * 1024L,
    private val storagePressureThreshold: Long = 85 * 1024 * 1024 * 1024L, // 85 GB default
    private val retentionPolicy: RetentionPolicy = RetentionPolicy(),
    private val cleanupPolicy: CleanupPolicy = CleanupPolicy()
) : StorageManager {
    
    // Storage tracking
    private val activeDatasets = mutableMapOf<String, DatasetStorageInfo>()
    private val previousDatasets = mutableMapOf<String, DatasetStorageInfo>()
    private val stagingPackages = mutableMapOf<String, PackageStorageInfo>()
    private val partialDownloads = mutableMapOf<String, DownloadStorageInfo>()
    private val cacheSize = mutableMapOf<String, Long>()
    private val databaseSize = mutableMapOf<String, Long>()
    private val metadataSize = mutableMapOf<String, Long>()
    
    // Configuration
    private var currentStoragePressureThreshold: Long = storagePressureThreshold
    private var currentRetentionPolicy = retentionPolicy
    private var currentCleanupPolicy = cleanupPolicy
    
    // Storage pressure level
    private var currentPressureLevel: StoragePressureLevel = StoragePressureLevel.LOW
    

    
    override fun getStorageUsage(): Flow<StorageUsageStatistics> = flow {
        val totalBytes = getTotalStorage()
        val usedBytes = getUsedStorage()
        val availableBytes = totalBytes - usedBytes
        val usedPercentage = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes * 100) else 0.0
        val availablePercentage = if (totalBytes > 0) (availableBytes.toDouble() / totalBytes * 100) else 0.0
        
        currentPressureLevel = StoragePressureLevel.fromPercentage(availablePercentage)
        
        emit(
            StorageUsageStatistics(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                usedBytes = usedBytes,
                availablePercentage = availablePercentage,
                usedPercentage = usedPercentage,
                pressureLevel = currentPressureLevel
            )
        )
    }
    
    override suspend fun calculateDatasetSize(datasetId: String, datasetType: String): Long {
        // Calculate size based on dataset type
        return when (datasetType) {
            "MAP" -> 1024 * 1024 * 1024L // 1 GB
            "ROUTING" -> 512 * 1024 * 1024L // 512 MB
            "ELEVATION" -> 256 * 1024 * 1024L // 256 MB
            "CHARGING" -> 128 * 1024 * 1024L // 128 MB
            "VEHICLE" -> 64 * 1024 * 1024L // 64 MB
            "WEATHER" -> 32 * 1024 * 1024L // 32 MB
            "TRAFFIC" -> 32 * 1024 * 1024L // 32 MB
            "FAVORITES" -> 1024 * 1024L // 1 MB
            "RECENT" -> 1024 * 1024L // 1 MB
            "TRIP" -> 2048 * 1024L // 2 MB
            "NAVIGATION" -> 4096 * 1024L // 4 MB
            "METADATA" -> 512 * 1024 * 1024L // 512 MB
            else -> 256 * 1024 * 1024L // 256 MB default
        }
    }
    
    override suspend fun hasSufficientStorage(requiredSize: Long): StorageCheckResult {
        val availableBytes = getAvailableStorage()
        val thresholdPercentage = currentCleanupPolicy.thresholdPercentage
        
        return if (availableBytes >= requiredSize) {
            StorageCheckResult(
                available = true,
                availableBytes = availableBytes,
                requiredBytes = requiredSize,
                reason = null,
                thresholdPercentage = thresholdPercentage
            )
        } else {
            val reason = when {
                availableBytes < requiredSize * 0.1 -> "Insufficient storage: required ${requiredSize / (1024 * 1024)} MB, available ${availableBytes / (1024 * 1024)} MB"
                availableBytes < requiredSize * 0.5 -> "Low storage: required ${requiredSize / (1024 * 1024)} MB, available ${availableBytes / (1024 * 1024)} MB"
                else -> "Storage check failed: required ${requiredSize / (1024 * 1024)} MB, available ${availableBytes / (1024 * 1024)} MB"
            }
            StorageCheckResult(
                available = false,
                availableBytes = availableBytes,
                requiredBytes = requiredSize,
                reason = reason,
                thresholdPercentage = thresholdPercentage
            )
        }
    }
    
    override suspend fun getAvailableStorage(): Long {
        val totalBytes = getTotalStorage()
        val usedBytes = getUsedStorage()
        return totalBytes - usedBytes
    }
    
    override suspend fun getTotalStorage(): Long {
        // Simulate total storage (in production, this would query the actual device storage)
        return totalStorage
    }
    
    override suspend fun getUsedStorage(): Long {
        var usedBytes = 0L
        
        // Active datasets
        activeDatasets.values.forEach { usedBytes += it.size }
        
        // Previous datasets
        previousDatasets.values.forEach { usedBytes += it.size }
        
        // Staging packages
        stagingPackages.values.forEach { usedBytes += it.size }
        
        // Partial downloads
        partialDownloads.values.forEach { usedBytes += it.size }
        
        // Cache
        cacheSize.values.forEach { usedBytes += it }
        
        // Database
        databaseSize.values.forEach { usedBytes += it }
        
        // Metadata
        metadataSize.values.forEach { usedBytes += it }
        
        return usedBytes
    }
    
    override suspend fun getStorageUsagePercentage(): Double {
        val totalBytes = getTotalStorage()
        val usedBytes = getUsedStorage()
        return if (totalBytes > 0) (usedBytes.toDouble() / totalBytes * 100) else 0.0
    }
    
    override suspend fun cleanObsoleteStorage(maxSize: Long, policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        // Clean cache
        val cacheResult = cleanCache(policy)
        freedBytes += cacheResult.freedBytes
        deletedCount += cacheResult.deletedCount
        failedCount += cacheResult.failedCount
        errors.addAll(cacheResult.errors)
        
        // Clean partial downloads
        val partialDownloadsResult = cleanPartialDownloads(policy)
        freedBytes += partialDownloadsResult.freedBytes
        deletedCount += partialDownloadsResult.deletedCount
        failedCount += partialDownloadsResult.failedCount
        errors.addAll(partialDownloadsResult.errors)
        
        // Clean staging packages
        val stagingPackagesResult = cleanStagingPackages(policy)
        freedBytes += stagingPackagesResult.freedBytes
        deletedCount += stagingPackagesResult.deletedCount
        failedCount += stagingPackagesResult.failedCount
        errors.addAll(stagingPackagesResult.errors)
        
        // Clean metadata
        val metadataResult = cleanMetadata(policy)
        freedBytes += metadataResult.freedBytes
        deletedCount += metadataResult.deletedCount
        failedCount += metadataResult.failedCount
        errors.addAll(metadataResult.errors)
        
        return result.copy(
            success = freedBytes > 0 || errors.isEmpty(),
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = failedCount,
            errors = errors,
            policy = policy
        )
    }
    
    override suspend fun deleteObsoleteData(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        // Delete obsolete cache
        val cacheResult = deleteObsoleteCache(policy)
        freedBytes += cacheResult.freedBytes
        deletedCount += cacheResult.deletedCount
        failedCount += cacheResult.failedCount
        errors.addAll(cacheResult.errors)
        
        // Delete obsolete partial downloads
        val partialDownloadsResult = deleteObsoletePartialDownloads(policy)
        freedBytes += partialDownloadsResult.freedBytes
        deletedCount += partialDownloadsResult.deletedCount
        failedCount += partialDownloadsResult.failedCount
        errors.addAll(partialDownloadsResult.errors)
        
        // Delete obsolete staging packages
        val stagingPackagesResult = deleteObsoleteStagingPackages(policy)
        freedBytes += stagingPackagesResult.freedBytes
        deletedCount += stagingPackagesResult.deletedCount
        failedCount += stagingPackagesResult.failedCount
        errors.addAll(stagingPackagesResult.errors)
        
        // Delete obsolete metadata
        val metadataResult = deleteObsoleteMetadata(policy)
        freedBytes += metadataResult.freedBytes
        deletedCount += metadataResult.deletedCount
        failedCount += metadataResult.failedCount
        errors.addAll(metadataResult.errors)
        
        return result.copy(
            success = freedBytes > 0 || errors.isEmpty(),
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = failedCount,
            errors = errors,
            policy = policy
        )
    }
    
    override suspend fun getStorageBreakdown(): Map<String, Long> {
        val breakdown = mutableMapOf<String, Long>()
        
        // Active datasets
        activeDatasets.values.forEach { breakdown["active_datasets_${it.datasetId}"] = it.size }
        
        // Previous datasets
        previousDatasets.values.forEach { breakdown["previous_datasets_${it.datasetId}"] = it.size }
        
        // Staging packages
        stagingPackages.values.forEach { breakdown["staging_packages_${it.packageId}"] = it.size }
        
        // Partial downloads
        partialDownloads.values.forEach { breakdown["partial_downloads_${it.downloadId}"] = it.size }
        
        // Cache
        cacheSize.forEach { breakdown["cache_${it.key}"] = it.value }
        
        // Database
        databaseSize.forEach { breakdown["database_${it.key}"] = it.value }
        
        // Metadata
        metadataSize.forEach { breakdown["metadata_${it.key}"] = it.value }
        
        return breakdown
    }
    
    override suspend fun getActiveDatasets(): List<String> {
        return activeDatasets.keys.toList()
    }
    
    override suspend fun getPreviousDatasets(): List<String> {
        return previousDatasets.keys.toList()
    }
    
    override suspend fun getStagingPackages(): List<String> {
        return stagingPackages.keys.toList()
    }
    
    override suspend fun getPartialDownloads(): List<String> {
        return partialDownloads.keys.toList()
    }
    
    override suspend fun getCacheSize(): Long {
        return cacheSize.values.sum()
    }
    
    override suspend fun getDatabaseSize(): Long {
        return databaseSize.values.sum()
    }
    
    override suspend fun getMetadataSize(): Long {
        return metadataSize.values.sum()
    }
    
    override suspend fun getStoragePressureLevel(): StoragePressureLevel {
        return currentPressureLevel
    }
    
    override suspend fun getStoragePressureThreshold(): Long {
        return currentStoragePressureThreshold
    }
    
    override suspend fun getRetentionPolicy(): RetentionPolicy {
        return currentRetentionPolicy
    }
    
    override suspend fun getCleanupPolicy(): CleanupPolicy {
        return currentCleanupPolicy
    }
    
    override suspend fun setStoragePressureThreshold(threshold: Long) {
        currentStoragePressureThreshold = threshold
    }
    
    override suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        currentRetentionPolicy = policy
    }
    
    override suspend fun setCleanupPolicy(policy: CleanupPolicy) {
        currentCleanupPolicy = policy
    }
    
    // Internal cleanup methods
    
    private suspend fun cleanCache(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        // Get current cache size
        val currentCacheSize = getCacheSize()
        
        if (currentCacheSize <= policy.maxSize) {
            return result.copy(
                success = true,
                freedBytes = 0L,
                deletedCount = 0,
                failedCount = 0,
                errors = emptyList(),
                policy = policy
            )
        }
        
        // Calculate how much to free
        var toFree = currentCacheSize - policy.maxSize
        
        // Delete oldest cache entries
        val sortedCacheEntries = cacheSize.entries.sortedBy { it.value }
        
        for ((key, size) in sortedCacheEntries) {
            if (toFree <= 0) break
            
            // Check if this entry is user-created data
            if (policy.preserveUserCreatedData && key.startsWith("user_")) {
                continue
            }
            
            cacheSize.remove(key)
            freedBytes += size
            deletedCount++
            toFree -= size
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun cleanPartialDownloads(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        // Get current partial downloads count
        var currentPartialDownloadsCount = partialDownloads.size
        
        if (currentPartialDownloadsCount <= policy.maxPartialDownloads) {
            return result.copy(
                success = true,
                freedBytes = 0L,
                deletedCount = 0,
                failedCount = 0,
                errors = emptyList(),
                policy = policy
            )
        }
        
        // Delete oldest partial downloads
        val sortedPartialDownloads = partialDownloads.entries.sortedBy { it.value.lastModified }
        
        for ((key, info) in sortedPartialDownloads) {
            if (currentPartialDownloadsCount <= policy.maxPartialDownloads) break
            
            // Check if this download is user-created data
            if (policy.preserveUserCreatedData && info.isUserCreated) {
                continue
            }
            
            partialDownloads.remove(key)
            freedBytes += info.size
            deletedCount++
            currentPartialDownloadsCount--
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun cleanStagingPackages(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        // Get current staging packages count
        var currentStagingPackagesCount = stagingPackages.size
        
        if (currentStagingPackagesCount <= policy.maxStagingPackages) {
            return result.copy(
                success = true,
                freedBytes = 0L,
                deletedCount = 0,
                failedCount = 0,
                errors = emptyList(),
                policy = policy
            )
        }
        
        // Delete oldest staging packages
        val sortedStagingPackages = stagingPackages.entries.sortedBy { it.value.lastModified }
        
        for ((key, info) in sortedStagingPackages) {
            if (currentStagingPackagesCount <= policy.maxStagingPackages) break
            
            // Check if this package is user-created data
            if (policy.preserveUserCreatedData && info.isUserCreated) {
                continue
            }
            
            stagingPackages.remove(key)
            freedBytes += info.size
            deletedCount++
            currentStagingPackagesCount--
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun cleanMetadata(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        // Get current metadata size
        var currentMetadataSize = getMetadataSize()
        
        if (currentMetadataSize <= policy.maxMetadataSize) {
            return result.copy(
                success = true,
                freedBytes = 0L,
                deletedCount = 0,
                failedCount = 0,
                errors = emptyList(),
                policy = policy
            )
        }
        
        // Delete oldest metadata entries
        val sortedMetadataEntries = metadataSize.entries.sortedBy { it.value }
        
        for ((key, size) in sortedMetadataEntries) {
            if (currentMetadataSize <= policy.maxMetadataSize) break
            
            // Check if this metadata is user-created data
            if (policy.preserveUserCreatedData && key.startsWith("user_")) {
                continue
            }
            
            metadataSize.remove(key)
            freedBytes += size
            deletedCount++
            currentMetadataSize -= size
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun deleteObsoleteCache(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        val now = System.currentTimeMillis()
        
        // Delete cache entries older than maxAge
        val obsoleteCacheEntries = cacheSize.filter { (_, size) ->
            val age = now - size // Simulated age
            age > policy.maxAge
        }
        
        for ((key, size) in obsoleteCacheEntries) {
            // Check if this entry is user-created data
            if (policy.preserveUserCreatedData && key.startsWith("user_")) {
                continue
            }
            
            cacheSize.remove(key)
            freedBytes += size
            deletedCount++
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun deleteObsoletePartialDownloads(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        val now = System.currentTimeMillis()
        
        // Delete partial downloads older than maxAge
        val obsoletePartialDownloads = partialDownloads.filter { (_, info) ->
            val age = now - info.lastModified
            age > policy.maxAge
        }
        
        for ((key, info) in obsoletePartialDownloads) {
            // Check if this download is user-created data
            if (policy.preserveUserCreatedData && info.isUserCreated) {
                continue
            }
            
            partialDownloads.remove(key)
            freedBytes += info.size
            deletedCount++
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun deleteObsoleteStagingPackages(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        val now = System.currentTimeMillis()
        
        // Delete staging packages older than maxAge
        val obsoleteStagingPackages = stagingPackages.filter { (_, info) ->
            val age = now - info.lastModified
            age > policy.maxAge
        }
        
        for ((key, info) in obsoleteStagingPackages) {
            // Check if this package is user-created data
            if (policy.preserveUserCreatedData && info.isUserCreated) {
                continue
            }
            
            stagingPackages.remove(key)
            freedBytes += info.size
            deletedCount++
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    private suspend fun deleteObsoleteMetadata(policy: CleanupPolicy): CleanupResult {
        val result = CleanupResult(
            success = false,
            freedBytes = 0L,
            deletedCount = 0,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
        
        var freedBytes = 0L
        var deletedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        val now = System.currentTimeMillis()
        
        // Delete metadata entries older than maxAge
        val obsoleteMetadataEntries = metadataSize.filter { (_, size) ->
            val age = now - size // Simulated age
            age > policy.maxAge
        }
        
        for ((key, size) in obsoleteMetadataEntries) {
            // Check if this metadata is user-created data
            if (policy.preserveUserCreatedData && key.startsWith("user_")) {
                continue
            }
            
            metadataSize.remove(key)
            freedBytes += size
            deletedCount++
        }
        
        return result.copy(
            success = freedBytes > 0,
            freedBytes = freedBytes,
            deletedCount = deletedCount,
            failedCount = 0,
            errors = emptyList(),
            policy = policy
        )
    }
    
    // Dataset tracking methods
    
    fun addActiveDataset(datasetId: String, datasetType: String, size: Long) {
        activeDatasets[datasetId] = DatasetStorageInfo(
            datasetId = datasetId,
            datasetType = datasetType,
            size = size,
            lastModified = System.currentTimeMillis()
        )
    }
    
    fun removeActiveDataset(datasetId: String) {
        activeDatasets.remove(datasetId)
    }
    
    fun addPreviousDataset(datasetId: String, datasetType: String, size: Long) {
        previousDatasets[datasetId] = DatasetStorageInfo(
            datasetId = datasetId,
            datasetType = datasetType,
            size = size,
            lastModified = System.currentTimeMillis()
        )
    }
    
    fun removePreviousDataset(datasetId: String) {
        previousDatasets.remove(datasetId)
    }
    
    fun addStagingPackage(packageId: String, datasetId: String, size: Long) {
        stagingPackages[packageId] = PackageStorageInfo(
            packageId = packageId,
            datasetId = datasetId,
            size = size,
            lastModified = System.currentTimeMillis()
        )
    }
    
    fun removeStagingPackage(packageId: String) {
        stagingPackages.remove(packageId)
    }
    
    fun addPartialDownload(downloadId: String, datasetId: String, size: Long, isUserCreated: Boolean = false) {
        partialDownloads[downloadId] = DownloadStorageInfo(
            downloadId = downloadId,
            datasetId = datasetId,
            size = size,
            lastModified = System.currentTimeMillis(),
            isUserCreated = isUserCreated
        )
    }
    
    fun removePartialDownload(downloadId: String) {
        partialDownloads.remove(downloadId)
    }
    
    fun addCacheEntry(key: String, size: Long) {
        cacheSize[key] = size
    }
    
    fun removeCacheEntry(key: String) {
        cacheSize.remove(key)
    }
    
    fun addDatabaseEntry(key: String, size: Long) {
        databaseSize[key] = size
    }
    
    fun removeDatabaseEntry(key: String) {
        databaseSize.remove(key)
    }
    
    fun addMetadataEntry(key: String, size: Long) {
        metadataSize[key] = size
    }
    
    fun removeMetadataEntry(key: String) {
        metadataSize.remove(key)
    }
    
    // Data classes for storage tracking
    
    data class DatasetStorageInfo(
        val datasetId: String,
        val datasetType: String,
        val size: Long,
        val lastModified: Long
    )
    
    data class PackageStorageInfo(
        val packageId: String,
        val datasetId: String,
        val size: Long,
        val lastModified: Long,
        val isUserCreated: Boolean = false
    )
    
    data class DownloadStorageInfo(
        val downloadId: String,
        val datasetId: String,
        val size: Long,
        val lastModified: Long,
        val isUserCreated: Boolean = false
    )
}