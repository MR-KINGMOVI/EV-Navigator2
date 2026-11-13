package com.evnavigator.iran.domain.model.navigation

/**
 * Types of navigation instructions.
 */
enum class InstructionType {
    /**
     * General instruction.
     */
    GENERAL,

    /**
     * Turn instruction.
     */
    TURN,

    /**
     * Continue instruction.
     */
    CONTINUE,

    /**
     * Roundabout instruction.
     */
    ROUNDABOUT,

    /**
     * Exit instruction.
     */
    EXIT,

    /**
     * Destination instruction.
     */
    DESTINATION
}