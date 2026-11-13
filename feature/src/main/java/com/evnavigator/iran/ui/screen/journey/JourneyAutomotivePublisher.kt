package com.evnavigator.iran.ui.screen.journey

import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState
import com.evnavigator.iran.domain.journey.multistop.ChargingTimeEstimator
import com.evnavigator.iran.domain.journey.multistop.ConnectorCompatibility
import com.evnavigator.iran.domain.journey.multistop.FeasibilityStatus
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.journey.network.JourneyConnectivityMode
import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Charger
import com.evnavigator.iran.domain.model.ChargerStatus
import com.evnavigator.iran.domain.model.ChargingConnector
import com.evnavigator.iran.domain.model.ConnectorType
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration
import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.Soc
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import com.evnavigator.iran.domain.model.navigation.ChargingStop
import com.evnavigator.iran.domain.model.navigation.EtaEstimate
import com.evnavigator.iran.domain.model.navigation.GpsQuality
import com.evnavigator.iran.domain.model.navigation.ManeuverType
import com.evnavigator.iran.domain.model.navigation.NavigationDataQuality
import com.evnavigator.iran.domain.model.navigation.NavigationProgress
import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationState
import com.evnavigator.iran.domain.model.navigation.NavigationStatus
import com.evnavigator.iran.domain.model.navigation.OffRouteState
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.TrafficStatus
import com.evnavigator.iran.domain.model.navigation.UpcomingManeuver
import com.evnavigator.iran.domain.model.navigation.WeatherStatus
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * V11 Extracted Collaborator: Builds and publishes the authoritative [NavigationState]
 * from [ActiveJourneyUiState] and [GpsLocationState] to [AutomotiveNavigationStateHolder].
 */
object JourneyAutomotivePublisher {

    fun publish(
        state: ActiveJourneyUiState,
        currentGps: GpsLocationState,
        maneuverCoordinator: JourneyManeuverProgressionCoordinator
    ) {
        val plan = state.plan
        val navStatus = when (state.navigationMode) {
            NavigationScreenMode.ACTIVE_NAVIGATION -> NavigationStatus.NAVIGATING
            NavigationScreenMode.ROUTE_PREVIEW -> NavigationStatus.STARTING
            NavigationScreenMode.NAVIGATION_PAUSED -> NavigationStatus.PAUSED
            NavigationScreenMode.GPS_WEAK_OR_DISABLED -> NavigationStatus.GPS_DEGRADED
            NavigationScreenMode.OFFLINE_MODE -> NavigationStatus.OFFLINE
            NavigationScreenMode.ROUTE_CALCULATION_ERROR, NavigationScreenMode.MISSING_ROUTE_DATA -> NavigationStatus.ERROR
            else -> NavigationStatus.IDLE
        }

        val matchedLoc: Location? = when (currentGps) {
            is GpsLocationState.FixAcquired -> Location(
                latitude = currentGps.location.latitude,
                longitude = currentGps.location.longitude,
                altitudeM = currentGps.location.altitudeM,
                accuracyMeters = if (currentGps.isAccuracyKnown && !currentGps.accuracyMeters.isNaN()) currentGps.accuracyMeters else null,
                timestampEpochMs = currentGps.location.timestampEpochMs
            )
            is GpsLocationState.MapMatchedFix -> Location(
                latitude = currentGps.snappedLocation.latitude,
                longitude = currentGps.snappedLocation.longitude,
                altitudeM = currentGps.snappedLocation.altitudeM,
                accuracyMeters = currentGps.snappedLocation.accuracyMeters,
                timestampEpochMs = currentGps.snappedLocation.timestampEpochMs
            )
            is GpsLocationState.DeadReckoning -> Location(
                latitude = currentGps.estimatedLocation.latitude,
                longitude = currentGps.estimatedLocation.longitude,
                altitudeM = currentGps.estimatedLocation.altitudeM,
                accuracyMeters = currentGps.estimatedLocation.accuracyMeters ?: currentGps.uncertaintyMeters,
                timestampEpochMs = currentGps.estimatedLocation.timestampEpochMs
            )
            else -> state.simulatedCarCoordinate?.let {
                Location(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = null,
                    timestampEpochMs = System.currentTimeMillis()
                )
            }
        }

        val gpsQual = when (currentGps) {
            is GpsLocationState.FixAcquired -> if (!currentGps.isAccuracyKnown || currentGps.accuracyMeters.isNaN()) {
                GpsQuality.DEGRADED
            } else if (currentGps.accuracyMeters <= 20f) {
                GpsQuality.GOOD
            } else {
                GpsQuality.DEGRADED
            }
            is GpsLocationState.MapMatchedFix -> if (currentGps.snappedLocation.accuracyMeters == null) {
                GpsQuality.DEGRADED
            } else {
                GpsQuality.GOOD
            }
            is GpsLocationState.DeadReckoning -> GpsQuality.POOR
            is GpsLocationState.GpsDisabled, is GpsLocationState.PermissionDenied -> GpsQuality.UNAVAILABLE
            else -> if (state.isSimulatingDrive) GpsQuality.DEGRADED else GpsQuality.UNKNOWN
        }

        val dataQuality = when {
            navStatus == NavigationStatus.ERROR -> NavigationDataQuality.UNAVAILABLE
            state.connectivityMode == JourneyConnectivityMode.OFFLINE_DEGRADED || !state.isInternetConnected -> NavigationDataQuality.CACHED
            matchedLoc != null && plan != null -> NavigationDataQuality.CACHED
            else -> NavigationDataQuality.PARTIAL
        }

        val totalDistMeters = plan?.totalDistanceMeters ?: state.directDistanceMeters ?: 0.0
        val remainingMeters = state.progress?.remainingDistanceToDestinationMeters
            ?: plan?.totalDistanceMeters
            ?: state.directDistanceMeters
        val remainingSec = state.progress?.remainingDurationToDestinationSeconds?.roundToLong()
            ?: plan?.totalDrivingDurationSeconds

        val traveledMeters = (totalDistMeters - (remainingMeters ?: totalDistMeters)).coerceIn(0.0, totalDistMeters.coerceAtLeast(0.0))
        val routePct = if (totalDistMeters > 0.0) (traveledMeters / totalDistMeters).coerceIn(0.0, 1.0) else 0.0

        val activeManeuverIdx = maneuverCoordinator.resolveMonotonicManeuverIndex(
            maneuvers = state.maneuvers,
            traveledMeters = traveledMeters,
            currentGenId = state.routeGenerationId,
            currentRouteId = state.activeRouteId
        )
        val currentRealManeuver = if (activeManeuverIdx >= 0) state.maneuvers[activeManeuverIdx] else null
        val nextRealManeuver = if (activeManeuverIdx >= 0) state.maneuvers.getOrNull(activeManeuverIdx + 1) else null

        val distToCurrentManeuver = if (currentRealManeuver != null) {
            (currentRealManeuver.cumulativeDistanceMeters - traveledMeters).coerceAtLeast(0.0)
        } else {
            state.nextManeuverDistanceMeters.coerceAtLeast(0.0)
        }
        val maneuverDist = Distance.fromMeters(distToCurrentManeuver)

        val effectiveSpeedMps = ((state.speedLimitKmh.takeIf { it > 0 } ?: currentRealManeuver?.speedLimitKmh?.takeIf { it > 0 } ?: 80) / 3.6).coerceAtLeast(5.0)
        val firstTimeSec = (distToCurrentManeuver / effectiveSpeedMps).roundToLong().coerceAtLeast(1L)

        val upcoming = if (currentRealManeuver != null || state.nextRoadName.isNotBlank()) {
            UpcomingManeuver(
                type = mapIconNameToDomainManeuver(currentRealManeuver?.maneuverType?.name ?: state.currentManeuverIcon),
                roadName = currentRealManeuver?.roadName?.ifBlank { state.nextRoadName } ?: state.nextRoadName.ifBlank { null },
                exitNumber = null,
                distanceToManeuver = maneuverDist,
                timeToManeuver = Duration.fromSeconds(firstTimeSec),
                direction = null
            )
        } else null

        val nextUpcoming = nextRealManeuver?.let {
            val distBetween = (it.cumulativeDistanceMeters - (currentRealManeuver?.cumulativeDistanceMeters ?: traveledMeters)).coerceAtLeast(it.distanceMeters.coerceAtLeast(0.0))
            val secondSpeedMps = ((it.speedLimitKmh.takeIf { spd -> spd > 0 } ?: 80) / 3.6).coerceAtLeast(5.0)
            val secondTimeSec = (distBetween / secondSpeedMps).roundToLong().coerceAtLeast(1L)
            UpcomingManeuver(
                type = mapIconNameToDomainManeuver(it.maneuverType.name),
                roadName = it.roadName,
                exitNumber = null,
                distanceToManeuver = Distance.fromMeters(distBetween),
                timeToManeuver = Duration.fromSeconds(secondTimeSec),
                direction = null
            )
        }

        val activePoly = state.roadPolyline.ifEmpty { plan?.fullPolyline ?: emptyList() }
        val domainSegments: List<RouteSegment> = if (state.routeSegments.isNotEmpty() && activePoly.isNotEmpty()) {
            state.routeSegments.mapNotNull { seg ->
                val startPt = seg.geometry.firstOrNull() ?: activePoly.firstOrNull() ?: return@mapNotNull null
                val endPt = seg.geometry.lastOrNull() ?: activePoly.lastOrNull() ?: startPt
                val segDist = Distance.fromMeters(seg.lengthMeters.coerceAtLeast(1.0))
                RouteSegment(
                    id = "${seg.edgeId}_${seg.segmentIndex}",
                    start = startPt,
                    end = endPt,
                    distance = segDist,
                    bearing = Bearing.calculate(startPt, endPt),
                    elevationProfile = emptyList(),
                    roadName = seg.roadName,
                    roadType = seg.roadType,
                    edgeId = seg.edgeId,
                    direction = seg.direction.name,
                    startOffsetMeters = seg.startOffsetMeters,
                    endOffsetMeters = seg.endOffsetMeters,
                    speedLimitKmh = seg.speedLimitKmh,
                    gradePercent = if (seg.isGradeUnknown) Double.NaN else seg.gradePercent,
                    isGradeUnknown = seg.isGradeUnknown
                )
            }
        } else emptyList()

        val domainRoute = if (plan != null && activePoly.size >= 2) {
            Route(
                id = state.activeRouteId.ifEmpty { plan.planId },
                start = activePoly.first(),
                end = activePoly.last(),
                segments = domainSegments,
                distance = Distance.fromMeters(totalDistMeters.coerceAtLeast(1.0)),
                estimatedTime = (remainingSec ?: plan.totalDrivingDurationSeconds.toLong().coerceAtLeast(1L)).coerceAtLeast(1L)
            )
        } else null

        val validMatchedGps = (currentGps as? GpsLocationState.MapMatchedFix)?.takeIf {
            state.routeGenerationId > 0L &&
                it.routeGenerationId > 0L &&
                it.routeGenerationId == state.routeGenerationId &&
                (it.routeId == null || state.activeRouteId.isEmpty() || it.routeId == state.activeRouteId)
        }
        var resolvedSegIdx = validMatchedGps?.segmentIndex ?: 0
        var resolvedOffsetInSeg = validMatchedGps?.distanceAlongSegmentMeters ?: 0.0
        if (validMatchedGps == null && domainSegments.isNotEmpty()) {
            var acc = 0.0
            for (idx in domainSegments.indices) {
                val segLen = domainSegments[idx].distance.meters
                if (traveledMeters <= acc + segLen || idx == domainSegments.lastIndex) {
                    resolvedSegIdx = idx
                    resolvedOffsetInSeg = (traveledMeters - acc).coerceIn(0.0, segLen)
                    break
                }
                acc += segLen
            }
        }

        val domainRoutePos = if (domainSegments.isNotEmpty()) {
            val safeIdx = resolvedSegIdx.coerceIn(0, domainSegments.lastIndex)
            val seg = domainSegments[safeIdx]
            val segLen = seg.distance.meters.coerceAtLeast(1.0)
            val clampedOffset = resolvedOffsetInSeg.coerceIn(0.0, segLen)
            val segProg = (clampedOffset / segLen).coerceIn(0.0, 1.0)
            val remOnSeg = (segLen - clampedOffset).coerceAtLeast(0.0)
            RoutePosition(
                segment = seg,
                progressAlongSegment = segProg,
                distanceTraveled = Distance.fromMeters(traveledMeters),
                distanceRemainingOnSegment = Distance.fromMeters(remOnSeg),
                totalDistanceRemaining = Distance.fromMeters((remainingMeters ?: 0.0).coerceAtLeast(0.0)),
                routePercentage = routePct,
                segmentIndex = safeIdx,
                edgeId = seg.edgeId,
                direction = seg.direction,
                offsetMeters = clampedOffset
            )
        } else null

        val domainProgress = if (totalDistMeters > 0.0) {
            val totalDrivingSec = plan?.totalDrivingDurationSeconds?.toLong() ?: 0L
            val elapsedSec = (totalDrivingSec - (remainingSec ?: totalDrivingSec)).coerceAtLeast(0L)
            NavigationProgress(
                distanceTraveled = Distance.fromMeters(traveledMeters),
                totalDistance = Distance.fromMeters(totalDistMeters),
                routePercentage = routePct,
                elapsedTime = Duration.fromSeconds(elapsedSec),
                currentSegmentIndex = resolvedSegIdx.coerceAtLeast(0),
                totalSegments = max(1, domainSegments.size)
            )
        } else null

        val nextStopWaypoint = plan?.waypoints?.firstOrNull {
            it.type == WaypointType.CHARGING_STOP &&
                (it.stationDetails?.distanceAlongRouteMeters ?: Double.MAX_VALUE) >= traveledMeters - 500.0
        } ?: plan?.waypoints?.firstOrNull { it.type == WaypointType.CHARGING_STOP }

        val stDetails = nextStopWaypoint?.stationDetails
        val legBeforeStop = if (nextStopWaypoint != null) {
            plan?.legs?.firstOrNull { it.toWaypoint.id == nextStopWaypoint.id }
        } else null
        val verifiedArrivalSoc = legBeforeStop?.predictedArrivalSocPercent
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
        val requestedTargetSoc = nextStopWaypoint?.targetDepartureSocPercent
            ?.takeIf { it.isFinite() && it in 15.0..100.0 }
        val verifiedVehicleBatteryKwh = state.vehicleBatteryKwh.takeIf { it.isFinite() && it > 0.0 }
        val verifiedVehicleMaxKw = state.vehicleMaxChargeKw.takeIf { it.isFinite() && it > 0.0 }
        val nowMs = System.currentTimeMillis()
        val hasFreshLiveTelemetry = stDetails?.liveStatusLastUpdatedEpochMs != null &&
            (nowMs - stDetails.liveStatusLastUpdatedEpochMs) in -60_000L..900_000L
        val isDeterministicValidStop = state.isVehicleSelected &&
            !state.isHypotheticalPlan &&
            !state.isDemoPlan &&
            verifiedVehicleBatteryKwh != null &&
            verifiedVehicleMaxKw != null &&
            nextStopWaypoint != null &&
            stDetails != null &&
            stDetails.maxPowerKw.isFinite() &&
            stDetails.maxPowerKw > 0.0 &&
            stDetails.isVerified &&
            hasFreshLiveTelemetry &&
            verifiedArrivalSoc != null &&
            requestedTargetSoc != null &&
            verifiedArrivalSoc < requestedTargetSoc &&
            (legBeforeStop.connectorCompatibility == ConnectorCompatibility.MATCH &&
                legBeforeStop.feasibilityStatus != FeasibilityStatus.INFEASIBLE)

        val domainChargingStop = if (
            isDeterministicValidStop &&
            nextStopWaypoint != null &&
            stDetails != null &&
            verifiedArrivalSoc != null &&
            requestedTargetSoc != null &&
            verifiedVehicleBatteryKwh != null &&
            verifiedVehicleMaxKw != null
        ) {
            val arrivalSoc = verifiedArrivalSoc
            val targetSoc = requestedTargetSoc.coerceIn(arrivalSoc, 95.0)
            val effectiveMaxKw = stDetails.maxPowerKw
            val domainConnectors = if (stDetails.compatibleConnectors.isNotEmpty()) {
                stDetails.compatibleConnectors.mapIndexed { idx, rawType ->
                    val norm = rawType.uppercase().replace("-", "").replace("_", "").replace("/", "")
                    val cType = when {
                        norm.contains("CCS2") -> ConnectorType.CCS_2
                        norm.contains("CCS1") -> ConnectorType.CCS_1
                        norm.contains("CHADEMO") -> ConnectorType.CHADEMO
                        norm.contains("GBT") -> ConnectorType.GB_T
                        norm.contains("TYPE2") -> ConnectorType.TYPE_2
                        norm.contains("TYPE1") -> ConnectorType.TYPE_1
                        norm.contains("TESLA") -> ConnectorType.TESLA
                        else -> ConnectorType.UNKNOWN
                    }
                    ChargingConnector(
                        id = "${stDetails.stationId}_conn_$idx",
                        type = cType,
                        maxPower = effectiveMaxKw,
                        isAvailable = true
                    )
                }
            } else {
                listOf(
                    ChargingConnector(
                        id = "${stDetails.stationId}_unknown",
                        type = ConnectorType.UNKNOWN,
                        maxPower = effectiveMaxKw,
                        isAvailable = false
                    )
                )
            }
            val distToStopMeters = (stDetails.distanceAlongRouteMeters - traveledMeters).coerceAtLeast(0.0)
            val estChargeMin = ChargingTimeEstimator.minutes(
                batteryCapacityKwh = verifiedVehicleBatteryKwh,
                arrivalSocPercent = arrivalSoc,
                targetSocPercent = targetSoc,
                stationMaxPowerKw = effectiveMaxKw,
                vehicleMaxPowerKw = verifiedVehicleMaxKw
            )
            ChargingStop(
                charger = Charger(
                    id = stDetails.stationId,
                    name = stDetails.stationName,
                    location = nextStopWaypoint.coordinate,
                    connectors = domainConnectors,
                    maxPower = effectiveMaxKw,
                    status = ChargerStatus.AVAILABLE,
                    isPublic = true,
                    provider = null
                ),
                distanceToStop = Distance.fromMeters(distToStopMeters),
                timeToStop = Duration.fromSeconds((distToStopMeters / effectiveSpeedMps).roundToLong().coerceAtLeast(0L)),
                estimatedArrivalSoc = Soc(arrivalSoc),
                targetSoc = Soc(targetSoc),
                estimatedChargingDuration = Duration.fromMinutes(estChargeMin.toLong().coerceAtLeast(1L)),
                isMandatory = true
            )
        } else null

        val offRouteState = when {
            state.rerouteState == RerouteState.IN_PROGRESS || state.lifecycleState == JourneyLifecycleState.REPLANNING ->
                OffRouteState.REROUTING
            state.activeWarning?.contains("انحراف") == true ->
                OffRouteState.SUSPECTED_OFF_ROUTE
            else -> OffRouteState.NORMAL
        }

        val isAuthoritativeVehicleSession = state.isVehicleSelected && !state.isHypotheticalPlan && !state.isDemoPlan
        val dynamicEtaConfidence = when {
            !isAuthoritativeVehicleSession -> 0.40
            gpsQual == GpsQuality.GOOD -> 0.85
            gpsQual == GpsQuality.DEGRADED -> 0.65
            else -> 0.50
        }

        val domainNavState = NavigationState(
            sessionId = NavigationSessionId(
                plan?.planId ?: "session_${state.vehicleId.ifEmpty { "unselected" }}"
            ),
            status = navStatus,
            route = domainRoute,
            routePosition = domainRoutePos,
            matchedLocation = matchedLoc,
            progress = domainProgress,
            upcomingManeuver = upcoming,
            currentManeuver = upcoming,
            nextManeuver = nextUpcoming,
            eta = remainingSec?.let {
                EtaEstimate(
                    eta = System.currentTimeMillis() + it * 1000L,
                    travelTime = Duration.fromSeconds(it),
                    confidence = dynamicEtaConfidence,
                    isBasedOnLiveTraffic = false,
                    adjustmentReason = if (!isAuthoritativeVehicleSession) "ASSUMED_OR_UNSELECTED_VEHICLE" else null
                )
            },
            remainingDistance = remainingMeters?.let { Distance.fromMeters(it) },
            remainingTime = remainingSec?.let { Duration.fromSeconds(it) },
            currentSoc = if (state.isVehicleSelected) state.currentSocPercent else null,
            predictedArrivalSoc = if (isAuthoritativeVehicleSession) {
                state.predictedArrivalSocPercent ?: plan?.finalArrivalSocPercent
            } else null,
            nextChargingStop = domainChargingStop,
            dataQuality = if (!isAuthoritativeVehicleSession && dataQuality != NavigationDataQuality.UNAVAILABLE) {
                NavigationDataQuality.PARTIAL
            } else dataQuality,
            gpsQuality = gpsQual,
            trafficStatus = TrafficStatus.UNAVAILABLE,
            weatherStatus = WeatherStatus.UNAVAILABLE,
            generationId = state.routeGenerationId,
            offRouteState = offRouteState,
            currentSegmentIndex = resolvedSegIdx,
            edgeId = domainRoutePos?.edgeId,
            offsetMeters = domainRoutePos?.offsetMeters ?: 0.0,
            vehicleId = state.vehicleId.ifEmpty { null }
        )

        AutomotiveNavigationStateHolder.updateNavigationState(domainNavState)
    }

    fun mapIconNameToDomainManeuver(iconName: String): ManeuverType {
        return when (iconName.uppercase()) {
            "TURN_LEFT" -> ManeuverType.TURN_LEFT
            "TURN_RIGHT" -> ManeuverType.TURN_RIGHT
            "SHARP_LEFT" -> ManeuverType.SHARP_LEFT
            "SHARP_RIGHT" -> ManeuverType.SHARP_RIGHT
            "SLIGHT_LEFT", "KEEP_LEFT", "FORK_LEFT" -> ManeuverType.SLIGHT_LEFT
            "SLIGHT_RIGHT", "KEEP_RIGHT", "FORK_RIGHT" -> ManeuverType.SLIGHT_RIGHT
            "UTURN", "U_TURN" -> ManeuverType.U_TURN
            "RAMP_EXIT", "EXIT", "RAMP_ENTER" -> ManeuverType.TAKE_EXIT
            "MERGE" -> ManeuverType.MERGE
            "ROUNDABOUT" -> ManeuverType.ENTER_ROUNDABOUT
            "ARRIVE", "DESTINATION" -> ManeuverType.ARRIVE
            else -> ManeuverType.CONTINUE
        }
    }
}
