package com.evnavigator.iran.domain.journey.multistop

import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.DefaultEnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Waypoint ordering and automatic charging stop insertion collaborator for [MultiStopRouteCoordinator] (V13.3.1 Sections 6 & 8).
 *
 * V13.3.1 Invariants:
 * 1. Strictly matches AC vs DC vehicle max charging power against the station's connector standard (`isDcFast`),
 *    never substituting `dcMaxChargePowerKw` for an AC port or vice versa.
 * 2. Eliminates all hardcoded consumption baselines (`185 Wh/km`, `195 Wh/km`) and synthetic distance multipliers
 *    (`1.22`, `1.25`). All candidate reachability and next-leg energy requirements are computed through
 *    [RoadRouteProvider] and [CanonicalEnergyEngine.predictRouteEnergy].
 * 3. Preserves `ambientTempC = null` when temperature is unknown rather than silently injecting `15.0°C`.
 */
internal object MultiStopWaypointOptimizer {

    fun orderIntermediateWaypoints(
        origin: WaypointNode,
        destination: WaypointNode,
        intermediates: List<WaypointNode>
    ): List<WaypointNode> {
        if (intermediates.size <= 1) return intermediates
        val remaining = intermediates.toMutableList()
        val ordered = mutableListOf<WaypointNode>()
        var currentCoord = origin.coordinate

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { candidate ->
                val distFromCurrent = haversineMeters(currentCoord, candidate.coordinate)
                val distToDest = haversineMeters(candidate.coordinate, destination.coordinate)
                distFromCurrent + distToDest * 0.25
            }!!
            ordered.add(next)
            remaining.remove(next)
            currentCoord = next.coordinate
        }
        return ordered
    }

    /**
     * Selects the best reachable charging station between [from] and [to] using real road leg routing
     * ([roadRouteProvider]) and physical energy prediction ([energyPredictionService]).
     */
    suspend fun selectBestReachableCharger(
        from: GeoCoordinate,
        to: GeoCoordinate,
        currentSoc: Double,
        minArrivalSoc: Double,
        usableKwh: Double,
        vehicle: VehiclePhysicsParams,
        candidates: List<WaypointNode>,
        usedIds: Set<String>,
        roadRouteProvider: RoadRouteProvider? = null,
        energyPredictionService: EnergyPredictionService = DefaultEnergyPredictionService(),
        ambientTempC: Double? = null,
        warnings: MutableList<String>? = null
    ): WaypointNode? {
        val directRoute = roadRouteProvider?.calculateRoute(from, to)
        val totalDirectMeters = directRoute?.distanceMeters?.takeIf { it > 0.0 }
            ?: haversineMeters(from, to).coerceAtLeast(1.0)

        val vehicleSupportedConnectors: Set<CanonicalConnectorType> = vehicle.supportedConnectors
            .map { CanonicalConnectorType.fromRaw(it) }
            .filter { it != CanonicalConnectorType.UNKNOWN }
            .toSet()

        val eligibleCandidates = candidates.filter { cand ->
            cand.id !in usedIds &&
                cand.isChargingStop &&
                cand.maxChargingPowerKw > 0.0 &&
                isCandidateConnectorAndPowerCompatible(cand, vehicleSupportedConnectors, vehicle)
        }
        if (eligibleCandidates.isEmpty()) return null

        val scored = mutableListOf<Pair<WaypointNode, Double>>()
        for (cand in eligibleCandidates) {
            val legToCand = roadRouteProvider?.calculateRoute(from, cand.coordinate)
            val legFromCandToDest = roadRouteProvider?.calculateRoute(cand.coordinate, to)
            if (roadRouteProvider != null && (legToCand == null || !legToCand.isRealRoadGraph)) {
                continue
            }

            val distToCandMeters = legToCand?.distanceMeters?.takeIf { it > 0.0 }
                ?: haversineMeters(from, cand.coordinate)
            val distCandToDestMeters = legFromCandToDest?.distanceMeters?.takeIf { it > 0.0 }
                ?: haversineMeters(cand.coordinate, to)

            if (distToCandMeters < 2_000.0) continue
            if (distCandToDestMeters >= totalDirectMeters * 0.98) continue
            val detourMeters = (distToCandMeters + distCandToDestMeters) - totalDirectMeters
            if (detourMeters > max(35_000.0, totalDirectMeters * 0.30)) continue

            val segmentsToCand = legToCand?.segments?.takeIf { it.isNotEmpty() }
                ?: listOf(RouteSegmentInput(distanceMeters = distToCandMeters, startElevationMeters = null, endElevationMeters = null))

            val energyPred = CanonicalEnergyEngine.predictRouteEnergy(
                route = RouteProfileInput(
                    segments = segmentsToCand,
                    isRouteAverageApproximation = legToCand == null || legToCand.segments.isEmpty()
                ),
                vehicle = vehicle.copy(usableBatteryKwh = usableKwh),
                conditions = EnvironmentalConditions(
                    departureSocPercent = currentSoc,
                    minReserveSocPercent = minArrivalSoc,
                    ambientTempC = ambientTempC ?: Double.NaN,
                    isTemperatureMeasured = ambientTempC != null
                ),
                delegate = energyPredictionService
            )

            val arrivalSocAtCand = energyPred.predictedArrivalSocPercent
            if (!arrivalSocAtCand.isFinite() || arrivalSocAtCand < minArrivalSoc) {
                continue
            }
            if (legToCand == null) {
                warnings?.add("ESTIMATED_CANDIDATE_REACHABILITY:${cand.id}")
            }

            val progressScore = (distToCandMeters / totalDirectMeters.coerceAtLeast(1.0)).coerceIn(0.05, 1.0)
            val (_, effectiveVehKw) = resolvePortModeAndVehicleKw(cand, vehicleSupportedConnectors, vehicle)
            val effectiveKw = if (effectiveVehKw != null && effectiveVehKw > 0.0) {
                min(cand.maxChargingPowerKw, effectiveVehKw)
            } else {
                cand.maxChargingPowerKw
            }
            val powerScore = (effectiveKw / 150.0).coerceIn(0.1, 1.5)
            val detourPenalty = (detourMeters.coerceAtLeast(0.0) / 10_000.0) * 0.15
            scored.add(cand to (progressScore * 0.65 + powerScore * 0.35 - detourPenalty))
        }

        return scored.maxByOrNull { it.second }?.first
    }

    /**
     * Resolves whether the candidate station stop operates in DC fast mode or AC mode and returns
     * the corresponding vehicle acceptance power limit (`dcMaxChargePowerKw` for DC, `acMaxChargePowerKw` for AC).
     */
    fun resolvePortModeAndVehicleKw(
        candidate: WaypointNode,
        vehicleSupportedConnectors: Set<CanonicalConnectorType>,
        vehicle: VehiclePhysicsParams
    ): Pair<Boolean, Double?> {
        val candidateConnectors = candidate.supportedConnectors
            .map { CanonicalConnectorType.fromRaw(it) }
            .filter { it != CanonicalConnectorType.UNKNOWN }
            .toSet()
        val matching = if (candidateConnectors.isNotEmpty() && vehicleSupportedConnectors.isNotEmpty()) {
            candidateConnectors.intersect(vehicleSupportedConnectors)
        } else {
            emptySet()
        }
        val canDoDc = matching.any { it.isDcFast } && (vehicle.dcMaxChargePowerKw ?: 0.0) > 0.0
        val canDoAc = matching.any { !it.isDcFast } && (vehicle.acMaxChargePowerKw ?: 0.0) > 0.0
        return when {
            canDoDc -> true to vehicle.dcMaxChargePowerKw
            canDoAc -> false to vehicle.acMaxChargePowerKw
            else -> false to null
        }
    }

    internal fun isCandidateConnectorAndPowerCompatible(
        candidate: WaypointNode,
        vehicleSupportedConnectors: Set<CanonicalConnectorType>,
        vehicle: VehiclePhysicsParams
    ): Boolean {
        val candidateConnectors = candidate.supportedConnectors
            .map { CanonicalConnectorType.fromRaw(it) }
            .filter { it != CanonicalConnectorType.UNKNOWN }
            .toSet()
        if (candidateConnectors.isEmpty() || vehicleSupportedConnectors.isEmpty()) {
            return false
        }
        val common = candidateConnectors.intersect(vehicleSupportedConnectors)
        if (common.isEmpty()) return false
        return common.any { conn ->
            if (conn.isDcFast) (vehicle.dcMaxChargePowerKw ?: 0.0) > 0.0
            else (vehicle.acMaxChargePowerKw ?: 0.0) > 0.0
        }
    }

    suspend fun buildLegWithCharging(
        from: WaypointNode,
        to: WaypointNode,
        legResult: LegRouteResult,
        arrivalSoc: Double,
        usableKwh: Double,
        minSafeSocPercent: Double,
        targetChargeSocPercent: Double,
        vehicle: VehiclePhysicsParams,
        ambientTempC: Double?,
        chargingTimeEstimator: ChargingTimeEstimator,
        nextStopOrDest: WaypointNode?,
        warnings: MutableList<String>,
        roadRouteProvider: RoadRouteProvider? = null,
        energyPredictionService: EnergyPredictionService = DefaultEnergyPredictionService()
    ): RouteLegPlan {
        var depSoc = arrivalSoc
        var chargeMinutes = 0.0
        var energyAddedKwh = 0.0

        val vehicleConns = vehicle.supportedConnectors
            .map { CanonicalConnectorType.fromRaw(it) }
            .filter { it != CanonicalConnectorType.UNKNOWN }
            .toSet()
        val (isDcFast, vehicleMaxChargeKw) = resolvePortModeAndVehicleKw(to, vehicleConns, vehicle)

        if (to.isChargingStop && to.maxChargingPowerKw > 0.0 && vehicleMaxChargeKw != null && vehicleMaxChargeKw > 0.0) {
            val neededForNextLegSoc = if (nextStopOrDest != null) {
                val nextLegRoute = roadRouteProvider?.calculateRoute(to.coordinate, nextStopOrDest.coordinate)
                val nextSegments = nextLegRoute?.segments?.takeIf { it.isNotEmpty() }
                    ?: listOf(
                        RouteSegmentInput(
                            distanceMeters = (nextLegRoute?.distanceMeters ?: haversineMeters(to.coordinate, nextStopOrDest.coordinate)).coerceAtLeast(1.0),
                            startElevationMeters = null,
                            endElevationMeters = null
                        )
                    )
                if (nextLegRoute == null) {
                    warnings.add("NEXT_LEG_ENERGY_ESTIMATED_WITHOUT_ROAD_GRAPH:${to.id}->${nextStopOrDest.id}")
                }
                val nextPred = CanonicalEnergyEngine.predictRouteEnergy(
                    route = RouteProfileInput(
                        segments = nextSegments,
                        isRouteAverageApproximation = nextLegRoute == null || nextLegRoute.segments.isEmpty()
                    ),
                    vehicle = vehicle.copy(usableBatteryKwh = usableKwh),
                    conditions = EnvironmentalConditions(
                        departureSocPercent = targetChargeSocPercent,
                        minReserveSocPercent = minSafeSocPercent,
                        ambientTempC = ambientTempC ?: Double.NaN,
                        isTemperatureMeasured = ambientTempC != null
                    ),
                    delegate = energyPredictionService
                )
                val estNextKwh = (nextPred.breakdown.netBatteryEnergyWh / 1000.0).coerceAtLeast(0.0)
                val uncertaintyMarginSoc = if (nextLegRoute == null) 12.0 else 8.0
                minSafeSocPercent + uncertaintyMarginSoc + (estNextKwh / usableKwh) * 100.0
            } else {
                targetChargeSocPercent
            }

            val desiredTarget = max(targetChargeSocPercent, neededForNextLegSoc.coerceAtMost(95.0))
                .coerceIn(arrivalSoc, 95.0)

            if (desiredTarget > arrivalSoc + 2.0) {
                depSoc = desiredTarget
                energyAddedKwh = ((depSoc - arrivalSoc) / 100.0) * usableKwh
                if (ambientTempC == null) {
                    warnings.add("MODEL_ASSUMPTION:CHARGING_AMBIENT_TEMPERATURE_UNKNOWN")
                }
                chargeMinutes = chargingTimeEstimator.estimateChargingMinutes(
                    arrivalSocPercent = arrivalSoc,
                    targetSocPercent = depSoc,
                    batteryCapacityKwh = usableKwh,
                    maxStationPowerKw = to.maxChargingPowerKw,
                    maxVehiclePowerKw = vehicleMaxChargeKw,
                    includeHandshakeOverhead = true,
                    ambientTempC = ambientTempC,
                    isDcFast = isDcFast
                )
            }
        } else if (to.isChargingStop && (vehicleMaxChargeKw == null || vehicleMaxChargeKw <= 0.0)) {
            warnings.add("توان شارژ متناظر خودرو (AC/DC) برای ایستگاه «${to.name}» نامشخص است؛ زمان شارژ قابل محاسبه نیست.")
        }

        if (arrivalSoc < minSafeSocPercent) {
            warnings.add("هشدار: شارژ باتری در ورود به «${to.name}» (${arrivalSoc.toInt()}٪) کمتر از حد ایمن (${minSafeSocPercent.toInt()}٪) است.")
        }

        return RouteLegPlan(
            from = from,
            to = to,
            distanceMeters = legResult.distanceMeters,
            durationMinutes = legResult.durationSeconds / 60.0,
            energyUsedKwh = legResult.energyKwh,
            arrivalSocPercent = arrivalSoc,
            departureSocPercent = depSoc,
            chargingDurationMinutes = chargeMinutes,
            energyAddedKwh = energyAddedKwh,
            polyline = legResult.polyline,
            segments = legResult.segments,
            maneuvers = legResult.maneuvers,
            sourceClassification = legResult.sourceClassification
        )
    }

    fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
