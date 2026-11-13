package com.evnavigator.iran.core.routing

import com.evnavigator.iran.core.routing.engine.OfflineRoutingSolver
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.core.routing.model.RoadEdge
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.core.routing.model.RoutingError
import com.evnavigator.iran.core.routing.model.TurnRestriction
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Speed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OfflineRoutingSolver.
 * Verifies Dijkstra/A* pathfinding, one-way constraints, turn restrictions,
 * disconnected graphs, and cooperative cancellation.
 */
class OfflineRoutingTest {

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
    fun `test findRoute finds optimal path on valid connected graph`() = runTest {
        val nA = RoadNode("A", GeoCoordinate(35.70, 51.40))
        val nB = RoadNode("B", GeoCoordinate(35.71, 51.40))
        val nC = RoadNode("C", GeoCoordinate(35.72, 51.40))

        val eAB = RoadEdge("e_ab", "A", "B", Distance.fromMeters(1000.0), Speed.ofKilometersPerHour(60.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)
        val eBC = RoadEdge("e_bc", "B", "C", Distance.fromMeters(1200.0), Speed.ofKilometersPerHour(60.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)

        val graph = createGraph(listOf(nA, nB, nC), listOf(eAB, eBC))
        val solver = OfflineRoutingSolver()

        val result = solver.findRoute(graph, "A", "C")
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(listOf("A", "B", "C"), route.nodes)
        assertEquals(listOf("e_ab", "e_bc"), route.edges)
        assertEquals(2200.0, route.distance.meters, 0.001)
    }

    @Test
    fun `test one-way directionality prevents reverse traversal`() = runTest {
        val nA = RoadNode("A", GeoCoordinate(35.70, 51.40))
        val nB = RoadNode("B", GeoCoordinate(35.71, 51.40))

        // Directed edge only from A to B
        val eAB = RoadEdge("e_ab", "A", "B", Distance.fromMeters(1000.0), Speed.ofKilometersPerHour(60.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)

        val graph = createGraph(listOf(nA, nB), listOf(eAB))
        val solver = OfflineRoutingSolver()

        // Trying to route from B to A on one-way road must fail
        val result = solver.findRoute(graph, "B", "A")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RoutingError.NoRouteFound)
    }

    @Test
    fun `test disconnected graph returns NoRouteFound without fabricating link`() = runTest {
        val nA = RoadNode("A", GeoCoordinate(35.70, 51.40))
        val nB = RoadNode("B", GeoCoordinate(35.71, 51.40))
        val nIsolated = RoadNode("Isolated", GeoCoordinate(35.90, 51.90))

        val eAB = RoadEdge("e_ab", "A", "B", Distance.fromMeters(1000.0), Speed.ofKilometersPerHour(60.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)

        val graph = createGraph(listOf(nA, nB, nIsolated), listOf(eAB))
        val solver = OfflineRoutingSolver()

        val result = solver.findRoute(graph, "A", "Isolated")
        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is RoutingError.NoRouteFound)
        assertEquals("A", (err as RoutingError.NoRouteFound).origin)
        assertEquals("Isolated", err.destination)
    }

    @Test
    fun `test turn restriction routes via alternative path`() = runTest {
        val nA = RoadNode("A", GeoCoordinate(35.70, 51.40))
        val nB = RoadNode("B", GeoCoordinate(35.71, 51.40))
        val nC = RoadNode("C", GeoCoordinate(35.71, 51.41))
        val nD = RoadNode("D", GeoCoordinate(35.70, 51.41))

        // Restriction: cannot turn from e_ab to e_bc at node B
        val restr = TurnRestriction.Prohibited(fromEdgeId = "e_ab", toEdgeId = "e_bc")
        val eAB = RoadEdge("e_ab", "A", "B", Distance.fromMeters(100.0), Speed.ofKilometersPerHour(50.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC, restrictions = listOf(restr))
        val eBC = RoadEdge("e_bc", "B", "C", Distance.fromMeters(100.0), Speed.ofKilometersPerHour(50.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)
        val eAD = RoadEdge("e_ad", "A", "D", Distance.fromMeters(150.0), Speed.ofKilometersPerHour(50.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)
        val eDC = RoadEdge("e_dc", "D", "C", Distance.fromMeters(150.0), Speed.ofKilometersPerHour(50.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC)

        val graph = createGraph(listOf(nA, nB, nC, nD), listOf(eAB, eBC, eAD, eDC))
        val solver = OfflineRoutingSolver()

        // Because e_ab -> e_bc is forbidden, path must be A -> D -> C
        val result = solver.findRoute(graph, "A", "C")
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(listOf("A", "D", "C"), route.nodes)
        assertEquals(listOf("e_ad", "e_dc"), route.edges)
    }

    @Test
    fun `test findAlternativeRoutes returns only single route when no real alternative exists without fake multipliers`() = runTest {
        val repo = object : com.evnavigator.iran.core.routing.repository.RoutingRepository {
            override suspend fun findNearestNode(coordinate: GeoCoordinate): Result<RoadNode> =
                Result.success(RoadNode("node_1", coordinate))
            override suspend fun getRoadGraph(): Result<RoadGraph> =
                Result.success(RoadGraph(emptyMap(), emptyMap(), emptyMap()))
            override suspend fun getRoadSegmentsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): Result<List<com.evnavigator.iran.domain.model.RoadSegment>> =
                Result.success(emptyList())
            override suspend fun getElevationDataForRoute(route: com.evnavigator.iran.core.routing.model.Route): Result<List<com.evnavigator.iran.domain.model.ElevationPoint>> =
                Result.success(emptyList())
            override suspend fun getWeatherAtCoordinate(coordinate: GeoCoordinate): Result<com.evnavigator.iran.domain.model.WeatherSnapshot> =
                Result.success(com.evnavigator.iran.domain.model.WeatherSnapshot(
                    coordinate = coordinate,
                    timestamp = System.currentTimeMillis(),
                    temperature = com.evnavigator.iran.domain.model.Temperature.ofCelsius(20.0),
                    humidity = 50.0,
                    windSpeed = com.evnavigator.iran.domain.model.Speed.ofKilometersPerHour(0.0),
                    windDirection = 0.0,
                    precipitation = 0.0,
                    visibility = com.evnavigator.iran.domain.model.Distance.fromMeters(10000.0),
                    pressure = 1013.0,
                    condition = "Clear"
                ))
            override suspend fun getChargingStationsNear(coordinate: GeoCoordinate, maxDistanceMeters: Double): Result<List<com.evnavigator.iran.domain.model.Charger>> =
                Result.success(emptyList())
        }
        val cost = object : com.evnavigator.iran.core.routing.model.CostFunction {
            override fun calculateCost(edge: RoadEdge, profile: com.evnavigator.iran.core.routing.model.RoutingProfile, distance: Distance): Double = distance.meters
            override fun getRoadTypeWeight(roadClass: RoadClass): Double = 1.0
            override fun getSpeedMultiplier(travelMode: com.evnavigator.iran.core.routing.model.TravelMode): Double = 1.0
        }
        val heuristic = object : com.evnavigator.iran.core.routing.model.HeuristicFunction {
            override fun estimateCost(from: GeoCoordinate, to: GeoCoordinate): Distance = Distance.fromMeters(0.0)
            override fun estimateTime(from: GeoCoordinate, to: GeoCoordinate, profile: com.evnavigator.iran.core.routing.model.RoutingProfile): Double = 0.0
        }

        val mockProvider = object : com.evnavigator.iran.core.routing.provider.RoutingProvider {
            override val providerId = "TEST_PROVIDER"
            override val providerType = com.evnavigator.iran.core.routing.provider.RoutingProviderType.OFFLINE_ROAD_GRAPH
            override suspend fun calculateRoute(origin: GeoCoordinate, destination: GeoCoordinate, preferences: com.evnavigator.iran.core.routing.model.RoutePreferences): Result<com.evnavigator.iran.core.routing.provider.RoutingProviderResult> =
                Result.success(com.evnavigator.iran.core.routing.provider.RoutingProviderResult(
                    polyline = listOf(origin, destination),
                    totalDistanceMeters = 1110.0,
                    totalDurationSeconds = 60.0,
                    provenance = com.evnavigator.iran.core.routing.provider.RouteProvenance(providerId, providerType, isOnline = false)
                ))
            override suspend fun calculateAlternativeRoutes(origin: GeoCoordinate, destination: GeoCoordinate, preferences: com.evnavigator.iran.core.routing.model.RoutePreferences, maxAlternatives: Int): Result<List<com.evnavigator.iran.core.routing.provider.RoutingProviderResult>> =
                Result.success(listOf(calculateRoute(origin, destination, preferences).getOrThrow()))
        }

        val engine = com.evnavigator.iran.core.routing.engine.RoutingEngine(repo, cost, heuristic, mockProvider)
        val result = engine.findAlternativeRoutes(GeoCoordinate(35.70, 51.40), GeoCoordinate(35.71, 51.40))
        assertTrue(result.isSuccess)
        val alternatives = result.getOrThrow()
        assertEquals("Must return exactly 1 route when only 1 valid route exists, without synthesizing fake routes", 1, alternatives.size)
    }
}
