package com.evnavigator.iran.domain.journey

import com.evnavigator.iran.domain.intelligence.replanning.DynamicReplanningCoordinator
import com.evnavigator.iran.domain.journey.charging.LiveChargingSessionManager
import com.evnavigator.iran.domain.journey.charging.ChargingSessionStatus
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.MultiStopJourneyPlan
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.journey.tracking.DeviationAssessment
import com.evnavigator.iran.domain.journey.tracking.JourneyProgressTracker
import com.evnavigator.iran.domain.journey.tracking.RouteDeviationDetector
import com.evnavigator.iran.domain.model.GeoCoordinate
import org.junit.Assert.*
import org.junit.Test

class JourneyExecutionTest {

    @Test
    fun testProgressTrackerComputesCorrectRemainingDistance() {
        val tracker = JourneyProgressTracker()
        val waypoints = listOf(
            JourneyWaypoint("1", "A", GeoCoordinate(35.6892, 51.3890), WaypointType.ORIGIN),
            JourneyWaypoint("2", "B", GeoCoordinate(35.0, 51.0), WaypointType.CHARGING_STOP),
            JourneyWaypoint("3", "C", GeoCoordinate(34.0, 50.5), WaypointType.DESTINATION)
        )
        val plan = MultiStopJourneyPlan(
            planId = "p1",
            vehicleId = "ev1",
            waypoints = waypoints,
            legs = emptyList(),
            totalDistanceMeters = 200_000.0,
            totalDrivingDurationSeconds = 7200.0,
            totalChargingDurationMinutes = 30.0,
            totalEnergyKwh = 36.0,
            initialDepartureSocPercent = 90.0,
            finalArrivalSocPercent = 40.0,
            isOverallFeasible = true
        )

        val progress = tracker.computeProgress(
            plan = plan,
            location = GeoCoordinate(35.6, 51.3),
            speedKmh = 90.0,
            accuracyMeters = 8.0
        )

        assertNotNull(progress.nextWaypoint)
        assertTrue("Remaining distance must be > 0", progress.remainingDistanceToDestinationMeters > 0)
        assertFalse("Recent fix should not be stale", progress.isGpsStale)
    }

    @Test
    fun testDeviationDetectorFiltersSingleJitter() {
        val coordinator = DynamicReplanningCoordinator()
        val detector = RouteDeviationDetector(coordinator, corridorWidthMeters = 50.0, requiredConsecutiveDeviations = 3)
        val route = listOf(
            GeoCoordinate(35.0, 51.0),
            GeoCoordinate(35.1, 51.1)
        )

        // 1st sample: far away -> PotentialDeviation (1)
        val s1 = detector.evaluatePosition(GeoCoordinate(35.05, 51.2), route, 10.0)
        assertTrue(s1 is DeviationAssessment.PotentialDeviation)

        // 2nd sample: back on route -> resets to OnRoute
        val s2 = detector.evaluatePosition(GeoCoordinate(35.05, 51.05), route, 10.0)
        assertTrue(s2 is DeviationAssessment.OnRoute)
    }

    @Test
    fun testDeviationDetectorConfirmsAfterConsecutiveSamples() {
        val coordinator = DynamicReplanningCoordinator()
        val detector = RouteDeviationDetector(coordinator, corridorWidthMeters = 50.0, requiredConsecutiveDeviations = 3)
        val route = listOf(
            GeoCoordinate(35.0, 51.0),
            GeoCoordinate(35.1, 51.1)
        )

        // 3 consecutive off-route samples
        detector.evaluatePosition(GeoCoordinate(35.5, 52.0), route, 10.0)
        detector.evaluatePosition(GeoCoordinate(35.5, 52.0), route, 10.0)
        val s3 = detector.evaluatePosition(GeoCoordinate(35.5, 52.0), route, 10.0)

        assertTrue(s3 is DeviationAssessment.ConfirmedDeviation)
    }

    @Test
    fun testChargingSessionManagerModelsProgressAndTaper() {
        val manager = LiveChargingSessionManager()
        val session = manager.startSession(
            stationId = "station_1",
            stationName = "مهروماه قم",
            currentSocPercent = 20.0,
            targetSocPercent = 85.0,
            chargerPowerLimitKw = 120.0,
            vehicleCapacityKwh = 70.0
        )

        assertEquals(ChargingSessionStatus.CHARGING, session.status)
        assertEquals(20.0, session.startSocPercent, 0.01)
        assertEquals(85.0, session.targetSocPercent, 0.01)

        // Update progress to 82% (taper should become active)
        val updated = manager.updateProgress(82.0, 70.0)
        assertNotNull(updated)
        assertEquals(82.0, updated!!.currentSocPercent, 0.01)
        assertTrue("Taper should be active above 80%", updated.isTaperActive)

        // Update progress to 85% -> Session completed
        val finished = manager.updateProgress(85.0, 70.0)
        assertNotNull(finished)
        assertEquals(ChargingSessionStatus.COMPLETED, finished!!.status)
    }
}
