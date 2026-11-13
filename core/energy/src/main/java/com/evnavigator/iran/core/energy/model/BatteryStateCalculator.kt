package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.energy.model.*

/**
 * Calculator for battery state management (SoE, SOC propagation)
 */
object BatteryStateCalculator {
    
    /**
     * Propagates battery state through a segment
     * @param currentBatteryState Current battery state
     * @param energyDeltaWh Net energy change for the segment (positive = consumption, negative = recovery)
     * @param reserveWh Reserve energy to maintain safety margin
     * @return Updated battery state after the segment
     */
    fun propagateBatteryState(
        currentBatteryState: BatteryState,
        energyDeltaWh: Double,
        reserveWh: Double? = null
    ): BatteryState {
        val effectiveReserveWh = reserveWh ?: currentBatteryState.reserveWh.wattHours
        
        // Calculate new SoE
        val newSoEWh = (currentBatteryState.currentSoEWh.wattHours + energyDeltaWh).coerceIn(
            0.0,
            currentBatteryState.usableCapacityWh.wattHours - effectiveReserveWh
        )
        
        return currentBatteryState.copy(
            currentSoEWh = Energy(newSoEWh)
        )
    }
    
    /**
     * Calculates the minimum SoC along a route
     * @param segmentResults List of segment results with battery states
     * @return Minimum SoC percentage encountered on the route
     */
    fun calculateMinimumSoC(
        segmentResults: List<SegmentEnergyResult>
    ): Double {
        if (segmentResults.isEmpty()) return 100.0
        
        return segmentResults.minOfOrNull { 
            it.batteryStateAfterSegment.currentSoCPercent 
        } ?: 100.0
    }
    
    /**
     * Calculates the maximum SoC along a route  
     * @param segmentResults List of segment results with battery states
     * @return Maximum SoC percentage encountered on the route
     */
    fun calculateMaximumSoC(
        segmentResults: List<SegmentEnergyResult>
    ): Double {
        if (segmentResults.isEmpty()) return 0.0
        
        return segmentResults.maxOfOrNull { 
            it.batteryStateAfterSegment.currentSoCPercent 
        } ?: 0.0
    }
    
    /**
     * Calculates the minimum SoE along a route
     * @param segmentResults List of segment results with battery states
     * @return Minimum SoE in Wh encountered on the route
     */
    fun calculateMinimumSoE(
        segmentResults: List<SegmentEnergyResult>
    ): Double {
        if (segmentResults.isEmpty()) return 0.0
        
        return segmentResults.minOfOrNull { 
            it.batteryStateAfterSegment.currentSoEWh.wattHours 
        } ?: 0.0
    }
    
    /**
     * Calculates the maximum SoE along a route
     * @param segmentResults List of segment results with battery states
     * @return Maximum SoE in Wh encountered on the route
     */
    fun calculateMaximumSoE(
        segmentResults: List<SegmentEnergyResult>
    ): Double {
        if (segmentResults.isEmpty()) return 0.0
        
        return segmentResults.maxOfOrNull { 
            it.batteryStateAfterSegment.currentSoEWh.wattHours 
        } ?: 0.0
    }
    
    /**
     * Validates that battery state remains within safe bounds
     * @param batteryState Battery state to validate
     * @return True if battery state is valid, false otherwise
     */
    fun validateBatteryState(
        batteryState: BatteryState
    ): Boolean {
        // Check SoE is within bounds
        if (batteryState.currentSoEWh.wattHours < 0.0) return false
        if (batteryState.currentSoEWh.wattHours > batteryState.usableCapacityWh.wattHours) return false
        
        // Check SoC is within bounds
        val currentSoC = batteryState.currentSoCPercent
        if (currentSoC < 0.0 || currentSoC > 100.0) return false
        
        // Check operational bounds
        if (batteryState.minimumOperationalSoCPercent > batteryState.maximumUsableSoCPercent) return false
        
        return true
    }
    
    /**
     * Checks if the battery is in a safe operational range
     * @param batteryState Battery state to check
     * @return True if battery is operational, false otherwise
     */
    fun isBatteryOperational(
        batteryState: BatteryState
    ): Boolean {
        return batteryState.isOperational && validateBatteryState(batteryState)
    }
    
    /**
     * Calculates the energy required to reach a specific SoC target
     * @param currentBatteryState Current battery state
     * @param targetSoCPercent Target SoC percentage
     * @return Energy needed to reach target (Wh). Negative if recovery is needed.
     */
    fun calculateEnergyToTargetSoC(
        currentBatteryState: BatteryState,
        targetSoCPercent: Double
    ): Double {
        val targetSoEWh = (currentBatteryState.usableCapacityWh.wattHours * (targetSoCPercent / 100.0))
        return targetSoEWh - currentBatteryState.currentSoEWh.wattHours
    }
}