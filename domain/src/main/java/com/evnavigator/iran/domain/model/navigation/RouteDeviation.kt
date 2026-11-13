package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Distance

/**
 * Represents information about route deviation.
 */
data class RouteDeviation(
    /**
     * Whether the user is currently off-route.
     */
    val isOffRoute: Boolean,

    /**
     * The distance from the route (positive = deviation away from route).
     */
    val deviationDistance: Distance,

    /**
     * Confidence level that the deviation is real (0.0 to 1.0).
     */
    val confidence: Double, // 0.0 = no confidence, 1.0 = full confidence

    /**
     * The time when deviation was first detected.
     */
    val detectionTime: Long,

    /**
     * Suggested action for handling deviation.
     */
    val suggestedAction: DeviationAction
)