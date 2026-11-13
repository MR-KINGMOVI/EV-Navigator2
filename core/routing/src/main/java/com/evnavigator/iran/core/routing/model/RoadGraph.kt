package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance

/**
 * Represents the complete road graph for routing
 */
class RoadGraph(
    val nodes: Map<String, RoadNode>,
    val edges: Map<String, RoadEdge>,
    private val nodeToEdges: Map<String, List<String>>, // Maps node ID to list of connected edge IDs
    val turnRestrictions: List<TurnRestriction> = emptyList()
) {

    fun getNode(nodeId: String): RoadNode? = nodes[nodeId]

    fun getEdge(edgeId: String): RoadEdge? = edges[edgeId]

    fun getOutgoingEdges(nodeId: String): List<RoadEdge> {
        return nodeToEdges[nodeId]?.mapNotNull { edges[it] } ?: emptyList()
    }

    fun getIncomingEdges(nodeId: String): List<RoadEdge> {
        return edges.values.filter { it.endNode == nodeId }
    }

    fun getAllNodes(): Collection<RoadNode> = nodes.values

    fun getAllEdges(): Collection<RoadEdge> = edges.values

    fun getEstimatedDistance(startNodeId: String, endNodeId: String): Distance? {
        val startNode = getNode(startNodeId)
        val endNode = getNode(endNodeId)

        return if (startNode != null && endNode != null) {
            Distance.fromMeters(startNode.coordinate.distanceTo(endNode.coordinate).meters)
        } else {
            null
        }
    }
}