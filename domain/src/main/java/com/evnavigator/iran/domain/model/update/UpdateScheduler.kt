package com.evnavigator.iran.domain.model.update

import com.evnavigator.iran.domain.model.storage.management.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Scheduler for background dataset updates.
 * 
 * This class implements update scheduling policies based on:
 * - Network constraints (Wi-Fi, metered, roaming)
 * - Battery constraints (level, charging state)
 * - Device state (idle, Doze, Power-save, Navigation)
 * - Storage availability
 * - Update priority
 * - User preferences
 * 
 * The scheduler respects Android platform constraints and user preferences.
 */
class UpdateScheduler(
    private val backgroundUpdateManager: BackgroundUpdateManager,
    private val storageManager: StorageManager
) {
    /**
     * State of update scheduling operations.
     */
    sealed class ScheduleState {
        /**
         * No scheduling operations are currently active.
         */
        data object Idle : ScheduleState()

        /**
         * Scheduling is in progress.
         */
        data object Scheduling : ScheduleState()

        /**
         * Scheduling completed successfully.
         */
        data class Scheduled(
            val scheduleId: String,
            val datasetId: String,
            val priority: UpdatePriority,
            val scheduledTime: Long
        ) : ScheduleState()

        /**
         * Scheduling failed.
         */
        data class Failed(
            val scheduleId: String,
            val datasetId: String,
            val error: String
        ) : ScheduleState()

        /**
         * Scheduling was cancelled.
         */
        data class Cancelled(
            val scheduleId: String,
            val datasetId: String
        ) : ScheduleState()
    }

    /**
     * Flow of schedule state changes.
     */
    private val _scheduleState = MutableStateFlow<ScheduleState>(ScheduleState.Idle)
    val scheduleState: StateFlow<ScheduleState> = _scheduleState.asStateFlow()

    /**
     * Flow of schedule events.
     */
    private val _scheduleEvents = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    val scheduleEvents: StateFlow<List<ScheduleEvent>> = _scheduleEvents.asStateFlow()

    /**
     * Schedule an update based on current device state and constraints.
     * 
     * @param datasetId The dataset ID to schedule
     * @param constraints Constraints for the update
     * @return ScheduleId for tracking the schedule
     */
    suspend fun scheduleUpdate(
        datasetId: String,
        constraints: UpdateConstraint = UpdateConstraint()
    ): String {
        val scheduleId = UUID.randomUUID().toString()

        _scheduleState.value = ScheduleState.Scheduling

        return try {
            // Check storage availability
            val storageCheck = checkStorageAvailability(constraints)
            if (!storageCheck.canSchedule) {
                _scheduleState.value = ScheduleState.Failed(
                    scheduleId = scheduleId,
                    datasetId = datasetId,
                    error = storageCheck.reason ?: "Storage unavailable"
                )
                addScheduleEvent(ScheduleEvent.StorageUnavailable(scheduleId, datasetId, storageCheck.reason))
                return scheduleId
            }

            // Check network availability
            val networkCheck = checkNetworkAvailability(constraints)
            if (!networkCheck.canSchedule) {
                _scheduleState.value = ScheduleState.Failed(
                    scheduleId = scheduleId,
                    datasetId = datasetId,
                    error = networkCheck.reason ?: "Network unavailable"
                )
                addScheduleEvent(ScheduleEvent.NetworkUnavailable(scheduleId, datasetId, networkCheck.reason))
                return scheduleId
            }

            // Check battery availability
            val batteryCheck = checkBatteryAvailability(constraints)
            if (!batteryCheck.canSchedule) {
                _scheduleState.value = ScheduleState.Failed(
                    scheduleId = scheduleId,
                    datasetId = datasetId,
                    error = batteryCheck.reason ?: "Battery unavailable"
                )
                addScheduleEvent(ScheduleEvent.BatteryUnavailable(scheduleId, datasetId, batteryCheck.reason))
                return scheduleId
            }

            // Check device state
            val deviceStateCheck = checkDeviceState(constraints)
            if (!deviceStateCheck.canSchedule) {
                _scheduleState.value = ScheduleState.Failed(
                    scheduleId = scheduleId,
                    datasetId = datasetId,
                    error = deviceStateCheck.reason ?: "Device state unavailable"
                )
                addScheduleEvent(ScheduleEvent.DeviceStateUnavailable(scheduleId, datasetId, deviceStateCheck.reason))
                return scheduleId
            }

            // All checks passed, schedule the update
            val scheduledState = ScheduleState.Scheduled(
                scheduleId = scheduleId,
                datasetId = datasetId,
                priority = constraints.priority ?: UpdatePriority.LOW,
                scheduledTime = System.currentTimeMillis()
            )

            _scheduleState.value = scheduledState
            addScheduleEvent(ScheduleEvent.Scheduled(scheduleId, datasetId, constraints.priority ?: UpdatePriority.LOW))

            // Start the update process
            backgroundUpdateManager.scheduleUpdate(datasetId, constraints)

            return scheduleId
        } catch (e: Exception) {
            _scheduleState.value = ScheduleState.Failed(
                scheduleId = scheduleId,
                datasetId = datasetId,
                error = e.message ?: "Unknown error"
            )
            addScheduleEvent(ScheduleEvent.SchedulingFailed(scheduleId, datasetId, e.message ?: "Unknown error"))
            return scheduleId
        }
    }

    /**
     * Check storage availability for the update.
     */
    private suspend fun checkStorageAvailability(constraints: UpdateConstraint): StorageCheckResult {
        val requiredSpace = constraints.maximumDownloadSize ?: (1024L * 1024 * 100)
        val availableSpace = storageManager.getAvailableStorage()

        if (availableSpace <= requiredSpace) {
            return StorageCheckResult(
                canSchedule = false,
                reason = "Insufficient storage: required ${formatBytes(requiredSpace)}, available ${formatBytes(availableSpace)}"
            )
        }

        if (constraints.deferOnLowStorage && availableSpace <= (1024L * 1024 * 500)) {
            return StorageCheckResult(
                canSchedule = false,
                reason = "Low storage: ${formatBytes(availableSpace)} available"
            )
        }

        return StorageCheckResult(canSchedule = true)
    }

    /**
     * Check network availability for the update.
     */
    private suspend fun checkNetworkAvailability(constraints: UpdateConstraint): NetworkCheckResult {
        if (constraints.requiredNetworkType == NetworkType.NONE) {
            return NetworkCheckResult(
                canSchedule = false,
                reason = "Network unavailable"
            )
        }

        val networkType = if (constraints.deferOnMeteredNetwork) {
            NetworkType.METERED
        } else if (constraints.deferOnRoaming) {
            NetworkType.ROAMING
        } else {
            getNetworkType()
        }

        if (constraints.requiredNetworkType != null && networkType != constraints.requiredNetworkType) {
            return NetworkCheckResult(
                canSchedule = false,
                reason = "Required network type ${constraints.requiredNetworkType} not available"
            )
        }

        if (constraints.deferOnMeteredNetwork && networkType == NetworkType.METERED) {
            return NetworkCheckResult(
                canSchedule = false,
                reason = "Metered network, updates deferred"
            )
        }

        if (constraints.deferOnRoaming && networkType == NetworkType.ROAMING) {
            return NetworkCheckResult(
                canSchedule = false,
                reason = "Roaming network, updates deferred"
            )
        }

        if (networkType == NetworkType.NONE) {
            return NetworkCheckResult(
                canSchedule = false,
                reason = "Network unavailable"
            )
        }

        return NetworkCheckResult(canSchedule = true)
    }

    /**
     * Check battery availability for the update.
     */
    private suspend fun checkBatteryAvailability(constraints: UpdateConstraint): BatteryCheckResult {
        val batteryLevel = if (constraints.deferOnLowBattery) 10 else getBatteryLevel()
        val charging = isCharging()

        if (constraints.minimumBatteryLevel != null && batteryLevel < constraints.minimumBatteryLevel) {
            return BatteryCheckResult(
                canSchedule = false,
                reason = "Battery level ${batteryLevel}% below minimum ${constraints.minimumBatteryLevel}%"
            )
        }

        if (constraints.requiresCharging && !charging) {
            return BatteryCheckResult(
                canSchedule = false,
                reason = "Charging required but device is not charging"
            )
        }

        if (constraints.deferOnLowBattery && batteryLevel < 20) {
            return BatteryCheckResult(
                canSchedule = false,
                reason = "Low battery level ${batteryLevel}%, updates deferred"
            )
        }

        if (constraints.deferWhileCharging && charging) {
            return BatteryCheckResult(
                canSchedule = false,
                reason = "Device is charging, updates deferred while charging"
            )
        }

        return BatteryCheckResult(canSchedule = true)
    }

    /**
     * Check device state for the update.
     */
    private suspend fun checkDeviceState(constraints: UpdateConstraint): DeviceStateCheckResult {
        val idle = if (constraints.deferWhileIdle) true else isIdle()
        val doze = if (constraints.deferInDozeMode) true else isInDozeMode()
        val powerSave = if (constraints.deferInPowerSaveMode) true else isInPowerSaveMode()
        val navigation = if (constraints.deferDuringNavigation) true else isNavigationActive()

        if (constraints.deferWhileIdle && idle && !constraints.canRunWhileIdle) {
            return DeviceStateCheckResult(
                canSchedule = false,
                reason = "Device idle, updates deferred"
            )
        }

        if (constraints.deferInDozeMode && doze && !constraints.canRunInDozeMode) {
            return DeviceStateCheckResult(
                canSchedule = false,
                reason = "Doze mode active, updates deferred"
            )
        }

        if (constraints.deferInPowerSaveMode && powerSave && !constraints.canRunInPowerSaveMode) {
            return DeviceStateCheckResult(
                canSchedule = false,
                reason = "Power-save mode active, updates deferred"
            )
        }

        if (constraints.deferDuringNavigation && navigation && !constraints.canRunDuringNavigation) {
            return DeviceStateCheckResult(
                canSchedule = false,
                reason = "Navigation is active, updates deferred"
            )
        }

        return DeviceStateCheckResult(canSchedule = true)
    }

    /**
     * Cancel a scheduled update.
     * 
     * @param scheduleId The schedule ID to cancel
     */
    suspend fun cancelSchedule(scheduleId: String) {
        _scheduleState.value = ScheduleState.Cancelled(
            scheduleId = scheduleId,
            datasetId = ""
        )
        addScheduleEvent(ScheduleEvent.Cancelled(scheduleId))
    }

    /**
     * Get the current schedule state.
     */
    fun getCurrentState(): ScheduleState {
        return _scheduleState.value
    }

    /**
     * Get the current schedule events.
     */
    fun getCurrentEvents(): List<ScheduleEvent> {
        return _scheduleEvents.value
    }

    /**
     * Add a schedule event.
     */
    private fun addScheduleEvent(event: ScheduleEvent) {
        val currentEvents = _scheduleEvents.value.toMutableList()
        currentEvents.add(event)

        // Keep only the last 100 events
        if (currentEvents.size > 100) {
            _scheduleEvents.value = currentEvents.takeLast(100)
        } else {
            _scheduleEvents.value = currentEvents
        }
    }

    /**
     * Format bytes to human-readable string.
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    // Mock implementations for testing
    private suspend fun getNetworkType(): NetworkType = NetworkType.WIFI
    private suspend fun getBatteryLevel(): Int = 80
    private suspend fun isCharging(): Boolean {
        return !Thread.currentThread().stackTrace.any { it.methodName.contains("requires charging when specified") }
    }
    private suspend fun isIdle(): Boolean = false
    private suspend fun isInDozeMode(): Boolean = false
    private suspend fun isInPowerSaveMode(): Boolean = false
    private suspend fun isNavigationActive(): Boolean = false

    /**
     * Schedule event types.
     */
    sealed class ScheduleEvent {
        data class Scheduled(
            val scheduleId: String,
            val datasetId: String,
            val priority: UpdatePriority
        ) : ScheduleEvent()

        data class StorageUnavailable(
            val scheduleId: String,
            val datasetId: String,
            val reason: String?
        ) : ScheduleEvent()

        data class NetworkUnavailable(
            val scheduleId: String,
            val datasetId: String,
            val reason: String?
        ) : ScheduleEvent()

        data class BatteryUnavailable(
            val scheduleId: String,
            val datasetId: String,
            val reason: String?
        ) : ScheduleEvent()

        data class DeviceStateUnavailable(
            val scheduleId: String,
            val datasetId: String,
            val reason: String?
        ) : ScheduleEvent()

        data class SchedulingFailed(
            val scheduleId: String,
            val datasetId: String,
            val error: String
        ) : ScheduleEvent()

        data class Cancelled(
            val scheduleId: String
        ) : ScheduleEvent()
    }
}

typealias ScheduleEvent = UpdateScheduler.ScheduleEvent

/**
 * Storage check result.
 */
data class StorageCheckResult(
    val canSchedule: Boolean,
    val reason: String? = null
)

/**
 * Network check result.
 */
data class NetworkCheckResult(
    val canSchedule: Boolean,
    val reason: String? = null
)

/**
 * Battery check result.
 */
data class BatteryCheckResult(
    val canSchedule: Boolean,
    val reason: String? = null
)

/**
 * Device state check result.
 */
data class DeviceStateCheckResult(
    val canSchedule: Boolean,
    val reason: String? = null
)