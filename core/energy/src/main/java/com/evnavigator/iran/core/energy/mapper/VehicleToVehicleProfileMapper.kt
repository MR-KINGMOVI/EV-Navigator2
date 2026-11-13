package com.evnavigator.iran.core.energy.mapper

import com.evnavigator.iran.core.vehicle.*

import com.evnavigator.iran.core.energy.model.*

/**
 * Mapper to convert between canonical Vehicle and Energy Model's VehicleProfile.
 *
 * This ensures clean separation between domain models and energy engine requirements
 * while preserving all necessary information for accurate energy calculations.
 */
object VehicleToVehicleProfileMapper {
    
    /**
     * Maps a canonical Vehicle to a VehicleProfile suitable for Phase 5 energy calculations
     *
     * @param vehicle The canonical vehicle specification
     * @param payloadMassKg Optional payload mass (defaults to 0 if not specified)
     * @return A VehicleProfile ready for energy calculations
     */
    fun mapToVehicleProfile(vehicle: Vehicle, payloadMassKg: Double = 0.0): VehicleProfile {
        // Map required fields from Vehicle to VehicleProfile
        return VehicleProfile(
            id = vehicle.id.id,
            name = vehicle.name,
            
            // Physical characteristics  
            curbMassKg = vehicle.curbMassKg,
            payloadMassKg = Mass(payloadMassKg),
            totalOperatingMassKg = vehicle.totalOperatingMass + Mass(payloadMassKg),
            
            // Aerodynamic properties
            frontalAreaM2 = vehicle.frontalAreaM2 ?: 2.2, // Default value if not specified
            dragCoefficient = vehicle.dragCoefficient ?: 0.32, // Default value if not specified
            
            // Rolling resistance  
            rollingResistanceCoefficient = vehicle.rollingResistanceCoefficient ?: 0.012,
            
            // Battery properties
            nominalBatteryCapacityWh = vehicle.nominalBatteryCapacityWh,
            usableBatteryCapacityWh = vehicle.usableBatteryCapacityWh,
            minimumOperationalSoCPercent = 10.0, // Default safe minimum
            maximumUsableSoCPercent = 90.0,      // Default maximum
            
            // Drivetrain efficiency - use defaults as these are complex to derive from basic specs
            drivetrainEfficiency = Efficiency(0.85),        // 85% overall efficiency (default)
            uphillEfficiency = Efficiency(0.75),            // 75% uphill efficiency (default)  
            downhillEfficiency = Efficiency(0.80),          // 80% regen efficiency (default)
            accelerationEfficiency = Efficiency(0.80),      // 80% acceleration efficiency (default)
            decelerationEfficiency = Efficiency(0.90),      // 90% deceleration efficiency (default)
            
            // Speed consumption curve - create a default curve based on vehicle characteristics
            speedConsumptionCurve = createDefaultSpeedConsumptionCurve(vehicle),
            
            // Auxiliary loads - use reasonable defaults
            fixedAuxiliaryPowerW = Power(200.0),           // 200W for HVAC, lights, etc.
            
            // Driving mode settings
            drivingMode = DrivingMode.NORMAL
        )
    }
    
    /**
     * Creates a default speed consumption curve based on vehicle specifications
     *
     * This is a simplified approach that estimates consumption based on basic vehicle characteristics
     */
    private fun createDefaultSpeedConsumptionCurve(vehicle: Vehicle): SpeedConsumptionCurve {
        // Use reasonable defaults for typical electric vehicles
        val points = mutableListOf<SpeedConsumptionPoint>()
        
        // Add some key points to make it representative
        
        // Low speeds - mostly idle and low-speed driving
        points.add(SpeedConsumptionPoint(
            speed = Speed.fromKilometersPerHour(0.0),
            consumptionWhPerKm = 150.0
        ))
        
        // Moderate speeds - typical highway driving  
        points.add(SpeedConsumptionPoint(
            speed = Speed.fromKilometersPerHour(20.0),
            consumptionWhPerKm = 140.0
        ))
        
        // Higher speeds - more aerodynamic drag becomes significant
        points.add(SpeedConsumptionPoint(
            speed = Speed.fromKilometersPerHour(60.0),
            consumptionWhPerKm = 120.0
        ))
        
        // High speeds - maximum drag
        points.add(SpeedConsumptionPoint(
            speed = Speed.fromKilometersPerHour(100.0),
            consumptionWhPerKm = 130.0
        ))
        
        // Very high speeds - aerodynamic penalties at high speeds
        points.add(SpeedConsumptionPoint(
            speed = Speed.fromKilometersPerHour(140.0),
            consumptionWhPerKm = 160.0
        ))
        
        // Extremely high speeds - very inefficient
        points.add(SpeedConsumptionPoint(
            speed = Speed.fromKilometersPerHour(160.0),
            consumptionWhPerKm = 200.0
        ))
        
        return SpeedConsumptionCurve(points)
    }
    
    /**
     * Maps a VehicleProfile back to a Vehicle (reverse mapping for completeness)
     *
     * This is primarily useful for testing and debugging purposes.
     */
    fun mapToVehicle(vehicleProfile: VehicleProfile): Vehicle {
        // Note: This is a simplified reverse mapping as some information may be lost
        // in the conversion process
        
        return Vehicle(
            id = VehicleId(
                manufacturer = "Unknown",  // This information is lost in VehicleProfile
                model = vehicleProfile.name,
                generation = "Unknown",
                modelYear = 2023,
                trim = "Unknown"
            ),
            name = vehicleProfile.name,
            
            // Physical characteristics  
            curbMassKg = vehicleProfile.curbMassKg,
            grossVehicleMassKg = null, // Lost in VehicleProfile
            lengthMm = null,           // Lost in VehicleProfile
            widthMm = null,            // Lost in VehicleProfile 
            heightMm = null,           // Lost in VehicleProfile
            wheelbaseMm = null,        // Lost in VehicleProfile
            groundClearanceMm = null,  // Lost in VehicleProfile
            
            // Aerodynamic properties
            dragCoefficient = vehicleProfile.dragCoefficient,
            frontalAreaM2 = vehicleProfile.frontalAreaM2,
            
            // Rolling resistance  
            rollingResistanceCoefficient = vehicleProfile.rollingResistanceCoefficient,
            
            // Battery properties
            nominalBatteryCapacityWh = vehicleProfile.nominalBatteryCapacityWh,
            usableBatteryCapacityWh = vehicleProfile.usableBatteryCapacityWh,
            batteryChemistry = null,   // Lost in VehicleProfile
            nominalVoltageV = null,    // Lost in VehicleProfile
            batteryConfiguration = null, // Lost in VehicleProfile
            
            // Motor/drivetrain specifications  
            motorCount = 0,            // Lost in VehicleProfile
            frontMotorPowerKw = null,  // Lost in VehicleProfile
            rearMotorPowerKw = null,   // Lost in VehicleProfile
            peakMotorPowerKw = null,   // Lost in VehicleProfile
            continuousMotorPowerKw = null, // Lost in VehicleProfile
            torqueNm = null,           // Lost in VehicleProfile
            drivetrainType = DrivetrainType.FWD, // Default value
            
            // Energy-related parameters for Phase 5 (not used in reverse mapping)
            baselineConsumptionWhPer100Km = null,
            
            // Charging capabilities - not included in VehicleProfile
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            chargePortCount = 0,
            
            // Connector information - not included in VehicleProfile
            connectorTypes = emptySet(),
            
            // Vehicle dimensions and weight for aerodynamics and rolling resistance
            wheelDiameterMm = null,    // Lost in VehicleProfile
            tireWidthMm = null,        // Lost in VehicleProfile
            
            // Data quality and provenance (these are not included in VehicleProfile)
            dataQuality = com.evnavigator.iran.core.vehicle.DataQuality.UNKNOWN,
            source = "Converted from VehicleProfile",
            sourceVersion = "1.0",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.5,          // Default confidence
            validationStatus = ValidationStatus.UNVERIFIED,
            notes = "Converted from VehicleProfile - original vehicle data may be incomplete"
        )
    }
}