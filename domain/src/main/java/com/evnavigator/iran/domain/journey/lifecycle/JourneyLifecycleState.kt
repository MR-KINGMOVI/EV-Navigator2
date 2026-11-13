package com.evnavigator.iran.domain.journey.lifecycle

/**
 * Complete set of states for an EV journey lifecycle in Phase 16.
 */
enum class JourneyLifecycleState {
    /** Journey draft being edited or configured by user */
    DRAFT,

    /** Multi-stop route calculation and charging stop optimization in progress */
    PLANNING,

    /** Feasible route plan confirmed and waiting for driver departure */
    READY_TO_START,

    /** Actively driving along the route with live tracking */
    ACTIVE,

    /** Vehicle is within threshold distance (e.g. 2 km) of an intermediate user waypoint */
    APPROACHING_WAYPOINT,

    /** Vehicle is within threshold distance of a designated EV charging station */
    APPROACHING_CHARGING_STOP,

    /** Vehicle has arrived at the charging station perimeter */
    CHARGING_STOP_REACHED,

    /** Actively connected to charger and drawing power (or manual session active) */
    CHARGING_IN_PROGRESS,

    /** Charging finished, battery target reached, vehicle disconnected and ready to depart */
    READY_TO_DEPART,

    /** Journey paused by user (e.g. rest stop, meal) */
    TEMPORARILY_PAUSED,

    /** Recalculating route due to deviation, SOC deficit, or closed charging station */
    REPLANNING,

    /** Driver has reached the final destination */
    COMPLETED,

    /** Journey explicitly cancelled by driver */
    CANCELLED,

    /** Journey was interrupted (app killed, process death, system crash) awaiting recovery */
    INTERRUPTED_RECOVERY;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    val isNavigating: Boolean
        get() = this == ACTIVE || this == APPROACHING_WAYPOINT || this == APPROACHING_CHARGING_STOP

    val isCharging: Boolean
        get() = this == CHARGING_STOP_REACHED || this == CHARGING_IN_PROGRESS || this == READY_TO_DEPART
}
