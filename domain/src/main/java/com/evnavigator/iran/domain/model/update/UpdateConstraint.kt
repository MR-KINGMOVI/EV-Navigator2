package com.evnavigator.iran.domain.model.update

/**
 * Constraints for background update execution.
 * 
 * These constraints determine when and how background updates can run,
 * respecting Android platform constraints and user preferences.
 */
data class UpdateConstraint(
    /**
     * Network type required for this update.
     * If null, any network type is acceptable.
     */
    val requiredNetworkType: NetworkType? = null,

    /**
     * Minimum battery level required for this update.
     * If null, no battery level restriction applies.
     */
    val minimumBatteryLevel: Int? = null,

    /**
     * Whether charging is required for this update.
     * Updates requiring charging should only run when the device is plugged in.
     */
    val requiresCharging: Boolean = false,

    /**
     * Whether the update can run while the device is idle.
     */
    val canRunWhileIdle: Boolean = false,

    /**
     * Whether the update can run while the device is in Doze mode.
     * Note: Android Doze mode restrictions apply.
     */
    val canRunInDozeMode: Boolean = false,

    /**
     * Whether the update can run while the device is in power-save mode.
     */
    val canRunInPowerSaveMode: Boolean = false,

    /**
     * Whether the update can run while navigation is active.
     * Updates that modify critical navigation data should not run during navigation.
     */
    val canRunDuringNavigation: Boolean = false,

    /**
     * Whether the update can run while the device is in low storage state.
     */
    val canRunWithLowStorage: Boolean = false,

    /**
     * Maximum allowed download size for this update.
     * If null, no size restriction applies.
     */
    val maximumDownloadSize: Long? = null,

    /**
     * Whether the update should be throttled (slower download speed).
     * Useful for metered networks.
     */
    val isThrottled: Boolean = false,

    /**
     * Whether the update should be deferred if network is metered.
     */
    val deferOnMeteredNetwork: Boolean = false,

    /**
     * Whether the update should be deferred if network is roaming.
     */
    val deferOnRoaming: Boolean = false,

    /**
     * Whether the update should be deferred if storage is insufficient.
     */
    val deferOnLowStorage: Boolean = false,

    /**
     * Whether the update should be deferred if battery is low.
     */
    val deferOnLowBattery: Boolean = false,

    /**
     * Whether the update should be deferred if device is charging.
     * Some users prefer updates only when not charging.
     */
    val deferWhileCharging: Boolean = false,

    /**
     * Whether the update should be deferred if device is idle.
     */
    val deferWhileIdle: Boolean = false,

    /**
     * Whether the update should be deferred if device is in Doze mode.
     */
    val deferInDozeMode: Boolean = false,

    /**
     * Whether the update should be deferred if device is in power-save mode.
     */
    val deferInPowerSaveMode: Boolean = false,

    /**
     * Whether the update should be deferred if navigation is active.
     */
    val deferDuringNavigation: Boolean = false,

    /**
     * Priority of the update.
     */
    val priority: UpdatePriority? = null,

    /**
     * Whether the update should be deferred if battery is critically low.
     */
    val deferOnCriticalLowBattery: Boolean = false
) {

    /**
     * Check if this update can run under the given system conditions.
     * 
     * @param networkType Current network type
     * @param batteryLevel Current battery level (0-100)
     * @param isCharging Whether device is currently charging
     * @param isIdle Whether device is idle
     * @param isInDozeMode Whether device is in Doze mode
     * @param isInPowerSaveMode Whether device is in power-save mode
     * @param isNavigationActive Whether navigation is currently active
     * @param storageAvailableBytes Available storage in bytes
     * @param totalStorageBytes Total storage in bytes
     * @return Result indicating whether update can run and reason if not
     */
    fun canRun(
        networkType: NetworkType,
        batteryLevel: Int,
        isCharging: Boolean,
        isIdle: Boolean,
        isInDozeMode: Boolean,
        isInPowerSaveMode: Boolean,
        isNavigationActive: Boolean,
        storageAvailableBytes: Long,
        totalStorageBytes: Long
    ): CanRunResult {
        // Check network requirements
        if (requiredNetworkType != null && networkType != requiredNetworkType) {
            return CanRunResult.CannotRun("Required network type ${requiredNetworkType.name} not available")
        }

        // Check battery level
        if (minimumBatteryLevel != null && batteryLevel < minimumBatteryLevel) {
            return CanRunResult.CannotRun("Battery level ${batteryLevel}% is below minimum ${minimumBatteryLevel}%")
        }

        // Check charging requirements
        if (requiresCharging && !isCharging) {
            return CanRunResult.CannotRun("Charging required but device is not charging")
        }

        // Check defer while charging
        if (isCharging && deferWhileCharging) {
            return CanRunResult.CannotRun("Update deferred while device is charging")
        }

        // Check idle mode
        if (isIdle && deferWhileIdle && !canRunWhileIdle) {
            return CanRunResult.CannotRun("Update deferred while device is idle")
        }

        // Check Doze mode
        if (isInDozeMode && deferInDozeMode && !canRunInDozeMode) {
            return CanRunResult.CannotRun("Update deferred while device is in Doze mode")
        }

        // Check power-save mode
        if (isInPowerSaveMode && deferInPowerSaveMode && !canRunInPowerSaveMode) {
            return CanRunResult.CannotRun("Update deferred while device is in power-save mode")
        }

        // Check navigation
        if (isNavigationActive && deferDuringNavigation && !canRunDuringNavigation) {
            return CanRunResult.CannotRun("Update deferred during active navigation")
        }

        // Check storage
        val storagePercentage = if (totalStorageBytes > 0) {
            storageAvailableBytes.toDouble() / totalStorageBytes
        } else if (totalStorageBytes < 0) {
            // Protect against 32-bit int overflow in callers passing Int totalStorageBytes
            val fixedTotal = (totalStorageBytes.toLong() and 0xFFFFFFFFL)
            storageAvailableBytes.toDouble() / fixedTotal
        } else {
            1.0
        }

        if (storagePercentage < 0.1 && (deferOnLowStorage || !canRunWithLowStorage) && !canRunWithLowStorage && deferOnLowStorage) {
            return CanRunResult.CannotRun("Update deferred due to low storage (${String.format("%.1f", storagePercentage * 100)}% available)")
        }

        // Check battery critically low
        if (batteryLevel < 15 && deferOnCriticalLowBattery) {
            return CanRunResult.CannotRun("Update deferred due to critically low battery (${batteryLevel}%)")
        }

        // Check battery low
        if (batteryLevel < 20 && deferOnLowBattery) {
            return CanRunResult.CannotRun("Update deferred due to low battery (${batteryLevel}%)")
        }

        // Check metered network
        if (networkType == NetworkType.METERED && deferOnMeteredNetwork && !isThrottled) {
            return CanRunResult.CannotRun("Update deferred on metered network")
        }

        // Check roaming
        if (networkType == NetworkType.ROAMING && deferOnRoaming) {
            return CanRunResult.CannotRun("Update deferred on roaming network")
        }

        // Check download size
        if (maximumDownloadSize != null && maximumDownloadSize > 0) {
            // This would be checked against the actual download size
            // For now, we just note it as a constraint
        }

        return CanRun
    }

    /**
     * Result of checking if an update can run.
     */
    sealed class CanRunResult {
        companion object {
            val CanRun: UpdateConstraint.CanRun = UpdateConstraint.CanRun
            fun CannotRun(reason: String): UpdateConstraint.CannotRun = UpdateConstraint.CannotRun(reason)
        }
    }

    data object CanRun : CanRunResult()
    data class CannotRun(val reason: String) : CanRunResult()
}

/**
 * Network type enumeration.
 */
enum class NetworkType {
    WIFI,
    MOBILE,
    METERED,
    ROAMING,
    NONE
}
