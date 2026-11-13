package com.evnavigator.iran.domain.intelligence.energy

import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the "unified energy service exists but nobody calls it" regression.
 */
class EnergyServiceWiringTest {

    private val vehicle = VehiclePhysicsParams(
        vehicleId = "test_ev",
        usableBatteryKwh = 72.0,
        totalMassKg = 1900.0,
        dragCoefficient = 0.28,
        frontalAreaM2 = 2.3,
        rollingResistanceCoeff = 0.010
    )

    private class CountingService(private val inner: EnergyPredictionService) : EnergyPredictionService {
        var calls = 0
        override fun predict(
            route: RouteProfileInput,
            vehicle: VehiclePhysicsParams,
            conditions: EnvironmentalConditions
        ): TripEnergyPrediction {
            calls++
            return inner.predict(route, vehicle, conditions)
        }
    }

    @Test
    fun coordinatorDelegatesEveryLegToEnergyService() = runTest {
        val service = CountingService(DefaultEnergyPredictionService())
        val mockProvider = object : com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider {
            override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult {
                val dist = 200_000.0
                return com.evnavigator.iran.domain.journey.multistop.RoadRouteCalculationResult(
                    distanceMeters = dist,
                    durationSeconds = dist / 25.0,
                    isRealRoadGraph = true,
                    segments = listOf(
                        RouteSegmentInput(
                            distanceMeters = dist,
                            startElevationMeters = 1100.0,
                            endElevationMeters = 1200.0,
                            speedLimitKmh = 100.0
                        )
                    ),
                    polyline = listOf(from, to)
                )
            }
        }
        val coordinator = MultiStopRouteCoordinator(energyService = service, roadRouteProvider = mockProvider)
        val waypoints = listOf(
            JourneyWaypoint("a", "Tehran", GeoCoordinate(35.6997, 51.3380), WaypointType.ORIGIN),
            JourneyWaypoint("b", "Kashan", GeoCoordinate(33.9850, 51.4100), WaypointType.INTERMEDIATE_USER_STOP),
            JourneyWaypoint("c", "Isfahan", GeoCoordinate(32.6370, 51.6830), WaypointType.DESTINATION)
        )
        val plan = coordinator.createPlan("test_ev", 72.0, 90.0, waypoints, vehiclePhysics = vehicle).getOrThrow()
        assertEquals(2, service.calls)
        // Not the old hard-coded 0.18 kWh/km constant
        val kwhPerKm = plan.totalEnergyKwh / (plan.totalDistanceMeters / 1000.0)
        assertTrue("Consumption should come from physics, got $kwhPerKm kWh/km", kwhPerKm > 0.10 && kwhPerKm < 0.25 && kwhPerKm != 0.18)
    }

    @Test
    fun unknownElevationIsNotReplacedByInventedAltitude() {
        val flatKnownSeaLevel = DefaultEnergyPredictionService().predict(
            RouteProfileInput(listOf(RouteSegmentInput(10_000.0, 0.0, 0.0, 90.0))), vehicle
        )
        val unknown = DefaultEnergyPredictionService().predict(
            RouteProfileInput(listOf(RouteSegmentInput(10_000.0, null, null, 90.0))), vehicle
        )
        assertEquals(flatKnownSeaLevel.breakdown.netBatteryEnergyWh, unknown.breakdown.netBatteryEnergyWh, 1e-6)
        assertTrue(unknown.diagnosticsNotes.any { it.contains("نامشخص") })
    }

    @Test
    fun uphillCostsMoreThanDownhill() {
        val up = DefaultEnergyPredictionService().predict(
            RouteProfileInput(listOf(RouteSegmentInput(10_000.0, 0.0, 400.0, 90.0))), vehicle
        )
        val down = DefaultEnergyPredictionService().predict(
            RouteProfileInput(listOf(RouteSegmentInput(10_000.0, 400.0, 0.0, 90.0))), vehicle
        )
        assertTrue(up.breakdown.netBatteryEnergyWh > down.breakdown.netBatteryEnergyWh)
    }

    @Test
    fun defaultEnvironmentalConditionsDoesNotMarkDepartureSocAsVerified() {
        val defaultCond = EnvironmentalConditions()
        assertTrue("Default departure SoC must not be marked verified", !defaultCond.isDepartureSocVerified)
        val result = DefaultEnergyPredictionService().predict(
            route = RouteProfileInput(
                listOf(RouteSegmentInput(10_000.0, 1000.0, 1050.0, speedLimitKmh = 90.0, targetSpeedKmh = 90.0))
            ),
            vehicle = vehicle.copy(isAssumedFallback = false),
            conditions = defaultCond
        )
        assertEquals("ASSUMED", result.dataQuality)
        assertEquals(InputProvenance.ASSUMED, result.inputProvenance["departureSoc"])
        assertTrue(result.assumptions.contains("UNVERIFIED_DEFAULT_DEPARTURE_SOC"))
    }

    @Test
    fun unmeasuredHvacLowersConfidenceAndPreventsVerifiedQuality() {
        val seg = RouteSegmentInput(10_000.0, 1000.0, 1020.0, speedLimitKmh = 100.0, targetSpeedKmh = 95.0)
        val realVeh = vehicle.copy(isAssumedFallback = false)
        val allMeasured = DefaultEnergyPredictionService().predict(
            route = RouteProfileInput(listOf(seg)),
            vehicle = realVeh,
            conditions = EnvironmentalConditions(
                departureSocPercent = 80.0,
                isDepartureSocVerified = true,
                isTemperatureMeasured = true,
                isWindMeasured = true,
                isHvacMeasured = true
            )
        )
        val hvacUnmeasured = DefaultEnergyPredictionService().predict(
            route = RouteProfileInput(listOf(seg)),
            vehicle = realVeh,
            conditions = EnvironmentalConditions(
                departureSocPercent = 80.0,
                isDepartureSocVerified = true,
                isTemperatureMeasured = true,
                isWindMeasured = true,
                isHvacMeasured = false
            )
        )
        assertEquals("VERIFIED", allMeasured.dataQuality)
        assertEquals("ESTIMATED", hvacUnmeasured.dataQuality)
        assertTrue("Unmeasured HVAC must lower confidence score", hvacUnmeasured.confidenceScore < allMeasured.confidenceScore)
        assertTrue("Unmeasured HVAC must increase uncertainty percent", hvacUnmeasured.uncertaintyPercent > allMeasured.uncertaintyPercent)
    }

    @Test
    fun speedLimitAndTargetSpeedAreDistinctWithExplicitAssumptions() {
        val realVeh = vehicle.copy(isAssumedFallback = false)
        val cond = EnvironmentalConditions(
            departureSocPercent = 80.0,
            isDepartureSocVerified = true,
            isTemperatureMeasured = true,
            isWindMeasured = true,
            isHvacMeasured = true
        )
        val onlyLimit = DefaultEnergyPredictionService().predict(
            route = RouteProfileInput(listOf(RouteSegmentInput(10_000.0, 1000.0, 1000.0, speedLimitKmh = 110.0, targetSpeedKmh = null))),
            vehicle = realVeh,
            conditions = cond
        )
        assertTrue(onlyLimit.assumptions.any { it.startsWith("TARGET_SPEED_ASSUMED_FROM_SPEED_LIMIT") })
        assertEquals(InputProvenance.ASSUMED, onlyLimit.inputProvenance["targetSpeed"])
        assertEquals(InputProvenance.MEASURED, onlyLimit.inputProvenance["speedLimit"])
    }

    @Test
    fun skippedInvalidSegmentMarksPredictionInfeasibleAndLowConfidence() {
        val realVeh = vehicle.copy(isAssumedFallback = false)
        val cond = EnvironmentalConditions(
            departureSocPercent = 90.0,
            isDepartureSocVerified = true,
            isTemperatureMeasured = true,
            isWindMeasured = true,
            isHvacMeasured = true
        )
        val withInvalidSegment = DefaultEnergyPredictionService().predict(
            route = RouteProfileInput(
                listOf(
                    RouteSegmentInput(10_000.0, 1000.0, 1020.0, speedLimitKmh = 90.0, targetSpeedKmh = 90.0),
                    RouteSegmentInput(-500.0, 1020.0, 1050.0, speedLimitKmh = 90.0, targetSpeedKmh = 90.0)
                )
            ),
            vehicle = realVeh,
            conditions = cond
        )
        org.junit.Assert.assertFalse("Route with skipped invalid segment must never claim isFeasibleWithoutCharging = true", withInvalidSegment.isFeasibleWithoutCharging)
        assertEquals("LOW_CONFIDENCE", withInvalidSegment.dataQuality)
        org.junit.Assert.assertFalse(withInvalidSegment.isExactCalculation)
        assertTrue(withInvalidSegment.assumptions.any { it.startsWith("SKIPPED_INVALID_SEGMENTS:") })

        val emptyRoute = DefaultEnergyPredictionService().predict(
            route = RouteProfileInput(emptyList()),
            vehicle = realVeh,
            conditions = cond
        )
        org.junit.Assert.assertFalse("Empty route must never claim isFeasibleWithoutCharging = true", emptyRoute.isFeasibleWithoutCharging)
        assertEquals("LOW_CONFIDENCE", emptyRoute.dataQuality)
        org.junit.Assert.assertFalse(emptyRoute.isExactCalculation)
    }
}
