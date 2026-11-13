package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.model.Soc

/**
 * Result indicating whether the trip can be completed with required reserve.
 * Explicitly separates:
 * - [isNumericallyFeasible]: nominal arrival SOC >= requiredReserveSoc
 * - [isFeasible]: operational feasibility (requires both numerical margin AND verified/high-confidence inputs)
 * - [isUncertain]: true when numerical margin exists but inputs are assumed, stale, or high-uncertainty
 * - [safetyEligibility]: "ELIGIBLE", "UNCERTAIN_REQUIRES_CONFIRMATION", or "INFEASIBLE"
 */
data class ReserveFeasibility(
    val isFeasible: Boolean,
    val requiredReserveSoc: Soc? = null,
    val availableReserveSoc: Soc? = null,
    val feasibilityReasons: List<String> = emptyList(),
    val isNumericallyFeasible: Boolean = isFeasible,
    val isUncertain: Boolean = false,
    val safetyEligibility: String = when {
        isFeasible && !isUncertain -> "ELIGIBLE"
        isNumericallyFeasible && isUncertain -> "UNCERTAIN_REQUIRES_CONFIRMATION"
        else -> "INFEASIBLE"
    }
)