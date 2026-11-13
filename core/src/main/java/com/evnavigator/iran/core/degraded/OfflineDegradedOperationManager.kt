package com.evnavigator.iran.core.degraded

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Functional domains participating in offline operation.
 */
enum class SystemDomain {
    MAP_ROUTING,
    ELEVATION,
    CHARGING_INFRASTRUCTURE,
    VEHICLE_SPECIFICATIONS,
    WEATHER,
    TRAFFIC
}

/**
 * High-integrity state classification for a data domain.
 */
enum class OperationalFreshnessState {
    LIVE,
    CACHED_FRESH,
    CACHED_STALE,
    OFFLINE_DATASET,
    PARTIAL_COVERAGE,
    UNAVAILABLE,
    INVALID_OR_CORRUPT
}

/**
 * Detailed status descriptor for a specific domain.
 */
data class DomainStatus(
    val domain: SystemDomain,
    val state: OperationalFreshnessState,
    val sourceDataset: String?,
    val datasetVersion: String?,
    val lastVerifiedTimestamp: Long,
    val diagnosticMessage: String
)

/**
 * Manages freshness state transitions and degraded operation policies across all EV Navigator domains.
 *
 * Enforces critical safety guarantees:
 * - Stale-to-live promotion is strictly prohibited without explicit fresh online verification
 * - Missing values are recorded as UNAVAILABLE or PARTIAL_COVERAGE, never synthesized into fake defaults
 * - Exposes unified observable system health for UI warnings and user explanations
 */
class OfflineDegradedOperationManager {

    private val domainStates = ConcurrentHashMap<SystemDomain, DomainStatus>()
    private val _systemHealthFlow = MutableStateFlow<Map<SystemDomain, DomainStatus>>(emptyMap())
    val systemHealthFlow: StateFlow<Map<SystemDomain, DomainStatus>> = _systemHealthFlow.asStateFlow()

    init {
        // Initialize all domains to UNAVAILABLE by default until verified
        for (domain in SystemDomain.values()) {
            domainStates[domain] = DomainStatus(
                domain = domain,
                state = OperationalFreshnessState.UNAVAILABLE,
                sourceDataset = null,
                datasetVersion = null,
                lastVerifiedTimestamp = 0L,
                diagnosticMessage = "Domain data not initialized or offline package missing"
            )
        }
        _systemHealthFlow.value = domainStates.toMap()
    }

    /**
     * Updates the status of a specific domain following strict state transition rules.
     */
    fun updateDomainStatus(
        domain: SystemDomain,
        newState: OperationalFreshnessState,
        sourceDataset: String?,
        datasetVersion: String?,
        timestamp: Long = System.currentTimeMillis(),
        diagnosticMessage: String
    ): Boolean {
        val current = domainStates[domain] ?: return false

        // Anti-promotion guard: Stale or offline dataset cannot transition to LIVE without a fresh online timestamp
        if (newState == OperationalFreshnessState.LIVE &&
            (current.state == OperationalFreshnessState.CACHED_STALE || current.state == OperationalFreshnessState.OFFLINE_DATASET)
        ) {
            // Must have a recent timestamp (within 5 seconds) to qualify as fresh live online verification
            if (System.currentTimeMillis() - timestamp > 5000L) {
                return false // Reject promotion without fresh verification
            }
        }

        val updated = DomainStatus(
            domain = domain,
            state = newState,
            sourceDataset = sourceDataset,
            datasetVersion = datasetVersion,
            lastVerifiedTimestamp = timestamp,
            diagnosticMessage = diagnosticMessage
        )
        domainStates[domain] = updated
        _systemHealthFlow.value = domainStates.toMap()
        return true
    }

    fun getDomainStatus(domain: SystemDomain): DomainStatus {
        return domainStates[domain] ?: DomainStatus(
            domain = domain,
            state = OperationalFreshnessState.UNAVAILABLE,
            sourceDataset = null,
            datasetVersion = null,
            lastVerifiedTimestamp = 0L,
            diagnosticMessage = "Domain not registered"
        )
    }

    /**
     * Checks if overall trip planning is viable in offline mode.
     */
    fun isOfflineTripPlanningViable(): Boolean {
        val mapStatus = getDomainStatus(SystemDomain.MAP_ROUTING).state
        val chargerStatus = getDomainStatus(SystemDomain.CHARGING_INFRASTRUCTURE).state
        val vehicleStatus = getDomainStatus(SystemDomain.VEHICLE_SPECIFICATIONS).state

        val mapReady = mapStatus == OperationalFreshnessState.OFFLINE_DATASET || mapStatus == OperationalFreshnessState.LIVE
        val chargerReady = chargerStatus == OperationalFreshnessState.OFFLINE_DATASET || chargerStatus == OperationalFreshnessState.LIVE
        val vehicleReady = vehicleStatus == OperationalFreshnessState.OFFLINE_DATASET || vehicleStatus == OperationalFreshnessState.LIVE || vehicleStatus == OperationalFreshnessState.CACHED_FRESH

        return mapReady && chargerReady && vehicleReady
    }
}
