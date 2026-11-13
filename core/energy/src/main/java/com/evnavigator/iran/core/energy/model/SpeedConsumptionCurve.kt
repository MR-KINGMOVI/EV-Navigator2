package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.energy.model.*

/**
 * Represents the speed consumption curve (Wh/km at different speeds)
 */
data class SpeedConsumptionCurve(
    val points: List<SpeedConsumptionPoint>
) {
    init {
        // Ensure points are sorted by speed
        require(points.zipWithNext().all { it.first.speed.metersPerSecond <= it.second.speed.metersPerSecond }) {
            "Speed consumption curve points must be sorted by speed"
        }
        
        require(points.isNotEmpty()) {
            "Speed consumption curve cannot be empty"
        }
    }
    
    /**
     * Gets the consumption at a specific speed using linear interpolation
     */
    fun getConsumptionAt(speed: Speed): Double {
        if (points.size == 1) return points[0].consumptionWhPerKm
        
        // Find the appropriate segment
        val sortedPoints = points.sortedBy { it.speed.metersPerSecond }
        
        // Handle speeds below minimum
        if (speed.metersPerSecond <= sortedPoints.first().speed.metersPerSecond) {
            return sortedPoints.first().consumptionWhPerKm
        }
        
        // Handle speeds above maximum  
        if (speed.metersPerSecond >= sortedPoints.last().speed.metersPerSecond) {
            return sortedPoints.last().consumptionWhPerKm
        }
        
        // Find the segment containing this speed
        for (i in 0 until sortedPoints.size - 1) {
            val point1 = sortedPoints[i]
            val point2 = sortedPoints[i + 1]
            
            if (speed.metersPerSecond >= point1.speed.metersPerSecond && 
                speed.metersPerSecond <= point2.speed.metersPerSecond) {
                
                // Linear interpolation
                val ratio = (speed.metersPerSecond - point1.speed.metersPerSecond) / 
                           (point2.speed.metersPerSecond - point1.speed.metersPerSecond)
                
                return point1.consumptionWhPerKm + ratio * (point2.consumptionWhPerKm - point1.consumptionWhPerKm)
            }
        }
        
        // Should not reach here if sorted correctly
        return sortedPoints.last().consumptionWhPerKm
    }
    
    /**
     * Gets the consumption at a specific speed, with bounds checking
     */
    fun getConsumptionAtSafe(speed: Speed): Double {
        val consumption = getConsumptionAt(speed)
        return consumption.coerceAtLeast(0.0) // Ensure non-negative consumption
    }
    
    /**
     * Validates that the curve is reasonable
     */
    fun validate(): Boolean {
        if (points.isEmpty()) return false
        
        // Check for negative consumption values
        val hasNegativeConsumption = points.any { it.consumptionWhPerKm < 0.0 }
        if (hasNegativeConsumption) return false
        
        // Check that speeds are non-negative
        val hasNegativeSpeeds = points.any { it.speed.metersPerSecond < 0.0 }
        if (hasNegativeSpeeds) return false
        
        return true
    }
    
    /**
     * Gets the minimum and maximum speeds in the curve
     */
    val minSpeed: Speed get() = points.minByOrNull { it.speed.metersPerSecond }?.speed ?: Speed(0.0)
    val maxSpeed: Speed get() = points.maxByOrNull { it.speed.metersPerSecond }?.speed ?: Speed(0.0)
    
    /**
     * Gets the minimum and maximum consumption values in the curve
     */
    val minConsumption: Double get() = points.minOfOrNull { it.consumptionWhPerKm } ?: 0.0
    val maxConsumption: Double get() = points.maxOfOrNull { it.consumptionWhPerKm } ?: 0.0
}

/**
 * Represents a single point on the speed consumption curve
 */
data class SpeedConsumptionPoint(
    val speed: Speed,
    val consumptionWhPerKm: Double
) {
    init {
        require(speed.metersPerSecond >= 0.0) { "Speed cannot be negative" }
        require(consumptionWhPerKm >= 0.0) { "Consumption cannot be negative" }
    }
}