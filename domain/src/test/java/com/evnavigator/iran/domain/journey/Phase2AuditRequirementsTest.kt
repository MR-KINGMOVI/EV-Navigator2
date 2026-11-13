package com.evnavigator.iran.domain.journey

import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.charging.OperationalConfidence
import com.evnavigator.iran.domain.intelligence.charging.RiskAwareChargingEvaluator
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult
import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Verification suite for Phase 2 Audit Requirements:
 * - Real road routing engine connected (no straight-line x 1.25 fallback)
 * - Disconnected graph / no road returns explicit failure
 * - Candidate station evaluation with incompatible connector rejection
 * - Station without online status tagged UNKNOWN and safely handled
 * - Multi-stop trip planning with candidate charging stop insertion
 */
class Phase2AuditRequirementsTest {

    @Test
    fun testGraphWithoutRouteFailsExplicitlyWithoutStraightLineFallback() = runTest {
        // Mock a road route provider that finds NO route between two disconnected points
        val disconnectedProvider = object : RoadRouteProvider {
            override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): RoadRouteCalculationResult? {
                return null // Graph has no road route connecting these points
            }
        }

        val coordinator = MultiStopRouteCoordinator(roadRouteProvider = disconnectedProvider)
        val waypoints = listOf(
            JourneyWaypoint("wp1", "Island A", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "Island B", GeoCoordinate(32.6370, 51.6830), WaypointType.DESTINATION)
        )

        val result = coordinator.createPlan(
            vehicleId = "vw_id4_crozz_2024",
            batteryCapacityKwh = 77.0,
            initialSocPercent = 90.0,
            rawWaypoints = waypoints
        )

        // Must fail explicitly rather than inventing a straight-line route
        assertTrue("Planning must fail when no road network route exists in graph", result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("Error message must state no road route exists in graph",
            errorMsg.contains("No road route exists") || errorMsg.contains("road network graph"))
    }

    @Test
    fun testIncompatibleConnectorRejectedByChargingEvaluator() {
        val evaluator = RiskAwareChargingEvaluator()

        // Candidate with incompatible connector (CHAdeMO only, vehicle is CCS2)
        val incompatibleStation = CandidateStation(
            stationId = "station_chademo_only",
            stationName = "ایستگاه ژاپنی بدون CCS2",
            compatibleConnectors = listOf("CHAdeMO"),
            maxPowerKw = 50.0,
            detourDistanceMeters = 200.0,
            detourDurationSeconds = 60.0,
            distanceAlongRouteMeters = 100_000.0,
            liveStatusLastUpdatedEpochMs = System.currentTimeMillis(),
            isReportedAvailable = true
        )

        val evaluation = evaluator.evaluateStation(
            candidate = incompatibleStation,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 20.0,
            socUncertaintyDeltaPercent = 5.0
        )

        assertFalse("Candidate with incompatible connector must not be compatible", evaluation.isConnectorCompatible)
        assertTrue("Risk factors must report connector incompatibility",
            evaluation.riskFactors.any { it.contains("Incompatible connector", ignoreCase = true) })
        assertTrue("Recommendation must reject incompatible candidate",
            evaluation.recommendationSummary.contains("REJECTED"))
    }

    @Test
    fun testStationWithoutOnlineStatusHandledWithUnknownOccupancy() {
        val evaluator = RiskAwareChargingEvaluator()

        // Station with unknown live occupancy / offline reporting
        val unknownStatusStation = CandidateStation(
            stationId = "station_offline_status",
            stationName = "ایستگاه بین‌راهی بدون تله‌متری آنلاین",
            compatibleConnectors = listOf("CCS2"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 300.0,
            detourDurationSeconds = 90.0,
            distanceAlongRouteMeters = 80_000.0,
            liveStatusLastUpdatedEpochMs = null, // No recent telemetry
            isReportedAvailable = null // UNKNOWN
        )

        val evaluation = evaluator.evaluateStation(
            candidate = unknownStatusStation,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 120.0,
            nominalArrivalSocPercent = 30.0,
            socUncertaintyDeltaPercent = 5.0
        )

        assertTrue("Connector must be compatible", evaluation.isConnectorCompatible)
        assertEquals("Operational confidence must be STATIC_VERIFIED_LIVE_UNKNOWN when live telemetry is missing",
            OperationalConfidence.STATIC_VERIFIED_LIVE_UNKNOWN, evaluation.operationalConfidence)
        assertTrue("Risk factors must warn about unconfirmed operational state",
            evaluation.riskFactors.any { it.contains("unconfirmed", ignoreCase = true) })
        assertTrue("Recommendation must caution about unknown live status",
            evaluation.recommendationSummary.contains("CAUTION"))
    }

    @Test
    fun testMultiStopPlanEvaluatesCandidateChargingStopsOnUnfeasibleLeg() = runTest {
        val connectedProvider = object : RoadRouteProvider {
            override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): RoadRouteCalculationResult {
                return RoadRouteCalculationResult(
                    distanceMeters = 450_000.0,
                    durationSeconds = 18_000.0,
                    isRealRoadGraph = true
                )
            }
        }

        val candidateStation = CandidateStation(
            stationId = "station_qom_fast",
            stationName = "ایستگاه شارژ سریع قم",
            compatibleConnectors = listOf("CCS2", "GB/T_DC"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 500.0,
            detourDurationSeconds = 120.0,
            distanceAlongRouteMeters = 130_000.0,
            liveStatusLastUpdatedEpochMs = System.currentTimeMillis(),
            isReportedAvailable = true
        )

        val coordinator = MultiStopRouteCoordinator(roadRouteProvider = connectedProvider)

        // Long leg (Tehran -> Isfahan ~ 450 km) starting with only 25% battery (impossible without charging)
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "اصفهان", GeoCoordinate(32.6370, 51.6830), WaypointType.DESTINATION)
        )

        val result = coordinator.createPlan(
            vehicleId = "vw_id4_crozz_2024",
            batteryCapacityKwh = 77.0,
            initialSocPercent = 25.0,
            rawWaypoints = waypoints,
            availableChargingStations = listOf(candidateStation)
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        // Must insert or evaluate the compatible candidate charging station, or reflect unfeasibility
        assertTrue("Leg with insufficient battery must insert charging stop or mark overall unfeasible",
            plan.waypoints.any { it.type == WaypointType.CHARGING_STOP } || !plan.isOverallFeasible)
    }
}
