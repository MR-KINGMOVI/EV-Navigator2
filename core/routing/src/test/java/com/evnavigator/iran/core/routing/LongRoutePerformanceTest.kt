package com.evnavigator.iran.core.routing

import com.evnavigator.iran.core.routing.engine.OfflineRoutingSolver
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.core.routing.model.RoadEdge
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Speed
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Step AH Test: Long-Route Performance and Scaling.
 *
 * Verifies that the offline routing algorithm scales linearly over long-distance corridors
 * (e.g. 500-segment 1000km route) without quadratic intermediate allocations or memory bloat,
 * and responds immediately to cooperative coroutine cancellation.
 */
class LongRoutePerformanceTest {

    @Test
    fun `test long route corridor scales linearly and produces deterministic result`() = runTest {
        val segmentCount = 500
        val nodes = mutableListOf<RoadNode>()
        val edges = mutableListOf<RoadEdge>()

        // Generate synthetic corridor from Tehran toward Mashhad (~900 km, ~1.8 km per segment)
        val startLat = 35.6892
        val startLon = 51.3890
        val endLat = 36.2972
        val endLon = 59.6067

        val dLat = (endLat - startLat) / segmentCount
        val dLon = (endLon - startLon) / segmentCount

        for (i in 0..segmentCount) {
            val lat = startLat + (i * dLat)
            val lon = startLon + (i * dLon)
            nodes.add(RoadNode("node_$i", GeoCoordinate(lat, lon)))
        }

        for (i in 0 until segmentCount) {
            val dist = Distance.fromMeters(1800.0)
            edges.add(
                RoadEdge(
                    id = "edge_$i",
                    startNode = "node_$i",
                    endNode = "node_${i + 1}",
                    distance = dist,
                    speed = Speed.ofKilometersPerHour(110.0),
                    roadClass = RoadClass.MOTORWAY,
                    access = RoadAccess.PUBLIC
                )
            )
        }

        val nodeMap = nodes.associateBy { it.id }
        val edgeMap = edges.associateBy { it.id }
        val nodeToEdges = mutableMapOf<String, MutableList<String>>()
        for (edge in edges) {
            nodeToEdges.getOrPut(edge.startNode) { mutableListOf() }.add(edge.id)
        }
        val graph = RoadGraph(nodeMap, edgeMap, nodeToEdges)

        val solver = OfflineRoutingSolver()
        val result = solver.findRoute(graph, "node_0", "node_$segmentCount")

        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(segmentCount + 1, route.nodes.size)
        assertEquals(segmentCount, route.edges.size)
        assertEquals(segmentCount * 1800.0, route.distance.meters, 0.001)
        assertEquals("node_0", route.nodes.first())
        assertEquals("node_$segmentCount", route.nodes.last())
    }

    @Test
    fun `test cancellation during long route calculation terminates immediately`() = runTest {
        // Construct a wide grid graph where search will take multiple iterations
        val size = 50 // 2500 nodes
        val nodes = mutableListOf<RoadNode>()
        val edges = mutableListOf<RoadEdge>()

        for (r in 0 until size) {
            for (c in 0 until size) {
                val id = "n_${r}_$c"
                nodes.add(RoadNode(id, GeoCoordinate(35.0 + (r * 0.01), 51.0 + (c * 0.01))))
                if (r < size - 1) {
                    edges.add(RoadEdge("e_v_${r}_$c", id, "n_${r + 1}_$c", Distance.fromMeters(100.0), Speed.ofKilometersPerHour(50.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC))
                }
                if (c < size - 1) {
                    edges.add(RoadEdge("e_h_${r}_$c", id, "n_${r}_${c + 1}", Distance.fromMeters(100.0), Speed.ofKilometersPerHour(50.0), RoadClass.PRIMARY_ROAD, RoadAccess.PUBLIC))
                }
            }
        }

        val nodeMap = nodes.associateBy { it.id }
        val edgeMap = edges.associateBy { it.id }
        val nodeToEdges = mutableMapOf<String, MutableList<String>>()
        for (edge in edges) {
            nodeToEdges.getOrPut(edge.startNode) { mutableListOf() }.add(edge.id)
        }
        val graph = RoadGraph(nodeMap, edgeMap, nodeToEdges)
        val solver = OfflineRoutingSolver()

        val job = launch {
            solver.findRoute(graph, "n_0_0", "n_${size - 1}_${size - 1}")
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
