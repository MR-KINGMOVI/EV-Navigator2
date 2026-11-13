package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Speed
import com.evnavigator.iran.domain.model.energy.RoadType

/**
 * Represents information about the current road the user is on.
 */
data class CurrentRoad(
    /**
     * Name of the current road.
     */
    val name: String?,

    /**
     * Direction of travel on this road.
     */
    val direction: String?,

    /**
     * Speed limit on this road (if available).
     */
    val speedLimit: Speed?,

    /**
     * Road type classification.
     */
    val roadType: RoadType?
)