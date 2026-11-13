package com.evnavigator.iran.navigation.engine

import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationState

/**
 * Interface for persisting navigation state.
 */
interface NavigationPersistence {
    /**
     * Save navigation session state.
     */
    suspend fun saveSession(sessionId: NavigationSessionId, state: NavigationState)

    /**
     * Load navigation session state.
     */
    suspend fun loadSession(sessionId: NavigationSessionId): NavigationState?

    /**
     * Delete navigation session state.
     */
    suspend fun deleteSession(sessionId: NavigationSessionId)

    /**
     * Check if session exists.
     */
    suspend fun sessionExists(sessionId: NavigationSessionId): Boolean
}