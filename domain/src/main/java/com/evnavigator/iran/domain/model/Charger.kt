package com.evnavigator.iran.domain.model

/**
 * Represents an electric vehicle charging station.
 *
 * @param id Unique identifier for the charger
 * @param name Name of the charging station
 * @param location Geographic location of the charger
 * @param connectors List of available charging connectors
 * @param maxPower Maximum power output in kW
 * @param status Current status of the charger
 * @param isPublic Whether the charger is publicly accessible
 * @param provider Provider/owner of the charging station
 */
data class Charger(
    val id: String,
    val name: String,
    val location: GeoCoordinate,
    val connectors: List<ChargingConnector>,
    val maxPower: Double, // in kW
    val status: ChargerStatus,
    val isPublic: Boolean,
    val provider: String?
)

/**
 * Enum representing the status of a charger.
 */
enum class ChargerStatus {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_SERVICE,
    MAINTENANCE,
    UNKNOWN
}