package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.energy.model.*

/**
 * Calculator for acceleration and deceleration energy components
 */
object AccelerationEnergyCalculator {
    
    /**
     * Calculates the kinetic energy change during acceleration or deceleration
     * @param mass Operating mass of the vehicle (kg)
     * @param initialSpeedMps Initial speed (m/s)  
     * @param finalSpeedMps Final speed (m/s)
     * @return Kinetic energy change (Joules). Positive for acceleration, negative for deceleration
     */
    fun calculateKineticEnergyChange(
        mass: Mass,
        initialSpeedMps: Double,
        finalSpeedMps: Double
    ): Double {
        // ΔKE = 0.5 * m * (v₂² - v₁²)
        val kineticEnergyChange = 0.5 * mass.kilograms * (finalSpeedMps * finalSpeedMps - initialSpeedMps * initialSpeedMps)
        return kineticEnergyChange
    }
    
    /**
     * Calculates the energy required for acceleration or recovered from deceleration
     * @param mass Operating mass of the vehicle (kg)
     * @param initialSpeedMps Initial speed (m/s)  
     * @param finalSpeedMps Final speed (m/s)
     * @param accelerationEfficiency Efficiency during acceleration
     * @param decelerationEfficiency Efficiency during deceleration
     * @return Energy required for acceleration or recovered from deceleration (Wh).
     *         Positive = energy needed, Negative = energy recovered
     */
    fun calculateAccelerationEnergy(
        mass: Mass,
        initialSpeedMps: Double,
        finalSpeedMps: Double,
        accelerationEfficiency: Efficiency,
        decelerationEfficiency: Efficiency
    ): Double {
        val kineticEnergyChangeJoules = calculateKineticEnergyChange(mass, initialSpeedMps, finalSpeedMps)
        
        // Convert to Wh
        val kineticEnergyChangeWh = kineticEnergyChangeJoules / 3600.0
        
        // Apply efficiency based on whether accelerating or decelerating
        return if (kineticEnergyChangeJoules > 0) {
            // Acceleration - need energy
            kineticEnergyChangeWh / accelerationEfficiency.fraction
        } else if (kineticEnergyChangeJoules < 0) {
            // Deceleration - recover energy  
            val recovery = Math.abs(kineticEnergyChangeWh) * decelerationEfficiency.fraction
            -recovery // Negative means recovery
        } else {
            0.0 // No change
        }
    }
    
    /**
     * Calculates the average acceleration during a segment
     * @param initialSpeedMps Initial speed (m/s)
     * @param finalSpeedMps Final speed (m/s) 
     * @param distanceMeters Distance of the segment
     * @return Average acceleration (m/s²)
     */
    fun calculateAverageAcceleration(
        initialSpeedMps: Double,
        finalSpeedMps: Double,
        distanceMeters: Double
    ): Double {
        if (distanceMeters <= 0) return 0.0
        
        // Using v² = u² + 2as, so a = (v² - u²) / (2s)
        val acceleration = (finalSpeedMps * finalSpeedMps - initialSpeedMps * initialSpeedMps) / (2.0 * distanceMeters)
        return acceleration
    }
    
    /**
     * Calculates the time required to change speed over a given distance
     * @param initialSpeedMps Initial speed (m/s)
     * @param finalSpeedMps Final speed (m/s)  
     * @param distanceMeters Distance of the segment
     * @return Time in seconds
     */
    fun calculateTimeForSpeedChange(
        initialSpeedMps: Double,
        finalSpeedMps: Double,
        distanceMeters: Double
    ): Double {
        if (distanceMeters <= 0) return 0.0
        
        // Using s = (u + v) / 2 * t, so t = 2s / (u + v)
        val time = 2.0 * distanceMeters / (initialSpeedMps + finalSpeedMps)
        return time.coerceAtLeast(0.0)
    }
}