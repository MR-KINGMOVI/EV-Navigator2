package com.evnavigator.iran.domain.model.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import java.io.File

/**
 * Mock implementation of StorageManager for testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MockStorageManager(
    private val availableBytes: Long = 1024 * 1024 * 1024, // 1 GB
    private val totalBytes: Long = 1024 * 1024 * 1024 * 2, // 2 GB
    private val usedBytes: Long = 1024 * 1024 * 1024 // 1 GB
) : StorageManager {

    private val testDispatcher = StandardTestDispatcher()

    init {
        Dispatchers.setMain(testDispatcher)
    }

    override suspend fun getAvailableStorage(): Long {
        return availableBytes
    }

    override suspend fun getTotalStorage(): Long {
        return totalBytes
    }

    override suspend fun getUsedStorage(): Long {
        return usedBytes
    }

    override suspend fun hasSufficientStorage(requiredSize: Long): Boolean {
        return availableBytes >= requiredSize
    }

    override suspend fun getStorageUsage(): StorageUsage {
        return StorageUsage(
            totalBytes = totalBytes,
            availableBytes = availableBytes,
            usedBytes = usedBytes,
            availablePercentage = if (totalBytes > 0) (availableBytes.toDouble() / totalBytes) * 100 else 0.0,
            usedPercentage = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes) * 100 else 0.0
        )
    }

    override suspend fun cleanObsoleteStorage(maxSize: Long): Long {
        val freed = 0L
        return freed
    }

    override suspend fun getStorageInfo(path: String): StorageInfo {
        return StorageInfo(
            path = path,
            totalBytes = totalBytes,
            availableBytes = availableBytes,
            usedBytes = usedBytes,
            isExternal = false,
            isEmulated = true
        )
    }
}
