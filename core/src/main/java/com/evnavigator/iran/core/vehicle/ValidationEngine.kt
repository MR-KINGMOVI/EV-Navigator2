package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.core.energy.model.Efficiency

/**
 * Validation engine for vehicle specifications.
 *
 * Provides deterministic validation rules for vehicle data with clear error categorization.
 */
object ValidationEngine {
    
    /**
     * Validates a complete vehicle specification and returns validation results
     */
    fun validateVehicle(vehicle: Vehicle): ValidationResult {
        require(vehicle.confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        require(vehicle.confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        val errors = mutableListOf<ValidationMessage>()
        val warnings = mutableListOf<ValidationMessage>()
        
        // Validate battery specifications
        validateBatterySpecifications(vehicle, errors, warnings)
        
        // Validate mass specifications  
        validateMassSpecifications(vehicle, errors, warnings)
        
        // Validate dimensions
        validateDimensions(vehicle, errors, warnings)
        
        // Validate motor specifications
        validateMotorSpecifications(vehicle, errors, warnings)
        
        // Validate charging capabilities
        validateChargingSpecifications(vehicle, errors, warnings)
        
        // Validate aerodynamic parameters
        validateAerodynamicParameters(vehicle, errors, warnings)
        
        // Validate rolling resistance
        validateRollingResistance(vehicle, errors, warnings)
        
        // Determine overall validation status
        val status = when {
            errors.isNotEmpty() -> ValidationStatus.INVALID
            warnings.isNotEmpty() -> ValidationStatus.PARTIALLY_VALIDATED
            else -> ValidationStatus.VALIDATED
        }
        
        return ValidationResult(
            status = status,
            errors = errors,
            warnings = warnings,
            vehicleId = vehicle.id.id
        )
    }
    
    /**
     * Validates battery specifications
     */
    private fun validateBatterySpecifications(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        // Validate that capacities are positive and reasonable
        if (vehicle.nominalBatteryCapacityWh.wattHours <= 0) {
            errors.add(ValidationMessage(
                type = ValidationMessageType.ERROR,
                field = "nominalBatteryCapacityWh",
                message = "Nominal battery capacity must be positive"
            ))
        }
        
        if (vehicle.usableBatteryCapacityWh.wattHours <= 0) {
            errors.add(ValidationMessage(
                type = ValidationMessageType.ERROR,
                field = "usableBatteryCapacityWh", 
                message = "Usable battery capacity must be positive"
            ))
        }
        
        // Validate that usable capacity doesn't exceed nominal
        if (vehicle.usableBatteryCapacityWh.wattHours > vehicle.nominalBatteryCapacityWh.wattHours) {
            errors.add(ValidationMessage(
                type = ValidationMessageType.ERROR,
                field = "usableBatteryCapacityWh",
                message = "Usable capacity cannot exceed nominal capacity"
            ))
        }
        
        // Validate battery chemistry (if provided)
        if (vehicle.batteryChemistry != null && vehicle.batteryChemistry.isBlank()) {
            warnings.add(ValidationMessage(
                type = ValidationMessageType.WARNING,
                field = "batteryChemistry",
                message = "Battery chemistry is blank but provided"
            ))
        }
    }
    
    /**
     * Validates mass specifications
     */
    private fun validateMassSpecifications(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        if (vehicle.curbMassKg.kilograms <= 0) {
            errors.add(ValidationMessage(
                type = ValidationMessageType.ERROR,
                field = "curbMassKg",
                message = "Curb mass must be positive"
            ))
        }
        
        vehicle.grossVehicleMassKg?.let { grossMass ->
            if (grossMass.kilograms <= 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "grossVehicleMassKg",
                    message = "Gross vehicle mass must be positive"
                ))
            }
            
            // Validate that gross mass is not less than curb mass
            if (grossMass.kilograms < vehicle.curbMassKg.kilograms) {
                warnings.add(ValidationMessage(
                    type = ValidationMessageType.WARNING,
                    field = "grossVehicleMassKg",
                    message = "Gross vehicle mass is less than curb mass"
                ))
            }
        }
    }
    
    /**
     * Validates dimensions
     */
    private fun validateDimensions(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        // Validate physical dimensions (if provided)
        vehicle.lengthMm?.let { length ->
            if (length <= 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "lengthMm",
                    message = "Length must be positive"
                ))
            }
        }
        
        vehicle.widthMm?.let { width ->
            if (width <= 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "widthMm",
                    message = "Width must be positive"
                ))
            }
        }
        
        vehicle.heightMm?.let { height ->
            if (height <= 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "heightMm",
                    message = "Height must be positive"
                ))
            }
        }
        
        vehicle.wheelbaseMm?.let { wheelbase ->
            if (wheelbase <= 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "wheelbaseMm",
                    message = "Wheelbase must be positive"
                ))
            }
        }
    }
    
    /**
     * Validates motor specifications
     */
    private fun validateMotorSpecifications(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        if (vehicle.motorCount < 0) {
            errors.add(ValidationMessage(
                type = ValidationMessageType.ERROR,
                field = "motorCount",
                message = "Motor count cannot be negative"
            ))
        }
        
        // Validate individual motor powers
        vehicle.frontMotorPowerKw?.let { frontPower ->
            if (frontPower.watts < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "frontMotorPowerKw",
                    message = "Front motor power cannot be negative"
                ))
            }
        }
        
        vehicle.rearMotorPowerKw?.let { rearPower ->
            if (rearPower.watts < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "rearMotorPowerKw",
                    message = "Rear motor power cannot be negative"
                ))
            }
        }
        
        vehicle.peakMotorPowerKw?.let { peakPower ->
            if (peakPower.watts < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "peakMotorPowerKw",
                    message = "Peak motor power cannot be negative"
                ))
            }
        }
        
        vehicle.continuousMotorPowerKw?.let { continuousPower ->
            if (continuousPower.watts < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "continuousMotorPowerKw",
                    message = "Continuous motor power cannot be negative"
                ))
            }
        }
        
        vehicle.torqueNm?.let { torque ->
            if (torque < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "torqueNm",
                    message = "Torque cannot be negative"
                ))
            }
        }
    }
    
    /**
     * Validates charging specifications
     */
    private fun validateChargingSpecifications(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        // Validate charging powers (if provided)
        vehicle.acChargingMaxPowerKw?.let { acPower ->
            if (acPower.watts < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "acChargingMaxPowerKw",
                    message = "AC charging power cannot be negative"
                ))
            }
        }
        
        vehicle.dcChargingMaxPowerKw?.let { dcPower ->
            if (dcPower.watts < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "dcChargingMaxPowerKw",
                    message = "DC charging power cannot be negative"
                ))
            }
        }
        
        // Validate connector types
        if (vehicle.connectorTypes.isEmpty()) {
            warnings.add(ValidationMessage(
                type = ValidationMessageType.WARNING,
                field = "connectorTypes",
                message = "No connector types specified"
            ))
        }
    }
    
    /**
     * Validates aerodynamic parameters
     */
    private fun validateAerodynamicParameters(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        if (vehicle.dragCoefficient == null) {
            warnings.add(ValidationMessage(
                type = ValidationMessageType.WARNING,
                field = "dragCoefficient",
                message = "Drag coefficient is missing"
            ))
        }
        vehicle.dragCoefficient?.let { dragCoeff ->
            if (dragCoeff < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "dragCoefficient",
                    message = "Drag coefficient cannot be negative"
                ))
            }
        }
        
        vehicle.frontalAreaM2?.let { frontalArea ->
            if (frontalArea < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "frontalAreaM2",
                    message = "Frontal area cannot be negative"
                ))
            }
        }
    }
    
    /**
     * Validates rolling resistance parameters
     */
    private fun validateRollingResistance(vehicle: Vehicle, errors: MutableList<ValidationMessage>, warnings: MutableList<ValidationMessage>) {
        if (vehicle.rollingResistanceCoefficient == null) {
            warnings.add(ValidationMessage(
                type = ValidationMessageType.WARNING,
                field = "rollingResistanceCoefficient",
                message = "Rolling resistance coefficient is missing"
            ))
        }
        vehicle.rollingResistanceCoefficient?.let { crr ->
            if (crr < 0) {
                errors.add(ValidationMessage(
                    type = ValidationMessageType.ERROR,
                    field = "rollingResistanceCoefficient",
                    message = "Rolling resistance coefficient cannot be negative"
                ))
            }
        }
    }
}

/**
 * Represents a validation result
 */
data class ValidationResult(
    val status: ValidationStatus,
    val errors: List<ValidationMessage>,
    val warnings: List<ValidationMessage>,
    val vehicleId: String
)

/**
 * Represents a single validation message
 */
data class ValidationMessage(
    val type: ValidationMessageType,
    val field: String,
    val message: String
)

/**
 * Represents the type of validation message
 */
enum class ValidationMessageType {
    ERROR,
    WARNING
}