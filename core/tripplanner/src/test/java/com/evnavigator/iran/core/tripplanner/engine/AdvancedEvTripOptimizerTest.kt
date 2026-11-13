package com.evnavigator.iran.core.tripplanner.engine

import org.junit.Assert.*
import org.junit.Test

class AdvancedEvTripOptimizerTest {

    private val testVehicle = AdvancedEvTripOptimizer.VehiclePhysicsParams(
        curbMassKg = 1600.0,
        payloadMassKg = 150.0,
        dragCoefficient = 0.28,
        frontalAreaM2 = 2.2,
        rollingResistanceCoeff = 0.010,
        usableBatteryCapacityKwh = 60.0,
        maxDcChargePowerKw = 120.0
    )

    // Helper to generate a flat route of N kilometers (each segment 10 km)
    private fun generateRoute(totalKm: Int, speedKmh: Double = 90.0): List<AdvancedEvTripOptimizer.TopoSegment> {
        val segmentCount = totalKm / 10
        return (0 until segmentCount).map {
            AdvancedEvTripOptimizer.TopoSegment(
                distanceMeters = 10_000.0,
                startElevationMeters = 1200.0,
                endElevationMeters = 1200.0,
                targetSpeedKmh = speedKmh
            )
        }
    }

    private fun createStation(id: String, name: String, km: Double, powerKw: Double = 100.0): AdvancedEvTripOptimizer.OptimizedChargingStop {
        return AdvancedEvTripOptimizer.OptimizedChargingStop(
            stationId = id,
            stationName = name,
            locationCoordinate = Pair(35.0, 51.0),
            distanceAlongRouteKm = km,
            arrivalSocPercent = 0.0,
            targetDepartureSocPercent = 80.0,
            energyChargedKwh = 0.0,
            chargingDurationMinutes = 0.0,
            maxChargePowerKw = powerKw,
            estimatedCostToman = 0.0,
            amenitiesSummary = "Restroom, Cafe"
        )
    }

    @Test
    fun test400KmRouteWithCorridorStationsRequiresStopAndMaintainsSafeSoc() {
        val segments = generateRoute(400)
        val stations = listOf(
            createStation("st120", "Station 120km", 120.0),
            createStation("st240", "Station 240km", 240.0),
            createStation("st330", "Station 330km", 330.0)
        )

        val plan = AdvancedEvTripOptimizer.optimizeMultiStopJourney(
            segments = segments,
            vehicle = testVehicle,
            initialSocPercent = 90.0,
            minSafeSocPercent = 15.0,
            candidateStations = stations
        )

        assertTrue("400 km trip with stations must be feasible", plan.isFeasible)
        assertTrue("At least one charging stop must be scheduled", plan.chargingStops.isNotEmpty())
        assertTrue(
            "Minimum en-route SOC must stay >= minSafeSoc (15%), was: ${plan.minEnRouteSocPercent}",
            plan.minEnRouteSocPercent >= 15.0
        )
        assertTrue(
            "Final arrival SOC must be >= minSafeSoc (15%), was: ${plan.arrivalSocPercent}",
            plan.arrivalSocPercent >= 15.0
        )
    }

    @Test
    fun testRouteWithoutStationsAndInsufficientBatteryIsFeasibleFalse() {
        val segments = generateRoute(400) // ~400 km requires ~65 kWh, vehicle has only 60 kWh starting at 70% (~42 kWh)

        val plan = AdvancedEvTripOptimizer.optimizeMultiStopJourney(
            segments = segments,
            vehicle = testVehicle,
            initialSocPercent = 70.0,
            minSafeSocPercent = 15.0,
            candidateStations = emptyList() // No stations available
        )

        assertFalse("Route without stations and insufficient battery must not be feasible", plan.isFeasible)
        assertTrue("Warnings must explain missing station", plan.warnings.any { it.contains("در دسترس نیست") })
    }

    @Test
    fun testStationBehindVehicleIsNeverSelected() {
        val segments = generateRoute(400)
        // Stations at km 50, 150, 280
        val stations = listOf(
            createStation("st50", "Station 50km", 50.0),
            createStation("st150", "Station 150km", 150.0),
            createStation("st280", "Station 280km", 280.0)
        )

        val plan = AdvancedEvTripOptimizer.optimizeMultiStopJourney(
            segments = segments,
            vehicle = testVehicle,
            initialSocPercent = 90.0,
            minSafeSocPercent = 15.0,
            candidateStations = stations
        )

        // After stopping at km 150, st50 (km 50) must never be selected
        val stopStations = plan.chargingStops.map { it.distanceAlongRouteKm }
        assertTrue("Stop distances must be strictly increasing", stopStations.zipWithNext().all { it.first < it.second })
        assertFalse("Station at km 50 should not be selected when vehicle can reach km 150 safely", stopStations.contains(50.0))
    }

    @Test
    fun testChargingLossIncreasesGridCost() {
        val segments = generateRoute(400)
        val stations = listOf(
            createStation("st200", "Station 200km", 200.0)
        )

        val planWithLosses = AdvancedEvTripOptimizer.optimizeMultiStopJourney(
            segments = segments,
            vehicle = testVehicle,
            initialSocPercent = 80.0,
            candidateStations = stations,
            economicsConfig = AdvancedEvTripOptimizer.EconomicsConfig(chargerEfficiency = 0.92)
        )

        val planNoLosses = AdvancedEvTripOptimizer.optimizeMultiStopJourney(
            segments = segments,
            vehicle = testVehicle,
            initialSocPercent = 80.0,
            candidateStations = stations,
            economicsConfig = AdvancedEvTripOptimizer.EconomicsConfig(chargerEfficiency = 1.00)
        )

        assertTrue(
            "Cost with 8% charging loss (${planWithLosses.estimatedTotalChargingCostToman}) must be higher than without loss (${planNoLosses.estimatedTotalChargingCostToman})",
            planWithLosses.estimatedTotalChargingCostToman > planNoLosses.estimatedTotalChargingCostToman
        )
    }

    @Test
    fun testPassSummitsDetectionUsesExplicitOrGenericLabel() {
        val segments = listOf(
            AdvancedEvTripOptimizer.TopoSegment(
                distanceMeters = 10_000.0,
                startElevationMeters = 2300.0,
                endElevationMeters = 2600.0,
                targetSpeedKmh = 60.0,
                passLabel = "گردنه دیزین (ارتفاع ۲۶۰۰ متر)"
            ),
            AdvancedEvTripOptimizer.TopoSegment(
                distanceMeters = 10_000.0,
                startElevationMeters = 2500.0,
                endElevationMeters = 2550.0,
                targetSpeedKmh = 60.0,
                passLabel = null // Should use generic label
            )
        )

        val plan = AdvancedEvTripOptimizer.optimizeMultiStopJourney(
            segments = segments,
            vehicle = testVehicle,
            candidateStations = emptyList()
        )

        assertTrue(plan.passSummitsCrossed.contains("گردنه دیزین (ارتفاع ۲۶۰۰ متر)"))
        assertTrue(plan.passSummitsCrossed.any { it.contains("گردنهٔ مرتفع") })
    }
}
