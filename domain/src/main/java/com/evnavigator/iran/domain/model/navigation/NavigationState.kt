package com.evnavigator.iran.domain.model.navigation

/**
 * Represents the complete navigation state at a given point in time (V13.0 Audit — Item 43 & Item 105).
 */
data class NavigationState(
    val sessionId: NavigationSessionId,
    val status: NavigationStatus,
    val routePosition: RoutePosition? = null,
    val currentManeuver: ManeuverInstruction? = null,
    val nextManeuver: ManeuverInstruction? = null,
    val upcomingManeuver: ManeuverInstruction? = null,
    val remainingDistanceMeters: Double = 0.0,
    val remainingTimeSeconds: Long = 0L,
    val estimatedTimeOfArrivalMillis: Long = 0L,
    val currentSoc: Double? = null, // State of Charge percentage (0-100)
    val estimatedDestinationSoc: Double? = null,
    val nextChargingStop: ChargingStopInstruction? = null,
    val offRouteState: OffRouteState = OffRouteState.OnRoute,
    val rerouteReason: RerouteReason? = null,
    val speedKmh: Double = 0.0,
    val bearingDegrees: Float = 0f,
    val isGpsAvailable: Boolean = true,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val clearNextChargingStop: Boolean = false,
    val clearManeuvers: Boolean = false,
    val clearRoutePosition: Boolean = false
) {
    /**
     * Applies an explicit [NavigationStatePatch] where nullable fields can be set, left unchanged,
     * or explicitly cleared via [Patch.Clear].
     */
    fun applyPatch(
        patch: NavigationStatePatch,
        nowMillis: Long = System.currentTimeMillis()
    ): NavigationState {
        return copy(
            status = when (val p = patch.status) {
                is Patch.Set -> p.value
                else -> status
            },
            routePosition = patch.routePosition.resolve(routePosition),
            currentManeuver = patch.currentManeuver.resolve(currentManeuver),
            nextManeuver = patch.nextManeuver.resolve(nextManeuver),
            upcomingManeuver = patch.upcomingManeuver.resolve(upcomingManeuver),
            remainingDistanceMeters = when (val p = patch.remainingDistanceMeters) {
                is Patch.Set -> p.value.coerceAtLeast(0.0)
                is Patch.Clear -> 0.0
                is Patch.Unchanged -> remainingDistanceMeters
            },
            remainingTimeSeconds = when (val p = patch.remainingTimeSeconds) {
                is Patch.Set -> p.value.coerceAtLeast(0L)
                is Patch.Clear -> 0L
                is Patch.Unchanged -> remainingTimeSeconds
            },
            estimatedTimeOfArrivalMillis = when (val p = patch.estimatedTimeOfArrivalMillis) {
                is Patch.Set -> p.value
                is Patch.Clear -> 0L
                is Patch.Unchanged -> estimatedTimeOfArrivalMillis
            },
            currentSoc = patch.currentSoc.resolve(currentSoc),
            estimatedDestinationSoc = patch.estimatedDestinationSoc.resolve(estimatedDestinationSoc),
            nextChargingStop = patch.nextChargingStop.resolve(nextChargingStop),
            offRouteState = when (val p = patch.offRouteState) {
                is Patch.Set -> p.value
                is Patch.Clear -> OffRouteState.OnRoute
                is Patch.Unchanged -> offRouteState
            },
            rerouteReason = patch.rerouteReason.resolve(rerouteReason),
            speedKmh = when (val p = patch.speedKmh) {
                is Patch.Set -> p.value.coerceAtLeast(0.0)
                is Patch.Clear -> 0.0
                is Patch.Unchanged -> speedKmh
            },
            bearingDegrees = when (val p = patch.bearingDegrees) {
                is Patch.Set -> p.value
                is Patch.Clear -> 0f
                is Patch.Unchanged -> bearingDegrees
            },
            isGpsAvailable = when (val p = patch.isGpsAvailable) {
                is Patch.Set -> p.value
                else -> isGpsAvailable
            },
            lastUpdatedMillis = nowMillis,
            clearNextChargingStop = patch.nextChargingStop is Patch.Clear,
            clearManeuvers = patch.currentManeuver is Patch.Clear &&
                patch.nextManeuver is Patch.Clear &&
                patch.upcomingManeuver is Patch.Clear,
            clearRoutePosition = patch.routePosition is Patch.Clear
        )
    }
}

/**
 * Instruction for an upcoming charging stop during navigation.
 */
data class ChargingStopInstruction(
    val stationId: String,
    val stationName: String,
    val distanceToStationMeters: Double,
    val timeToStationSeconds: Long,
    val arrivalSoc: Double,
    val targetSoc: Double,
    val estimatedChargingTimeSeconds: Long,
    val availableChargers: Int? = null
)
