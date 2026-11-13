package com.evnavigator.iran.core.vehicle

/**
 * Aerodynamic specification for electric vehicles.
 */
data class AerodynamicSpecification(
    // Core aerodynamic parameters
    val dragCoefficient: Double?,  // Drag coefficient (Cd) - dimensionless
    val frontalAreaM2: Double?,    // Frontal area in square meters
    
    // Additional aerodynamic information (if known)
    val aerodynamicSource: String?, // Source of the aerodynamic data (e.g., "NEDC", "WLTP")
    val aerodynamicConfidence: Double?, // Confidence level 0.0 to 1.0 in the measurements
    val windTunnelTested: Boolean?,   // Whether tested in a wind tunnel
    
    // Aerodynamic characteristics (if known)
    val coefficientOfLift: Double?,   // Coefficient of lift (Cl) - dimensionless  
    val coefficientOfDrag: Double?,   // Coefficient of drag (Cd) - dimensionless
    val sideWindEffect: Double?       // Effect of crosswind on drag (if known)
) {
    init {
        dragCoefficient?.let { require(it >= 0.0) { "Drag coefficient cannot be negative" } }
        frontalAreaM2?.let { require(it >= 0.0) { "Frontal area cannot be negative" } }
        aerodynamicConfidence?.let { require(it >= 0.0 && it <= 1.0) { "Aerodynamic confidence must be between 0.0 and 1.0" } }
        coefficientOfLift?.let { require(it >= -1.0 && it <= 1.0) { "Coefficient of lift must be between -1.0 and 1.0" } }
        coefficientOfDrag?.let { require(it >= 0.0) { "Coefficient of drag cannot be negative" } }
    }
    
    /**
     * Determines if basic aerodynamic parameters are available
     */
    val hasBasicAerodynamics: Boolean
        get() = dragCoefficient != null && frontalAreaM2 != null
    
    /**
     * Gets the aerodynamic source information
     */
    val sourceInfo: String
        get() = when {
            aerodynamicSource != null -> "Source: $aerodynamicSource"
            else -> "No source specified"
        }
    
    /**
     * Calculates the aerodynamic drag force at a given speed (for testing)
     */
    fun calculateDragForce(
        speedMps: Double, 
        airDensityKgm3: Double = 1.225 // Standard air density
    ): Double? {
        return if (hasBasicAerodynamics && speedMps >= 0) {
            val dragForce = 0.5 * airDensityKgm3 * dragCoefficient!! * frontalAreaM2!! * speedMps * speedMps
            dragForce
        } else {
            null
        }
    }
}