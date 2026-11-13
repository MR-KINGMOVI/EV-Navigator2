package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.routing.engine.OfflineRoutingSolver
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.Route
import com.evnavigator.iran.domain.model.navigation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of an offline navigation reroute attempt.
 */
sealed class OfflineRerouteResult {
    data class Success(val newRoute: Route) : OfflineRerouteResult()
    data class CoverageUnavailable(val reason: String) : OfflineRerouteResult()
    data class Disconnected(val reason: String) : OfflineRerouteResult()
}

/**
 * Coordinates live turn-by-turn navigation state when running completely offline.
 *
 * Rules:
 * - Detects GPS loss / tunnel traversal and sets GpsQuality.LOST without pretending fresh fixes exist
 * - When off-route, attempts deterministic offline routing using local RoadGraph
 * - If offline graph is out of bounds or disconnected, explicitly reports CoverageUnavailable / Disconnected;
 *   NEVER invents a fake reroute!
 */
class OfflineNavigationCoordinator(
    private val session: NavigationSession,
    private val offlineRoutingSolver: OfflineRoutingSolver,
    private val roadGraphProvider: () -> RoadGraph?
) {

    private val _gpsQuality = MutableStateFlow(GpsQuality.GOOD)
    val gpsQuality: StateFlow<GpsQuality> = _gpsQuality.asStateFlow()

    private val _lastKnownLocation = MutableStateFlow<GeoCoordinate?>(null)
    val lastKnownLocation: StateFlow<GeoCoordinate?> = _lastKnownLocation.asStateFlow()

    /**
     * Ingests a new GPS fix, validating freshness and accuracy.
     */
    fun onLocationUpdate(
        coordinate: GeoCoordinate,
        accuracyMeters: Float,
        isTunnelOrLost: Boolean
    ) {
        if (isTunnelOrLost || accuracyMeters > 50.0f) {
            _gpsQuality.value = GpsQuality.UNAVAILABLE
            // Preserve last known valid position; do not overwrite with invalid noise
            return
        }

        _gpsQuality.value = if (accuracyMeters <= 10.0f) GpsQuality.EXCELLENT else GpsQuality.GOOD
        _lastKnownLocation.value = coordinate
    }

    /**
     * Handles off-route deviation using offline graph.
     */
    suspend fun handleOfflineReroute(
        currentNearestNodeId: String,
        destinationNodeId: String
    ): OfflineRerouteResult {
        val graph = roadGraphProvider()
            ?: return OfflineRerouteResult.CoverageUnavailable("No offline road network dataset installed")

        val startNode = graph.getNode(currentNearestNodeId)
        val endNode = graph.getNode(destinationNodeId)

        if (startNode == null) {
            return OfflineRerouteResult.CoverageUnavailable(
                "Current position ($currentNearestNodeId) is outside offline road dataset coverage"
            )
        }
        if (endNode == null) {
            return OfflineRerouteResult.CoverageUnavailable(
                "Destination ($destinationNodeId) is outside offline road dataset coverage"
            )
        }

        session.transitionTo(NavigationStatus.RECALCULATING)

        val solverResult = offlineRoutingSolver.findRoute(graph, currentNearestNodeId, destinationNodeId)

        return if (solverResult.isSuccess) {
            val route = solverResult.getOrThrow()
            session.transitionTo(NavigationStatus.NAVIGATING)
            OfflineRerouteResult.Success(route)
        } else {
            session.transitionTo(NavigationStatus.ERROR)
            OfflineRerouteResult.Disconnected(
                "Unable to compute offline reroute: destination is disconnected on local road graph"
            )
        }
    }
}
