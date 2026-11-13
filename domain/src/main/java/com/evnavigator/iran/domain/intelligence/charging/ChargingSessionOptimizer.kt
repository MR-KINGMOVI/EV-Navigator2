package com.evnavigator.iran.domain.intelligence.charging

import kotlin.math.min

/**
 * A discrete point on a verified DC fast charging curve.
 */
data class ChargingCurvePoint(
    val socPercent: Double,
    val maxAcceptancePowerKw: Double
)

/**
 * Vehicle DC fast charging profile.
 */
data class VehicleChargingProfile(
    val vehicleId: String,
    val batteryCapacityKwh: Double,
    val maxDCPowerKw: Double,
    val verifiedCurve: List<ChargingCurvePoint>? = null // null indicates conservative model fallback
) {
    val hasVerifiedCurve: Boolean get() = !verifiedCurve.isNullOrEmpty()
}

/**
 * Result of charging session optimization.
 */
data class ChargingSessionOptimizationResult(
    val startSocPercent: Double,
    val targetSocPercent: Double,
    val energyAddedKwh: Double,
    val effectiveAveragePowerKw: Double,
    val estimatedDurationMinutes: Double,
    val isTaperingActive: Boolean,
    val isConservativeApproximation: Boolean,
    val recommendationNote: String
)

/**
 * Optimizer that models non-linear charging curves, thermal limits, taper behavior,
 * and session durations.
 */
class ChargingSessionOptimizer {

    /**
     * Compute optimized charging time and energy transfer from startSoc to targetSoc.
     */
    fun optimizeSession(
        profile: VehicleChargingProfile,
        chargerPowerLimitKw: Double,
        startSocPercent: Double,
        targetSocPercent: Double,
        batteryTempCelsius: Double = 25.0
    ): ChargingSessionOptimizationResult {
        require(startSocPercent in 0.0..100.0) { "Start SOC must be in [0, 100]." }
        require(targetSocPercent in 0.0..100.0) { "Target SOC must be in [0, 100]." }
        require(targetSocPercent >= startSocPercent) { "Target SOC cannot be less than start SOC." }

        if (startSocPercent == targetSocPercent) {
            return ChargingSessionOptimizationResult(
                startSocPercent = startSocPercent,
                targetSocPercent = targetSocPercent,
                energyAddedKwh = 0.0,
                effectiveAveragePowerKw = 0.0,
                estimatedDurationMinutes = 0.0,
                isTaperingActive = false,
                isConservativeApproximation = false,
                recommendationNote = "Battery already at or above target SOC."
            )
        }

        val tempDeratingFactor = when {
            batteryTempCelsius < 0.0 -> 0.50 // Severe cold throttling
            batteryTempCelsius < 10.0 -> 0.70 // Cold battery throttling
            batteryTempCelsius < 18.0 -> 0.85 // Sub-optimal temperature
            batteryTempCelsius > 48.0 -> 0.75 // Thermal throttling to prevent overheating
            else -> 1.00 // Optimal operating window
        }

        val stepSoc = 1.0 // 1% SOC integration steps
        var currentSoc = startSocPercent
        var totalMinutes = 0.0
        var totalEnergyWh = 0.0
        var sawTaper = false

        while (currentSoc < targetSocPercent) {
            val nextSoc = min(currentSoc + stepSoc, targetSocPercent)
            val deltaSoc = nextSoc - currentSoc
            val deltaEnergyKwh = (deltaSoc / 100.0) * profile.batteryCapacityKwh

            // Acceptance power at current SOC
            val acceptancePowerKw = getAcceptancePower(profile, currentSoc)
            if (currentSoc >= 80.0) {
                sawTaper = true
            }

            // Power is bottlenecked by: charger limit, vehicle acceptance, and thermal derating
            val effectivePowerKw = min(chargerPowerLimitKw, acceptancePowerKw) * tempDeratingFactor
            val safePowerKw = effectivePowerKw.coerceAtLeast(3.0) // minimum baseline draw

            val hours = deltaEnergyKwh / safePowerKw
            totalMinutes += hours * 60.0
            totalEnergyWh += deltaEnergyKwh * 1000.0

            currentSoc = nextSoc
        }

        val totalEnergyKwh = totalEnergyWh / 1000.0
        val avgPowerKw = if (totalMinutes > 0) {
            val raw = totalEnergyKwh / (totalMinutes / 60.0)
            raw.coerceAtMost(min(chargerPowerLimitKw, profile.maxDCPowerKw) * tempDeratingFactor)
        } else 0.0

        val note = buildString {
            if (targetSocPercent > 80.0) {
                append("Charging above 80% tapers power significantly. Stopping at 80% saves charging time on road trips. ")
            }
            if (tempDeratingFactor < 1.0) {
                append("Charging speed throttled by ${(1.0 - tempDeratingFactor) * 100}% due to battery temperature (${batteryTempCelsius}°C). ")
            }
            if (!profile.hasVerifiedCurve) {
                append("Using conservative automotive taper approximation (verified curve unavailable).")
            }
        }.trim()

        return ChargingSessionOptimizationResult(
            startSocPercent = startSocPercent,
            targetSocPercent = targetSocPercent,
            energyAddedKwh = totalEnergyKwh,
            effectiveAveragePowerKw = avgPowerKw,
            estimatedDurationMinutes = totalMinutes,
            isTaperingActive = sawTaper,
            isConservativeApproximation = !profile.hasVerifiedCurve,
            recommendationNote = note
        )
    }

    private fun getAcceptancePower(profile: VehicleChargingProfile, soc: Double): Double {
        val curve = profile.verifiedCurve
        if (curve != null && curve.isNotEmpty()) {
            // Linear interpolation on verified curve points
            val sorted = curve.sortedBy { it.socPercent }
            return when {
                soc <= sorted.first().socPercent -> sorted.first().maxAcceptancePowerKw
                soc >= sorted.last().socPercent -> sorted.last().maxAcceptancePowerKw
                else -> {
                    val upperIdx = sorted.indexOfFirst { it.socPercent >= soc }
                    val p1 = sorted[upperIdx - 1]
                    val p2 = sorted[upperIdx]
                    val fraction = (soc - p1.socPercent) / (p2.socPercent - p1.socPercent)
                    p1.maxAcceptancePowerKw + fraction * (p2.maxAcceptancePowerKw - p1.maxAcceptancePowerKw)
                }
            }
        }

        // Conservative piecewise approximation for Li-ion automotive fast charging
        val maxPower = profile.maxDCPowerKw
        return when {
            soc <= 50.0 -> maxPower
            soc <= 80.0 -> {
                // Tapers from maxPower down to 45% of maxPower
                val fraction = (soc - 50.0) / 30.0
                maxPower - (fraction * 0.55 * maxPower)
            }
            else -> {
                // Tapers from 45% down to 15% of maxPower
                val fraction = (soc - 80.0) / 20.0
                (0.45 * maxPower) - (fraction * 0.30 * maxPower)
            }
        }
    }
}
