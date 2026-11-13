package com.evnavigator.iran.core.tripplanner.model

import com.evnavigator.iran.core.charging.ConnectorType

/**
 * Trip planning constraints that define limits and preferences for the trip planner.
 */
data class TripConstraints(
    val minSocBufferPercent: Double = 10.0,      // never plan to arrive at a stop below this SOC
    val maxSocChargeTargetPercent: Double = 90.0, // don't plan to charge above this (charging curve tapers hard above ~80-90%)
    val maxDetourKm: Double = 5.0,                // max extra driving distance to reach a charging stop off-route
    val preferredConnectorTypes: Set<com.evnavigator.iran.core.charging.ConnectorType>? = null,
    val minStationPowerKw: Double? = null,
    val verifiedStationsOnly: Boolean = false
)
