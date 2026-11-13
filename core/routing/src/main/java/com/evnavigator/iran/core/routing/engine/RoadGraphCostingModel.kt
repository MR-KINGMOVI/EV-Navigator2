package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RoadClass

/**
 * V11 Extracted Collaborator: Road segment effective speed and turn-maneuver penalty costing model.
 */
object RoadGraphCostingModel {

    fun computeEffectiveSegmentSpeedKmh(edge: CorridorEdge): Double {
        val rawSpeed = edge.speedLimitKmh
        val baseSpeed = if (rawSpeed in 5..140) {
            rawSpeed.toDouble()
        } else {
            when (edge.roadClass) {
                RoadClass.MOTORWAY -> 110.0
                RoadClass.TRUNK_ROAD -> 90.0
                RoadClass.PRIMARY_ROAD -> 80.0
                RoadClass.SECONDARY_ROAD -> 60.0
                RoadClass.LOCAL_ROAD -> 45.0
                RoadClass.SERVICE_ROAD -> 30.0
                RoadClass.PEDESTRIAN_PATH,
                RoadClass.CYCLE_PATH -> 15.0
            }
        }
        val roadClassFactor = when (edge.roadClass) {
            RoadClass.MOTORWAY -> 0.94
            RoadClass.TRUNK_ROAD -> 0.90
            RoadClass.PRIMARY_ROAD -> 0.85
            RoadClass.SECONDARY_ROAD -> 0.80
            RoadClass.LOCAL_ROAD -> 0.72
            RoadClass.SERVICE_ROAD -> 0.65
            RoadClass.PEDESTRIAN_PATH,
            RoadClass.CYCLE_PATH -> 0.50
        }
        val surfaceFactor = when (edge.surface.lowercase()) {
            "asphalt", "concrete", "paved" -> 1.0
            "cobblestone", "paving_stones" -> 0.75
            "gravel", "unpaved", "dirt" -> 0.60
            else -> 0.95
        }
        return (baseSpeed * roadClassFactor * surfaceFactor).coerceIn(8.0, 125.0)
    }

    fun computeManeuverPenaltySeconds(type: RealManeuverType): Double {
        return when (type) {
            RealManeuverType.UTURN -> 15.0
            RealManeuverType.TURN_LEFT, RealManeuverType.SHARP_LEFT -> 8.0
            RealManeuverType.TURN_RIGHT, RealManeuverType.SHARP_RIGHT -> 6.0
            RealManeuverType.ROUNDABOUT -> 5.0
            RealManeuverType.RAMP_EXIT, RealManeuverType.RAMP_ENTER, RealManeuverType.MERGE -> 4.0
            RealManeuverType.FORK_LEFT, RealManeuverType.FORK_RIGHT -> 2.0
            else -> 0.0
        }
    }
}
