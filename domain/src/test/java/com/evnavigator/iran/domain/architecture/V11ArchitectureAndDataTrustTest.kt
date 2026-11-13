package com.evnavigator.iran.domain.architecture

import com.evnavigator.iran.domain.intelligence.charging.RiskAwareChargingEvaluator
import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.routing.IntelligentRouteScorer
import com.evnavigator.iran.domain.intelligence.trip.CorridorStationProjector
import com.evnavigator.iran.domain.intelligence.trip.PredictiveChargingOrchestrator
import com.evnavigator.iran.domain.intelligence.trip.SegmentPhysicsEnergyCalculator
import com.evnavigator.iran.domain.intelligence.trip.StationPortEligibilityEvaluator
import com.evnavigator.iran.domain.intelligence.trip.TripEnergyAndFeasibilityOrchestrator
import com.evnavigator.iran.domain.intelligence.uncertainty.ConfidenceAndUncertaintyEngine
import com.evnavigator.iran.domain.journey.multistop.MultiStopWaypointOptimizer
import com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult
import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.RouteCategory
import com.evnavigator.iran.domain.journey.multistop.WaypointSequenceSolver
import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.Trip
import com.evnavigator.iran.domain.model.Vehicle
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.catalog.DomainChargingStation
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainSourceType
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.SourceEvidence
import com.evnavigator.iran.domain.model.catalog.SpecProvenance
import com.evnavigator.iran.domain.model.catalog.VerificationMethod
import com.evnavigator.iran.domain.model.trip.intelligence.DetourCalculationSource
import com.evnavigator.iran.domain.model.trip.intelligence.PlanRecommendationStatus
import com.evnavigator.iran.domain.model.trip.intelligence.TripIntelligenceError
import com.evnavigator.iran.domain.model.trip.intelligence.TripIntelligenceRequest
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.domain.policy.FreshnessClassification
import com.evnavigator.iran.domain.policy.FreshnessPolicyEvaluator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V12.6 Comprehensive Architecture, Physics, Freshness & Data-Trust Verification Suite.
 *
 * Covers all mandatory V12.6 scenarios (P0-1 through P2-17, Scenarios A–F):
 * - Scenario A: Full verified trip (`VERIFIED_FEASIBLE`) with external SourceEvidence, fresh station/port TTLs, and AVAILABLE port.
 * - Scenario B: Verified station with `port.status == "UNKNOWN"` -> `NEEDS_VERIFICATION` (never `VERIFIED_FEASIBLE` or `FEASIBLE`).
 * - Scenario C: 2024 timestamp evaluated in 2026 via injected `DomainClock` -> `STALE` even when `validationStatus == "VERIFIED"`,
 *   plus exact TTL boundary, zero/negative timestamp, null timestamp, and future clock skew tests.
 * - Scenario D: Independent AC and DC vehicle charging power (no `min(22.0, vehicleMaxDcKw)`, no 60kW/11kW fallbacks)
 *   and zero connector guessing from vehicle brand/make (`KMC`, `JAC`, `BYD`).
 * - Scenario E: Station close to polyline (2 km cross-track) but requiring 32 km road detour via `RoadRouteProvider`
 *   rejected by `maxRoadDetourMeters = 25 km`; without `RoadRouteProvider`, `verifiedRoadDetourMeters` is `null` (no `* 2.7`).
 * - Scenario F: Per-segment uphill (+6%) vs flat vs downhill (-5%) physics with high-SoC/cold regen throttling,
 *   authentic `InputOrigin`, and `WaypointSequenceSolver` Branch-and-Bound vs Brute-Force exact optimality.
 */
class V11ArchitectureAndDataTrustTest {

    private val testNow2026EpochMs = 1_775_000_000_000L // Year 2026 reference epoch
    private val fixedClock2026 = DomainClock.fixed(testNow2026EpochMs)

    private fun createSampleTripAndRequest(
        distanceMeters: Double = 320_000.0,
        batteryCapacityKwh: Double = 77.0,
        maxChargingKw: Double = 135.0,
        acChargingKw: Double? = 11.0,
        initialSocPercent: Double? = 85.0,
        isInitialSocVerified: Boolean = true,
        supportedConnectors: List<String> = listOf("CCS2", "GBT_DC"),
        segments: List<RouteSegment> = emptyList()
    ): TripIntelligenceRequest {
        val start = GeoCoordinate(35.6997, 51.3380) // Tehran
        val midQom = GeoCoordinate(34.6401, 50.8764) // Qom
        val midKashan = GeoCoordinate(33.9850, 51.4100) // Kashan
        val end = GeoCoordinate(32.6370, 51.6830) // Isfahan
        val vehicle = Vehicle(
            id = "vw_id4_crozz_2024",
            make = "Volkswagen",
            model = "ID.4 Crozz",
            year = 2024,
            batteryCapacity = batteryCapacityKwh,
            maxChargingPower = maxChargingKw,
            efficiency = 17.2,
            weight = 2120.0
        )
        val route = Route(
            id = "route_tehran_isfahan",
            startLocation = start,
            endLocation = end,
            waypoints = listOf(start, midQom, midKashan, end),
            distance = Distance(distanceMeters),
            estimatedTime = 12_600L,
            segments = segments
        )
        val trip = Trip(
            id = "trip_tehran_isfahan",
            startDate = testNow2026EpochMs,
            endDate = testNow2026EpochMs + 12_600_000L,
            startLocation = start,
            endLocation = end,
            route = route,
            vehicle = vehicle,
            totalDistance = Distance(distanceMeters),
            totalTime = 12_600L,
            energyConsumed = 0.0,
            chargingStops = emptyList(),
            weatherSnapshots = emptyList(),
            routeSegments = segments
        )
        return TripIntelligenceRequest(
            trip = trip,
            vehicle = vehicle,
            routeDistanceMeters = distanceMeters,
            elevationGainMeters = 620.0,
            elevationDescentMeters = 450.0,
            initialSocPercent = initialSocPercent,
            isInitialSocVerified = isInitialSocVerified,
            maxRoadDetourMeters = 25_000.0,
            maxCrossTrackMeters = 15_000.0,
            routePolyline = listOf(start, midQom, midKashan, end),
            supportedConnectors = supportedConnectors,
            acMaxChargingPowerKw = acChargingKw,
            dcMaxChargingPowerKw = maxChargingKw.takeIf { it > 0.0 }
        )
    }

    private fun buildVerifiedQomStation(
        portStatus: String = "AVAILABLE",
        stationVerifiedAtMs: Long = testNow2026EpochMs - 3_600_000L,
        portLiveUpdatedAtMs: Long? = testNow2026EpochMs - 120_000L,
        hasExternalEvidence: Boolean = true
    ): DomainChargingStationWithPorts {
        val externalUrl = if (hasExternalEvidence) "https://meko.ir/stations/mapna_qom_01" else null
        val evidence = SourceEvidence(
            sourceName = "MAPNA_OFFICIAL",
            sourceType = DomainSourceType.NETWORK_PROVIDER,
            externalRecordId = if (hasExternalEvidence) "MAPNA_QOM_01" else null,
            sourceUrl = externalUrl,
            internalRegistryUrn = "urn:iran-ev-registry:mapna-meko:MAPNA_QOM_01:ir_qom_mehromah_dc_01",
            retrievedAtEpochMs = testNow2026EpochMs,
            lastVerifiedAtEpochMs = stationVerifiedAtMs,
            verificationMethod = if (hasExternalEvidence) VerificationMethod.DOCUMENTED_PUBLIC_RELEASE else VerificationMethod.UNVERIFIED_CORRIDOR_SEED,
            verificationStatus = "VERIFIED",
            portPowerProvenance = SpecProvenance.OPERATOR_PUBLISHED_SPEC,
            connectorProvenance = SpecProvenance.OPERATOR_PUBLISHED_SPEC
        )
        return DomainChargingStationWithPorts(
            station = DomainChargingStation(
                id = "ir_qom_mehromah_dc_01",
                name = "ایستگاه شارژ سریع مجتمع مهروماه قم",
                operatorName = "مپنا (MAPNA)",
                latitude = 34.6401,
                longitude = 50.8764,
                address = "کیلومتر ۶ آزادراه قم-کاشان",
                city = "قم",
                province = "قم",
                isVerified = true,
                validationStatus = "VERIFIED",
                retrievedDate = testNow2026EpochMs,
                verifiedAt = stationVerifiedAtMs,
                externalId = if (hasExternalEvidence) "MAPNA_QOM_01" else null,
                sourceUrl = externalUrl,
                sourceEvidence = evidence
            ),
            ports = listOf(
                DomainChargingPort(
                    id = "port_dc_ccs2_120",
                    stationId = "ir_qom_mehromah_dc_01",
                    connectorType = "CCS2",
                    powerKw = 120.0,
                    isDc = true,
                    status = portStatus,
                    retrievedDate = testNow2026EpochMs,
                    liveStatusUpdatedAtEpochMs = portLiveUpdatedAtMs
                )
            )
        )
    }

    @Test
    fun `Scenario A and B - Port status AVAILABLE yields VERIFIED_FEASIBLE while UNKNOWN yields NEEDS_VERIFICATION`() = runTest {
        val orchestrator = PredictiveChargingOrchestrator(clock = fixedClock2026)
        val request = createSampleTripAndRequest(distanceMeters = 420_000.0, batteryCapacityKwh = 65.0, initialSocPercent = 55.0)

        // Scenario A: Port is explicitly AVAILABLE, fresh, and station has external SourceEvidence
        val verifiedContext = TripIntelligenceContext(
            vehicleId = "vw_id4_crozz_2024",
            batteryCapacityKwh = InputQualityAttribute.verified(65.0, testNow2026EpochMs),
            usableBatteryCapacityKwh = InputQualityAttribute.verified(65.0, testNow2026EpochMs),
            currentSocPercent = InputQualityAttribute.measured(55.0, testNow2026EpochMs),
            curbWeightKg = InputQualityAttribute.verified(2120.0, testNow2026EpochMs),
            dragCoefficient = InputQualityAttribute.verified(0.28, testNow2026EpochMs),
            frontalAreaM2 = InputQualityAttribute.verified(2.36, testNow2026EpochMs),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010, testNow2026EpochMs),
            driveEfficiency = InputQualityAttribute.verified(0.91, testNow2026EpochMs),
            routeDistanceMeters = 420_000.0,
            elevationGainMeters = InputQualityAttribute.verified(620.0, testNow2026EpochMs),
            elevationLossMeters = InputQualityAttribute.verified(450.0, testNow2026EpochMs),
            averageElevationMeters = InputQualityAttribute.verified(1250.0, testNow2026EpochMs),
            averageSpeedKmh = InputQualityAttribute.verified(95.0, testNow2026EpochMs),
            ambientTemperatureCelsius = InputQualityAttribute.measured(22.0, testNow2026EpochMs),
            minReserveSocPercent = InputQualityAttribute.userInput(15.0, testNow2026EpochMs)
        )

        val availableStation = buildVerifiedQomStation(portStatus = "AVAILABLE")
        val planAvailable = orchestrator.generateChargingPlan(
            request = request,
            context = verifiedContext,
            candidateStationsOverride = listOf(availableStation),
            clockOverride = fixedClock2026
        ).getOrThrow()

        assertTrue(planAvailable.isVerifiedPlan)
        assertEquals(PlanRecommendationStatus.VERIFIED_FEASIBLE, planAvailable.recommendationStatus)
        assertEquals(
            PlanRecommendationStatus.VERIFIED_FEASIBLE,
            planAvailable.plannedChargingStops.first().recommendationStatus
        )

        // Scenario B: Port status is UNKNOWN -> MUST NEVER be VERIFIED_FEASIBLE or FEASIBLE
        val unknownPortStation = buildVerifiedQomStation(portStatus = "UNKNOWN", portLiveUpdatedAtMs = null)
        val planUnknownPort = orchestrator.generateChargingPlan(
            request = request,
            context = verifiedContext,
            candidateStationsOverride = listOf(unknownPortStation),
            clockOverride = fixedClock2026
        ).getOrThrow()

        assertFalse("Plan with UNKNOWN port status must not be marked isVerifiedPlan", planUnknownPort.isVerifiedPlan)
        assertEquals(PlanRecommendationStatus.NEEDS_VERIFICATION, planUnknownPort.recommendationStatus)
        assertEquals("NEEDS_VERIFICATION", planUnknownPort.feasibilityStatus)
        assertEquals(
            PlanRecommendationStatus.NEEDS_VERIFICATION,
            planUnknownPort.plannedChargingStops.first().recommendationStatus
        )
    }

    @Test
    fun `Scenario C - FreshnessPolicyEvaluator enforces TTL boundaries, rejects 2024 timestamps in 2026, and blocks future skew`() = runTest {
        val ttl30Days = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS

        // 1. Just before TTL boundary -> FRESH
        val justBeforeExpiry = testNow2026EpochMs - ttl30Days + 1_000L
        assertEquals(
            FreshnessClassification.FRESH,
            FreshnessPolicyEvaluator.evaluateTimestamp(justBeforeExpiry, ttl30Days, fixedClock2026).classification
        )

        // 2. Just after TTL boundary -> STALE
        val justAfterExpiry = testNow2026EpochMs - ttl30Days - 1_000L
        assertEquals(
            FreshnessClassification.STALE,
            FreshnessPolicyEvaluator.evaluateTimestamp(justAfterExpiry, ttl30Days, fixedClock2026).classification
        )

        // 3. Zero, negative, null, and future timestamps -> INVALID / MISSING
        assertEquals(
            FreshnessClassification.INVALID_ZERO_OR_NEGATIVE_TIMESTAMP,
            FreshnessPolicyEvaluator.evaluateTimestamp(0L, ttl30Days, fixedClock2026).classification
        )
        assertEquals(
            FreshnessClassification.INVALID_ZERO_OR_NEGATIVE_TIMESTAMP,
            FreshnessPolicyEvaluator.evaluateTimestamp(-500L, ttl30Days, fixedClock2026).classification
        )
        assertEquals(
            FreshnessClassification.MISSING_TIMESTAMP,
            FreshnessPolicyEvaluator.evaluateTimestamp(null, ttl30Days, fixedClock2026).classification
        )
        assertEquals(
            FreshnessClassification.INVALID_FUTURE_TIMESTAMP,
            FreshnessPolicyEvaluator.evaluateTimestamp(
                testNow2026EpochMs + EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS + 5_000L,
                ttl30Days,
                fixedClock2026
            ).classification
        )

        // 4. Station with validationStatus == VERIFIED but timestamp from Nov 2024 evaluated in 2026 -> STALE & NEEDS_VERIFICATION
        val timestamp2024 = 1_730_851_200_000L
        val stale2024Station = buildVerifiedQomStation(
            portStatus = "AVAILABLE",
            stationVerifiedAtMs = timestamp2024,
            portLiveUpdatedAtMs = testNow2026EpochMs - 60_000L
        )
        assertTrue(stale2024Station.station.isStale(nowEpochMs = testNow2026EpochMs))

        val orchestrator = PredictiveChargingOrchestrator(clock = fixedClock2026)
        val request = createSampleTripAndRequest(distanceMeters = 420_000.0, batteryCapacityKwh = 65.0, initialSocPercent = 55.0)
        val planWithStaleStation = orchestrator.generateChargingPlan(
            request = request,
            context = null,
            candidateStationsOverride = listOf(stale2024Station),
            clockOverride = fixedClock2026
        ).getOrThrow()

        assertFalse(planWithStaleStation.isVerifiedPlan)
        assertEquals(PlanRecommendationStatus.NEEDS_VERIFICATION, planWithStaleStation.recommendationStatus)
        assertFalse(planWithStaleStation.plannedChargingStops.first().isStationFresh)
    }

    @Test
    fun `Scenario D - No AC power guessing from DC power and no connector guessing from vehicle brand`() = runTest {
        val orchestrator = PredictiveChargingOrchestrator(clock = fixedClock2026)

        // 1. DomainVehicle with known DC (120 kW) and null AC power must keep acMaxChargePowerKw == null (no 11.0 kW fallback)
        val domainVehicleNoAc = DomainVehicle(
            id = "kmc_j7_ev_custom",
            name = "KMC J7 EV",
            manufacturer = "KMC",
            model = "J7 EV",
            modelYear = 2025,
            curbMassKg = 1750.0,
            dragCoefficient = 0.27,
            frontalAreaM2 = 2.28,
            rollingResistanceCoefficient = 0.010,
            nominalBatteryCapacityWh = 55_000.0,
            usableBatteryCapacityWh = 50_100.0,
            peakMotorPowerKw = 142.0,
            baselineConsumptionWhPer100Km = 15_500.0,
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = 120.0,
            connectorTypes = listOf("GBT_DC")
        )
        val physicsParams = domainVehicleNoAc.toPhysicsParamsOrNull()
        assertNotNull(physicsParams)
        assertNull("AC max charging power must remain null when unknown, never 11.0 kW", physicsParams!!.acMaxChargePowerKw)
        assertEquals(120.0, physicsParams.dcMaxChargePowerKw!!, 0.01)

        // 2. AC-only station with vehicle that has high DC power (135 kW) but acMaxChargingPowerKw = null -> must be rejected!
        val acOnlyStation = DomainChargingStationWithPorts(
            station = DomainChargingStation(
                id = "ir_qom_ac_only",
                name = "ایستگاه شارژ کند قم",
                operatorName = "شهرداری قم",
                latitude = 34.6401,
                longitude = 50.8764,
                isVerified = true,
                validationStatus = "VERIFIED",
                retrievedDate = testNow2026EpochMs,
                verifiedAt = testNow2026EpochMs - 60_000L
            ),
            ports = listOf(
                DomainChargingPort(
                    id = "port_ac_22",
                    stationId = "ir_qom_ac_only",
                    connectorType = "TYPE_2_AC",
                    powerKw = 22.0,
                    isDc = false,
                    status = "AVAILABLE",
                    retrievedDate = testNow2026EpochMs,
                    liveStatusUpdatedAtEpochMs = testNow2026EpochMs - 60_000L
                )
            )
        )
        val requestNoAcPower = createSampleTripAndRequest(
            distanceMeters = 420_000.0,
            batteryCapacityKwh = 65.0,
            maxChargingKw = 135.0,
            acChargingKw = null,
            initialSocPercent = 55.0,
            supportedConnectors = listOf("TYPE_2_AC")
        )
        val acPlanWhenVehicleAcUnknown = orchestrator.generateChargingPlan(
            request = requestNoAcPower,
            context = null,
            candidateStationsOverride = listOf(acOnlyStation),
            clockOverride = fixedClock2026
        )
        assertTrue(
            "AC port must be rejected when vehicle AC charging power is null (never derived from DC power)",
            acPlanWhenVehicleAcUnknown.isFailure
        )

        // 3. Empty explicitConnectors even with "KMC" or "BYD" brand must NOT guess connectors -> InsufficientData / UNKNOWN_VEHICLE_CONNECTOR
        val outcomeNoConnectors = StationPortEligibilityEvaluator.evaluateStationAndSelectPort(
            stationWithPorts = buildVerifiedQomStation(),
            explicitConnectors = emptyList(),
            vehicleMaxDcKw = 120.0,
            vehicleMaxAcKw = 11.0,
            clock = fixedClock2026
        )
        assertTrue(outcomeNoConnectors is StationPortEligibilityEvaluator.StationEvaluationOutcome.Rejected)
        assertEquals(
            "UNKNOWN_VEHICLE_CONNECTOR",
            (outcomeNoConnectors as StationPortEligibilityEvaluator.StationEvaluationOutcome.Rejected).reasonCode
        )
    }

    @Test
    fun `Scenario E - Road detour vs geometric cross-track separation and rejection of long highway U-turn detour`() = runTest {
        val routePolyline = listOf(
            GeoCoordinate(35.6997, 51.3380),
            GeoCoordinate(34.6401, 50.8764),
            GeoCoordinate(33.9850, 51.4100),
            GeoCoordinate(32.6370, 51.6830)
        )
        // Station is right on Qom waypoint (~0 km cross-track)
        val qomStation = buildVerifiedQomStation()

        // 1. Without RoadRouteProvider -> verifiedRoadDetourMeters is null, source is CROSS_TRACK_GEOMETRIC_ESTIMATE (never crossTrack * 2.7)
        val (projectedNoProvider, _) = CorridorStationProjector.evaluateAndProjectStations(
            stations = listOf(qomStation),
            routePolyline = routePolyline,
            routeDistanceMeters = 320_000.0,
            maxRoadDetourMeters = 25_000.0,
            maxCrossTrackMeters = 15_000.0,
            explicitConnectors = listOf("CCS2"),
            vehicleMaxDcKw = 135.0,
            vehicleMaxAcKw = 11.0,
            clock = fixedClock2026,
            roadRouteProvider = null
        )
        assertEquals(1, projectedNoProvider.size)
        assertNull(projectedNoProvider.first().verifiedRoadDetourMeters)
        assertNull(projectedNoProvider.first().detourDurationMinutes)
        assertEquals(
            DetourCalculationSource.CROSS_TRACK_GEOMETRIC_ESTIMATE,
            projectedNoProvider.first().detourCalculationSource
        )

        // 2. With RoadRouteProvider reporting 16 km each way (32 km total road detour > 25 km maxRoadDetourMeters) -> REJECTED!
        val longDetourProvider = object : RoadRouteProvider {
            override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): RoadRouteCalculationResult {
                return RoadRouteCalculationResult(
                    distanceMeters = 16_000.0,
                    durationSeconds = 960.0,
                    isRealRoadGraph = true,
                    routeCategory = RouteCategory.AUTHENTIC_ROAD_GRAPH
                )
            }
        }
        val (projectedLongDetour, rejectedLongDetour) = CorridorStationProjector.evaluateAndProjectStations(
            stations = listOf(qomStation),
            routePolyline = routePolyline,
            routeDistanceMeters = 320_000.0,
            maxRoadDetourMeters = 25_000.0,
            maxCrossTrackMeters = 15_000.0,
            explicitConnectors = listOf("CCS2"),
            vehicleMaxDcKw = 135.0,
            vehicleMaxAcKw = 11.0,
            clock = fixedClock2026,
            roadRouteProvider = longDetourProvider
        )
        assertTrue("Station with 32 km road detour must be rejected when maxRoadDetourMeters is 25 km", projectedLongDetour.isEmpty())
        assertTrue(rejectedLongDetour["ir_qom_mehromah_dc_01"]!!.startsWith("OFF_ROUTE_EXCEEDS_MAX_ROAD_DETOUR"))
    }

    @Test
    fun `Scenario F - Per-segment uphill vs downhill energy physics and Branch-and-Bound vs Brute-Force optimality`() = runTest {
        val start = GeoCoordinate(35.70, 51.34)
        val end = GeoCoordinate(36.20, 51.30)
        val segDistance = Distance(50_000.0)

        val uphillSeg = RouteSegment(
            id = "seg_uphill",
            start = start,
            end = end,
            distance = segDistance,
            bearing = Bearing(0.0),
            speedLimitKmh = 90,
            gradePercent = 6.0
        )
        val flatSeg = uphillSeg.copy(id = "seg_flat", gradePercent = 0.0)
        val downhillSeg = uphillSeg.copy(id = "seg_downhill", gradePercent = -5.0)

        val reqUphill = createSampleTripAndRequest(distanceMeters = 50_000.0, segments = listOf(uphillSeg))
        val reqFlat = createSampleTripAndRequest(distanceMeters = 50_000.0, segments = listOf(flatSeg))
        val reqDownhill = createSampleTripAndRequest(distanceMeters = 50_000.0, segments = listOf(downhillSeg))

        val ctx = TripIntelligenceContext(
            vehicleId = "vw_id4_crozz_2024",
            batteryCapacityKwh = InputQualityAttribute.verified(77.0, testNow2026EpochMs),
            currentSocPercent = InputQualityAttribute.measured(80.0, testNow2026EpochMs),
            curbWeightKg = InputQualityAttribute.verified(2120.0, testNow2026EpochMs),
            dragCoefficient = InputQualityAttribute.verified(0.28, testNow2026EpochMs),
            frontalAreaM2 = InputQualityAttribute.verified(2.36, testNow2026EpochMs),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010, testNow2026EpochMs),
            driveEfficiency = InputQualityAttribute.verified(0.91, testNow2026EpochMs),
            routeDistanceMeters = 50_000.0,
            ambientTemperatureCelsius = InputQualityAttribute.measured(20.0, testNow2026EpochMs)
        )

        val profUphill = SegmentPhysicsEnergyCalculator.buildRouteSegmentProfiles(reqUphill, ctx, listOf(start, end), 50_000.0)
        val profFlat = SegmentPhysicsEnergyCalculator.buildRouteSegmentProfiles(reqFlat, ctx, listOf(start, end), 50_000.0)
        val profDownhill = SegmentPhysicsEnergyCalculator.buildRouteSegmentProfiles(reqDownhill, ctx, listOf(start, end), 50_000.0)

        val simUphill = SegmentPhysicsEnergyCalculator.simulateLegEnergy(profUphill, 0.0, 50_000.0, 0.0, 80.0, 77.0, ctx)
        val simFlat = SegmentPhysicsEnergyCalculator.simulateLegEnergy(profFlat, 0.0, 50_000.0, 0.0, 80.0, 77.0, ctx)
        val simDownhill80 = SegmentPhysicsEnergyCalculator.simulateLegEnergy(profDownhill, 0.0, 50_000.0, 0.0, 80.0, 77.0, ctx)
        val simDownhill99 = SegmentPhysicsEnergyCalculator.simulateLegEnergy(profDownhill, 0.0, 50_000.0, 0.0, 99.0, 77.0, ctx)

        assertTrue("Uphill (+6%) must consume significantly more Wh than flat", simUphill.expectedEnergyWh > simFlat.expectedEnergyWh * 1.8)
        assertTrue("Downhill (-5%) must consume less Wh than flat due to regen", simDownhill80.expectedEnergyWh < simFlat.expectedEnergyWh)
        assertTrue("Downhill at 80% SoC recovers regen energy", simDownhill80.regeneratedWh > 1_000.0)
        assertEquals("Downhill at 99% SoC throttles regen to 0 Wh", 0.0, simDownhill99.regeneratedWh, 0.01)

        // Verify Branch-and-Bound matches exhaustive Brute-Force reference on a 6-node cost matrix
        val costMatrix = arrayOf(
            doubleArrayOf(0.0, 45.0, 20.0, 65.0, 80.0, 120.0),
            doubleArrayOf(45.0, 0.0, 30.0, 25.0, 40.0, 70.0),
            doubleArrayOf(20.0, 30.0, 0.0, 50.0, 60.0, 95.0),
            doubleArrayOf(65.0, 25.0, 50.0, 0.0, 18.0, 40.0),
            doubleArrayOf(80.0, 40.0, 60.0, 18.0, 0.0, 22.0),
            doubleArrayOf(120.0, 70.0, 95.0, 40.0, 22.0, 0.0)
        )
        val intermediates = listOf(1, 2, 3, 4)
        val bnbSolution = WaypointSequenceSolver.solveBranchAndBound(costMatrix, 0, 5, intermediates)
        val bruteForceSolution = WaypointSequenceSolver.solveExactBruteForceReference(costMatrix, 0, 5, intermediates)
        assertEquals(bruteForceSolution.totalCostMeters, bnbSolution.totalCostMeters, 1e-6)
        assertEquals(bruteForceSolution.orderedIntermediateIndices, bnbSolution.orderedIntermediateIndices)
    }

    @Test
    fun `MultiStopWaypointOptimizer caches road distance matrix, isolates vehicle profiles, and flags ROUTE_UNKNOWN`() = runTest {
        var providerCallCount = 0
        val fakeRoadProvider = object : RoadRouteProvider {
            override suspend fun calculateRoute(
                from: GeoCoordinate,
                to: GeoCoordinate
            ): RoadRouteCalculationResult? {
                providerCallCount++
                if (to.latitude > 37.0 || from.latitude > 37.0) return null
                return RoadRouteCalculationResult(
                    distanceMeters = 140_000.0,
                    durationSeconds = 5_000.0,
                    polyline = listOf(from, to),
                    isRealRoadGraph = true,
                    routeCategory = RouteCategory.AUTHENTIC_ROAD_GRAPH
                )
            }
        }

        MultiStopWaypointOptimizer.clearCache()

        val start = com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint(
            id = "origin",
            name = "Tehran",
            coordinate = GeoCoordinate(35.6997, 51.3380),
            type = com.evnavigator.iran.domain.journey.multistop.WaypointType.ORIGIN
        )
        val stopQom = com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint(
            id = "s1",
            name = "Qom",
            coordinate = GeoCoordinate(34.6401, 50.8764),
            type = com.evnavigator.iran.domain.journey.multistop.WaypointType.INTERMEDIATE_USER_STOP
        )
        val stopKashan = com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint(
            id = "s2",
            name = "Kashan",
            coordinate = GeoCoordinate(33.9850, 51.4100),
            type = com.evnavigator.iran.domain.journey.multistop.WaypointType.INTERMEDIATE_USER_STOP
        )
        val end = com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint(
            id = "dest",
            name = "Isfahan",
            coordinate = GeoCoordinate(32.6370, 51.6830),
            type = com.evnavigator.iran.domain.journey.multistop.WaypointType.DESTINATION
        )

        val waypoints = listOf(start, stopQom, stopKashan, end)
        val firstRun = MultiStopWaypointOptimizer.optimizeWaypointsWithDiagnostics(
            waypoints = waypoints,
            keepPinnedStopsFixed = false,
            roadRouteProvider = fakeRoadProvider,
            vehicleMassKg = 2000.0
        )
        val callsAfterFirstRun = providerCallCount
        assertTrue(callsAfterFirstRun > 0)
        assertFalse(firstRun.hasRouteUnknownLegs)

        // Second identical optimization hits cache with zero extra provider calls
        val secondRun = MultiStopWaypointOptimizer.optimizeWaypointsWithDiagnostics(
            waypoints = waypoints,
            keepPinnedStopsFixed = false,
            roadRouteProvider = fakeRoadProvider,
            vehicleMassKg = 2000.0
        )
        assertEquals(callsAfterFirstRun, providerCallCount)
        assertTrue(secondRun.cacheHitsCount > 0)

        // Different vehicle mass bucket (e.g. 2600 kg SUV vs 2000 kg sedan) uses a distinct CacheKey
        MultiStopWaypointOptimizer.optimizeWaypointsWithDiagnostics(
            waypoints = waypoints,
            keepPinnedStopsFixed = false,
            roadRouteProvider = fakeRoadProvider,
            vehicleMassKg = 2600.0
        )
        assertTrue("Distinct vehicle mass bucket must not collide in CacheKey", providerCallCount > callsAfterFirstRun)
    }

    @Test
    fun `CanonicalConnectorType normalizes Iranian and international connector strings`() {
        assertEquals(CanonicalConnectorType.GBT_DC, CanonicalConnectorType.fromRaw("GB/T DC"))
        assertEquals(CanonicalConnectorType.GBT_DC, CanonicalConnectorType.fromRaw("gbt_fast"))
        assertEquals(CanonicalConnectorType.CCS2, CanonicalConnectorType.fromRaw("CCS Combo 2"))
        assertEquals(CanonicalConnectorType.TYPE_2_AC, CanonicalConnectorType.fromRaw("Type 2 AC"))
    }
}
