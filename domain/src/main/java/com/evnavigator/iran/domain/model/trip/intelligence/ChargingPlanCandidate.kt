package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.model.Soc
import java.util.Date

/**
 * Unified recommendation status for charging stops and trip plans (V12.6 P2-15 / P0-3).
 *
 * Invariant: [VERIFIED_FEASIBLE] is ONLY permitted when:
 * 1. Station has traceable external `SourceEvidence` and `validationStatus == "VERIFIED"`.
 * 2. Port live operational status is explicitly `"AVAILABLE"` within live TTL.
 * 3. Connector compatibility is explicitly [ConnectorCompatibilityStatus.COMPATIBLE] from exact vehicle specs.
 * 4. Usable port power > 0.0 is documented.
 * 5. Neither station static verification nor port live status is stale per injected [DomainClock].
 * 6. Arrival SOC and safety reserve margins are satisfied.
 */
enum class PlanRecommendationStatus {
    VERIFIED_FEASIBLE,
    ESTIMATED_FEASIBLE,
    NEEDS_VERIFICATION,
    INSUFFICIENT_DATA,
    INFEASIBLE,
    PROVIDER_UNAVAILABLE
}

/**
 * Explicit source of detour distance and duration calculation (V12.6 P0-2).
 * Never conflates perpendicular geometric cross-track distance with road-graph detour distance.
 */
enum class DetourCalculationSource {
    /** Computed via an actual road routing engine (route-to-station + station-to-route minus direct corridor). */
    ROAD_GRAPH_VERIFIED,
    /** Only perpendicular cross-track distance to the route polyline was computed; road detour is unverified (`null`). */
    CROSS_TRACK_GEOMETRIC_ESTIMATE,
    /** Road route provider was invoked for detour routing but returned no route (`null` / unreachable). */
    ROUTE_UNAVAILABLE
}

/**
 * Explicit connector compatibility classification between a vehicle and a charging port (V12.6 P1-7).
 */
enum class ConnectorCompatibilityStatus {
    COMPATIBLE,
    INCOMPATIBLE,
    UNKNOWN
}

/**
 * Candidate plan for charging stops during a trip.
 *
 * V12.6 Contract:
 * - Exposes [recommendationStatus] ([PlanRecommendationStatus]) alongside [feasibilityStatus].
 * - [isVerifiedPlan] is `true` ONLY when [recommendationStatus] == [PlanRecommendationStatus.VERIFIED_FEASIBLE].
 * - Never reports `feasibilityStatus = "FEASIBLE"` or `"VERIFIED_FEASIBLE"` when any stop has
 *   `portStatus == "UNKNOWN"`, stale station/port timestamps, or missing external `SourceEvidence`.
 */
data class ChargingPlanCandidate(
    val id: String,
    val tripId: String,
    val plannedChargingStops: List<PlannedChargingStop>,
    val estimatedTotalEnergyKwh: Double,
    val estimatedChargingTimeMinutes: Double,
    val requiredReserveAfterCharging: Soc,
    val isVerifiedPlan: Boolean = false,
    val uncertaintyMinutesDelta: Double = 0.0,
    val finalArrivalSocPercent: Double = 0.0,
    val confidence: Double = 0.5,
    val feasibilityStatus: String = "NEEDS_VERIFICATION",
    val recommendationStatus: PlanRecommendationStatus = PlanRecommendationStatus.NEEDS_VERIFICATION,
    val warnings: List<String> = emptyList(),
    val planAssumptions: List<String> = emptyList(),
    val inputOrigins: Map<String, String> = emptyMap(),
    val rejectedStationReasons: Map<String, String> = emptyMap()
)

/**
 * Represents a planned charging stop within a candidate charging plan.
 *
 * V12.6 Contract:
 * - Single-port consistency: [portId], [connectorType], [maxPowerKw], and [portStatus] always refer to
 *   the exact same compatible port on [stationId].
 * - Honest detour metrics (P0-2): [verifiedRoadDetourMeters] is non-null ONLY when calculated via a real
 *   road routing provider ([DetourCalculationSource.ROAD_GRAPH_VERIFIED]); [estimatedCrossTrackMeters]
 *   holds the perpendicular geometric distance to the route polyline without any fake `* 2.7` multiplier.
 */
data class PlannedChargingStop(
    val stationId: String,
    val portId: String? = null,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val connectorType: String = "UNKNOWN",
    val maxPowerKw: Double = 0.0,
    val estimatedArrivalTime: Date,
    val requiredEnergyKwh: Double,
    val effectiveChargingPowerKw: Double,
    val estimatedDurationMinutes: Double,
    val isVerifiedStation: Boolean = false,
    val freshnessStatus: String = "UNVERIFIED",
    val routePositionIndex: Int = 0,
    val distanceAlongRouteMeters: Double = 0.0,
    val verifiedRoadDetourMeters: Double? = null,
    val estimatedCrossTrackMeters: Double = 0.0,
    val detourDistanceMeters: Double = verifiedRoadDetourMeters ?: estimatedCrossTrackMeters,
    val detourDurationMinutes: Double? = null,
    val detourCalculationSource: DetourCalculationSource = DetourCalculationSource.CROSS_TRACK_GEOMETRIC_ESTIMATE,
    val arrivalSocPercent: Double = 0.0,
    val arrivalSocRangeMinPercent: Double = 0.0,
    val arrivalSocRangeMaxPercent: Double = 0.0,
    val targetSocPercent: Double = 80.0,
    val portStatus: String = "UNKNOWN",
    val connectorCompatibilityStatus: ConnectorCompatibilityStatus = ConnectorCompatibilityStatus.COMPATIBLE,
    val isStationFresh: Boolean = false,
    val isPortStatusFresh: Boolean = false,
    val hasExternalSourceEvidence: Boolean = false,
    val dataProvenance: String = "UNVERIFIED",
    val confidence: Double = 0.5,
    val feasibilityStatus: String = "NEEDS_VERIFICATION",
    val recommendationStatus: PlanRecommendationStatus = PlanRecommendationStatus.NEEDS_VERIFICATION,
    val warnings: List<String> = emptyList(),
    val selectionReason: String = ""
)