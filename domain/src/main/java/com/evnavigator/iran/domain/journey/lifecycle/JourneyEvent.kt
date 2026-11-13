package com.evnavigator.iran.domain.journey.lifecycle

/**
 * Domain events that trigger state transitions in the EV journey lifecycle.
 */
sealed class JourneyEvent {
    data object CreateDraft : JourneyEvent()
    data class StartPlanning(val origin: String, val destination: String) : JourneyEvent()
    data class PlanReady(val planId: String) : JourneyEvent()
    data object StartJourney : JourneyEvent()
    data class PauseJourney(val reason: String = "User requested") : JourneyEvent()
    data object ResumeJourney : JourneyEvent()

    data class ApproachWaypoint(val waypointId: String, val distanceMeters: Double) : JourneyEvent()
    data class ApproachChargingStop(val stationId: String, val distanceMeters: Double) : JourneyEvent()
    data class ReachChargingStop(val stationId: String) : JourneyEvent()
    data class StartCharging(val stationId: String, val startSocPercent: Double, val chargerPowerKw: Double) : JourneyEvent()
    data class FinishCharging(val stationId: String, val currentSocPercent: Double) : JourneyEvent()
    data class DepartChargingStop(val stationId: String) : JourneyEvent()

    data class TriggerReplanning(val reason: String) : JourneyEvent()
    data class ReplanSucceeded(val newRouteId: String) : JourneyEvent()
    data class ReplanFailed(val reason: String) : JourneyEvent()

    data object ReachDestination : JourneyEvent()
    data class CancelJourney(val reason: String = "User cancelled") : JourneyEvent()
    data class SystemInterrupted(val errorDescription: String) : JourneyEvent()
    data object RecoverJourney : JourneyEvent()
}

/**
 * Result of evaluating a lifecycle transition.
 */
sealed class JourneyTransitionResult {
    data class Success(
        val fromState: JourneyLifecycleState,
        val toState: JourneyLifecycleState,
        val event: JourneyEvent,
        val timestampEpochMs: Long = System.currentTimeMillis()
    ) : JourneyTransitionResult()

    data class InvalidTransition(
        val currentState: JourneyLifecycleState,
        val event: JourneyEvent,
        val reason: String
    ) : JourneyTransitionResult()

    data class NoOp(
        val currentState: JourneyLifecycleState,
        val message: String
    ) : JourneyTransitionResult()
}
