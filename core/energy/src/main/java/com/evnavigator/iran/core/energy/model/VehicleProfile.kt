package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.energy.model.*

/**
 * Represents a vehicle energy profile with all necessary parameters for energy calculations
 */
data class VehicleProfile(
    val id: String,
    val name: String,
    
    // Physical characteristics
    val curbMassKg: Mass,                           // Vehicle mass without passengers/load
    val payloadMassKg: Mass,                        // Passenger and cargo load
    val totalOperatingMassKg: Mass,                // Curb mass + payload
    
    // Aerodynamic properties  
    val frontalAreaM2: Double,                      // Frontal area in square meters
    val dragCoefficient: Double,                    // Drag coefficient (Cd)
    
    // Rolling resistance
    val rollingResistanceCoefficient: Double,      // Crr (typically 0.008-0.015 for cars)
    
    // Battery properties
    val nominalBatteryCapacityWh: Energy,          // Total battery capacity in Wh
    val usableBatteryCapacityWh: Energy,           // Usable portion of battery in Wh
    val minimumOperationalSoCPercent: Double,      // Minimum SoC that's safe to operate (0-100)
    val maximumUsableSoCPercent: Double,           // Maximum SoC to avoid overcharging (0-100)
    
    // Drivetrain efficiency
    val drivetrainEfficiency: Efficiency,          // Overall drivetrain efficiency
    val uphillEfficiency: Efficiency,              // Efficiency when going uphill  
    val downhillEfficiency: Efficiency,            // Efficiency when going downhill (regen)
    val accelerationEfficiency: Efficiency,        // Efficiency during acceleration
    val decelerationEfficiency: Efficiency,        // Efficiency during deceleration
    
    // Speed consumption curve - Wh/km at different speeds
    val speedConsumptionCurve: SpeedConsumptionCurve,
    
    // Auxiliary loads
    val fixedAuxiliaryPowerW: Power,               // Fixed auxiliary power in Watts (HVAC, lights, etc.)
    
    // Driving mode settings
    val drivingMode: DrivingMode = DrivingMode.NORMAL
) {
    /**
     * Gets the total operating mass (curb mass + payload)
     */
    val totalOperatingMass: Mass get() = curbMassKg + payloadMassKg
    
    companion object {
        /**
         * Creates a default vehicle profile for testing
         */
        fun createDefaultTestProfile(): VehicleProfile {
            return VehicleProfile(
                id = "default_test_vehicle",
                name = "Default Test Vehicle",
                
                // Physical characteristics  
                curbMassKg = Mass(1500.0),
                payloadMassKg = Mass(200.0), 
                totalOperatingMassKg = Mass(1700.0),
                
                // Aerodynamic properties
                frontalAreaM2 = 2.2,
                dragCoefficient = 0.32,
                
                // Rolling resistance  
                rollingResistanceCoefficient = 0.012,
                
                // Battery properties
                nominalBatteryCapacityWh = Energy(60000.0),    // 60 kWh
                usableBatteryCapacityWh = Energy(54000.0),      // 90% of nominal
                minimumOperationalSoCPercent = 10.0,            // 10% minimum
                maximumUsableSoCPercent = 90.0,                 // 90% maximum
                
                // Drivetrain efficiency
                drivetrainEfficiency = Efficiency(0.85),        // 85% overall
                uphillEfficiency = Efficiency(0.75),            // 75% uphill  
                downhillEfficiency = Efficiency(0.80),          // 80% regen
                accelerationEfficiency = Efficiency(0.80),      // 80% acceleration
                decelerationEfficiency = Efficiency(0.90),      // 90% deceleration
                
                // Speed consumption curve (Wh/km at various speeds)
                speedConsumptionCurve = SpeedConsumptionCurve(
                    points = listOf(
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(0.0), 150.0),    // 0 km/h - idle/stop
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(20.0), 140.0),   // 72 km/h 
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(40.0), 130.0),   // 144 km/h
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(60.0), 120.0),   // 216 km/h
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(80.0), 115.0),   // 288 km/h  
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(100.0), 120.0),  // 360 km/h
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(120.0), 140.0),  // 432 km/h
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(140.0), 170.0),  // 504 km/h
                        SpeedConsumptionPoint(Speed.fromKilometersPerHour(160.0), 200.0)   // 576 km/h
                    )
                ),
                
                // Auxiliary loads
                fixedAuxiliaryPowerW = Power(200.0),           // 200W for HVAC, lights, etc.
                
                // Driving mode settings
                drivingMode = DrivingMode.NORMAL
            )
        }
    }
}

/**
 * Represents different driving modes for energy calculations
 */
enum class DrivingMode {
    ECO,        // Energy efficient mode
    NORMAL,     // Normal driving mode  
    SPORT       // Performance mode
}
