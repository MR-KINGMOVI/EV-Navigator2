package com.evnavigator.iran.domain.journey.multistop

import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.abs

/**
 * Typealias preserving backward and cross-module compatibility for [MultiStopJourneyPlan].
 */
typealias JourneyPlan = MultiStopJourneyPlan

/**
 * Domain turn-by-turn maneuver tied to a specific leg and its geometry (V13.3.1 Section 2).
 */
data class PlannedLegManeuver(
    val legIndex: Int,
    val maneuverIndexInLeg: Int,
    val coordinate: GeoCoordinate,
    val instructionFarsi: String,
    val maneuverType: String = "STRAIGHT",
    val distanceMeters: Double = 0.0,
    val cumulativeDistanceAlongLegMeters: Double = 0.0,
    val cumulativeDistanceAlongRouteMeters: Double = 0.0,
    val roadName: String = "",
    val speedLimitKmh: Int? = null
)

/**
 * Classification of how a leg or route was computed (V13.3.1 Section 1 & Section 9).
 * Only [AUTHENTIC_ROAD_GRAPH] represents a verified road-network graph path.
 */
enum class RouteCategory(val isTrueRoadGraph: Boolean) {
    AUTHENTIC_ROAD_GRAPH(true),
    CURATED_CORRIDOR_FALLBACK(false),
    ESTIMATED_GEODESIC(false),
    SYNTHETIC_ESTIMATE(false),
    UNKNOWN(false)
}

/**
 * Thrown or returned in [Result.failure] when a waypoint is outside Iran road network coverage,
 * when the road graph is disconnected/severed, or when a synthetic geodesic fallback is rejected.
 */
class RouteCoverageException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Type of stop within a multi-stop EV journey.
 */
enum class WaypointType {
    ORIGIN,
    INTERMEDIATE_USER_STOP,
    CHARGING_STOP,
    DESTINATION
}

/**
 * Explicit connector compatibility state between vehicle and charging station.
 * Empty or unknown connector lists must be [UNKNOWN], never [MATCH].
 */
enum class ConnectorCompatibility {
    MATCH,
    MISMATCH,
    UNKNOWN
}

/**
 * Canonical EV charging connector types in the Iranian market.
 * Used as a single source of truth across route coordination and risk-aware station evaluation
 * to prevent alias mismatches (e.g., "CCS2 Combo" vs "CCS2", "GB/T DC" vs "GBT").
 */
enum class CanonicalConnectorType(val canonicalCode: String) {
    CCS2("CCS2"),
    GBT_DC("GBT_DC"),
    GBT_AC("GBT_AC"),
    TYPE2("TYPE2"),
    CHADEMO("CHADEMO"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun parse(raw: String?): CanonicalConnectorType {
            if (raw.isNullOrBlank()) return UNKNOWN
            val norm = raw.trim().uppercase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .replace("/", "")
            if (norm.isEmpty() || norm == "UNKNOWN" || norm == "NONE" || norm == "INCOMPATIBLE") {
                return UNKNOWN
            }
            return when {
                norm.contains("CCS2") || norm.contains("COMBO2") -> CCS2
                norm.contains("GBTAC") -> GBT_AC
                norm.contains("GBTDC") || norm == "GBT" -> GBT_DC
                norm.contains("CHADEMO") -> CHADEMO
                norm.contains("TYPE2") || norm.contains("MENNEKES") -> TYPE2
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Result of matching vehicle connectors against station connectors using [CanonicalConnectorType].
 */
data class ConnectorMatchResult(
    val compatibility: ConnectorCompatibility,
    val matchedCanonicalType: CanonicalConnectorType = CanonicalConnectorType.UNKNOWN,
    val matchedStationConnectorRaw: String? = null
)

/**
 * Three-valued feasibility classification for legs and overall multi-stop plans.
 * Deterministic [FEASIBLE] is only reported when all required station power, connector,
 * vehicle, and energy inputs are known and verified.
 */
enum class FeasibilityStatus {
    FEASIBLE,
    INFEASIBLE,
    UNCERTAIN
}

/**
 * Individual stop in a multi-stop journey.
 */
data class JourneyWaypoint(
    val id: String,
    val name: String,
    val coordinate: GeoCoordinate,
    val type: WaypointType,
    val isRequired: Boolean = true,
    val estimatedStopDurationMinutes: Double = 0.0,
    val targetArrivalSocPercent: Double? = null,
    val targetDepartureSocPercent: Double? = null,
    val orderIndex: Int = 0,
    val stationDetails: CandidateStation? = null
) {
    val isChargingStop: Boolean get() = type == WaypointType.CHARGING_STOP
}

/**
 * Represents a single leg between two consecutive waypoints (V13.3.1 Sections 1 & 2).
 * Carries its own independent road polyline, physical route segments, turn-by-turn maneuvers,
 * elevation gain/loss, arrival SOC, charging energy/duration, and departure SOC.
 */
data class MultiStopJourneyLeg(
    val legIndex: Int,
    val fromWaypoint: JourneyWaypoint,
    val toWaypoint: JourneyWaypoint,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val estimatedEnergyKwh: Double,
    val departureSocPercent: Double,
    val arrivalSocPercent: Double,
    val isFeasible: Boolean,
    val polyline: List<GeoCoordinate> = emptyList(),
    val segments: List<com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput> = emptyList(),
    val maneuvers: List<PlannedLegManeuver> = emptyList(),
    val chargingDurationMinutes: Double = 0.0,
    val energyAddedKwh: Double = 0.0,
    val postChargeDepartureSocPercent: Double = arrivalSocPercent,
    val sourceClassification: RouteSourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM,
    val feasibilityStatus: FeasibilityStatus = if (isFeasible) FeasibilityStatus.FEASIBLE else FeasibilityStatus.INFEASIBLE,
    val isNumericallyFeasible: Boolean = isFeasible,
    val confidenceStatus: String = if (feasibilityStatus == FeasibilityStatus.FEASIBLE) "VERIFIED" else "UNCERTAIN",
    val connectorCompatibility: ConnectorCompatibility = ConnectorCompatibility.UNKNOWN,
    val estimatedUncertainChargingMinutes: Double = 0.0,
    val assumptions: List<String> = emptyList()
) {
    val predictedArrivalSocPercent: Double get() = arrivalSocPercent
}

/**
 * Comprehensive validated multi-stop EV journey plan bound to a specific vehicle, route, and departure SOC.
 */
data class MultiStopJourneyPlan(
    val planId: String,
    val vehicleId: String,
    val waypoints: List<JourneyWaypoint>,
    val legs: List<MultiStopJourneyLeg>,
    val totalDistanceMeters: Double,
    val totalDrivingDurationSeconds: Double,
    val totalChargingDurationMinutes: Double,
    val totalEnergyKwh: Double,
    val initialDepartureSocPercent: Double,
    val finalArrivalSocPercent: Double,
    val isOverallFeasible: Boolean,
    val warnings: List<String> = emptyList(),
    val routeId: String = planId,
    val routeVersion: Long = 1L,
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
    val feasibilityStatus: FeasibilityStatus = if (isOverallFeasible) FeasibilityStatus.FEASIBLE else FeasibilityStatus.INFEASIBLE,
    val isNumericallyFeasible: Boolean = isOverallFeasible,
    val confidenceStatus: String = if (feasibilityStatus == FeasibilityStatus.FEASIBLE) "VERIFIED" else "UNCERTAIN",
    val totalUncertainChargingDurationMinutes: Double = 0.0,
    val isDepartureSocVerified: Boolean = false,
    val departureSocSource: String = "UNCONFIRMED_DEFAULT",
    val assumptions: List<String> = emptyList(),
    val finalPlannedRoute: FinalPlannedRoute? = null,
    val fullPolyline: List<GeoCoordinate> = buildList {
        for (leg in legs) {
            if (isEmpty()) {
                addAll(leg.polyline)
            } else if (leg.polyline.isNotEmpty()) {
                if (last() == leg.polyline.first()) {
                    addAll(leg.polyline.drop(1))
                } else {
                    addAll(leg.polyline)
                }
            }
        }
    }
) {
    /**
     * Checks whether this charging & journey plan is still valid for the given route, vehicle, and departure SOC.
     */
    fun isValidForContext(
        expectedVehicleId: String,
        expectedDepartureSocPercent: Double,
        expectedRouteId: String? = null
    ): Boolean {
        if (vehicleId != expectedVehicleId) return false
        if (abs(initialDepartureSocPercent - expectedDepartureSocPercent) > 2.0) return false
        if (expectedRouteId != null && routeId != expectedRouteId) return false
        return true
    }
}
