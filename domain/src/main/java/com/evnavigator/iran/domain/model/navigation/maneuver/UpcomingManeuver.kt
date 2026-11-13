package com.evnavigator.iran.domain.model.navigation.maneuver

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration

/**
 * Represents an upcoming maneuver in the navigation route.
 */
data class UpcomingManeuver(
    /**
     * Type of maneuver to execute.
     */
    val type: ManeuverType,

    /**
     * Name of the road to turn onto (if applicable).
     */
    val roadName: String?,

    /**
     * Exit number (for highway exits, etc.).
     */
    val exitNumber: String?,

    /**
     * Distance to the maneuver location.
     */
    val distanceToManeuver: Distance,

    /**
     * Time to the maneuver location.
     */
    val timeToManeuver: Duration,

    /**
     * Direction of the maneuver (left, right, etc.).
     */
    val direction: String?
)