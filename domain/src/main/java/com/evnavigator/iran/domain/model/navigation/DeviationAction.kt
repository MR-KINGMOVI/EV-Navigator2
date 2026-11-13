package com.evnavigator.iran.domain.model.navigation

/**
 * Suggested actions for handling route deviation.
 */
enum class DeviationAction {
    /**
     * No action needed, user is on track.
     */
    NO_ACTION,

    /**
     * Continue navigation, no reroute required.
     */
    CONTINUE,

    /**
     * Prompt user to confirm reroute.
     */
    CONFIRM_REROUTE,

    /**
     * Automatically initiate reroute.
     */
    AUTO_REROUTE,

    /**
     * Suggest alternative route.
     */
    SUGGEST_ALTERNATIVE
}