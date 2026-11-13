package com.evnavigator.iran.core.routing

import com.evnavigator.iran.core.routing.codec.PolylineCodec
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.provider.GraphHopperRoutingProvider
import com.evnavigator.iran.core.routing.provider.ValhallaRoutingProvider
import com.evnavigator.iran.core.routing.validation.RouteIntegrityValidator
import com.evnavigator.iran.domain.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V13.1 Behavioral Audit Tests for Core Routing, Endpoint Contiguity, Reversal Detection,
 * and Elimination of Fabricated 90/100 km/h Speed Limits (Sections 5 & 6).
 */
class V131RoutingAndGeometryBehavioralTest {

    private val tehran = GeoCoordinate(35.6892, 51.3890)
    private val karaj = GeoCoordinate(35.8327, 50.9915)
    private val qazvin = GeoCoordinate(36.2688, 50.0041)

    @Test
    fun `buildContiguousSegmentsFromManeuvers never invents 90 kmh speedLimitKmh when maneuver speed limit is unknown`() {
        val poly = listOf(tehran, karaj, qazvin)
        val maneuversWithoutSpeedLimit = listOf(
            RealRoadManeuver(
                index = 0,
                instructionFa = "حرکت در مسیر",
                maneuverType = RealManeuverType.DEPART,
                coordinate = tehran,
                distanceFromPreviousMeters = 42_000.0,
                durationSeconds = 1800.0,
                roadName = "آزادراه تهران-کرج",
                speedLimitKmh = 0 // Unknown legal speed limit
            )
        )
        val segments = RouteIntegrityValidator.buildContiguousSegmentsFromManeuvers(
            polyline = poly,
            maneuvers = maneuversWithoutSpeedLimit
        )
        assertEquals(2, segments.size)
        for (seg in segments) {
            assertEquals("Unknown legal speed limit must remain 0 (UNKNOWN), never 90", 0, seg.speedLimitKmh)
        }
    }

    @Test
    fun `validateDecodedRouteGeometry detects reversed route and mismatched endpoints`() {
        val roadPoints = listOf(
            tehran,
            GeoCoordinate(35.7500, 51.2000),
            karaj,
            GeoCoordinate(36.0500, 50.5000),
            qazvin
        )
        val totalGeomDist = RouteIntegrityValidator.computePolylineDistanceMeters(roadPoints)

        // 1. Valid forward geometry passes
        val forwardCheck = RouteIntegrityValidator.validateDecodedRouteGeometry(
            polyline = roadPoints,
            summaryDistanceMeters = totalGeomDist,
            summaryDurationSeconds = 5400.0,
            requestedOrigin = tehran,
            requestedDestination = qazvin
        )
        assertTrue(forwardCheck.isValid)

        // 2. Reversed geometry (qazvin -> tehran when tehran -> qazvin was requested) fails with ROUTE_GEOMETRY_REVERSED
        val reversedCheck = RouteIntegrityValidator.validateDecodedRouteGeometry(
            polyline = roadPoints.reversed(),
            summaryDistanceMeters = totalGeomDist,
            summaryDurationSeconds = 5400.0,
            requestedOrigin = tehran,
            requestedDestination = qazvin
        )
        assertFalse(reversedCheck.isValid)
        assertTrue(reversedCheck.rejectionReason!!.contains("ROUTE_GEOMETRY_REVERSED"))

        // 3. Summary distance wildly inconsistent with geometry fails with GEOMETRY_DISTANCE_MISMATCH
        val mismatchCheck = RouteIntegrityValidator.validateDecodedRouteGeometry(
            polyline = roadPoints,
            summaryDistanceMeters = totalGeomDist * 4.0,
            summaryDurationSeconds = 5400.0,
            requestedOrigin = tehran,
            requestedDestination = qazvin
        )
        assertFalse(mismatchCheck.isValid)
        assertTrue(mismatchCheck.rejectionReason!!.contains("GEOMETRY_DISTANCE_MISMATCH"))
    }

    @Test
    fun `Valhalla and GraphHopper providers preserve speedLimitKmh = 0 when backend omits speed limit`() {
        val roadPoints = listOf(tehran, karaj, qazvin)
        val shape6 = PolylineCodec.encode(roadPoints, precision = 6)
        val valhallaProvider = ValhallaRoutingProvider(baseUrl = "https://valhalla.example.org")
        val valhallaJson = """
            {
              "trip": {
                "status": 0,
                "summary": { "length": 145.0, "time": 5400.0 },
                "legs": [
                  {
                    "shape": "$shape6",
                    "maneuvers": [
                      { "type": 1, "instruction": "حرکت", "length": 145.0, "time": 5400.0, "begin_shape_index": 0 }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
        val parsedValhalla = valhallaProvider.parseValhallaPayload(valhallaJson, tehran, qazvin).getOrThrow()
        assertEquals(0, parsedValhalla.route.maneuvers.first().speedLimitKmh)
        assertTrue(parsedValhalla.route.segments.all { it.speedLimitKmh == 0 })

        val ghProvider = GraphHopperRoutingProvider(baseUrl = "https://gh.example.org")
        val ghJson = """
            {
              "paths": [
                {
                  "distance": 145000.0,
                  "time": 5400000,
                  "points_encoded": false,
                  "points": {
                    "type": "LineString",
                    "coordinates": [
                      [${tehran.longitude}, ${tehran.latitude}],
                      [${karaj.longitude}, ${karaj.latitude}],
                      [${qazvin.longitude}, ${qazvin.latitude}]
                    ]
                  },
                  "instructions": [
                    { "sign": 0, "text": "ادامه مسیر", "distance": 145000.0, "time": 5400000, "interval": [0, 2] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val parsedGh = ghProvider.parseGraphHopperPayload(ghJson, tehran, qazvin).getOrThrow()
        assertEquals(0, parsedGh.route.maneuvers.first().speedLimitKmh)
        assertTrue(parsedGh.route.segments.all { it.speedLimitKmh == 0 })
    }
}
