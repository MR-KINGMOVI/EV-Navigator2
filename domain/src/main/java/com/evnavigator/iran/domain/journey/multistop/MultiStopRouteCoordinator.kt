package com.evnavigator.iran.domain.journey.multistop

import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.DefaultEnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.trip.CanonicalTravelTimeEngine
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.routing.RoutingEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class WaypointNode(
    val id: String,
    val name: String,
    val coordinate: GeoCoordinate,
    val isChargingStop: Boolean = false,
    val maxChargingPowerKw: Double = 0.0,
    val supportedConnectors: List<String> = emptyList()
)

data class RouteLegPlan(
    val from: WaypointNode,
    val to: WaypointNode,
    val distanceMeters: Double,
    val durationMinutes: Double,
    val energyUsedKwh: Double,
    val arrivalSocPercent: Double,
    val departureSocPercent: Double,
    val chargingDurationMinutes: Double = 0.0,
    val energyAddedKwh: Double = 0.0,
    val polyline: List<GeoCoordinate> = emptyList(),
    val segments: List<RouteSegmentInput> = emptyList(),
    val maneuvers: List<PlannedLegManeuver> = emptyList(),
    val sourceClassification: RouteSourceClassification = RouteSourceClassification.FULL_OFFLINE_OSM
)

data class MultiStopRoutePlan(
    val legs: List<RouteLegPlan>,
    val totalDistanceKm: Double,
    val totalDrivingMinutes: Double,
    val totalChargingMinutes: Double,
    val totalTripMinutes: Double,
    val totalEnergyUsedKwh: Double,
    val totalEnergyChargedKwh: Double,
    val finalArrivalSocPercent: Double,
    val isFeasible: Boolean,
    val warnings: List<String> = emptyList()
)

typealias RoadRouteCalculationResult = LegRouteResult

data class LegRouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val energyKwh: Double = 0.0,
    val polyline: List<GeoCoordinate> = emptyList(),
    val isRealRoadGraph: Boolean = true,
    val routeCategory: RouteCategory = if (isRealRoadGraph) {
        RouteCategory.AUTHENTIC_ROAD_GRAPH
    } else {
        RouteCategory.CURATED_CORRIDOR_FALLBACK
    },
    val sourceClassification: RouteSourceClassification = when (routeCategory) {
        RouteCategory.AUTHENTIC_ROAD_GRAPH -> RouteSourceClassification.FULL_OFFLINE_OSM
        RouteCategory.CURATED_CORRIDOR_FALLBACK -> RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK
        RouteCategory.ESTIMATED_GEODESIC, RouteCategory.SYNTHETIC_ESTIMATE -> RouteSourceClassification.STRAIGHT_LINE_ESTIMATE
        RouteCategory.UNKNOWN -> RouteSourceClassification.UNKNOWN
    },
    val segments: List<RouteSegmentInput> = emptyList(),
    val maneuvers: List<PlannedLegManeuver> = emptyList(),
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0
)

/**
 * Unified road-network route provider interface (V13.3.1 Section 1).
 * Supports both `calculateRoute` and `calculateLeg` seamlessly across SAM lambdas,
 * anonymous test providers, `CorridorStationProjector`, and `MultiStopRouteCoordinator`.
 */
fun interface RoadRouteProvider {
    suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): RoadRouteCalculationResult?

    suspend fun calculateLeg(from: GeoCoordinate, to: GeoCoordinate): LegRouteResult? =
        calculateRoute(from, to)
}

/**
 * Production [RoadRouteProvider] backed by the domain [RoutingEngine].
 */
class DefaultRoadRouteProvider(
    private val routingEngine: RoutingEngine,
    private val sourceClassifier: () -> RouteSourceClassification = { RouteSourceClassification.FULL_OFFLINE_OSM }
) : RoadRouteProvider {
    override suspend fun calculateRoute(from: GeoCoordinate, to: GeoCoordinate): RoadRouteCalculationResult? {
        return try {
            val route = routingEngine.calculateRoute(from, to)
            val distM = route.distance.meters
            if (!distM.isFinite() || distM <= 0.0) return null
            val poly = if (route.waypoints.size >= 2) {
                route.waypoints
            } else if (route.segments.isNotEmpty()) {
                buildList {
                    add(route.segments.first().start)
                    for (seg in route.segments) add(seg.end)
                }
            } else {
                listOf(from, to)
            }
            val srcClass = sourceClassifier()
            val cat = if (srcClass.isTrueRoadNetwork) RouteCategory.AUTHENTIC_ROAD_GRAPH else RouteCategory.CURATED_CORRIDOR_FALLBACK
            val segInputs = if (route.segments.isNotEmpty()) {
                route.segments.map { seg ->
                    val sElev = seg.elevationProfile.firstOrNull()?.elevation?.altitude?.value()
                    val eElev = seg.elevationProfile.lastOrNull()?.elevation?.altitude?.value()
                    RouteSegmentInput(
                        distanceMeters = seg.distance.meters.coerceAtLeast(1.0),
                        startElevationMeters = sElev,
                        endElevationMeters = eElev,
                        speedLimitKmh = seg.speedLimitKmh.takeIf { it in 5..140 }?.toDouble(),
                        targetSpeedKmh = seg.estimatedAverageSpeedKmh?.takeIf { it in 5.0..140.0 }
                    )
                }
            } else {
                listOf(RouteSegmentInput(distanceMeters = distM, startElevationMeters = null, endElevationMeters = null))
            }
            LegRouteResult(
                distanceMeters = distM,
                durationSeconds = route.estimatedTime.toDouble().coerceAtLeast(1.0),
                energyKwh = 0.0,
                polyline = poly,
                isRealRoadGraph = true,
                routeCategory = cat,
                sourceClassification = srcClass,
                segments = segInputs,
                elevationGainMeters = route.totalClimb,
                elevationLossMeters = route.totalDescent
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Coordinates multi-stop route planning and automatic charging stop insertion (V13.3.1 Sections 1, 2, 3 & 6).
 */
class MultiStopRouteCoordinator(
    private val roadRouteProvider: RoadRouteProvider,
    private val energyPredictionService: EnergyPredictionService = DefaultEnergyPredictionService(),
    private val chargingTimeEstimator: ChargingTimeEstimator = ChargingTimeEstimator(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * WaypointNode-based multi-stop planning entry point returning [MultiStopRoutePlan].
     */
    suspend fun createPlan(
        origin: WaypointNode,
        destination: WaypointNode,
        intermediateWaypoints: List<WaypointNode> = emptyList(),
        candidateChargingStations: List<WaypointNode> = emptyList(),
        initialSocPercent: Double = 80.0,
        usableBatteryKwh: Double,
        minSafeSocPercent: Double = 15.0,
        targetChargeSocPercent: Double = 80.0,
        optimizeIntermediateOrder: Boolean = false,
        vehiclePhysics: VehiclePhysicsParams? = null,
        ambientTemperatureCelsius: Double? = null
    ): MultiStopRoutePlan = withContext(defaultDispatcher) {
        val warnings = mutableListOf<String>()
        val safeUsableKwh = usableBatteryKwh.takeIf { it.isFinite() && it > 0.0 }
            ?: vehiclePhysics?.usableBatteryKwh?.takeIf { it.isFinite() && it > 0.0 }
            ?: throw IllegalArgumentException("usableBatteryKwh ($usableBatteryKwh) must be positive and finite")

        val resolvedVehicle = vehiclePhysics?.copy(usableBatteryKwh = safeUsableKwh)
            ?: VehiclePhysicsParams.assumedReference(usableBatteryKwh = safeUsableKwh)
        if (vehiclePhysics == null || resolvedVehicle.isAssumedFallback) {
            warnings.add("مشخصات فیزیکی دقیق خودرو تأیید نشده است؛ محاسبه انرژی با پارامترهای مرجع تخمینی (ASSUMED) انجام شد.")
        }

        val orderedIntermediates = if (optimizeIntermediateOrder && intermediateWaypoints.size > 1) {
            MultiStopWaypointOptimizer.orderIntermediateWaypoints(origin, destination, intermediateWaypoints)
        } else {
            intermediateWaypoints
        }

        val allStops = buildList {
            add(origin)
            addAll(orderedIntermediates)
            add(destination)
        }

        val legs = mutableListOf<RouteLegPlan>()
        var currentSoc = initialSocPercent.coerceIn(5.0, 100.0)
        var currentStop = allStops.first()
        var targetIndex = 1
        val usedStationIds = mutableSetOf<String>()
        var totalInjectedStops = 0

        while (targetIndex < allStops.size) {
            val nextTarget = allStops[targetIndex]
            val directLeg = calculateLegWithEnergy(
                from = currentStop.coordinate,
                to = nextTarget.coordinate,
                departureSoc = currentSoc,
                minSafeSoc = minSafeSocPercent,
                vehicle = resolvedVehicle,
                ambientTempC = ambientTemperatureCelsius,
                warnings = warnings
            )
            val arrivalSocDirect = currentSoc - (directLeg.energyKwh / safeUsableKwh) * 100.0

            if (arrivalSocDirect >= minSafeSocPercent || candidateChargingStations.isEmpty() || totalInjectedStops >= 6) {
                val legPlan = MultiStopWaypointOptimizer.buildLegWithCharging(
                    from = currentStop,
                    to = nextTarget,
                    legResult = directLeg,
                    arrivalSoc = arrivalSocDirect,
                    usableKwh = safeUsableKwh,
                    minSafeSocPercent = minSafeSocPercent,
                    targetChargeSocPercent = targetChargeSocPercent,
                    vehicle = resolvedVehicle,
                    ambientTempC = ambientTemperatureCelsius,
                    chargingTimeEstimator = chargingTimeEstimator,
                    nextStopOrDest = allStops.getOrNull(targetIndex + 1),
                    warnings = warnings,
                    roadRouteProvider = roadRouteProvider,
                    energyPredictionService = energyPredictionService
                )
                legs.add(legPlan)
                currentSoc = legPlan.departureSocPercent
                currentStop = nextTarget
                targetIndex++
            } else {
                val selectedCharger = MultiStopWaypointOptimizer.selectBestReachableCharger(
                    from = currentStop.coordinate,
                    to = nextTarget.coordinate,
                    currentSoc = currentSoc,
                    minArrivalSoc = minSafeSocPercent,
                    usableKwh = safeUsableKwh,
                    vehicle = resolvedVehicle,
                    candidates = candidateChargingStations,
                    usedIds = usedStationIds,
                    roadRouteProvider = roadRouteProvider,
                    energyPredictionService = energyPredictionService,
                    ambientTempC = ambientTemperatureCelsius,
                    warnings = warnings
                )

                if (selectedCharger == null) {
                    warnings.add("ایستگاه شارژ سازگار و قابل دسترس بین «${currentStop.name}» و «${nextTarget.name}» یافت نشد.")
                    val legPlan = MultiStopWaypointOptimizer.buildLegWithCharging(
                        from = currentStop,
                        to = nextTarget,
                        legResult = directLeg,
                        arrivalSoc = arrivalSocDirect,
                        usableKwh = safeUsableKwh,
                        minSafeSocPercent = minSafeSocPercent,
                        targetChargeSocPercent = targetChargeSocPercent,
                        vehicle = resolvedVehicle,
                        ambientTempC = ambientTemperatureCelsius,
                        chargingTimeEstimator = chargingTimeEstimator,
                        nextStopOrDest = allStops.getOrNull(targetIndex + 1),
                        warnings = warnings,
                        roadRouteProvider = roadRouteProvider,
                        energyPredictionService = energyPredictionService
                    )
                    legs.add(legPlan)
                    currentSoc = legPlan.departureSocPercent
                    currentStop = nextTarget
                    targetIndex++
                } else {
                    usedStationIds.add(selectedCharger.id)
                    totalInjectedStops++
                    val toChargerLeg = calculateLegWithEnergy(
                        from = currentStop.coordinate,
                        to = selectedCharger.coordinate,
                        departureSoc = currentSoc,
                        minSafeSoc = minSafeSocPercent,
                        vehicle = resolvedVehicle,
                        ambientTempC = ambientTemperatureCelsius,
                        warnings = warnings
                    )
                    val arrivalAtCharger = currentSoc - (toChargerLeg.energyKwh / safeUsableKwh) * 100.0
                    val chargerLegPlan = MultiStopWaypointOptimizer.buildLegWithCharging(
                        from = currentStop,
                        to = selectedCharger,
                        legResult = toChargerLeg,
                        arrivalSoc = arrivalAtCharger,
                        usableKwh = safeUsableKwh,
                        minSafeSocPercent = minSafeSocPercent,
                        targetChargeSocPercent = targetChargeSocPercent,
                        vehicle = resolvedVehicle,
                        ambientTempC = ambientTemperatureCelsius,
                        chargingTimeEstimator = chargingTimeEstimator,
                        nextStopOrDest = nextTarget,
                        warnings = warnings,
                        roadRouteProvider = roadRouteProvider,
                        energyPredictionService = energyPredictionService
                    )
                    legs.add(chargerLegPlan)
                    currentSoc = chargerLegPlan.departureSocPercent
                    currentStop = selectedCharger
                }
            }
        }

        val totalDistKm = legs.sumOf { it.distanceMeters } / 1000.0
        val totalDriveMin = legs.sumOf { it.durationMinutes }
        val totalChargeMin = legs.sumOf { it.chargingDurationMinutes }
        val totalEnergyUsed = legs.sumOf { it.energyUsedKwh }
        val totalEnergyAdded = legs.sumOf { it.energyAddedKwh }
        val finalSoc = legs.lastOrNull()?.arrivalSocPercent ?: initialSocPercent
        val feasible = legs.all { it.arrivalSocPercent >= 5.0 } && finalSoc >= minSafeSocPercent * 0.5

        MultiStopRoutePlan(
            legs = legs,
            totalDistanceKm = totalDistKm,
            totalDrivingMinutes = totalDriveMin,
            totalChargingMinutes = totalChargeMin,
            totalTripMinutes = totalDriveMin + totalChargeMin,
            totalEnergyUsedKwh = totalEnergyUsed,
            totalEnergyChargedKwh = totalEnergyAdded,
            finalArrivalSocPercent = finalSoc,
            isFeasible = feasible,
            warnings = warnings.distinct()
        )
    }

    /**
     * Canonical JourneyWaypoint-based multi-stop planning entry point returning [Result]<[MultiStopJourneyPlan]>
     * (V13.3.1 Sections 1 & 2). Used by `JourneyRouteAndSimulationCoordinator`, `ActiveJourneyViewModel`,
     * `MapNavigationViewModel`, and `MultiStopRouteCoordinatorTest`.
     */
    suspend fun createPlan(
        vehicleId: String,
        batteryCapacityKwh: Double,
        initialSocPercent: Double,
        rawWaypoints: List<JourneyWaypoint>,
        vehiclePhysics: VehiclePhysicsParams? = null,
        isDepartureSocVerified: Boolean = false,
        departureSocSource: String = "UNCONFIRMED_DEFAULT",
        ambientTemperatureCelsius: Double? = null
    ): Result<MultiStopJourneyPlan> = withContext(defaultDispatcher) {
        try {
            if (rawWaypoints.size < 2) {
                return@withContext Result.failure(
                    IllegalArgumentException("Multi-stop route requires at least 2 waypoints (origin and destination), got ${rawWaypoints.size}")
                )
            }
            for (wp in rawWaypoints) {
                val c = wp.coordinate
                if (!c.latitude.isFinite() || !c.longitude.isFinite() ||
                    c.latitude !in 24.0..40.5 || c.longitude !in 43.5..64.5
                ) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Waypoint '${wp.name}' (${c.latitude}, ${c.longitude}) is outside Iran geographic bounds")
                    )
                }
            }
            if (!initialSocPercent.isFinite() || initialSocPercent !in 0.0..100.0) {
                return@withContext Result.failure(
                    IllegalArgumentException("initialSocPercent ($initialSocPercent) must be within [0, 100]")
                )
            }

            val usableKwh = batteryCapacityKwh.takeIf { it.isFinite() && it > 0.0 }
                ?: vehiclePhysics?.usableBatteryKwh?.takeIf { it.isFinite() && it > 0.0 }
                ?: return@withContext Result.failure(
                    IllegalArgumentException("batteryCapacityKwh ($batteryCapacityKwh) must be positive and finite")
                )

            val resolvedPhysics = vehiclePhysics?.copy(usableBatteryKwh = usableKwh)
                ?: VehiclePhysicsParams(
                    vehicleId = vehicleId,
                    usableBatteryKwh = usableKwh,
                    isAssumedFallback = vehicleId.isBlank() || vehicleId == "assumed_fallback" || vehicleId == "unknown_car"
                )

            val warnings = mutableListOf<String>()
            val assumptions = mutableListOf<String>()
            if (resolvedPhysics.isAssumedFallback) {
                warnings.add("مشخصات فنی خودرو فرضی (ASSUMED) است؛ دقت تخمین مصرف انرژی کاهش یافت.")
                assumptions.add("ASSUMED_VEHICLE_PHYSICS")
            }
            if (!isDepartureSocVerified) {
                assumptions.add("UNVERIFIED_INITIAL_SOC")
            }

            val vehicleCanonicalConns = if (resolvedPhysics.isAssumedFallback) {
                emptySet()
            } else {
                resolvedPhysics.supportedConnectors
                    .map { CanonicalConnectorType.parse(it) }
                    .filter { it != CanonicalConnectorType.UNKNOWN }
                    .toSet()
            }

            val builtLegs = mutableListOf<MultiStopJourneyLeg>()
            var runningSoc = initialSocPercent.coerceIn(0.0, 100.0)
            var totalChargeMin = 0.0
            var totalUncertainChargeMin = 0.0
            var cumulativeRouteMeters = 0.0
            var anyLegInfeasible = false
            var anyLegUncertain = resolvedPhysics.isAssumedFallback

            for (legIdx in 0 until rawWaypoints.size - 1) {
                val fromWp = rawWaypoints[legIdx]
                val toWp = rawWaypoints[legIdx + 1]
                val routeRes = roadRouteProvider.calculateRoute(fromWp.coordinate, toWp.coordinate)
                if (routeRes == null || !routeRes.isRealRoadGraph ||
                    routeRes.routeCategory == RouteCategory.ESTIMATED_GEODESIC ||
                    routeRes.routeCategory == RouteCategory.SYNTHETIC_ESTIMATE ||
                    routeRes.distanceMeters <= 0.0
                ) {
                    return@withContext Result.failure(
                        RouteCoverageException(
                            "عدم پوشش شبکه معابر یا مسیر غیرواقعی بین «${fromWp.name}» و «${toWp.name}»"
                        )
                    )
                }

                val legPoly = routeRes.polyline.ifEmpty { listOf(fromWp.coordinate, toWp.coordinate) }
                val legSegments = routeRes.segments.ifEmpty {
                    listOf(
                        RouteSegmentInput(
                            distanceMeters = routeRes.distanceMeters,
                            startElevationMeters = null,
                            endElevationMeters = null
                        )
                    )
                }

                val energyPred = CanonicalEnergyEngine.predictRouteEnergy(
                    route = RouteProfileInput(segments = legSegments),
                    vehicle = resolvedPhysics,
                    conditions = EnvironmentalConditions(
                        departureSocPercent = runningSoc,
                        minReserveSocPercent = 10.0,
                        ambientTempC = ambientTemperatureCelsius ?: Double.NaN,
                        isTemperatureMeasured = ambientTemperatureCelsius != null,
                        isDepartureSocVerified = isDepartureSocVerified
                    ),
                    delegate = energyPredictionService
                )
                val legEnergyKwh = (energyPred.breakdown.netBatteryEnergyWh / 1000.0).coerceAtLeast(0.0)
                val arrivalSoc = (runningSoc - (legEnergyKwh / usableKwh) * 100.0).coerceIn(-20.0, 100.0)
                val isNumericallyFeasible = arrivalSoc >= (toWp.targetArrivalSocPercent ?: 5.0)

                var legCompat = ConnectorCompatibility.MATCH
                var legFeasibility = if (isNumericallyFeasible) FeasibilityStatus.FEASIBLE else FeasibilityStatus.INFEASIBLE
                var legChargeMin = 0.0
                var legUncertainChargeMin = 0.0
                var legAddedKwh = 0.0
                var postChargeSoc = arrivalSoc

                if (toWp.isChargingStop) {
                    val stDetails = toWp.stationDetails
                    val stConns = stDetails?.compatibleConnectors
                        ?.map { CanonicalConnectorType.parse(it) }
                        ?.filter { it != CanonicalConnectorType.UNKNOWN }
                        ?.toSet()
                        .orEmpty()

                    when {
                        resolvedPhysics.isAssumedFallback || stDetails == null || stConns.isEmpty() || vehicleCanonicalConns.isEmpty() -> {
                            legCompat = ConnectorCompatibility.UNKNOWN
                            legFeasibility = FeasibilityStatus.UNCERTAIN
                            anyLegUncertain = true
                            val targetDep = (toWp.targetDepartureSocPercent ?: 80.0).coerceIn(arrivalSoc, 95.0)
                            if (targetDep > arrivalSoc + 0.5 && stDetails != null && stDetails.maxPowerKw > 0.0) {
                                legUncertainChargeMin = chargingTimeEstimator.estimateChargingMinutes(
                                    arrivalSocPercent = arrivalSoc.coerceAtLeast(0.0),
                                    targetSocPercent = targetDep,
                                    batteryCapacityKwh = usableKwh,
                                    maxStationPowerKw = stDetails.maxPowerKw,
                                    maxVehiclePowerKw = resolvedPhysics.dcMaxChargePowerKw ?: resolvedPhysics.maxChargePowerKw,
                                    includeHandshakeOverhead = true,
                                    ambientTempC = ambientTemperatureCelsius,
                                    isDcFast = true
                                )
                                totalUncertainChargeMin += legUncertainChargeMin
                            }
                            warnings.add("مشخصات درگاه یا ایستگاه شارژ «${toWp.name}» کامل نیست؛ امکان‌سنجی در وضعیت UNCERTAIN قرار گرفت.")
                        }
                        stConns.intersect(vehicleCanonicalConns).isEmpty() -> {
                            legCompat = ConnectorCompatibility.MISMATCH
                            legFeasibility = FeasibilityStatus.INFEASIBLE
                            anyLegInfeasible = true
                            warnings.add("درگاه ایستگاه شارژ «${toWp.name}» با خودرو ناسازگار است.")
                        }
                        else -> {
                            legCompat = ConnectorCompatibility.MATCH
                            val matchedConns = stConns.intersect(vehicleCanonicalConns)
                            val isDc = matchedConns.any { it == CanonicalConnectorType.CCS2 || it == CanonicalConnectorType.GBT_DC || it == CanonicalConnectorType.CHADEMO }
                            val vehMaxKw = if (isDc) {
                                resolvedPhysics.dcMaxChargePowerKw ?: resolvedPhysics.maxChargePowerKw
                            } else {
                                resolvedPhysics.acMaxChargePowerKw ?: resolvedPhysics.maxChargePowerKw
                            }
                            val targetDep = (toWp.targetDepartureSocPercent ?: 80.0)
                            if (targetDep > arrivalSoc + 0.5 && stDetails.maxPowerKw > 0.0 && vehMaxKw > 0.0) {
                                postChargeSoc = targetDep.coerceIn(arrivalSoc, 98.0)
                                legAddedKwh = ((postChargeSoc - arrivalSoc) / 100.0) * usableKwh
                                legChargeMin = chargingTimeEstimator.estimateChargingMinutes(
                                    arrivalSocPercent = arrivalSoc.coerceAtLeast(0.0),
                                    targetSocPercent = postChargeSoc,
                                    batteryCapacityKwh = usableKwh,
                                    maxStationPowerKw = stDetails.maxPowerKw,
                                    maxVehiclePowerKw = vehMaxKw,
                                    includeHandshakeOverhead = true,
                                    ambientTempC = ambientTemperatureCelsius,
                                    isDcFast = isDc
                                )
                                totalChargeMin += legChargeMin
                            }
                        }
                    }
                }

                if (!isNumericallyFeasible) {
                    anyLegInfeasible = true
                    legFeasibility = FeasibilityStatus.INFEASIBLE
                }

                val legManeuvers = routeRes.maneuvers.ifEmpty {
                    buildLegManeuversFromPolyline(
                        legIndex = legIdx,
                        fromName = fromWp.name,
                        toName = toWp.name,
                        polyline = legPoly,
                        legDistanceMeters = routeRes.distanceMeters,
                        routeStartOffsetMeters = cumulativeRouteMeters,
                        speedLimitKmh = legSegments.firstOrNull()?.speedLimitKmh?.toInt()
                    )
                }

                builtLegs.add(
                    MultiStopJourneyLeg(
                        legIndex = legIdx,
                        fromWaypoint = fromWp,
                        toWaypoint = toWp,
                        distanceMeters = routeRes.distanceMeters,
                        durationSeconds = routeRes.durationSeconds,
                        elevationGainMeters = routeRes.elevationGainMeters,
                        elevationLossMeters = routeRes.elevationLossMeters,
                        estimatedEnergyKwh = legEnergyKwh,
                        departureSocPercent = runningSoc,
                        arrivalSocPercent = arrivalSoc.coerceAtLeast(0.0),
                        isFeasible = legFeasibility == FeasibilityStatus.FEASIBLE,
                        polyline = legPoly,
                        segments = legSegments,
                        maneuvers = legManeuvers,
                        chargingDurationMinutes = legChargeMin,
                        energyAddedKwh = legAddedKwh,
                        postChargeDepartureSocPercent = postChargeSoc.coerceAtLeast(0.0),
                        sourceClassification = routeRes.sourceClassification,
                        feasibilityStatus = legFeasibility,
                        isNumericallyFeasible = isNumericallyFeasible,
                        confidenceStatus = if (legFeasibility == FeasibilityStatus.FEASIBLE && !anyLegUncertain) "VERIFIED" else "UNCERTAIN",
                        connectorCompatibility = legCompat,
                        estimatedUncertainChargingMinutes = legUncertainChargeMin,
                        assumptions = assumptions.toList()
                    )
                )
                cumulativeRouteMeters += routeRes.distanceMeters
                runningSoc = postChargeSoc.coerceIn(0.0, 100.0)
            }

            val overallStatus = when {
                anyLegInfeasible -> FeasibilityStatus.INFEASIBLE
                anyLegUncertain -> FeasibilityStatus.UNCERTAIN
                else -> FeasibilityStatus.FEASIBLE
            }
            val planId = "plan_${vehicleId}_${rawWaypoints.first().id}_${rawWaypoints.last().id}"
            val plan = MultiStopJourneyPlan(
                planId = planId,
                vehicleId = vehicleId,
                waypoints = rawWaypoints,
                legs = builtLegs,
                totalDistanceMeters = builtLegs.sumOf { it.distanceMeters },
                totalDrivingDurationSeconds = builtLegs.sumOf { it.durationSeconds },
                totalChargingDurationMinutes = totalChargeMin,
                totalEnergyKwh = builtLegs.sumOf { it.estimatedEnergyKwh },
                initialDepartureSocPercent = initialSocPercent,
                finalArrivalSocPercent = builtLegs.lastOrNull()?.arrivalSocPercent ?: initialSocPercent,
                isOverallFeasible = overallStatus == FeasibilityStatus.FEASIBLE,
                warnings = warnings.distinct(),
                routeId = planId,
                feasibilityStatus = overallStatus,
                isNumericallyFeasible = !anyLegInfeasible,
                confidenceStatus = if (overallStatus == FeasibilityStatus.FEASIBLE) "VERIFIED" else "UNCERTAIN",
                totalUncertainChargingDurationMinutes = totalUncertainChargeMin,
                isDepartureSocVerified = isDepartureSocVerified,
                departureSocSource = departureSocSource,
                assumptions = assumptions.distinct()
            )
            Result.success(plan)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Optimizes intermediate waypoint order to minimize total geographic distance while keeping
     * origin at index 0, destination at last index, and pinned (`isRequired = true`) stops fixed
     * when [keepPinnedStopsFixed] is true.
     */
    fun optimizeStopOrder(
        waypoints: List<JourneyWaypoint>,
        keepPinnedStopsFixed: Boolean = true
    ): List<JourneyWaypoint> {
        if (waypoints.size <= 3) return waypoints
        val origin = waypoints.first()
        val dest = waypoints.last()
        val intermediates = waypoints.subList(1, waypoints.size - 1)

        if (!keepPinnedStopsFixed || intermediates.none { it.isRequired }) {
            val remaining = intermediates.toMutableList()
            val ordered = mutableListOf<JourneyWaypoint>()
            var currentCoord = origin.coordinate
            while (remaining.isNotEmpty()) {
                val next = remaining.minByOrNull { cand ->
                    val dCurr = MultiStopWaypointOptimizer.haversineMeters(currentCoord, cand.coordinate)
                    val dDest = MultiStopWaypointOptimizer.haversineMeters(cand.coordinate, dest.coordinate)
                    dCurr + dDest * 0.25
                }!!
                ordered.add(next)
                remaining.remove(next)
                currentCoord = next.coordinate
            }
            return buildList {
                add(origin)
                addAll(ordered)
                add(dest)
            }.mapIndexed { idx, wp -> wp.copy(orderIndex = idx) }
        }

        // Keep required stops at their relative pinned slots and optimize flexible stops
        val result = waypoints.toMutableList()
        val flexibleIndices = (1 until waypoints.size - 1).filter { !waypoints[it].isRequired }
        if (flexibleIndices.size <= 1) return waypoints
        val flexibleStops = flexibleIndices.map { waypoints[it] }.toMutableList()
        for (slotIdx in flexibleIndices) {
            val prevCoord = result[slotIdx - 1].coordinate
            val best = flexibleStops.minByOrNull { cand ->
                MultiStopWaypointOptimizer.haversineMeters(prevCoord, cand.coordinate)
            }!!
            result[slotIdx] = best
            flexibleStops.remove(best)
        }
        return result.mapIndexed { idx, wp -> wp.copy(orderIndex = idx) }
    }

    private fun buildLegManeuversFromPolyline(
        legIndex: Int,
        fromName: String,
        toName: String,
        polyline: List<GeoCoordinate>,
        legDistanceMeters: Double,
        routeStartOffsetMeters: Double,
        speedLimitKmh: Int?
    ): List<PlannedLegManeuver> {
        if (polyline.isEmpty()) return emptyList()
        if (polyline.size == 1) {
            return listOf(
                PlannedLegManeuver(
                    legIndex = legIndex,
                    maneuverIndexInLeg = 0,
                    coordinate = polyline.first(),
                    instructionFarsi = "حرکت از $fromName به سمت $toName",
                    maneuverType = "DEPART",
                    distanceMeters = legDistanceMeters,
                    cumulativeDistanceAlongLegMeters = 0.0,
                    cumulativeDistanceAlongRouteMeters = routeStartOffsetMeters,
                    roadName = toName,
                    speedLimitKmh = speedLimitKmh
                )
            )
        }
        return listOf(
            PlannedLegManeuver(
                legIndex = legIndex,
                maneuverIndexInLeg = 0,
                coordinate = polyline.first(),
                instructionFarsi = "حرکت از $fromName در مسیر $toName",
                maneuverType = "DEPART",
                distanceMeters = legDistanceMeters,
                cumulativeDistanceAlongLegMeters = 0.0,
                cumulativeDistanceAlongRouteMeters = routeStartOffsetMeters,
                roadName = toName,
                speedLimitKmh = speedLimitKmh
            ),
            PlannedLegManeuver(
                legIndex = legIndex,
                maneuverIndexInLeg = 1,
                coordinate = polyline.last(),
                instructionFarsi = "ورود به $toName",
                maneuverType = "ARRIVE",
                distanceMeters = 0.0,
                cumulativeDistanceAlongLegMeters = legDistanceMeters,
                cumulativeDistanceAlongRouteMeters = routeStartOffsetMeters + legDistanceMeters,
                roadName = toName,
                speedLimitKmh = speedLimitKmh
            )
        )
    }

    private suspend fun calculateLegWithEnergy(
        from: GeoCoordinate,
        to: GeoCoordinate,
        departureSoc: Double,
        minSafeSoc: Double,
        vehicle: VehiclePhysicsParams,
        ambientTempC: Double?,
        warnings: MutableList<String>
    ): LegRouteResult {
        val rawLeg = roadRouteProvider.calculateRoute(from, to)
        val distMeters: Double
        val poly: List<GeoCoordinate>
        val isRealGraph: Boolean
        val sourceClass: RouteSourceClassification
        val rawSegments: List<RouteSegmentInput>

        if (rawLeg != null && rawLeg.distanceMeters > 0.0) {
            distMeters = rawLeg.distanceMeters
            poly = rawLeg.polyline.ifEmpty { listOf(from, to) }
            isRealGraph = rawLeg.isRealRoadGraph
            sourceClass = rawLeg.sourceClassification
            rawSegments = rawLeg.segments
            if (!isRealGraph) {
                warnings.add("بخش مسیر (${"%.1f".format(distMeters / 1000.0)} کیلومتر) از شبکه محدود کریدور (Fallback) تولید شده است.")
            }
        } else {
            val haversineM = MultiStopWaypointOptimizer.haversineMeters(from, to).coerceAtLeast(100.0)
            distMeters = haversineM
            poly = listOf(from, to)
            isRealGraph = false
            sourceClass = RouteSourceClassification.STRAIGHT_LINE_ESTIMATE
            rawSegments = emptyList()
            warnings.add("مسیر شبکه جاده‌ای برای این قطعه در دسترس نبود؛ از فاصله خط مستقیم با افزایش عدم‌قطعیت استفاده شد.")
        }

        val segmentInputs = if (rawSegments.isNotEmpty()) {
            rawSegments
        } else if (poly.size >= 2) {
            val segDistances = poly.zipWithNext { a, b ->
                MultiStopWaypointOptimizer.haversineMeters(a, b).coerceAtLeast(1.0)
            }
            val rawPolyDist = segDistances.sum().coerceAtLeast(1.0)
            val scale = distMeters / rawPolyDist
            poly.zipWithNext().mapIndexed { idx, (a, b) ->
                RouteSegmentInput(
                    distanceMeters = (segDistances[idx] * scale).coerceAtLeast(1.0),
                    startElevationMeters = a.altitude,
                    endElevationMeters = b.altitude,
                    speedLimitKmh = null,
                    targetSpeedKmh = null
                )
            }
        } else {
            listOf(
                RouteSegmentInput(
                    distanceMeters = distMeters.coerceAtLeast(1.0),
                    startElevationMeters = from.altitude,
                    endElevationMeters = to.altitude,
                    speedLimitKmh = null,
                    targetSpeedKmh = null
                )
            )
        }

        val energyPrediction = CanonicalEnergyEngine.predictRouteEnergy(
            route = RouteProfileInput(
                segments = segmentInputs,
                isRouteAverageApproximation = !isRealGraph
            ),
            vehicle = vehicle,
            conditions = EnvironmentalConditions(
                departureSocPercent = departureSoc,
                minReserveSocPercent = minSafeSoc,
                targetSpeedKmh = Double.NaN,
                ambientTempC = ambientTempC ?: Double.NaN,
                isTemperatureMeasured = ambientTempC != null,
                isDepartureSocVerified = true
            ),
            delegate = energyPredictionService
        )

        val rawDriveSec = rawLeg?.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
            ?: energyPrediction.totalDurationSeconds.coerceAtLeast(1.0)
        val canonicalEta = CanonicalTravelTimeEngine.calculateFromRouteDurations(
            baseDrivingDurationSeconds = rawDriveSec,
            detourDurationSeconds = 0.0,
            chargingDurationSeconds = 0.0,
            waitingDurationSeconds = 0.0,
            totalDistanceMeters = distMeters,
            isRouteFromRealGraph = isRealGraph
        )
        val computedEnergyKwh = if (rawLeg != null && rawLeg.energyKwh > 0.0) {
            rawLeg.energyKwh
        } else {
            (energyPrediction.breakdown.netBatteryEnergyWh / 1000.0).coerceAtLeast(0.01)
        }

        val legManeuvers = rawLeg?.maneuvers?.takeIf { it.isNotEmpty() }
            ?: buildLegManeuversFromPolyline(
                legIndex = 0,
                fromName = "مبدأ",
                toName = "مقصد",
                polyline = poly,
                legDistanceMeters = distMeters,
                routeStartOffsetMeters = 0.0,
                speedLimitKmh = segmentInputs.firstOrNull()?.speedLimitKmh?.toInt()
            )

        return LegRouteResult(
            distanceMeters = distMeters,
            durationSeconds = canonicalEta.baseDrivingDuration.seconds,
            energyKwh = computedEnergyKwh,
            polyline = poly,
            isRealRoadGraph = isRealGraph,
            routeCategory = if (isRealGraph) RouteCategory.AUTHENTIC_ROAD_GRAPH else RouteCategory.CURATED_CORRIDOR_FALLBACK,
            sourceClassification = sourceClass,
            segments = segmentInputs,
            maneuvers = legManeuvers
        )
    }
}
