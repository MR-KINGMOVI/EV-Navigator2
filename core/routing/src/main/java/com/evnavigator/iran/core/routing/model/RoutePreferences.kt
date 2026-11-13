package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.RoutingPreference

/**
 * Encapsulates user preferences and constraints for route calculation (V13.0 Audit — Item 4 & Item 99).
 */
data class RoutePreferences(
    val travelMode: TravelMode = TravelMode.DRIVING,
    val optimizeFor: RoutingPreference = RoutingPreference.FASTEST,
    val avoidTolls: Boolean = false,
    val avoidUnpavedRoads: Boolean = true,
    val avoidFerries: Boolean = true,
    val avoidTunnels: Boolean = false,
    val maxDetourFactor: Double = 1.3,
    val preferHighwayAccess: Boolean = true,
    val strictPreferenceEnforcement: Boolean = false
)