package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration

/**
 * Represents navigation progress information.
 */
data class NavigationProgress(
    /**
     * Distance traveled so far.
     */
    val distanceTraveled: Distance,

    /**
     * Total distance of the route.
     */
    val totalDistance: Distance,

    /**
     * Percentage of route completed (0.0 to 1.0).
     */
    val routePercentage: Double, // 0.0 = start, 1.0 = end

    /**
     * Elapsed time since navigation started.
     */
    val elapsedTime: Duration,

    /**
     * Current segment being navigated.
     */
    val currentSegmentIndex: Int,

    /**
     * Total number of segments in the route.
     */
    val totalSegments: Int
)