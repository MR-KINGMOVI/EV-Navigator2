package com.evnavigator.iran.core.routing

import com.evnavigator.iran.core.routing.engine.OsrmOnlineRouteClient
import com.evnavigator.iran.core.routing.engine.OsrmParseOutcome
import com.evnavigator.iran.core.routing.engine.OsrmRouteCompleteness
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.domain.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V13.2 Section 1 Mandatory Behavioral Test Suite for [OsrmOnlineRouteClient] and [RealRoadManeuver].
 *
 * Covers all 11 required scenarios:
 * 1. Valid OSRM response with geometry + steps -> COMPLETE route with turn-by-turn maneuvers.
 * 2. OSRM response with valid geometry + distance + duration but missing/empty `steps` ->
 *    accepted ONLY as incomplete route (`INSUFFICIENT_ROUTE_DETAILS`), `hasTurnByTurnGuidance = false`, 0 maneuvers.
 * 3. OSRM response without `routes` -> `NoRouteFound`.
 * 4. Malformed JSON -> `MalformedJsonFailure` (never swallowed).
 * 5. Invalid/missing `distance` -> `InvalidDistance` (never `polyline.size * 50.0` or Haversine).
 * 6. Invalid/missing `duration` -> `InvalidDuration` (never `polyline.size * 2.0` or `distance / 25.0`).
 * 7. Empty `geometry` -> `EmptyOrIncompleteGeometry`.
 * 8. Incomplete `geometry` (1 point) -> `EmptyOrIncompleteGeometry`.
 * 9. Malformed step / step parsing error -> `StepParsingFailure` (never silently ignored).
 * 10. Zero fabricated `RealRoadManeuver` when `steps` are missing.
 * 11. Zero `speedLimitKmh = 100` default or road-name speed limit guessing (`120/100/80/50/30`).
 */
class V132OsrmAndRoutingBehavioralTest {

    private val client = OsrmOnlineRouteClient()
    private val origin = GeoCoordinate(35.6892, 51.3890, 1190.0)
    private val mid = GeoCoordinate(35.7500, 51.2000, 1240.0)
    private val destination = GeoCoordinate(35.8327, 50.9915, 1310.0)

    @Test
    fun `1 - valid OSRM response produces COMPLETE route with real maneuvers and unknown speed limit unless explicitly provided`() {
        val validJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 42500.0,
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [51.3890, 35.6892],
                      [51.2000, 35.7500],
                      [50.9915, 35.8327]
                    ]
                  },
                  "legs": [
                    {
                      "summary": "آزادراه تهران - کرج",
                      "steps": [
                        {
                          "name": "آزادراه شیخ فضل‌الله نوری",
                          "distance": 22000.0,
                          "duration": 980.0,
                          "maneuver": {
                            "type": "depart",
                            "location": [51.3890, 35.6892]
                          }
                        },
                        {
                          "name": "آزادراه تهران - کرج",
                          "distance": 20500.0,
                          "duration": 940.0,
                          "maneuver": {
                            "type": "arrive",
                            "location": [50.9915, 35.8327]
                          }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val outcome = client.parseOsrmJsonStrict(validJson, origin, destination, "مسیر اول (سریع‌ترین)")
        assertTrue("Expected OsrmParseOutcome.Success, got $outcome", outcome is OsrmParseOutcome.Success)
        val success = outcome as OsrmParseOutcome.Success
        assertEquals(OsrmRouteCompleteness.COMPLETE, success.completeness)
        assertTrue(success.hasTurnByTurnGuidance)
        assertEquals(42500.0, success.route.totalDistanceMeters, 1e-3)
        assertEquals(1920.0, success.route.totalDurationSeconds, 1e-3)
        assertEquals(2, success.route.maneuvers.size)
        assertEquals("COMPLETE", success.route.completenessStatus)
        assertTrue(success.route.hasTurnByTurnGuidance)

        // Verify no road-name speed limit guessing (freeway name must NOT invent 120 or 100 km/h)
        for (maneuver in success.route.maneuvers) {
            assertEquals("Road name must never invent speedLimitKmh = 100 or 120", 0, maneuver.speedLimitKmh)
            assertNull(maneuver.legalSpeedLimitKmh)
            assertFalse(maneuver.hasVerifiedSpeedLimit)
        }
    }

    @Test
    fun `2 and 10 - OSRM response with geometry but without steps returns INSUFFICIENT_ROUTE_DETAILS and zero fake maneuvers`() {
        val noStepsJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 42500.0,
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [51.3890, 35.6892],
                      [51.2000, 35.7500],
                      [50.9915, 35.8327]
                    ]
                  },
                  "legs": [
                    {
                      "summary": "آزادراه تهران - کرج",
                      "steps": []
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val outcome = client.parseOsrmJsonStrict(noStepsJson, origin, destination, "مسیر بدون گام")
        assertTrue("Expected Success with INSUFFICIENT_ROUTE_DETAILS, got $outcome", outcome is OsrmParseOutcome.Success)
        val success = outcome as OsrmParseOutcome.Success
        assertEquals(OsrmRouteCompleteness.INSUFFICIENT_ROUTE_DETAILS, success.completeness)
        assertFalse("Turn-by-turn guidance must be disabled when steps are missing", success.hasTurnByTurnGuidance)
        assertTrue("Zero fake maneuvers must be created when steps are missing", success.route.maneuvers.isEmpty())
        assertEquals("INSUFFICIENT_ROUTE_DETAILS", success.route.completenessStatus)
        assertFalse(success.route.hasTurnByTurnGuidance)
        assertTrue(success.warnings.any { it.contains("INSUFFICIENT_ROUTE_DETAILS") })
    }

    @Test
    fun `3 - OSRM response without routes returns NoRouteFound`() {
        val emptyRoutesJson = """{"code": "Ok", "routes": []}"""
        val outcome = client.parseOsrmJsonStrict(emptyRoutesJson, origin, destination, "مسیر خالی")
        assertTrue("Expected NoRouteFound, got $outcome", outcome is OsrmParseOutcome.Failure.NoRouteFound)
    }

    @Test
    fun `4 - malformed JSON returns MalformedJsonFailure and is never swallowed`() {
        val corruptedJson = """{"code": "Ok", "routes": [{"distance": 42500.0, "geometry": BROKEN_JSON"""
        val outcome = client.parseOsrmJsonStrict(corruptedJson, origin, destination, "مسیر خراب")
        assertTrue("Expected MalformedJsonFailure, got $outcome", outcome is OsrmParseOutcome.Failure.MalformedJsonFailure)
    }

    @Test
    fun `5 - invalid or missing distance returns InvalidDistance and never uses polyline size x 50m`() {
        val missingDistanceJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[51.3890, 35.6892], [50.9915, 35.8327]]
                  }
                }
              ]
            }
        """.trimIndent()
        val outcomeMissing = client.parseOsrmJsonStrict(missingDistanceJson, origin, destination, "مسیر")
        assertTrue("Expected InvalidDistance for missing distance, got $outcomeMissing", outcomeMissing is OsrmParseOutcome.Failure.InvalidDistance)

        val negativeDistanceJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": -100.0,
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[51.3890, 35.6892], [50.9915, 35.8327]]
                  }
                }
              ]
            }
        """.trimIndent()
        val outcomeNeg = client.parseOsrmJsonStrict(negativeDistanceJson, origin, destination, "مسیر")
        assertTrue("Expected InvalidDistance for negative distance, got $outcomeNeg", outcomeNeg is OsrmParseOutcome.Failure.InvalidDistance)
    }

    @Test
    fun `6 - invalid or missing duration returns InvalidDuration and never uses polyline size x 2s`() {
        val zeroDurationJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 42500.0,
                  "duration": 0.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[51.3890, 35.6892], [50.9915, 35.8327]]
                  }
                }
              ]
            }
        """.trimIndent()
        val outcome = client.parseOsrmJsonStrict(zeroDurationJson, origin, destination, "مسیر")
        assertTrue("Expected InvalidDuration, got $outcome", outcome is OsrmParseOutcome.Failure.InvalidDuration)
    }

    @Test
    fun `7 and 8 - empty or single-point geometry returns EmptyOrIncompleteGeometry`() {
        val emptyGeometryJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 42500.0,
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": []
                  }
                }
              ]
            }
        """.trimIndent()
        val emptyOutcome = client.parseOsrmJsonStrict(emptyGeometryJson, origin, destination, "مسیر")
        assertTrue(emptyOutcome is OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry)

        val singlePointGeometryJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 42500.0,
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[51.3890, 35.6892]]
                  }
                }
              ]
            }
        """.trimIndent()
        val singleOutcome = client.parseOsrmJsonStrict(singlePointGeometryJson, origin, destination, "مسیر")
        assertTrue(singleOutcome is OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry)
    }

    @Test
    fun `9 - malformed step inside legs returns StepParsingFailure instead of silently ignoring`() {
        val brokenStepJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 42500.0,
                  "duration": 1920.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[51.3890, 35.6892], [50.9915, 35.8327]]
                  },
                  "legs": [
                    {
                      "steps": [
                        {
                          "name": "گام ناقص بدون فاصله و زمان",
                          "maneuver": { "type": "depart", "location": [51.3890, 35.6892] }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val outcome = client.parseOsrmJsonStrict(brokenStepJson, origin, destination, "مسیر")
        assertTrue("Expected StepParsingFailure when step omits distance/duration, got $outcome", outcome is OsrmParseOutcome.Failure.StepParsingFailure)
    }

    @Test
    fun `11 - RealRoadManeuver default speedLimitKmh is 0 (UNKNOWN) and never 100`() {
        val maneuver = RealRoadManeuver(
            index = 0,
            instructionFa = "حرکت به سمت شمال",
            maneuverType = RealManeuverType.DEPART,
            coordinate = origin,
            distanceFromPreviousMeters = 1200.0,
            durationSeconds = 60.0,
            roadName = "بزرگراه همت"
        )
        assertEquals("Default speedLimitKmh must be 0 (UNKNOWN), never 100", 0, maneuver.speedLimitKmh)
        assertNull("legalSpeedLimitKmh must be null when speed limit is unverified", maneuver.legalSpeedLimitKmh)
        assertFalse(maneuver.hasVerifiedSpeedLimit)
    }
}
