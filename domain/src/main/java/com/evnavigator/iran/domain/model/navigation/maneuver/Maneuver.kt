package com.evnavigator.iran.domain.model.navigation.maneuver

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration

/**
 * Representation of a navigation maneuver instruction.
 */
data class Maneuver(
    val type: ManeuverType,
    val roadName: String? = null,
    val exitNumber: String? = null,
    val distanceToManeuver: Distance? = null,
    val timeToManeuver: Duration? = null,
    val direction: String? = null
)
