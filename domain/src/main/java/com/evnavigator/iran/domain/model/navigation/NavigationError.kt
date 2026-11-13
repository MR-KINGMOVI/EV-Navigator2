package com.evnavigator.iran.domain.model.navigation

/**
 * Represents an error in the navigation system.
 */
data class NavigationError(
    /**
     * The type of error that occurred.
     */
    val type: ErrorType,

    /**
     * A human-readable description of the error.
     */
    val message: String,

    /**
     * The timestamp when the error occurred.
     */
    val timestamp: Long,

    /**
     * Any related data that might help diagnose the issue.
     */
    val details: Map<String, Any>?
)