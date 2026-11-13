package com.evnavigator.iran.navigation

import com.evnavigator.iran.core.domain.GeoCoordinate as CoreGeoCoordinate
import com.evnavigator.iran.core.routing.engine.OfflineRoutingSolver
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.core.routing.model.RoadEdge
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate as DomainGeoCoordinate
import com.evnavigator.iran.domain.model.Speed
import com.evnavigator.iran.domain.model.navigation.GpsQuality
import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationStatus
import com.evnavigator.iran.navigation.engine.*
import com.evnavigator.iran.navigation.engine.impl.NavigationStateReducerImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflineLiveNavigation.
 * Verifies GPS degradation, tunnel handling, off-route rerouting, and out-of-coverage degradation.
 */
class OfflineLiveNavigationTest {

    private lateinit var session: NavigationSession
    private lateinit var solver: OfflineRoutingSolver

    @Before
    fun setUp() {
        val reducer = NavigationStateReducerImpl()
        session = NavigationSession(NavigationSessionId("test_session"), NavigationStatus.NAVIGATING, null, reducer)
        solver = OfflineRoutingSolver()
    }

    private fun createGraph(
        nodesList: List<RoadNode>,
        edgesList: List<RoadEdge>
    ): RoadGraph {
        val nodeMap = nodesList.associateBy { it.id }
        val edgeMap = edgesList.associateBy { it.id }
        val nodeToEdges = mutableMapOf<String, MutableList<String>>()
        for (edge in edgesList) {
            nodeToEdges.getOrPut(edge.startNode) { mutableListOf() }.add(edge.id)
        }
        return RoadGraph(nodeMap, edgeMap, nodeToEdges)
    }

    @Test
    fun `test gps loss preserves last known coordinate and sets GpsQuality UNAVAILABLE`() {
        val coordinator = OfflineNavigationCoordinator(session, solver) { null }

        val goodCoord = CoreGeoCoordinate(35.6892, 51.3890)
        coordinator.onLocationUpdate(goodCoord, accuracyMeters = 5.0f, isTunnelOrLost = false)

        assertEquals(GpsQuality.EXCELLENT, coordinator.gpsQuality.value)
        assertEquals(goodCoord, coordinator.lastKnownLocation.value)

        // Enter tunnel -> signal lost
        val noisyCoord = CoreGeoCoordinate(0.0, 0.0)
        coordinator.onLocationUpdate(noisyCoord, accuracyMeters = 100.0f, isTunnelOrLost = true)

        assertEquals(GpsQuality.UNAVAILABLE, coordinator.gpsQuality.value)
        // Last known location must NOT be corrupted by noisy coordinate
        assertEquals(goodCoord, coordinator.lastKnownLocation.value)
    }

    @Test
    fun `test offline reroute succeeds when graph is connected`() = runBlocking {
        val nA = RoadNode("A", DomainGeoCoordinate(35.70, 51.40))
        val nB = RoadNode("B", DomainGeoCoordinate(35.71, 51.40))
        val eAB = RoadEdge("e_ab", "A", "B", Distance.fromMeters(1000.0), Speed.ofKilometersPerHour(60.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)
        val graph = createGraph(listOf(nA, nB), listOf(eAB))

        val coordinator = OfflineNavigationCoordinator(session, solver) { graph }

        val result = coordinator.handleOfflineReroute("A", "B")

        assertTrue(result is OfflineRerouteResult.Success)
        assertEquals(NavigationStatus.NAVIGATING, session.status)
        val success = result as OfflineRerouteResult.Success
        assertEquals(listOf("A", "B"), success.newRoute.nodes)
    }

    @Test
    fun `test offline reroute fails gracefully when position is outside map coverage`() = runBlocking {
        val nA = RoadNode("A", DomainGeoCoordinate(35.70, 51.40))
        val graph = createGraph(listOf(nA), emptyList())

        val coordinator = OfflineNavigationCoordinator(session, solver) { graph }

        // Start node "UnknownNode" is outside coverage
        val result = coordinator.handleOfflineReroute("UnknownNode", "A")

        assertTrue(result is OfflineRerouteResult.CoverageUnavailable)
        val unavailable = result as OfflineRerouteResult.CoverageUnavailable
        assertTrue(unavailable.reason.contains("outside offline road dataset coverage"))
    }

    @Test
    fun `test HmmMapMatcher rejects out-of-order GPS timestamps and flags low-speed bearing confidence`() {
        val matcher = com.evnavigator.iran.navigation.engine.impl.HmmMapMatcherImpl()
        val segStart = DomainGeoCoordinate(35.7000, 51.3400)
        val segEnd = DomainGeoCoordinate(35.7100, 51.3400)
        val roadSeg = com.evnavigator.iran.domain.model.RoadSegment(
            id = "seg_1",
            startPoint = segStart,
            endPoint = segEnd,
            length = Distance.fromMeters(1100.0),
            speedLimit = Speed.fromKilometersPerHour(80.0),
            roadType = com.evnavigator.iran.domain.model.RoadType.HIGHWAY,
            surfaceType = com.evnavigator.iran.domain.model.SurfaceType.ASPHALT,
            gradient = 0.0
        )
        val road = com.evnavigator.iran.domain.model.Road(
            id = "road_1",
            name = "بزرگراه چمران",
            type = com.evnavigator.iran.domain.model.RoadType.HIGHWAY,
            surface = com.evnavigator.iran.domain.model.SurfaceType.ASPHALT,
            speedLimit = Speed.fromKilometersPerHour(80.0),
            segments = listOf(roadSeg)
        )
        val routeSeg = com.evnavigator.iran.domain.model.RouteSegment(
            road = road,
            startPoint = segStart,
            endPoint = segEnd,
            distance = Distance.fromMeters(1100.0),
            estimatedTime = 50L,
            grade = 0.0,
            edgeId = "edge_chamran_1"
        )
        val route = com.evnavigator.iran.domain.model.Route(
            id = "r_1",
            start = segStart,
            end = segEnd,
            segments = listOf(routeSeg),
            distance = Distance.fromMeters(1100.0),
            estimatedTime = 50L
        )

        val t1 = 1_700_000_010_000L
        val fix1 = com.evnavigator.iran.domain.model.Location(
            latitude = 35.7020,
            longitude = 51.3400,
            speedMps = 0.5f, // Low speed (< 1.5 m/s) -> bearing confidence must be low
            bearingDeg = 10f,
            accuracyMeters = 6f,
            timestampEpochMs = t1
        )
        val matched1 = matcher.matchLocation(fix1, route)
        assertNotNull(matched1)
        assertTrue("Low speed (< 1.5 m/s) must yield low bearingConfidence", matched1!!.bearingConfidence <= 0.3)
        assertTrue("Longitudinal offset along segment must be positive", matched1.distanceAlongSegmentMeters > 0.0)

        // Out-of-order older fix (t0 < t1) at a different latitude must be rejected and return previous match
        val staleFix = com.evnavigator.iran.domain.model.Location(
            latitude = 35.7090,
            longitude = 51.3400,
            speedMps = 20f,
            bearingDeg = 0f,
            accuracyMeters = 6f,
            timestampEpochMs = t1 - 5_000L
        )
        val matchedStale = matcher.matchLocation(staleFix, route)
        assertNotNull(matchedStale)
        assertEquals("Out-of-order GPS fix must not jump vehicle position", matched1.snappedLocation.latitude, matchedStale!!.snappedLocation.latitude, 1e-6)
    }

    @Test
    fun `test NavigationStateReducerImpl ignores stale generationId and merges partial updates`() {
        val reducer = NavigationStateReducerImpl()
        val initialState = com.evnavigator.iran.domain.model.navigation.NavigationState(
            sessionId = NavigationSessionId("s1"),
            status = NavigationStatus.NAVIGATING,
            route = null,
            routePosition = null,
            matchedLocation = null,
            progress = null,
            upcomingManeuver = null,
            eta = null,
            remainingDistance = Distance.fromMeters(5000.0),
            remainingTime = com.evnavigator.iran.domain.model.Duration.fromSeconds(300L),
            currentSoc = 82.0,
            predictedArrivalSoc = 64.0,
            nextChargingStop = null,
            dataQuality = com.evnavigator.iran.domain.model.navigation.NavigationDataQuality.COMPLETE,
            gpsQuality = GpsQuality.GOOD,
            trafficStatus = com.evnavigator.iran.domain.model.navigation.TrafficStatus.FRESH,
            weatherStatus = com.evnavigator.iran.domain.model.navigation.WeatherStatus.FRESH,
            generationId = 5L,
            vehicleId = "vw_id4_crozz"
        )

        // Attempt to apply a stale state with generationId = 3L
        val staleIncoming = initialState.copy(
            currentSoc = 10.0,
            generationId = 3L
        )
        val afterStale = reducer.reduceState(
            currentState = initialState,
            location = null,
            route = null,
            eta = null,
            maneuvers = emptyList(),
            chargingStops = emptyList(),
            newState = staleIncoming
        )
        assertEquals("Stale generationId (3 < 5) must be ignored", 82.0, afterStale.currentSoc!!, 1e-4)
        assertEquals(5L, afterStale.generationId)
    }
}

