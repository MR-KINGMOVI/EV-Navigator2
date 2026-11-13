package com.evnavigator.iran.domain.model.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Tests for StorageManager.
 * 
 * Tests cover:
 * - Storage availability checking
 * - Storage size calculations
 * - Storage usage statistics
 * - Storage cleanup policies
 */
@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StorageManagerTest {

    private lateinit var storageManager: StorageManager
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        storageManager = MockStorageManager(
            availableBytes = 1024L * 1024 * 1024 * 2, // 2 GB
            totalBytes = 1024L * 1024 * 1024 * 4, // 4 GB
            usedBytes = 1024L * 1024 * 1024 * 1 // 1 GB
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getAvailableStorage returns available bytes`() = runTest {
        // Act
        val available = storageManager.getAvailableStorage()
        
        // Assert
        assertEquals(1024L * 1024 * 1024 * 2, available)
    }

    @Test
    fun `getTotalStorage returns total bytes`() = runTest {
        // Act
        val total = storageManager.getTotalStorage()
        
        // Assert
        assertEquals(1024L * 1024 * 1024 * 4, total)
    }

    @Test
    fun `getUsedStorage returns used bytes`() = runTest {
        // Act
        val used = storageManager.getUsedStorage()
        
        // Assert
        assertEquals(1024L * 1024 * 1024 * 1, used)
    }

    @Test
    fun `hasSufficientStorage returns true when sufficient storage is available`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(1024L * 1024 * 1024 * 1) // 1 GB
        
        // Assert
        assertTrue(hasSufficient)
    }

    @Test
    fun `hasSufficientStorage returns false when insufficient storage is available`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(1024L * 1024 * 1024 * 3) // 3 GB
        
        // Assert
        assertFalse(hasSufficient)
    }

    @Test
    fun `hasSufficientStorage returns true for exact size match`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(1024L * 1024 * 1024 * 2) // 2 GB
        
        // Assert
        assertTrue(hasSufficient)
    }

    @Test
    fun `getStorageUsage returns correct statistics`() = runTest {
        // Act
        val usage = storageManager.getStorageUsage()
        
        // Assert
        assertEquals(1024L * 1024 * 1024 * 4, usage.totalBytes)
        assertEquals(1024L * 1024 * 1024 * 2, usage.availableBytes)
        assertEquals(1024L * 1024 * 1024 * 1, usage.usedBytes)
        assertEquals(50.0, usage.availablePercentage, 0.01)
        assertEquals(25.0, usage.usedPercentage, 0.01)
    }

    @Test
    fun `getStorageUsage calculates percentages correctly`() = runTest {
        // Act
        val usage = storageManager.getStorageUsage()
        
        // Assert
        assertEquals(50.0, usage.availablePercentage, 0.01)
        assertEquals(25.0, usage.usedPercentage, 0.01)
    }

    @Test
    fun `cleanObsoleteStorage returns cleaned size`() = runTest {
        // Act
        val cleaned = storageManager.cleanObsoleteStorage(1024L * 1024 * 1024 * 500) // 500 MB
        
        // Assert
        assertTrue(cleaned >= 0)
    }

    @Test
    fun `cleanObsoleteStorage with zero size returns zero`() = runTest {
        // Act
        val cleaned = storageManager.cleanObsoleteStorage(0)
        
        // Assert
        assertEquals(0, cleaned)
    }

    @Test
    fun `storageUsage is consistent`() = runTest {
        // Act
        val usage1 = storageManager.getStorageUsage()
        val usage2 = storageManager.getStorageUsage()
        
        // Assert
        assertEquals(usage1.totalBytes, usage2.totalBytes)
        assertEquals(usage1.availableBytes, usage2.availableBytes)
        assertEquals(usage1.usedBytes, usage2.usedBytes)
        assertEquals(usage1.availablePercentage, usage2.availablePercentage, 0.01)
        assertEquals(usage1.usedPercentage, usage2.usedPercentage, 0.01)
    }

    @Test
    fun `storageUsage reflects changes`() = runTest {
        // Arrange
        val customStorageManager = MockStorageManager(
            availableBytes = 1024L * 1024 * 1024 * 1, // 1 GB
            totalBytes = 1024L * 1024 * 1024 * 2, // 2 GB
            usedBytes = 1024L * 1024 * 1024 * 1 // 1 GB
        )
        
        // Act
        val usage = customStorageManager.getStorageUsage()
        
        // Assert
        assertEquals(50.0, usage.availablePercentage, 0.01)
        assertEquals(50.0, usage.usedPercentage, 0.01)
    }

    @Test
    fun `hasSufficientStorage with zero required size returns true`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(0)
        
        // Assert
        assertTrue(hasSufficient)
    }

    @Test
    fun `hasSufficientStorage with negative required size returns true`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(-1024L)
        
        // Assert
        assertTrue(hasSufficient)
    }

    @Test
    fun `storageUsage maintains consistency after cleanup`() = runTest {
        // Arrange
        val usageBefore = storageManager.getStorageUsage()
        
        // Act
        val cleaned = storageManager.cleanObsoleteStorage(1024L * 1024 * 1024 * 200) // 200 MB
        
        // Assert
        val usageAfter = storageManager.getStorageUsage()
        assertTrue(usageAfter.availableBytes >= usageBefore.availableBytes)
    }

    @Test
    fun `storageUsage maintains consistency after multiple cleanups`() = runTest {
        // Arrange
        val usage1 = storageManager.getStorageUsage()
        
        // Act
        storageManager.cleanObsoleteStorage(1024L * 1024 * 1024 * 100)
        storageManager.cleanObsoleteStorage(1024L * 1024 * 1024 * 100)
        
        val usage2 = storageManager.getStorageUsage()
        
        // Assert
        assertTrue(usage2.availableBytes >= usage1.availableBytes)
    }

    @Test
    fun `storageUsage maintains consistency after multiple checks`() = runTest {
        // Arrange
        val usage1 = storageManager.getStorageUsage()
        
        // Act
        storageManager.getAvailableStorage()
        storageManager.getTotalStorage()
        storageManager.getUsedStorage()
        storageManager.hasSufficientStorage(1024L * 1024 * 1024 * 1)
        
        val usage2 = storageManager.getStorageUsage()
        
        // Assert
        assertEquals(usage1.totalBytes, usage2.totalBytes)
        assertEquals(usage1.availableBytes, usage2.availableBytes)
        assertEquals(usage1.usedBytes, usage2.usedBytes)
    }

    @Test
    fun `storageUsage maintains consistency after mixed operations`() = runTest {
        // Arrange
        val usage1 = storageManager.getStorageUsage()
        
        // Act
        storageManager.getAvailableStorage()
        storageManager.cleanObsoleteStorage(1024L * 1024 * 1024 * 100)
        storageManager.hasSufficientStorage(1024L * 1024 * 1024 * 1)
        
        val usage2 = storageManager.getStorageUsage()
        
        // Assert
        assertEquals(usage1.totalBytes, usage2.totalBytes)
        assertEquals(usage1.availableBytes, usage2.availableBytes)
        assertEquals(usage1.usedBytes, usage2.usedBytes)
    }

    @Test
    fun `hasSufficientStorage with very large required size returns false`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(1024L * 1024 * 1024 * 1024) // 1 TB
        
        // Assert
        assertFalse(hasSufficient)
    }

    @Test
    fun `hasSufficientStorage with very small required size returns true`() = runTest {
        // Act
        val hasSufficient = storageManager.hasSufficientStorage(1) // 1 byte
        
        // Assert
        assertTrue(hasSufficient)
    }

    @Test
    fun `storageUsage with zero total storage`() = runTest {
        // Arrange
        val zeroStorageManager = MockStorageManager(
            availableBytes = 0,
            totalBytes = 0,
            usedBytes = 0
        )
        
        // Act
        val usage = zeroStorageManager.getStorageUsage()
        
        // Assert
        assertEquals(0, usage.totalBytes)
        assertEquals(0, usage.availableBytes)
        assertEquals(0, usage.usedBytes)
        assertEquals(0.0, usage.availablePercentage, 0.01)
        assertEquals(0.0, usage.usedPercentage, 0.01)
    }

    @Test
    fun `storageUsage with 100% used storage`() = runTest {
        // Arrange
        val fullStorageManager = MockStorageManager(
            availableBytes = 0,
            totalBytes = 1024L * 1024 * 1024 * 1,
            usedBytes = 1024L * 1024 * 1024 * 1
        )
        
        // Act
        val usage = fullStorageManager.getStorageUsage()
        
        // Assert
        assertEquals(0.0, usage.availablePercentage, 0.01)
        assertEquals(100.0, usage.usedPercentage, 0.01)
    }

    @Test
    fun `storageUsage with 100% available storage`() = runTest {
        // Arrange
        val emptyStorageManager = MockStorageManager(
            availableBytes = 1024L * 1024 * 1024 * 1,
            totalBytes = 1024L * 1024 * 1024 * 1,
            usedBytes = 0
        )
        
        // Act
        val usage = emptyStorageManager.getStorageUsage()
        
        // Assert
        assertEquals(100.0, usage.availablePercentage, 0.01)
        assertEquals(0.0, usage.usedPercentage, 0.01)
    }
}
