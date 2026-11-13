package com.evnavigator.iran.domain.journey

import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState
import com.evnavigator.iran.domain.journey.network.JourneyConnectivityMode
import com.evnavigator.iran.domain.journey.network.NetworkDegradationManager
import com.evnavigator.iran.domain.journey.recovery.JourneyRecoveryManager
import com.evnavigator.iran.domain.journey.recovery.RecoveryAssessment
import org.junit.Assert.*
import org.junit.Test

class JourneyRecoveryAndNetworkTest {

    @Test
    fun testRecoveryAssessesRecentActiveJourneyAsRecoverable() {
        val manager = JourneyRecoveryManager()
        val now = System.currentTimeMillis()

        val assessment = manager.assessRecovery(
            journeyId = "j_tehran_isfahan_01",
            stateString = "ACTIVE",
            originName = "تهران",
            destinationName = "اصفهان",
            lastSocPercent = 65.0,
            lastUpdatedEpochMs = now - (15 * 60 * 1000L), // 15 mins ago
            currentEpochMs = now
        )

        assertTrue(assessment is RecoveryAssessment.Recoverable)
        val rec = assessment as RecoveryAssessment.Recoverable
        assertEquals(JourneyLifecycleState.ACTIVE, rec.lastState)
        assertEquals(65.0, rec.lastSocPercent, 0.01)
        assertEquals(15L, rec.elapsedMinutesSinceInterruption)
    }

    @Test
    fun testRecoveryRejectsTerminalJourney() {
        val manager = JourneyRecoveryManager()
        val assessment = manager.assessRecovery(
            journeyId = "j_done",
            stateString = "COMPLETED",
            originName = "تهران",
            destinationName = "قم",
            lastSocPercent = 30.0,
            lastUpdatedEpochMs = System.currentTimeMillis() - 5000L
        )

        assertTrue(assessment is RecoveryAssessment.NoActiveJourney)
    }

    @Test
    fun testRecoveryRejectsExpiredJourney() {
        val manager = JourneyRecoveryManager()
        val now = System.currentTimeMillis()

        val assessment = manager.assessRecovery(
            journeyId = "j_old",
            stateString = "ACTIVE",
            originName = "تهران",
            destinationName = "مشهد",
            lastSocPercent = 40.0,
            lastUpdatedEpochMs = now - (24 * 3600 * 1000L), // 24 hours ago
            currentEpochMs = now
        )

        assertTrue(assessment is RecoveryAssessment.Expired)
    }

    @Test
    fun testNetworkDegradationReporting() {
        val manager = NetworkDegradationManager()
        val now = System.currentTimeMillis()

        // 1. Online
        assertEquals(JourneyConnectivityMode.ONLINE_FULL, manager.determineMode(true, now, now))

        // 2. Offline recent (10 mins)
        val degraded = manager.determineMode(false, now - (10 * 60 * 1000L), now)
        assertEquals(JourneyConnectivityMode.OFFLINE_DEGRADED, degraded)
        assertNotNull(manager.getDegradationNotice(degraded))

        // 3. Offline stale (2 hours)
        val stale = manager.determineMode(false, now - (2 * 3600 * 1000L), now)
        assertEquals(JourneyConnectivityMode.OFFLINE_STALE_DATA, stale)
        assertTrue(manager.getDegradationNotice(stale)!!.contains("هشدار"))
    }
}
