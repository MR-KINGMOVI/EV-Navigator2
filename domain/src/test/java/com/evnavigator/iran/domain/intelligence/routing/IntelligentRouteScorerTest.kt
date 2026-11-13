package com.evnavigator.iran.domain.intelligence.routing

import org.junit.Assert.*
import org.junit.Test

class IntelligentRouteScorerTest {

    private val scorer = IntelligentRouteScorer()

    private val fastRoute = RouteCandidate(
        routeId = "fast_highway",
        distanceMeters = 100_000.0,
        durationSeconds = 3600.0, // 60 mins
        predictedEnergyKwh = 22.0,
        predictedArrivalSocPercent = 30.0,
        chargingStopCount = 0,
        totalChargingAndDetourMinutes = 0.0,
        uncertaintyScore = 0.10
    )

    private val ecoRoute = RouteCandidate(
        routeId = "scenic_eco",
        distanceMeters = 90_000.0,
        durationSeconds = 4800.0, // 80 mins
        predictedEnergyKwh = 15.0, // saves 7 kWh
        predictedArrivalSocPercent = 45.0,
        chargingStopCount = 0,
        totalChargingAndDetourMinutes = 0.0,
        uncertaintyScore = 0.15
    )

    private val restrictedRoute = RouteCandidate(
        routeId = "illegal_shortcut",
        distanceMeters = 80_000.0,
        durationSeconds = 3000.0,
        predictedEnergyKwh = 14.0,
        predictedArrivalSocPercent = 48.0,
        chargingStopCount = 0,
        totalChargingAndDetourMinutes = 0.0,
        uncertaintyScore = 0.10,
        hasRestrictedOrInaccessibleRoad = true
    )

    @Test
    fun testScoringIsDeterministic() {
        val candidates = listOf(fastRoute, ecoRoute)
        val result1 = scorer.scoreAndRankRoutes(candidates, RouteOptimizationObjective.BALANCED)
        val result2 = scorer.scoreAndRankRoutes(candidates, RouteOptimizationObjective.BALANCED)

        assertEquals("Number of ranked routes must match", result1.size, result2.size)
        assertEquals("First ranked route must be identical", result1[0].candidate.routeId, result2[0].candidate.routeId)
        assertEquals("Scores must be identical down to double precision", result1[0].components.compositeScore, result2[0].components.compositeScore, 0.000001)
    }

    @Test
    fun testFastRouteWinsUnderMinimumTimeObjective() {
        val ranked = scorer.scoreAndRankRoutes(listOf(fastRoute, ecoRoute), RouteOptimizationObjective.MINIMUM_TIME)
        assertEquals("Fast route must be rank 1 under MINIMUM_TIME", "fast_highway", ranked[0].candidate.routeId)
        assertEquals(1, ranked[0].rank)
        assertTrue(ranked[0].explanationReason.contains("Fastest route"))
    }

    @Test
    fun testEcoRouteWinsUnderMinimumEnergyObjective() {
        val ranked = scorer.scoreAndRankRoutes(listOf(fastRoute, ecoRoute), RouteOptimizationObjective.MINIMUM_ENERGY)
        assertEquals("Eco route must be rank 1 under MINIMUM_ENERGY", "scenic_eco", ranked[0].candidate.routeId)
        assertEquals(1, ranked[0].rank)
        assertTrue(ranked[0].explanationReason.contains("Most energy-efficient"))
    }

    @Test
    fun testRestrictedRoadRouteIsPenalizedAndRejected() {
        val ranked = scorer.scoreAndRankRoutes(listOf(fastRoute, restrictedRoute))
        val restrictedScored = ranked.first { it.candidate.routeId == "illegal_shortcut" }

        assertFalse("Restricted road route must not be viable", restrictedScored.isViable)
        assertTrue("Penalty must be large", restrictedScored.components.penalty >= 1000.0)
        assertEquals("Restricted route must be ranked last", 2, restrictedScored.rank)
    }

    @Test
    fun testNegativeArrivalSocRejected() {
        val deadBatteryRoute = fastRoute.copy(
            routeId = "depleted_battery",
            predictedArrivalSocPercent = -5.0
        )
        val ranked = scorer.scoreAndRankRoutes(listOf(deadBatteryRoute, ecoRoute))
        val scoredDepleted = ranked.first { it.candidate.routeId == "depleted_battery" }

        assertFalse("Route with negative arrival SOC cannot be viable", scoredDepleted.isViable)
        assertTrue(scoredDepleted.explanationReason.contains("below zero"))
    }
}
