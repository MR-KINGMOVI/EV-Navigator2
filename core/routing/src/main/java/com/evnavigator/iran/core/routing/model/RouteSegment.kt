package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Speed

/**
 * A segment of a route between two waypoints
 */
data class RouteSegment(
    val id: String,
    val startNode: String,
    val endNode: String,
    val edges: List<String>,
    val distance: Distance,
    val estimatedTime: Double, // seconds
    val elevationGain: Double = 0.0, // meters
    val elevationLoss: Double = 0.0, // meters
    val geometry: String? = null, // GeoJSON or similar format
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(id.isNotEmpty()) { "RouteSegment ID cannot be empty" }
        require(edges.isNotEmpty()) { "RouteSegment must contain at least one edge" }
    }
}