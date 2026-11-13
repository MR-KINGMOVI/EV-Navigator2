package com.evnavigator.iran.automotive.lifecycle

import android.util.Log
import androidx.car.app.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manages the lifecycle of automotive sessions and ensures proper cleanup
 * of resources when sessions are created or destroyed.
 */
class AutomotiveLifecycleManager {
    companion object {
        private const val TAG = "AutomotiveLifecycle"
    }

    private var coroutineScope: CoroutineScope? = null
    private var currentSession: Session? = null

    /**
     * Initialize the lifecycle manager with a coroutine scope for background operations
     */
    fun initialize(scope: CoroutineScope) {
        this.coroutineScope = scope
        Log.d(TAG, "Automotive lifecycle manager initialized")
    }

    /**
     * Handle session creation
     */
    fun onSessionCreated(session: Session) {
        currentSession = session
        Log.d(TAG, "Automotive session created: ${session.javaClass.simpleName}")
        
        // Start any background tasks for this session
        coroutineScope?.launch(Dispatchers.IO) {
            // Example: initialize session-specific resources
            Log.d(TAG, "Initializing session resources")
        }
    }

    /**
     * Handle session destruction
     */
    fun onSessionDestroyed() {
        Log.d(TAG, "Automotive session destroyed")
        currentSession = null
        
        // Cancel any ongoing operations
        coroutineScope?.cancel("Session destroyed")
    }

    /**
     * Check if a session is currently active
     */
    fun isSessionActive(): Boolean {
        return currentSession != null
    }
}