package com.evnavigator.iran.domain.model.trip.intelligence

/**
 * Execution outcome classification for [TripIntelligenceOrchestrator] (V13.3 Section 14).
 * Distinguishes a complete analysis from a partial analysis where energy prediction succeeded
 * but charging plan generation failed.
 */
enum class TripIntelligenceExecutionStatus {
    FULL_SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}

/**
 * Final result of trip intelligence calculations.
 */
data class TripIntelligenceResult(
    val isSuccess: Boolean,
    val requestTimestamp: Long,  // When the calculation was requested
    val completionTimestamp: Long, // When the calculation completed
    val vehicleAndBatteryInputs: VehicleAndBatteryInputs? = null,
    val routeAndSegmentInputs: RouteAndSegmentInputs? = null,
    val energyPrediction: EnergyPrediction? = null,
    val arrivalSocPrediction: ArrivalSocPrediction? = null,
    val reserveFeasibility: ReserveFeasibility? = null,
    val chargingRequirement: ChargingRequirement? = null,
    val chargingPlanCandidate: ChargingPlanCandidate? = null, // Only present if charging is required
    val dataQualityAndFreshness: DataQualityAndFreshness? = null,
    val predictionAssumptions: PredictionAssumptions? = null,
    val decisionExplanation: DecisionExplanation? = null,
    val error: TripIntelligenceError? = null,  // Present when overall processing failed
    val chargingPlanError: TripIntelligenceError? = null, // Present when charging was required but plan generation failed
    val executionStatus: TripIntelligenceExecutionStatus = when {
        !isSuccess -> TripIntelligenceExecutionStatus.FAILED
        chargingRequirement?.requiresCharging == true && chargingPlanCandidate == null -> TripIntelligenceExecutionStatus.PARTIAL_SUCCESS
        else -> TripIntelligenceExecutionStatus.FULL_SUCCESS
    }
) {
    val isPartialSuccess: Boolean
        get() = executionStatus == TripIntelligenceExecutionStatus.PARTIAL_SUCCESS

    /**
     * Returns true if the trip is feasible with current parameters (either directly without charging,
     * or via a valid non-infeasible charging plan when charging is required).
     */
    fun isFeasible(): Boolean {
        if (!isSuccess || executionStatus == TripIntelligenceExecutionStatus.FAILED) return false
        val requiresCharging = chargingRequirement?.requiresCharging == true || reserveFeasibility?.isFeasible == false
        return if (!requiresCharging) {
            reserveFeasibility?.isFeasible == true
        } else {
            chargingPlanCandidate != null && chargingPlanCandidate.isFeasible
        }
    }
}