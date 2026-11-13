package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.*
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.core.routing.model.RoadAccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.PriorityQueue

/**
 * Production-grade offline routing solver implementing deterministic A* pathfinding.
 *
 * Strict offline rules:
 * - Search state incorporates incoming edge ID `(nodeId, incomingEdgeId)` to guarantee
 *   that turn restrictions depending on arrival edge are strictly evaluated without state loss.
 * - Never fabricates connections when a graph is disconnected.
 * - Strictly respects one-way rules, access restrictions, and turn restrictions.
 * - Traversal times and ETAs are computed per-edge according to individual speed limits and
 *   road class velocity profiles, never assuming a uniform 50 km/h.
 * - Cooperative coroutine cancellation on large graphs.
 * - Deterministic tie-breaking for reproducible routing results.
 */
class OfflineRoutingSolver {

    /**
     * Composite search state key preserving the incoming edge context.
     * This ensures that reaching a node via different incoming edges maintains
     * distinct paths when subsequent turn legality depends on the arrival edge.
     */
    data class SearchKey(
        val nodeId: String,
        val incomingEdgeId: String?
    )

    private data class PathNode(
        val key: SearchKey,
        val gCostMeters: Double,
        val fCostMeters: Double
    ) : Comparable<PathNode> {
        override fun compareTo(other: PathNode): Int {
            val cmp = this.fCostMeters.compareTo(other.fCostMeters)
            return if (cmp != 0) cmp else this.key.nodeId.compareTo(other.key.nodeId) // Deterministic tie-breaking
        }
    }

    /**
     * Finds the shortest valid path on roadGraph from startNodeId to targetNodeId.
     */
    suspend fun findRoute(
        roadGraph: RoadGraph,
        startNodeId: String,
        targetNodeId: String,
        allowedAccess: Set<RoadAccess> = setOf(RoadAccess.PUBLIC, RoadAccess.PERMIT_REQUIRED)
    ): Result<Route> {
        val startNode = roadGraph.getNode(startNodeId)
            ?: return Result.failure(RoutingError.InvalidInput("Start node $startNodeId not found in graph"))
        val targetNode = roadGraph.getNode(targetNodeId)
            ?: return Result.failure(RoutingError.InvalidInput("Target node $targetNodeId not found in graph"))

        if (startNodeId == targetNodeId) {
            return Result.failure(RoutingError.InvalidInput("Start and destination nodes must be distinct"))
        }

        val openQueue = PriorityQueue<PathNode>()
        val gScore = mutableMapOf<SearchKey, Double>()
        val cameFrom = mutableMapOf<SearchKey, SearchKey>()

        val startKey = SearchKey(startNodeId, null)
        gScore[startKey] = 0.0
        val initialH = startNode.coordinate.distanceTo(targetNode.coordinate).meters
        openQueue.add(PathNode(startKey, 0.0, initialH))

        var finalKey: SearchKey? = null

        while (openQueue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val current = openQueue.poll() ?: break
            val currentKey = current.key
            val currentNodeId = currentKey.nodeId

            if (currentNodeId == targetNodeId) {
                finalKey = currentKey
                break
            }

            if (current.gCostMeters > (gScore[currentKey] ?: Double.MAX_VALUE)) {
                continue
            }

            // Inspect outgoing edges
            val outgoingEdges = roadGraph.getOutgoingEdges(currentNodeId)
            for (edge in outgoingEdges) {
                // Check access permissions
                if (edge.access !in allowedAccess) continue

                val neighborNodeId = edge.endNode
                val neighborNode = roadGraph.getNode(neighborNodeId) ?: continue

                // Check turn restrictions from incomingEdgeId to edge.id
                val prevEdgeId = currentKey.incomingEdgeId
                if (prevEdgeId != null) {
                    val prevEdge = roadGraph.getEdge(prevEdgeId)
                    val hasRestriction = prevEdge?.restrictions?.any { restr ->
                        when (restr) {
                            is TurnRestriction.Prohibited -> restr.toEdgeId == edge.id
                            is TurnRestriction.ViaNode -> restr.toEdgeId == edge.id && restr.viaNodeId == currentNodeId
                            is TurnRestriction.Mandatory -> restr.toEdgeId != edge.id
                            is TurnRestriction.MultiEdge -> restr.edgeIds.size >= 2 && restr.edgeIds[0] == prevEdgeId && restr.edgeIds[1] == edge.id
                        }
                    } ?: false
                    if (hasRestriction) continue
                }

                val tentativeG = current.gCostMeters + edge.distance.meters
                val neighborKey = SearchKey(neighborNodeId, edge.id)
                val existingG = gScore[neighborKey] ?: Double.MAX_VALUE

                if (tentativeG < existingG) {
                    gScore[neighborKey] = tentativeG
                    cameFrom[neighborKey] = currentKey

                    val h = neighborNode.coordinate.distanceTo(targetNode.coordinate).meters
                    val f = tentativeG + h
                    openQueue.add(PathNode(neighborKey, tentativeG, f))
                }
            }
        }

        if (finalKey == null) {
            // Target was not reachable: graph is disconnected! Never fabricate a connection!
            return Result.failure(RoutingError.NoRouteFound(startNodeId, targetNodeId))
        }

        // Reconstruct path
        val nodePath = mutableListOf<String>()
        val edgePath = mutableListOf<String>()
        var curr: SearchKey? = finalKey

        while (curr != null) {
            nodePath.add(0, curr.nodeId)
            val incomingEdge = curr.incomingEdgeId
            if (incomingEdge != null) {
                edgePath.add(0, incomingEdge)
            }
            curr = cameFrom[curr]
        }

        if (nodePath.size < 2 || edgePath.isEmpty() || nodePath.size != edgePath.size + 1) {
            return Result.failure(RoutingError.InternalError("Path reconstruction error"))
        }

        val totalDistanceMeters = gScore[finalKey] ?: 0.0

        // Physically compute travel time across edges based on speed limits / road class profiles
        var totalEstimatedTimeSec = 0.0
        for (edgeId in edgePath) {
            val edge = roadGraph.getEdge(edgeId)
            val speedMps = if (edge != null && edge.speed.value > 5.0) {
                edge.speed.metersPerSecond
            } else {
                getRoadClassNominalSpeedMps(edge?.roadClass)
            }
            val edgeDistMeters = edge?.distance?.meters ?: 0.0
            totalEstimatedTimeSec += if (speedMps > 0.0) edgeDistMeters / speedMps else edgeDistMeters / 13.88
        }

        val route = Route(
            id = "offline_route_${startNodeId}_${targetNodeId}",
            origin = startNodeId,
            destination = targetNodeId,
            nodes = nodePath,
            edges = edgePath,
            distance = Distance.fromMeters(totalDistanceMeters),
            estimatedTime = totalEstimatedTimeSec
        )
        return Result.success(route)
    }

    companion object {
        /**
         * Resolves the nominal physical cruising velocity (m/s) based on highway / road classifications in Iran.
         * Used whenever edge-specific numerical speed limit data is absent, eliminating blind 50 km/h defaults.
         */
        fun getRoadClassNominalSpeedMps(roadClass: RoadClass?): Double {
            return when (roadClass) {
                RoadClass.MOTORWAY -> 110.0 / 3.6     // 110 km/h nominal (e.g. Tehran-Qom Highway)
                RoadClass.TRUNK_ROAD -> 95.0 / 3.6     // 95 km/h nominal
                RoadClass.PRIMARY_ROAD -> 80.0 / 3.6   // 80 km/h nominal (Major inter-city corridors)
                RoadClass.SECONDARY_ROAD -> 60.0 / 3.6 // 60 km/h nominal
                RoadClass.LOCAL_ROAD -> 40.0 / 3.6     // 40 km/h nominal
                RoadClass.SERVICE_ROAD -> 25.0 / 3.6   // 25 km/h nominal
                RoadClass.PEDESTRIAN_PATH, RoadClass.CYCLE_PATH -> 15.0 / 3.6
                null -> 50.0 / 3.6
            }
        }
    }
}
