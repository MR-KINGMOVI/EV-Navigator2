package com.evnavigator.iran.domain.journey

import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.GeoCoordinate
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MultiStopRouteCoordinatorTest {

    private lateinit var coordinator: MultiStopRouteCoordinator

    @Before
    fun setUp() {
        coordinator = MultiStopRouteCoordinator(roadRouteProvider = TestMockRoadRouteProvider())
    }

class TestMockRoadRouteProvider : com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider {
    override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult? {
        if (from.latitude !in 24.0..40.5 || from.longitude !in 43.5..64.5 ||
            to.latitude !in 24.0..40.5 || to.longitude !in 43.5..64.5) {
            return null
        }
        // Specific unmapped / severed regions
        if (to.latitude == 30.5000 && to.longitude == 58.8000) {
            return null
        }
        val dist = haversineM(from, to)
        val polyline = listOf(from, to)
        val segments = listOf(
            com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput(
                distanceMeters = dist,
                startElevationMeters = 1100.0,
                endElevationMeters = 1200.0,
                speedLimitKmh = 110.0
            )
        )
        return com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult(
            distanceMeters = dist,
            durationSeconds = dist / 25.0,
            isRealRoadGraph = true,
            routeCategory = com.evnavigator.iran.domain.journey.multistop.RouteCategory.AUTHENTIC_ROAD_GRAPH,
            segments = segments,
            polyline = polyline
        )
    }

    private fun haversineM(c1: GeoCoordinate, c2: GeoCoordinate): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(c1.latitude)) * kotlin.math.cos(Math.toRadians(c2.latitude)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}


    @Test
    fun testRejectsSingleWaypoint() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "Tehran", GeoCoordinate(35.6892, 51.3890), WaypointType.ORIGIN)
        )
        val result = coordinator.createPlan("skywell_et5", 72.0, 90.0, waypoints)
        assertTrue(result.isFailure)
    }

    @Test
    fun testRejectsCoordinatesOutsideIran() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "Tehran", GeoCoordinate(35.6892, 51.3890), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "Paris", GeoCoordinate(48.8566, 2.3522), WaypointType.DESTINATION)
        )
        val result = coordinator.createPlan("skywell_et5", 72.0, 90.0, waypoints)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("outside Iran") == true)
    }

    @Test
    fun testSuccessfulMultiStopPlan() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران (آزادی)", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم (مهروماه)", GeoCoordinate(34.6401, 50.8764), WaypointType.CHARGING_STOP, targetDepartureSocPercent = 85.0),
            JourneyWaypoint("wp3", "کاشان", GeoCoordinate(33.9850, 51.4100), WaypointType.INTERMEDIATE_USER_STOP),
            JourneyWaypoint("wp4", "اصفهان (پل خواجو)", GeoCoordinate(32.6370, 51.6830), WaypointType.DESTINATION)
        )

        val result = coordinator.createPlan(
            vehicleId = "skywell_et5",
            batteryCapacityKwh = 72.0,
            initialSocPercent = 90.0,
            rawWaypoints = waypoints
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertEquals(4, plan.waypoints.size)
        assertEquals(3, plan.legs.size)
        assertEquals(WaypointType.ORIGIN, plan.waypoints.first().type)
        assertEquals(WaypointType.DESTINATION, plan.waypoints.last().type)
        assertTrue("Total distance should be greater than 300 km", plan.totalDistanceMeters > 300_000.0)
        assertTrue("Plan must aggregate full real polyline geometry", plan.fullPolyline.isNotEmpty())
        for (leg in plan.legs) {
            assertTrue("Leg ${leg.legIndex} must have authentic non-empty polyline", leg.polyline.isNotEmpty())
            assertTrue("Leg ${leg.legIndex} must have real road segments", leg.segments.isNotEmpty())
            assertTrue("Leg distance must be positive", leg.distanceMeters > 0.0)
        }
    }

    @Test
    fun testOutOfCoverageLocationFailsWithRouteCoverageException() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "کویر لوت نامتعارف", GeoCoordinate(30.5000, 58.8000), WaypointType.DESTINATION)
        )

        val result = coordinator.createPlan(
            vehicleId = "skywell_et5",
            batteryCapacityKwh = 72.0,
            initialSocPercent = 90.0,
            rawWaypoints = waypoints
        )

        assertTrue("Routing to out-of-coverage coordinates must fail", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exception must be RouteCoverageException", ex is com.evnavigator.iran.domain.journey.multistop.RouteCoverageException)
        assertTrue(ex?.message?.contains("عدم پوشش شبکه معابر") == true)
    }

    @Test
    fun testDisconnectedOrSeveredNetworkFailsExplicitly() = runTest {
        // RoadRouteProvider that simulates a severed/disconnected bridge
        val severedProvider = object : com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider {
            override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult? {
                // Return null simulating severed graph
                return null
            }
        }
        val severedCoordinator = MultiStopRouteCoordinator(roadRouteProvider = severedProvider)
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.DESTINATION)
        )

        val result = severedCoordinator.createPlan("skywell_et5", 72.0, 90.0, waypoints)
        assertTrue("Severed network must fail route creation", result.isFailure)
        assertTrue(result.exceptionOrNull() is com.evnavigator.iran.domain.journey.multistop.RouteCoverageException)
    }

    @Test
    fun testEstimatedGeodesicRouteIsRejectedAsNotReal() = runTest {
        // Provider returning synthetic ESTIMATED_GEODESIC
        val fakeProvider = object : com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider {
            override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult {
                return com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult(
                    distanceMeters = 150_000.0,
                    durationSeconds = 6000.0,
                    isRealRoadGraph = false,
                    routeCategory = com.evnavigator.iran.domain.journey.multistop.RouteCategory.ESTIMATED_GEODESIC,
                    segments = emptyList(),
                    polyline = emptyList()
                )
            }
        }
        val customCoordinator = MultiStopRouteCoordinator(roadRouteProvider = fakeProvider)
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.DESTINATION)
        )

        val result = customCoordinator.createPlan("skywell_et5", 72.0, 90.0, waypoints)
        assertTrue("ESTIMATED_GEODESIC must never be accepted as real road route in production", result.isFailure)
        assertTrue(result.exceptionOrNull() is com.evnavigator.iran.domain.journey.multistop.RouteCoverageException)
    }

    @Test
    fun testVehicleWithoutPhysicalSpecsFlagsAssumedWarning() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران (آزادی)", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم (مهروماه)", GeoCoordinate(34.6401, 50.8764), WaypointType.DESTINATION)
        )

        val fallbackPhysics = com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams(
            vehicleId = "unknown_car",
            usableBatteryKwh = 60.0,
            totalMassKg = 1900.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.3,
            rollingResistanceCoeff = 0.010,
            isAssumedFallback = true
        )

        val result = coordinator.createPlan(
            vehicleId = "unknown_car",
            batteryCapacityKwh = 60.0,
            initialSocPercent = 90.0,
            rawWaypoints = waypoints,
            vehiclePhysics = fallbackPhysics
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertTrue("Plan must contain warning about assumed physical specs", plan.warnings.any { it.contains("مشخصات فرضی") || it.contains("ASSUMED") })
    }

    @Test
    fun testContinuousPolylineStitchingWithoutDuplicateJoints() = runTest {
        val pTehran = GeoCoordinate(35.6997, 51.3380)
        val pQom = GeoCoordinate(34.6401, 50.8764)
        val pKashan = GeoCoordinate(33.9850, 51.4100)

        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", pTehran, WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم", pQom, WaypointType.INTERMEDIATE_USER_STOP),
            JourneyWaypoint("wp3", "کاشان", pKashan, WaypointType.DESTINATION)
        )

        val result = coordinator.createPlan("skywell_et5", 72.0, 90.0, waypoints)
        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertEquals(2, plan.legs.size)

        // Verify no two consecutive points in fullPolyline are identical
        for (i in 0 until plan.fullPolyline.size - 1) {
            val curr = plan.fullPolyline[i]
            val next = plan.fullPolyline[i + 1]
            assertFalse("Polyline must not contain consecutive duplicate joint coordinates", curr == next)
        }
    }

    @Test
    fun testOptimizeStopOrderMinimizesDistanceWhileFixingEndpoints() = runTest {
        val tehran = JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN)
        val isfahan = JourneyWaypoint("wp2", "اصفهان", GeoCoordinate(32.6370, 51.6830), WaypointType.INTERMEDIATE_USER_STOP, isRequired = false)
        val qom = JourneyWaypoint("wp3", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.INTERMEDIATE_USER_STOP, isRequired = false)
        val kashan = JourneyWaypoint("wp4", "کاشان", GeoCoordinate(33.9850, 51.4100), WaypointType.INTERMEDIATE_USER_STOP, isRequired = false)
        val shiraz = JourneyWaypoint("wp5", "شیراز", GeoCoordinate(29.5918, 52.5837), WaypointType.DESTINATION)

        // Suboptimal zig-zag input order: Tehran -> Isfahan -> Qom -> Kashan -> Shiraz
        val unoptimized = listOf(tehran, isfahan, qom, kashan, shiraz)
        val optimized = coordinator.optimizeStopOrder(unoptimized, keepPinnedStopsFixed = false)

        assertEquals("Origin must remain at index 0", "wp1", optimized.first().id)
        assertEquals("Destination must remain at last index", "wp5", optimized.last().id)
        assertEquals(5, optimized.size)

        // Optimal geographic order heading south from Tehran to Shiraz is Qom -> Kashan -> Isfahan
        assertEquals("Qom should be first intermediate stop", "wp3", optimized[1].id)
        assertEquals("Kashan should be second intermediate stop", "wp4", optimized[2].id)
        assertEquals("Isfahan should be third intermediate stop", "wp2", optimized[3].id)
    }

    @Test
    fun testOptimizeStopOrderRespectsPinnedWaypoints() = runTest {
        val origin = JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN)
        val pinnedStop = JourneyWaypoint("wp2", "کاشان (توقف اجباری جلسه)", GeoCoordinate(33.9850, 51.4100), WaypointType.INTERMEDIATE_USER_STOP, isRequired = true)
        val flexibleStop = JourneyWaypoint("wp3", "قم (خرید سوغات اختیاری)", GeoCoordinate(34.6401, 50.8764), WaypointType.INTERMEDIATE_USER_STOP, isRequired = false)
        val dest = JourneyWaypoint("wp4", "اصفهان", GeoCoordinate(32.6370, 51.6830), WaypointType.DESTINATION)

        val unoptimized = listOf(origin, pinnedStop, flexibleStop, dest)
        val optimized = coordinator.optimizeStopOrder(unoptimized, keepPinnedStopsFixed = true)

        assertEquals("Origin remains index 0", "wp1", optimized[0].id)
        assertEquals("Destination remains last index", "wp4", optimized.last().id)
        assertEquals(4, optimized.size)
    }

    @Test
    fun testIncompatibleConnectorChargingStopFlaggedUnfeasibleAndWarned() = runTest {
        val gbtOnlyVehicle = com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams(
            vehicleId = "kmc_ej7",
            usableBatteryKwh = 50.1,
            maxChargePowerKw = 50.0,
            supportedConnectors = listOf("GB/T")
        )
        val ccs2OnlyStation = com.evnavigator.iran.domain.intelligence.charging.CandidateStation(
            stationId = "st_ccs2_only",
            stationName = "ایستگاه فقط CCS2",
            compatibleConnectors = listOf("CCS2"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 400.0,
            detourDurationSeconds = 60.0,
            distanceAlongRouteMeters = 60_000.0
        )
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "ایستگاه CCS2", GeoCoordinate(34.6401, 50.8764), WaypointType.CHARGING_STOP, stationDetails = ccs2OnlyStation),
            JourneyWaypoint("wp3", "اصفهان", GeoCoordinate(32.6370, 51.6830), WaypointType.DESTINATION)
        )

        val result = coordinator.createPlan(
            vehicleId = "kmc_ej7",
            batteryCapacityKwh = 50.1,
            initialSocPercent = 85.0,
            rawWaypoints = waypoints,
            vehiclePhysics = gbtOnlyVehicle
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertFalse("Plan with incompatible connector stop must not be marked overall feasible", plan.isOverallFeasible)
        assertTrue("Plan warnings must report connector incompatibility", plan.warnings.any { it.contains("ناسازگار") })
    }

    @Test
    fun testVehicleMaxChargePowerCapsChargingDurationAndAddsHandshakeOverhead() {
        val estimator = com.evnavigator.iran.domain.journey.multistop.ChargingTimeEstimator()
        // Station is 150 kW, but vehicle only accepts 50 kW (e.g., KMC EJ7)
        val cappedMinutes = estimator.estimateChargingMinutes(
            arrivalSocPercent = 20.0,
            targetSocPercent = 80.0,
            batteryCapacityKwh = 50.0,
            maxStationPowerKw = 150.0,
            maxVehiclePowerKw = 50.0,
            includeHandshakeOverhead = true
        )
        val uncappedMinutes = estimator.estimateChargingMinutes(
            arrivalSocPercent = 20.0,
            targetSocPercent = 80.0,
            batteryCapacityKwh = 50.0,
            maxStationPowerKw = 150.0,
            maxVehiclePowerKw = 150.0,
            includeHandshakeOverhead = false
        )

        assertTrue("Vehicle power cap (50 kW vs 150 kW) + 3 min handshake must take significantly longer", cappedMinutes > uncappedMinutes + 20.0)
    }

    @Test
    fun testPlanContextValidationDetectsVehicleOrSocMismatch() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.DESTINATION)
        )
        val plan = coordinator.createPlan("vw_id4_crozz", 77.0, 85.0, waypoints).getOrThrow()

        assertTrue("Plan must be valid for matching vehicle and departure SoC", plan.isValidForContext("vw_id4_crozz", 85.0))
        assertFalse("Plan must be invalidated when vehicle changes", plan.isValidForContext("kmc_ej7", 85.0))
        assertFalse("Plan must be invalidated when departure SoC shifts by > 2%", plan.isValidForContext("vw_id4_crozz", 70.0))
    }

    @Test
    fun testMissingStationDetailsDoesNotFabricate50KwAndMarksPlanUncertain() = runTest {
        val realVehicle = com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams(
            vehicleId = "vw_id4_crozz_2024",
            usableBatteryKwh = 84.8,
            totalMassKg = 2150.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.38,
            rollingResistanceCoeff = 0.010,
            maxChargePowerKw = 135.0,
            supportedConnectors = listOf("CCS2"),
            isAssumedFallback = false
        )
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "ایستگاه بدون مشخصات", GeoCoordinate(34.6401, 50.8764), WaypointType.CHARGING_STOP, stationDetails = null),
            JourneyWaypoint("wp3", "کاشان", GeoCoordinate(33.9850, 51.4100), WaypointType.DESTINATION)
        )
        val plan = coordinator.createPlan("vw_id4_crozz_2024", 84.8, 90.0, waypoints, vehiclePhysics = realVehicle).getOrThrow()

        assertEquals(com.evnavigator.iran.domain.journey.multistop.FeasibilityStatus.UNCERTAIN, plan.feasibilityStatus)
        assertFalse("Plan with missing stationDetails must not be marked deterministic feasible", plan.isOverallFeasible)
        assertEquals("Missing stationDetails must not credit deterministic charging duration", 0.0, plan.totalChargingDurationMinutes, 1e-6)
        assertEquals(com.evnavigator.iran.domain.journey.multistop.ConnectorCompatibility.UNKNOWN, plan.legs[0].connectorCompatibility)
    }

    @Test
    fun testEmptyOrUnknownConnectorListReturnsUnknownCompatibilityAndUncertainFeasibility() = runTest {
        val realVehicle = com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams(
            vehicleId = "vw_id4_crozz_2024",
            usableBatteryKwh = 84.8,
            totalMassKg = 2150.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.38,
            rollingResistanceCoeff = 0.010,
            maxChargePowerKw = 135.0,
            supportedConnectors = listOf("CCS2"),
            isAssumedFallback = false
        )
        val emptyConnectorsStation = com.evnavigator.iran.domain.intelligence.charging.CandidateStation(
            stationId = "st_empty_conn",
            stationName = "ایستگاه بدون کانکتور مشخص",
            compatibleConnectors = emptyList(),
            maxPowerKw = 120.0,
            detourDistanceMeters = 300.0,
            detourDurationSeconds = 40.0,
            distanceAlongRouteMeters = 60_000.0
        )
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "ایستگاه نامشخص", GeoCoordinate(34.6401, 50.8764), WaypointType.CHARGING_STOP, stationDetails = emptyConnectorsStation),
            JourneyWaypoint("wp3", "کاشان", GeoCoordinate(33.9850, 51.4100), WaypointType.DESTINATION)
        )
        val plan = coordinator.createPlan("vw_id4_crozz_2024", 84.8, 90.0, waypoints, vehiclePhysics = realVehicle).getOrThrow()

        assertEquals(com.evnavigator.iran.domain.journey.multistop.ConnectorCompatibility.UNKNOWN, plan.legs[0].connectorCompatibility)
        assertEquals(com.evnavigator.iran.domain.journey.multistop.FeasibilityStatus.UNCERTAIN, plan.feasibilityStatus)
        assertFalse(plan.isOverallFeasible)
    }

    @Test
    fun testJourneyProgressTrackerPreservesNaNAccuracyAndValidatesMapMatchedFixGeneration() = runTest {
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.DESTINATION)
        )
        val plan = coordinator.createPlan("vw_id4_crozz", 77.0, 85.0, waypoints).getOrThrow()
        val tracker = com.evnavigator.iran.domain.journey.tracking.JourneyProgressTracker()

        // 1. Unknown accuracy (Double.NaN) must remain NaN and mark progress as low-confidence
        val nanProg = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.20, 51.10),
            speedKmh = 90.0,
            accuracyMeters = Double.NaN,
            activeRouteGenerationId = 5L
        )
        assertTrue("Unknown accuracy must remain NaN (never 50.0)", nanProg.gpsAccuracyMeters.isNaN())
        assertFalse(nanProg.isAccuracyKnown)
        assertTrue(nanProg.isLowConfidence)

        // 2. MapMatchedFix with matching generationId (5L) must be accepted
        val validFix = com.evnavigator.iran.domain.journey.tracking.GpsMatchedProgressInput(
            segmentIndex = 0,
            distanceAlongSegmentMeters = 30_000.0,
            distanceAlongRouteMeters = 40_000.0,
            crossTrackOffsetMeters = 4.0,
            routeGenerationId = 5L,
            confidence = 0.92f,
            segmentLengthMeters = 60_000.0
        )
        val matchedProg = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.20, 51.10),
            speedKmh = 90.0,
            accuracyMeters = 8.0,
            matchedFix = validFix,
            activeRouteGenerationId = 5L
        )
        assertTrue(matchedProg.isMatchedFixAccepted)
        assertEquals(plan.totalDistanceMeters - 40_000.0, matchedProg.remainingDistanceToDestinationMeters, 1e-3)
        assertEquals(0.5, matchedProg.segmentProgressFraction, 1e-3)

        // 3. Stale MapMatchedFix with old generationId (4L vs active 5L) must be rejected
        val staleFix = validFix.copy(routeGenerationId = 4L)
        val rejectedProg = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.20, 51.10),
            speedKmh = 90.0,
            accuracyMeters = 8.0,
            matchedFix = staleFix,
            activeRouteGenerationId = 5L
        )
        assertFalse("Stale MapMatchedFix from old route generation must be rejected", rejectedProg.isMatchedFixAccepted)

        // 4. Wildcard generationId = 0L must be rejected
        val zeroGenFix = validFix.copy(routeGenerationId = 0L)
        val zeroGenProg = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.20, 51.10),
            speedKmh = 90.0,
            accuracyMeters = 8.0,
            matchedFix = zeroGenFix,
            activeRouteGenerationId = 5L
        )
        assertFalse("MapMatchedFix with routeGenerationId == 0L must be rejected", zeroGenProg.isMatchedFixAccepted)

        // 5. Stationary vehicle (speedKmh = 0.0) must not fabricate 90 km/h live speed and must flag isEtaEstimated = true
        val stoppedProg = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.20, 51.10),
            speedKmh = 0.0,
            accuracyMeters = 8.0,
            matchedFix = validFix,
            activeRouteGenerationId = 5L
        )
        assertTrue(stoppedProg.isEtaEstimated)
        assertEquals("ROUTE_PLAN_AVERAGE_SPEED", stoppedProg.etaCalculationSource)

        // 6. Stale GPS timestamp (> 10s old) must reject matchedFix before progression
        val nowMs = System.currentTimeMillis()
        val oldTimestampProg = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.20, 51.10),
            speedKmh = 90.0,
            accuracyMeters = 5.0,
            timestampEpochMs = nowMs - 25_000L,
            matchedFix = validFix,
            activeRouteGenerationId = 5L,
            nowEpochMs = nowMs
        )
        assertTrue(oldTimestampProg.isGpsStale)
        assertFalse("Stale GPS observation must never accept MapMatchedFix", oldTimestampProg.isMatchedFixAccepted)
    }

    @Test
    fun testAssumedVehicleAtChargingStopReturnsUnknownConnectorAndUncertainFeasibility() = runTest {
        val assumedVehicle = com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams.assumedReference(
            vehicleId = "assumed_fallback",
            usableBatteryKwh = 70.0
        )
        val verifiedStation = com.evnavigator.iran.domain.intelligence.charging.CandidateStation(
            stationId = "st_verified_ccs2",
            stationName = "ایستگاه مپنا",
            compatibleConnectors = listOf("CCS2"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 200.0,
            detourDurationSeconds = 30.0,
            distanceAlongRouteMeters = 50_000.0,
            liveStatusLastUpdatedEpochMs = System.currentTimeMillis(),
            isReportedAvailable = true
        )
        val waypoints = listOf(
            JourneyWaypoint("wp1", "تهران", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.CHARGING_STOP, stationDetails = verifiedStation),
            JourneyWaypoint("wp3", "کاشان", GeoCoordinate(33.9850, 51.4100), WaypointType.DESTINATION)
        )
        val plan = coordinator.createPlan(
            vehicleId = "assumed_fallback",
            batteryCapacityKwh = 70.0,
            initialSocPercent = 90.0,
            rawWaypoints = waypoints,
            vehiclePhysics = assumedVehicle,
            isDepartureSocVerified = true
        ).getOrThrow()

        assertEquals(com.evnavigator.iran.domain.journey.multistop.ConnectorCompatibility.UNKNOWN, plan.legs[0].connectorCompatibility)
        assertEquals(com.evnavigator.iran.domain.journey.multistop.FeasibilityStatus.UNCERTAIN, plan.feasibilityStatus)
        assertFalse(plan.isOverallFeasible)
    }

    @Test
    fun testUnverifiedStationStatusAndHighArrivalSocHandledSafelyWithoutCrash() = runTest {
        val realVehicle = com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams(
            vehicleId = "vw_id4_crozz_2024",
            usableBatteryKwh = 84.8,
            totalMassKg = 2150.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.38,
            rollingResistanceCoeff = 0.010,
            maxChargePowerKw = 135.0,
            supportedConnectors = listOf("CCS2 Combo"),
            isAssumedFallback = false
        )
        val verifiedStation = com.evnavigator.iran.domain.intelligence.charging.CandidateStation(
            stationId = "st_ccs2",
            stationName = "ایستگاه نزدیک مبدأ",
            compatibleConnectors = listOf("CCS2"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 100.0,
            detourDurationSeconds = 20.0,
            distanceAlongRouteMeters = 1_000.0,
            liveStatusLastUpdatedEpochMs = System.currentTimeMillis(),
            isReportedAvailable = true
        )
        // Very short leg with 99% initial SoC so arrivalSoc > 95.0
        val shortWaypoints = listOf(
            JourneyWaypoint("wp1", "تهران ۱", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("wp2", "ایستگاه نزدیک", GeoCoordinate(35.6950, 51.3400), WaypointType.CHARGING_STOP, targetDepartureSocPercent = 80.0, stationDetails = verifiedStation),
            JourneyWaypoint("wp3", "قم", GeoCoordinate(34.6401, 50.8764), WaypointType.DESTINATION)
        )
        val highSocPlan = coordinator.createPlan(
            vehicleId = "vw_id4_crozz_2024",
            batteryCapacityKwh = 84.8,
            initialSocPercent = 99.0,
            rawWaypoints = shortWaypoints,
            vehiclePhysics = realVehicle,
            isDepartureSocVerified = true,
            departureSocSource = "USER_MANUAL_INPUT"
        ).getOrThrow()

        assertEquals("CCS2 Combo must canonically match CCS2", com.evnavigator.iran.domain.journey.multistop.ConnectorCompatibility.MATCH, highSocPlan.legs[0].connectorCompatibility)
        assertEquals("No charging needed when arrivalSoc > targetSoc", 0.0, highSocPlan.totalChargingDurationMinutes, 1e-6)
    }
}

