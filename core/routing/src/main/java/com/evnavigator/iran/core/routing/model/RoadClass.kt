package com.evnavigator.iran.core.routing.model

/**
 * Enumeration of road classes/types for routing purposes
 */
enum class RoadClass(val defaultSpeedKmh: Double) {
    MOTORWAY(110.0),
    TRUNK_ROAD(95.0),
    PRIMARY_ROAD(80.0),
    SECONDARY_ROAD(60.0),
    LOCAL_ROAD(40.0),
    SERVICE_ROAD(25.0),
    PEDESTRIAN_PATH(15.0),
    CYCLE_PATH(15.0);

    companion object {
        val TRUNK: RoadClass get() = TRUNK_ROAD
        val PRIMARY: RoadClass get() = PRIMARY_ROAD
        val SECONDARY: RoadClass get() = SECONDARY_ROAD
        val TERTIARY: RoadClass get() = LOCAL_ROAD
        val RESIDENTIAL: RoadClass get() = LOCAL_ROAD
        val SERVICE: RoadClass get() = SERVICE_ROAD
        val MOTORWAY_LINK: RoadClass get() = MOTORWAY
        val TRUNK_LINK: RoadClass get() = TRUNK_ROAD
        val PRIMARY_LINK: RoadClass get() = PRIMARY_ROAD
        val SECONDARY_LINK: RoadClass get() = SECONDARY_ROAD
        val TERTIARY_LINK: RoadClass get() = LOCAL_ROAD
        val TRACK: RoadClass get() = SERVICE_ROAD
        val PEDESTRIAN: RoadClass get() = PEDESTRIAN_PATH
        val CYCLEWAY: RoadClass get() = CYCLE_PATH
        val UNKNOWN: RoadClass get() = SERVICE_ROAD
    }
}