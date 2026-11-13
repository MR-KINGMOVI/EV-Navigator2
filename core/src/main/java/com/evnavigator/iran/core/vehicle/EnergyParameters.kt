package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.core.energy.model.Efficiency

/**
 * Energy-related parameters extracted for Phase 5 energy engine consumption.
 *
 * This represents the minimal set of vehicle parameters that Phase 5 needs
 * to perform accurate energy calculations.
 */
data class EnergyParameters(
    // Mass parameters (needed for rolling resistance, elevation, acceleration)
    val totalOperatingMassKg: Mass,
    
    // Aerodynamic parameters (needed for drag calculation)  
    val dragCoefficient: Double?,
    val frontalAreaM2: Double?,
    
    // Rolling resistance coefficient (if known)
    val rollingResistanceCoefficient: Double?,
    
    // Battery parameters (needed for SoE calculations)
    val usableBatteryCapacityWh: Energy,
    
    // Auxiliary power consumption (needed for baseline consumption)
    val fixedAuxiliaryPowerW: Power,
    
    // Drivetrain efficiency parameters (for energy accounting)
    val drivetrainEfficiency: Efficiency?,
    val uphillEfficiency: Efficiency?,
    val downhillEfficiency: Efficiency?,
    val accelerationEfficiency: Efficiency?,
    val decelerationEfficiency: Efficiency?,
    
    // Speed consumption curve (if available) - for baseline consumption
    val baselineConsumptionWhPer100Km: Double?
) {
    init {
        require(totalOperatingMassKg.kilograms > 0) { "Total operating mass must be positive" }
        require(usableBatteryCapacityWh.wattHours > 0) { "Usable battery capacity must be positive" }
        dragCoefficient?.let { require(it >= 0.0) { "Drag coefficient cannot be negative" } }
        frontalAreaM2?.let { require(it >= 0.0) { "Frontal area cannot be negative" } }
        rollingResistanceCoefficient?.let { require(it >= 0.0) { "Rolling resistance coefficient cannot be negative" } }
        require(fixedAuxiliaryPowerW.watts >= 0) { "Fixed auxiliary power cannot be negative" }
        
        // Validate efficiency values
        drivetrainEfficiency?.let { require(it.fraction >= 0.0 && it.fraction <= 1.0) { "Drivetrain efficiency must be between 0.0 and 1.0" } }
        uphillEfficiency?.let { require(it.fraction >= 0.0 && it.fraction <= 1.0) { "Uphill efficiency must be between 0.0 and 1.0" } }
        downhillEfficiency?.let { require(it.fraction >= 0.0 && it.fraction <= 1.0) { "Downhill efficiency must be between 0.0 and 1.0" } }
        accelerationEfficiency?.let { require(it.fraction >= 0.0 && it.fraction <= 1.0) { "Acceleration efficiency must be between 0.0 and 1.0" } }
        decelerationEfficiency?.let { require(it.fraction >= 0.0 && it.fraction <= 1.0) { "Deceleration efficiency must be between 0.0 and 1.0" } }
    }
    
    /**
     * Creates a minimal set of energy parameters for basic calculations
     */
    companion object {
        fun createMinimal(
            totalOperatingMassKg: Mass,
            usableBatteryCapacityWh: Energy,
            fixedAuxiliaryPowerW: Power = Power(200.0) // Default 200W auxiliary power
        ): EnergyParameters {
            return EnergyParameters(
                totalOperatingMassKg = totalOperatingMassKg,
                dragCoefficient = null,
                frontalAreaM2 = null,
                rollingResistanceCoefficient = null,
                usableBatteryCapacityWh = usableBatteryCapacityWh,
                fixedAuxiliaryPowerW = fixedAuxiliaryPowerW,
                drivetrainEfficiency = null,
                uphillEfficiency = null,
                downhillEfficiency = null,
                accelerationEfficiency = null,
                decelerationEfficiency = null,
                baselineConsumptionWhPer100Km = null
            )
        }
    }
}