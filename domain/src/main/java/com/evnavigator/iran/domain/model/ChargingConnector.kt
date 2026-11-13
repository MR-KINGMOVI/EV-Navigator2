package com.evnavigator.iran.domain.model

/**
 * Represents a charging connector at a charging station.
 *
 * @param type Type of the charging connector
 * @param power Power output in kW
 * @param voltage Voltage in volts
 * @param current Current in amps
 * @param isAvailable Whether the connector is currently available
 */
data class ChargingConnector(
    val type: ConnectorType,
    val power: Double, // in kW
    val voltage: Double, // in volts
    val current: Double, // in amps
    val isAvailable: Boolean
)

/**
 * Enum representing different types of charging connectors.
 */
enum class ConnectorType {
    CCS_2,          // Combined Charging System 2
    CHAdeMO,        // CHAdeMO standard
    TYPE_1,         // Type 1 (SAE J1772)
    TYPE_2,         // Type 2 (IEC 62196)
    TYPE_3,         // Type 3 (IEC 62196)
    DOMESTIC_A,     // Domestic A (120V)
    DOMESTIC_B,     // Domestic B (240V)
    TESLA_Supercharger,
    TESLA_FCC,
    GB_T,           // GB/T 20234 AC/DC standard
    UNKNOWN         // Unknown or unverified connector
}