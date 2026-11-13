package com.evnavigator.iran.core.routing.model

/**
 * Enumeration of road access restrictions for routing purposes
 */
enum class RoadAccess {
    PUBLIC,
    PRIVATE,
    RESTRICTED,
    MOTOR_VEHICLE_ONLY,
    PEDESTRIAN_ONLY,
    CYCLE_ONLY,
    DISABLED_ACCESS,
    PERMIT_REQUIRED;

    companion object {
        val EV_ONLY: RoadAccess get() = PERMIT_REQUIRED
        val DESTINATION_ONLY: RoadAccess get() = PERMIT_REQUIRED
    }
}