package com.evnavigator.iran.domain.model.trip.intelligence

/**
 * Represents an error or unavailable state in trip intelligence processing.
 */
sealed class TripIntelligenceError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Indicates that the required data for making a decision is missing.
     */
    class InsufficientData(
        message: String = "Insufficient data to make a meaningful trip intelligence decision",
        cause: Throwable? = null
    ) : TripIntelligenceError(message, cause)
    
    /**
     * Indicates that the requested operation cannot be performed due to invalid inputs.
     */
    class InvalidInput(
        message: String = "Invalid input parameters for trip intelligence",
        cause: Throwable? = null
    ) : TripIntelligenceError(message, cause)
    
    /**
     * Indicates a failure in one of the underlying systems used by trip intelligence.
     */
    class SystemFailure(
        message: String = "System error occurred during processing",
        cause: Throwable? = null
    ) : TripIntelligenceError(message, cause)
    
    /**
     * Indicates that no feasible plan could be generated for the given constraints.
     */
    class InfeasiblePlan(
        message: String = "No feasible trip plan found with current parameters",
        cause: Throwable? = null
    ) : TripIntelligenceError(message, cause)
}
