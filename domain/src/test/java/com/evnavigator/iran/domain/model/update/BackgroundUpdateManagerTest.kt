package com.evnavigator.iran.domain.model.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.evnavigator.iran.domain.model.download.DownloadState
import com.evnavigator.iran.domain.model.download.DownloadManager
import com.evnavigator.iran.domain.model.rollback.RollbackManager
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Comprehensive tests for BackgroundUpdateManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundUpdateManagerTest {

    private lateinit var manager: BackgroundUpdateManager
    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var updateCheckStrategy: UpdateCheckStrategy

    @Mock
    private lateinit var downloadManager: DownloadManager

    @Mock
    private lateinit var packageInstaller: PackageInstaller

    @Mock
    private lateinit var rollbackManager: RollbackManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        manager = BackgroundUpdateManager(
            updateCheckStrategy = updateCheckStrategy,
            downloadManager = downloadManager,
            packageInstaller = packageInstaller,
            rollbackManager = rollbackManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkForUpdates returns Idle state when check is in progress`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        // Act
        val result = manager.checkForUpdates(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Idle)
        assert(result is UpdateCheckResult)
    }

    @Test
    fun `checkForUpdates returns result from strategy when successful`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        val expectedResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(expectedResult)
        
        // Act
        val result = manager.checkForUpdates(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Idle)
        assert(result == expectedResult)
    }

    @Test
    fun `checkForUpdates returns failure result when strategy throws exception`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenThrow(RuntimeException("Network error"))
        
        // Act
        val result = manager.checkForUpdates(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Idle)
        assert(result is UpdateCheckResult)
        assert(!result.isUpdateAvailable)
        assert(result.availabilityReason == "Network error")
    }

    @Test
    fun `scheduleUpdate returns updateId when no update is available`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        val noUpdateResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = false,
            currentVersion = "1.0.0",
            availableVersion = null,
            priority = null,
            downloadSizeBytes = null,
            releaseNotes = null,
            publicationTimestamp = null,
            expirationTimestamp = null,
            isMandatory = false,
            constraints = null,
            canBeDeferred = true,
            availabilityReason = "No update available",
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = false,
            isDownloadable = false,
            isInstallable = false,
            isCompatible = false,
            isFresh = false,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "UNKNOWN",
            degradation = "NONE",
            coverage = "NONE",
            integrity = "UNKNOWN",
            installState = "NOT_INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = null,
            checksum = null,
            checksumAlgorithm = null,
            compression = null,
            schemaVersion = 0,
            packageVersion = "1.0.0",
            datasetVersion = "1.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(noUpdateResult)
        
        // Act
        val updateId = manager.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Idle)
        assert(updateId.isNotEmpty())
        
        val events = manager.updateEvents.first()
        assert(events.any { it is UpdateEvent.UpdateNotAvailable })
    }

    @Test
    fun `scheduleUpdate returns updateId when update is blocked`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        val blockedResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = "Storage insufficient",
            isStorageBlocked = true,
            isNetworkBlocked = false,
            isBlocked = true,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = false,
            isInstallable = false,
            isCompatible = false,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "STORAGE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(blockedResult)
        
        // Act
        val updateId = manager.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Idle)
        assert(updateId.isNotEmpty())
        
        val events = manager.updateEvents.first()
        assert(events.any { it is UpdateEvent.UpdateBlocked })
    }

    @Test
    fun `scheduleUpdate starts download when update is available and not blocked`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = true, error = null))
        
        // Act
        val updateId = manager.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Installed)
        
        val events = manager.updateEvents.first()
        assert(events.any { it is UpdateEvent.UpdateStarted })
        assert(events.any { it is UpdateEvent.UpdatePreparing })
        assert(events.any { it is UpdateEvent.UpdateCompleted })
    }

    @Test
    fun `scheduleUpdate handles download failure`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val failedState = DownloadState.Failed(
            datasetId = datasetId,
            version = "2.0.0",
            error = "Network timeout",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis()
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(failedState)
        
        // Act
        val updateId = manager.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Failed)
        
        val events = manager.updateEvents.first()
        assert(events.any { it is UpdateEvent.UpdateStarted })
        assert(events.any { it is UpdateEvent.UpdateFailed })
    }

    @Test
    fun `scheduleUpdate handles installation failure`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = false, error = "Installation failed"))
        
        // Act
        val updateId = manager.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Failed)
        
        val events = manager.updateEvents.first()
        assert(events.any { it is UpdateEvent.UpdateStarted })
        assert(events.any { it is UpdateEvent.UpdatePreparing })
        assert(events.any { it is UpdateEvent.UpdateFailed })
    }

    @Test
    fun `cancelUpdate cancels all downloads and clears state`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = true, error = null))
        
        // Start update
        manager.scheduleUpdate(datasetId, constraints)
        
        // Act
        manager.cancelUpdate("test-update-id")
        
        // Assert
        assert(manager.getCurrentState() is BackgroundUpdateManager.UpdateState.Idle)
        assert(manager.getCurrentProgress() == null)
        
        val events = manager.updateEvents.first()
        assert(events.any { it is UpdateEvent.UpdateCancelled })
        
        verify(downloadManager).cancelAllDownloads()
    }

    @Test
    fun `getCurrentState returns current update state`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = true, error = null))
        
        // Start update
        manager.scheduleUpdate(datasetId, constraints)
        
        // Act
        val state = manager.getCurrentState()
        
        // Assert
        assert(state is BackgroundUpdateManager.UpdateState.Installed)
    }

    @Test
    fun `getCurrentProgress returns current update progress`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = true, error = null))
        
        // Start update
        manager.scheduleUpdate(datasetId, constraints)
        
        // Act
        val progress = manager.getCurrentProgress()
        
        // Assert
        assert(progress != null)
        assert(progress!!.datasetId == datasetId)
        assert(progress.version == "2.0.0")
    }

    @Test
    fun `updateEvents flow emits events`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = true, error = null))
        
        // Act
        manager.scheduleUpdate(datasetId, constraints)
        
        // Assert
        val events = manager.updateEvents.first()
        assert(events.isNotEmpty())
        assert(events.any { it is UpdateEvent.UpdateStarted })
        assert(events.any { it is UpdateEvent.UpdatePreparing })
        assert(events.any { it is UpdateEvent.UpdateCompleted })
    }

    @Test
    fun `updateEvents flow limits to last 100 events`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        val availableResult = UpdateCheckResult(
            datasetId = datasetId,
            isUpdateAvailable = true,
            currentVersion = "1.0.0",
            availableVersion = "2.0.0",
            priority = UpdatePriority.HIGH,
            downloadSizeBytes = 1024 * 1024,
            releaseNotes = "Test update",
            publicationTimestamp = System.currentTimeMillis(),
            expirationTimestamp = null,
            isMandatory = false,
            constraints = constraints,
            canBeDeferred = true,
            availabilityReason = null,
            blockReason = null,
            isStorageBlocked = false,
            isNetworkBlocked = false,
            isBlocked = false,
            isDeferred = false,
            isAvailable = true,
            isDownloadable = true,
            isInstallable = true,
            isCompatible = true,
            isFresh = true,
            isStale = false,
            isExpired = false,
            isCorrupted = false,
            isPartial = false,
            isOffline = false,
            isLive = false,
            isCached = false,
            isUnknown = false,
            isInvalid = false,
            freshness = "FRESH",
            degradation = "NONE",
            coverage = "FULL",
            integrity = "VALID",
            installState = "INSTALLED",
            source = "REMOTE",
            freshnessTimestamp = System.currentTimeMillis(),
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            compression = "GZIP",
            schemaVersion = 1,
            packageVersion = "1.0.0",
            datasetVersion = "2.0.0",
            dependencies = emptyList(),
            metadata = emptyMap()
        )
        
        val downloadId = "download-123"
        val completedState = DownloadState.Completed(
            datasetId = datasetId,
            version = "2.0.0",
            sizeBytes = 1024 * 1024,
            checksum = "abc123",
            checksumAlgorithm = "SHA-256",
            downloadId = downloadId,
            downloadUrl = "https://example.com/package.zip",
            downloadStartTime = System.currentTimeMillis(),
            downloadEndTime = System.currentTimeMillis(),
            downloadDurationMs = 1000,
            downloadSpeedBps = 1024 * 1024,
            resumeToken = null
        )
        
        `when`(updateCheckStrategy.checkForUpdates(datasetId, constraints))
            .thenReturn(availableResult)
        
        `when`(downloadManager.registerDownload(anyString(), anyString(), nullable(String::class.java)))
            .thenReturn(downloadId)
        
        `when`(downloadManager.waitForCompletion(downloadId))
            .thenReturn(completedState)
        
        `when`(packageInstaller.installPackage(datasetId, "2.0.0", downloadId))
            .thenReturn(PackageInstaller.InstallResult(success = true, error = null))
        
        // Start multiple updates to generate many events
        repeat(150) {
            manager.scheduleUpdate(datasetId, constraints)
            manager.cancelUpdate("cancel-$it")
        }
        
        // Act
        val events = manager.updateEvents.first()
        
        // Assert
        assert(events.size <= 100)
    }
}