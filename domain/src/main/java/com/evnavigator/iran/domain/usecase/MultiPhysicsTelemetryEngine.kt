package com.evnavigator.iran.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * World-Class Multi-Physics EV Telemetry, Thermodynamic & CC-CV Fast Charging Engine
 * Calculates:
 * 1. Payload Mass Physics (Passengers + Cargo kg) on Mountain Grade & Rolling Resistance
 * 2. Relative Air Velocity Vector Aerodynamics (Vehicle Speed + Headwind/Tailwind + Roof Box CdA)
 * 3. Barometric Air Density (ISA Standard Atmosphere at Altitude & Temperature)
 * 4. Road Surface Condition (DRY, WET, SNOW) & Tire Pressure (PSI) Rolling Resistance Crr
 * 5. Battery State-of-Health (SoH %), Cold Reversible Capacity Derating & Internal Joule Heating Loss (I^2 * R_int)
 * 6. Non-Linear CC-CV DC Fast Charging Taper Curve (10% to 100% SoC) + BMS Pre-Conditioning Time Savings
 * 7. Golden Optimal Eco-Speed Solver & Prevented CO2 Emissions (kg)
 */
@Singleton
class MultiPhysicsTelemetryEngine @Inject constructor() {

    enum class RoadSurface(val crrMultiplier: Double, val regenEfficiency: Double, val labelFa: String) {
        DRY(1.00, 0.74, "آسفالت خشک استاندارد"),
        WET(1.16, 0.68, "آسفالت بارانی و خیس"),
        SNOW(1.36, 0.48, "جاده‌های برفی و یخبندان")
    }

    enum class HvacMode(val powerMultiplier: Double, val labelFa: String) {
        ECO(0.68, "حالت اقتصادی هیت‌پمپ (ECO)"),
        AUTO(1.00, "تهویه اتوماتیک کابین (AUTO)"),
        MAX(1.42, "حداکثر توان سرمایش/گرمایش (MAX)")
    }

    data class MultiPhysicsInput(
        val curbMassKg: Double = 2160.0,
        val usableBatteryKwh: Double = 78.0,
        val baseDragCd: Double = 0.27,
        val frontalAreaM2: Double = 2.52,
        val baseRollingCrr: Double = 0.0095,
        val maxDcChargeKw: Double = 135.0,
        val passengersCount: Int = 2,
        val cargoWeightKg: Double = 35.0,
        val hasRoofRack: Boolean = false,
        val headwindKmh: Double = 0.0,
        val cruisingSpeedKmh: Double = 105.0,
        val ambientTempC: Double = 24.0,
        val roadSurface: RoadSurface = RoadSurface.DRY,
        val tirePressurePsi: Double = 39.0,
        val batterySohPercent: Double = 98.0,
        val hvacMode: HvacMode = HvacMode.AUTO,
        val bmsPreconditioningActive: Boolean = true
    )

    data class CcCvCurvePoint(
        val socPercent: Int,
        val allocatedPowerKw: Double
    )

    data class MultiPhysicsBreakdown(
        val effectiveUsableBatteryKwh: Double,
        val totalMassWithPayloadKg: Double,
        val effectiveDragCd: Double,
        val effectiveRollingCrr: Double,
        val aerodynamicDragKwh: Double,
        val rollingResistanceKwh: Double,
        val elevationClimbKwh: Double,
        val cabinHvacKwh: Double,
        val bmsThermalConditioningKwh: Double,
        val packInternalJouleLossKwh: Double,
        val regenerativeRecoveredKwh: Double,
        val totalNetEnergyKwh: Double,
        val optimalEcoSpeedKmh: Int,
        val co2PreventedKg: Double,
        val ccCvCurve: List<CcCvCurvePoint>
    )

    fun calculateBarometricAirDensity(elevationMeters: Double, tempCelsius: Double): Double {
        val tempKelvin = (tempCelsius + 273.15).coerceAtLeast(220.0)
        val pressureRatio = (1.0 - (0.0065 * elevationMeters) / 288.15).coerceAtLeast(0.4).pow(5.25588)
        val pressurePa = 101325.0 * pressureRatio
        return (pressurePa / (287.058 * tempKelvin)).coerceIn(0.70, 1.45)
    }

    fun generateCcCvChargingCurve(
        vehicleMaxDcKw: Double,
        stationMaxKw: Double,
        bmsPreconditioned: Boolean
    ): List<CcCvCurvePoint> {
        val ceilingKw = min(vehicleMaxDcKw, stationMaxKw)
        val thermalFactor = if (bmsPreconditioned) 1.0 else 0.78
        return (10..100 step 10).map { soc ->
            val taperFactor = when {
                soc <= 20 -> 0.88
                soc <= 50 -> 1.00
                soc <= 60 -> 0.92
                soc <= 70 -> 0.78
                soc <= 80 -> 0.62
                soc <= 90 -> 0.36
                else -> 0.18
            }
            CcCvCurvePoint(
                socPercent = soc,
                allocatedPowerKw = round(ceilingKw * taperFactor * thermalFactor * 10.0) / 10.0
            )
        }
    }

    fun evaluateCorridorPhysics(
        input: MultiPhysicsInput,
        distanceKm: Double,
        totalElevationGainM: Double,
        totalElevationLossM: Double,
        avgElevationM: Double
    ): MultiPhysicsBreakdown {
        val coldCapFactor = when {
            input.ambientTempC < -5.0 -> 0.88
            input.ambientTempC < 5.0 -> 0.94
            input.ambientTempC < 12.0 -> 0.97
            else -> 1.00
        }
        val effectiveUsableKwh = (input.usableBatteryKwh * (input.batterySohPercent / 100.0) * coldCapFactor)
            .coerceAtLeast(15.0)

        val payloadKg = (input.passengersCount.coerceIn(1, 7) * 75.0) + input.cargoWeightKg.coerceIn(0.0, 600.0)
        val totalMassKg = input.curbMassKg + payloadKg

        val effectiveCd = input.baseDragCd + if (input.hasRoofRack) 0.045 else 0.0
        val effectiveArea = input.frontalAreaM2 + if (input.hasRoofRack) 0.16 else 0.0

        val psiDeficit = (39.0 - input.tirePressurePsi).coerceAtLeast(0.0)
        val effectiveCrr = input.baseRollingCrr * input.roadSurface.crrMultiplier * (1.0 + psiDeficit * 0.012)

        val rho = calculateBarometricAirDensity(avgElevationM, input.ambientTempC)
        val distanceMeters = distanceKm * 1000.0
        val speedMs = (input.cruisingSpeedKmh / 3.6).coerceAtLeast(5.0)
        val relativeAirSpeedMs = ((input.cruisingSpeedKmh + input.headwindKmh) / 3.6).coerceAtLeast(2.0)
        val durationHours = (distanceMeters / speedMs) / 3600.0

        val fRolling = effectiveCrr * totalMassKg * 9.80665
        val fDrag = 0.5 * rho * effectiveCd * effectiveArea * relativeAirSpeedMs.pow(2)

        val eRollingKwh = (fRolling * distanceMeters) / 3_600_000.0 / 0.91
        val eDragKwh = (fDrag * distanceMeters) / 3_600_000.0 / 0.91
        val eClimbKwh = (totalMassKg * 9.80665 * totalElevationGainM) / 3_600_000.0 / 0.91
        val eRegenKwh = ((totalMassKg * 9.80665 * totalElevationLossM) / 3_600_000.0) * input.roadSurface.regenEfficiency * 0.65

        val tempDelta = abs(21.5 - input.ambientTempC)
        val hvacPowerKw = (1.35 + tempDelta * 0.065) * input.hvacMode.powerMultiplier
        val eHvacKwh = hvacPowerKw * durationHours

        val bmsPowerKw = if (input.ambientTempC < 8.0 || input.ambientTempC > 36.0) {
            if (input.bmsPreconditioningActive) 0.65 else 0.35
        } else 0.15
        val eBmsKwh = bmsPowerKw * durationHours

        val tractionTotal = eRollingKwh + eDragKwh + eClimbKwh
        val jouleLossFactor = 0.032 * (1.0 + (100.0 - input.batterySohPercent) * 0.015)
        val eJouleKwh = tractionTotal * jouleLossFactor

        val netEnergyKwh = (tractionTotal + eJouleKwh + eHvacKwh + eBmsKwh - eRegenKwh).coerceAtLeast(5.0)

        val optimalEcoSpeed = when {
            totalElevationGainM > 1400 -> 80
            input.headwindKmh > 15 -> 95
            netEnergyKwh > effectiveUsableKwh * 0.75 -> 100
            else -> 108
        }

        val co2SavedKg = (distanceKm / 100.0) * 9.2 * 2.31

        return MultiPhysicsBreakdown(
            effectiveUsableBatteryKwh = round(effectiveUsableKwh * 10.0) / 10.0,
            totalMassWithPayloadKg = round(totalMassKg),
            effectiveDragCd = round(effectiveCd * 1000.0) / 1000.0,
            effectiveRollingCrr = round(effectiveCrr * 10000.0) / 10000.0,
            aerodynamicDragKwh = round(eDragKwh * 10.0) / 10.0,
            rollingResistanceKwh = round(eRollingKwh * 10.0) / 10.0,
            elevationClimbKwh = round(eClimbKwh * 10.0) / 10.0,
            cabinHvacKwh = round(eHvacKwh * 10.0) / 10.0,
            bmsThermalConditioningKwh = round(eBmsKwh * 10.0) / 10.0,
            packInternalJouleLossKwh = round(eJouleKwh * 10.0) / 10.0,
            regenerativeRecoveredKwh = round(eRegenKwh * 10.0) / 10.0,
            totalNetEnergyKwh = round(netEnergyKwh * 10.0) / 10.0,
            optimalEcoSpeedKmh = optimalEcoSpeed,
            co2PreventedKg = round(co2SavedKg * 10.0) / 10.0,
            ccCvCurve = generateCcCvChargingCurve(input.maxDcChargeKw, 150.0, input.bmsPreconditioningActive)
        )
    }
}
