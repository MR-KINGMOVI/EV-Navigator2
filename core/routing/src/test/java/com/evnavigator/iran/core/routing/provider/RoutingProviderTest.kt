package com.evnavigator.iran.core.routing.provider

import com.evnavigator.iran.core.routing.model.*
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying the RoutingProvider architecture:
 * OSRM, OfflineRoadGraph, Composite fallback, and coordinate validation.
 */
class RoutingProviderTest {

    @Test
    fun testOsrmProviderValidatesIdenticalCoordinates() = runTest {
        val provider = OsrmRoutingProvider()

        // Identical origin and destination must fail validation
        val sameResult = provider.calculateRoute(GeoCoordinate(35.7, 51.4), GeoCoordinate(35.7, 51.4))
        assertTrue(sameResult.isFailure)
        assertTrue(sameResult.exceptionOrNull() is RoutingError.InvalidInput)
    }

    @Test
    fun testOfflineRoadGraphProviderCalculatesVerifiedCorridor() = runTest {
        val provider = OfflineRoadGraphRoutingProvider()
        val tehran = GeoCoordinate(35.6997, 51.3380)
        val qom = GeoCoordinate(34.6401, 50.8764)

        val result = provider.calculateRoute(tehran, qom)
        assertTrue("Offline highway graph routing must succeed for Tehran-Qom", result.isSuccess)

        val routeResult = result.getOrThrow()
        assertTrue("Distance should be approximately 148km", routeResult.totalDistanceMeters > 100_000.0)
        assertTrue("Polyline points should be present", routeResult.polyline.size >= 2)
        assertEquals(RoutingProviderType.OFFLINE_ROAD_GRAPH, routeResult.provenance.providerType)
        assertFalse("Offline route must have isOnline = false", routeResult.provenance.isOnline)
    }

    @Test
    fun testCompositeProviderFallsBackGracefullyToOffline() = runTest {
        val offlineProvider = OfflineRoadGraphRoutingProvider()
        val composite = CompositeRoutingProvider(
            primaryOnlineProvider = OsrmRoutingProvider(baseUrl = "https://invalid-nonexistent-domain-test.local"),
            fallbackOfflineProvider = offlineProvider,
            isOnlineEnabled = true
        )

        val tehran = GeoCoordinate(35.6997, 51.3380)
        val isfahan = GeoCoordinate(32.6370, 51.6830)

        val result = composite.calculateRoute(tehran, isfahan)
        assertTrue("Composite must succeed using offline fallback when online fails", result.isSuccess)

        val routeResult = result.getOrThrow()
        assertEquals(RouteValidationStatus.FALLBACK_OFFLINE, routeResult.provenance.validationStatus)
        assertTrue("Warnings must mention fallback", routeResult.provenance.warnings.any { it.contains("آفلاین") })
    }

    @Test
    fun testRoutingEngineReturnsAuthenticResultFromProvider() = runTest {
        val repo = object : com.evnavigator.iran.core.routing.repository.RoutingRepository {
            override suspend fun findNearestNode(coordinate: GeoCoordinate): Result<RoadNode> =
                Result.success(RoadNode("node_1", coordinate))
            override suspend fun getRoadGraph(): Result<RoadGraph> =
                Result.success(RoadGraph(emptyMap(), emptyMap(), emptyMap()))
            override suspend fun getRoadSegmentsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): Result<List<com.evnavigator.iran.domain.model.RoadSegment>> =
                Result.success(emptyList())
            override suspend fun getElevationDataForRoute(route: Route): Result<List<com.evnavigator.iran.domain.model.ElevationPoint>> =
                Result.success(emptyList())
            override suspend fun getWeatherAtCoordinate(coordinate: GeoCoordinate): Result<com.evnavigator.iran.domain.model.WeatherSnapshot> =
                Result.failure(UnsupportedOperationException())
            override suspend fun getChargingStationsNear(coordinate: GeoCoordinate, maxDistanceMeters: Double): Result<List<com.evnavigator.iran.domain.model.Charger>> =
                Result.success(emptyList())
        }
        val cost = object : CostFunction {
            override fun calculateCost(edge: RoadEdge, profile: RoutingProfile, distance: Distance): Double = distance.meters
            override fun getRoadTypeWeight(roadClass: RoadClass): Double = 1.0
            override fun getSpeedMultiplier(travelMode: TravelMode): Double = 1.0
        }
        val heuristic = object : HeuristicFunction {
            override fun estimateCost(from: GeoCoordinate, to: GeoCoordinate): Distance = Distance.fromMeters(0.0)
            override fun estimateTime(from: GeoCoordinate, to: GeoCoordinate, profile: RoutingProfile): Double = 0.0
        }

        val testProvider = object : RoutingProvider {
            override val providerId = "MOCK_ACCURATE_PROVIDER"
            override val providerType = RoutingProviderType.OSRM
            override suspend fun calculateRoute(origin: GeoCoordinate, destination: GeoCoordinate, preferences: RoutePreferences): Result<RoutingProviderResult> {
                return Result.success(
                    RoutingProviderResult(
                        polyline = listOf(origin, GeoCoordinate((origin.latitude + destination.latitude) / 2, (origin.longitude + destination.longitude) / 2), destination),
                        totalDistanceMeters = 245000.0,
                        totalDurationSeconds = 7200.0,
                        provenance = RouteProvenance(providerId, providerType, isOnline = true),
                        routeSummary = "Freeway 5"
                    )
                )
            }
            override suspend fun calculateAlternativeRoutes(origin: GeoCoordinate, destination: GeoCoordinate, preferences: RoutePreferences, maxAlternatives: Int): Result<List<RoutingProviderResult>> {
                return Result.success(listOf(calculateRoute(origin, destination, preferences).getOrThrow()))
            }
        }

        val engine = com.evnavigator.iran.core.routing.engine.RoutingEngine(repo, cost, heuristic, testProvider)
        val optimalResult = engine.findOptimalRoute(GeoCoordinate(35.6997, 51.3380), GeoCoordinate(34.6401, 50.8764))
        assertTrue(optimalResult.isSuccess)

        val routeResult = optimalResult.getOrThrow()
        assertEquals(245000.0, routeResult.totalDistance.meters, 0.1)
        assertEquals(7200.0, routeResult.estimatedTime, 0.1)
        assertEquals("MOCK_ACCURATE_PROVIDER", routeResult.metadata["provider"])
    }
}
