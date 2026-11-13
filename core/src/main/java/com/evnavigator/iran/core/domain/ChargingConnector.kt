package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a charging connector at a charging station.
 *
 * @param type Type of charging connector (e.g., CCS, CHAdeMO, Tesla)
 * @param powerKw Power output in kilowatts
 * @param voltageV Voltage in volts
 * @param amperageA Amperage in amps
 * @param isAvailable Whether the connector is currently available for use
 */
@Parcelize
data class ChargingConnector(
    val type: ConnectorType,
    val powerKw: Double,
    val voltageV: Int,
    val amperageA: Int,
    val isAvailable: Boolean = true
) : Parcelable {
    
    init {
        require(powerKw > 0) { "Power must be positive" }
        require(voltageV > 0) { "Voltage must be positive" }
        require(amperageA > 0) { "Amperage must be positive" }
    }

    /**
     * Calculates the charging current in amperes
     */
    val chargingCurrentA: Double
        get() = powerKw * 1000.0 / voltageV


}

/**
 * Enum representing different types of charging connectors
 */
enum class ConnectorType {
    CCS,          // Combined Charging System
    CHAdeMO,      // Japanese fast charging standard
    TESLA,        // Tesla proprietary connector
    TYPE_1,       // Type 1 (AC only)
    TYPE_2,       // Type 2 (AC/DC)
    GB_T,         // German standard (CCS2)
    DOMESTIC     // Domestic socket
}