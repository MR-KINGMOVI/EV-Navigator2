package com.evnavigator.iran.domain.model.navigation.maneuver

/**
 * Types of maneuvers in navigation.
 */
enum class ManeuverType {
    /**
     * Continue straight.
     */
    CONTINUE,

    /**
     * Turn left.
     */
    TURN_LEFT,

    /**
     * Turn right.
     */
    TURN_RIGHT,

    /**
     * Sharp left turn.
     */
    SHARP_LEFT,

    /**
     * Sharp right turn.
     */
    SHARP_RIGHT,

    /**
     * Slight left turn.
     */
    SLIGHT_LEFT,

    /**
     * Slight right turn.
     */
    SLIGHT_RIGHT,

    /**
     * U-turn.
     */
    U_TURN,

    /**
     * Merge onto road.
     */
    MERGE,

    /**
     * Keep to the left.
     */
    KEEP_LEFT,

    /**
     * Keep to the right.
     */
    KEEP_RIGHT,

    /**
     * Enter roundabout.
     */
    ENTER_ROUNDABOUT,

    /**
     * Exit roundabout.
     */
    EXIT_ROUNDABOUT,

    /**
     * Take ramp.
     */
    TAKE_RAMP,

    /**
     * Take exit.
     */
    TAKE_EXIT,

    /**
     * Arrive at destination.
     */
    ARRIVE
}