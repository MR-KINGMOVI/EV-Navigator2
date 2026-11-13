package com.evnavigator.iran.ui.screen.journey

import com.evnavigator.iran.domain.journey.tracking.DeviationAssessment
import com.evnavigator.iran.domain.journey.tracking.GpsMatchedProgressInput
import com.evnavigator.iran.domain.journey.tracking.JourneyProgressTracker
import com.evnavigator.iran.domain.journey.tracking.RouteDeviationDetector
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState

/**
 * Coordinates live GPS signal state transitions, longitudinal progress tracking,
 * monotonic maneuver updates, and off-route deviation assessment for [ActiveJourneyViewModel].
 */
object JourneyGpsAndDeviationCoordinator {

    data class GpsSignalAction(
        val updatedState: ActiveJourneyUiState,
        val locationUpdateParams: LocationUpdateParams? = null,
        val shouldPublishAutomotive: Boolean = false
    )

    data class LocationUpdateParams(
        val latitude: Double,
        val longitude: Double,
        val speedKmh: Double,
        val accuracyMeters: Double,
        val matchedFix: GpsMatchedProgressInput? = null
    )

    data class LocationEvaluationOutcome(
        val updatedState: ActiveJourneyUiState,
        val shouldTriggerReplanning: Boolean
    )

    fun handleGpsLocationState(
        gps: GpsLocationState,
        currentState: ActiveJourneyUiState
    ): GpsSignalAction {
        return when (gps) {
            is GpsLocationState.FixAcquired -> {
                var nextState = currentState.copy(
                    isTunnelMode = false,
                    activeWarning = if (currentState.navigationMode == NavigationScreenMode.GPS_WEAK_OR_DISABLED) null else currentState.activeWarning
                )
                if (nextState.navigationMode == NavigationScreenMode.GPS_WEAK_OR_DISABLED) {
                    nextState = nextState.copy(
                        navigationMode = if (nextState.plan != null) NavigationScreenMode.ACTIVE_NAVIGATION else NavigationScreenMode.IDLE_FREE_DRIVE
                    )
                }
                val validAcc = if (gps.isAccuracyKnown && !gps.accuracyMeters.isNaN()) gps.accuracyMeters else null
                AutomotiveNavigationStateHolder.updateLastKnownLocation(
                    coordinate = GeoCoordinate(gps.location.latitude, gps.location.longitude),
                    timestampEpochMs = gps.location.timestampEpochMs,
                    accuracyMeters = validAcc
                )
                GpsSignalAction(
                    updatedState = nextState,
                    locationUpdateParams = LocationUpdateParams(
                        latitude = gps.location.latitude,
                        longitude = gps.location.longitude,
                        speedKmh = gps.speedKmh?.toDouble() ?: 0.0,
                        accuracyMeters = validAcc?.toDouble() ?: Double.NaN
                    )
                )
            }
            is GpsLocationState.MapMatchedFix -> {
                val nextState = currentState.copy(isTunnelMode = false)
                val realAccuracy = gps.snappedLocation.accuracyMeters
                AutomotiveNavigationStateHolder.updateLastKnownLocation(
                    coordinate = GeoCoordinate(gps.snappedLocation.latitude, gps.snappedLocation.longitude),
                    timestampEpochMs = gps.snappedLocation.timestampEpochMs,
                    accuracyMeters = realAccuracy
                )
                val segLen = nextState.routeSegments.getOrNull(gps.segmentIndex)?.lengthMeters ?: 0.0
                val matchedInput = GpsMatchedProgressInput(
                    segmentIndex = gps.segmentIndex,
                    distanceAlongSegmentMeters = gps.distanceAlongSegmentMeters,
                    distanceAlongRouteMeters = gps.distanceAlongRouteMeters,
                    crossTrackOffsetMeters = gps.crossTrackOffsetMeters,
                    routeGenerationId = gps.routeGenerationId,
                    confidence = gps.confidence,
                    segmentLengthMeters = segLen,
                    routeId = gps.routeId,
                    fixTimestampEpochMs = gps.snappedLocation.timestampEpochMs
                )
                GpsSignalAction(
                    updatedState = nextState,
                    locationUpdateParams = LocationUpdateParams(
                        latitude = gps.snappedLocation.latitude,
                        longitude = gps.snappedLocation.longitude,
                        speedKmh = gps.speedKmh.toDouble(),
                        accuracyMeters = realAccuracy?.toDouble() ?: Double.NaN,
                        matchedFix = matchedInput
                    )
                )
            }
            is GpsLocationState.DeadReckoning -> {
                val nextState = currentState.copy(
                    isTunnelMode = true,
                    statusMessage = "تونل / ناوبری تخمینی اینرسیال (${gps.outageDurationSeconds}s)"
                )
                val drAccuracy = gps.estimatedLocation.accuracyMeters ?: gps.uncertaintyMeters
                AutomotiveNavigationStateHolder.updateLastKnownLocation(
                    coordinate = GeoCoordinate(gps.estimatedLocation.latitude, gps.estimatedLocation.longitude),
                    timestampEpochMs = gps.estimatedLocation.timestampEpochMs,
                    accuracyMeters = drAccuracy
                )
                GpsSignalAction(
                    updatedState = nextState,
                    locationUpdateParams = LocationUpdateParams(
                        latitude = gps.estimatedLocation.latitude,
                        longitude = gps.estimatedLocation.longitude,
                        speedKmh = gps.estimatedSpeedKmh.toDouble(),
                        accuracyMeters = drAccuracy.toDouble()
                    )
                )
            }
            is GpsLocationState.SearchingForFix,
            is GpsLocationState.GpsDisabled,
            is GpsLocationState.PermissionDenied -> {
                if (currentState.navigationMode == NavigationScreenMode.ACTIVE_NAVIGATION) {
                    GpsSignalAction(
                        updatedState = currentState.copy(
                            isTunnelMode = false,
                            activeWarning = "سیگنال ماهواره GPS ضعیف است یا قطع شده است"
                        ),
                        shouldPublishAutomotive = true
                    )
                } else {
                    GpsSignalAction(updatedState = currentState)
                }
            }
            else -> GpsSignalAction(updatedState = currentState)
        }
    }

    fun evaluateLocationUpdate(
        currentState: ActiveJourneyUiState,
        latitude: Double,
        longitude: Double,
        speedKmh: Double,
        accuracyMeters: Double,
        matchedFix: GpsMatchedProgressInput?,
        progressTracker: JourneyProgressTracker,
        deviationDetector: RouteDeviationDetector,
        maneuverCoordinator: JourneyManeuverProgressionCoordinator
    ): LocationEvaluationOutcome? {
        val currentPlan = currentState.plan ?: return null
        val currentGenId = currentState.routeGenerationId
        val pos = GeoCoordinate(latitude, longitude)
        val hasValidAccuracy = !accuracyMeters.isNaN() && accuracyMeters.isFinite() && accuracyMeters > 0.0
        val effectiveAccuracy = if (hasValidAccuracy) accuracyMeters else Double.NaN
        val prevTargetWpIdx = ((currentState.progress?.currentWaypointIndex ?: 0) + 1).coerceAtLeast(1)

        val progress = progressTracker.computeProgress(
            plan = currentPlan,
            location = pos,
            speedKmh = speedKmh,
            accuracyMeters = effectiveAccuracy,
            matchedFix = matchedFix,
            activeRouteGenerationId = currentGenId,
            previousTargetWaypointIndex = prevTargetWpIdx
        )

        val activeRoutePolyline = currentState.roadPolyline.ifEmpty { currentPlan.fullPolyline }
        val deviation = deviationDetector.evaluatePosition(pos, activeRoutePolyline, effectiveAccuracy)

        val totalDist = currentPlan.totalDistanceMeters.coerceAtLeast(0.0)
        val traveledMeters = (totalDist - progress.remainingDistanceToDestinationMeters).coerceIn(0.0, totalDist)
        val maneuvers = currentState.maneuvers
        val activeManeuverIdx = maneuverCoordinator.resolveMonotonicManeuverIndex(
            maneuvers = maneuvers,
            traveledMeters = traveledMeters,
            currentGenId = currentGenId,
            currentRouteId = currentState.activeRouteId
        )
        val activeManeuver = if (activeManeuverIdx >= 0) maneuvers[activeManeuverIdx] else null
        val distToActiveManeuver = if (activeManeuver != null) {
            (activeManeuver.cumulativeDistanceMeters - traveledMeters).coerceAtLeast(0.0)
        } else {
            currentState.nextManeuverDistanceMeters
        }

        val updatedState = currentState.copy(
            progress = progress,
            currentManeuver = activeManeuver?.instructionFarsi ?: currentState.currentManeuver,
            currentManeuverIcon = activeManeuver?.maneuverType?.name ?: currentState.currentManeuverIcon,
            nextManeuverDistanceMeters = distToActiveManeuver,
            nextRoadName = activeManeuver?.roadName ?: currentState.nextRoadName,
            speedLimitKmh = activeManeuver?.speedLimitKmh ?: currentState.speedLimitKmh,
            activeWarning = when {
                !hasValidAccuracy -> "دقت سیگنال GPS نامشخص است؛ تشخیص انحراف موقتاً غیرفعال شد"
                deviation is DeviationAssessment.PotentialDeviation -> "احتمال انحراف از مسیر..."
                deviation is DeviationAssessment.ConfirmedDeviation -> "انحراف از مسیر! در حال محاسبه مسیر جدید..."
                else -> currentState.activeWarning
            }
        )

        return LocationEvaluationOutcome(
            updatedState = updatedState,
            shouldTriggerReplanning = deviation is DeviationAssessment.ConfirmedDeviation && hasValidAccuracy
        )
    }
}
