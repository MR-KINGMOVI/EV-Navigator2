package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.Distance

/**
 * A leg of a route - typically between waypoints
 */
data class RouteLeg(
    val id: String,
    val startNode: String,
    val endNode: String,
    val segments: List<RouteSegment>,
    val distance: Distance,
    val estimatedTime: Double, // seconds
    val elevationGain: Double = 0.0, // meters
    val elevationLoss: Double = 0.0, // meters
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(id.isNotEmpty()) { "RouteLeg ID cannot be empty" }
        require(segments.isNotEmpty()) { "RouteLeg must contain at least one segment" }
    }
}