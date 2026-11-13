package com.evnavigator.iran.domain.intelligence

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.DefaultEnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.InputProvenance
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.route.RouteIntegrityValidator
import com.evnavigator.iran.domain.intelligence.trip.CanonicalTravelTimeEngine
import com.evnavigator.iran.domain.intelligence.trip.SegmentPhysicsEnergyCalculator
import com.evnavigator.iran.domain.intelligence.trip.StationPortEligibilityEvaluator
import com.evnavigator.iran.domain.intelligence.uncertainty.ConfidenceAndUncertaintyEngine
import com.evnavigator.iran.domain.intelligence.uncertainty.QualitativeUncertainty
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.catalog.DomainChargingStation
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.StationCatalogDeduplicator
import com.evnavigator.iran.domain.model.trip.intelligence.ConnectorCompatibilityStatus
import com.evnavigator.iran.domain.model.trip.intelligence.PlanRecommendationStatus
import com.evnavigator.iran.domain.model.units.KilowattHours
import com.evnavigator.iran.domain.model.units.Meters
import com.evnavigator.iran.domain.model.units.Seconds
import com.evnavigator.iran.domain.policy.DomainClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V13.1 Master Behavioral Regression Test Suite covering Sections 1–12 of the
 * EV Navigator Iran V13.1 Master Fix Specification.
 */
class V131MasterBehavioralAuditTest {

    private val energyService = DefaultEnergyPredictionService()
    private val uncertaintyEngine = ConfidenceAndUncertaintyEngine()

    @Test
    fun `Section 2 - Empty or unrecognized connectors return UNKNOWN and missing AC or DC vehicle power never falls back to 11kW or defaultMaxPowerKw`() {
        // 1. Empty vehicle connector list -> UNKNOWN (never COMPATIBLE)
        val emptyVehicleEval = CanonicalEnergyEngine.ConnectorCompatibilityEngine.evaluatePortCompatibility(
            supportedVehicleConnectors = emptyList(),
            rawPortConnector = "CCS2",
            portMaxPowerKw = 120.0,
            portIsDc = true,
            vehicleDcMaxKw = 100.0,
            vehicleAcMaxKw = 11.0
        )
        assertEquals(ConnectorCompatibilityStatus.UNKNOWN, emptyVehicleEval.compatibilityStatus)
        assertFalse(emptyVehicleEval.canProduceDeterministicDuration)
        assertNull(emptyVehicleEval.effectivePeakPowerKw)

        // 2. Unknown port connector string -> UNKNOWN with rawPortConnector preserved
        val unknownPortEval = CanonicalEnergyEngine.ConnectorCompatibilityEngine.evaluatePortCompatibility(
            supportedVehicleConnectors = listOf("CCS2", "PROPRIETARY_IRAN_PLUG_X"),
            rawPortConnector = "PROPRIETARY_PORT_Z99",
            portMaxPowerKw = 60.0,
            portIsDc = true,
            vehicleDcMaxKw = 100.0,
            vehicleAcMaxKw = 11.0
        )
        assertEquals(ConnectorCompatibilityStatus.UNKNOWN, unknownPortEval.compatibilityStatus)
        assertEquals("PROPRIETARY_PORT_Z99", unknownPortEval.rawPortConnector)
        assertTrue(unknownPortEval.unrecognizedVehicleConnectors.contains("PROPRIETARY_IRAN_PLUG_X"))

        // 3. Compatible AC port (Type 2, 22 kW) with vehicleAcMaxKw = null and vehicleDcMaxKw = 150 kW
        // Must NOT fall back to 11.0 kW or use DC power!
        val missingAcPowerEval = CanonicalEnergyEngine.ConnectorCompatibilityEngine.evaluatePortCompatibility(
            supportedVehicleConnectors = listOf("Type 2", "CCS2"),
            rawPortConnector = "TYPE_2",
            portMaxPowerKw = 22.0,
            portIsDc = false,
            vehicleDcMaxKw = 150.0,
            vehicleAcMaxKw = null
        )
        assertEquals(ConnectorCompatibilityStatus.COMPATIBLE, missingAcPowerEval.compatibilityStatus)
        assertNull("Must not invent 11.0 kW AC fallback when vehicleAcMaxKw is null", missingAcPowerEval.effectivePeakPowerKw)
        assertFalse(missingAcPowerEval.canProduceDeterministicDuration)

        // 4. Compatible DC port (CCS2, 180 kW) with vehicleDcMaxKw = null and vehicleAcMaxKw = 11 kW
        // Must NOT fall back to standard.defaultMaxPowerKw (350 kW) or AC power!
        val missingDcPowerEval = CanonicalEnergyEngine.ConnectorCompatibilityEngine.evaluatePortCompatibility(
            supportedVehicleConnectors = listOf("CCS2", "Type 2"),
            rawPortConnector = "CCS2",
            portMaxPowerKw = 180.0,
            portIsDc = true,
            vehicleDcMaxKw = null,
            vehicleAcMaxKw = 11.0
        )
        assertEquals(ConnectorCompatibilityStatus.COMPATIBLE, missingDcPowerEval.compatibilityStatus)
        assertNull("Must not invent defaultMaxPowerKw fallback when vehicleDcMaxKw is null", missingDcPowerEval.effectivePeakPowerKw)
        assertFalse(missingDcPowerEval.canProduceDeterministicDuration)
    }

    @Test
    fun `Section 3 - Missing or invalid usable battery capacity returns INSUFFICIENT_VEHICLE_DATA and never substitutes 60kWh`() {
        val sampleRoute = RouteProfileInput(
            segments = listOf(
                RouteSegmentInput(
                    distanceMeters = 50_000.0,
                    startElevationMeters = 1200.0,
                    endElevationMeters = 1350.0,
                    speedLimitKmh = 110.0,
                    targetSpeedKmh = 100.0
                )
            )
        )

        val invalidBatteryValues = listOf(0.0, -15.0, Double.NaN, Double.POSITIVE_INFINITY)
        for (invalidKwh in invalidBatteryValues) {
            val vehicle = VehiclePhysicsParams(
                vehicleId = "veh_invalid",
                usableBatteryKwh = invalidKwh,
                nominalGrossBatteryKwh = null
            )
            val pred = energyService.predict(sampleRoute, vehicle, EnvironmentalConditions(isDepartureSocVerified = true))
            assertEquals("INSUFFICIENT_VEHICLE_DATA", pred.dataQuality)
            assertFalse(pred.isFeasibleWithoutCharging)
            assertTrue(pred.predictedArrivalSocPercent.isNaN())
            assertTrue(pred.assumptions.contains("INSUFFICIENT_VEHICLE_DATA"))
            assertEquals(InputProvenance.UNKNOWN, pred.inputProvenance["batteryCapacity"])
        }

        // Gross-only battery capacity derives usable only as ESTIMATED and never marks deterministic feasibility
        val grossOnlyVehicle = VehiclePhysicsParams(
            vehicleId = "veh_gross_only",
            usableBatteryKwh = Double.NaN,
            nominalGrossBatteryKwh = 80.0
        )
        val grossPred = energyService.predict(sampleRoute, grossOnlyVehicle, EnvironmentalConditions(isDepartureSocVerified = true))
        assertEquals("ESTIMATED", grossPred.dataQuality)
        assertFalse("Gross-derived battery capacity must not claim deterministic feasibility without charging", grossPred.isFeasibleWithoutCharging)
        assertTrue(grossPred.assumptions.contains("ASSUMED_USABLE_FROM_GROSS_BATTERY"))
        assertEquals(InputProvenance.ESTIMATED, grossPred.inputProvenance["batteryCapacity"])
    }

    @Test
    fun `Section 3 & 4 - ConfidenceAndUncertaintyEngine with unknown battery capacity reports VERY_HIGH_UNCERTAINTY without 60kWh fallback`() {
        val sampleRoute = RouteProfileInput(
            segments = listOf(RouteSegmentInput(distanceMeters = 40_000.0, startElevationMeters = 1000.0, endElevationMeters = 1000.0, speedLimitKmh = 100.0, targetSpeedKmh = 95.0))
        )
        val pred = energyService.predict(
            sampleRoute,
            VehiclePhysicsParams.insufficientVehicleData(),
            EnvironmentalConditions()
        )
        val contextWithMissingBattery = TripIntelligenceContext(
            vehicleId = "unknown_ev",
            batteryCapacityKwh = InputQualityAttribute.unknown("Missing battery capacity"),
            curbWeightKg = InputQualityAttribute.assumed(1900.0, "Assumed mass"),
            dragCoefficient = InputQualityAttribute.assumed(0.28, "Assumed Cd"),
            frontalAreaM2 = InputQualityAttribute.assumed(2.3, "Assumed A"),
            rollingResistanceCoeff = InputQualityAttribute.assumed(0.010, "Assumed Crr"),
            driveEfficiency = InputQualityAttribute.assumed(0.88, "Assumed eff"),
            currentSocPercent = InputQualityAttribute.assumed(80.0, "Assumed SoC"),
            minReserveSocPercent = InputQualityAttribute.userConfigured(15.0),
            routeDistanceMeters = 40_000.0,
            averageSpeedKmh = InputQualityAttribute.assumed(95.0, "Assumed speed")
        )

        val assessment = uncertaintyEngine.evaluateUncertainty(pred, contextWithMissingBattery)
        assertEquals(QualitativeUncertainty.VERY_HIGH_UNCERTAINTY, assessment.qualitativeLevel)
        assertTrue(assessment.primaryRiskFactors.any { it.contains("MISSING_BATTERY_CAPACITY") })
    }

    @Test
    fun `Section 5 & 6 - RouteIntegrityValidator separates legal speedLimitKmh from operational speed and detects reversed or mismatched geometry`() {
        // 1. Missing legal speed limit stays null (UNKNOWN) while operational speed is DERIVED_FROM_ROUTE_TIME
        val resolution = RouteIntegrityValidator.resolveSegmentSpeedWithProvenance(
            rawSpeedLimitKmh = 0,
            explicitTargetSpeedKmh = null,
            segmentDistanceMeters = 25_000.0,
            segmentDurationSeconds = 1_000.0, // 25 m/s = 90 km/h
            fallbackReferenceSpeedKmh = 85.0
        )
        assertNull("Missing legal speed limit must remain null, never 90 or 100", resolution.legalSpeedLimitKmh)
        assertEquals(InputOrigin.UNKNOWN, resolution.legalSpeedLimitOrigin)
        assertEquals(90.0, resolution.operationalSpeedKmh, 0.01)
        assertEquals(InputOrigin.DERIVED_FROM_ROUTE_TIME, resolution.operationalSpeedOrigin)

        // 2. Reversed route geometry detection (first point at destination, last point at origin)
        val tehran = GeoCoordinate(35.6892, 51.3890)
        val qom = GeoCoordinate(34.6416, 50.8746)
        val reversedGeometry = listOf(
            qom,
            GeoCoordinate(35.1000, 51.1000),
            tehran
        )
        val reversedCheck = RouteIntegrityValidator.validateRouteGeometryAndEndpoints(
            requestedOrigin = tehran,
            requestedDestination = qom,
            geometry = reversedGeometry,
            reportedDistanceMeters = 132_000.0
        )
        assertFalse(reversedCheck.isValid)
        assertEquals(RouteIntegrityValidator.ValidationReasonCode.ROUTE_GEOMETRY_REVERSED, reversedCheck.reasonCode)

        // 3. Discontinuous segments detection
        val seg1 = RouteSegment(
            startCoordinate = tehran,
            endCoordinate = GeoCoordinate(35.3000, 51.2000),
            distance = Distance.fromMeters(50_000.0),
            speedLimitKmh = 0
        )
        val seg2Discontinuous = RouteSegment(
            startCoordinate = GeoCoordinate(34.9000, 50.9500), // > 40 km gap from seg1.endCoordinate!
            endCoordinate = qom,
            distance = Distance.fromMeters(35_000.0),
            speedLimitKmh = 0
        )
        val discontinuousRoute = Route(
            id = "r_disc",
            origin = tehran,
            destination = qom,
            segments = listOf(seg1, seg2Discontinuous),
            distance = Distance.fromMeters(85_000.0),
            estimatedTime = 3600L
        )
        val domainCheck = RouteIntegrityValidator.validateDomainRoute(
            route = discontinuousRoute,
            requestedOrigin = tehran,
            requestedDestination = qom
        )
        assertFalse(domainCheck.isValid)
        assertEquals(RouteIntegrityValidator.ValidationReasonCode.DISCONTINUOUS_SEGMENTS, domainCheck.reasonCode)
    }

    @Test
    fun `Section 7 - StationPortEligibilityEvaluator boundary conditions around minReserveSoc and port power eligibility`() {
        val nowMs = 1_775_000_000_000L
        val fixedClock = DomainClock.fixed(nowMs)
        val station = DomainChargingStation(
            id = "st_boundary",
            name = "ایستگاه مهروماه قم",
            operatorName = "مپنا",
            latitude = 34.6401,
            longitude = 50.8764,
            isVerified = true,
            retrievedDate = nowMs - 3_600_000L,
            verifiedAt = nowMs - 86_400_000L,
            externalId = "MAPNA_023",
            sourceUrl = "https://meko.ir/stations/mapna_023"
        )
        val port = DomainChargingPort(
            id = "p1",
            stationId = "st_boundary",
            connectorType = "CCS2",
            powerKw = 120.0,
            isDc = true,
            status = "AVAILABLE",
            retrievedDate = nowMs - 60_000L,
            liveStatusUpdatedAtEpochMs = nowMs - 60_000L
        )
        val swp = DomainChargingStationWithPorts(station, listOf(port))
        val outcome = StationPortEligibilityEvaluator.evaluateStationAndSelectPort(
            stationWithPorts = swp,
            explicitConnectors = listOf("CCS2"),
            vehicleMaxDcKw = 100.0,
            vehicleAcMaxKw = 11.0,
            clock = fixedClock
        )
        assertTrue(outcome is StationPortEligibilityEvaluator.StationEvaluationOutcome.Eligible)
        val sel = (outcome as StationPortEligibilityEvaluator.StationEvaluationOutcome.Eligible).selection

        // Boundary 1: arrivalSoc = minReserveSoc - 1.0 -> INFEASIBLE
        val belowReserve = StationPortEligibilityEvaluator.determineStopRecommendationStatus(
            selection = sel,
            isSocAuthoritative = true,
            arrivalSocNominalPercent = 14.0,
            arrivalSocMinPercent = 12.0,
            minReserveSocPercent = 15.0
        )
        assertEquals(PlanRecommendationStatus.INFEASIBLE, belowReserve)

        // Boundary 2: arrivalSoc == minReserveSoc (exact boundary) with worst-case bound >= minReserveSoc -> VERIFIED_FEASIBLE
        val exactReserve = StationPortEligibilityEvaluator.determineStopRecommendationStatus(
            selection = sel,
            isSocAuthoritative = true,
            arrivalSocNominalPercent = 15.0,
            arrivalSocMinPercent = 15.0,
            minReserveSocPercent = 15.0
        )
        assertEquals(PlanRecommendationStatus.VERIFIED_FEASIBLE, exactReserve)

        // Boundary 3: arrivalSoc = minReserveSoc + 1.0 but worst-case min bound < minReserveSoc -> ESTIMATED_FEASIBLE
        val uncertainBound = StationPortEligibilityEvaluator.determineStopRecommendationStatus(
            selection = sel,
            isSocAuthoritative = true,
            arrivalSocNominalPercent = 16.0,
            arrivalSocMinPercent = 13.5,
            minReserveSocPercent = 15.0
        )
        assertEquals(PlanRecommendationStatus.ESTIMATED_FEASIBLE, uncertainBound)
    }

    @Test
    fun `Section 8 - StationCatalogDeduplicator merges duplicate stations preserving external SourceEvidence and ports`() {
        val nowMs = 1_775_000_000_000L
        val verifiedPrimary = DomainChargingStationWithPorts(
            station = DomainChargingStation(
                id = "station_tehran_milad",
                name = "ایستگاه شارژ سریع برج میلاد",
                operatorName = "مپنا",
                latitude = 35.7448,
                longitude = 51.3753,
                isVerified = true,
                validationStatus = "VERIFIED",
                confidence = 0.85,
                retrievedDate = nowMs,
                verifiedAt = nowMs - 100_000L,
                externalId = "MAPNA_001",
                sourceUrl = "https://meko.ir/stations/mapna_001"
            ),
            ports = listOf(
                DomainChargingPort("p_ccs2", "station_tehran_milad", "CCS2", 60.0, isDc = true, status = "AVAILABLE")
            )
        )
        val unverifiedDuplicateNearby = DomainChargingStationWithPorts(
            station = DomainChargingStation(
                id = "station_tehran_milad_dup",
                name = "ایستگاه برج میلاد",
                operatorName = "مپنا",
                latitude = 35.7449,
                longitude = 51.3754,
                isVerified = false,
                validationStatus = "PARTIALLY_VERIFIED",
                confidence = 0.60,
                retrievedDate = nowMs,
                verifiedAt = null,
                externalId = "MAPNA_001",
                sourceUrl = null
            ),
            ports = listOf(
                DomainChargingPort("p_type2", "station_tehran_milad_dup", "TYPE_2", 22.0, isDc = false, status = "UNKNOWN")
            )
        )

        val merged = StationCatalogDeduplicator.deduplicateAndMergeDomainStations(
            listOf(unverifiedDuplicateNearby, verifiedPrimary)
        )
        assertEquals(1, merged.size)
        val survivor = merged.first()
        assertEquals("VERIFIED", survivor.station.validationStatus)
        assertTrue(survivor.station.hasTraceableExternalEvidence)
        assertEquals("https://meko.ir/stations/mapna_001", survivor.station.sourceUrl)
        assertEquals("Both DC and AC ports from duplicate records must be preserved", 2, survivor.ports.size)
        assertTrue(survivor.ports.any { it.canonicalConnector == CanonicalConnectorType.CCS2 })
        assertTrue(survivor.ports.any { it.canonicalConnector == CanonicalConnectorType.TYPE_2_AC })
    }

    @Test
    fun `Section 9 - CanonicalTravelTimeEngine rejects negative or non-finite inputs and produces consistent breakdown`() {
        val validProfiles = listOf(
            SegmentPhysicsEnergyCalculator.SegmentKineticProfile(
                lengthMeters = 90_000.0,
                gradeFraction = 0.01,
                speedKmh = 90.0, // 25 m/s -> 3600s
                altitudeMeters = 1200.0,
                speedOrigin = InputOrigin.NETWORK_PROVIDER
            )
        )
        val validBreakdown = CanonicalTravelTimeEngine.computeTripDuration(
            profiles = validProfiles,
            totalDetourMeters = Meters(900.0), // 900m at 12.5 m/s = 72s
            totalChargingDuration = Seconds(1_200.0),
            chargingStopCount = 1,
            weatherSpeedReductionRatio = 0.10,
            trafficCongestionMultiplier = 1.15
        )
        assertTrue(validBreakdown.isValid)
        assertNull(validBreakdown.validationErrorCode)
        assertEquals(3600.0, validBreakdown.baseDrivingDuration.value, 0.1)
        assertEquals(180.0, validBreakdown.chargingOverheadDuration.value, 0.1)
        val expectedSum = validBreakdown.baseDrivingDuration.value +
            validBreakdown.trafficDelayDuration.value +
            validBreakdown.weatherPenaltyDuration.value +
            validBreakdown.detourDrivingDuration.value +
            validBreakdown.chargingSessionDuration.value +
            validBreakdown.chargingOverheadDuration.value
        assertEquals(expectedSum, validBreakdown.expectedTotalDuration.value, 0.01)
        assertTrue(validBreakdown.optimisticTotalDuration.value < validBreakdown.expectedTotalDuration.value)
        assertTrue(validBreakdown.pessimisticTotalDuration.value > validBreakdown.expectedTotalDuration.value)

        // Negative or NaN inputs must return isValid = false
        val negativeCharging = CanonicalTravelTimeEngine.computeTripDuration(
            profiles = validProfiles,
            totalChargingDuration = Seconds(-60.0)
        )
        assertFalse(negativeCharging.isValid)
        assertNotNull(negativeCharging.validationErrorCode)

        val nanSpeedProfile = listOf(
            SegmentPhysicsEnergyCalculator.SegmentKineticProfile(
                lengthMeters = 10_000.0,
                gradeFraction = 0.0,
                speedKmh = Double.NaN,
                altitudeMeters = null,
                speedOrigin = InputOrigin.ESTIMATED_MODEL
            )
        )
        val nanResult = CanonicalTravelTimeEngine.computeTripDuration(profiles = nanSpeedProfile)
        assertFalse(nanResult.isValid)
    }

    @Test
    fun `Section 1 & 2 - ChargeCurveEngine simulateChargingSessionOrNull returns null when effective power is null`() {
        val noPowerSession = CanonicalEnergyEngine.ChargeCurveEngine.simulateChargingSessionOrNull(
            usableCapacityKwh = KilowattHours(75.0),
            startSocPercent = 20.0,
            targetSocPercent = 80.0,
            effectiveMaxPowerKw = null,
            ambientTempC = 25.0
        )
        assertNull(noPowerSession)
    }
}
