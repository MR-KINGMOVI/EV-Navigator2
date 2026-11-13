package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.energy.model.*

/**
 * Calculator for auxiliary energy consumption (HVAC, lights, infotainment, etc.)
 */
object AuxiliaryEnergyCalculator {
    
    /**
     * Calculates auxiliary energy consumption over a time period
     * @param auxiliaryPowerW Fixed auxiliary power consumption (W)
     * @param timeSeconds Time period (seconds)  
     * @return Energy consumed by auxiliary systems (Wh)
     */
    fun calculateAuxiliaryEnergy(
        auxiliaryPowerW: Power,
        timeSeconds: Double
    ): Double {
        if (timeSeconds.isNaN() || timeSeconds.isInfinite() || timeSeconds <= 0.0) return 0.0
        // E = P * t (Joules) → convert to Wh
        val energyJoules = auxiliaryPowerW.watts * timeSeconds
        return energyJoules / 3600.0
    }
    
    /**
     * Calculates auxiliary energy consumption based on distance and speed
     * @param auxiliaryPowerW Fixed auxiliary power consumption (W)
     * @param distanceMeters Distance of the segment (m)  
     * @param averageSpeedMps Average speed during the segment (m/s)
     * @return Energy consumed by auxiliary systems (Wh)
     */
    fun calculateAuxiliaryEnergyFromDistance(
        auxiliaryPowerW: Power,
        distanceMeters: Double,
        averageSpeedMps: Double
    ): Double {
        if (averageSpeedMps <= 0) return 0.0
        
        val timeSeconds = distanceMeters / averageSpeedMps
        return calculateAuxiliaryEnergy(auxiliaryPowerW, timeSeconds)
    }
    
    /**
     * Calculates auxiliary energy with temperature adjustment
     * @param auxiliaryPowerW Base auxiliary power (W)
     * @param timeSeconds Time period (seconds)
     * @param temperatureAdjustmentFactor Temperature adjustment factor (1.0 = normal)
     * @return Adjusted energy consumption (Wh)
     */
    fun calculateAuxiliaryEnergyWithTemperature(
        auxiliaryPowerW: Power,
        timeSeconds: Double,
        temperatureAdjustmentFactor: Double = 1.0
    ): Double {
        val adjustedPower = Power(auxiliaryPowerW.watts * temperatureAdjustmentFactor)
        return calculateAuxiliaryEnergy(adjustedPower, timeSeconds)
    }
    
    /**
     * Calculates auxiliary energy with driving mode adjustment  
     * @param auxiliaryPowerW Base auxiliary power (W)
     * @param timeSeconds Time period (seconds)
     * @param drivingMode Driving mode that affects consumption
     * @return Adjusted energy consumption (Wh)
     */
    fun calculateAuxiliaryEnergyWithDrivingMode(
        auxiliaryPowerW: Power,
        timeSeconds: Double,
        drivingMode: DrivingMode
    ): Double {
        val adjustmentFactor = when (drivingMode) {
            DrivingMode.ECO -> 0.9   // 10% less consumption
            DrivingMode.NORMAL -> 1.0  // Normal consumption
            DrivingMode.SPORT -> 1.2   // 20% more consumption
        }
        
        return calculateAuxiliaryEnergyWithTemperature(auxiliaryPowerW, timeSeconds, adjustmentFactor)
    }
}