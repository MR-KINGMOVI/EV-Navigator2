package com.evnavigator.iran.ui.screen.journey

import com.evnavigator.iran.core.routing.model.RealRoadManeuver

/**
 * V11 Extracted Collaborator: Monotonic maneuver index resolver with GPS teleport guard.
 */
class JourneyManeuverProgressionCoordinator {

    private var highestReachedManeuverIndex: Int = 0
    private var maneuverTrackingGenerationId: Long = 1L
    private var maneuverTrackingRouteId: String = ""
    private var lastValidatedTraveledMeters: Double = 0.0

    fun reset(genId: Long, routeId: String = "") {
        maneuverTrackingGenerationId = genId
        maneuverTrackingRouteId = routeId
        highestReachedManeuverIndex = 0
        lastValidatedTraveledMeters = 0.0
    }

    fun resolveMonotonicManeuverIndex(
        maneuvers: List<RealRoadManeuver>,
        traveledMeters: Double,
        currentGenId: Long,
        currentRouteId: String = ""
    ): Int {
        if (maneuvers.isEmpty() || !traveledMeters.isFinite() || traveledMeters < 0.0) return -1

        var prevCumDist = 0.0
        for (m in maneuvers) {
            if (!m.cumulativeDistanceMeters.isFinite() ||
                m.cumulativeDistanceMeters < 0.0 ||
                m.cumulativeDistanceMeters < prevCumDist - 1e-3
            ) {
                return -1
            }
            prevCumDist = m.cumulativeDistanceMeters
        }

        val routeChanged = currentRouteId.isNotEmpty() &&
            maneuverTrackingRouteId.isNotEmpty() &&
            maneuverTrackingRouteId != currentRouteId
        if (maneuverTrackingGenerationId != currentGenId || routeChanged) {
            maneuverTrackingGenerationId = currentGenId
            maneuverTrackingRouteId = currentRouteId
            highestReachedManeuverIndex = 0
            lastValidatedTraveledMeters = traveledMeters
        } else if (maneuverTrackingRouteId.isEmpty() && currentRouteId.isNotEmpty()) {
            maneuverTrackingRouteId = currentRouteId
        }

        val rawIdx = maneuvers.indexOfFirst { it.cumulativeDistanceMeters >= traveledMeters }
            .let { if (it == -1) maneuvers.lastIndex else it }

        val deltaTraveled = traveledMeters - lastValidatedTraveledMeters
        val maxAllowedIdx = if (lastValidatedTraveledMeters > 0.0 && deltaTraveled > 500.0) {
            (highestReachedManeuverIndex + 1).coerceAtMost(maneuvers.lastIndex)
        } else {
            maneuvers.lastIndex
        }

        val boundedRawIdx = rawIdx.coerceAtMost(maxAllowedIdx)
        val monotonicIdx = maxOf(boundedRawIdx, highestReachedManeuverIndex.coerceAtMost(maneuvers.lastIndex))
        highestReachedManeuverIndex = monotonicIdx
        lastValidatedTraveledMeters = maxOf(lastValidatedTraveledMeters, traveledMeters)
        return monotonicIdx
    }
}
