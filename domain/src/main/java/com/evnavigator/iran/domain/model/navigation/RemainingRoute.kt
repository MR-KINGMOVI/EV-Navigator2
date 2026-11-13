package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration
import com.evnavigator.iran.domain.model.RouteSegment

/**
 * Represents remaining route information.
 */
data class RemainingRoute(
    /**
     * Distance remaining to destination.
     */
    val distance: Distance,

    /**
     * Time remaining to destination.
     */
    val time: Duration,

    /**
     * List of remaining segments.
     */
    val remainingSegments: List<RouteSegment>,

    /**
     * List of upcoming charging stops.
     */
    val upcomingChargingStops: List<ChargingStop>
)