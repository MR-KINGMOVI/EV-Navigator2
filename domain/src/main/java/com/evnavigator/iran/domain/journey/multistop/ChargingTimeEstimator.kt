package com.evnavigator.iran.domain.journey.multistop

import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine

/**
 * DC/AC charging time estimator delegating to [CanonicalEnergyEngine.ChargeCurveEngine]
 * with separate DC CC-CV power taper vs AC constant-power curves, vehicle AC/DC power cap,
 * and station plug-in / handshake overhead time (V13.3.1 Section 12).
 *
 * V13.3.1 Invariant: Accepts nullable [ambientTempC] (`Double? = null`) so missing ambient
 * temperature is passed as `null` to [CanonicalEnergyEngine.ChargeCurveEngine] (which flags
 * `isDeterministic = false` and widens thermal uncertainty bounds) rather than pretending `15.0°C`
 * was measured.
 */
class ChargingTimeEstimator {

    fun estimateChargingMinutes(
        arrivalSocPercent: Double,
        targetSocPercent: Double,
        batteryCapacityKwh: Double,
        maxStationPowerKw: Double,
        maxVehiclePowerKw: Double? = null,
        includeHandshakeOverhead: Boolean = true,
        ambientTempC: Double? = null,
        isDcFast: Boolean = true
    ): Double = minutes(
        startSocPercent = arrivalSocPercent,
        endSocPercent = targetSocPercent,
        peakKw = maxStationPowerKw,
        batteryKwh = batteryCapacityKwh,
        vehicleMaxChargeKw = maxVehiclePowerKw,
        plugHandshakeOverheadMinutes = if (includeHandshakeOverhead) DEFAULT_PLUG_HANDSHAKE_OVERHEAD_MINUTES else 0.0,
        ambientTempC = ambientTempC,
        isDcFast = isDcFast
    )

    companion object {
        const val DEFAULT_PLUG_HANDSHAKE_OVERHEAD_MINUTES = 3.0

        fun effectivePowerKw(
            socPercent: Double,
            peakKw: Double,
            vehicleMaxChargeKw: Double? = null,
            ambientTempC: Double? = null,
            isDcFast: Boolean = true
        ): Double {
            val cappedPeakKw = if (vehicleMaxChargeKw != null && vehicleMaxChargeKw > 0.0) {
                minOf(peakKw, vehicleMaxChargeKw)
            } else {
                peakKw
            }
            if (!cappedPeakKw.isFinite() || cappedPeakKw <= 0.0) return 0.0
            val thermalFactor = when {
                ambientTempC == null || !ambientTempC.isFinite() -> 1.0
                !isDcFast -> if (ambientTempC < -5.0) 0.92 else 1.0
                ambientTempC < 0.0 -> 0.62
                ambientTempC < 10.0 -> 0.80
                ambientTempC > 38.0 -> 0.85
                else -> 1.0
            }
            val rawPower = if (isDcFast) {
                when {
                    socPercent < 0.0 || socPercent > 100.0 -> 0.0
                    socPercent <= 50.0 -> cappedPeakKw
                    socPercent <= 80.0 -> cappedPeakKw * (1.0 - 0.50 * (socPercent - 50.0) / 30.0)
                    else -> (cappedPeakKw * (1.0 - 0.50 - 0.35 * (socPercent - 80.0) / 20.0)).coerceAtLeast(cappedPeakKw * 0.15)
                }
            } else {
                when {
                    socPercent < 0.0 || socPercent > 100.0 -> 0.0
                    socPercent <= 94.0 -> cappedPeakKw * 0.98
                    else -> cappedPeakKw * 0.58
                }
            }
            return rawPower * thermalFactor
        }

        fun minutes(
            startSocPercent: Double,
            endSocPercent: Double,
            peakKw: Double,
            batteryKwh: Double,
            efficiency: Double = 0.92,
            vehicleMaxChargeKw: Double? = null,
            plugHandshakeOverheadMinutes: Double = 0.0,
            ambientTempC: Double? = null,
            isDcFast: Boolean = true
        ): Double {
            if (!startSocPercent.isFinite() || !endSocPercent.isFinite() || !peakKw.isFinite() || !batteryKwh.isFinite()) {
                return 0.0
            }
            val end = endSocPercent.coerceIn(0.0, 100.0)
            val start = startSocPercent.coerceIn(0.0, 100.0)
            val cappedPeakKw = if (vehicleMaxChargeKw != null && vehicleMaxChargeKw > 0.0) {
                minOf(peakKw, vehicleMaxChargeKw)
            } else {
                peakKw
            }
            if (cappedPeakKw <= 0.0 || batteryKwh <= 0.0 || start >= end) return 0.0
            val sim = CanonicalEnergyEngine.ChargeCurveEngine.simulateChargingSessionOrNull(
                usableBatteryKwh = batteryKwh,
                arrivalSocPercent = start,
                targetSocPercent = end,
                effectivePeakPowerKw = cappedPeakKw * (efficiency.coerceIn(0.5, 1.0) / 0.92),
                isDcFast = isDcFast,
                ambientTemperatureCelsius = ambientTempC
            ) ?: return 0.0
            return sim.chargingDuration.toMinutes() + plugHandshakeOverheadMinutes.coerceAtLeast(0.0)
        }
    }
}
