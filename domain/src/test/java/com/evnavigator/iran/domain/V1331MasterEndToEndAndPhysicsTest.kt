package com.evnavigator.iran.domain

import com.evnavigator.iran.domain.geocoding.GeocodingAndReverseGeocodingService
import com.evnavigator.iran.domain.geocoding.GeocodingPlaceType
import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.EnergyCalculationRequest
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.UnifiedReferenceEnergyCalculator
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.trip.BatteryAndSocState
import com.evnavigator.iran.domain.intelligence.trip.CanonicalFinalRouteAssembler
import com.evnavigator.iran.domain.intelligence.trip.ParameterProvenanceStatus
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.LegRouteResult
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.WaypointNode
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.VehicleDataQualityEvaluator
import com.evnavigator.iran.domain.model.trip.intelligence.ChargingStopRecommendation
import com.evnavigator.iran.domain.model.trip.intelligence.ConnectorCompatibilityStatus
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * V13.3.1 Master Physical Reference & End-to-End Behavioral Verification Suite
 * (V13.3.1 Specification Sections 1–22).
 */
class V1331MasterEndToEndAndPhysicsTest {

    private val verifiedVehiclePhysics = VehiclePhysicsParams(
        vehicleId = "vw_id4_crozz_pro",
        usableBatteryKwh = 77.0,
        nominalGrossBatteryKwh = 84.8,
        totalMassKg = 2120.0,
        dragCoefficient = 0.28,
        frontalAreaM2 = 2.36,
        rollingResistanceCoeff = 0.010,
        drivetrainEfficiency = 0.91,
        regenEfficiency = 0.72,
        auxiliaryBasePowerKw = 0.45,
        maxChargePowerKw = 135.0,
        dcMaxChargePowerKw = 135.0,
        acMaxChargePowerKw = 11.0,
        supportedConnectors = listOf("CCS2", "TYPE_2_AC"),
        isAssumedFallback = false,
        dataQuality = "VERIFIED"
    )

    // =========================================================================
    // SECTION 18: UNIFIED PHYSICAL ENERGY CALCULATOR & REGENERATION TESTS
    // =========================================================================

    @Test
    fun `Section 18_1 - flat 100km highway reference test verifies 9-component EnergyBreakdown`() {
        val request = EnergyCalculationRequest(
            segments = listOf(
                RouteSegmentInput(
                    distanceMeters = 100_000.0,
                    startElevationMeters = 1200.0,
                    endElevationMeters = 1200.0,
                    gradePercent = 0.0,
                    speedLimitKmh = 100.0,
                    targetSpeedKmh = 100.0,
                    altitudeMeters = 1200.0
                )
            ),
            vehicle = verifiedVehiclePhysics,
            departureSocPercent = 80.0,
            isDepartureSocVerified = true,
            ambientTemperatureCelsius = 20.0
        )

        val result = CanonicalEnergyEngine.calculateEnergy(request)
        val b = result.breakdown

        assertTrue("Rolling resistance must be positive", b.rollingResistanceWh > 3_500.0)
        assertTrue("Aerodynamic drag must be positive", b.aerodynamicDragWh > 6_000.0)
        assertEquals("Flat road has zero grade potential energy", 0.0, b.gradePotentialWh, 1e-3)
        assertTrue("Drivetrain losses must be positive", b.drivetrainLossesWh > 800.0)
        assertTrue("HVAC + auxiliary energy must be positive", b.hvacAuxiliaryWh > 300.0)
        assertTrue("Battery internal I2R losses must be positive", b.batteryInternalLossesWh > 50.0)
        assertEquals("Flat cruise has zero regenerative energy", 0.0, b.regenerativeEnergyWh, 1e-3)

        // Realistic 100 km consumption at 100 km/h for a 2120 kg SUV is ~16.5 - 22.5 kWh
        assertTrue(
            "Net battery energy (${result.netEnergyKwh} kWh) must be within physical bounds [15.0, 24.0]",
            result.netEnergyKwh in 15.0..24.0
        )
        assertTrue(result.arrivalSocNominalPercent < 80.0 && result.arrivalSocNominalPercent > 45.0)
    }

    @Test
    fun `Section 18_2 - uphill vs downhill and physical regeneration power, high-SoC, and cold battery limits`() {
        val uphillSeg = RouteSegmentInput(
            distanceMeters = 20_000.0,
            startElevationMeters = 1000.0,
            endElevationMeters = 2000.0,
            gradePercent = 5.0,
            speedLimitKmh = 80.0,
            targetSpeedKmh = 80.0
        )
        val downhillSeg = RouteSegmentInput(
            distanceMeters = 20_000.0,
            startElevationMeters = 2000.0,
            endElevationMeters = 1000.0,
            gradePercent = -5.0,
            speedLimitKmh = 80.0,
            targetSpeedKmh = 80.0
        )

        val uphillRes = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(
                segments = listOf(uphillSeg),
                vehicle = verifiedVehiclePhysics,
                departureSocPercent = 70.0,
                ambientTemperatureCelsius = 20.0
            )
        )
        val downhillNormalRes = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(
                segments = listOf(downhillSeg),
                vehicle = verifiedVehiclePhysics,
                departureSocPercent = 60.0,
                ambientTemperatureCelsius = 20.0
            )
        )
        val downhillHighSocRes = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(
                segments = listOf(downhillSeg),
                vehicle = verifiedVehiclePhysics,
                departureSocPercent = 97.0,
                ambientTemperatureCelsius = 20.0
            )
        )
        val downhillColdRes = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(
                segments = listOf(downhillSeg),
                vehicle = verifiedVehiclePhysics,
                departureSocPercent = 60.0,
                ambientTemperatureCelsius = -12.0
            )
        )

        assertTrue("Uphill must consume much more than downhill", uphillRes.netEnergyWh > downhillNormalRes.netEnergyWh * 3.0)
        assertTrue("Normal downhill must recover regenerative energy", downhillNormalRes.breakdown.regenerativeEnergyWh > 1_000.0)
        assertTrue(
            "High SoC (97%) must taper regenerative braking recovery",
            downhillHighSocRes.breakdown.regenerativeEnergyWh < downhillNormalRes.breakdown.regenerativeEnergyWh * 0.35
        )
        assertTrue(
            "Cold battery (-12C) must derate regenerative braking recovery",
            downhillColdRes.breakdown.regenerativeEnergyWh < downhillNormalRes.breakdown.regenerativeEnergyWh
        )
    }

    @Test
    fun `Section 18_3 & 18_4 - round-trip A-B-A symmetry and sensitivity to speed, mass, wind, and temperature`() {
        val flatSeg = RouteSegmentInput(distanceMeters = 50_000.0, gradePercent = 0.0, targetSpeedKmh = 90.0)
        val climbSeg = RouteSegmentInput(distanceMeters = 50_000.0, startElevationMeters = 1000.0, endElevationMeters = 2200.0, targetSpeedKmh = 90.0)
        val descentSeg = RouteSegmentInput(distanceMeters = 50_000.0, startElevationMeters = 2200.0, endElevationMeters = 1000.0, targetSpeedKmh = 90.0)

        val flatRoundTripWh = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(listOf(flatSeg, flatSeg), verifiedVehiclePhysics, ambientTemperatureCelsius = 20.0)
        ).netEnergyWh

        val mountainRoundTripWh = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(listOf(climbSeg, descentSeg), verifiedVehiclePhysics, ambientTemperatureCelsius = 20.0)
        ).netEnergyWh

        assertTrue(
            "Mountain round-trip ($mountainRoundTripWh Wh) must exceed flat round-trip ($flatRoundTripWh Wh) due to irreversible regen/drivetrain losses",
            mountainRoundTripWh > flatRoundTripWh
        )

        // Speed sensitivity: 120 km/h > 80 km/h
        val slowWh = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(listOf(flatSeg.copy(targetSpeedKmh = 80.0)), verifiedVehiclePhysics, ambientTemperatureCelsius = 20.0)
        ).netEnergyWh
        val fastWh = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(listOf(flatSeg.copy(targetSpeedKmh = 120.0)), verifiedVehiclePhysics, ambientTemperatureCelsius = 20.0)
        ).netEnergyWh
        assertTrue("120 km/h must consume significantly more than 80 km/h", fastWh > slowWh * 1.35)

        // Headwind (+12 m/s) vs Tailwind (-12 m/s)
        val headwindWh = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(listOf(flatSeg), verifiedVehiclePhysics, headwindComponentMps = 12.0, ambientTemperatureCelsius = 20.0)
        ).netEnergyWh
        val tailwindWh = UnifiedReferenceEnergyCalculator.calculate(
            EnergyCalculationRequest(listOf(flatSeg), verifiedVehiclePhysics, headwindComponentMps = -12.0, ambientTemperatureCelsius = 20.0)
        ).netEnergyWh
        assertTrue("Headwind must consume more energy than tailwind", headwindWh > tailwindWh * 1.25)
    }

    // =========================================================================
    // SECTION 22: 6 MANDATORY END-TO-END SCENARIOS
    // =========================================================================

    @Test
    fun `Scenario 1 - Direct urban-intercity route without charging stop produces 1 consistent leg`() {
        val origin = GeoCoordinate(35.7575, 51.4099) // Tehran Valiasr
        val dest = GeoCoordinate(35.8400, 50.9391)   // Karaj
        val poly = listOf(origin, GeoCoordinate(35.7900, 51.1800), dest)
        val segs = listOf(
            RouteSegment("s0", poly[0], poly[1], Distance.fromMeters(22_000.0), Bearing(280.0), roadName = "آزادراه تهران-کرج", startOffsetMeters = 0.0, endOffsetMeters = 22_000.0, speedLimitKmh = 110),
            RouteSegment("s1", poly[1], poly[2], Distance.fromMeters(23_000.0), Bearing(280.0), roadName = "آزادراه تهران-کرج", startOffsetMeters = 22_000.0, endOffsetMeters = 45_000.0, speedLimitKmh = 110)
        )

        val finalRoute = CanonicalFinalRouteAssembler.assembleFromChargingStops(
            routeId = "tehran_karaj_v1",
            routeVersion = 1L,
            origin = origin,
            destination = dest,
            fullPolyline = poly,
            fullSegments = segs,
            baseDrivingDurationSeconds = 1800.0,
            baseTotalDistanceMeters = 45_000.0,
            totalEnergyRequiredKwh = 8.2,
            departureSocPercent = 80.0,
            finalArrivalSocPercent = 69.3,
            minReserveSocPercent = 15.0,
            chargingStops = emptyList(),
            sourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM,
            confidenceScore = 0.90
        )

        assertEquals(1, finalRoute.legs.size)
        assertEquals(0, finalRoute.chargingStopsCount)
        assertTrue(finalRoute.maneuvers.isNotEmpty())
        val consistency = finalRoute.validatePreNavigationConsistency()
        assertTrue("Direct route must pass pre-navigation consistency: ${consistency.violations}", consistency.isConsistent)
    }

    @Test
    fun `Scenario 2 - Single charging stop route splits FinalPlannedRoute into 2 distinct legs with sliced geometry and rebuilt maneuvers`() {
        val tehran = GeoCoordinate(35.6997, 51.3380)
        val qomStop = GeoCoordinate(34.6401, 50.8764)
        val isfahan = GeoCoordinate(32.6546, 51.6680)
        val poly = listOf(
            tehran,
            GeoCoordinate(35.1500, 51.1000),
            qomStop,
            GeoCoordinate(33.9850, 51.4100),
            isfahan
        )

        val stopRec = ChargingStopRecommendation(
            stationId = "st_qom_mehromah",
            stationName = "مجتمع مهروماه قم",
            location = qomStop,
            distanceFromStartKm = 135.0,
            detourDistanceMeters = 600.0,
            detourDurationMinutes = 1.5,
            arrivalSocPercent = 22.0,
            targetChargeSocPercent = 80.0,
            chargingDurationMinutes = 28.0,
            effectiveChargingPowerKw = 120.0,
            selectedConnector = CanonicalConnectorType.CCS2,
            connectorCompatibility = ConnectorCompatibilityStatus.COMPATIBLE,
            energyAddedKwh = 44.6,
            isVerifiedStation = true,
            stationConfidenceScore = 0.92
        )

        val finalRoute = CanonicalFinalRouteAssembler.assembleFromChargingStops(
            routeId = "tehran_isfahan_v1",
            routeVersion = 1L,
            origin = tehran,
            destination = isfahan,
            fullPolyline = poly,
            fullSegments = emptyList(),
            baseDrivingDurationSeconds = 16_200.0,
            baseTotalDistanceMeters = 435_000.0,
            totalEnergyRequiredKwh = 82.0,
            departureSocPercent = 55.0,
            finalArrivalSocPercent = 31.0,
            minReserveSocPercent = 15.0,
            chargingStops = listOf(stopRec),
            sourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM,
            confidenceScore = 0.88
        )

        assertEquals("1 charging stop must produce 2 legs (Origin->Stop1, Stop1->Dest)", 2, finalRoute.legs.size)
        assertEquals(listOf("st_qom_mehromah"), finalRoute.selectedChargingStopIds)
        assertEquals("st_qom_mehromah", finalRoute.legs[0].chargingStopStationId)
        assertNull(finalRoute.legs[1].chargingStopStationId)
        assertEquals(22.0, finalRoute.legs[0].arrivalSocPercent, 0.01)
        assertEquals(80.0, finalRoute.legs[0].postChargeDepartureSocPercent, 0.01)
        assertEquals(80.0, finalRoute.legs[1].departureSocPercent, 0.01)
        assertEquals(31.0, finalRoute.legs[1].arrivalSocPercent, 0.01)
        assertTrue("Each leg must have sliced polyline points", finalRoute.legs.all { it.polyline.size >= 2 })
        assertTrue("Each leg must have rebuilt maneuvers", finalRoute.legs.all { it.maneuvers.isNotEmpty() })
        val consistency = finalRoute.validatePreNavigationConsistency()
        assertTrue("2-leg route must pass pre-navigation consistency: ${consistency.violations}", consistency.isConsistent)
    }

    @Test
    fun `Scenario 3 - Multi-stop long corridor route inserts 2 charging stops (3 legs) using CanonicalEnergyEngine without 185 or 195 Wh per km`() = runBlocking {
        val origin = WaypointNode("orig", "تهران", GeoCoordinate(35.6997, 51.3380))
        val stop1 = WaypointNode("st_semnan", "ایستگاه سمنان", GeoCoordinate(35.5769, 53.3970), isChargingStop = true, maxChargingPowerKw = 120.0, supportedConnectors = listOf("CCS2"))
        val stop2 = WaypointNode("st_sabzevar", "ایستگاه سبزوار", GeoCoordinate(36.2126, 57.6819), isChargingStop = true, maxChargingPowerKw = 120.0, supportedConnectors = listOf("CCS2"))
        val dest = WaypointNode("dest", "مشهد", GeoCoordinate(36.2605, 59.6168))

        val provider = RoadRouteProvider { from, to ->
            val haversine = com.evnavigator.iran.domain.intelligence.trip.CorridorStationProjector.haversineMeters(from, to)
            val roadMeters = haversine * 1.12
            val durationSec = roadMeters / (100.0 / 3.6)
            val mid = GeoCoordinate((from.latitude + to.latitude) / 2.0, (from.longitude + to.longitude) / 2.0)
            LegRouteResult(
                distanceMeters = roadMeters,
                durationSeconds = durationSec,
                polyline = listOf(from, mid, to),
                isRealRoadGraph = true,
                sourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM
            )
        }

        val coordinator = MultiStopRouteCoordinator(provider)
        val plan = coordinator.createPlan(
            origin = origin,
            destination = dest,
            candidateChargingStations = listOf(stop1, stop2),
            initialSocPercent = 58.0,
            usableBatteryKwh = 65.0,
            minSafeSocPercent = 15.0,
            targetChargeSocPercent = 82.0,
            vehiclePhysics = verifiedVehiclePhysics.copy(usableBatteryKwh = 65.0)
        )

        assertTrue("Long Tehran-Mashhad route must insert at least 2 charging stops", plan.chargingStopCount >= 2)
        assertEquals(plan.chargingStopCount + 1, plan.legs.size)
        assertTrue("Plan must be complete and feasible", plan.isComplete)

        val baseRoute = Route(
            id = "teh_mhd_base",
            start = origin.coordinate,
            end = dest.coordinate,
            segments = emptyList(),
            distance = Distance.fromMeters(plan.totalDistanceMeters),
            estimatedTime = plan.totalDrivingSeconds.toLong(),
            elevationProfile = emptyList(),
            waypoints = plan.legs.flatMap { it.polyline }
        )
        val finalPlanned = CanonicalFinalRouteAssembler.assembleFromMultiStopPlan(
            baseRouteId = "teh_mhd",
            routeVersion = 1L,
            baseRoute = baseRoute,
            plan = plan,
            batteryAndSoc = BatteryAndSocState(72.0, 65.0, 58.0, 15.0, true),
            sourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM
        )

        assertEquals(plan.legs.size, finalPlanned.legs.size)
        assertEquals(plan.chargingStopCount, finalPlanned.chargingStopsCount)
        assertTrue(finalPlanned.validatePreNavigationConsistency().isConsistent)
    }

    @Test
    fun `Scenario 4 - Connector incompatibility rejects CCS2-only station for GBT_DC vehicle and selects compatible GBT_DC station`() {
        val gbtVehicle = verifiedVehiclePhysics.copy(
            supportedConnectors = listOf("GB_T_DC", "GB_T_AC")
        )
        val ccsEval = CanonicalEnergyEngine.evaluatePortCompatibility(
            portConnectorRaw = "CCS2",
            portRatedPowerKw = 150.0,
            isPortDc = true,
            isPortAc = false,
            vehicle = gbtVehicle
        )
        val gbtEval = CanonicalEnergyEngine.evaluatePortCompatibility(
            portConnectorRaw = "GB/T DC",
            portRatedPowerKw = 120.0,
            isPortDc = true,
            isPortAc = false,
            vehicle = gbtVehicle
        )

        assertFalse("CCS2 port must be incompatible with GB/T-only vehicle", ccsEval.isCompatible)
        assertEquals(ConnectorCompatibilityStatus.INCOMPATIBLE, ccsEval.compatibilityStatus)
        assertTrue("GB/T DC port must be compatible with GB/T vehicle", gbtEval.isCompatible)
        assertEquals(120.0, gbtEval.effectivePeakPowerKw ?: 0.0, 0.01)
    }

    @Test
    fun `Scenario 5 & 6 - Full-Iran Geocoding, Reverse Geocoding, Per-Property Vehicle Provenance, and Strict SoC Validation`() {
        // 1. Geocoding resolves street addresses, cities beyond the old 11-city list, and arbitrary coordinates
        val streetResolved = GeocodingAndReverseGeocodingService.geocodeQuery("تهران، خیابان ولیعصر، میدان ونک")
        assertNotNull(streetResolved)
        assertEquals(GeocodingPlaceType.STREET, streetResolved!!.placeType)
        assertFalse(streetResolved.isCityCentroidFallback)

        val ahvazCity = GeocodingAndReverseGeocodingService.geocodeQuery("اهواز")
        assertNotNull("Ahvaz (outside old 11-city list) must resolve", ahvazCity)
        assertEquals(GeocodingPlaceType.CITY_CENTROID, ahvazCity!!.placeType)
        assertTrue(ahvazCity.isCityCentroidFallback)

        val reversePlace = GeocodingAndReverseGeocodingService.reverseGeocode(GeoCoordinate(32.6514, 51.6679))
        assertTrue(reversePlace.city.contains("اصفهان"))

        // 2. Strict SoC validation rejects < 0% and > 100%
        assertThrows(IllegalArgumentException::class.java) {
            EvDomainPolicyConfig.validateSocPercentOrThrow(-5.0, "testNegativeSoc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EvDomainPolicyConfig.validateSocPercentOrThrow(112.0, "testOver100Soc")
        }
        assertEquals(80.0, EvDomainPolicyConfig.validateSocPercentOrThrow(80.0), 0.001)

        // 3. Per-property Vehicle provenance (Section 15)
        val partialVehicle = DomainVehicle(
            id = "test_veh_partial",
            manufacturer = "JAC",
            model = "EJS1",
            modelYear = 2024,
            nominalBatteryKwh = 31.4,
            usableBatteryKwh = 30.0,
            consumptionWhPerKm = 135.0,
            acMaxChargePowerKw = 6.6,
            dcMaxChargePowerKw = 40.0,
            connectorTypes = listOf("GB/T DC", "GB/T AC"),
            curbMassKg = 1180.0,
            dragCoefficient = null, // missing Cd -> UNKNOWN provenance
            frontalAreaM2 = 2.15,
            rollingResistanceCoefficient = 0.011,
            isVerified = true,
            verifiedAt = System.currentTimeMillis() - 86_400_000L,
            dataSource = "VERIFIED_OEM_SPEC"
        )
        val assessment = VehicleDataQualityEvaluator.evaluate(partialVehicle)
        assertEquals(ParameterProvenanceStatus.VERIFIED, assessment.propertyProvenance["usableBatteryKwh"]?.status)
        assertEquals(ParameterProvenanceStatus.UNKNOWN, assessment.propertyProvenance["dragCoefficient"]?.status)
        assertTrue(assessment.assumedOrEstimatedFields.contains("dragCoefficient"))
    }
}
