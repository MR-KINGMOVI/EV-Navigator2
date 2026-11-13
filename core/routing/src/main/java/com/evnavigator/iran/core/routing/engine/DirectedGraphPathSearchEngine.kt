package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.TurnRestriction
import java.util.PriorityQueue

/**
 * V11 Extracted Collaborator: Directed Edge-State A* pathfinding and turn-restriction enforcement.
 */
object DirectedGraphPathSearchEngine {

    private data class EdgeTraversalState(
        val nodeId: String,
        val incomingEdgeIndex: Int,
        val previousEdgeIndex: Int = -1,
        val isForward: Boolean = true
    )

    private data class IndexedDirectedEdge(
        val edgeIndex: Int,
        val targetNodeId: String,
        val distanceMeters: Double,
        val edge: CorridorEdge,
        val isForward: Boolean
    )

    private data class SearchQueueEntry(
        val state: EdgeTraversalState,
        val gScore: Double,
        val fScore: Double
    )

    fun isTurnPermitted(
        previousEdgeIndex: Int,
        incomingEdgeIndex: Int,
        incomingEdge: CorridorEdge?,
        candidateEdgeIndex: Int,
        targetNodeId: String,
        multiEdgeRestrictions: List<TurnRestriction.MultiEdge>
    ): Boolean {
        if (incomingEdge == null) return true
        if (incomingEdge.prohibitedTurns.contains(targetNodeId)) return false
        if (incomingEdge.mandatoryTurns.isNotEmpty() && !incomingEdge.mandatoryTurns.contains(targetNodeId)) {
            return false
        }
        if (previousEdgeIndex >= 0 && incomingEdgeIndex >= 0 && multiEdgeRestrictions.isNotEmpty()) {
            val prevIdxStr = (previousEdgeIndex + 1).toString()
            val viaIdxStr = (incomingEdgeIndex + 1).toString()
            val nextIdxStr = (candidateEdgeIndex + 1).toString()
            val prevEdgeTag = "edge_$prevIdxStr"
            val viaEdgeTag = "edge_$viaIdxStr"
            val nextEdgeTag = "edge_$nextIdxStr"
            val blockedByMultiEdge = multiEdgeRestrictions.any { restriction ->
                val seq = restriction.edgeIds
                seq.size >= 3 &&
                    (seq[seq.size - 3] == prevIdxStr || seq[seq.size - 3] == prevEdgeTag) &&
                    (seq[seq.size - 2] == viaIdxStr || seq[seq.size - 2] == viaEdgeTag) &&
                    (seq[seq.size - 1] == nextIdxStr || seq[seq.size - 1] == nextEdgeTag)
            }
            if (blockedByMultiEdge) return false
        }
        return true
    }

    fun findShortestPathDirectedEdges(
        startId: String,
        targetId: String,
        edges: List<CorridorEdge>,
        nodes: List<CorridorNode> = emptyList(),
        multiEdgeRestrictions: List<TurnRestriction.MultiEdge> = emptyList()
    ): List<IranRealRoadRoutingEngine.DirectedEdgeTraversal> {
        if (startId == targetId) return emptyList()

        val nodeCoordMap = if (nodes.isNotEmpty()) nodes.associate { it.id to it.coordinate } else emptyMap()
        val targetCoord = nodeCoordMap[targetId]

        val adj = mutableMapOf<String, MutableList<IndexedDirectedEdge>>()
        edges.forEachIndexed { idx, e ->
            if (e.access == RoadAccess.RESTRICTED || e.access == RoadAccess.PEDESTRIAN_ONLY || e.access == RoadAccess.CYCLE_ONLY) {
                return@forEachIndexed
            }
            if (e.direction == EdgeDirection.FORWARD || e.direction == EdgeDirection.BIDIRECTIONAL) {
                adj.getOrPut(e.from) { mutableListOf() }.add(IndexedDirectedEdge(idx, e.to, e.distanceMeters, e, true))
            }
            if (e.direction == EdgeDirection.REVERSE || e.direction == EdgeDirection.BIDIRECTIONAL) {
                adj.getOrPut(e.to) { mutableListOf() }.add(IndexedDirectedEdge(idx, e.from, e.distanceMeters, e, false))
            }
        }

        val gScoreMap = mutableMapOf<EdgeTraversalState, Double>()
        val prevStateMap = mutableMapOf<EdgeTraversalState, EdgeTraversalState>()
        val pq = PriorityQueue<SearchQueueEntry>(compareBy { it.fScore })

        val startState = EdgeTraversalState(startId, -1, -1, true)
        val initialHeuristic = if (targetCoord != null && nodeCoordMap[startId] != null) {
            RoadGraphSnappingEngine.calculateHaversineDistanceMeters(nodeCoordMap[startId]!!, targetCoord) * 0.95
        } else 0.0

        gScoreMap[startState] = 0.0
        pq.add(SearchQueueEntry(startState, 0.0, initialHeuristic))

        var goalState: EdgeTraversalState? = null

        while (pq.isNotEmpty()) {
            val current = pq.poll() ?: break
            val state = current.state
            val u = state.nodeId
            val g = current.gScore

            if (u == targetId) {
                goalState = state
                break
            }
            if (g > (gScoreMap[state] ?: Double.MAX_VALUE)) continue

            val incomingEdge = if (state.incomingEdgeIndex >= 0) edges[state.incomingEdgeIndex] else null

            adj[u]?.forEach { outEdge ->
                val v = outEdge.targetNodeId
                if (!isTurnPermitted(
                        previousEdgeIndex = state.previousEdgeIndex,
                        incomingEdgeIndex = state.incomingEdgeIndex,
                        incomingEdge = incomingEdge,
                        candidateEdgeIndex = outEdge.edgeIndex,
                        targetNodeId = v,
                        multiEdgeRestrictions = multiEdgeRestrictions
                    )
                ) {
                    return@forEach
                }

                val nextState = EdgeTraversalState(
                    nodeId = v,
                    incomingEdgeIndex = outEdge.edgeIndex,
                    previousEdgeIndex = if (multiEdgeRestrictions.isNotEmpty()) state.incomingEdgeIndex else -1,
                    isForward = outEdge.isForward
                )
                val tentativeG = g + outEdge.distanceMeters
                if (tentativeG < (gScoreMap[nextState] ?: Double.MAX_VALUE)) {
                    gScoreMap[nextState] = tentativeG
                    prevStateMap[nextState] = state
                    val h = if (targetCoord != null && nodeCoordMap[v] != null) {
                        RoadGraphSnappingEngine.calculateHaversineDistanceMeters(nodeCoordMap[v]!!, targetCoord) * 0.95
                    } else 0.0
                    pq.add(SearchQueueEntry(nextState, tentativeG, tentativeG + h))
                }
            }
        }

        val finalState = goalState ?: return emptyList()
        val steps = mutableListOf<IranRealRoadRoutingEngine.DirectedEdgeTraversal>()
        var curr: EdgeTraversalState? = finalState
        while (curr != null && curr.incomingEdgeIndex >= 0) {
            val prev = prevStateMap[curr] ?: break
            val edgeIdx = curr.incomingEdgeIndex
            val edge = edges[edgeIdx]
            steps.add(
                0,
                IranRealRoadRoutingEngine.DirectedEdgeTraversal(
                    edgeIndex = edgeIdx,
                    edgeId = "edge_${edgeIdx + 1}",
                    fromNodeId = prev.nodeId,
                    toNodeId = curr.nodeId,
                    isForward = curr.isForward,
                    edge = edge
                )
            )
            curr = prev
        }
        return steps
    }

    fun findShortestPathDirected(
        startId: String,
        targetId: String,
        edges: List<CorridorEdge>,
        nodes: List<CorridorNode> = emptyList(),
        multiEdgeRestrictions: List<TurnRestriction.MultiEdge> = emptyList()
    ): List<String> {
        if (startId == targetId) return listOf(startId)
        val steps = findShortestPathDirectedEdges(startId, targetId, edges, nodes, multiEdgeRestrictions)
        if (steps.isEmpty()) return emptyList()
        return listOf(steps.first().fromNodeId) + steps.map { it.toNodeId }
    }

    fun findAlternativePathDirectedEdges(
        startId: String,
        targetId: String,
        edges: List<CorridorEdge>,
        primarySteps: List<IranRealRoadRoutingEngine.DirectedEdgeTraversal>,
        nodes: List<CorridorNode> = emptyList(),
        multiEdgeRestrictions: List<TurnRestriction.MultiEdge> = emptyList()
    ): List<IranRealRoadRoutingEngine.DirectedEdgeTraversal> {
        if (primarySteps.isEmpty()) return emptyList()
        val primaryEdgeIndices = primarySteps.map { it.edgeIndex }.toSet()
        val nodeCoordMap = if (nodes.isNotEmpty()) nodes.associate { it.id to it.coordinate } else emptyMap()
        val targetCoord = nodeCoordMap[targetId]

        val adj = mutableMapOf<String, MutableList<IndexedDirectedEdge>>()
        edges.forEachIndexed { idx, e ->
            if (e.access == RoadAccess.RESTRICTED || e.access == RoadAccess.PEDESTRIAN_ONLY || e.access == RoadAccess.CYCLE_ONLY) {
                return@forEachIndexed
            }
            val isPrimary = primaryEdgeIndices.contains(idx)
            val weight = if (isPrimary) e.distanceMeters * 1.55 else e.distanceMeters

            if (e.direction == EdgeDirection.FORWARD || e.direction == EdgeDirection.BIDIRECTIONAL) {
                adj.getOrPut(e.from) { mutableListOf() }.add(IndexedDirectedEdge(idx, e.to, weight, e, true))
            }
            if (e.direction == EdgeDirection.REVERSE || e.direction == EdgeDirection.BIDIRECTIONAL) {
                adj.getOrPut(e.to) { mutableListOf() }.add(IndexedDirectedEdge(idx, e.from, weight, e, false))
            }
        }

        val gScoreMap = mutableMapOf<EdgeTraversalState, Double>()
        val prevStateMap = mutableMapOf<EdgeTraversalState, EdgeTraversalState>()
        val pq = PriorityQueue<SearchQueueEntry>(compareBy { it.fScore })

        val startState = EdgeTraversalState(startId, -1, -1, true)
        gScoreMap[startState] = 0.0
        pq.add(SearchQueueEntry(startState, 0.0, 0.0))

        var goalState: EdgeTraversalState? = null

        while (pq.isNotEmpty()) {
            val current = pq.poll() ?: break
            val state = current.state
            val u = state.nodeId
            val g = current.gScore

            if (u == targetId) {
                goalState = state
                break
            }
            if (g > (gScoreMap[state] ?: Double.MAX_VALUE)) continue

            val incomingEdge = if (state.incomingEdgeIndex >= 0) edges[state.incomingEdgeIndex] else null

            adj[u]?.forEach { outEdge ->
                val v = outEdge.targetNodeId
                if (!isTurnPermitted(
                        previousEdgeIndex = state.previousEdgeIndex,
                        incomingEdgeIndex = state.incomingEdgeIndex,
                        incomingEdge = incomingEdge,
                        candidateEdgeIndex = outEdge.edgeIndex,
                        targetNodeId = v,
                        multiEdgeRestrictions = multiEdgeRestrictions
                    )
                ) {
                    return@forEach
                }

                val nextState = EdgeTraversalState(
                    nodeId = v,
                    incomingEdgeIndex = outEdge.edgeIndex,
                    previousEdgeIndex = if (multiEdgeRestrictions.isNotEmpty()) state.incomingEdgeIndex else -1,
                    isForward = outEdge.isForward
                )
                val tentativeG = g + outEdge.distanceMeters
                if (tentativeG < (gScoreMap[nextState] ?: Double.MAX_VALUE)) {
                    gScoreMap[nextState] = tentativeG
                    prevStateMap[nextState] = state
                    val h = if (targetCoord != null && nodeCoordMap[v] != null) {
                        RoadGraphSnappingEngine.calculateHaversineDistanceMeters(nodeCoordMap[v]!!, targetCoord) * 0.90
                    } else 0.0
                    pq.add(SearchQueueEntry(nextState, tentativeG, tentativeG + h))
                }
            }
        }

        val finalState = goalState ?: return emptyList()
        val steps = mutableListOf<IranRealRoadRoutingEngine.DirectedEdgeTraversal>()
        var curr: EdgeTraversalState? = finalState
        while (curr != null && curr.incomingEdgeIndex >= 0) {
            val prev = prevStateMap[curr] ?: break
            val edgeIdx = curr.incomingEdgeIndex
            val edge = edges[edgeIdx]
            steps.add(
                0,
                IranRealRoadRoutingEngine.DirectedEdgeTraversal(
                    edgeIndex = edgeIdx,
                    edgeId = "edge_${edgeIdx + 1}",
                    fromNodeId = prev.nodeId,
                    toNodeId = curr.nodeId,
                    isForward = curr.isForward,
                    edge = edge
                )
            )
            curr = prev
        }
        return steps
    }
}
