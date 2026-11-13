package com.evnavigator.iran.navigation.engine.impl

import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationState
import com.evnavigator.iran.domain.model.navigation.NavigationStatePatch
import com.evnavigator.iran.domain.model.navigation.NavigationStatus
import com.evnavigator.iran.domain.model.navigation.OffRouteState
import com.evnavigator.iran.navigation.engine.NavigationStateReducer

/**
 * Implementation of [NavigationStateReducer] (V13.0 Audit — Item 43 & Item 105).
 *
 * Key V13.0 fixes:
 * - Honors `clearNextChargingStop`, `clearManeuvers`, and `clearRoutePosition` flags so completed
 *   charging stops and finished maneuvers can be explicitly set to `null`.
 * - Supports [reducePatch] with tri-state `Patch<T>` (`Unchanged`, `Set`, `Clear`) semantics.
 * - Automatically clears stale maneuvers and charging stops when transitioning to `ARRIVED`, `CANCELLED`, or `IDLE`.
 */
class NavigationStateReducerImpl : NavigationStateReducer {

    override fun reduce(
        sessionId: NavigationSessionId,
        currentState: NavigationState?,
        newState: NavigationState
    ): NavigationState {
        val now = System.currentTimeMillis()
        if (currentState == null) {
            return newState.copy(
                sessionId = sessionId,
                lastUpdatedMillis = now
            )
        }

        val isTerminalStatus = newState.status == NavigationStatus.ARRIVED ||
            newState.status == NavigationStatus.CANCELLED ||
            newState.status == NavigationStatus.IDLE

        val resolvedRoutePosition = when {
            newState.clearRoutePosition || newState.status == NavigationStatus.CANCELLED -> null
            else -> newState.routePosition ?: currentState.routePosition
        }

        val resolvedCurrentManeuver = when {
            newState.clearManeuvers || isTerminalStatus -> null
            else -> newState.currentManeuver ?: currentState.currentManeuver
        }

        val resolvedNextManeuver = when {
            newState.clearManeuvers || isTerminalStatus -> null
            else -> newState.nextManeuver ?: currentState.nextManeuver
        }

        val resolvedUpcomingManeuver = when {
            newState.clearManeuvers || isTerminalStatus -> null
            else -> newState.upcomingManeuver ?: currentState.upcomingManeuver
        }

        val resolvedNextChargingStop = when {
            newState.clearNextChargingStop || isTerminalStatus -> null
            else -> newState.nextChargingStop ?: currentState.nextChargingStop
        }

        return currentState.copy(
            sessionId = sessionId,
            status = newState.status,
            routePosition = resolvedRoutePosition,
            currentManeuver = resolvedCurrentManeuver,
            nextManeuver = resolvedNextManeuver,
            upcomingManeuver = resolvedUpcomingManeuver,
            remainingDistanceMeters = if (newState.status == NavigationStatus.ARRIVED) 0.0 else newState.remainingDistanceMeters,
            remainingTimeSeconds = if (newState.status == NavigationStatus.ARRIVED) 0L else newState.remainingTimeSeconds,
            estimatedTimeOfArrivalMillis = newState.estimatedTimeOfArrivalMillis,
            currentSoc = newState.currentSoc ?: currentState.currentSoc,
            estimatedDestinationSoc = newState.estimatedDestinationSoc ?: currentState.estimatedDestinationSoc,
            nextChargingStop = resolvedNextChargingStop,
            offRouteState = newState.offRouteState,
            rerouteReason = newState.rerouteReason,
            speedKmh = newState.speedKmh,
            bearingDegrees = newState.bearingDegrees,
            isGpsAvailable = newState.isGpsAvailable,
            lastUpdatedMillis = now,
            clearNextChargingStop = false,
            clearManeuvers = false,
            clearRoutePosition = false
        )
    }

    override fun reducePatch(
        sessionId: NavigationSessionId,
        currentState: NavigationState?,
        patch: NavigationStatePatch
    ): NavigationState {
        val base = currentState ?: createInitialState(sessionId)
        return base.copy(sessionId = sessionId).applyPatch(patch)
    }

    override fun createInitialState(sessionId: NavigationSessionId): NavigationState {
        return NavigationState(
            sessionId = sessionId,
            status = NavigationStatus.INITIALIZING,
            routePosition = null,
            currentManeuver = null,
            nextManeuver = null,
            upcomingManeuver = null,
            remainingDistanceMeters = 0.0,
            remainingTimeSeconds = 0L,
            estimatedTimeOfArrivalMillis = 0L,
            currentSoc = null,
            estimatedDestinationSoc = null,
            nextChargingStop = null,
            offRouteState = OffRouteState.OnRoute,
            rerouteReason = null,
            speedKmh = 0.0,
            bearingDegrees = 0f,
            isGpsAvailable = true,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    override fun handleLifecycleTransition(
        sessionId: NavigationSessionId,
        currentState: NavigationState?,
        newStatus: NavigationStatus
    ): NavigationState {
        val baseState = currentState ?: createInitialState(sessionId)

        return when (newStatus) {
            NavigationStatus.CANCELLED, NavigationStatus.ARRIVED -> {
                baseState.copy(
                    status = newStatus,
                    currentManeuver = null,
                    nextManeuver = null,
                    upcomingManeuver = null,
                    nextChargingStop = null,
                    remainingDistanceMeters = if (newStatus == NavigationStatus.ARRIVED) 0.0 else baseState.remainingDistanceMeters,
                    remainingTimeSeconds = if (newStatus == NavigationStatus.ARRIVED) 0L else baseState.remainingTimeSeconds,
                    speedKmh = 0.0,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
            NavigationStatus.PAUSED -> {
                baseState.copy(
                    status = newStatus,
                    speedKmh = 0.0,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
            else -> {
                baseState.copy(
                    status = newStatus,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
        }
    }
}