package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Distance
import com.evnavigator.iran.core.energy.model.Mass

/**
 * Vehicle dimensions and mass specifications.
 */
data class VehicleDimensions(
    // Mass specifications
    val curbMassKg: Mass,              // Curb weight without passengers/load
    val grossVehicleMassKg: Mass?,    // Gross vehicle mass including passengers/load
    
    // Physical dimensions  
    val lengthMm: Int?,               // Length in millimeters
    val widthMm: Int?,                // Width in millimeters
    val heightMm: Int?,               // Height in millimeters
    val wheelbaseMm: Int?,            // Wheelbase in millimeters
    val groundClearanceMm: Int?,      // Ground clearance in millimeters
    
    // Wheel and tire specifications (if known)
    val wheelDiameterMm: Int?,        // Wheel diameter in millimeters  
    val tireWidthMm: Int?             // Tire width in millimeters
    
) {
    init {
        require(curbMassKg.kilograms > 0) { "Curb mass must be positive" }
        grossVehicleMassKg?.let { require(it.kilograms > 0) { "Gross vehicle mass must be positive" } }
        
        // Validate dimensions (if provided)
        lengthMm?.let { require(it > 0) { "Length must be positive" } }
        widthMm?.let { require(it > 0) { "Width must be positive" } }
        heightMm?.let { require(it > 0) { "Height must be positive" } }
        wheelbaseMm?.let { require(it > 0) { "Wheelbase must be positive" } }
        groundClearanceMm?.let { require(it >= 0) { "Ground clearance cannot be negative" } }
        
        // Validate wheel/tire specifications
        wheelDiameterMm?.let { require(it > 0) { "Wheel diameter must be positive" } }
        tireWidthMm?.let { require(it > 0) { "Tire width must be positive" } }
    }
    
    /**
     * Gets the vehicle length in meters
     */
    val lengthM: Double?
        get() = lengthMm?.toDouble()?.div(1000.0)
    
    /**
     * Gets the vehicle width in meters
     */
    val widthM: Double?
        get() = widthMm?.toDouble()?.div(1000.0)
        
    /**
     * Gets the vehicle height in meters
     */
    val heightM: Double?
        get() = heightMm?.toDouble()?.div(1000.0)
    
    /**
     * Gets the wheelbase in meters
     */
    val wheelbaseM: Double?
        get() = wheelbaseMm?.toDouble()?.div(1000.0)
    
    /**
     * Calculates the vehicle's frontal area (if dimensions are available)
     */
    fun calculateFrontalArea(): Double? {
        return if (widthM != null && heightM != null) {
            widthM!! * heightM!!
        } else {
            null
        }
    }
    
    /**
     * Determines if all key dimensions are available for aerodynamic calculations
     */
    val hasCompleteDimensions: Boolean
        get() = widthMm != null && heightMm != null
    
    /**
     * Checks if the vehicle has wheel/tire specifications
     */
    val hasWheelSpecifications: Boolean
        get() = wheelDiameterMm != null || tireWidthMm != null
}