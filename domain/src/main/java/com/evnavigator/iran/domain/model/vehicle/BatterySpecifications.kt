package com.evnavigator.iran.domain.model.vehicle

/**
 * Represents battery capacity specifications for a vehicle.
 *
 * This model captures both gross and usable battery capacities,
 * which are critical parameters for energy consumption calculations.
 */
data class BatterySpecifications(
    /**
     * Gross battery capacity in kWh (nominal full charge)
     */
    val grossKwh: Double? = null,

    /**
     * Usable battery capacity in kWh (practical available capacity)
     */
    val usableKwh: Double? = null,

    /**
     * Voltage level of the battery pack
     */
    val voltage: Double? = null,

    /**
     * Nominal cell voltage for battery chemistry identification
     */
    val nominalCellVoltage: Double? = null,

    /**
     * Battery chemistry type (e.g., LFP, NCM, NCA)
     */
    val chemistry: String? = null,

    /**
     * Number of cells in series connection
     */
    val cellsInSeries: Int? = null,

    /**
     * Number of battery modules or packs
     */
    val numberOfModules: Int? = null,

    /**
     * Maximum continuous discharge power (kW)
     */
    val maxDischargePowerKw: Double? = null,

    /**
     * Maximum charging power supported (kW)
     */
    val maxChargePowerKw: Double? = null,

    /**
     * Efficiency factor for charge/discharge cycles
     */
    val efficiencyFactor: Double? = null,

    /**
     * State of Charge (SOC) at which battery is considered full (0.0 to 1.0)
     */
    val socFullChargeThreshold: Double? = null,

    /**
     * State of Charge (SOC) at which charging should be stopped for safety
     */
    val socChargingLimit: Double? = null,

    /**
     * Temperature range in Celsius where battery is safe to operate
     */
    val operatingTemperatureRangeCelsius: Pair<Double, Double>? = null,

    /**
     * Battery weight in kilograms (including pack and cooling system)
     */
    val weightKg: Double? = null,

    /**
     * Volume of the battery pack in liters or cubic decimeters
     */
    val volumeLiters: Double? = null,

    /**
     * Cycle life - number of charge/discharge cycles before capacity degrades significantly
     */
    val cycleLife: Int? = null,

    /**
     * Expected degradation rate per year (as percentage)
     */
    val annualDegradationRatePercent: Double? = null,

    /**
     * Warranty period or coverage information
     */
    val warrantyPeriodYears: Double? = null,
    
    /**
     * Warranty mileage or charge cycles if applicable
     */
    val warrantyMileageKm: Long? = null,

    /**
     * Manufacturer's recommended maintenance schedule for battery
     */
    val maintenanceSchedule: String? = null,

    /**
     * Any additional properties specific to this implementation
     */
    val additionalProperties: Map<String, Any>? = null
) {
    companion object {
        fun create(
            grossKwh: Double? = null,
            usableKwh: Double? = null,
            voltage: Double? = null,
            nominalCellVoltage: Double? = null,
            chemistry: String? = null,
            cellsInSeries: Int? = null,
            numberOfModules: Int? = null,
            maxDischargePowerKw: Double? = null,
            maxChargePowerKw: Double? = null,
            efficiencyFactor: Double? = null,
            socFullChargeThreshold: Double? = null,
            socChargingLimit: Double? = null,
            operatingTemperatureRangeCelsius: Pair<Double, Double>? = null,
            weightKg: Double? = null,
            volumeLiters: Double? = null,
            cycleLife: Int? = null,
            annualDegradationRatePercent: Double? = null,
            warrantyPeriodYears: Double? = null,
            warrantyMileageKm: Long? = null,
            maintenanceSchedule: String? = null,
            additionalProperties: Map<String, Any>? = null
        ): BatterySpecifications {
            return BatterySpecifications(
                grossKwh = grossKwh,
                usableKwh = usableKwh,
                voltage = voltage,
                nominalCellVoltage = nominalCellVoltage,
                chemistry = chemistry,
                cellsInSeries = cellsInSeries,
                numberOfModules = numberOfModules,
                maxDischargePowerKw = maxDischargePowerKw,
                maxChargePowerKw = maxChargePowerKw,
                efficiencyFactor = efficiencyFactor,
                socFullChargeThreshold = socFullChargeThreshold,
                socChargingLimit = socChargingLimit,
                operatingTemperatureRangeCelsius = operatingTemperatureRangeCelsius,
                weightKg = weightKg,
                volumeLiters = volumeLiters,
                cycleLife = cycleLife,
                annualDegradationRatePercent = annualDegradationRatePercent,
                warrantyPeriodYears = warrantyPeriodYears,
                warrantyMileageKm = warrantyMileageKm,
                maintenanceSchedule = maintenanceSchedule,
                additionalProperties = additionalProperties
            )
        }
    }

    /**
     * Calculates the usable capacity in kWh (if gross is known and there's a percentage).
     */
    fun getUsableCapacity(): Double? {
        return if (this.usableKwh != null) {
            this.usableKwh
        } else if (this.grossKwh != null && this.usableKwh == null) {
            // Estimate usable capacity as 90% of gross for demonstration purposes
            this.grossKwh * 0.9
        } else {
            null
        }
    }

    /**
     * Checks if battery specifications are valid.
     */
    fun isValid(): Boolean {
        return (grossKwh != null && grossKwh > 0) || 
               (usableKwh != null && usableKwh > 0)
    }
}