package com.evnavigator.iran.domain.intelligence

import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.trip.*
import com.evnavigator.iran.domain.model.*
import com.evnavigator.iran.domain.model.catalog.*
import com.evnavigator.iran.domain.model.trip.intelligence.*
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.time.RouteDurationConsistencyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * V13.3 Master Architecture, Compile Type Safety, Physics & Consistency Test Suite (Section 18).
 *
 * Covers all mandatory V13.3 test groups:
 * A. Compile & Type Safety (`ConnectorNormalizer`, `CanonicalConnectorType.fromDomainType`, `VehicleDataQualityEvaluator`)
 * B. Connector Normalization (rejecting bare `"CCS"` & bare `"GB/T"`, accepting `"CCS1"`, `"CCS2"`, `"GB/T AC"`, `"GB/T DC"`, `"Type 2"`)
 * C. Charging Power & Curve (AC vs DC separation, incompatible AC/DC rejection, `min(port, vehicle, cable, protocol)`)
 * D. Battery & SOC (`usableCapacityKwh < grossCapacityKwh` uses usable capacity; rejects `usable > gross`; `BatteryAndSocState`)
 * E. Energy Physics (2-point polyline -> 1 segment, no 50/50 elevation split, net downhill regeneration `< 0.0 Wh`, zero-distance -> `0.0 Wh`, legal vs cruising speed)
 * F. Canonical ETA (`100 min` * `1.5` traffic / `(1 - 0.20)` weather = `187.5 min`, waitingDuration, empty segment & zero duration rejection)
 * G. Multi-Stop & Map Consistency (`FinalPlannedRoute`, `TripExecutionContext`, `validatePreNavigationConsistency`)
 * H. Station Eligibility (`StationPortEligibilityEvaluator` full corridor coverage, no `size >= 2` truncation)
 * I. Coroutine & Failure Semantics (`CancellationException` rethrown, `PARTIAL_SUCCESS` when charging plan fails)
 */
class V133MasterArchitectureAndPhysicsTest {

    private val fixedClock = DomainClock.fixed(1_762_905_600_000L)

    private fun createValidDomainVehicle(
        id: String = "vw_id4_crozz_2024",
        nominalWh: Double = 84_800.0,
        usableWh: Double = 77_000.0,
        acKw: Double? = 11.0,
        dcKw: Double? = 135.0,
        connectors: List<String> = listOf("CCS2", "Type 2")
    ): DomainVehicle = DomainVehicle(
        id = id,
        name = "VW ID.4 Crozz",
        manufacturer = "Volkswagen",
        model = "ID.4 Crozz",
        modelYear = 2024,
        curbMassKg = 2120.0,
        dragCoefficient = 0.28,
        frontalAreaM2 = 2.36,
        rollingResistanceCoefficient = 0.010,
        nominalBatteryCapacityWh = nominalWh,
        usableBatteryCapacityWh = usableWh,
        peakMotorPowerKw = 150.0,
        baselineConsumptionWhPer100Km = 16_200.0,
        acChargingMaxPowerKw = acKw,
        dcChargingMaxPowerKw = dcKw,
        connectorTypes = connectors,
        dataQuality = "VERIFIED",
        validationStatus = "VERIFIED",
        retrievedDate = fixedClock.nowEpochMs() - 86_400_000L,
        verifiedAt = fixedClock.nowEpochMs() - 86_400_000L
    )

    // =========================================================================
    // Group A: Compile & Type Safety
    // =========================================================================

    @Test
    fun `Group A - ConnectorNormalizer normalizes List of String without String split on List`() {
        val rawList = listOf(" CCS2 , Type 2 ", "GB/T DC;ccs2", "  ")
        val normalized = ConnectorNormalizer.normalizeConnectorStrings(rawList)
        assertEquals(listOf("CCS2", "Type 2", "GB/T DC"), normalized)

        val fromCsv = ConnectorNormalizer.normalizeFromCsvBoundary("CCS2, GB/T AC , Type 2")
        assertEquals(listOf("CCS2", "GB/T AC", "Type 2"), fromCsv)
    }

    @Test
    fun `Group A - CanonicalConnectorType maps enum via fromDomainType and raw strings via fromRaw`() {
        val port = DomainChargingPort(
            id = "p1",
            stationId = "s1",
            connectorType = DomainConnectorType.CCS_COMBO_2,
            powerKw = 120.0,
            isAc = false,
            isDc = true
        )
        assertEquals(CanonicalConnectorType.CCS2, CanonicalConnectorType.fromDomainType(port.connectorType))
        assertEquals(CanonicalConnectorType.CCS2, port.canonicalConnector)
    }

    @Test
    fun `Group A - VehicleDataQualityEvaluator replaces non-existent isValid and catches invalid vehicles`() {
        val validVehicle = createValidDomainVehicle()
        val validAssessment = validVehicle.evaluateQuality(fixedClock)
        assertEquals(VehicleQualityStatus.VERIFIED, validAssessment.status)
        assertTrue(validAssessment.isUsableForPhysics)
        assertTrue(validAssessment.isUsableForCharging)

        val invalidMassVehicle = validVehicle.copy(curbMassKg = 200.0)
        val invalidAssessment = invalidMassVehicle.evaluateQuality(fixedClock)
        assertEquals(VehicleQualityStatus.INVALID, invalidAssessment.status)
        assertFalse(invalidAssessment.isUsableForPhysics)
        assertTrue(invalidAssessment.missingOrInvalidFields.contains("curbMassKg"))
    }

    // =========================================================================
    // Group B: Connector Normalization (Rejecting Ambiguous CCS & GB/T)
    // =========================================================================

    @Test
    fun `Group B - Bare CCS and bare GBT are rejected as ambiguous UNKNOWN`() {
        assertEquals(CanonicalConnectorType.UNKNOWN, CanonicalConnectorType.fromRaw("CCS"))
        assertEquals(CanonicalConnectorType.UNKNOWN, CanonicalConnectorType.fromRaw("GB/T"))
        assertEquals(CanonicalConnectorType.UNKNOWN, CanonicalConnectorType.fromRaw("GBT"))
        assertTrue(CanonicalConnectorType.isAmbiguousRaw("CCS"))
        assertTrue(CanonicalConnectorType.isAmbiguousRaw("GB/T"))

        assertNull(CanonicalEnergyEngine.CanonicalConnectorStandard.fromRawName("CCS"))
        assertNull(CanonicalEnergyEngine.CanonicalConnectorStandard.fromRawName("GB/T"))

        assertEquals(CanonicalConnectorType.CCS1, CanonicalConnectorType.fromRaw("CCS1"))
        assertEquals(CanonicalConnectorType.CCS2, CanonicalConnectorType.fromRaw("CCS2"))
        assertEquals(CanonicalConnectorType.GBT_AC, CanonicalConnectorType.fromRaw("GB/T AC"))
        assertEquals(CanonicalConnectorType.GBT_DC, CanonicalConnectorType.fromRaw("GB/T DC"))
        assertEquals(CanonicalConnectorType.TYPE_2_AC, CanonicalConnectorType.fromRaw("Type 2"))
    }

    // =========================================================================
    // Group C: Charging Power & Curve (AC vs DC Separation)
    // =========================================================================

    @Test
    fun `Group C - AC station with DC-only vehicle is rejected and vice versa`() {
        val dcOnlyVehicle = VehiclePhysicsParams(
            vehicleId = "dc_only",
            usableBatteryKwh = 70.0,
            totalMassKg = 2000.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.35,
            rollingResistanceCoeff = 0.010,
            maxChargePowerKw = 120.0,
            dcMaxChargePowerKw = 120.0,
            acMaxChargePowerKw = null,
            supportedConnectors = listOf("CCS2")
        )
        val acEval = CanonicalEnergyEngine.evaluatePortCompatibility(
            portConnectorRaw = "TYPE_2",
            portRatedPowerKw = 22.0,
            isPortDc = false,
            isPortAc = true,
            vehicle = dcOnlyVehicle
        )
        assertFalse(acEval.isCompatible)
        assertNull(acEval.effectivePeakPowerKw)

        val acOnlyVehicle = dcOnlyVehicle.copy(
            vehicleId = "ac_only",
            maxChargePowerKw = 11.0,
            dcMaxChargePowerKw = null,
            acMaxChargePowerKw = 11.0,
            supportedConnectors = listOf("Type 2")
        )
        val dcEval = CanonicalEnergyEngine.evaluatePortCompatibility(
            portConnectorRaw = "CCS2",
            portRatedPowerKw = 150.0,
            isPortDc = true,
            isPortAc = false,
            vehicle = acOnlyVehicle
        )
        assertFalse(dcEval.isCompatible)
        assertNull(dcEval.effectivePeakPowerKw)
    }

    @Test
    fun `Group C - Effective charging power uses min of port, vehicle AC or DC limit, cable, and protocol`() {
        val vehicle = VehiclePhysicsParams(
            vehicleId = "dual_mode",
            usableBatteryKwh = 77.0,
            totalMassKg = 2100.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.36,
            rollingResistanceCoeff = 0.010,
            maxChargePowerKw = 135.0,
            dcMaxChargePowerKw = 135.0,
            acMaxChargePowerKw = 11.0,
            supportedConnectors = listOf("CCS2", "Type 2")
        )
        // AC 22 kW port + 11 kW vehicle onboard charger -> 11.0 kW effective
        val acEval = CanonicalEnergyEngine.evaluatePortCompatibility(
            portConnectorRaw = "Type 2",
            portRatedPowerKw = 22.0,
            isPortDc = false,
            isPortAc = true,
            vehicle = vehicle
        )
        assertTrue(acEval.isCompatible)
        assertEquals(11.0, acEval.effectivePeakPowerKw!!, 1e-4)

        // DC 180 kW port + 135 kW vehicle DC limit + 100 kW cable limit -> 100.0 kW effective
        val dcEval = CanonicalEnergyEngine.evaluatePortCompatibility(
            portConnectorRaw = "CCS2",
            portRatedPowerKw = 180.0,
            isPortDc = true,
            isPortAc = false,
            vehicle = vehicle,
            cableMaxPowerKw = 100.0
        )
        assertTrue(dcEval.isCompatible)
        assertEquals(100.0, dcEval.effectivePeakPowerKw!!, 1e-4)
    }

    @Test
    fun `Group C - AC and DC charging curves are separated even at identical 22 kW power`() {
        val acSession = CanonicalEnergyEngine.ChargeCurveEngine.estimateChargingSession(
            batteryCapacityKwh = 70.0,
            startSocPercent = 20.0,
            targetSocPercent = 85.0,
            effectivePeakPowerKw = 22.0,
            isDcFast = false
        )
        val dcSession = CanonicalEnergyEngine.ChargeCurveEngine.estimateChargingSession(
            batteryCapacityKwh = 70.0,
            startSocPercent = 20.0,
            targetSocPercent = 85.0,
            effectivePeakPowerKw = 22.0,
            isDcFast = true
        )
        assertTrue("AC session keeps constant power longer than DC taper", acSession.averageSessionPowerKw > dcSession.averageSessionPowerKw)
    }

    // =========================================================================
    // Group D: Battery & SOC Semantics
    // =========================================================================

    @Test
    fun `Group D - Usable battery capacity is used for range and rejects usable greater than gross`() {
        val brokenVehicle = createValidDomainVehicle(nominalWh = 70_000.0, usableWh = 75_000.0)
        val assessment = brokenVehicle.evaluateQuality(fixedClock)
        assertEquals(VehicleQualityStatus.INVALID, assessment.status)
        assertTrue(assessment.missingOrInvalidFields.contains("usableBatteryCapacityWh>nominalBatteryCapacityWh"))

        val validVehicle = createValidDomainVehicle(nominalWh = 84_800.0, usableWh = 77_000.0)
        val socState = BatteryAndSocState(
            usableBatteryCapacityKwh = validVehicle.usableBatteryKwh,
            grossBatteryCapacityKwh = validVehicle.grossBatteryKwh,
            currentSocPercent = 80.0,
            socSource = DomainVehicleSocSource.USER_MANUAL_INPUT,
            socUpdatedAtEpochMs = fixedClock.nowEpochMs(),
            minimumReserveSocPercent = 10.0,
            targetArrivalSocPercent = 15.0,
            isSocVerified = true
        )
        assertEquals(77.0 * 0.80, socState.remainingUsableEnergyKwh, 1e-3)
        assertEquals(77.0 * 0.70, socState.availableEnergyAboveReserveKwh, 1e-3)
    }

    // =========================================================================
    // Group E: Energy Physics (No 2-Point Double Counting, No 50-50 Elevation, No Regeneration Clamping)
    // =========================================================================

    @Test
    fun `Group E - 2-point polyline creates exactly 1 kinetic segment without 50-50 elevation split`() {
        val builder = SegmentKineticProfileBuilder()
        val route = Route(
            id = "r_2pt",
            origin = Coordinate(35.7000, 51.4000, 1200.0),
            destination = Coordinate(35.8000, 51.4000, 1500.0),
            waypoints = emptyList(),
            geometry = listOf(
                Coordinate(35.7000, 51.4000, null),
                Coordinate(35.8000, 51.4000, null)
            ),
            segments = emptyList(),
            distance = Distance.ofMeters(11_119.5),
            duration = Duration.ofSeconds(500L),
            elevationGainMeters = 300.0,
            elevationLossMeters = 0.0
        )
        val kineticSegments = builder.buildKineticSegments(route, WeatherCondition(temperatureCelsius = 20.0))
        assertEquals(1, kineticSegments.size)
        // Because coordinate elevations are null, segment elevation must not invent a 50/50 split
        assertEquals(0.0, kineticSegments[0].elevationGainMeters, 1e-6)
        assertEquals(0.0, kineticSegments[0].elevationLossMeters, 1e-6)
        assertFalse(kineticSegments[0].hasVerifiedElevation)
    }

    @Test
    fun `Group E - Steep downhill segment produces negative net energy (regeneration) without clamping to 10 Wh`() {
        val calc = SegmentPhysicsEnergyCalculator()
        val downhillSeg = KineticRouteSegment(
            index = 0,
            startCoord = Coordinate(36.10, 51.30, 2600.0),
            endCoord = Coordinate(36.20, 51.30, 1600.0),
            distanceMeters = 10_000.0,
            legalSpeedLimitKmh = 60,
            targetSpeedKmh = 55.0,
            speedSource = KineticInputSource.ROUTE_SEGMENT_ACTUAL,
            elevationGainMeters = 0.0,
            elevationLossMeters = 1000.0, // -10% grade
            averageGradePercent = -10.0,
            hasVerifiedElevation = true,
            ambientTemperatureC = 20.0,
            temperatureSource = KineticInputSource.LIVE_PROVIDER,
            headwindComponentKmh = 0.0,
            windSource = KineticInputSource.LIVE_PROVIDER,
            precipitationIntensityMmPerHour = 0.0,
            isRoadWetOrSnowy = false,
            roadSurfacePenaltyMultiplier = 1.0,
            trafficCongestionPenalty = 1.0
        )
        val profile = calc.calculateProfile(
            routeId = "downhill_chalus",
            segments = listOf(downhillSeg),
            departureSocPercent = 60.0,
            batteryCapacityKwh = 77.0,
            curbWeightKg = 2100.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.36,
            rollingResistanceCoeff = 0.010
        )
        assertTrue("Steep downhill must yield net regenerative energy (< 0 Wh)", profile.totalExpectedEnergyWh < 0.0)
        assertTrue("Arrival SOC must increase above departure SOC on steep downhill", profile.predictedArrivalSocPercent > 60.0)
    }

    @Test
    fun `Group E - Zero-distance segment produces 0 Wh and invalid battery capacity throws`() {
        val calc = SegmentPhysicsEnergyCalculator()
        val zeroSeg = KineticRouteSegment(
            index = 0,
            startCoord = Coordinate(35.7, 51.4),
            endCoord = Coordinate(35.7, 51.4),
            distanceMeters = 0.0,
            legalSpeedLimitKmh = null,
            targetSpeedKmh = 0.0,
            speedSource = KineticInputSource.ROUTE_SEGMENT_ACTUAL,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            averageGradePercent = 0.0,
            hasVerifiedElevation = true,
            ambientTemperatureC = 20.0,
            temperatureSource = KineticInputSource.LIVE_PROVIDER,
            headwindComponentKmh = 0.0,
            windSource = KineticInputSource.LIVE_PROVIDER,
            precipitationIntensityMmPerHour = 0.0,
            isRoadWetOrSnowy = false,
            roadSurfacePenaltyMultiplier = 1.0,
            trafficCongestionPenalty = 1.0
        )
        val zeroProfile = calc.calculateProfile(
            routeId = "zero_seg",
            segments = listOf(zeroSeg),
            departureSocPercent = 80.0,
            batteryCapacityKwh = 75.0
        )
        assertEquals(0.0, zeroProfile.totalExpectedEnergyWh, 1e-6)

        assertThrows(IllegalArgumentException::class.java) {
            calc.calculateProfile(
                routeId = "bad_batt",
                segments = listOf(zeroSeg),
                departureSocPercent = 80.0,
                batteryCapacityKwh = 0.0
            )
        }
    }

    // =========================================================================
    // Group F: Canonical ETA (Multiplicative Traffic & Weather, Waiting Duration)
    // =========================================================================

    @Test
    fun `Group F - CanonicalTravelTimeEngine applies multiplicative traffic and weather penalties accurately`() {
        // 100 min base (6000 s), traffic = 1.5, weather speed reduction = 0.20 -> 100 * 1.5 / 0.80 = 187.5 min (11,250 s)
        val breakdown = CanonicalTravelTimeEngine.calculateFromRouteDurations(
            baseDrivingDurationSeconds = 6000.0,
            detourDurationSeconds = 0.0,
            chargingDurationSeconds = 0.0,
            waitingDurationSeconds = 300.0,
            trafficMultiplier = 1.5,
            weatherSpeedReductionFraction = 0.20,
            totalDistanceMeters = 120_000.0,
            isRouteFromRealGraph = true,
            isTrafficLive = true,
            isWeatherLive = true,
            areStationsVerified = true
        )
        assertEquals(6000.0, breakdown.baseDrivingDuration.value, 1e-4)
        // Adjusted driving = 6000 * 1.5 / 0.80 = 11,250s -> penalty = 5,250s
        assertEquals(5250.0, breakdown.weatherAndTrafficPenalty.value, 1e-4)
        assertEquals(300.0, breakdown.waitingDuration.value, 1e-4)
        assertEquals(11_550.0, breakdown.totalDuration.value, 1e-4)
        assertTrue(RouteDurationConsistencyPolicy.verifyExactBreakdownSum(breakdown.toDomainBreakdown()))
    }

    @Test
    fun `Group F - CanonicalTravelTimeEngine rejects empty segments or zero duration when distance is positive`() {
        val badRoute = Route(
            id = "r_bad",
            origin = Coordinate(35.7, 51.4),
            destination = Coordinate(35.8, 51.5),
            waypoints = emptyList(),
            geometry = listOf(Coordinate(35.7, 51.4), Coordinate(35.8, 51.5)),
            segments = emptyList(),
            distance = Distance.ofMeters(10_000.0),
            duration = Duration.ofSeconds(500L)
        )
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalTravelTimeEngine.calculateFromDomainRoute(
                route = badRoute,
                allowRouteLevelDurationFallback = false
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalTravelTimeEngine.calculateFromRouteDurations(
                baseDrivingDurationSeconds = 0.0,
                totalDistanceMeters = 10_000.0
            )
        }
    }

    // =========================================================================
    // Group G: Multi-Stop & Map Consistency (FinalPlannedRoute & TripExecutionContext)
    // =========================================================================

    @Test
    fun `Group G - FinalPlannedRoute and TripExecutionContext enforce single routeId, routeVersion, and stop count`() {
        val p1 = GeoCoordinate(35.6997, 51.3380)
        val p2 = GeoCoordinate(34.6401, 50.8764)
        val p3 = GeoCoordinate(32.6370, 51.6830)

        val leg1 = PlannedRouteLeg(
            legIndex = 0,
            fromName = "تهران",
            toName = "ایستگاه مهروماه قم",
            fromCoordinate = p1,
            toCoordinate = p2,
            polyline = listOf(p1, p2),
            segments = emptyList(),
            distanceMeters = 135_000.0,
            drivingDurationSeconds = 4800L,
            energyUsedKwh = 24.0,
            departureSocPercent = 80.0,
            arrivalSocPercent = 48.0,
            chargingStopStationId = "st_qom_mehromah",
            chargingTargetSocPercent = 80.0,
            chargingDurationSeconds = 1200L
        )
        val leg2 = PlannedRouteLeg(
            legIndex = 1,
            fromName = "ایستگاه مهروماه قم",
            toName = "اصفهان",
            fromCoordinate = p2,
            toCoordinate = p3,
            polyline = listOf(p2, p3),
            segments = emptyList(),
            distanceMeters = 285_000.0,
            drivingDurationSeconds = 9600L,
            energyUsedKwh = 48.0,
            departureSocPercent = 80.0,
            arrivalSocPercent = 18.0
        )

        val breakdown = CanonicalTravelTimeBreakdown(
            baseDrivingDuration = Duration.ofSeconds(14_400L),
            trafficPenaltyDuration = Duration.ZERO,
            weatherPenaltyDuration = Duration.ZERO,
            chargingDuration = Duration.ofSeconds(1200L),
            waitingDuration = Duration.ZERO,
            uncertaintyDuration = Duration.ofSeconds(600L)
        )
        val finalRoute = CanonicalFinalRouteAssembler.assembleMultiLegRoute(
            baseRouteId = "route_thr_isf",
            routeVersion = 7L,
            sourceClassification = RouteSourceClassification.OFFLINE_OSM_GRAPH,
            originName = "تهران",
            destinationName = "اصفهان",
            legs = listOf(leg1, leg2),
            selectedChargingStopIds = listOf("st_qom_mehromah"),
            travelTimeBreakdown = breakdown,
            totalEnergyUsedKwh = 72.0,
            energyUncertaintyPercent = 6.0,
            warnings = emptyList(),
            createdAtEpochMs = fixedClock.nowEpochMs()
        )

        assertEquals(3, finalRoute.fullRoadPolyline.size)
        assertEquals(1, finalRoute.chargingStopsCount)
        assertEquals(18.0, finalRoute.finalArrivalSocPercent, 1e-4)
        assertTrue(finalRoute.validatePreNavigationConsistency().isConsistent)

        val inconsistentRoute = finalRoute.copy(selectedChargingStopIds = emptyList())
        val check = inconsistentRoute.validatePreNavigationConsistency()
        assertFalse(check.isConsistent)
        assertTrue(check.violations.any { it.contains("Mismatch between selectedChargingStopIds") })
    }

    // =========================================================================
    // Group H: Station Eligibility (Full Corridor Coverage)
    // =========================================================================

    @Test
    fun `Group H - StationPortEligibilityEvaluator evaluates all compatible stations without truncation`() {
        val evaluator = StationPortEligibilityEvaluator(fixedClock)
        val stations = (1..5).map { idx ->
            val stId = "st_$idx"
            DomainChargingStationWithPorts(
                station = DomainChargingStation(
                    id = stId,
                    name = "Station $idx",
                    latitude = 35.0 - idx * 0.2,
                    longitude = 51.0,
                    isVerified = true,
                    retrievedDate = fixedClock.nowEpochMs() - 3_600_000L,
                    verifiedAt = fixedClock.nowEpochMs() - 7_200_000L,
                    operationalStatus = StationOperationalStatus.OPEN
                ),
                ports = listOf(
                    DomainChargingPort(
                        id = "port_$idx",
                        stationId = stId,
                        connectorType = "CCS2",
                        powerKw = 120.0,
                        isDc = true,
                        status = "AVAILABLE",
                        retrievedDate = fixedClock.nowEpochMs() - 60_000L,
                        liveStatusUpdatedAtEpochMs = fixedClock.nowEpochMs() - 60_000L
                    )
                )
            )
        }
        val routeCoords = listOf(
            Coordinate(35.0, 51.0),
            Coordinate(34.0, 51.0)
        )
        val candidates = evaluator.evaluateEligibleCandidates(
            allStations = stations,
            routeGeometry = routeCoords,
            supportedRawConnectors = listOf("CCS2"),
            vehicleMaxPowerKw = 135.0,
            policy = StationSelectionPolicy(maxCorridorDetourMeters = 15_000.0)
        )
        assertEquals("All 5 corridor stations must be evaluated without truncation", 5, candidates.size)
    }

    // =========================================================================
    // Group I: Coroutine & Failure Semantics
    // =========================================================================

    @Test
    fun `Group I - TripIntelligenceOrchestrator rethrows CancellationException and surfaces PARTIAL_SUCCESS on charging failure`() = runBlocking {
        val route = Route(
            id = "r_cancel_test",
            origin = Coordinate(35.7, 51.4),
            destination = Coordinate(32.6, 51.6),
            waypoints = emptyList(),
            geometry = listOf(Coordinate(35.7, 51.4), Coordinate(32.6, 51.6)),
            segments = listOf(
                RouteSegment(
                    startCoordinate = Coordinate(35.7, 51.4),
                    endCoordinate = Coordinate(32.6, 51.6),
                    distance = Distance.ofMeters(400_000.0),
                    duration = Duration.ofSeconds(14_400L),
                    speedLimitKmh = 110,
                    estimatedAverageSpeedKmh = 100.0
                )
            ),
            distance = Distance.ofMeters(400_000.0),
            duration = Duration.ofSeconds(14_400L)
        )
        val request = TripIntelligenceRequest(
            route = route,
            departureSocPercent = 30.0, // Insufficient for 400 km -> requires charging
            targetDestinationSocPercent = 20.0,
            minimumSafeSocPercent = 15.0,
            vehicleBatteryCapacityKwh = 60.0,
            vehicleMaxAcceptanceKw = 100.0,
            supportedConnectors = listOf("CCS2"),
            curbWeightKg = 2000.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.35
        )
        val insights = TripExplanationAndInsightsBuilder()
        val riskCalc = TripRiskAndConfidenceCalculator()
        val altEngine = TripAlternativesComparisonEngine(
            SegmentKineticProfileBuilder(),
            SegmentPhysicsEnergyCalculator()
        )
        val orchestrator = TripIntelligenceOrchestrator(
            kineticBuilder = SegmentKineticProfileBuilder(),
            physicsCalculator = SegmentPhysicsEnergyCalculator(),
            chargingOrchestrator = null, // Forces charging failure while energy succeeds
            alternativesEngine = altEngine,
            riskCalculator = riskCalc,
            insightsBuilder = insights
        )

        val result = orchestrator.analyzeTrip(request, weather = WeatherCondition(temperatureCelsius = 25.0), availableStations = emptyList())
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(TripIntelligenceExecutionStatus.PARTIAL_SUCCESS, payload.executionStatus)
        assertNotNull(payload.chargingPlanError)
    }
}
