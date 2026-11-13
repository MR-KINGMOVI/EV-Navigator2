package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.energy.model.*
import com.evnavigator.iran.core.elevation.ClimbDescentSection
import kotlin.math.atan

/**
 * Calculator for elevation-related energy components
 */
object ElevationEnergyCalculator {
    
    private const val GRAVITATIONAL_ACCELERATION = 9.80665 // m/s²
    
    /**
     * Calculates energy required to overcome elevation changes
     * @param mass Operating mass of the vehicle (kg)
     * @param elevationChangeMeters Vertical distance change (positive for uphill)
     * @param distanceMeters Horizontal distance of the segment  
     * @param gradePercent Grade percentage (positive for uphill, negative for downhill)
     * @param uphillEfficiency Efficiency when going uphill
     * @param downhillEfficiency Efficiency when going downhill (regen)
     * @return Energy required or recovered (Wh). Positive = energy needed, Negative = energy recovered
     */
    fun calculateElevationEnergy(
        mass: Mass,
        elevationChangeMeters: Double,
        distanceMeters: Double,
        gradePercent: Double,
        uphillEfficiency: Efficiency,
        downhillEfficiency: Efficiency
    ): Double {
        if (!elevationChangeMeters.isFinite() || elevationChangeMeters.isNaN() || gradePercent.isNaN()) {
            return Double.NaN
        }

        // Potential energy = m * g * h
        val potentialEnergyJoules = mass.kilograms * GRAVITATIONAL_ACCELERATION * elevationChangeMeters
        
        // Convert to Wh
        val potentialEnergyWh = potentialEnergyJoules / 3600.0
        
        // For uphill, we need energy to overcome potential
        if (elevationChangeMeters > 0) {
            return potentialEnergyWh / uphillEfficiency.fraction
        } 
        // For downhill, we can recover some energy  
        else if (elevationChangeMeters < 0) {
            val recoveredEnergy = potentialEnergyWh * downhillEfficiency.fraction
            return -recoveredEnergy // Negative means recovery
        }
        
        return 0.0
    }
    
    /**
     * Calculates the grade percentage from elevation change and distance
     * @param elevationChangeMeters Vertical distance change  
     * @param horizontalDistanceMeters Horizontal distance
     * @return Grade percentage (positive for uphill, negative for downhill)
     */
    fun calculateGradePercent(
        elevationChangeMeters: Double,
        horizontalDistanceMeters: Double
    ): Double {
        if (horizontalDistanceMeters <= 0) return 0.0
        
        // Grade = (elevation change / horizontal distance) * 100
        return (elevationChangeMeters / horizontalDistanceMeters) * 100.0
    }
    
    /**
     * Calculates the grade angle in radians from grade percentage
     * @param gradePercent Grade percentage (e.g. 6.0 for 6%)
     * @return Grade angle in radians: theta = atan(gradePercent / 100.0)
     */
    fun calculateGradeAngleRadians(gradePercent: Double): Double {
        // Grade % is 100 * tan(theta). Thus theta = atan(grade / 100)
        return atan(gradePercent / 100.0)
    }
    
    /**
     * Calculates the elevation gain and loss from elevation points
     * @param startElevationPoint Starting elevation point  
     * @param endElevationPoint Ending elevation point
     * @return Elevation gain (positive) and loss (negative) in meters
     */
    fun calculateElevationGainsAndLosses(
        startElevationPoint: ElevationPoint,
        endElevationPoint: ElevationPoint
    ): Pair<Double, Double> {
        if (!startElevationPoint.isValid() || !endElevationPoint.isValid()) {
            return Pair(Double.NaN, Double.NaN)
        }
        
        val elevationChange = endElevationPoint.elevationMeters - startElevationPoint.elevationMeters
        val gain = if (elevationChange > 0) elevationChange else 0.0
        val loss = if (elevationChange < 0) Math.abs(elevationChange) else 0.0
        
        return Pair(gain, loss)
    }
}
