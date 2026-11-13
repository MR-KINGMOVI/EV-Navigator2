package com.evnavigator.iran.domain.intelligence.replanning

import org.junit.Assert.*
import org.junit.Test

class DynamicReplanningCoordinatorTest {

    private val coordinator = DynamicReplanningCoordinator(
        minReplanIntervalMs = 20_000L,
        minOffRouteDistanceMeters = 50.0,
        minSocDeficitPercent = 4.0
    )

    @Test
    fun testSmallDeviationDoesNotTriggerReplan() {
        val eval = coordinator.evaluateDeviation(18.0)
        assertFalse("18m deviation should not trigger replan", eval.shouldReplan)
        assertNull(eval.trigger)
        assertTrue(eval.explanation.contains("GPS jitter tolerance"))
    }

    @Test
    fun testLargeDeviationTriggersReplan() {
        val eval = coordinator.evaluateDeviation(75.0)
        assertTrue("75m deviation must trigger replan", eval.shouldReplan)
        assertEquals(ReplanningTrigger.ROUTE_DEVIATION, eval.trigger)
        assertTrue(eval.explanation.contains("deviated by 75 meters"))
    }

    @Test
    fun testSocDeficitGatingAndHysteresis() {
        // Minor deficit (2% vs 4% threshold)
        val minorEval = coordinator.evaluateSocDeficit(actualSocPercent = 48.0, expectedSocPercent = 50.0)
        assertFalse(minorEval.shouldReplan)

        // Significant deficit (6% > 4% threshold)
        val t0 = 100_000L
        val majorEval = coordinator.evaluateSocDeficit(actualSocPercent = 44.0, expectedSocPercent = 50.0, currentEpochMs = t0)
        assertTrue(majorEval.shouldReplan)
        assertEquals(ReplanningTrigger.MATERIAL_SOC_DEFICIT, majorEval.trigger)

        // Rapid duplicate deficit 5 seconds later should be suppressed by hysteresis
        val suppressedEval = coordinator.evaluateSocDeficit(actualSocPercent = 43.0, expectedSocPercent = 50.0, currentEpochMs = t0 + 5_000L)
        assertFalse("Rapid trigger within 20s must be suppressed by hysteresis", suppressedEval.shouldReplan)
        assertTrue(suppressedEval.isSuppressedByHysteresis)
    }

    @Test
    fun testPlannedStationDownTriggersImmediateReplan() {
        val eval = coordinator.evaluateChargingStationStatus(
            isPlannedStationStillAvailable = false,
            stationName = "Station Qom North"
        )
        assertTrue(eval.shouldReplan)
        assertEquals(ReplanningTrigger.CHARGING_STATION_UNAVAILABLE, eval.trigger)
        assertTrue(eval.explanation.contains("reported unavailable"))
    }

    @Test
    fun testRoutePreservationOnReplacementFailure() {
        coordinator.setActiveRoute("route_initial_1")
        assertEquals("route_initial_1", coordinator.getCurrentActiveRouteId())

        // Replacement route computation failed
        val accepted = coordinator.confirmRouteReplacement(newRouteId = null, isReplacementValid = false)
        assertFalse(accepted)
        assertEquals("Active route must remain initial route on failure", "route_initial_1", coordinator.getCurrentActiveRouteId())

        // Replacement route computation succeeded
        val accepted2 = coordinator.confirmRouteReplacement(newRouteId = "route_new_2", isReplacementValid = true)
        assertTrue(accepted2)
        assertEquals("route_new_2", coordinator.getCurrentActiveRouteId())
    }
}
