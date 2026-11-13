package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.journey.multistop.MultiStopJourneyPlan
import com.evnavigator.iran.domain.journey.multistop.MultiStopRoutePlan
import com.evnavigator.iran.domain.journey.multistop.PlannedLegManeuver
import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.DomainVehicleSocSource
import com.evnavigator.iran.domain.model.catalog.VehicleDataQualityEvaluator
import com.evnavigator.iran.domain.model.catalog.VehicleQualityAssessment
import com.evnavigator.iran.domain.model.trip.intelligence.DetourCalculationSource
import com.evnavigator.iran.domain.model.trip.intelligence.PlannedChargingStop
import com.evnavigator.iran.domain.policy.DomainClock
import kotlin.math.abs
import kotlin.math.max

/**
 * Explicit classification of how a route geometry was produced (V13.3.1 Sections 9 & 12).
 * Prevents fallback corridor skeletons or straight-line estimates from being labeled as full OSM graphs.
 */
enum class RouteSourceClassification(val isTrueRoadNetwork: Boolean, val persianLabel: String) {
    FULL_OFFLINE_OSM(true, "گراف کامل جاده‌ای آفلاین (OSM)"),
    ONLINE_ROUTING_PROVIDER(true, "مسیریاب آنلاین جاده‌ای"),
    LIMITED_CORRIDOR_FALLBACK(false, "شبکه محدود کریدورهای بین‌شهری (Fallback)"),
    STRAIGHT_LINE_ESTIMATE(false, "تخمین خط مستقیم (فاقد هندسه جاده‌ای)"),
    IMPORTED_GPX(false, "مسیر واردشده از فایل GPX"),
    UNKNOWN(false, "منبع مسیر نامشخص")
}

/**
 * Per-parameter provenance status for trip calculations (V13.3.1 Sections 5 & 21).
 */
enum class ParameterProvenanceStatus {
    VERIFIED,
    USER_PROVIDED,
    ESTIMATED,
    UNKNOWN
}

/**
 * Complete Provenance and Diagnostics Report for every route, energy, and charging calculation
 * (V13.3.1 Section 21). Allows any developer, test, or UI inspector to audit the exact origin of
 * every input parameter and see which parameters were estimated or unknown.
 */
data class ProvenanceAndDiagnosticsReport(
    val routeId: String,
    val routeVersion: Long,
    val routeSource: RouteSourceClassification,
    val elevationSource: ParameterProvenanceStatus,
    val speedSource: ParameterProvenanceStatus,
    val ambientTemperatureSource: ParameterProvenanceStatus,
    val vehicleSpecificationSource: ParameterProvenanceStatus,
    val stationVerificationSource: ParameterProvenanceStatus,
    val portOperationalStatusSource: ParameterProvenanceStatus,
    val initialSocSource: ParameterProvenanceStatus,
    val parameterSources: Map<String, ParameterProvenanceStatus> = emptyMap(),
    val estimatedParameters: List<String> = emptyList(),
    val unknownParameters: List<String> = emptyList(),
    val diagnosticMessages: List<String> = emptyList()
) {
    val isStrictlyVerified: Boolean
        get() = routeSource.isTrueRoadNetwork &&
            vehicleSpecificationSource in setOf(ParameterProvenanceStatus.VERIFIED, ParameterProvenanceStatus.USER_PROVIDED) &&
            initialSocSource in setOf(ParameterProvenanceStatus.VERIFIED, ParameterProvenanceStatus.USER_PROVIDED) &&
            unknownParameters.isEmpty()
}

/**
 * Canonical separation of Gross vs Usable battery capacity and State of Charge (V13.3.1 Section 11).
 *
 * Invariants:
 * - Range, leg energy consumption, and charging session calculations ALWAYS use [usableCapacityKwh].
 * - [grossCapacityKwh] is retained strictly for informational display and battery buffer diagnostics.
 * - Rejects invalid SOC (< 0 or > 100) with an explicit [IllegalArgumentException] instead of silent clamping.
 */
data class BatteryAndSocState(
    val grossCapacityKwh: Double?,
    val usableCapacityKwh: Double,
    val currentSocPercent: Double,
    val reserveSocPercent: Double,
    val isSocAuthoritative: Boolean,
    val socSource: DomainVehicleSocSource = if (isSocAuthoritative) {
        DomainVehicleSocSource.USER_MANUAL_INPUT
    } else {
        DomainVehicleSocSource.UNCONFIRMED_DEFAULT
    }
) {
    init {
        require(usableCapacityKwh.isFinite() && usableCapacityKwh > 0.0) {
            "usableCapacityKwh ($usableCapacityKwh) must be finite and positive"
        }
        require(grossCapacityKwh == null || (grossCapacityKwh.isFinite() && grossCapacityKwh >= usableCapacityKwh - 0.5)) {
            "grossCapacityKwh ($grossCapacityKwh) cannot be smaller than usableCapacityKwh ($usableCapacityKwh)"
        }
        require(currentSocPercent.isFinite() && currentSocPercent in 0.0..100.0) {
            "currentSocPercent ($currentSocPercent) must be within 0..100; silent clamping of out-of-range SOC is forbidden"
        }
        require(reserveSocPercent.isFinite() && reserveSocPercent in 0.0..95.0) {
            "reserveSocPercent ($reserveSocPercent) must be within 0..95"
        }
    }

    val usableEnergyRemainingKwh: Double
        get() = usableCapacityKwh * (currentSocPercent / 100.0)

    val reserveEnergyKwh: Double
        get() = usableCapacityKwh * (reserveSocPercent / 100.0)

    val availableEnergyAboveReserveKwh: Double
        get() = (usableEnergyRemainingKwh - reserveEnergyKwh).coerceAtLeast(0.0)
}

/**
 * Represents one independent leg of a [FinalPlannedRoute] (e.g., `Origin -> Stop 1`,
 * `Stop 1 -> Stop 2`, `Stop 2 -> Destination`) with its own geometry, segments, maneuvers,
 * SOC transitions, charging session details, and detour metadata (V13.3.1 Section 2).
 */
data class PlannedRouteLeg(
    val legIndex: Int,
    val start: GeoCoordinate,
    val end: GeoCoordinate,
    val polyline: List<GeoCoordinate>,
    val segments: List<RouteSegment>,
    val maneuvers: List<PlannedLegManeuver> = emptyList(),
    val distanceMeters: Double,
    val drivingDurationSeconds: Double,
    val energyRequiredWh: Double,
    val departureSocPercent: Double,
    val arrivalSocPercent: Double,
    val postChargeDepartureSocPercent: Double = arrivalSocPercent,
    val startName: String = "مبدأ قطعه ${legIndex + 1}",
    val endName: String = "مقصد قطعه ${legIndex + 1}",
    val chargingStopStationId: String? = null,
    val chargingStopStationName: String? = null,
    val chargingEnergyAddedKwh: Double = 0.0,
    val chargingDurationSeconds: Double = 0.0,
    val waitingDurationSeconds: Double = 0.0,
    val detourDistanceMeters: Double = 0.0,
    val detourDurationSeconds: Double = 0.0,
    val detourSource: DetourCalculationSource = DetourCalculationSource.CROSS_TRACK_GEOMETRIC_ESTIMATE
)

data class RouteConsistencyCheckResult(
    val isConsistent: Boolean,
    val routeId: String,
    val routeVersion: Long,
    val violations: List<String>
)

/**
 * Single Source of Truth (SSOT) representation of the final planned route across Map UI,
 * GPS navigation engine, Energy prediction, Charging stop cards, ETA display, and Android Auto
 * (V13.3.1 Sections 2, 16 & 21).
 */
data class FinalPlannedRoute(
    val routeId: String,
    val routeVersion: Long,
    val origin: GeoCoordinate,
    val destination: GeoCoordinate,
    val legs: List<PlannedRouteLeg>,
    val polyline: List<GeoCoordinate>,
    val segments: List<RouteSegment>,
    val maneuvers: List<PlannedLegManeuver> = emptyList(),
    val selectedChargingStopIds: List<String>,
    val totalDistanceMeters: Double,
    val baseDrivingDurationSeconds: Double,
    val detourDurationSeconds: Double,
    val chargingDurationSeconds: Double,
    val waitingDurationSeconds: Double,
    val weatherTrafficPenaltySeconds: Double,
    val totalTripDurationSeconds: Double,
    val optimisticDurationSeconds: Double,
    val pessimisticDurationSeconds: Double,
    val totalEnergyRequiredKwh: Double,
    val departureSocPercent: Double,
    val finalArrivalSocPercent: Double,
    val minReserveSocPercent: Double,
    val sourceClassification: RouteSourceClassification,
    val isRealRoadGraph: Boolean,
    val confidenceScore: Double,
    val warnings: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val provenanceAndDiagnostics: ProvenanceAndDiagnosticsReport? = null
) {
    /**
     * Strictly equals the number of selected charging stops in the final planned route,
     * never the count of raw corridor candidates or preset waypoints.
     */
    val chargingStopsCount: Int
        get() = selectedChargingStopIds.size

    fun toDomainRoute(): Route = Route(
        id = routeId,
        start = origin,
        end = destination,
        segments = segments,
        distance = Distance.fromMeters(totalDistanceMeters.coerceAtLeast(1.0)),
        estimatedTime = totalTripDurationSeconds.toLong().coerceAtLeast(1L),
        waypoints = polyline
    )

    /**
     * Validates that map geometry, leg distances, per-leg segments/maneuvers, charging stop counts,
     * and ETA components are mutually consistent before starting active turn-by-turn navigation
     * (V13.3.1 Sections 2 & 16).
     */
    fun validatePreNavigationConsistency(): RouteConsistencyCheckResult {
        val violations = mutableListOf<String>()
        if (routeId.isBlank()) violations.add("EMPTY_ROUTE_ID")
        if (polyline.size < 2) violations.add("DEGENERATE_POLYLINE (${polyline.size} points)")
        if (segments.isEmpty()) violations.add("EMPTY_ROUTE_SEGMENTS")
        if (!totalDistanceMeters.isFinite() || totalDistanceMeters <= 0.0) {
            violations.add("INVALID_TOTAL_DISTANCE ($totalDistanceMeters)")
        }
        if (totalDistanceMeters > 0.0 && baseDrivingDurationSeconds <= 0.0) {
            violations.add("ZERO_DRIVING_DURATION_FOR_NONZERO_DISTANCE")
        }
        if (legs.isEmpty()) {
            violations.add("EMPTY_PLANNED_LEGS")
        } else {
            val expectedMinLegs = selectedChargingStopIds.size + 1
            if (legs.size < expectedMinLegs) {
                violations.add("MULTI_STOP_COLLAPSED_LEGS (legs=${legs.size} < required=$expectedMinLegs for ${selectedChargingStopIds.size} charging stops)")
            }
            for (leg in legs) {
                if (leg.polyline.size < 2) {
                    violations.add("LEG_${leg.legIndex}_DEGENERATE_POLYLINE")
                }
                if (leg.segments.isEmpty()) {
                    violations.add("LEG_${leg.legIndex}_EMPTY_SEGMENTS")
                }
                if (leg.maneuvers.any { it.legIndex != leg.legIndex }) {
                    violations.add("LEG_${leg.legIndex}_STALE_MANEUVER_INDEX_MISMATCH")
                }
            }
            val legSumDist = legs.sumOf { it.distanceMeters + it.detourDistanceMeters }
            if (abs(legSumDist - totalDistanceMeters) > max(250.0, totalDistanceMeters * 0.05)) {
                violations.add("LEG_DISTANCE_MISMATCH (legs=$legSumDist vs total=$totalDistanceMeters)")
            }
            val legStops = legs.mapNotNull { it.chargingStopStationId }
            if (legStops.size != selectedChargingStopIds.size) {
                violations.add("CHARGING_STOP_COUNT_MISMATCH (legs=${legStops.size} vs selected=${selectedChargingStopIds.size})")
            }
        }
        val componentDurationSum = baseDrivingDurationSeconds +
            detourDurationSeconds +
            chargingDurationSeconds +
            waitingDurationSeconds +
            weatherTrafficPenaltySeconds
        if (abs(componentDurationSum - totalTripDurationSeconds) > 5.0) {
            violations.add("TOTAL_DURATION_COMPONENT_MISMATCH (sum=$componentDurationSum vs total=$totalTripDurationSeconds)")
        }
        return RouteConsistencyCheckResult(
            isConsistent = violations.isEmpty(),
            routeId = routeId,
            routeVersion = routeVersion,
            violations = violations
        )
    }
}

/**
 * Unified Trip Execution Context shared across MapNavigationViewModel, TripIntelligenceViewModel,
 * ActiveJourneyViewModel, CanonicalEnergyEngine, and CanonicalTravelTimeEngine (V13.3.1 Sections 2 & 16).
 */
data class TripExecutionContext(
    val routeId: String,
    val routeVersion: Long,
    val vehicle: DomainVehicle?,
    val vehicleQuality: VehicleQualityAssessment,
    val batteryAndSoc: BatteryAndSocState,
    val finalPlannedRoute: FinalPlannedRoute,
    val ambientTemperatureCelsius: Double?,
    val isTemperatureAuthoritative: Boolean,
    val windSpeedMps: Double? = null,
    val trafficMultiplier: Double = 1.0,
    val weatherSpeedReductionFraction: Double = 0.0,
    val evaluatedAtEpochMs: Long = DomainClock.SYSTEM.nowEpochMs()
)

object CanonicalFinalRouteAssembler {

    fun assembleFromMultiStopPlan(
        baseRouteId: String,
        routeVersion: Long = 1L,
        baseRoute: Route,
        plan: MultiStopRoutePlan,
        batteryAndSoc: BatteryAndSocState,
        sourceClassification: RouteSourceClassification,
        trafficMultiplier: Double = 1.0,
        weatherSpeedReductionFraction: Double = 0.0,
        waitingMinutesPerStop: Double = 0.0,
        extraWarnings: List<String> = emptyList(),
        extraAssumptions: List<String> = emptyList(),
        ambientTemperatureAuthoritative: Boolean = false,
        vehicleSpecAuthoritative: Boolean = true
    ): FinalPlannedRoute {
        val canonicalId = "${baseRouteId}_v$routeVersion"
        val plannedLegs = mutableListOf<PlannedRouteLeg>()
        val stitchedPolyline = mutableListOf<GeoCoordinate>()
        val stitchedSegments = mutableListOf<RouteSegment>()
        val stitchedManeuvers = mutableListOf<PlannedLegManeuver>()
        val selectedStopIds = mutableListOf<String>()

        var runningSoc = batteryAndSoc.currentSocPercent
        var cursorMeters = 0.0

        for ((idx, leg) in plan.legs.withIndex()) {
            val legPoly = leg.polyline.ifEmpty { listOf(leg.from.coordinate, leg.to.coordinate) }
            for (pt in legPoly) {
                val last = stitchedPolyline.lastOrNull()
                if (last == null || last.latitude != pt.latitude || last.longitude != pt.longitude) {
                    stitchedPolyline.add(pt)
                }
            }
            val legDistM = leg.distanceMeters.coerceAtLeast(1.0)
            val legDriveSec = (leg.durationMinutes * 60.0).coerceAtLeast(1.0)
            val legStartOffsetMeters = cursorMeters

            val legSegs = if (legPoly.size >= 2) {
                val rawEdgeDists = legPoly.zipWithNext { p1, p2 ->
                    CorridorStationProjector.haversineMeters(p1, p2).coerceAtLeast(1.0)
                }
                val rawPolySum = rawEdgeDists.sum().coerceAtLeast(1.0)
                val distScale = legDistM / rawPolySum
                legPoly.zipWithNext().mapIndexed { sIdx, (p1, p2) ->
                    val segD = (rawEdgeDists[sIdx] * distScale).coerceAtLeast(1.0)
                    val segStart = cursorMeters
                    cursorMeters += segD
                    val inputSeg = leg.segments.getOrNull(sIdx)
                    RouteSegment(
                        id = "${canonicalId}_leg_${idx}_seg_$sIdx",
                        start = p1,
                        end = p2,
                        distance = Distance.fromMeters(segD),
                        bearing = Bearing(0.0),
                        roadName = leg.to.name,
                        roadType = if (sourceClassification.isTrueRoadNetwork) "ROAD_NETWORK" else "CORRIDOR_FALLBACK",
                        startOffsetMeters = segStart,
                        endOffsetMeters = cursorMeters,
                        speedLimitKmh = inputSeg?.speedLimitKmh?.toInt() ?: 0
                    )
                }
            } else {
                val segStart = cursorMeters
                cursorMeters += legDistM
                listOf(
                    RouteSegment(
                        id = "${canonicalId}_leg_${idx}_seg_0",
                        start = leg.from.coordinate,
                        end = leg.to.coordinate,
                        distance = Distance.fromMeters(legDistM),
                        bearing = Bearing(0.0),
                        roadName = leg.to.name,
                        roadType = if (sourceClassification.isTrueRoadNetwork) "ROAD_NETWORK" else "CORRIDOR_FALLBACK",
                        startOffsetMeters = segStart,
                        endOffsetMeters = cursorMeters,
                        speedLimitKmh = 0
                    )
                )
            }
            stitchedSegments.addAll(legSegs)

            val legManeuvers = buildManeuversForLeg(
                legIndex = idx,
                fromName = leg.from.name,
                toName = leg.to.name,
                legPoly = legPoly,
                legDistanceMeters = legDistM,
                legStartOffsetMeters = legStartOffsetMeters,
                speedLimitKmh = legSegs.firstOrNull()?.speedLimitKmh?.takeIf { it > 0 }
            )
            stitchedManeuvers.addAll(legManeuvers)

            val isChargeStop = leg.to.isChargingStop && leg.chargingDurationMinutes > 0.0
            val stopId = if (isChargeStop) leg.to.id else null
            if (stopId != null) selectedStopIds.add(stopId)
            val chargeSec = if (isChargeStop) leg.chargingDurationMinutes * 60.0 else 0.0
            val waitSec = if (isChargeStop) waitingMinutesPerStop.coerceAtLeast(0.0) * 60.0 else 0.0

            plannedLegs.add(
                PlannedRouteLeg(
                    legIndex = idx,
                    start = leg.from.coordinate,
                    end = leg.to.coordinate,
                    polyline = legPoly,
                    segments = legSegs,
                    maneuvers = legManeuvers,
                    distanceMeters = legDistM,
                    drivingDurationSeconds = legDriveSec,
                    energyRequiredWh = leg.energyUsedKwh * 1000.0,
                    departureSocPercent = runningSoc,
                    arrivalSocPercent = leg.arrivalSocPercent,
                    postChargeDepartureSocPercent = leg.departureSocPercent,
                    startName = leg.from.name,
                    endName = leg.to.name,
                    chargingStopStationId = stopId,
                    chargingStopStationName = if (isChargeStop) leg.to.name else null,
                    chargingEnergyAddedKwh = leg.energyAddedKwh,
                    chargingDurationSeconds = chargeSec,
                    waitingDurationSeconds = waitSec
                )
            )
            runningSoc = leg.departureSocPercent
        }

        val finalPoly = if (stitchedPolyline.size >= 2) stitchedPolyline else baseRoute.waypoints.ifEmpty { listOf(baseRoute.start, baseRoute.end) }
        val finalSegs = if (stitchedSegments.isNotEmpty()) stitchedSegments else baseRoute.segments
        val totalDistM = plannedLegs.sumOf { it.distanceMeters + it.detourDistanceMeters }.coerceAtLeast(1.0)
        val baseDriveSec = (plan.totalDrivingMinutes * 60.0).coerceAtLeast(1.0)
        val totalChargeSec = plan.totalChargingMinutes * 60.0
        val totalWaitSec = selectedStopIds.size * waitingMinutesPerStop.coerceAtLeast(0.0) * 60.0

        val etaBreakdown = CanonicalTravelTimeEngine.calculateFromRouteDurations(
            baseDrivingDurationSeconds = baseDriveSec,
            detourDurationSeconds = 0.0,
            chargingDurationSeconds = totalChargeSec,
            waitingDurationSeconds = totalWaitSec,
            trafficMultiplier = trafficMultiplier,
            weatherSpeedReductionFraction = weatherSpeedReductionFraction,
            totalDistanceMeters = totalDistM,
            isRouteFromRealGraph = sourceClassification.isTrueRoadNetwork
        )

        val finalArrSoc = plannedLegs.lastOrNull()?.arrivalSocPercent ?: batteryAndSoc.currentSocPercent
        val allWarnings = (plan.warnings + extraWarnings).distinct()
        val allAssumptions = extraAssumptions.distinct()

        val provenanceReport = buildProvenanceReport(
            routeId = canonicalId,
            routeVersion = routeVersion,
            sourceClassification = sourceClassification,
            batteryAndSoc = batteryAndSoc,
            hasElevation = finalSegs.any { it.elevationProfile.isNotEmpty() },
            hasSpeedLimits = finalSegs.any { it.speedLimitKmh > 0 },
            ambientTempAuthoritative = ambientTemperatureAuthoritative,
            vehicleSpecAuthoritative = vehicleSpecAuthoritative,
            assumptions = allAssumptions,
            warnings = allWarnings
        )

        return FinalPlannedRoute(
            routeId = canonicalId,
            routeVersion = routeVersion,
            origin = finalPoly.first(),
            destination = finalPoly.last(),
            legs = plannedLegs,
            polyline = finalPoly,
            segments = finalSegs,
            maneuvers = stitchedManeuvers,
            selectedChargingStopIds = selectedStopIds,
            totalDistanceMeters = totalDistM,
            baseDrivingDurationSeconds = baseDriveSec,
            detourDurationSeconds = 0.0,
            chargingDurationSeconds = totalChargeSec,
            waitingDurationSeconds = totalWaitSec,
            weatherTrafficPenaltySeconds = etaBreakdown.weatherAndTrafficPenalty.seconds,
            totalTripDurationSeconds = etaBreakdown.totalDuration.seconds,
            optimisticDurationSeconds = etaBreakdown.optimisticTotalDuration.seconds,
            pessimisticDurationSeconds = etaBreakdown.pessimisticTotalDuration.seconds,
            totalEnergyRequiredKwh = plan.totalEnergyUsedKwh,
            departureSocPercent = batteryAndSoc.currentSocPercent,
            finalArrivalSocPercent = finalArrSoc,
            minReserveSocPercent = batteryAndSoc.reserveSocPercent,
            sourceClassification = sourceClassification,
            isRealRoadGraph = sourceClassification.isTrueRoadNetwork,
            confidenceScore = if (sourceClassification.isTrueRoadNetwork && batteryAndSoc.isSocAuthoritative) 0.86 else 0.64,
            warnings = allWarnings,
            assumptions = allAssumptions,
            provenanceAndDiagnostics = provenanceReport
        )
    }

    /**
     * Builds a canonical multi-leg [FinalPlannedRoute] when 0, 1, or 2+ [PlannedChargingStop]s are
     * selected along a base route (V13.3.1 Section 2). Splits the route into `stops.size + 1` distinct
     * legs (`Origin -> Stop 1 -> Stop 2 -> Destination`), slicing geometry, rebuilding segments and
     * maneuvers per leg, and stitching the full polyline through every selected charging stop!
     */
    fun assembleFromChargingStops(
        baseRouteId: String,
        routeVersion: Long = 1L,
        originCoord: GeoCoordinate,
        originName: String = "مبدأ",
        destinationCoord: GeoCoordinate,
        destinationName: String = "مقصد",
        basePolyline: List<GeoCoordinate>,
        baseSegments: List<RouteSegment>,
        baseDistanceMeters: Double,
        baseDrivingDurationSeconds: Double,
        totalEnergyRequiredKwh: Double,
        finalArrivalSocPercent: Double,
        stops: List<PlannedChargingStop>,
        batteryAndSoc: BatteryAndSocState,
        sourceClassification: RouteSourceClassification,
        confidenceScore: Double,
        ambientTemperatureAuthoritative: Boolean = false,
        vehicleSpecAuthoritative: Boolean = true,
        warnings: List<String> = emptyList(),
        assumptions: List<String> = emptyList()
    ): FinalPlannedRoute {
        val canonicalId = "${baseRouteId}_v$routeVersion"
        val safeBasePoly = basePolyline.ifEmpty { listOf(originCoord, destinationCoord) }
        val safeBaseDistM = baseDistanceMeters.coerceAtLeast(1.0)
        val safeBaseDriveSec = baseDrivingDurationSeconds.coerceAtLeast(1.0)

        val plannedLegs = mutableListOf<PlannedRouteLeg>()
        val stitchedPolyline = mutableListOf<GeoCoordinate>()
        val stitchedSegments = mutableListOf<RouteSegment>()
        val stitchedManeuvers = mutableListOf<PlannedLegManeuver>()
        val selectedStopIds = stops.map { it.stationId }

        if (stops.isEmpty()) {
            val legPoly = safeBasePoly
            stitchedPolyline.addAll(legPoly)
            val legSegs = if (baseSegments.isNotEmpty()) {
                baseSegments.mapIndexed { sIdx, seg ->
                    seg.copy(id = "${canonicalId}_leg_0_seg_$sIdx")
                }
            } else {
                buildSegmentsForLegPolyline(canonicalId, 0, legPoly, safeBaseDistM, 0.0, destinationName, sourceClassification)
            }
            stitchedSegments.addAll(legSegs)
            val legManeuvers = buildManeuversForLeg(0, originName, destinationName, legPoly, safeBaseDistM, 0.0, legSegs.firstOrNull()?.speedLimitKmh?.takeIf { it > 0 })
            stitchedManeuvers.addAll(legManeuvers)
            plannedLegs.add(
                PlannedRouteLeg(
                    legIndex = 0,
                    start = legPoly.first(),
                    end = legPoly.last(),
                    polyline = legPoly,
                    segments = legSegs,
                    maneuvers = legManeuvers,
                    distanceMeters = safeBaseDistM,
                    drivingDurationSeconds = safeBaseDriveSec,
                    energyRequiredWh = totalEnergyRequiredKwh * 1000.0,
                    departureSocPercent = batteryAndSoc.currentSocPercent,
                    arrivalSocPercent = finalArrivalSocPercent,
                    postChargeDepartureSocPercent = finalArrivalSocPercent,
                    startName = originName,
                    endName = destinationName
                )
            )
        } else {
            val orderedStops = stops.sortedBy { it.distanceFromStartMeters }
            var prevDistMeters = 0.0
            var prevCoord = originCoord
            var prevName = originName
            var prevDepartureSoc = batteryAndSoc.currentSocPercent
            var cumulativeRouteOffsetM = 0.0

            for ((idx, stop) in orderedStops.withIndex()) {
                val stopAlongM = stop.distanceFromStartMeters.coerceIn(prevDistMeters + 10.0, (safeBaseDistM - 10.0).coerceAtLeast(prevDistMeters + 10.0))
                val legBaseDistM = (stopAlongM - prevDistMeters).coerceAtLeast(1.0)
                val distFraction = (legBaseDistM / safeBaseDistM).coerceIn(0.01, 1.0)
                val legDriveSec = (safeBaseDriveSec * distFraction).coerceAtLeast(1.0)
                val legPoly = CorridorStationProjector.slicePolylineForLeg(
                    fullPolyline = safeBasePoly,
                    startDistanceMeters = prevDistMeters,
                    endDistanceMeters = stopAlongM,
                    overrideStartCoord = prevCoord,
                    overrideEndCoord = stop.location
                )
                for (pt in legPoly) {
                    val last = stitchedPolyline.lastOrNull()
                    if (last == null || last.latitude != pt.latitude || last.longitude != pt.longitude) {
                        stitchedPolyline.add(pt)
                    }
                }
                val legTotalDistWithDetour = legBaseDistM + stop.detourDistanceMeters
                val legSegs = buildSegmentsForLegPolyline(
                    canonicalId = canonicalId,
                    legIdx = idx,
                    legPoly = legPoly,
                    legDistanceMeters = legBaseDistM,
                    startOffsetMeters = cumulativeRouteOffsetM,
                    roadName = stop.stationName,
                    sourceClassification = sourceClassification
                )
                stitchedSegments.addAll(legSegs)
                val legManeuvers = buildManeuversForLeg(
                    legIndex = idx,
                    fromName = prevName,
                    toName = stop.stationName,
                    legPoly = legPoly,
                    legDistanceMeters = legTotalDistWithDetour,
                    legStartOffsetMeters = cumulativeRouteOffsetM,
                    speedLimitKmh = legSegs.firstOrNull()?.speedLimitKmh?.takeIf { it > 0 }
                )
                stitchedManeuvers.addAll(legManeuvers)

                val legEnergyWh = ((prevDepartureSoc - stop.arrivalSocPercent).coerceAtLeast(0.5) / 100.0) *
                    batteryAndSoc.usableCapacityKwh * 1000.0

                plannedLegs.add(
                    PlannedRouteLeg(
                        legIndex = idx,
                        start = prevCoord,
                        end = stop.location,
                        polyline = legPoly,
                        segments = legSegs,
                        maneuvers = legManeuvers,
                        distanceMeters = legBaseDistM,
                        drivingDurationSeconds = legDriveSec,
                        energyRequiredWh = legEnergyWh,
                        departureSocPercent = prevDepartureSoc,
                        arrivalSocPercent = stop.arrivalSocPercent,
                        postChargeDepartureSocPercent = stop.departureSocPercent,
                        startName = prevName,
                        endName = stop.stationName,
                        chargingStopStationId = stop.stationId,
                        chargingStopStationName = stop.stationName,
                        chargingEnergyAddedKwh = stop.energyToDeliverKwh,
                        chargingDurationSeconds = stop.chargingDurationMinutes * 60.0,
                        waitingDurationSeconds = 0.0,
                        detourDistanceMeters = stop.detourDistanceMeters,
                        detourDurationSeconds = stop.detourDurationMinutes * 60.0,
                        detourSource = stop.detourCalculationSource
                    )
                )

                cumulativeRouteOffsetM += legTotalDistWithDetour
                prevDistMeters = stopAlongM
                prevCoord = stop.location
                prevName = stop.stationName
                prevDepartureSoc = stop.departureSocPercent
            }

            // Final leg: Last Stop -> Destination
            val finalLegIdx = orderedStops.size
            val finalLegDistM = (safeBaseDistM - prevDistMeters).coerceAtLeast(1.0)
            val finalFraction = (finalLegDistM / safeBaseDistM).coerceIn(0.01, 1.0)
            val finalLegDriveSec = (safeBaseDriveSec * finalFraction).coerceAtLeast(1.0)
            val finalLegPoly = CorridorStationProjector.slicePolylineForLeg(
                fullPolyline = safeBasePoly,
                startDistanceMeters = prevDistMeters,
                endDistanceMeters = safeBaseDistM,
                overrideStartCoord = prevCoord,
                overrideEndCoord = destinationCoord
            )
            for (pt in finalLegPoly) {
                val last = stitchedPolyline.lastOrNull()
                if (last == null || last.latitude != pt.latitude || last.longitude != pt.longitude) {
                    stitchedPolyline.add(pt)
                }
            }
            val finalLegSegs = buildSegmentsForLegPolyline(
                canonicalId = canonicalId,
                legIdx = finalLegIdx,
                legPoly = finalLegPoly,
                legDistanceMeters = finalLegDistM,
                startOffsetMeters = cumulativeRouteOffsetM,
                roadName = destinationName,
                sourceClassification = sourceClassification
            )
            stitchedSegments.addAll(finalLegSegs)
            val finalLegManeuvers = buildManeuversForLeg(
                legIndex = finalLegIdx,
                fromName = prevName,
                toName = destinationName,
                legPoly = finalLegPoly,
                legDistanceMeters = finalLegDistM,
                legStartOffsetMeters = cumulativeRouteOffsetM,
                speedLimitKmh = finalLegSegs.firstOrNull()?.speedLimitKmh?.takeIf { it > 0 }
            )
            stitchedManeuvers.addAll(finalLegManeuvers)
            val finalLegEnergyWh = ((prevDepartureSoc - finalArrivalSocPercent).coerceAtLeast(0.5) / 100.0) *
                batteryAndSoc.usableCapacityKwh * 1000.0

            plannedLegs.add(
                PlannedRouteLeg(
                    legIndex = finalLegIdx,
                    start = prevCoord,
                    end = destinationCoord,
                    polyline = finalLegPoly,
                    segments = finalLegSegs,
                    maneuvers = finalLegManeuvers,
                    distanceMeters = finalLegDistM,
                    drivingDurationSeconds = finalLegDriveSec,
                    energyRequiredWh = finalLegEnergyWh,
                    departureSocPercent = prevDepartureSoc,
                    arrivalSocPercent = finalArrivalSocPercent,
                    postChargeDepartureSocPercent = finalArrivalSocPercent,
                    startName = prevName,
                    endName = destinationName
                )
            )
        }

        val totalDetourMeters = plannedLegs.sumOf { it.detourDistanceMeters }
        val totalDistM = plannedLegs.sumOf { it.distanceMeters + it.detourDistanceMeters }
        val totalBaseDriveSec = plannedLegs.sumOf { it.drivingDurationSeconds }
        val totalDetourSec = plannedLegs.sumOf { it.detourDurationSeconds }
        val totalChargeSec = plannedLegs.sumOf { it.chargingDurationSeconds }
        val totalWaitSec = plannedLegs.sumOf { it.waitingDurationSeconds }

        val etaBreakdown = CanonicalTravelTimeEngine.calculateFromRouteDurations(
            baseDrivingDurationSeconds = totalBaseDriveSec,
            detourDurationSeconds = totalDetourSec,
            chargingDurationSeconds = totalChargeSec,
            waitingDurationSeconds = totalWaitSec,
            totalDistanceMeters = totalDistM,
            isRouteFromRealGraph = sourceClassification.isTrueRoadNetwork
        )

        val provenanceReport = buildProvenanceReport(
            routeId = canonicalId,
            routeVersion = routeVersion,
            sourceClassification = sourceClassification,
            batteryAndSoc = batteryAndSoc,
            hasElevation = baseSegments.any { it.elevationProfile.isNotEmpty() },
            hasSpeedLimits = baseSegments.any { it.speedLimitKmh > 0 },
            ambientTempAuthoritative = ambientTemperatureAuthoritative,
            vehicleSpecAuthoritative = vehicleSpecAuthoritative,
            assumptions = assumptions,
            warnings = warnings
        )

        return FinalPlannedRoute(
            routeId = canonicalId,
            routeVersion = routeVersion,
            origin = stitchedPolyline.firstOrNull() ?: originCoord,
            destination = stitchedPolyline.lastOrNull() ?: destinationCoord,
            legs = plannedLegs,
            polyline = stitchedPolyline,
            segments = stitchedSegments,
            maneuvers = stitchedManeuvers,
            selectedChargingStopIds = selectedStopIds,
            totalDistanceMeters = totalDistM,
            baseDrivingDurationSeconds = totalBaseDriveSec,
            detourDurationSeconds = totalDetourSec,
            chargingDurationSeconds = totalChargeSec,
            waitingDurationSeconds = totalWaitSec,
            weatherTrafficPenaltySeconds = etaBreakdown.weatherAndTrafficPenalty.seconds,
            totalTripDurationSeconds = etaBreakdown.totalDuration.seconds,
            optimisticDurationSeconds = etaBreakdown.optimisticTotalDuration.seconds,
            pessimisticDurationSeconds = etaBreakdown.pessimisticTotalDuration.seconds,
            totalEnergyRequiredKwh = totalEnergyRequiredKwh,
            departureSocPercent = batteryAndSoc.currentSocPercent,
            finalArrivalSocPercent = finalArrivalSocPercent,
            minReserveSocPercent = batteryAndSoc.reserveSocPercent,
            sourceClassification = sourceClassification,
            isRealRoadGraph = sourceClassification.isTrueRoadNetwork,
            confidenceScore = confidenceScore,
            warnings = warnings.distinct(),
            assumptions = assumptions.distinct(),
            provenanceAndDiagnostics = provenanceReport
        )
    }

    /**
     * Builds a canonical [FinalPlannedRoute] from a [MultiStopJourneyPlan] (`JourneyWaypoint` workflow).
     */
    fun assembleFromJourneyPlan(
        plan: MultiStopJourneyPlan,
        routeVersion: Long = 1L,
        batteryAndSoc: BatteryAndSocState,
        sourceClassification: RouteSourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM
    ): FinalPlannedRoute {
        val canonicalId = "${plan.routeId}_v$routeVersion"
        val plannedLegs = mutableListOf<PlannedRouteLeg>()
        val stitchedSegments = mutableListOf<RouteSegment>()
        val stitchedManeuvers = mutableListOf<PlannedLegManeuver>()
        val selectedStopIds = mutableListOf<String>()
        var cursorMeters = 0.0

        for (leg in plan.legs) {
            val legPoly = leg.polyline.ifEmpty { listOf(leg.fromWaypoint.coordinate, leg.toWaypoint.coordinate) }
            val legStartOffset = cursorMeters
            val legSegs = buildSegmentsForLegPolyline(
                canonicalId = canonicalId,
                legIdx = leg.legIndex,
                legPoly = legPoly,
                legDistanceMeters = leg.distanceMeters.coerceAtLeast(1.0),
                startOffsetMeters = legStartOffset,
                roadName = leg.toWaypoint.name,
                sourceClassification = leg.sourceClassification
            )
            cursorMeters += leg.distanceMeters.coerceAtLeast(1.0)
            stitchedSegments.addAll(legSegs)
            val legManeuvers = if (leg.maneuvers.isNotEmpty()) {
                leg.maneuvers.map { it.copy(legIndex = leg.legIndex) }
            } else {
                buildManeuversForLeg(
                    legIndex = leg.legIndex,
                    fromName = leg.fromWaypoint.name,
                    toName = leg.toWaypoint.name,
                    legPoly = legPoly,
                    legDistanceMeters = leg.distanceMeters,
                    legStartOffsetMeters = legStartOffset,
                    speedLimitKmh = leg.segments.firstOrNull()?.speedLimitKmh?.toInt()
                )
            }
            stitchedManeuvers.addAll(legManeuvers)

            val isChargeStop = leg.toWaypoint.isChargingStop
            val stopId = if (isChargeStop) (leg.toWaypoint.stationDetails?.stationId ?: leg.toWaypoint.id) else null
            if (stopId != null) selectedStopIds.add(stopId)

            plannedLegs.add(
                PlannedRouteLeg(
                    legIndex = leg.legIndex,
                    start = leg.fromWaypoint.coordinate,
                    end = leg.toWaypoint.coordinate,
                    polyline = legPoly,
                    segments = legSegs,
                    maneuvers = legManeuvers,
                    distanceMeters = leg.distanceMeters,
                    drivingDurationSeconds = leg.durationSeconds,
                    energyRequiredWh = leg.estimatedEnergyKwh * 1000.0,
                    departureSocPercent = leg.departureSocPercent,
                    arrivalSocPercent = leg.arrivalSocPercent,
                    postChargeDepartureSocPercent = leg.postChargeDepartureSocPercent,
                    startName = leg.fromWaypoint.name,
                    endName = leg.toWaypoint.name,
                    chargingStopStationId = stopId,
                    chargingStopStationName = if (isChargeStop) leg.toWaypoint.name else null,
                    chargingEnergyAddedKwh = leg.energyAddedKwh,
                    chargingDurationSeconds = leg.chargingDurationMinutes * 60.0
                )
            )
        }

        val totalDistM = plannedLegs.sumOf { it.distanceMeters + it.detourDistanceMeters }.coerceAtLeast(1.0)
        val baseDriveSec = plannedLegs.sumOf { it.drivingDurationSeconds }.coerceAtLeast(1.0)
        val totalChargeSec = plannedLegs.sumOf { it.chargingDurationSeconds }
        val etaBreakdown = CanonicalTravelTimeEngine.calculateFromRouteDurations(
            baseDrivingDurationSeconds = baseDriveSec,
            detourDurationSeconds = 0.0,
            chargingDurationSeconds = totalChargeSec,
            waitingDurationSeconds = 0.0,
            totalDistanceMeters = totalDistM,
            isRouteFromRealGraph = sourceClassification.isTrueRoadNetwork
        )
        val poly = plan.fullPolyline.ifEmpty {
            listOf(plan.waypoints.first().coordinate, plan.waypoints.last().coordinate)
        }
        val provenanceReport = buildProvenanceReport(
            routeId = canonicalId,
            routeVersion = routeVersion,
            sourceClassification = sourceClassification,
            batteryAndSoc = batteryAndSoc,
            hasElevation = plan.legs.any { it.elevationGainMeters > 0.0 || it.elevationLossMeters > 0.0 },
            hasSpeedLimits = plan.legs.any { it.segments.any { s -> s.speedLimitKmh != null } },
            ambientTempAuthoritative = false,
            vehicleSpecAuthoritative = !plan.assumptions.contains("ASSUMED_VEHICLE_PHYSICS"),
            assumptions = plan.assumptions,
            warnings = plan.warnings
        )

        return FinalPlannedRoute(
            routeId = canonicalId,
            routeVersion = routeVersion,
            origin = poly.first(),
            destination = poly.last(),
            legs = plannedLegs,
            polyline = poly,
            segments = stitchedSegments,
            maneuvers = stitchedManeuvers,
            selectedChargingStopIds = selectedStopIds,
            totalDistanceMeters = totalDistM,
            baseDrivingDurationSeconds = baseDriveSec,
            detourDurationSeconds = 0.0,
            chargingDurationSeconds = totalChargeSec,
            waitingDurationSeconds = 0.0,
            weatherTrafficPenaltySeconds = etaBreakdown.weatherAndTrafficPenalty.seconds,
            totalTripDurationSeconds = etaBreakdown.totalDuration.seconds,
            optimisticDurationSeconds = etaBreakdown.optimisticTotalDuration.seconds,
            pessimisticDurationSeconds = etaBreakdown.pessimisticTotalDuration.seconds,
            totalEnergyRequiredKwh = plan.totalEnergyKwh,
            departureSocPercent = batteryAndSoc.currentSocPercent,
            finalArrivalSocPercent = plan.finalArrivalSocPercent,
            minReserveSocPercent = batteryAndSoc.reserveSocPercent,
            sourceClassification = sourceClassification,
            isRealRoadGraph = sourceClassification.isTrueRoadNetwork,
            confidenceScore = if (plan.isOverallFeasible) 0.85 else 0.55,
            warnings = plan.warnings,
            assumptions = plan.assumptions,
            provenanceAndDiagnostics = provenanceReport
        )
    }

    private fun buildSegmentsForLegPolyline(
        canonicalId: String,
        legIdx: Int,
        legPoly: List<GeoCoordinate>,
        legDistanceMeters: Double,
        startOffsetMeters: Double,
        roadName: String,
        sourceClassification: RouteSourceClassification
    ): List<RouteSegment> {
        if (legPoly.size < 2) {
            val pt = legPoly.firstOrNull() ?: GeoCoordinate(35.6997, 51.3380)
            return listOf(
                RouteSegment(
                    id = "${canonicalId}_leg_${legIdx}_seg_0",
                    start = pt,
                    end = pt,
                    distance = Distance.fromMeters(legDistanceMeters.coerceAtLeast(1.0)),
                    bearing = Bearing(0.0),
                    roadName = roadName,
                    roadType = if (sourceClassification.isTrueRoadNetwork) "ROAD_NETWORK" else "CORRIDOR_FALLBACK",
                    startOffsetMeters = startOffsetMeters,
                    endOffsetMeters = startOffsetMeters + legDistanceMeters.coerceAtLeast(1.0),
                    speedLimitKmh = 0
                )
            )
        }
        val rawLengths = legPoly.zipWithNext { a, b ->
            CorridorStationProjector.haversineMeters(a, b).coerceAtLeast(1.0)
        }
        val rawSum = rawLengths.sum().coerceAtLeast(1.0)
        val scale = legDistanceMeters.coerceAtLeast(1.0) / rawSum
        var cursor = startOffsetMeters
        return legPoly.zipWithNext().mapIndexed { sIdx, (p1, p2) ->
            val segLen = (rawLengths[sIdx] * scale).coerceAtLeast(1.0)
            val sStart = cursor
            cursor += segLen
            RouteSegment(
                id = "${canonicalId}_leg_${legIdx}_seg_$sIdx",
                start = p1,
                end = p2,
                distance = Distance.fromMeters(segLen),
                bearing = Bearing(0.0),
                roadName = roadName,
                roadType = if (sourceClassification.isTrueRoadNetwork) "ROAD_NETWORK" else "CORRIDOR_FALLBACK",
                startOffsetMeters = sStart,
                endOffsetMeters = cursor,
                speedLimitKmh = 0
            )
        }
    }

    private fun buildManeuversForLeg(
        legIndex: Int,
        fromName: String,
        toName: String,
        legPoly: List<GeoCoordinate>,
        legDistanceMeters: Double,
        legStartOffsetMeters: Double,
        speedLimitKmh: Int?
    ): List<PlannedLegManeuver> {
        val startPt = legPoly.firstOrNull() ?: return emptyList()
        val endPt = legPoly.lastOrNull() ?: startPt
        return listOf(
            PlannedLegManeuver(
                legIndex = legIndex,
                maneuverIndexInLeg = 0,
                coordinate = startPt,
                instructionFarsi = "حرکت از $fromName به سمت $toName",
                maneuverType = "DEPART",
                distanceMeters = legDistanceMeters,
                cumulativeDistanceAlongLegMeters = 0.0,
                cumulativeDistanceAlongRouteMeters = legStartOffsetMeters,
                roadName = toName,
                speedLimitKmh = speedLimitKmh
            ),
            PlannedLegManeuver(
                legIndex = legIndex,
                maneuverIndexInLeg = 1,
                coordinate = endPt,
                instructionFarsi = "ورود به $toName",
                maneuverType = "ARRIVE",
                distanceMeters = 0.0,
                cumulativeDistanceAlongLegMeters = legDistanceMeters,
                cumulativeDistanceAlongRouteMeters = legStartOffsetMeters + legDistanceMeters,
                roadName = toName,
                speedLimitKmh = speedLimitKmh
            )
        )
    }

    private fun buildProvenanceReport(
        routeId: String,
        routeVersion: Long,
        sourceClassification: RouteSourceClassification,
        batteryAndSoc: BatteryAndSocState,
        hasElevation: Boolean,
        hasSpeedLimits: Boolean,
        ambientTempAuthoritative: Boolean,
        vehicleSpecAuthoritative: Boolean,
        assumptions: List<String>,
        warnings: List<String>
    ): ProvenanceAndDiagnosticsReport {
        val elevProv = if (hasElevation) ParameterProvenanceStatus.VERIFIED else ParameterProvenanceStatus.UNKNOWN
        val speedProv = if (hasSpeedLimits) ParameterProvenanceStatus.VERIFIED else ParameterProvenanceStatus.ESTIMATED
        val tempProv = if (ambientTempAuthoritative) ParameterProvenanceStatus.USER_PROVIDED else ParameterProvenanceStatus.ESTIMATED
        val vehProv = if (vehicleSpecAuthoritative) ParameterProvenanceStatus.VERIFIED else ParameterProvenanceStatus.ESTIMATED
        val socProv = if (batteryAndSoc.isSocAuthoritative) ParameterProvenanceStatus.USER_PROVIDED else ParameterProvenanceStatus.ESTIMATED

        val paramMap = mapOf(
            "routeGeometry" to if (sourceClassification.isTrueRoadNetwork) ParameterProvenanceStatus.VERIFIED else ParameterProvenanceStatus.ESTIMATED,
            "elevationProfile" to elevProv,
            "roadSpeedProfile" to speedProv,
            "ambientTemperature" to tempProv,
            "vehicleSpecifications" to vehProv,
            "initialSoc" to socProv
        )
        val estimatedList = paramMap.filterValues { it == ParameterProvenanceStatus.ESTIMATED }.keys.toList() + assumptions
        val unknownList = paramMap.filterValues { it == ParameterProvenanceStatus.UNKNOWN }.keys.toList()

        return ProvenanceAndDiagnosticsReport(
            routeId = routeId,
            routeVersion = routeVersion,
            routeSource = sourceClassification,
            elevationSource = elevProv,
            speedSource = speedProv,
            ambientTemperatureSource = tempProv,
            vehicleSpecificationSource = vehProv,
            stationVerificationSource = ParameterProvenanceStatus.VERIFIED,
            portOperationalStatusSource = ParameterProvenanceStatus.ESTIMATED,
            initialSocSource = socProv,
            parameterSources = paramMap,
            estimatedParameters = estimatedList.distinct(),
            unknownParameters = unknownList.distinct(),
            diagnosticMessages = warnings.distinct()
        )
    }

    fun buildExecutionContext(
        vehicle: DomainVehicle?,
        batteryAndSoc: BatteryAndSocState,
        finalPlannedRoute: FinalPlannedRoute,
        ambientTemperatureCelsius: Double? = null,
        isTemperatureAuthoritative: Boolean = false,
        windSpeedMps: Double? = null,
        trafficMultiplier: Double = 1.0,
        weatherSpeedReductionFraction: Double = 0.0,
        clock: DomainClock = DomainClock.SYSTEM
    ): TripExecutionContext {
        val quality = VehicleDataQualityEvaluator.evaluate(vehicle, clock)
        return TripExecutionContext(
            routeId = finalPlannedRoute.routeId,
            routeVersion = finalPlannedRoute.routeVersion,
            vehicle = vehicle,
            vehicleQuality = quality,
            batteryAndSoc = batteryAndSoc,
            finalPlannedRoute = finalPlannedRoute,
            ambientTemperatureCelsius = ambientTemperatureCelsius,
            isTemperatureAuthoritative = isTemperatureAuthoritative,
            windSpeedMps = windSpeedMps,
            trafficMultiplier = trafficMultiplier,
            weatherSpeedReductionFraction = weatherSpeedReductionFraction,
            evaluatedAtEpochMs = clock.nowEpochMs()
        )
    }
}
