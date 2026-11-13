package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * High-definition turn-by-turn maneuver types for automotive cockpits.
 */
enum class RealManeuverType {
    DEPART,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    CONTINUE,
    MERGE,
    FORK_LEFT,
    FORK_RIGHT,
    RAMP_ENTER,
    RAMP_EXIT,
    ROUNDABOUT,
    UTURN,
    ARRIVE,
    UNKNOWN
}

/**
 * Explicit announcement stage for turn-by-turn voice and visual prompts.
 */
enum class ManeuverAnnouncementStage {
    INITIAL_WARNING,
    REMINDER,
    EXECUTION,
    COMPLETED
}

/**
 * Detailed lane guidance information for highway interchanges.
 */
data class LaneGuidanceInfo(
    val totalLanes: Int = 0,
    val activeLaneIndices: List<Int> = emptyList(), // 0-indexed from left to right
    val laneArrows: List<String> = emptyList(),
    val isKnown: Boolean = false
) {
    companion object {
        val UNKNOWN = LaneGuidanceInfo(
            totalLanes = 0,
            activeLaneIndices = emptyList(),
            laneArrows = emptyList(),
            isKnown = false
        )
    }
}

/**
 * A concrete turn-by-turn step along a real road route.
 */
data class RealRoadManeuver(
    val stepIndex: Int,
    val coordinate: GeoCoordinate,
    val roadName: String,
    val instructionFarsi: String,
    val instructionEn: String,
    val maneuverType: RealManeuverType,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val speedLimitKmh: Int = 0,
    val legalSpeedLimitKmh: Int? = speedLimitKmh.takeIf { it > 0 },
    val laneGuidance: LaneGuidanceInfo = LaneGuidanceInfo.UNKNOWN,
    val edgeId: String? = null,
    val segmentIndex: Int = stepIndex,
    val bearingBefore: Double = 0.0,
    val bearingAfter: Double = 0.0,
    val exitNumber: String? = null,
    val roundaboutExitNumber: Int? = null,
    val initialWarningMeters: Double = 1000.0,
    val reminderMeters: Double = 300.0,
    val executionMeters: Double = 60.0,
    val cumulativeDistanceMeters: Double = 0.0
) {
    val hasVerifiedSpeedLimit: Boolean
        get() = legalSpeedLimitKmh != null && legalSpeedLimitKmh > 0
}

/**
 * Preserves full topological identity of each directed segment along a computed route.
 * Eliminates ambiguity when parallel edges or loops exist between the same node pairs.
 */
data class RealRoadRouteSegment(
    val segmentIndex: Int,
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val direction: EdgeDirection = EdgeDirection.FORWARD,
    val startOffsetMeters: Double,
    val endOffsetMeters: Double,
    val lengthMeters: Double,
    val cumulativeStartMeters: Double,
    val cumulativeEndMeters: Double,
    val speedLimitKmh: Int,
    val roadClass: String,
    val roadName: String,
    val surface: String = "UNKNOWN",
    val startElevationMeters: Double? = null,
    val endElevationMeters: Double? = null,
    val gradePercent: Double = Double.NaN,
    val isGradeUnknown: Boolean = true,
    val polyline: List<GeoCoordinate> = emptyList(),
    val isToll: Boolean? = null
) {
    val geometry: List<GeoCoordinate>
        get() = polyline

    constructor(
        segmentIndex: Int,
        edgeId: String,
        fromNodeId: String,
        toNodeId: String,
        direction: String,
        startOffsetMeters: Double,
        endOffsetMeters: Double,
        lengthMeters: Double,
        cumulativeStartMeters: Double,
        cumulativeEndMeters: Double,
        speedLimitKmh: Int,
        roadClass: String,
        roadName: String,
        surface: String = "UNKNOWN",
        startElevationMeters: Double? = null,
        endElevationMeters: Double? = null,
        gradePercent: Double = Double.NaN,
        isGradeUnknown: Boolean = true,
        polyline: List<GeoCoordinate> = emptyList(),
        isToll: Boolean? = null
    ) : this(
        segmentIndex = segmentIndex,
        edgeId = edgeId,
        fromNodeId = fromNodeId,
        toNodeId = toNodeId,
        direction = when (direction.uppercase()) {
            "REVERSE", "BACKWARD" -> EdgeDirection.REVERSE
            "BIDIRECTIONAL" -> EdgeDirection.BIDIRECTIONAL
            else -> EdgeDirection.FORWARD
        },
        startOffsetMeters = startOffsetMeters,
        endOffsetMeters = endOffsetMeters,
        lengthMeters = lengthMeters,
        cumulativeStartMeters = cumulativeStartMeters,
        cumulativeEndMeters = cumulativeEndMeters,
        speedLimitKmh = speedLimitKmh,
        roadClass = roadClass,
        roadName = roadName,
        surface = surface,
        startElevationMeters = startElevationMeters,
        endElevationMeters = endElevationMeters,
        gradePercent = gradePercent,
        isGradeUnknown = isGradeUnknown,
        polyline = polyline,
        isToll = isToll
    )
}

data class RealRoadSnapMetadata(
    val nodeId: String,
    val edgeId: String,
    val snapPoint: GeoCoordinate,
    val lateralSnapDistanceMeters: Double,
    val distanceAlongEdgeMeters: Double,
    val edgeLengthMeters: Double,
    val isForward: Boolean
)

/**
 * Result of calculating a route along real asphalt road geometries.
 */
data class RealRoadRouteResult(
    val polyline: List<GeoCoordinate>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val maneuvers: List<RealRoadManeuver>,
    val isOnlineRouting: Boolean,
    val routeSummary: String,
    val originSnapDistanceMeters: Double = 0.0,
    val destinationSnapDistanceMeters: Double = 0.0,
    val alternativeRoutes: List<RealRoadRouteResult> = emptyList(),
    val routeId: String = "route_${polyline.hashCode()}_${totalDistanceMeters.toInt()}",
    val generationId: Long = 1L,
    val requestId: String? = null,
    val segments: List<RealRoadRouteSegment> = emptyList(),
    val startSnap: RealRoadSnapMetadata? = null,
    val endSnap: RealRoadSnapMetadata? = null,
    val startElevationMeters: Double? = null,
    val endElevationMeters: Double? = null,
    val completenessStatus: String = if (maneuvers.isNotEmpty()) "COMPLETE" else "INSUFFICIENT_ROUTE_DETAILS",
    val hasTurnByTurnGuidance: Boolean = maneuvers.isNotEmpty(),
    val warnings: List<String> = emptyList()
)

/**
 * Configuration for snapping GPS coordinates to the authentic road network.
 * Snap distance dynamically adapts based on GPS accuracy and road classification.
 */
data class SnapConfig(
    val defaultMaxSnapDistanceMeters: Double = 1_500.0,
    val absoluteMaxSnapDistanceMeters: Double = 2_500.0,
    val gpsAccuracyMultiplier: Double = 2.0
) {
    fun calculateMaxSnap(gpsAccuracyMeters: Double?): Double {
        if (gpsAccuracyMeters == null || gpsAccuracyMeters <= 0.0) return defaultMaxSnapDistanceMeters
        return (gpsAccuracyMeters * gpsAccuracyMultiplier)
            .coerceIn(defaultMaxSnapDistanceMeters, absoluteMaxSnapDistanceMeters)
    }
}

/**
 * Thrown when origin or destination coordinate cannot be snapped to a valid road within the threshold.
 */
class SnapExceededException(
    val pointType: String,
    val coordinate: GeoCoordinate,
    val snapDistanceMeters: Double,
    val maxAllowedSnapDistanceMeters: Double
) : Exception(
    "Snap distance exceeded for $pointType (${coordinate.latitude}, ${coordinate.longitude}): " +
        "nearest road is at ${snapDistanceMeters.toInt()}m, exceeding allowable threshold of ${maxAllowedSnapDistanceMeters.toInt()}m."
)

/**
 * Thrown when no route or road coverage connects the requested coordinates.
 */
class RouteCoverageException(message: String) : IllegalStateException(message)

/**
 * Allowed travel direction along a road edge.
 */
enum class EdgeDirection {
    FORWARD,        // One-way: from -> to
    REVERSE,        // One-way: to -> from
    BIDIRECTIONAL   // Two-way: both directions permitted
}
