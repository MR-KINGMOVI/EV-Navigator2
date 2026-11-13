package com.evnavigator.iran.ui.screen.journey

import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.model.ManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.trip.BatteryAndSocState
import com.evnavigator.iran.domain.intelligence.trip.CanonicalFinalRouteAssembler
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState
import com.evnavigator.iran.domain.journey.multistop.JourneyPlan
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.ActiveJourneyDomainModel
import com.evnavigator.iran.domain.model.catalog.ActiveVehicleDomainStateHolder
import com.evnavigator.iran.domain.model.catalog.ChargingStationWithPortsDomainModel
import com.evnavigator.iran.domain.model.catalog.ConnectorNormalizer
import com.evnavigator.iran.domain.model.catalog.DomainVehicleSocSource
import com.evnavigator.iran.domain.model.catalog.VehicleDomainModel
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import com.evnavigator.iran.domain.model.navigation.SocDataSource
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import kotlin.math.min

/**
 * Coordinates physics-based route planning, intermediate charging stop projection,
 * reroute execution, drive simulation steps, and persistence snapshot creation for [ActiveJourneyViewModel].
 *
 * V13.3.1 Invariants:
 * - Normalizes vehicle connectors via [ConnectorNormalizer.normalizeConnectorStrings].
 * - Routes all energy predictions through [CanonicalEnergyEngine.predictRouteEnergy].
 * - Builds and attaches a canonical [com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute]
 *   via [CanonicalFinalRouteAssembler.assembleFromJourneyPlan] so `ActiveJourneyViewModel`,
 *   `AutomotiveGpsEngine`, and `JourneyAutomotivePublisher` share the exact same multi-leg route,
 *   polyline, maneuvers, and versioned `routeId`.
 */
object JourneyRouteAndSimulationCoordinator {

    data class ComputedJourneyPlanBundle(
        val realRouteResult: RealRoadRouteResult,
        val planResult: Result<JourneyPlan>,
        val isHypothetical: Boolean,
        val directBearing: Float,
        val waypointsCount: Int
    )

    fun mapSocSource(source: DomainVehicleSocSource): SocDataSource = when (source) {
        DomainVehicleSocSource.OBD_II_LIVE,
        DomainVehicleSocSource.LIVE_VEHICLE_TELEMETRY -> SocDataSource.OBD_II_LIVE
        DomainVehicleSocSource.USER_MANUAL,
        DomainVehicleSocSource.USER_MANUAL_INPUT -> SocDataSource.USER_MANUAL
        DomainVehicleSocSource.VEHICLE_PROFILE,
        DomainVehicleSocSource.ENERGY_MODEL_ESTIMATE,
        DomainVehicleSocSource.ESTIMATED_DURING_NAVIGATION -> SocDataSource.VEHICLE_PROFILE
        DomainVehicleSocSource.DEFAULT_FALLBACK,
        DomainVehicleSocSource.UNCONFIRMED_DEFAULT -> SocDataSource.DEFAULT_FALLBACK
    }

    fun applyActiveVehicleToState(
        currentState: ActiveJourneyUiState,
        active: VehicleDomainModel?,
        socSource: DomainVehicleSocSource
    ): Pair<ActiveJourneyUiState, Boolean> {
        val prevVehicleId = currentState.vehicleId
        return if (active != null) {
            val capacityKwh = active.usableBatteryCapacityWh / 1000.0
            val baselineWhPer100Km = active.baselineConsumptionWhPer100Km
            val baselineConsumption = if (baselineWhPer100Km > 0.0) baselineWhPer100Km / 100.0 else 0.0
            val maxChargeKw = active.dcChargingMaxPowerKw ?: 0.0
            val connectors = ConnectorNormalizer.normalizeConnectorStrings(active.connectorTypes)
            val hasCompleteSpecs = capacityKwh > 0.0 && baselineConsumption > 0.0 && maxChargeKw > 0.0 && connectors.isNotEmpty()
            val updated = currentState.copy(
                vehicleModelName = "${active.manufacturer} ${active.model}",
                vehicleBatteryKwh = capacityKwh,
                vehicleId = active.id,
                vehicleMaxChargeKw = maxChargeKw,
                vehicleSupportedConnectors = connectors,
                isVehicleSelected = hasCompleteSpecs,
                averageConsumptionWhPerKm = baselineConsumption,
                statusMessage = if (hasCompleteSpecs) "آماده حرکت با ${active.manufacturer} ${active.model}" else "مشخصات فنی خودرو (${active.manufacturer} ${active.model}) ناقص است"
            )
            AutomotiveNavigationStateHolder.updateVehicleTelemetry(
                socPercent = currentState.currentSocPercent,
                batteryCapacityKwh = capacityKwh,
                consumptionWhPerKm = baselineConsumption,
                socSource = mapSocSource(socSource)
            )
            val shouldReroute = prevVehicleId.isNotEmpty() && prevVehicleId != active.id && currentState.plan != null
            updated to shouldReroute
        } else {
            currentState.copy(
                vehicleModelName = "خودرو انتخاب نشده",
                vehicleBatteryKwh = 0.0,
                vehicleId = "",
                vehicleMaxChargeKw = 0.0,
                vehicleSupportedConnectors = emptyList(),
                averageConsumptionWhPerKm = 0.0,
                isVehicleSelected = false
            ) to false
        }
    }

    fun buildRoutePreviewSuccessState(
        currentState: ActiveJourneyUiState,
        bundle: ComputedJourneyPlanBundle,
        plan: JourneyPlan,
        destinationTarget: DestinationSearchResult,
        isDemo: Boolean,
        genId: Long
    ): ActiveJourneyUiState {
        val realRouteResult = bundle.realRouteResult
        val finalPlanned = plan.finalPlannedRoute
        val maneuvers = resolveUnifiedManeuvers(realRouteResult, finalPlanned)
        val effectivePolyline = finalPlanned?.polyline?.takeIf { it.size >= 2 } ?: realRouteResult.polyline
        val firstManeuver = maneuvers.firstOrNull()
        val firstSeg = realRouteResult.segments.firstOrNull()
        val hypotheticalNotice = if (bundle.isHypothetical) " (محاسبه فرضی بدون خودروی منتخب)" else ""
        return currentState.copy(
            navigationMode = NavigationScreenMode.ROUTE_PREVIEW,
            plan = plan,
            finalPlannedRoute = finalPlanned,
            isDemoPlan = isDemo,
            isHypotheticalPlan = bundle.isHypothetical,
            roadPolyline = effectivePolyline,
            routeSegments = realRouteResult.segments,
            maneuvers = maneuvers,
            selectedDestination = destinationTarget,
            directDistanceMeters = finalPlanned?.totalDistanceMeters ?: realRouteResult.totalDistanceMeters,
            directBearingDegrees = bundle.directBearing,
            predictedArrivalSocPercent = plan.finalArrivalSocPercent,
            routeCalculationError = null,
            activeWarning = if (bundle.isHypothetical) "اطلاعات خودرو ناقص است؛ محاسبه بر اساس خودروی فرضی مرجع انجام شد." else plan.warnings.firstOrNull(),
            statusMessage = "مسیر آماده است • ${bundle.waypointsCount} نقطه توقف$hypotheticalNotice",
            currentManeuver = firstManeuver?.instructionFarsi ?: "",
            currentManeuverIcon = firstManeuver?.maneuverType?.name ?: "",
            nextManeuverDistanceMeters = firstManeuver?.distanceMeters ?: firstSeg?.lengthMeters ?: 0.0,
            nextRoadName = firstManeuver?.roadName ?: firstSeg?.roadName ?: "",
            speedLimitKmh = firstManeuver?.speedLimitKmh ?: firstSeg?.speedLimitKmh ?: 0,
            routeGenerationId = genId,
            activeRouteId = finalPlanned?.routeId ?: realRouteResult.routeId
        )
    }

    fun buildRerouteSuccessState(
        currentState: ActiveJourneyUiState,
        bundle: ComputedJourneyPlanBundle,
        newPlan: JourneyPlan,
        genId: Long,
        completedEpochMs: Long
    ): ActiveJourneyUiState {
        val realRouteResult = bundle.realRouteResult
        val finalPlanned = newPlan.finalPlannedRoute
        val newManeuvers = resolveUnifiedManeuvers(realRouteResult, finalPlanned)
        val effectivePolyline = finalPlanned?.polyline?.takeIf { it.size >= 2 } ?: realRouteResult.polyline
        val firstManeuver = newManeuvers.firstOrNull()
        val firstSeg = realRouteResult.segments.firstOrNull()
        return currentState.copy(
            plan = newPlan,
            finalPlannedRoute = finalPlanned,
            isHypotheticalPlan = bundle.isHypothetical,
            roadPolyline = effectivePolyline,
            routeSegments = realRouteResult.segments,
            maneuvers = newManeuvers,
            predictedArrivalSocPercent = newPlan.finalArrivalSocPercent,
            statusMessage = "مسیر بر اساس موقعیت فعلی به‌روزرسانی شد",
            currentManeuver = firstManeuver?.instructionFarsi ?: "",
            currentManeuverIcon = firstManeuver?.maneuverType?.name ?: "",
            nextManeuverDistanceMeters = firstManeuver?.distanceMeters ?: firstSeg?.lengthMeters ?: 0.0,
            nextRoadName = firstManeuver?.roadName ?: firstSeg?.roadName ?: "",
            speedLimitKmh = firstManeuver?.speedLimitKmh ?: firstSeg?.speedLimitKmh ?: 0,
            routeGenerationId = genId,
            activeRouteId = finalPlanned?.routeId ?: realRouteResult.routeId,
            rerouteState = RerouteState.IDLE,
            lastRerouteTimestampMs = completedEpochMs
        )
    }

    private fun resolveUnifiedManeuvers(
        realRouteResult: RealRoadRouteResult,
        finalPlanned: com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute?
    ): List<RealRoadManeuver> {
        if (realRouteResult.maneuvers.isNotEmpty() && (finalPlanned == null || finalPlanned.legs.size <= 1)) {
            return realRouteResult.maneuvers
        }
        val plannedManeuvers = finalPlanned?.maneuvers.orEmpty()
        if (plannedManeuvers.isEmpty()) return realRouteResult.maneuvers
        return plannedManeuvers.map { pm ->
            val mType = runCatching { ManeuverType.valueOf(pm.maneuverType) }.getOrDefault(ManeuverType.CONTINUE)
            RealRoadManeuver(
                stepIndex = pm.stepIndex,
                instructionFarsi = pm.instructionFarsi,
                roadName = pm.roadName,
                distanceMeters = pm.distanceMeters,
                durationSeconds = pm.durationSeconds,
                coordinate = pm.coordinate,
                maneuverType = mType,
                bearingAfter = pm.bearingAfter,
                speedLimitKmh = pm.speedLimitKmh
            )
        }
    }

    fun buildVehiclePhysicsParams(
        activeVeh: VehicleDomainModel?,
        uiState: ActiveJourneyUiState
    ): VehiclePhysicsParams {
        val verifiedBatteryKwh = activeVeh?.let { it.usableBatteryCapacityWh / 1000.0 }?.takeIf { it > 0.0 }
            ?: uiState.vehicleBatteryKwh.takeIf { it > 0.0 }
        val resolvedVehicleId = activeVeh?.id
            ?: uiState.vehicleId.ifEmpty { ActiveVehicleDomainStateHolder.CANONICAL_DEFAULT_VEHICLE_ID }
        val supportedConnectors = activeVeh?.let { ConnectorNormalizer.normalizeConnectorStrings(it.connectorTypes) }
            ?: uiState.vehicleSupportedConnectors

        return if (activeVeh != null && verifiedBatteryKwh != null) {
            val hasFullPhysics = activeVeh.curbMassKg > 400.0 &&
                activeVeh.dragCoefficient != null &&
                activeVeh.frontalAreaM2 != null &&
                activeVeh.rollingResistanceCoefficient != null &&
                activeVeh.isVerified
            VehiclePhysicsParams(
                vehicleId = activeVeh.id,
                usableBatteryKwh = verifiedBatteryKwh,
                nominalGrossBatteryKwh = activeVeh.nominalBatteryKwh.takeIf { it > 0.0 },
                totalMassKg = activeVeh.curbMassKg + 150.0,
                dragCoefficient = activeVeh.dragCoefficient ?: 0.28,
                frontalAreaM2 = activeVeh.frontalAreaM2 ?: 2.38,
                rollingResistanceCoeff = activeVeh.rollingResistanceCoefficient ?: 0.010,
                maxChargePowerKw = activeVeh.dcChargingMaxPowerKw ?: uiState.vehicleMaxChargeKw,
                dcMaxChargePowerKw = activeVeh.dcMaxChargePowerKw,
                acMaxChargePowerKw = activeVeh.acMaxChargePowerKw,
                supportedConnectors = supportedConnectors,
                isAssumedFallback = !hasFullPhysics,
                dataQuality = if (hasFullPhysics) "VERIFIED" else "ESTIMATED"
            )
        } else if (verifiedBatteryKwh != null) {
            VehiclePhysicsParams.assumedReference(
                vehicleId = resolvedVehicleId,
                usableBatteryKwh = verifiedBatteryKwh
            )
        } else {
            VehiclePhysicsParams.assumedReference(
                vehicleId = resolvedVehicleId
            )
        }
    }

    suspend fun computeFullRoutePlan(
        originCoord: GeoCoordinate,
        originName: String,
        destCoord: GeoCoordinate,
        destName: String,
        activeVeh: VehicleDomainModel?,
        uiState: ActiveJourneyUiState,
        chargingStations: List<ChargingStationWithPortsDomainModel>,
        isSocVerified: Boolean,
        socSourceName: String,
        realRoadRoutingEngine: IranRealRoadRoutingEngine,
        routeCoordinator: MultiStopRouteCoordinator
    ): ComputedJourneyPlanBundle {
        val initialSoc = uiState.currentSocPercent
        val physicsParams = buildVehiclePhysicsParams(activeVeh, uiState)
        val isHypothetical = physicsParams.isAssumedFallback
        val realRouteResult = realRoadRoutingEngine.calculateRealRoute(originCoord, destCoord)
        val roadPolyline = realRouteResult.polyline
        val roadDistMeters = realRouteResult.totalDistanceMeters
        val directBearing = DestinationSearchCoordinator.calculateBearingDegrees(originCoord, destCoord)

        val waypoints = mutableListOf<JourneyWaypoint>()
        waypoints.add(JourneyWaypoint("wp_orig", originName, originCoord, WaypointType.ORIGIN))

        val segmentInputs = if (realRouteResult.segments.isNotEmpty()) {
            realRouteResult.segments.map { seg ->
                RouteSegmentInput(
                    distanceMeters = seg.lengthMeters,
                    startElevationMeters = seg.startElevationMeters,
                    endElevationMeters = seg.endElevationMeters,
                    speedLimitKmh = seg.speedLimitKmh.takeIf { it > 0 }?.toDouble(),
                    targetSpeedKmh = seg.speedLimitKmh.takeIf { it > 0 }?.toDouble()
                )
            }
        } else {
            listOf(RouteSegmentInput(roadDistMeters, null, null, null))
        }

        val directPrediction = CanonicalEnergyEngine.predictRouteEnergy(
            route = RouteProfileInput(
                segments = segmentInputs,
                routeHeadingDegrees = directBearing.toDouble()
            ),
            vehicle = physicsParams,
            conditions = EnvironmentalConditions(
                departureSocPercent = initialSoc,
                minReserveSocPercent = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                isDepartureSocVerified = isSocVerified
            )
        )

        if (directPrediction.predictedArrivalSocPercent < EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT && roadPolyline.size >= 2) {
            val stopWaypoint = DestinationSearchCoordinator.projectBestChargingStopAlongPolyline(
                roadPolyline = roadPolyline,
                roadDistMeters = roadDistMeters,
                chargingStations = chargingStations,
                physicsParams = physicsParams,
                totalEnergyUsedKwh = directPrediction.totalEnergyUsedKwh
            )
            if (stopWaypoint != null) {
                waypoints.add(stopWaypoint)
            }
        }

        waypoints.add(JourneyWaypoint("wp_dest", destName, destCoord, WaypointType.DESTINATION))

        val rawPlanResult = routeCoordinator.createPlan(
            vehicleId = physicsParams.vehicleId,
            batteryCapacityKwh = physicsParams.usableBatteryKwh,
            initialSocPercent = initialSoc,
            rawWaypoints = waypoints,
            vehiclePhysics = physicsParams,
            isDepartureSocVerified = isSocVerified,
            departureSocSource = socSourceName
        )

        val sourceClass = classifyRouteSource(realRouteResult)
        val enrichedPlanResult = rawPlanResult.map { plan ->
            val batteryAndSoc = BatteryAndSocState(
                grossCapacityKwh = physicsParams.nominalGrossBatteryKwh ?: physicsParams.usableBatteryKwh,
                usableCapacityKwh = physicsParams.usableBatteryKwh,
                currentSocPercent = initialSoc,
                reserveSocPercent = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                isSocAuthoritative = isSocVerified
            )
            val finalRoute = CanonicalFinalRouteAssembler.assembleFromJourneyPlan(
                baseRouteId = realRouteResult.routeId,
                routeVersion = uiState.routeGenerationId + 1L,
                plan = plan,
                batteryAndSoc = batteryAndSoc,
                sourceClassification = sourceClass
            )
            plan.copy(finalPlannedRoute = finalRoute)
        }

        return ComputedJourneyPlanBundle(
            realRouteResult = realRouteResult,
            planResult = enrichedPlanResult,
            isHypothetical = isHypothetical,
            directBearing = directBearing,
            waypointsCount = waypoints.size
        )
    }

    suspend fun computeReroutePlan(
        currentPos: GeoCoordinate,
        currentPlan: JourneyPlan,
        activeVeh: VehicleDomainModel?,
        uiState: ActiveJourneyUiState,
        isSocVerified: Boolean,
        socSourceName: String,
        realRoadRoutingEngine: IranRealRoadRoutingEngine,
        routeCoordinator: MultiStopRouteCoordinator
    ): ComputedJourneyPlanBundle {
        val currentWaypoints = currentPlan.waypoints.toMutableList()
        if (currentWaypoints.isNotEmpty()) {
            currentWaypoints[0] = currentWaypoints[0].copy(coordinate = currentPos)
        }
        val destCoord = currentWaypoints.lastOrNull()?.coordinate ?: currentPos
        val physicsParams = buildVehiclePhysicsParams(activeVeh, uiState)
        val isHypothetical = physicsParams.isAssumedFallback
        val realRouteResult = realRoadRoutingEngine.calculateRealRoute(currentPos, destCoord)
        val directBearing = DestinationSearchCoordinator.calculateBearingDegrees(currentPos, destCoord)

        val rawReplanResult = routeCoordinator.createPlan(
            vehicleId = physicsParams.vehicleId,
            batteryCapacityKwh = physicsParams.usableBatteryKwh,
            initialSocPercent = uiState.currentSocPercent,
            rawWaypoints = currentWaypoints,
            vehiclePhysics = physicsParams,
            isDepartureSocVerified = isSocVerified,
            departureSocSource = socSourceName
        )

        val sourceClass = classifyRouteSource(realRouteResult)
        val enrichedReplanResult = rawReplanResult.map { newPlan ->
            val batteryAndSoc = BatteryAndSocState(
                grossCapacityKwh = physicsParams.nominalGrossBatteryKwh ?: physicsParams.usableBatteryKwh,
                usableCapacityKwh = physicsParams.usableBatteryKwh,
                currentSocPercent = uiState.currentSocPercent,
                reserveSocPercent = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                isSocAuthoritative = isSocVerified
            )
            val finalRoute = CanonicalFinalRouteAssembler.assembleFromJourneyPlan(
                baseRouteId = realRouteResult.routeId,
                routeVersion = uiState.routeGenerationId + 1L,
                plan = newPlan,
                batteryAndSoc = batteryAndSoc,
                sourceClassification = sourceClass
            )
            newPlan.copy(finalPlannedRoute = finalRoute)
        }

        return ComputedJourneyPlanBundle(
            realRouteResult = realRouteResult,
            planResult = enrichedReplanResult,
            isHypothetical = isHypothetical,
            directBearing = directBearing,
            waypointsCount = currentWaypoints.size
        )
    }

    private fun classifyRouteSource(realRoute: RealRoadRouteResult): RouteSourceClassification = when {
        realRoute.isOnlineRouting -> RouteSourceClassification.ONLINE_ROUTING_PROVIDER
        OsmDatasetManager.isUsingImportedOsmDataset() -> RouteSourceClassification.FULL_OFFLINE_OSM
        else -> RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK
    }

    fun stepSimulationFrame(
        polyline: List<GeoCoordinate>,
        currentIndex: Int,
        remainingSoc: Double,
        uiState: ActiveJourneyUiState,
        physicsParams: VehiclePhysicsParams
    ): Pair<ActiveJourneyUiState, Double> {
        val currentCoord = polyline[currentIndex]
        val nextIndex = min(polyline.size - 1, currentIndex + 2)
        val nextCoord = polyline[nextIndex]
        val bearing = DestinationSearchCoordinator.calculateBearingDegrees(currentCoord, nextCoord)

        val upcomingManeuver = uiState.maneuvers.find { m ->
            DestinationSearchCoordinator.calculateHaversineDistanceMeters(currentCoord, m.coordinate) > 40.0
        } ?: uiState.maneuvers.lastOrNull()

        val distToManeuver = if (upcomingManeuver != null) {
            DestinationSearchCoordinator.calculateHaversineDistanceMeters(currentCoord, upcomingManeuver.coordinate)
        } else {
            100.0
        }

        val targetSpeedKmh = upcomingManeuver?.speedLimitKmh?.takeIf { it > 0 }?.toDouble()
        var stepDistMeters = 0.0
        for (i in currentIndex until nextIndex) {
            stepDistMeters += DestinationSearchCoordinator.calculateHaversineDistanceMeters(polyline[i], polyline[i + 1])
        }
        var updatedSoc = remainingSoc
        if (stepDistMeters > 0.5) {
            val stepPrediction = CanonicalEnergyEngine.predictRouteEnergy(
                route = RouteProfileInput(
                    segments = listOf(
                        RouteSegmentInput(
                            distanceMeters = stepDistMeters,
                            startElevationMeters = null,
                            endElevationMeters = null,
                            targetSpeedKmh = targetSpeedKmh
                        )
                    ),
                    routeHeadingDegrees = bearing.toDouble()
                ),
                vehicle = physicsParams,
                conditions = EnvironmentalConditions(departureSocPercent = remainingSoc)
            )
            updatedSoc = stepPrediction.predictedArrivalSocPercent.coerceAtLeast(2.0)
        }

        val nextUiState = uiState.copy(
            simulatedCarCoordinate = currentCoord,
            simulatedCarBearing = bearing,
            simulatedSpeedKmh = targetSpeedKmh ?: 80.0,
            currentSocPercent = updatedSoc,
            currentManeuver = upcomingManeuver?.instructionFarsi ?: "در مسیر اصلی مستقیم برانید",
            currentManeuverIcon = upcomingManeuver?.maneuverType?.name ?: "STRAIGHT",
            nextManeuverDistanceMeters = distToManeuver,
            nextRoadName = upcomingManeuver?.roadName ?: "آزادراه",
            speedLimitKmh = upcomingManeuver?.speedLimitKmh ?: 0,
            activeLaneConfig = upcomingManeuver?.laneGuidance
        )
        return nextUiState to updatedSoc
    }

    fun buildPersistenceSnapshot(
        state: ActiveJourneyUiState,
        currentCoord: GeoCoordinate?
    ): ActiveJourneyDomainModel? {
        if (state.isDemoPlan) return null
        val plan = state.plan ?: return null
        val origin = plan.waypoints.firstOrNull()?.name ?: "مبدأ"
        val destination = plan.waypoints.lastOrNull()?.name ?: "مقصد"
        val serializedWaypoints = plan.waypoints.joinToString(prefix = "[", postfix = "]") { wp ->
            val safeName = wp.name.replace("\"", "\\\"")
            """{"id":"${wp.id}","name":"$safeName","lat":${wp.coordinate.latitude},"lon":${wp.coordinate.longitude},"type":"${wp.type.name}"}"""
        }

        return ActiveJourneyDomainModel(
            journeyId = "current_active_journey",
            lifecycleState = state.lifecycleState.name,
            vehicleId = state.vehicleId,
            originName = origin,
            destinationName = destination,
            waypointsJson = serializedWaypoints,
            currentSocPercent = state.currentSocPercent,
            batteryCapacityKwh = state.vehicleBatteryKwh,
            totalDistanceMeters = plan.totalDistanceMeters,
            remainingDistanceMeters = state.progress?.remainingDistanceToDestinationMeters ?: plan.totalDistanceMeters,
            lastLatitude = currentCoord?.latitude ?: plan.waypoints.firstOrNull()?.coordinate?.latitude ?: 0.0,
            lastLongitude = currentCoord?.longitude ?: plan.waypoints.firstOrNull()?.coordinate?.longitude ?: 0.0
        )
    }

    fun getStatusDescription(state: JourneyLifecycleState): String = when (state) {
        JourneyLifecycleState.DRAFT -> "پیش‌نویس سفر"
        JourneyLifecycleState.PLANNING -> "در حال برنامه‌ریزی"
        JourneyLifecycleState.READY_TO_START -> "آماده حرکت"
        JourneyLifecycleState.ACTIVE -> "در حال ناوبری زنده"
        JourneyLifecycleState.APPROACHING_WAYPOINT -> "نزدیک‌شدن به نقطه میانی"
        JourneyLifecycleState.APPROACHING_CHARGING_STOP -> "نزدیک‌شدن به ایستگاه شارژ"
        JourneyLifecycleState.CHARGING_STOP_REACHED -> "ورود به محدوده ایستگاه شارژ"
        JourneyLifecycleState.CHARGING_IN_PROGRESS -> "در حال شارژ باتری خودرو"
        JourneyLifecycleState.READY_TO_DEPART -> "شارژ تکمیل شد • آماده حرکت"
        JourneyLifecycleState.TEMPORARILY_PAUSED -> "سفر متوقف شده است"
        JourneyLifecycleState.REPLANNING -> "در حال بازنگری مسیر"
        JourneyLifecycleState.COMPLETED -> "سفر با موفقیت به پایان رسید"
        JourneyLifecycleState.CANCELLED -> "سفر لغو شد"
        JourneyLifecycleState.INTERRUPTED_RECOVERY -> "بازیابی سفر پس از قطعی"
    }
}
