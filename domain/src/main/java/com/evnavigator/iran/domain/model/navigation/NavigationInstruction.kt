package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration

/**
 * Represents a navigation instruction for the user.
 */
data class NavigationInstruction(
    /**
     * The instruction text to display.
     */
    val text: String,

    /**
     * Type of instruction.
     */
    val type: InstructionType,

    /**
     * Distance to next maneuver (if applicable).
     */
    val distanceToNextManeuver: Distance?,

    /**
     * Estimated time to next maneuver (if applicable).
     */
    val timeToNextManeuver: Duration?
)