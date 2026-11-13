package com.evnavigator.iran.core.degraded

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflineDegradedOperationManager.
 * Verifies freshness state transitions, anti-promotion rules, and cross-domain health reporting.
 */
class OfflineDegradedOperationTest {

    private lateinit var manager: OfflineDegradedOperationManager

    @Before
    fun setUp() {
        manager = OfflineDegradedOperationManager()
    }

    @Test
    fun `test initial state of all domains is UNAVAILABLE`() {
        for (domain in SystemDomain.values()) {
            val status = manager.getDomainStatus(domain)
            assertEquals(OperationalFreshnessState.UNAVAILABLE, status.state)
        }
        assertFalse(manager.isOfflineTripPlanningViable())
    }

    @Test
    fun `test transition to OFFLINE_DATASET activates offline planning viability`() {
        manager.updateDomainStatus(
            SystemDomain.MAP_ROUTING,
            OperationalFreshnessState.OFFLINE_DATASET,
            "osm_tehran",
            "1.0",
            System.currentTimeMillis(),
            "Offline road network installed"
        )
        manager.updateDomainStatus(
            SystemDomain.CHARGING_INFRASTRUCTURE,
            OperationalFreshnessState.OFFLINE_DATASET,
            "chargers_iran",
            "1.0",
            System.currentTimeMillis(),
            "Offline charging stations installed"
        )
        manager.updateDomainStatus(
            SystemDomain.VEHICLE_SPECIFICATIONS,
            OperationalFreshnessState.OFFLINE_DATASET,
            "vehicles_iran",
            "1.0",
            System.currentTimeMillis(),
            "Offline vehicle specifications installed"
        )

        assertTrue(manager.isOfflineTripPlanningViable())
    }

    @Test
    fun `test stale-to-live promotion guard rejects stale data without fresh online verification`() {
        // Set to STALE with old timestamp
        val oldTime = System.currentTimeMillis() - 60_000L // 1 minute ago
        manager.updateDomainStatus(
            SystemDomain.TRAFFIC,
            OperationalFreshnessState.CACHED_STALE,
            "traffic_cache",
            "1.0",
            oldTime,
            "Traffic data is stale"
        )

        // Attempt to promote to LIVE with old timestamp -> must be rejected
        val accepted = manager.updateDomainStatus(
            SystemDomain.TRAFFIC,
            OperationalFreshnessState.LIVE,
            "traffic_cache",
            "1.0",
            oldTime,
            "Unverified promotion"
        )

        assertFalse(accepted)
        assertEquals(OperationalFreshnessState.CACHED_STALE, manager.getDomainStatus(SystemDomain.TRAFFIC).state)
    }
}
