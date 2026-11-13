package com.evnavigator.iran.domain.model.update

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for UpdateConstraint class.
 */
class UpdateConstraintTest {

    @Test
    fun `default constraint should allow all updates`() {
        val constraint = UpdateConstraint()

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Default constraint should allow updates", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `constraint with required network type should block when wrong type`() {
        val constraint = UpdateConstraint(requiredNetworkType = NetworkType.WIFI)

        val result = constraint.canRun(
            networkType = NetworkType.MOBILE,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block when wrong network type", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate wrong network type", "Required network type WIFI not available", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint with minimum battery level should block when below minimum`() {
        val constraint = UpdateConstraint(minimumBatteryLevel = 50)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 30,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block when below minimum battery level", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate low battery", "Battery level 30% is below minimum 50%", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint requiring charging should block when not charging`() {
        val constraint = UpdateConstraint(requiresCharging = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block when not charging", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate charging required", "Charging required but device is not charging", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint requiring charging should allow when charging`() {
        val constraint = UpdateConstraint(requiresCharging = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = true,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when charging", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `constraint deferring while idle should block when idle`() {
        val constraint = UpdateConstraint(deferWhileIdle = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = true,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block when idle", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate idle mode", "Update deferred while device is idle", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint deferring while idle should allow when not idle`() {
        val constraint = UpdateConstraint(deferWhileIdle = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when not idle", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `constraint deferring in Doze mode should block in Doze mode`() {
        val constraint = UpdateConstraint(deferInDozeMode = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = true,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block in Doze mode", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate Doze mode", "Update deferred while device is in Doze mode", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint deferring in power-save mode should block in power-save mode`() {
        val constraint = UpdateConstraint(deferInPowerSaveMode = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = true,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block in power-save mode", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate power-save mode", "Update deferred while device is in power-save mode", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint deferring during navigation should block during navigation`() {
        val constraint = UpdateConstraint(deferDuringNavigation = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = true,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block during navigation", result is UpdateConstraint.CannotRun)
        assertEquals("Should indicate navigation active", "Update deferred during active navigation", (result as UpdateConstraint.CannotRun).reason)
    }

    @Test
    fun `constraint deferring on low storage should block when storage is low`() {
        val constraint = UpdateConstraint(deferOnLowStorage = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 100, // 100 bytes available
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block on low storage", result is UpdateConstraint.CannotRun)
        assertTrue("Should indicate low storage", (result as UpdateConstraint.CannotRun).reason.contains("low storage"))
    }

    @Test
    fun `constraint deferring on critical low battery should block when battery is critically low`() {
        val constraint = UpdateConstraint(deferOnCriticalLowBattery = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 10,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block on critical low battery", result is UpdateConstraint.CannotRun)
        assertTrue("Should indicate critical low battery", (result as UpdateConstraint.CannotRun).reason.contains("critically low battery"))
    }

    @Test
    fun `constraint deferring on metered network should block on metered network`() {
        val constraint = UpdateConstraint(deferOnMeteredNetwork = true)

        val result = constraint.canRun(
            networkType = NetworkType.METERED,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block on metered network", result is UpdateConstraint.CannotRun)
        assertTrue("Should indicate metered network", (result as UpdateConstraint.CannotRun).reason.contains("metered network"))
    }

    @Test
    fun `constraint deferring on roaming should block on roaming network`() {
        val constraint = UpdateConstraint(deferOnRoaming = true)

        val result = constraint.canRun(
            networkType = NetworkType.ROAMING,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should block on roaming network", result is UpdateConstraint.CannotRun)
        assertTrue("Should indicate roaming network", (result as UpdateConstraint.CannotRun).reason.contains("roaming network"))
    }

    @Test
    fun `constraint with throttling should allow on metered network`() {
        val constraint = UpdateConstraint(isThrottled = true)

        val result = constraint.canRun(
            networkType = NetworkType.METERED,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow on metered network with throttling", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `constraint with maximum download size should allow when size is within limit`() {
        val constraint = UpdateConstraint(maximumDownloadSize = 100 * 1024 * 1024) // 100 MB

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when download size is within limit", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `canRunWithLowStorage should allow when storage is low`() {
        val constraint = UpdateConstraint(canRunWithLowStorage = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 100, // 100 bytes available
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when canRunWithLowStorage is true", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `canRunDuringNavigation should allow when navigation is not active`() {
        val constraint = UpdateConstraint(canRunDuringNavigation = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when navigation is not active", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `canRunInDozeMode should allow when not in Doze mode`() {
        val constraint = UpdateConstraint(canRunInDozeMode = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when not in Doze mode", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `canRunInPowerSaveMode should allow when not in power-save mode`() {
        val constraint = UpdateConstraint(canRunInPowerSaveMode = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024,
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when not in power-save mode", result is UpdateConstraint.CanRun)
    }

    @Test
    fun `canRunWithLowStorage should allow when storage is sufficient`() {
        val constraint = UpdateConstraint(canRunWithLowStorage = true)

        val result = constraint.canRun(
            networkType = NetworkType.WIFI,
            batteryLevel = 50,
            isCharging = false,
            isIdle = false,
            isInDozeMode = false,
            isInPowerSaveMode = false,
            isNavigationActive = false,
            storageAvailableBytes = 1024 * 1024 * 1024 * 1024, // 1 GB available
            totalStorageBytes = 1024L * 1024 * 1024 * 2
        )

        assertTrue("Should allow when storage is sufficient", result is UpdateConstraint.CanRun)
    }
}
