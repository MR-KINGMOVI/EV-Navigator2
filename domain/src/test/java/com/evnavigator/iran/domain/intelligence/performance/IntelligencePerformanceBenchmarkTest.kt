package com.evnavigator.iran.domain.intelligence.performance

import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.routing.IntelligentRouteScorer
import com.evnavigator.iran.domain.intelligence.routing.RouteCandidate
import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureNanoTime

class IntelligencePerformanceBenchmarkTest {

    private val orchestrator = PredictiveEnergyOrchestrator()
    private val scorer = IntelligentRouteScorer()

    private fun createBenchmarkContext(speed: Double): TripIntelligenceContext {
        return TripIntelligenceContext(
            vehicleId = "bench_ev",
            batteryCapacityKwh = InputQualityAttribute.verified(60.0),
            curbWeightKg = InputQualityAttribute.verified(1600.0),
            additionalPayloadKg = InputQualityAttribute.verified(100.0),
            dragCoefficient = InputQualityAttribute.verified(0.28),
            frontalAreaM2 = InputQualityAttribute.verified(2.2),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010),
            driveEfficiency = InputQualityAttribute.verified(0.90),
            currentSocPercent = InputQualityAttribute.measured(80.0, System.currentTimeMillis()),
            targetArrivalSocPercent = InputQualityAttribute.userConfigured(15.0),
            minReserveSocPercent = InputQualityAttribute.userConfigured(10.0),
            routeDistanceMeters = 50_000.0,
            routeEstimatedDurationSeconds = 1800.0,
            elevationGainMeters = InputQualityAttribute.verified(100.0),
            elevationLossMeters = InputQualityAttribute.verified(50.0),
            averageSpeedKmh = InputQualityAttribute.verified(speed),
            ambientTemperatureCelsius = InputQualityAttribute.measured(22.0, System.currentTimeMillis()),
            windSpeedMps = InputQualityAttribute.measured(2.0, System.currentTimeMillis()),
            windDirectionDegrees = InputQualityAttribute.measured(0.0, System.currentTimeMillis()),
            hvacPowerKw = InputQualityAttribute.verified(0.8),
            drivingMode = DrivingMode.NORMAL
        )
    }

    @Test
    fun benchmarkPredictiveEnergyCalculationThroughput() {
        // Warmup
        repeat(50) { orchestrator.predictTripEnergy(createBenchmarkContext(80.0)) }

        val iterations = 1000
        val ctx = createBenchmarkContext(90.0)

        val totalNanos = measureNanoTime {
            repeat(iterations) {
                orchestrator.predictTripEnergy(ctx)
            }
        }

        val totalMs = totalNanos / 1_000_000.0
        val avgMicrosPerCalc = (totalNanos / iterations) / 1000.0

        println("[BENCHMARK] $iterations Energy Predictions completed in ${String.format("%.2f", totalMs)} ms (Avg: ${String.format("%.2f", avgMicrosPerCalc)} µs/calc)")
        assertTrue("1000 energy calculations must complete well within 500ms budget, took ${totalMs}ms", totalMs < 500.0)
    }

    @Test
    fun benchmarkRouteScoringPerformance() {
        val candidates = (1..10).map { i ->
            RouteCandidate(
                routeId = "route_$i",
                distanceMeters = 50_000.0 + (i * 2000),
                durationSeconds = 1800.0 + (i * 120),
                predictedEnergyKwh = 10.0 + (i * 0.5),
                predictedArrivalSocPercent = 60.0 - (i * 1.5),
                chargingStopCount = if (i % 3 == 0) 1 else 0,
                totalChargingAndDetourMinutes = if (i % 3 == 0) 15.0 else 0.0,
                uncertaintyScore = 0.05 * i
            )
        }

        val iterations = 200
        val totalNanos = measureNanoTime {
            repeat(iterations) {
                scorer.scoreAndRankRoutes(candidates, RouteOptimizationObjective.BALANCED)
            }
        }

        val totalMs = totalNanos / 1_000_000.0
        println("[BENCHMARK] $iterations Route Scoring operations (10 candidates each) completed in ${String.format("%.2f", totalMs)} ms")
        assertTrue("Route scoring must complete in < 50ms budget, took ${totalMs}ms", totalMs < 50.0)
    }

    @Test
    fun testBoundedLruCacheCapacityAndEviction() {
        val cache = BoundedIntelligenceCache<String, String>(maxCapacity = 5)

        for (i in 1..7) {
            cache.put("key_$i", "value_$i")
        }

        assertEquals("Cache size must not exceed capacity (5)", 5, cache.currentSize)
        assertNull("Eldest entries key_1 and key_2 should have been evicted", cache.get("key_1"))
        assertNull(cache.get("key_2"))
        assertNotNull(cache.get("key_3"))
        assertNotNull(cache.get("key_7"))

        assertTrue("Cache miss count should be tracked", cache.missCount >= 2)
        assertTrue("Cache hit count should be tracked", cache.hitCount >= 2)
    }
}
