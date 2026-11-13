package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.GeoCoordinate
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit tri-state patch type for nullable navigation state fields (V13.0 Audit — Item 43 & Item 105).
 *
 * Solves the reducer bug where `newState.nextChargingStop ?: currentState.nextChargingStop`
 * made it impossible to clear a completed or cancelled charging stop or maneuver.
 *
 * - [Unchanged]: Retain the existing value in `currentState`.
 * - [Set]: Replace with [value].
 * - [Clear]: Explicitly set the nullable field to `null`.
 */
sealed class Patch<out T> {
    data object Unchanged : Patch<Nothing>()
    data class Set<out T>(val value: T) : Patch<T>()
    data object Clear : Patch<Nothing>()

    fun <R : Any> resolve(current: R?): R? where T : R = when (this) {
        is Unchanged -> current
        is Set -> this.value
        is Clear -> null
    }
}

/**
 * Structured patch payload for atomic navigation state updates (V13.0 Audit — Item 43).
 */
data class NavigationStatePatch(
    val status: Patch<NavigationStatus> = Patch.Unchanged,
    val routePosition: Patch<RoutePosition> = Patch.Unchanged,
    val currentManeuver: Patch<ManeuverInstruction> = Patch.Unchanged,
    val nextManeuver: Patch<ManeuverInstruction> = Patch.Unchanged,
    val upcomingManeuver: Patch<ManeuverInstruction> = Patch.Unchanged,
    val remainingDistanceMeters: Patch<Double> = Patch.Unchanged,
    val remainingTimeSeconds: Patch<Long> = Patch.Unchanged,
    val estimatedTimeOfArrivalMillis: Patch<Long> = Patch.Unchanged,
    val currentSoc: Patch<Double> = Patch.Unchanged,
    val estimatedDestinationSoc: Patch<Double> = Patch.Unchanged,
    val nextChargingStop: Patch<ChargingStopInstruction> = Patch.Unchanged,
    val offRouteState: Patch<OffRouteState> = Patch.Unchanged,
    val rerouteReason: Patch<RerouteReason> = Patch.Unchanged,
    val speedKmh: Patch<Double> = Patch.Unchanged,
    val bearingDegrees: Patch<Float> = Patch.Unchanged,
    val isGpsAvailable: Patch<Boolean> = Patch.Unchanged
)

/**
 * Unified single-stream navigation event hierarchy for cockpit UI, Android Auto surface, and voice engine
 * (V13.0 Audit — Items 44–47, 106–110, 187).
 */
sealed class NavigationEvent {
    abstract val timestampMs: Long

    data class RouteRecalculated(
        val routeId: String,
        val reason: RerouteReason,
        val distanceMeters: Double,
        val durationSeconds: Long,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : NavigationEvent()

    data class ManeuverChanged(
        val stepIndex: Int,
        val instruction: ManeuverInstruction,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : NavigationEvent()

    data class VoiceGuidanceTriggered(
        val announcementKey: String,
        val stepIndex: Int,
        val stageName: String,
        val textFa: String,
        val isSafetyCritical: Boolean = false,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : NavigationEvent()

    data class ChargingStopCleared(
        val stationId: String?,
        val reason: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : NavigationEvent()

    data class OffRouteDetected(
        val deviationDistanceMeters: Double,
        val currentLocation: GeoCoordinate?,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : NavigationEvent()

    data class ArrivalConfirmed(
        val destinationCoordinate: GeoCoordinate?,
        val finalSocPercent: Double?,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : NavigationEvent()
}

/**
 * Stateful voice-prompt deduplicator ensuring each `(routeId, stepIndex, stage)` tuple is announced
 * at most once unless a safety-critical alert overrides it (V13.0 Audit — Item 48 & Item 110).
 */
class VoiceAnnouncementDeduplicator {
    private val announcedKeys = ConcurrentHashMap.newKeySet<String>()

    fun shouldAnnounce(
        routeId: String,
        stepIndex: Int,
        stageName: String,
        isSafetyCritical: Boolean = false
    ): Boolean {
        if (isSafetyCritical) return true
        val key = "$routeId:$stepIndex:$stageName"
        return announcedKeys.add(key)
    }

    fun onNewRoute(routeId: String) {
        announcedKeys.removeIf { !it.startsWith("$routeId:") }
    }

    fun reset() {
        announcedKeys.clear()
    }
}
