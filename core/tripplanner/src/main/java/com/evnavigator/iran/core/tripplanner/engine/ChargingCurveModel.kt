package com.evnavigator.iran.core.tripplanner.engine

/**
 * Models non-linear DC fast-charging speed.
 * 
 * DC fast charging is NOT linear - charging power stays near-maximum until 
 * roughly 50-60% SOC, then tapers significantly toward 80-100% to protect the battery.
 */
object ChargingCurveModel {
    /**
     * Returns the effective charging power in kW at a given SOC, given the
     * station's max power and the vehicle's max DC charging power.
     * Implements a realistic automotive taper:
     *  - 0% to 50% SOC: full min(stationMaxKw, vehicleMaxKw)
     *  - 50% to 80% SOC: linear taper down to 50% of peak
     *  - 80% to 100% SOC: linear taper down to 15% of peak (trickle balancing)
     */
    fun effectivePowerKw(
        socPercent: Double,
        stationMaxPowerKw: Double,
        vehicleMaxChargingPowerKw: Double
    ): Double {
        val maxPower = minOf(stationMaxPowerKw, vehicleMaxChargingPowerKw).coerceAtLeast(0.0)
        if (maxPower <= 0.0) return 0.0
        
        return when {
            socPercent < 0.0 || socPercent > 100.0 -> 0.0
            socPercent <= 50.0 -> maxPower
            socPercent <= 80.0 -> maxPower * (1.0 - 0.50 * (socPercent - 50.0) / 30.0)
            else -> (maxPower * (0.50 - 0.35 * (socPercent - 80.0) / 20.0)).coerceAtLeast(maxPower * 0.15)
        }
    }

    /**
     * Integrates effectivePowerKw over the SOC range [startSocPercent, endSocPercent]
     * to estimate charging duration in minutes, given the vehicle's usable battery
     * capacity in kWh.
     *
     * Dimensional derivation:
     *   delta_Energy_kWh = usableBatteryCapacityKwh * (delta_SOC / 100)
     *   delta_time_hours = delta_Energy_kWh / (effectivePowerKw * chargingEfficiency)
     *   delta_time_minutes = delta_time_hours * 60.0
     *
     * @param chargingEfficiency Accounting for thermal dissipation, cable resistance, and cooling pump overhead (default 92%)
     */
    fun estimateChargingDurationMinutes(
        startSocPercent: Double,
        endSocPercent: Double,
        stationMaxPowerKw: Double,
        vehicleMaxChargingPowerKw: Double,
        usableBatteryCapacityKwh: Double
    ): Double {
        if (startSocPercent.isNaN() || endSocPercent.isNaN()) return 0.0
        val start = startSocPercent.coerceIn(0.0, 100.0)
        val end = endSocPercent.coerceIn(0.0, 100.0)
        if (start >= end || usableBatteryCapacityKwh <= 0.0) return 0.0
        var hours = 0.0
        var soc = start
        while (soc < end) {
            val slice = minOf(1.0, end - soc)                  // % SOC
            val mid = soc + slice / 2.0
            val p = effectivePowerKw(mid, stationMaxPowerKw, vehicleMaxChargingPowerKw).coerceAtLeast(1.0)
            hours += usableBatteryCapacityKwh * slice / 100.0 / p   // kWh / kW = h
            soc += slice
        }
        return hours * 60.0
    }
}
