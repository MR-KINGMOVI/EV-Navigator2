package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.navigation.engine.impl.DefaultDeviationDetector
import com.evnavigator.iran.navigation.engine.impl.DefaultLocationValidator
import com.evnavigator.iran.navigation.engine.impl.DefaultRoutePositionResolver
import com.evnavigator.iran.navigation.engine.impl.HmmMapMatcherImpl
import org.junit.Assert.*
import org.junit.Test

class HmmMapMatcherTest {

    private fun buildSampleRoute(): Route {
        val seg1 = RouteSegment(
            id = "seg_azadi_hemmat",
            start = GeoCoordinate(35.6997, 51.3380),
            end = GeoCoordinate(35.7200, 51.3380),
            distance = Distance.fromMeters(2250.0),
            bearing = Bearing(0.0),
            roadName = "بزرگراه شهید باکری"
        )
        val seg2 = RouteSegment(
            id = "seg_hemmat_east",
            start = GeoCoordinate(35.7200, 51.3380),
            end = GeoCoordinate(35.7200, 51.3650),
            distance = Distance.fromMeters(2450.0),
            bearing = Bearing(90.0),
            roadName = "بزرگراه شهید همت"
        )
        return Route(
            id = "route_test_1",
            start = seg1.start,
            end = seg2.end,
            segments = listOf(seg1, seg2),
            distance = Distance.fromMeters(4700.0),
            estimatedTime = 240L
        )
    }

    @Test
    fun `HMM map matcher snaps noisy GPS coordinates onto correct route segment`() {
        val matcher = HmmMapMatcherImpl()
        val route = buildSampleRoute()
        val now = System.currentTimeMillis()

        val loc1 = Location(
            latitude = 35.7050,
            longitude = 51.3382, // slightly east of seg1
            accuracyMeters = 8.0f,
            timestampEpochMs = now - 5000L
        )
        val match1 = matcher.matchToRoute(loc1, route)
        assertNotNull(match1)
        assertEquals("seg_azadi_hemmat", match1!!.roadSegmentId)
        assertEquals(51.3380, match1.matchedPoint.longitude, 0.0005)
        assertTrue(match1.confidence > 0.6)

        val loc2 = Location(
            latitude = 35.7201,
            longitude = 51.3500, // along seg2
            accuracyMeters = 6.0f,
            timestampEpochMs = now
        )
        val match2 = matcher.matchToRoute(loc2, route)
        assertNotNull(match2)
        assertEquals("seg_hemmat_east", match2!!.roadSegmentId)
        assertEquals(35.7200, match2.matchedPoint.latitude, 0.0005)
    }

    @Test
    fun `stationary vehicle lock prevents segment jitter when stopped`() {
        val matcher = HmmMapMatcherImpl()
        val route = buildSampleRoute()
        val t0 = System.currentTimeMillis()

        val firstFix = Location(35.7100, 51.3380, accuracyMeters = 5.0f, timestampEpochMs = t0)
        val firstMatch = matcher.matchToRoute(firstFix, route)!!

        // Vehicle stopped: tiny 0.6m GPS drift 2 seconds later
        val driftedFix = Location(35.710005, 51.338005, accuracyMeters = 6.0f, timestampEpochMs = t0 + 2000L)
        val secondMatch = matcher.matchToRoute(driftedFix, route)!!

        assertEquals(firstMatch.roadSegmentId, secondMatch.roadSegmentId)
        assertEquals(firstMatch.matchedPoint.latitude, secondMatch.matchedPoint.latitude, 1e-7)
        assertEquals(firstMatch.matchedPoint.longitude, secondMatch.matchedPoint.longitude, 1e-7)
    }

    @Test
    fun `location validator flags suspicious teleportation jump`() {
        val validator = DefaultLocationValidator()
        val t0 = System.currentTimeMillis()
        val prev = Location(35.7000, 51.3400, accuracyMeters = 5.0f, timestampEpochMs = t0)
        // 5 km jump in 1 second (5000 m/s -> impossible vehicle speed)
        val jump = Location(35.7450, 51.3400, accuracyMeters = 5.0f, timestampEpochMs = t0 + 1000L)

        assertTrue(validator.isLocationJumpSuspicious(prev, jump, maxDistanceMeters = 500.0))
    }

    @Test
    fun `deviation detector triggers reroute when vehicle moves far off route`() {
        val matcher = HmmMapMatcherImpl()
        val resolver = DefaultRoutePositionResolver()
        val detector = DefaultDeviationDetector()
        val route = buildSampleRoute()
        val t0 = System.currentTimeMillis()

        // 150 meters away from the route
        val offRouteLoc1 = Location(35.7100, 51.3400, accuracyMeters = 6.0f, timestampEpochMs = t0)
        val matched1 = matcher.matchToRoute(offRouteLoc1, route)!!
        val pos1 = resolver.determineRoutePosition(route, matched1)!!
        val dev1 = detector.detectDeviation(route, pos1, matched1, offRouteLoc1)!!
        assertTrue(dev1.isOffRoute)

        // 4 seconds later still > 150 meters off route -> AUTO_REROUTE
        val offRouteLoc2 = Location(35.7110, 51.3405, accuracyMeters = 6.0f, timestampEpochMs = t0 + 4000L)
        val matched2 = matcher.matchToRoute(offRouteLoc2, route)!!
        val pos2 = resolver.determineRoutePosition(route, matched2)!!
        val dev2 = detector.detectDeviation(route, pos2, matched2, offRouteLoc2)!!
        assertTrue(detector.isRerouteRequired(dev2))
    }
}
