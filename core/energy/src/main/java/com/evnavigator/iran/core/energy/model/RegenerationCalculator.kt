package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.energy.model.*

/**
 * Calculator for regenerative energy recovery components
 */
object RegenerationCalculator {
    
    private const val GRAVITATIONAL_ACCELERATION = 9.80665 // m/s²
    
    /**
     * Calculates the maximum possible energy that could be recovered from downhill driving
     * @param mass Operating mass of the vehicle (kg)
     * @param elevationChangeMeters Vertical distance change (negative for downhill)  
     * @param downhillEfficiency Efficiency when going downhill (regen)
     * @return Maximum recoverable energy (Wh)
     */
    fun calculateMaximumRecoverableEnergy(
        mass: Mass,
        elevationChangeMeters: Double,
        downhillEfficiency: Efficiency
    ): Double {
        if (elevationChangeMeters >= 0) return 0.0 // No recovery on uphill
        
        // Potential energy = m * g * h
        val potentialEnergyJoules = mass.kilograms * GRAVITATIONAL_ACCELERATION * Math.abs(elevationChangeMeters)
        
        // Convert to Wh and apply efficiency
        val potentialEnergyWh = potentialEnergyJoules / 3600.0
        return potentialEnergyWh * downhillEfficiency.fraction
    }
    
    /**
     * Calculates the actual energy recovered from regenerative braking
     * @param mass Operating mass of the vehicle (kg)
     * @param elevationChangeMeters Vertical distance change  
     * @param distanceMeters Horizontal distance
     * @param gradePercent Grade percentage
     * @param downhillEfficiency Efficiency when going downhill (regen)
     * @param maxRegenerativePowerW Maximum regenerative power available
     * @param batteryAcceptanceWh Maximum energy the battery can accept in this segment  
     * @return Actual recovered energy (Wh) - always positive, but represents a reduction in consumption
     */
    fun calculateActualRecovery(
        mass: Mass,
        elevationChangeMeters: Double,
        distanceMeters: Double,
        gradePercent: Double,
        downhillEfficiency: Efficiency,
        maxRegenerativePowerW: Power? = null,
        batteryAcceptanceWh: Double? = null
    ): Double {
        if (elevationChangeMeters >= 0) return 0.0 // No recovery on uphill
        
        // Calculate maximum possible energy that could be recovered
        val maxRecoverableEnergy = calculateMaximumRecoverableEnergy(
            mass, 
            elevationChangeMeters, 
            downhillEfficiency
        )
        
        // Apply battery acceptance constraint if provided
        val finalEnergy = if (batteryAcceptanceWh != null && batteryAcceptanceWh < maxRecoverableEnergy) {
            batteryAcceptanceWh
        } else {
            maxRecoverableEnergy
        }
        
        return finalEnergy
    }
    
    /**
     * Calculates whether regeneration is possible for a given segment
     * @param elevationChangeMeters Vertical distance change  
     * @param distanceMeters Horizontal distance
     * @return True if downhill driving (potential for recovery)
     */
    fun canRegenerate(
        elevationChangeMeters: Double,
        distanceMeters: Double
    ): Boolean {
        return elevationChangeMeters < 0 && distanceMeters > 0
    }
    
    /**
     * Calculates the maximum regenerative power that could be generated
     * @param mass Operating mass of the vehicle (kg)
     * @param speedMps Speed of the vehicle (m/s) 
     * @param gradeAngleRadians Grade angle in radians
     * @param downhillEfficiency Efficiency when going downhill (regen)
     * @return Maximum regenerative power (W)
     */
    fun calculateMaximumRegenerativePower(
        mass: Mass,
        speedMps: Double,
        gradeAngleRadians: Double,
        downhillEfficiency: Efficiency
    ): Power {
        // Regenerative power is related to kinetic energy and grade
        // This is a simplified model - in reality, it depends on vehicle regen limits
        val kineticEnergy = 0.5 * mass.kilograms * speedMps * speedMps
        
        // Approximate regenerative power from gravity component
        val gravitationalPower = mass.kilograms * GRAVITATIONAL_ACCELERATION * Math.sin(gradeAngleRadians) * speedMps
        
        // Apply efficiency
        val effectivePower = gravitationalPower * downhillEfficiency.fraction
        
        return Power(effectivePower.coerceAtLeast(0.0))
    }
}