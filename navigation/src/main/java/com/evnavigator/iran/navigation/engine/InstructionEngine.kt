package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.navigation.maneuver.UpcomingManeuver
import com.evnavigator.iran.domain.model.navigation.NavigationInstruction

/**
 * Interface for generating navigation instructions.
 */
interface InstructionEngine {
    /**
     * Generate instruction for an upcoming maneuver.
     */
    fun generateInstruction(maneuver: UpcomingManeuver): NavigationInstruction

    /**
     * Generate multiple instructions for upcoming maneuvers.
     */
    fun generateInstructions(maneuvers: List<UpcomingManeuver>): List<NavigationInstruction>

    /**
     * Generate instruction for destination arrival.
     */
    fun generateArrivalInstruction(): NavigationInstruction
}