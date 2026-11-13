package com.evnavigator.iran.domain.intelligence

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.trip.SegmentPhysicsEnergyCalculator
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.Trip
import com.evnavigator.iran.domain.model.Vehicle
import com.evnavigator.iran.domain.model.navigation.ChargingStopInstruction
import com.evnavigator.iran.domain.model.navigation.ManeuverInstruction
import com.evnavigator.iran.domain.model.navigation.ManeuverType
import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationState
import com.evnavigator.iran.domain.model.navigation.NavigationStatePatch
import com.evnavigator.iran.domain.model.navigation.NavigationStatus
import com.evnavigator.iran.domain.model.navigation.Patch
import com.evnavigator.iran.domain.model.trip.intelligence.TripIntelligenceRequest
import com.evnavigator.iran.domain.policy.DiagnosticCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral regression tests for V13.0 Domain Energy, Provenance, Connector Compatibility,
 * and Navigation Patch<T> Clear semantics (Items 6–13, 40–43, 71–78, 105, 141–147).
 */
class V13EnergyAndNavigationPatchTest {

    @Test
    fun `resolveRouteReferenceSpeed tags speed derived from duration as DERIVED_FROM_ROUTE_TIME`() {
        val origin = GeoCoordinate(35.6892, 51.3890)
        val dest = GeoCoordinate(34.6416, 50.8746)
        val route = Route(
            id = "r1",
            origin = origin,
            destination = dest,
            distance = Distance.fromKilometers(135.0),
            estimatedTime = 5_400L, // 1.5 hours => 90 km/h
            segments = emptyList()
        )
        val vehicle = Vehicle(id = "veh_1", name = "Test EV", batteryCapacity = 75.0, maxRange = 450.0, weight = 1950.0)
        val trip = Trip(
            id = "t1",
            name = "Tehran-Qom",
            startLocation = origin,
            endLocation = dest,
            vehicle = vehicle,
            route = route,
            totalDistance = Distance.fromKilometers(135.0),
            totalTime = 5_400L
        )
        val request = TripIntelligenceRequest(trip = trip, vehicle = vehicle)

        val (speedKmh, originTag) = SegmentPhysicsEnergyCalculator.resolveRouteReferenceSpeed(
            request = request,
            context = null,
            routeDistanceMeters = 135_000.0
        )
        assertEquals(90.0, speedKmh, 0.1)
        assertEquals(InputOrigin.DERIVED_FROM_ROUTE_TIME, originTag)
    }

    @Test
    fun `buildExplicitContext never fabricates 1200m altitude and simulateLegEnergyStrict rejects unverified inputs`() {
        val origin = GeoCoordinate(35.6892, 51.3890)
        val dest = GeoCoordinate(34.6416, 50.8746)
        val route = Route(
            id = "r1",
            origin = origin,
            destination = dest,
            distance = Distance.fromKilometers(100.0),
            estimatedTime = 3_600L
        )
        val vehicle = Vehicle(id = "", name = "Unverified EV", batteryCapacity = 60.0, maxRange = 350.0, weight = 0.0)
        val trip = Trip(
            id = "t1",
            name = "Test",
            startLocation = origin,
            endLocation = dest,
            vehicle = vehicle,
            route = route,
            totalDistance = Distance.fromKilometers(100.0),
            totalTime = 3_600L
        )
        val request = TripIntelligenceRequest(trip = trip, vehicle = vehicle, isInitialSocVerified = false)
        val context = SegmentPhysicsEnergyCalculator.buildExplicitContext(
            request = request,
            catalogVehicle = null,
            batteryCapKwh = 60.0,
            currentSoc = 80.0,
            isSocVerified = false,
            minReserveSoc = 15.0,
            routeDistanceMeters = 100_000.0,
            nowMs = 1_700_000_000_000L
        )

        assertNull("Average elevation must not be fabricated as 1200m", context.averageElevationMeters.value)
        assertEquals(InputOrigin.UNKNOWN, context.averageElevationMeters.origin)

        val profiles = SegmentPhysicsEnergyCalculator.buildRouteSegmentProfiles(
            request = request,
            context = context,
            routePolyline = listOf(origin, dest),
            routeDistanceMeters = 100_000.0
        )
        val strictResult = SegmentPhysicsEnergyCalculator.simulateLegEnergyStrict(
            profiles = profiles,
            legStartMeters = 0.0,
            legEndMeters = 100_000.0,
            extraDetourMeters = 0.0,
            departureSocPercent = 80.0,
            batteryCapacityKwh = 60.0,
            context = context
        )
        assertTrue("Strict mode must fail when vehicleId, mass, or SoC are unverified", strictResult.isFailure)
        val ex = strictResult.exceptionOrNull() as SegmentPhysicsEnergyCalculator.StrictEnergyCalculationException
        assertTrue(ex.diagnostics.contains(DiagnosticCode.MISSING_VEHICLE_ID))
        assertTrue(ex.diagnostics.contains(DiagnosticCode.ASSUMED_INITIAL_SOC))
    }

    @Test
    fun `ConnectorCompatibilityEngine prevents AC connector from receiving DC fast power`() {
        val acMatch = CanonicalEnergyEngine.ConnectorCompatibilityEngine.resolveCompatiblePower(
            rawConnectorName = "TYPE_2",
            stationPortMaxKw = 43.0,
            vehicleAcMaxKw = 11.0,
            vehicleDcMaxKw = 150.0
        )
        assertNotNull(acMatch)
        assertFalse(acMatch!!.isDcSession)
        assertEquals(11.0, acMatch.effectivePeakPowerKw.value, 1e-4)

        val dcMatch = CanonicalEnergyEngine.ConnectorCompatibilityEngine.resolveCompatiblePower(
            rawConnectorName = "CCS2",
            stationPortMaxKw = 180.0,
            vehicleAcMaxKw = 11.0,
            vehicleDcMaxKw = 120.0
        )
        assertNotNull(dcMatch)
        assertTrue(dcMatch!!.isDcSession)
        assertEquals(120.0, dcMatch.effectivePeakPowerKw.value, 1e-4)
    }

    @Test
    fun `NavigationState applyPatch with Patch Clear removes nextChargingStop and maneuvers`() {
        val sessionId = NavigationSessionId("sess_1")
        val initialStop = ChargingStopInstruction(
            stationId = "st_1",
            stationName = "ایستگاه شارژ آزادی",
            distanceToStationMeters = 12_000.0,
            timeToStationSeconds = 600L,
            arrivalSoc = 22.0,
            targetSoc = 80.0,
            estimatedChargingTimeSeconds = 1_800L
        )
        val initialManeuver = ManeuverInstruction(
            type = ManeuverType.TURN_RIGHT,
            distanceMeters = 450.0,
            streetName = "بزرگراه همت",
            instructionText = "گردش به راست"
        )
        val stateWithStop = NavigationState(
            sessionId = sessionId,
            status = NavigationStatus.ACTIVE,
            currentManeuver = initialManeuver,
            nextChargingStop = initialStop
        )

        val clearedState = stateWithStop.applyPatch(
            NavigationStatePatch(
                nextChargingStop = Patch.Clear,
                currentManeuver = Patch.Clear
            )
        )
        assertNull("Patch.Clear must clear nextChargingStop to null", clearedState.nextChargingStop)
        assertNull("Patch.Clear must clear currentManeuver to null", clearedState.currentManeuver)
        assertTrue(clearedState.clearNextChargingStop)
    }
}
