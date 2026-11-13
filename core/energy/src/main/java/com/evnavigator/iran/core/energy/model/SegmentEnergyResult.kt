package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.energy.model.*

/**
 * Represents the energy consumption and recovery for a single route segment
 */
data class SegmentEnergyResult(
    // Segment geometry and identification
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val distanceMeters: Double,
    
    // Speed information  
    val averageSpeedMps: Double,
    val estimatedTravelTimeSeconds: Double,
    
    // Energy components (all in Wh)
    val energyConsumedWh: Double,
    val energyRecoveredWh: Double,
    val netEnergyDeltaWh: Double,
    
    // Detailed energy breakdown
    val rollingResistanceEnergyWh: Double,
    val aerodynamicDragEnergyWh: Double,
    val elevationEnergyWh: Double,
    val accelerationEnergyWh: Double,
    val decelerationEnergyWh: Double,
    val auxiliaryEnergyWh: Double,
    
    // Grade and elevation information
    val gradePercent: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val demUncertaintyWh: Double = 0.0,
    val isGradeUnknown: Boolean = false,
    
    // Battery state after this segment
    val batteryStateAfterSegment: BatteryState,
    
    // Model fidelity and quality
    val modelFidelity: EnergyModelFidelity,
    val dataQuality: DataQuality,
    val totalDemUncertaintyWh: Double = 0.0,
    
    // Warnings about potential issues
    val warnings: List<String> = emptyList()
) {
    companion object {
        /**
         * Creates a segment result with all components set to zero (for invalid segments)
         */
        fun createInvalidSegment(
            startCoordinate: GeoCoordinate,
            endCoordinate: GeoCoordinate,
            modelFidelity: EnergyModelFidelity = EnergyModelFidelity.BASIC,
            dataQuality: DataQuality = DataQuality.UNKNOWN
        ): SegmentEnergyResult {
            return SegmentEnergyResult(
                startCoordinate = startCoordinate,
                endCoordinate = endCoordinate,
                distanceMeters = 0.0,
                averageSpeedMps = 0.0,
                estimatedTravelTimeSeconds = 0.0,
                energyConsumedWh = 0.0,
                energyRecoveredWh = 0.0,
                netEnergyDeltaWh = 0.0,
                rollingResistanceEnergyWh = 0.0,
                aerodynamicDragEnergyWh = 0.0,
                elevationEnergyWh = 0.0,
                accelerationEnergyWh = 0.0,
                decelerationEnergyWh = 0.0,
                auxiliaryEnergyWh = 0.0,
                gradePercent = 0.0,
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                batteryStateAfterSegment = BatteryState(
                    usableCapacityWh = Energy(0.0),
                    currentSoEWh = Energy(0.0),
                    minimumOperationalSoCPercent = 0.0,
                    maximumUsableSoCPercent = 100.0
                ),
                modelFidelity = modelFidelity,
                dataQuality = dataQuality
            )
        }
    }
}

/**
 * Represents the complete route energy calculation result
 */
data class RouteEnergyResult(
    // Route summary
    val totalDistanceMeters: Double,
    val estimatedTotalTravelTimeSeconds: Double,
    
    // Total energy consumption
    val totalEnergyConsumedWh: Double,
    val totalEnergyRecoveredWh: Double,
    val netEnergyDeltaWh: Double,
    
    // Battery state at start and end
    val initialBatteryState: BatteryState,
    val finalBatteryState: BatteryState,
    
    // Route statistics  
    val minimumSoCPercent: Double,
    val maximumSoCPercent: Double,
    val minimumSoEWh: Double,
    val maximumSoEWh: Double,
    
    // Segment-by-segment results
    val segmentResults: List<SegmentEnergyResult>,
    
    // Model fidelity and quality
    val modelFidelity: EnergyModelFidelity,
    val dataQuality: DataQuality,
    val totalDemUncertaintyWh: Double = 0.0,
    
    // Warnings about potential issues
    val warnings: List<String> = emptyList(),
    
    // Route feasibility
    val isReachable: Boolean,
    val reasonForUnreachability: String? = null
)

/**
 * Represents different levels of model fidelity
 */
enum class EnergyModelFidelity {
    BASIC,          // Only baseline consumption curve
    ENHANCED,       // Includes rolling resistance and aerodynamic drag
    FULL_PHYSICS    // Full physics including grade, acceleration, etc.
}

/**
 * Represents data quality for energy calculations
 */
enum class DataQuality {
    HIGH,
    MEDIUM, 
    LOW,
    UNKNOWN
}