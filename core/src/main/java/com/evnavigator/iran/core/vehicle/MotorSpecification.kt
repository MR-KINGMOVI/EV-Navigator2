package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Power

/**
 * Motor specification for electric vehicles.
 */
data class MotorSpecification(
    // Number of motors
    val motorCount: Int,
    
    // Individual motor specifications (if known)
    val frontMotorPowerKw: Power?,  // Front motor power in kW
    val rearMotorPowerKw: Power?,   // Rear motor power in kW
    
    // Overall motor capabilities
    val peakMotorPowerKw: Power?,   // Peak power output in kW  
    val continuousMotorPowerKw: Power?, // Continuous power output in kW
    
    // Torque specifications (if known)
    val torqueNm: Int?, // Maximum torque in Newton-meters
    
    // Drivetrain configuration
    val drivetrainType: DrivetrainType,
    
    // Additional motor characteristics
    val motorEfficiency: Double?, // Efficiency as fraction 0.0-1.0 (if known)
    val motorControlType: MotorControlType? // Type of motor controller
    
) {
    init {
        require(motorCount >= 0) { "Motor count cannot be negative" }
        frontMotorPowerKw?.let { require(it.watts >= 0) { "Front motor power cannot be negative" } }
        rearMotorPowerKw?.let { require(it.watts >= 0) { "Rear motor power cannot be negative" } }
        peakMotorPowerKw?.let { require(it.watts >= 0) { "Peak motor power cannot be negative" } }
        continuousMotorPowerKw?.let { require(it.watts >= 0) { "Continuous motor power cannot be negative" } }
        torqueNm?.let { require(it >= 0) { "Torque cannot be negative" } }
        motorEfficiency?.let { require(it >= 0.0 && it <= 1.0) { "Motor efficiency must be between 0.0 and 1.0" } }
    }
    
    /**
     * Gets total motor power (sum of all motors)
     */
    val totalMotorPowerKw: Power
        get() {
            val front = frontMotorPowerKw?.watts ?: 0.0
            val rear = rearMotorPowerKw?.watts ?: 0.0
            return Power(front + rear)
        }
    
    /**
     * Determines if this is a dual-motor configuration
     */
    val isDualMotor: Boolean
        get() = motorCount >= 2 || (frontMotorPowerKw != null && rearMotorPowerKw != null)
    
    /**
     * Gets the primary motor power (front if available, otherwise total)
     */
    val primaryMotorPowerKw: Power
        get() {
            return frontMotorPowerKw ?: totalMotorPowerKw
        }
}

/**
 * Represents different types of motor control systems
 */
enum class MotorControlType {
    VSI,         // Variable Speed Inverter
    VFD,         // Variable Frequency Drive  
    DC_SERVO,    // DC Servo Control
    AC_INVERTER, // AC Inverter Control
    OTHER        // Other unspecified motor control
}