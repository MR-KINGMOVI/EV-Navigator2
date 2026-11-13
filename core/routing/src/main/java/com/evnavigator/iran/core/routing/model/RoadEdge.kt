package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Speed

/**
 * Represents a directed edge in the road graph - a segment of road between two nodes
 */
data class RoadEdge(
    val id: String,
    val startNode: String,
    val endNode: String,
    val distance: Distance,
    val speed: Speed,
    val roadClass: RoadClass,
    val access: RoadAccess,
    val geometryReference: String? = null,
    val restrictions: List<TurnRestriction> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(id.isNotEmpty()) { "RoadEdge ID cannot be empty" }
        require(startNode.isNotEmpty()) { "Start node ID cannot be empty" }
        require(endNode.isNotEmpty()) { "End node ID cannot be empty" }
        require(startNode != endNode) { "Start and end nodes must be different" }
    }
}