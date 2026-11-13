package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.core.routing.model.TravelMode

/**
 * Routing profile that defines how to route for specific use cases
 */
data class RoutingProfile(
    val travelMode: TravelMode,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val preferPrimaryRoads: Boolean = false,
    val maxHeight: Double? = null, // meters
    val maxWidth: Double? = null,  // meters
    val maxWeight: Double? = null, // kilograms
    val speedMultiplier: Double = 1.0
)