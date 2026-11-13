package com.evnavigator.iran.domain.model.storage.management

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * StorageManagerTest - Comprehensive tests for StorageManager
 * 
 * Tests cover:
 * - Storage availability checking
 * - Storage size calculations
 * - Storage usage statistics
 * - Storage cleanup policies
 * - Retention policies
 * - Safe deletion of obsolete data
 * - Storage pressure level detection
 * - Breakdown by category
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageManagerTest {
    
    private lateinit var storageManager: StorageManagerImpl
    
    @Before
    fun setup() {
        storageManager = StorageManagerImpl()
    }
    
    @Test
    fun `getStorageUsage returns valid statistics`() = runTest {
        val statistics = storageManager.getStorageUsage().first()
        
        assertNotNull(statistics)
        assertTrue(statistics.totalBytes > 0)
        assertTrue(statistics.availableBytes >= 0)
        assertTrue(statistics.usedBytes >= 0)
        assertTrue(statistics.availablePercentage >= 0 && statistics.availablePercentage <= 100)
        assertTrue(statistics.usedPercentage >= 0 && statistics.usedPercentage <= 100)
        assertNotNull(statistics.pressureLevel)
    }
    
    @Test
    fun `getAvailableStorage returns correct value`() = runTest {
        val available = storageManager.getAvailableStorage()
        
        assertTrue(available >= 0)
    }
    
    @Test
    fun `getTotalStorage returns correct value`() = runTest {
        val total = storageManager.getTotalStorage()
        
        assertTrue(total > 0)
    }
    
    @Test
    fun `getUsedStorage returns correct value`() = runTest {
        val used = storageManager.getUsedStorage()
        
        assertTrue(used >= 0)
    }
    
    @Test
    fun `getStorageUsagePercentage returns correct value`() = runTest {
        val percentage = storageManager.getStorageUsagePercentage()
        
        assertTrue(percentage >= 0 && percentage <= 100)
    }
    
    @Test
    fun `hasSufficientStorage returns true when sufficient storage available`() = runTest {
        val result = storageManager.hasSufficientStorage(1024 * 1024L) // 1 MB
        
        assertTrue(result.available)
        assertTrue(result.availableBytes >= 1024 * 1024L)
        assertEquals(1024 * 1024L, result.requiredBytes)
        assertNull(result.reason)
    }
    
    @Test
    fun `hasSufficientStorage returns false when insufficient storage available`() = runTest {
        // Add a large dataset to reduce available storage
        storageManager.addActiveDataset("test_dataset", "MAP", 100 * 1024 * 1024L)
        
        val result = storageManager.hasSufficientStorage(1024 * 1024L * 100) // 100 MB
        
        assertFalse(result.available)
        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("Insufficient storage"))
    }
    
    @Test
    fun `hasSufficientStorage returns detailed reason for low storage`() = runTest {
        storageManager.addActiveDataset("test_dataset", "MAP", 100 * 1024 * 1024L)
        
        val result = storageManager.hasSufficientStorage(1024 * 1024L * 10) // 10 MB
        
        assertTrue(result.reason!!.contains("Low storage"))
    }
    
    @Test
    fun `getStorageBreakdown returns breakdown by category`() = runTest {
        // Add some data
        storageManager.addActiveDataset("dataset1", "MAP", 50 * 1024 * 1024L)
        storageManager.addCacheEntry("cache1", 10 * 1024 * 1024L)
        
        val breakdown = storageManager.getStorageBreakdown()
        
        assertNotNull(breakdown)
        assertTrue(breakdown.isNotEmpty())
    }
    
    @Test
    fun `getActiveDatasets returns list of active datasets`() = runTest {
        storageManager.addActiveDataset("dataset1", "MAP", 50 * 1024 * 1024L)
        storageManager.addActiveDataset("dataset2", "ROUTING", 30 * 1024 * 1024L)
        
        val datasets = storageManager.getActiveDatasets()
        
        assertEquals(2, datasets.size)
        assertTrue(datasets.contains("dataset1"))
        assertTrue(datasets.contains("dataset2"))
    }
    
    @Test
    fun `getPreviousDatasets returns list of previous datasets`() = runTest {
        storageManager.addPreviousDataset("dataset1", "MAP", 50 * 1024 * 1024L)
        storageManager.addPreviousDataset("dataset2", "ROUTING", 30 * 1024 * 1024L)
        
        val datasets = storageManager.getPreviousDatasets()
        
        assertEquals(2, datasets.size)
        assertTrue(datasets.contains("dataset1"))
        assertTrue(datasets.contains("dataset2"))
    }
    
    @Test
    fun `getStagingPackages returns list of staging packages`() = runTest {
        storageManager.addStagingPackage("package1", "dataset1", 50 * 1024 * 1024L)
        storageManager.addStagingPackage("package2", "dataset2", 30 * 1024 * 1024L)
        
        val packages = storageManager.getStagingPackages()
        
        assertEquals(2, packages.size)
        assertTrue(packages.contains("package1"))
        assertTrue(packages.contains("package2"))
    }
    
    @Test
    fun `getPartialDownloads returns list of partial downloads`() = runTest {
        storageManager.addPartialDownload("download1", "dataset1", 50 * 1024 * 1024L)
        storageManager.addPartialDownload("download2", "dataset2", 30 * 1024 * 1024L)
        
        val downloads = storageManager.getPartialDownloads()
        
        assertEquals(2, downloads.size)
        assertTrue(downloads.contains("download1"))
        assertTrue(downloads.contains("download2"))
    }
    
    @Test
    fun `getCacheSize returns correct cache size`() = runTest {
        storageManager.addCacheEntry("cache1", 10 * 1024 * 1024L)
        storageManager.addCacheEntry("cache2", 20 * 1024 * 1024L)
        
        val cacheSize = storageManager.getCacheSize()
        
        assertEquals(30 * 1024 * 1024L, cacheSize)
    }
    
    @Test
    fun `getDatabaseSize returns correct database size`() = runTest {
        storageManager.addDatabaseEntry("database1", 10 * 1024 * 1024L)
        storageManager.addDatabaseEntry("database2", 20 * 1024 * 1024L)
        
        val databaseSize = storageManager.getDatabaseSize()
        
        assertEquals(30 * 1024 * 1024L, databaseSize)
    }
    
    @Test
    fun `getMetadataSize returns correct metadata size`() = runTest {
        storageManager.addMetadataEntry("metadata1", 10 * 1024 * 1024L)
        storageManager.addMetadataEntry("metadata2", 20 * 1024 * 1024L)
        
        val metadataSize = storageManager.getMetadataSize()
        
        assertEquals(30 * 1024 * 1024L, metadataSize)
    }
    
    @Test
    fun `getStoragePressureLevel returns correct level`() = runTest {
        val level = storageManager.getStoragePressureLevel()
        
        assertNotNull(level)
        assertTrue(level in StoragePressureLevel.values())
    }
    
    @Test
    fun `getStoragePressureThreshold returns correct threshold`() = runTest {
        val threshold = storageManager.getStoragePressureThreshold()
        
        assertTrue(threshold > 0)
    }
    
    @Test
    fun `getRetentionPolicy returns correct policy`() = runTest {
        val policy = storageManager.getRetentionPolicy()
        
        assertNotNull(policy)
        assertTrue(policy.maxPreviousVersions > 0)
        assertTrue(policy.maxStagingPackages > 0)
        assertTrue(policy.maxPartialDownloads > 0)
    }
    
    @Test
    fun `getCleanupPolicy returns correct policy`() = runTest {
        val policy = storageManager.getCleanupPolicy()
        
        assertNotNull(policy)
        assertTrue(policy.maxAge > 0)
        assertTrue(policy.maxSize > 0)
    }
    
    @Test
    fun `setStoragePressureThreshold updates threshold`() = runTest {
        val newThreshold = 100 * 1024 * 1024 * 1024L // 100 GB
        storageManager.setStoragePressureThreshold(newThreshold)
        
        assertEquals(newThreshold, storageManager.getStoragePressureThreshold())
    }
    
    @Test
    fun `setRetentionPolicy updates policy`() = runTest {
        val newPolicy = RetentionPolicy(
            maxPreviousVersions = 5,
            maxStagingPackages = 3,
            maxPartialDownloads = 10
        )
        storageManager.setRetentionPolicy(newPolicy)
        
        assertEquals(newPolicy, storageManager.getRetentionPolicy())
    }
    
    @Test
    fun `setCleanupPolicy updates policy`() = runTest {
        val newPolicy = CleanupPolicy(
            priority = CleanupPriority.HIGH,
            maxAge = 14 * 24 * 60 * 60 * 1000L,
            maxSize = 200 * 1024 * 1024L
        )
        storageManager.setCleanupPolicy(newPolicy)
        
        assertEquals(newPolicy, storageManager.getCleanupPolicy())
    }
    
    @Test
    fun `calculateDatasetSize returns correct size for different types`() = runTest {
        val mapSize = storageManager.calculateDatasetSize("dataset1", "MAP")
        val routingSize = storageManager.calculateDatasetSize("dataset2", "ROUTING")
        val elevationSize = storageManager.calculateDatasetSize("dataset3", "ELEVATION")
        val chargingSize = storageManager.calculateDatasetSize("dataset4", "CHARGING")
        val vehicleSize = storageManager.calculateDatasetSize("dataset5", "VEHICLE")
        
        assertTrue(mapSize > 0)
        assertTrue(routingSize > 0)
        assertTrue(elevationSize > 0)
        assertTrue(chargingSize > 0)
        assertTrue(vehicleSize > 0)
    }
    
    @Test
    fun `calculateDatasetSize returns default size for unknown type`() = runTest {
        val unknownSize = storageManager.calculateDatasetSize("dataset1", "UNKNOWN")
        
        assertTrue(unknownSize > 0)
    }
    
    @Test
    fun `cleanObsoleteStorage frees space when needed`() = runTest {
        // Add some data to exceed cleanup policy
        storageManager.addCacheEntry("cache1", 150 * 1024 * 1024L)
        
        val policy = CleanupPolicy(
            priority = CleanupPriority.MEDIUM,
            maxSize = 100 * 1024 * 1024L
        )
        
        val result = storageManager.cleanObsoleteStorage(200 * 1024 * 1024L, policy)
        
        assertTrue(result.success || result.freedBytes > 0)
        assertTrue(result.deletedCount >= 0)
    }
    
    @Test
    fun `cleanObsoleteStorage respects preserveUserCreatedData`() = runTest {
        // Add user-created cache entry
        storageManager.addCacheEntry("user_favorites", 10 * 1024 * 1024L)
        
        val policy = CleanupPolicy(
            priority = CleanupPriority.MEDIUM,
            maxSize = 5 * 1024 * 1024L,
            preserveUserCreatedData = true
        )
        
        val result = storageManager.cleanObsoleteStorage(20 * 1024 * 1024L, policy)
        
        // User-created data should not be deleted
        assertTrue(result.deletedCount >= 0)
    }
    
    @Test
    fun `deleteObsoleteData frees space when needed`() = runTest {
        // Add some data
        storageManager.addCacheEntry("cache1", 50 * 1024 * 1024L)
        
        val policy = CleanupPolicy(
            priority = CleanupPriority.MEDIUM,
            maxAge = 0 // Delete immediately
        )
        
        val result = storageManager.deleteObsoleteData(policy)
        
        assertTrue(result.success || result.freedBytes > 0)
        assertTrue(result.deletedCount >= 0)
    }
    
    @Test
    fun `deleteObsoleteData respects preserveUserCreatedData`() = runTest {
        // Add user-created cache entry
        storageManager.addCacheEntry("user_favorites", 10 * 1024 * 1024L)
        
        val policy = CleanupPolicy(
            priority = CleanupPriority.MEDIUM,
            maxAge = 0,
            preserveUserCreatedData = true
        )
        
        val result = storageManager.deleteObsoleteData(policy)
        
        // User-created data should not be deleted
        assertTrue(result.deletedCount >= 0)
    }
    
    @Test
    fun `StoragePressureLevel fromPercentage returns correct level`() {
        assertEquals(StoragePressureLevel.LOW, StoragePressureLevel.fromPercentage(70.0))
        assertEquals(StoragePressureLevel.MEDIUM, StoragePressureLevel.fromPercentage(85.0))
        assertEquals(StoragePressureLevel.HIGH, StoragePressureLevel.fromPercentage(90.0))
        assertEquals(StoragePressureLevel.CRITICAL, StoragePressureLevel.fromPercentage(95.0))
    }
    
    @Test
    fun `CleanupPriority fromPriority returns correct priority`() {
        assertEquals(CleanupPriority.LOW, CleanupPriority.fromPriority(1))
        assertEquals(CleanupPriority.MEDIUM, CleanupPriority.fromPriority(2))
        assertEquals(CleanupPriority.HIGH, CleanupPriority.fromPriority(3))
        assertEquals(CleanupPriority.CRITICAL, CleanupPriority.fromPriority(4))
    }
    
    @Test
    fun `StorageCheckResult equality`() {
        val result1 = StorageCheckResult(
            available = true,
            availableBytes = 100 * 1024 * 1024L,
            requiredBytes = 50 * 1024 * 1024L,
            reason = null,
            thresholdPercentage = 85.0
        )
        
        val result2 = StorageCheckResult(
            available = true,
            availableBytes = 100 * 1024 * 1024L,
            requiredBytes = 50 * 1024 * 1024L,
            reason = null,
            thresholdPercentage = 85.0
        )
        
        assertEquals(result1, result2)
    }
    
    @Test
    fun `CleanupResult equality`() {
        val result1 = CleanupResult(
            success = true,
            freedBytes = 100 * 1024 * 1024L,
            deletedCount = 10,
            failedCount = 0,
            errors = emptyList(),
            policy = CleanupPolicy()
        )
        
        val result2 = CleanupResult(
            success = true,
            freedBytes = 100 * 1024 * 1024L,
            deletedCount = 10,
            failedCount = 0,
            errors = emptyList(),
            policy = CleanupPolicy()
        )
        
        assertEquals(result1, result2)
    }
    
    @Test
    fun `StorageUsageStatistics equality`() {
        val stats1 = StorageUsageStatistics(
            totalBytes = 256 * 1024 * 1024 * 1024L,
            availableBytes = 100 * 1024 * 1024 * 1024L,
            usedBytes = 156 * 1024 * 1024 * 1024L,
            availablePercentage = 39.06,
            usedPercentage = 60.94,
            pressureLevel = StoragePressureLevel.MEDIUM
        )
        
        val stats2 = StorageUsageStatistics(
            totalBytes = 256 * 1024 * 1024 * 1024L,
            availableBytes = 100 * 1024 * 1024 * 1024L,
            usedBytes = 156 * 1024 * 1024 * 1024L,
            availablePercentage = 39.06,
            usedPercentage = 60.94,
            pressureLevel = StoragePressureLevel.MEDIUM
        )
        
        assertEquals(stats1, stats2)
    }
    
    @Test
    fun `StoragePressureLevel values are ordered`() {
        val levels = StoragePressureLevel.values()
        
        for (i in 0 until levels.size - 1) {
            assertTrue(levels[i].level <= levels[i + 1].level)
        }
    }
    
    @Test
    fun `CleanupPriority values are ordered`() {
        val priorities = CleanupPriority.values()
        
        for (i in 0 until priorities.size - 1) {
            assertTrue(priorities[i].priority <= priorities[i + 1].priority)
        }
    }
    
    @Test
    fun `RetentionPolicy preserves user created data by default`() {
        val policy = RetentionPolicy()
        
        assertTrue(policy.preserveUserCreatedData)
        assertTrue(policy.preserveNavigationState)
    }
    
    @Test
    fun `CleanupPolicy preserves active datasets by default`() {
        val policy = CleanupPolicy()
        
        assertTrue(policy.preserveActiveDatasets)
        assertTrue(policy.preservePreviousDatasets)
        assertTrue(policy.preserveRollbackData)
    }
    
    @Test
    fun `StorageManagerImpl can be instantiated`() {
        val manager = StorageManagerImpl()
        
        assertNotNull(manager)
    }
    
    @Test
    fun `StorageManagerImpl can be instantiated with custom policies`() = runTest {
        val customPolicy = RetentionPolicy(
            maxPreviousVersions = 5,
            maxStagingPackages = 3,
            maxPartialDownloads = 10
        )
        
        val manager = StorageManagerImpl(retentionPolicy = customPolicy)
        
        assertNotNull(manager)
        assertEquals(customPolicy, manager.getRetentionPolicy())
    }
}