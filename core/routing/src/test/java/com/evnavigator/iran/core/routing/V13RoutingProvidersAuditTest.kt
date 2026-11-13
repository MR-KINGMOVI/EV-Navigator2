package com.evnavigator.iran.core.routing

import com.evnavigator.iran.core.routing.codec.PolylineCodec
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RoutePreferences
import com.evnavigator.iran.core.routing.model.RoutingError
import com.evnavigator.iran.core.routing.network.CircuitState
import com.evnavigator.iran.core.routing.network.NetworkRoutingHttpClient
import com.evnavigator.iran.core.routing.network.RoutingCircuitBreaker
import com.evnavigator.iran.core.routing.provider.AlternativeRouteSupport
import com.evnavigator.iran.core.routing.provider.GraphHopperRoutingProvider
import com.evnavigator.iran.core.routing.provider.ValhallaRoutingProvider
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.RoutingPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral regression test suite for V13.0 Routing Audit (Items 1–5, 28–39, 91–93, 165–171).
 */
class V13RoutingProvidersAuditTest {

    private val tehranOrigin = GeoCoordinate(35.6892, 51.3890)
    private val karajMid = GeoCoordinate(35.8327, 50.9915)
    private val qazvinDest = GeoCoordinate(36.2688, 50.0041)

    @Test
    fun `PolylineCodec round-trips precision-6 Valhalla and precision-5 standard polylines`() {
        val sampleCoords = listOf(
            tehranOrigin,
            GeoCoordinate(35.7210, 51.2450),
            karajMid,
            GeoCoordinate(36.0512, 50.5123),
            qazvinDest
        )

        val encoded6 = PolylineCodec.encode(sampleCoords, precision = 6)
        val decoded6 = PolylineCodec.decode(encoded6, precision = 6)
        assertEquals(sampleCoords.size, decoded6.size)
        for (i in sampleCoords.indices) {
            assertEquals(sampleCoords[i].latitude, decoded6[i].latitude, 1e-5)
            assertEquals(sampleCoords[i].longitude, decoded6[i].longitude, 1e-5)
        }

        val encoded5 = PolylineCodec.encode(sampleCoords, precision = 5)
        val decoded5 = PolylineCodec.decode(encoded5, precision = 5)
        assertEquals(sampleCoords.size, decoded5.size)
        for (i in sampleCoords.indices) {
            assertEquals(sampleCoords[i].latitude, decoded5[i].latitude, 1e-4)
            assertEquals(sampleCoords[i].longitude, decoded5[i].longitude, 1e-4)
        }
    }

    @Test
    fun `ValhallaRoutingProvider decodes precision-6 shape and rejects missing shape without straight-line fallback`() {
        val provider = ValhallaRoutingProvider(baseUrl = "https://valhalla.example.org")
        val roadPoints = listOf(
            tehranOrigin,
            GeoCoordinate(35.7400, 51.2100),
            karajMid,
            GeoCoordinate(36.0800, 50.4500),
            qazvinDest
        )
        val shape6 = PolylineCodec.encode(roadPoints, precision = 6)

        val validValhallaJson = """
            {
              "trip": {
                "status": 0,
                "summary": { "length": 152.4, "time": 6420.0, "summary": "آزادراه تهران-قزوین" },
                "legs": [
                  {
                    "shape": "$shape6",
                    "maneuvers": [
                      { "type": 1, "instruction": "حرکت در آزادراه", "length": 50.0, "time": 2100.0, "begin_shape_index": 0, "street_names": ["آزادراه تهران-کرج"] },
                      { "type": 26, "instruction": "در میدان از خروجی ۲ خارج شوید", "length": 102.4, "time": 4320.0, "begin_shape_index": 2, "roundabout_exit_count": 2 },
                      { "type": 999, "instruction": "مانور ناشناخته", "length": 0.0, "time": 0.0, "begin_shape_index": 4 }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = provider.parseValhallaPayload(validValhallaJson, tehranOrigin, qazvinDest)
        assertTrue("Valid Valhalla payload with precision-6 shape must succeed", parsed.isSuccess)
        val enriched = parsed.getOrThrow()
        assertEquals(roadPoints.size, enriched.route.polyline.size)
        assertEquals(karajMid.latitude, enriched.route.maneuvers[1].coordinate.latitude, 1e-4)
        assertEquals(2, enriched.route.maneuvers[1].roundaboutExitNumber)
        assertEquals(RealManeuverType.UNKNOWN, enriched.route.maneuvers[2].maneuverType)

        // Missing shape must fail with RouteGeometryMissing (never 2-point straight line)
        val missingShapeJson = """
            {
              "trip": {
                "status": 0,
                "summary": { "length": 152.4, "time": 6420.0 },
                "legs": [ { "maneuvers": [] } ]
              }
            }
        """.trimIndent()
        val failed = provider.parseValhallaPayload(missingShapeJson, tehranOrigin, qazvinDest)
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull() is RoutingError.RouteGeometryMissing)
    }

    @Test
    fun `GraphHopperRoutingProvider rejects empty or missing points without straight-line fallback`() {
        val provider = GraphHopperRoutingProvider(baseUrl = "https://gh.example.org", apiKey = "secret_key_123")
        val missingPointsJson = """
            {
              "paths": [
                {
                  "distance": 150000.0,
                  "time": 6200000,
                  "points": { "type": "LineString", "coordinates": [] }
                }
              ]
            }
        """.trimIndent()

        val result = provider.parseGraphHopperPayload(missingPointsJson, tehranOrigin, qazvinDest)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RoutingError.RouteGeometryMissing)
    }

    @Test
    fun `Valhalla and GraphHopper map RoutePreferences into backend request parameters`() {
        val prefs = RoutePreferences(
            optimizeFor = RoutingPreference.SHORTEST,
            avoidTolls = true,
            avoidFerries = true,
            avoidUnpavedRoads = true
        )

        val valhalla = ValhallaRoutingProvider(baseUrl = "https://valhalla.example.org")
        val appliedValhalla = mutableListOf<String>()
        val valhallaJson = valhalla.buildValhallaRequestJson(
            origin = tehranOrigin,
            destination = qazvinDest,
            waypoints = emptyList(),
            preferences = prefs,
            requestedAlternates = 2,
            appliedPrefs = appliedValhalla
        )
        val autoOpts = valhallaJson.getJSONObject("costing_options").getJSONObject("auto")
        assertEquals(0.0, autoOpts.getDouble("use_tolls"), 1e-6)
        assertEquals(0.0, autoOpts.getDouble("use_ferries"), 1e-6)
        assertEquals(0.0, autoOpts.getDouble("use_tracks"), 1e-6)
        assertTrue(autoOpts.getBoolean("shortest"))
        assertEquals(2, valhallaJson.getInt("alternates"))

        val gh = GraphHopperRoutingProvider(baseUrl = "https://gh.example.org", apiKey = "my_secret_token")
        val appliedGh = mutableListOf<String>()
        val ghParams = gh.buildGraphHopperQueryParams(
            origin = tehranOrigin,
            destination = qazvinDest,
            waypoints = emptyList(),
            preferences = prefs,
            maxAlternatives = 3,
            appliedPrefs = appliedGh
        ).toMap()
        assertEquals("true", ghParams["ch.disable"])
        assertTrue(ghParams["avoid"]!!.contains("toll"))
        assertTrue(ghParams["avoid"]!!.contains("ferry"))
        assertEquals("shortest", ghParams["weighting"])
        assertEquals("alternative_route", ghParams["algorithm"])
    }

    @Test
    fun `NetworkRoutingHttpClient masks API keys and CircuitBreaker transitions CLOSED to OPEN to HALF_OPEN`() {
        val rawUrl = "https://gh.example.org/route?point=35.6,51.3&key=SUPER_SECRET_KEY_999&locale=fa"
        val masked = NetworkRoutingHttpClient.maskSecrets(rawUrl)
        assertFalse(masked.contains("SUPER_SECRET_KEY_999"))
        assertTrue(masked.contains("key=***REDACTED***"))

        var fakeNow = 1_000_000L
        val cb = RoutingCircuitBreaker(failureThreshold = 2, openCooldownMs = 10_000L, clockMs = { fakeNow })
        assertEquals(CircuitState.CLOSED, cb.currentState("TestProvider"))
        cb.recordFailure("TestProvider")
        assertEquals(CircuitState.CLOSED, cb.currentState("TestProvider"))
        cb.recordFailure("TestProvider")
        assertEquals(CircuitState.OPEN, cb.currentState("TestProvider"))
        assertFalse(cb.allowRequest("TestProvider"))

        fakeNow += 11_000L
        assertEquals(CircuitState.HALF_OPEN, cb.currentState("TestProvider"))
        assertTrue(cb.allowRequest("TestProvider"))
        cb.recordSuccess("TestProvider")
        assertEquals(CircuitState.CLOSED, cb.currentState("TestProvider"))
    }
}
