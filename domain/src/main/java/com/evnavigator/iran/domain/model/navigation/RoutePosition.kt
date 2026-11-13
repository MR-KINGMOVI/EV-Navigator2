package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.RouteSegment

/**
 * Represents the current position on a route, including segment index, edge ID,
 * direction, and longitudinal offset along the segment.
 */
data class RoutePosition(
    /**
     * The segment of the route the user is currently on.
     */
    val segment: RouteSegment,

    /**
     * Progress along the segment (0.0 to 1.0).
     */
    val progressAlongSegment: Double,

    /**
     * Distance traveled along the route so far.
     */
    val distanceTraveled: Distance,

    /**
     * Distance remaining on the current segment.
     */
    val distanceRemainingOnSegment: Distance,

    /**
     * Total distance remaining to destination.
     */
    val totalDistanceRemaining: Distance,

    /**
     * Route percentage complete (0.0 to 1.0).
     */
    val routePercentage: Double,

    /**
     * 0-based index of the active segment in route.segments.
     */
    val segmentIndex: Int = 0,

    /**
     * Underlying directed edge identifier.
     */
    val edgeId: String = segment.edgeId,

    /**
     * Traversal direction ("FORWARD" or "BACKWARD").
     */
    val direction: String = segment.direction,

    /**
     * Longitudinal offset from the start of the current segment in meters.
     */
    val offsetMeters: Double = (segment.distance.meters * progressAlongSegment).coerceIn(0.0, segment.distance.meters)
)