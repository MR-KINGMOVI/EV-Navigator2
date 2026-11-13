package com.evnavigator.iran.domain.journey.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe domain manager for the EV Journey lifecycle.
 * Enforces valid state transitions and protects against invalid operations.
 */
class JourneyLifecycleManager(
    initialState: JourneyLifecycleState = JourneyLifecycleState.DRAFT
) {
    private val _currentState = MutableStateFlow(initialState)
    val currentState: StateFlow<JourneyLifecycleState> = _currentState.asStateFlow()

    private val _transitionHistory = mutableListOf<JourneyTransitionResult.Success>()
    val transitionHistory: List<JourneyTransitionResult.Success> get() = _transitionHistory.toList()

    private var preReplanningState: JourneyLifecycleState = JourneyLifecycleState.ACTIVE

    @Synchronized
    fun processEvent(event: JourneyEvent): JourneyTransitionResult {
        val current = _currentState.value

        // Prevent any transitions from terminal states
        if (current.isTerminal) {
            return JourneyTransitionResult.InvalidTransition(
                currentState = current,
                event = event,
                reason = "Cannot transition from terminal state $current."
            )
        }

        val result = when (event) {
            is JourneyEvent.CreateDraft -> {
                if (current == JourneyLifecycleState.DRAFT) {
                    JourneyTransitionResult.NoOp(current, "Already in DRAFT state.")
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot create draft from $current.")
                }
            }

            is JourneyEvent.StartPlanning -> {
                if (current == JourneyLifecycleState.DRAFT || current == JourneyLifecycleState.READY_TO_START) {
                    transitionTo(JourneyLifecycleState.PLANNING, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot start planning from $current.")
                }
            }

            is JourneyEvent.PlanReady -> {
                if (current == JourneyLifecycleState.PLANNING || current == JourneyLifecycleState.DRAFT) {
                    transitionTo(JourneyLifecycleState.READY_TO_START, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Plan cannot be marked ready from $current.")
                }
            }

            is JourneyEvent.StartJourney -> {
                if (current == JourneyLifecycleState.READY_TO_START || current == JourneyLifecycleState.TEMPORARILY_PAUSED) {
                    transitionTo(JourneyLifecycleState.ACTIVE, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Journey can only start from READY_TO_START or TEMPORARILY_PAUSED (was: $current).")
                }
            }

            is JourneyEvent.PauseJourney -> {
                if (current == JourneyLifecycleState.TEMPORARILY_PAUSED) {
                    JourneyTransitionResult.NoOp(current, "Journey is already paused.")
                } else if (current.isNavigating || current == JourneyLifecycleState.READY_TO_START) {
                    transitionTo(JourneyLifecycleState.TEMPORARILY_PAUSED, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot pause journey in state $current.")
                }
            }

            is JourneyEvent.ResumeJourney -> {
                if (current == JourneyLifecycleState.TEMPORARILY_PAUSED) {
                    transitionTo(JourneyLifecycleState.ACTIVE, event)
                } else if (current == JourneyLifecycleState.ACTIVE) {
                    JourneyTransitionResult.NoOp(current, "Journey is already active.")
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot resume journey that is not paused.")
                }
            }

            is JourneyEvent.ApproachWaypoint -> {
                if (current == JourneyLifecycleState.ACTIVE || current == JourneyLifecycleState.APPROACHING_WAYPOINT) {
                    transitionTo(JourneyLifecycleState.APPROACHING_WAYPOINT, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot approach waypoint from $current.")
                }
            }

            is JourneyEvent.ApproachChargingStop -> {
                if (current == JourneyLifecycleState.ACTIVE || current == JourneyLifecycleState.APPROACHING_WAYPOINT || current == JourneyLifecycleState.APPROACHING_CHARGING_STOP) {
                    transitionTo(JourneyLifecycleState.APPROACHING_CHARGING_STOP, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot approach charging stop from $current.")
                }
            }

            is JourneyEvent.ReachChargingStop -> {
                if (current == JourneyLifecycleState.APPROACHING_CHARGING_STOP || current == JourneyLifecycleState.ACTIVE) {
                    transitionTo(JourneyLifecycleState.CHARGING_STOP_REACHED, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot reach charging stop from $current.")
                }
            }

            is JourneyEvent.StartCharging -> {
                if (current == JourneyLifecycleState.CHARGING_STOP_REACHED) {
                    transitionTo(JourneyLifecycleState.CHARGING_IN_PROGRESS, event)
                } else if (current == JourneyLifecycleState.CHARGING_IN_PROGRESS) {
                    JourneyTransitionResult.NoOp(current, "Charging session already in progress.")
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot start charging before reaching stop (current: $current).")
                }
            }

            is JourneyEvent.FinishCharging -> {
                if (current == JourneyLifecycleState.CHARGING_IN_PROGRESS || current == JourneyLifecycleState.CHARGING_STOP_REACHED) {
                    transitionTo(JourneyLifecycleState.READY_TO_DEPART, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot finish charging from $current.")
                }
            }

            is JourneyEvent.DepartChargingStop -> {
                if (current == JourneyLifecycleState.READY_TO_DEPART || current == JourneyLifecycleState.CHARGING_STOP_REACHED) {
                    transitionTo(JourneyLifecycleState.ACTIVE, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot depart charging stop without finishing or aborting session.")
                }
            }

            is JourneyEvent.TriggerReplanning -> {
                if (current.isNavigating || current == JourneyLifecycleState.TEMPORARILY_PAUSED) {
                    preReplanningState = current
                    transitionTo(JourneyLifecycleState.REPLANNING, event)
                } else if (current == JourneyLifecycleState.REPLANNING) {
                    JourneyTransitionResult.NoOp(current, "Replanning already in progress.")
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot trigger replanning from $current.")
                }
            }

            is JourneyEvent.ReplanSucceeded -> {
                if (current == JourneyLifecycleState.REPLANNING) {
                    transitionTo(JourneyLifecycleState.ACTIVE, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "No active replanning operation to succeed.")
                }
            }

            is JourneyEvent.ReplanFailed -> {
                if (current == JourneyLifecycleState.REPLANNING) {
                    // Fall back to pre-replanning state so user can continue without loss of plan
                    transitionTo(preReplanningState, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "No active replanning operation to fail.")
                }
            }

            is JourneyEvent.ReachDestination -> {
                if (current.isNavigating || current == JourneyLifecycleState.READY_TO_DEPART) {
                    transitionTo(JourneyLifecycleState.COMPLETED, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot complete journey from $current.")
                }
            }

            is JourneyEvent.CancelJourney -> {
                transitionTo(JourneyLifecycleState.CANCELLED, event)
            }

            is JourneyEvent.SystemInterrupted -> {
                transitionTo(JourneyLifecycleState.INTERRUPTED_RECOVERY, event)
            }

            is JourneyEvent.RecoverJourney -> {
                if (current == JourneyLifecycleState.INTERRUPTED_RECOVERY) {
                    transitionTo(JourneyLifecycleState.ACTIVE, event)
                } else {
                    JourneyTransitionResult.InvalidTransition(current, event, "Cannot recover journey when not in INTERRUPTED_RECOVERY.")
                }
            }
        }

        return result
    }

    private fun transitionTo(newState: JourneyLifecycleState, event: JourneyEvent): JourneyTransitionResult.Success {
        val fromState = _currentState.value
        _currentState.value = newState
        val success = JourneyTransitionResult.Success(
            fromState = fromState,
            toState = newState,
            event = event
        )
        _transitionHistory.add(success)
        return success
    }
}
