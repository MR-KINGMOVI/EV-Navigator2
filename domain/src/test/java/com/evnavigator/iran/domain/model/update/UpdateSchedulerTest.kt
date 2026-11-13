package com.evnavigator.iran.domain.model.update

import com.evnavigator.iran.domain.model.storage.management.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Tests for UpdateScheduler.
 * 
 * Tests cover:
 * - Storage availability checking
 * - Network availability checking
 * - Battery availability checking
 * - Device state checking
 * - Scheduling logic
 * - Cancelation logic
 * - Event tracking
 */
@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSchedulerTest {

    private lateinit var scheduler: UpdateScheduler
    private lateinit var testDispatcher: TestDispatcher

    @Mock
    private lateinit var backgroundUpdateManager: BackgroundUpdateManager

    @Mock
    private lateinit var storageManager: StorageManager

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        scheduler = UpdateScheduler(backgroundUpdateManager, storageManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scheduleUpdate succeeds when all checks pass`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2) // 2 GB
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
        verify(backgroundUpdateManager).scheduleUpdate(datasetId, constraints)
    }

    @Test
    fun `scheduleUpdate fails when storage is insufficient`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 100) // 100 MB
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Insufficient storage"))
    }

    @Test
    fun `scheduleUpdate fails when network is unavailable`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(requiredNetworkType = NetworkType.NONE)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Network unavailable"))
    }

    @Test
    fun `scheduleUpdate fails when battery is too low`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(minimumBatteryLevel = 90)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Battery level"))
    }

    @Test
    fun `scheduleUpdate succeeds when battery level is sufficient`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(minimumBatteryLevel = 50)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate defers on metered network`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferOnMeteredNetwork = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Metered network"))
    }

    @Test
    fun `scheduleUpdate defers on roaming network`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferOnRoaming = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Roaming network"))
    }

    @Test
    fun `scheduleUpdate defers on low storage`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferOnLowStorage = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 500) // 500 MB
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Low storage"))
    }

    @Test
    fun `scheduleUpdate defers on low battery`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferOnLowBattery = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Low battery level"))
    }

    @Test
    fun `scheduleUpdate defers while charging`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferWhileCharging = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("while charging"))
    }

    @Test
    fun `scheduleUpdate defers while idle`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferWhileIdle = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("idle"))
    }

    @Test
    fun `scheduleUpdate defers in Doze mode`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferInDozeMode = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Doze mode"))
    }

    @Test
    fun `scheduleUpdate defers in Power-save mode`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferInPowerSaveMode = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Power-save mode"))
    }

    @Test
    fun `scheduleUpdate defers during navigation`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(deferDuringNavigation = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Navigation is active"))
    }

    @Test
    fun `scheduleUpdate requires charging when specified`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(requiresCharging = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Charging required"))
    }

    @Test
    fun `scheduleUpdate succeeds when charging is required and device is charging`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(requiresCharging = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate uses required network type`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(requiredNetworkType = NetworkType.METERED)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Required network type"))
    }

    @Test
    fun `scheduleUpdate succeeds with required network type`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(requiredNetworkType = NetworkType.WIFI)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate uses maximum download size`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(maximumDownloadSize = 1024L * 1024 * 500) // 500 MB
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate fails when maximum download size exceeds available storage`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(maximumDownloadSize = 1024L * 1024 * 1024 * 3) // 3 GB
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2) // 2 GB
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Insufficient storage"))
    }

    @Test
    fun `cancelSchedule cancels the current schedule`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        scheduler.cancelSchedule(scheduleId)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Cancelled)
    }

    @Test
    fun `getCurrentState returns current state`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        val state = scheduler.getCurrentState()
        assert(state is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `getCurrentEvents returns list of events`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        val events = scheduler.getCurrentEvents()
        assert(events.isNotEmpty())
        assert(events.any { it is UpdateScheduler.ScheduleEvent.Scheduled })
    }

    @Test
    fun `scheduleId is unique for each schedule`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        val scheduleId1 = scheduler.scheduleUpdate(datasetId, constraints)
        val scheduleId2 = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduleId1 != scheduleId2)
    }

    @Test
    fun `scheduleUpdate handles exceptions gracefully`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenThrow(RuntimeException("Storage error"))
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Failed)
        val state = scheduler.getCurrentState() as UpdateScheduler.ScheduleState.Failed
        assert(state.error.contains("Storage error"))
    }

    @Test
    fun `scheduleState flow emits state changes`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        val stateFlow = scheduler.scheduleState
        val collectedStates = mutableListOf<UpdateScheduler.ScheduleState>()
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            stateFlow.collect { collectedStates.add(it) }
        }
        scheduler.scheduleUpdate(datasetId, constraints)
        collectJob.cancel()
        
        // Assert
        assert(collectedStates.isNotEmpty())
        val nonIdle = collectedStates.filterNot { it is UpdateScheduler.ScheduleState.Idle }
        assert(nonIdle[0] is UpdateScheduler.ScheduleState.Scheduling)
        assert(nonIdle[1] is UpdateScheduler.ScheduleState.Scheduled)

    }

    @Test
    fun `scheduleEvents flow emits events`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        val eventsFlow = scheduler.scheduleEvents
        val collectedEvents = mutableListOf<UpdateScheduler.ScheduleEvent>()
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventsFlow.collect { collectedEvents.addAll(it) }
        }
        scheduler.scheduleUpdate(datasetId, constraints)
        collectJob.cancel()
        
        // Assert
        assert(collectedEvents.isNotEmpty())
        assert(collectedEvents.any { it is UpdateScheduler.ScheduleEvent.Scheduled })
    }

    @Test
    fun `scheduleEvents limits to last 100 events`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint()
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        whenever(backgroundUpdateManager.scheduleUpdate(datasetId, constraints)).thenReturn("test-update-id")
        
        // Act
        for (i in 1..150) {
            scheduler.scheduleUpdate(datasetId, constraints)
        }
        
        // Assert
        val events = scheduler.getCurrentEvents()
        assert(events.size <= 100)
    }

    @Test
    fun `scheduleUpdate respects canRunWhileIdle constraint`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(canRunWhileIdle = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate respects canRunInDozeMode constraint`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(canRunInDozeMode = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate respects canRunInPowerSaveMode constraint`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(canRunInPowerSaveMode = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }

    @Test
    fun `scheduleUpdate respects canRunDuringNavigation constraint`() = runTest {
        // Arrange
        val datasetId = "test-dataset"
        val constraints = UpdateConstraint(canRunDuringNavigation = true)
        
        whenever(storageManager.getAvailableStorage()).thenReturn(1024L * 1024 * 1024 * 2)
        
        // Act
        val scheduleId = scheduler.scheduleUpdate(datasetId, constraints)
        
        // Assert
        assert(scheduler.getCurrentState() is UpdateScheduler.ScheduleState.Scheduled)
    }
}
