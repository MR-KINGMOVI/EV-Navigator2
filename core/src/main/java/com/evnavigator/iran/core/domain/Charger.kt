package com.evnavigator.iran.core.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents an electric vehicle charging station.
 *
 * @param id Unique identifier for the charger
 * @param name Name of the charging station
 * @param location Geographic location of the charger
 * @param connectors List of available charging connectors
 * @param provider Provider/Operator of the charging station
 * @param isAvailable Whether the charger is currently available for use
 * @param costPerKWh Cost per kilowatt-hour in local currency
 */
@Parcelize
data class Charger(
    val id: String,
    val name: String,
    val location: GeoCoordinate,
    val connectors: List<ChargingConnector>,
    val provider: String,
    val isAvailable: Boolean = true,
    val costPerKWh: Double = 0.0
) : Parcelable {
    
    init {
        require(connectors.isNotEmpty()) { "Charger must have at least one connector" }
        require(costPerKWh >= 0) { "Cost per kWh cannot be negative" }
    }

    /**
     * Gets the total number of connectors
     */
    val totalConnectors: Int
        get() = connectors.size

    /**
     * Gets the number of available connectors
     */
    val availableConnectors: Int
        get() = connectors.count { it.isAvailable }

    /**
     * Checks if this charger supports fast charging (power > 50kW)
     */
    val isFastCharger: Boolean
        get() = connectors.any { it.powerKw > 50.0 }
}