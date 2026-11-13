package com.evnavigator.iran.domain.model

/**
 * Represents a charging stop during a trip.
 *
 * @param id Unique identifier for the charging stop
 * @param charger Reference to the charger used
 * @param startTime Start time of the charging session
 * @param endTime End time of the charging session
 * @param energyAdded Energy added during this stop in kWh
 * @param duration Duration of the charging session in seconds
 * @param startBatteryState Battery state at the beginning of the stop
 * @param endBatteryState Battery state at the end of the stop
 * @param connectorType Type of connector used for charging
 */
data class ChargingStop(
    val id: String,
    val charger: Charger,
    val startTime: Long, // Unix timestamp
    val endTime: Long, // Unix timestamp
    val energyAdded: Double, // in kWh
    val duration: Long, // in seconds
    val startBatteryState: Battery,
    val endBatteryState: Battery,
    val connectorType: ConnectorType
)