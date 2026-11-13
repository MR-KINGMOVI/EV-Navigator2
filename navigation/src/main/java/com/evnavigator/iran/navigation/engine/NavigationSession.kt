package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationStatus
import com.evnavigator.iran.domain.model.navigation.NavigationState

/**
 * Represents a navigation session.
 */
class NavigationSession(
    val id: NavigationSessionId,
    var status: NavigationStatus = NavigationStatus.IDLE,
    var state: NavigationState? = null,
    private val stateReducer: NavigationStateReducer
) {
    /**
     * Update the navigation session with new state.
     */
    fun updateState(newState: NavigationState) {
        this.state = newState
    }

    /**
     * Transition to a new status.
     */
    fun transitionTo(status: NavigationStatus) {
        this.status = status
    }
}