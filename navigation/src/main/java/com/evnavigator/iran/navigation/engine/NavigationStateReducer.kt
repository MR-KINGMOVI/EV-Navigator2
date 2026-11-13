package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationState
import com.evnavigator.iran.domain.model.navigation.NavigationStatePatch
import com.evnavigator.iran.domain.model.navigation.NavigationStatus

/**
 * Reducer for navigation state management (V13.0 Audit — Item 43 & Item 105).
 * Responsible for applying state transitions, explicit `Patch.Clear` semantics, and lifecycle transformations.
 */
interface NavigationStateReducer {
    /**
     * Reduce a new state based on current state and action.
     */
    fun reduce(
        sessionId: NavigationSessionId,
        currentState: NavigationState?,
        newState: NavigationState
    ): NavigationState

    /**
     * Reduce an explicit [NavigationStatePatch] where nullable fields can be set, left unchanged,
     * or explicitly cleared via `Patch.Clear`.
     */
    fun reducePatch(
        sessionId: NavigationSessionId,
        currentState: NavigationState?,
        patch: NavigationStatePatch
    ): NavigationState {
        val base = currentState ?: createInitialState(sessionId)
        return base.applyPatch(patch)
    }

    /**
     * Create initial state for a session.
     */
    fun createInitialState(sessionId: NavigationSessionId): NavigationState

    /**
     * Handle session lifecycle transitions.
     */
    fun handleLifecycleTransition(
        sessionId: NavigationSessionId,
        currentState: NavigationState?,
        newStatus: NavigationStatus
    ): NavigationState
}