package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.elevation.ClimbDescentSection
import com.evnavigator.iran.core.energy.model.*
import com.evnavigator.iran.core.roadprofile.RoadProfileData

/**
 * Calculator for road load components (rolling resistance, aerodynamic drag)
 */
object RoadLoadCalculator {
    
    /**
     * Calculates rolling resistance energy for a segment
     * @param mass Operating mass of the vehicle
     * @param distanceMeters Distance of the segment  
     * @param rollingResistanceCoefficient Rolling resistance coefficient
     * @return Energy required to overcome rolling resistance (Wh)
     */
    fun calculateRollingResistanceEnergy(
        mass: Mass,
        distanceMeters: Double,
        rollingResistanceCoefficient: Double
    ): Double {
        // F = Crr * m * g
        val gravitationalAcceleration = 9.80665 // m/s²
        val rollingResistanceForce = rollingResistanceCoefficient * mass.kilograms * gravitationalAcceleration
        
        // E = F * d (Joules) → convert to Wh
        val energyJoules = rollingResistanceForce * distanceMeters
        return energyJoules / 3600.0
    }
    
    /**
     * Calculates aerodynamic drag energy for a segment
     * @param speedMps Speed of the vehicle (m/s)
     * @param frontalAreaM2 Frontal area of the vehicle (m²)  
     * @param dragCoefficient Drag coefficient
     * @param distanceMeters Distance of the segment
     * @return Energy required to overcome aerodynamic drag (Wh)
     */
    fun calculateAerodynamicDragEnergy(
        speedMps: Double,
        frontalAreaM2: Double,
        dragCoefficient: Double,
        distanceMeters: Double
    ): Double {
        // F = 0.5 * rho * Cd * A * v²
        val airDensity = 1.225 // kg/m³ at sea level (standard atmosphere)
        val aerodynamicForce = 0.5 * airDensity * dragCoefficient * frontalAreaM2 * speedMps * speedMps
        
        // E = F * d (Joules) → convert to Wh
        val energyJoules = aerodynamicForce * distanceMeters
        return energyJoules / 3600.0
    }
    
    /**
     * Calculates the total road load energy for a segment
     * @param mass Operating mass of the vehicle
     * @param speedMps Speed of the vehicle (m/s)
     * @param distanceMeters Distance of the segment  
     * @param rollingResistanceCoefficient Rolling resistance coefficient
     * @param frontalAreaM2 Frontal area of the vehicle (m²)
     * @param dragCoefficient Drag coefficient
     * @return Total road load energy (Wh)
     */
    fun calculateTotalRoadLoadEnergy(
        mass: Mass,
        speedMps: Double,
        distanceMeters: Double,
        rollingResistanceCoefficient: Double,
        frontalAreaM2: Double,
        dragCoefficient: Double
    ): Double {
        val rollingResistanceEnergy = calculateRollingResistanceEnergy(
            mass, 
            distanceMeters, 
            rollingResistanceCoefficient
        )
        
        val aerodynamicDragEnergy = calculateAerodynamicDragEnergy(
            speedMps, 
            frontalAreaM2, 
            dragCoefficient, 
            distanceMeters
        )
        
        return rollingResistanceEnergy + aerodynamicDragEnergy
    }
}