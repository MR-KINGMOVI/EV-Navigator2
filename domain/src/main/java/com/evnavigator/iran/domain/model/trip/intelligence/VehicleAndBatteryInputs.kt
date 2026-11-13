package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.model.Vehicle
import com.evnavigator.iran.domain.model.Battery
import com.evnavigator.iran.domain.model.Soc

/**
 * Encapsulates vehicle and battery related inputs for trip intelligence calculations.
 */
data class VehicleAndBatteryInputs(
    val vehicle: Vehicle? = null,
    val batteryCapacityWh: Double? = null, // Total usable battery capacity in watt-hours
    val currentSoc: Soc? = null,           // State of charge at start of journey
    val minReserveSoc: Double? = null      // Minimum reserve to maintain during trip (in percentage)
)
