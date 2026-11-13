package com.evnavigator.iran.domain.model

/**
 * Represents an electric vehicle.
 *
 * @param id Unique identifier for the vehicle
 * @param make Manufacturer of the vehicle
 * @param model Model name of the vehicle
 * @param year Year of manufacture
 * @param batteryCapacity Battery capacity in kWh
 * @param maxChargingPower Maximum charging power in kW
 * @param efficiency Efficiency in kWh per 100 km
 * @param weight Weight of the vehicle in kg
 */
data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    val year: Int,
    val batteryCapacity: Double, // in kWh
    val maxChargingPower: Double, // in kW
    val efficiency: Double, // in kWh per 100 km
    val weight: Double // in kg
)