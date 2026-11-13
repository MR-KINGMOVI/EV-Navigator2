package com.evnavigator.iran.core.startup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Startup health and initialization state.
 */
enum class StartupState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    DEGRADED,
    FAILED
}

/**
 * Startup diagnostic report.
 */
data class StartupReport(
    val state: StartupState,
    val durationMs: Long,
    val activeMapVersion: String?,
    val activeChargerVersion: String?,
    val activeVehicleVersion: String?,
    val warnings: List<String>
)

/**
 * Manages asynchronous, non-blocking preflight initialization for offline EV Navigator systems.
 *
 * Guarantees:
 * - Never blocks the Android Main/UI thread (runs strictly on Dispatchers.IO)
 * - Safe degradation if datasets are missing (transitions to DEGRADED rather than crashing)
 * - Idempotent initialization with Mutex synchronization
 */
class OfflineStartupInitializer(
    private val verifyDatabases: suspend () -> Boolean,
    private val loadActiveVersions: suspend () -> Triple<String?, String?, String?>
) {

    private val mutex = Mutex()
    private var currentState: StartupState = StartupState.UNINITIALIZED
    private var cachedReport: StartupReport? = null

    suspend fun initialize(): StartupReport = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (currentState == StartupState.READY && cachedReport != null) {
                return@withContext cachedReport!!
            }

            currentState = StartupState.INITIALIZING
            val start = System.currentTimeMillis()
            val warnings = mutableListOf<String>()

            try {
                val dbOk = verifyDatabases()
                if (!dbOk) {
                    warnings.add("Local database tables not fully populated")
                }

                val (mapVer, chargerVer, vehicleVer) = loadActiveVersions()

                if (mapVer == null) warnings.add("Offline road network dataset not found")
                if (chargerVer == null) warnings.add("Offline charging stations dataset not found")
                if (vehicleVer == null) warnings.add("Offline vehicle specification database not found")

                val finalState = if (mapVer != null && chargerVer != null && vehicleVer != null && dbOk) {
                    StartupState.READY
                } else {
                    StartupState.DEGRADED
                }

                currentState = finalState
                val report = StartupReport(
                    state = finalState,
                    durationMs = System.currentTimeMillis() - start,
                    activeMapVersion = mapVer,
                    activeChargerVersion = chargerVer,
                    activeVehicleVersion = vehicleVer,
                    warnings = warnings
                )
                cachedReport = report
                return@withContext report
            } catch (e: Throwable) {
                currentState = StartupState.FAILED
                val failReport = StartupReport(
                    state = StartupState.FAILED,
                    durationMs = System.currentTimeMillis() - start,
                    activeMapVersion = null,
                    activeChargerVersion = null,
                    activeVehicleVersion = null,
                    warnings = listOf("Critical startup failure: ${e.message}")
                )
                cachedReport = failReport
                return@withContext failReport
            }
        }
    }
}
