package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Speed
import com.evnavigator.iran.domain.model.Bearing

/**
 * Complete routing result containing the full route information
 */
data class Route(
    val id: String,
    val origin: String, // node ID
    val destination: String, // node ID
    val nodes: List<String>, // ordered list of node IDs
    val edges: List<String>, // ordered list of edge IDs
    val distance: Distance,
    val estimatedTime: Double, // seconds
    val totalElevationGain: Double = 0.0, // meters
    val totalElevationLoss: Double = 0.0, // meters
    val geometry: String? = null, // GeoJSON or similar format
    val turnInstructions: List<TurnInstructionData> = emptyList(),
    val segments: List<RouteSegment> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(id.isNotEmpty()) { "Route ID cannot be empty" }
        require(nodes.isNotEmpty()) { "Route must contain at least one node" }
        require(edges.isNotEmpty()) { "Route must contain at least one edge" }
        require(nodes.size == edges.size + 1) { "Nodes and edges count mismatch" }
    }
    
    val startNode: String
        get() = nodes.first()
        
    val endNode: String
        get() = nodes.last()
        
    fun isValid(): Boolean {
        return nodes.isNotEmpty() && 
               edges.isNotEmpty() && 
               nodes.size == edges.size + 1 &&
               distance.meters >= 0.0 &&
               estimatedTime >= 0.0
    }
}