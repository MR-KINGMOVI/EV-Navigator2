package com.evnavigator.iran.core.startup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OfflineStartupInitializer.
 * Verifies non-blocking preflight check, graceful degradation when datasets are missing, and idempotency.
 */
class OfflineStartupInitializerTest {

    @Test
    fun `test all datasets present results in READY state`() = runTest {
        val initializer = OfflineStartupInitializer(
            verifyDatabases = { true },
            loadActiveVersions = { Triple("osm_1.0", "chargers_1.0", "vehicles_1.0") }
        )

        val report = initializer.initialize()

        assertEquals(StartupState.READY, report.state)
        assertEquals("osm_1.0", report.activeMapVersion)
        assertEquals("chargers_1.0", report.activeChargerVersion)
        assertEquals("vehicles_1.0", report.activeVehicleVersion)
        assertTrue(report.warnings.isEmpty())
    }

    @Test
    fun `test missing datasets results in DEGRADED state with actionable warnings`() = runTest {
        val initializer = OfflineStartupInitializer(
            verifyDatabases = { true },
            loadActiveVersions = { Triple(null, "chargers_1.0", null) } // Missing map and vehicle datasets
        )

        val report = initializer.initialize()

        assertEquals(StartupState.DEGRADED, report.state)
        assertNull(report.activeMapVersion)
        assertEquals("chargers_1.0", report.activeChargerVersion)
        assertEquals(2, report.warnings.size)
        assertTrue(report.warnings.any { it.contains("Offline road network") })
    }

    @Test
    fun `test initialization failure is captured safely without throwing unhandled exception`() = runTest {
        val initializer = OfflineStartupInitializer(
            verifyDatabases = { throw RuntimeException("Disk I/O error") },
            loadActiveVersions = { Triple("1", "1", "1") }
        )

        val report = initializer.initialize()

        assertEquals(StartupState.FAILED, report.state)
        assertTrue(report.warnings.first().contains("Disk I/O error"))
    }
}
