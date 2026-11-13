package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.trip.intelligence.TripIntelligenceRequest
import com.evnavigator.iran.domain.policy.DiagnosticCode
import com.evnavigator.iran.domain.policy.EnergyCalculationMode
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Per-segment physical longitudinal vehicle dynamics calculator for EV route legs (V13.3 Sections 4, 5 & 13).
 *
 * V13.3 Invariants:
 * - Never clamps invalid/zero `batteryCapacityKwh` to `5.0 kWh`; requires finite positive usable capacity.
 * - Never clamps `totalExpectedWh` to `10.0 Wh`; preserves genuine zero-distance (`0.0 Wh`) and net-regenerative
 *   downhill (`< 0.0 Wh`) energy deltas.
 */
object SegmentPhysicsEnergyCalculator {

    private const val GRAVITY = 9.80665
    private const val SEA_LEVEL_AIR_DENSITY = 1.225

    class StrictEnergyCalculationException(
        val missingParameters: List<String>,
        val diagnostics: List<DiagnosticCode>
    ) : IllegalStateException(
        "STRICT energy calculation rejected missing/unverified critical parameters: ${missingParameters.joinToString(", ")}"
    )

    data class SegmentKineticProfile(
        val segmentIndex: Int,
        val startDistanceMeters: Double,
        val endDistanceMeters: Double,
        val lengthMeters: Double,
        val gradePercent: Double,
        val isGradeKnown: Boolean,
        val altitudeMeters: Double?,
        val isAltitudeKnown: Boolean,
        val speedKmh: Double,
        val speedOrigin: InputOrigin,
        val roadSurfaceFactor: Double = 1.0,
        val trafficStopGoFactor: Double = 0.0,
        val legalSpeedLimitKmh: Int? = null
    )

    data class LegEnergySimulationResult(
        val startDistanceMeters: Double,
        val endDistanceMeters: Double,
        val totalDistanceMeters: Double,
        val durationSeconds: Double,
        val minEnergyWh: Double,
        val expectedEnergyWh: Double,
        val maxEnergyWh: Double,
        val regeneratedWh: Double,
        val auxiliaryHvacWh: Double,
        val startSocPercent: Double,
        val arrivalSocNominalPercent: Double,
        val arrivalSocMinPercent: Double,
        val arrivalSocMaxPercent: Double,
        val usedEstimatedVehiclePhysics: Boolean,
        val usedEstimatedSpeed: Boolean,
        val usedEstimatedElevation: Boolean,
        val assumptions: List<String>,
        val diagnosticCodes: List<DiagnosticCode> = emptyList(),
        val confidenceCeiling: Double = 1.0,
        val calculationMode: EnergyCalculationMode = EnergyCalculationMode.ESTIMATE_WITH_WARNING
    ) {
        val expectedEnergyKwh: Double get() = expectedEnergyWh / 1000.0
        val minEnergyKwh: Double get() = minEnergyWh / 1000.0
        val maxEnergyKwh: Double get() = maxEnergyWh / 1000.0
    }

    fun resolveRouteReferenceSpeed(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?,
        routeDistanceMeters: Double
    ): Pair<Double, InputOrigin> =
        SegmentKineticProfileBuilder.resolveRouteReferenceSpeed(request, context, routeDistanceMeters)

    fun buildExplicitContext(
        request: TripIntelligenceRequest,
        catalogVehicle: DomainVehicle?,
        batteryCapKwh: Double,
        currentSoc: Double,
        isSocVerified: Boolean,
        minReserveSoc: Double,
        routeDistanceMeters: Double,
        nowMs: Long
    ): TripIntelligenceContext = SegmentKineticProfileBuilder.buildExplicitContext(
        request, catalogVehicle, batteryCapKwh, currentSoc, isSocVerified, minReserveSoc, routeDistanceMeters, nowMs
    )

    fun buildRouteSegmentProfiles(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?,
        routePolyline: List<GeoCoordinate>,
        routeDistanceMeters: Double
    ): List<SegmentKineticProfile> =
        SegmentKineticProfileBuilder.buildRouteSegmentProfiles(request, context, routePolyline, routeDistanceMeters)

    fun simulateLegEnergyStrict(
        profiles: List<SegmentKineticProfile>, legStartMeters: Double, legEndMeters: Double,
        extraDetourMeters: Double, departureSocPercent: Double, batteryCapacityKwh: Double, context: TripIntelligenceContext
    ): Result<LegEnergySimulationResult> {
        val missing = mutableListOf<String>()
        val diag = mutableListOf<DiagnosticCode>()
        if (context.vehicleId.isBlank() || context.vehicleId == "assumed_fallback") { missing.add("vehicleId"); diag.add(DiagnosticCode.MISSING_VEHICLE_ID) }
        val usableAttr = if (context.usableBatteryCapacityKwh.isPresent) context.usableBatteryCapacityKwh else context.batteryCapacityKwh
        if (!usableAttr.isAuthoritative() || !batteryCapacityKwh.isFinite() || batteryCapacityKwh <= 0.0) {
            missing.add("usableBatteryCapacityKwh"); diag.add(DiagnosticCode.ASSUMED_BATTERY_CAPACITY)
        }
        if (!context.currentSocPercent.isAuthoritative()) { missing.add("currentSocPercent"); diag.add(DiagnosticCode.ASSUMED_INITIAL_SOC) }
        if (!context.curbWeightKg.isAuthoritative() || (context.totalMassKg ?: 0.0) <= 400.0) { missing.add("curbWeightKg"); diag.add(DiagnosticCode.ASSUMED_MASS) }
        if (missing.isNotEmpty()) return Result.failure(StrictEnergyCalculationException(missing, diag))
        return Result.success(simulateLegEnergy(profiles, legStartMeters, legEndMeters, extraDetourMeters, departureSocPercent, batteryCapacityKwh, context, EnergyCalculationMode.STRICT))
    }

    fun simulateLegEnergy(
        profiles: List<SegmentKineticProfile>, legStartMeters: Double, legEndMeters: Double,
        extraDetourMeters: Double, departureSocPercent: Double, batteryCapacityKwh: Double,
        context: TripIntelligenceContext, mode: EnergyCalculationMode = EnergyCalculationMode.ESTIMATE_WITH_WARNING
    ): LegEnergySimulationResult {
        val usableKwh = batteryCapacityKwh.takeIf { it.isFinite() && it > 0.0 }
            ?: context.usableBatteryCapacityKwh.value?.takeIf { it.isFinite() && it > 0.0 }
            ?: context.batteryCapacityKwh.value?.takeIf { it.isFinite() && it > 0.0 }
            ?: throw IllegalArgumentException("usableBatteryCapacityKwh ($batteryCapacityKwh) must be positive and finite; silent 5.0 kWh clamp is forbidden.")
        val usableBatteryWh = usableKwh * 1000.0

        val assumptions = LinkedHashSet<String>()
        val diagnostics = LinkedHashSet<DiagnosticCode>()
        if (context.vehicleId.isBlank() || context.vehicleId == "assumed_fallback") { assumptions.add("MISSING_VEHICLE_ID"); diagnostics.add(DiagnosticCode.MISSING_VEHICLE_ID) }
        if (!context.batteryCapacityKwh.isAuthoritative() && !context.usableBatteryCapacityKwh.isAuthoritative()) {
            assumptions.add("ASSUMED_BATTERY_CAPACITY_KWH"); diagnostics.add(DiagnosticCode.ASSUMED_BATTERY_CAPACITY)
        }
        if (!context.currentSocPercent.isAuthoritative()) { assumptions.add("ASSUMED_INITIAL_SOC"); diagnostics.add(DiagnosticCode.ASSUMED_INITIAL_SOC) }
        val massKg = context.totalMassKg?.takeIf { it.isFinite() && it > 400.0 } ?: 1950.0
        if (!context.curbWeightKg.isAuthoritative() || context.totalMassKg == null) { assumptions.add("ESTIMATED_VEHICLE_MASS_KG"); diagnostics.add(DiagnosticCode.ASSUMED_MASS) }
        val cd = context.dragCoefficient.value?.takeIf { it.isFinite() && it in 0.15..0.65 } ?: 0.28
        if (!context.dragCoefficient.isAuthoritative()) { assumptions.add("ESTIMATED_DRAG_COEFFICIENT"); diagnostics.add(DiagnosticCode.ASSUMED_DRAG_COEFF) }
        val frontalArea = context.frontalAreaM2.value?.takeIf { it.isFinite() && it in 1.2..4.5 } ?: 2.35
        if (!context.frontalAreaM2.isAuthoritative()) { assumptions.add("ESTIMATED_FRONTAL_AREA_M2"); diagnostics.add(DiagnosticCode.ASSUMED_FRONTAL_AREA) }
        val crr = context.rollingResistanceCoeff.value?.takeIf { it.isFinite() && it in 0.005..0.030 } ?: 0.010
        if (!context.rollingResistanceCoeff.isAuthoritative()) { assumptions.add("ESTIMATED_ROLLING_RESISTANCE"); diagnostics.add(DiagnosticCode.ASSUMED_ROLLING_RESISTANCE) }

        val driveEff = (context.driveEfficiency.value?.takeIf { it.isFinite() } ?: 0.91).coerceIn(0.75, 0.97)
        val rawAmbientTemp = context.ambientTemperatureCelsius.value?.takeIf { it.isFinite() && it in -60.0..70.0 }
        val ambientTempC = rawAmbientTemp ?: EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS
        if (rawAmbientTemp == null || !context.ambientTemperatureCelsius.isAuthoritative()) {
            assumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE"); assumptions.add("ASSUMED_AMBIENT_TEMPERATURE"); diagnostics.add(DiagnosticCode.UNKNOWN_WEATHER)
        }

        val windSpeedMps = context.windSpeedMps.value?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val headwindComponentMps = if (context.routeHeadingDegrees != null && context.windDirectionDegrees.value != null) {
            windSpeedMps * cos(Math.toRadians(context.windDirectionDegrees.value - context.routeHeadingDegrees))
        } else 0.0
        val hvacKw = context.hvacPowerKw.value?.takeIf { it.isFinite() && it >= 0.0 } ?: estimateAuxiliaryAndHvacKw(ambientTempC)
        val coldResistanceMultiplier = when { ambientTempC >= 15.0 -> 1.00; ambientTempC >= 0.0 -> 1.00 + (15.0 - ambientTempC) * 0.005; else -> 1.075 + abs(ambientTempC) * 0.010 }
        val coldRegenDerating = when { ambientTempC >= 10.0 -> 1.00; ambientTempC >= 0.0 -> 0.75; ambientTempC >= -10.0 -> 0.45; else -> 0.25 }

        var runningSoc = departureSocPercent.coerceIn(0.0, 100.0)
        var totalExpectedWh = 0.0; var totalMinWh = 0.0; var totalMaxWh = 0.0
        var totalRegenWh = 0.0; var totalAuxWh = 0.0; var totalDurationSec = 0.0
        var usedEstimatedSpeed = false; var usedEstimatedElevation = false
        val usedEstimatedVehiclePhysics = !context.curbWeightKg.isAuthoritative() || !context.dragCoefficient.isAuthoritative() ||
            !context.frontalAreaM2.isAuthoritative() || !context.rollingResistanceCoeff.isAuthoritative()

        for (seg in profiles) {
            val overlapStart = max(legStartMeters, seg.startDistanceMeters)
            val overlapEnd = min(legEndMeters, seg.endDistanceMeters)
            if (overlapEnd <= overlapStart) continue
            val subLenM = overlapEnd - overlapStart
            if (seg.speedOrigin == InputOrigin.ESTIMATED_MODEL || seg.speedOrigin == InputOrigin.ASSUMED_DEFAULT) {
                usedEstimatedSpeed = true; assumptions.add("ESTIMATED_SEGMENT_SPEED"); diagnostics.add(DiagnosticCode.ASSUMED_SPEED)
            } else if (seg.speedOrigin == InputOrigin.DERIVED_FROM_ROUTE_TIME) {
                usedEstimatedSpeed = true; assumptions.add("DERIVED_SPEED_FROM_ROUTE_TIME"); diagnostics.add(DiagnosticCode.DERIVED_SPEED_FROM_DURATION)
            }
            if (!seg.isGradeKnown || !seg.isAltitudeKnown) {
                usedEstimatedElevation = true; assumptions.add("MISSING_SEGMENT_ELEVATION_PROFILE"); diagnostics.add(DiagnosticCode.UNKNOWN_ELEVATION_PROFILE)
                if (!seg.isGradeKnown) diagnostics.add(DiagnosticCode.UNKNOWN_GRADE)
            }

            val spdMps = (seg.speedKmh / 3.6).coerceAtLeast(1.39)
            val segDurationSec = subLenM / spdMps
            totalDurationSec += segDurationSec
            val theta = atan(seg.gradePercent / 100.0)
            val airDensity = SEA_LEVEL_AIR_DENSITY * exp(-(seg.altitudeMeters ?: 0.0) / 8500.0) * (288.15 / (ambientTempC + 273.15).coerceAtLeast(220.0))
            val effectiveAirSpeedMps = (spdMps + headwindComponentMps).coerceAtLeast(0.0)

            val fGradeN = massKg * GRAVITY * sin(theta)
            val fRollN = massKg * GRAVITY * crr * cos(theta) * seg.roadSurfaceFactor
            val fAeroN = 0.5 * airDensity * cd * frontalArea * effectiveAirSpeedMps.pow(2)
            val fStopGoN = massKg * (0.08 * seg.trafficStopGoFactor)
            val wheelWorkWh = ((fGradeN + fRollN + fAeroN + fStopGoN) * subLenM) / 3600.0

            val socRegenLimit = when { runningSoc >= 98.0 -> 0.0; runningSoc >= 85.0 -> (98.0 - runningSoc) / 13.0; else -> 1.0 }
            val effectiveRegenEff = (0.72 * coldRegenDerating * socRegenLimit).coerceIn(0.0, 0.75)
            val segTractionOrRegenWh = if (wheelWorkWh >= 0.0) (wheelWorkWh / driveEff) * coldResistanceMultiplier
            else { val rec = abs(wheelWorkWh) * effectiveRegenEff; totalRegenWh += rec; -rec }

            val segAuxWh = hvacKw * 1000.0 * (segDurationSec / 3600.0)
            totalAuxWh += segAuxWh
            val segExpectedWh = (segTractionOrRegenWh + segAuxWh).coerceAtLeast(-usableBatteryWh * 0.25)

            var segUncertaintyFrac = 0.05
            if (usedEstimatedVehiclePhysics) segUncertaintyFrac += 0.06
            if (!seg.isGradeKnown) segUncertaintyFrac += 0.06
            if (!seg.isAltitudeKnown) segUncertaintyFrac += 0.03
            if (seg.speedOrigin == InputOrigin.ESTIMATED_MODEL || seg.speedOrigin == InputOrigin.DERIVED_FROM_ROUTE_TIME) segUncertaintyFrac += 0.04
            if (!context.ambientTemperatureCelsius.isAuthoritative()) segUncertaintyFrac += 0.03

            val segDeltaWh = max(abs(segExpectedWh), subLenM * 0.05) * segUncertaintyFrac
            totalExpectedWh += segExpectedWh; totalMinWh += (segExpectedWh - segDeltaWh * 0.65); totalMaxWh += (segExpectedWh + segDeltaWh)
            runningSoc = (runningSoc - (segExpectedWh / usableBatteryWh) * 100.0).coerceIn(0.0, 100.0)
        }

        if (extraDetourMeters > 0.0) {
            val detourSpdMps = EvDomainPolicyConfig.ARTERIAL_REFERENCE_SPEED_KMH / 3.6
            val detourSec = extraDetourMeters / detourSpdMps
            totalDurationSec += detourSec
            val fRollDetour = massKg * GRAVITY * crr
            val fAeroDetour = 0.5 * SEA_LEVEL_AIR_DENSITY * cd * frontalArea * detourSpdMps.pow(2)
            val detourTractionWh = (((fRollDetour + fAeroDetour) * extraDetourMeters) / 3600.0) / driveEff * coldResistanceMultiplier
            val detourAuxWh = hvacKw * 1000.0 * (detourSec / 3600.0)
            val detourWh = detourTractionWh + detourAuxWh
            totalAuxWh += detourAuxWh; totalExpectedWh += detourWh; totalMinWh += detourWh * 0.90; totalMaxWh += detourWh * 1.15
        }

        val safeExpectedWh = totalExpectedWh
        val safeMinWh = min(totalMinWh, safeExpectedWh)
        val safeMaxWh = max(totalMaxWh, safeExpectedWh)
        val arrNominal = (departureSocPercent - (safeExpectedWh / usableBatteryWh) * 100.0).coerceIn(-50.0, 100.0)
        val arrMin = (departureSocPercent - (safeMaxWh / usableBatteryWh) * 100.0).coerceIn(-50.0, 100.0)
        val arrMax = (departureSocPercent - (safeMinWh / usableBatteryWh) * 100.0).coerceIn(-50.0, 100.0)
        val diagCap = diagnostics.minOfOrNull { it.confidenceCeiling } ?: 1.0
        val finalConfidenceCeiling = min(diagCap, context.criticalInputMinConfidence.coerceIn(0.0, 1.0))

        return LegEnergySimulationResult(
            legStartMeters, legEndMeters,
            (legEndMeters - legStartMeters).coerceAtLeast(0.0) + extraDetourMeters.coerceAtLeast(0.0),
            totalDurationSec, safeMinWh, safeExpectedWh, safeMaxWh, totalRegenWh, totalAuxWh,
            departureSocPercent, arrNominal, arrMin, arrMax,
            usedEstimatedVehiclePhysics, usedEstimatedSpeed, usedEstimatedElevation,
            assumptions.toList(), diagnostics.toList(), finalConfidenceCeiling, mode
        )
    }

    private fun estimateAuxiliaryAndHvacKw(ambientTempC: Double): Double = when {
        ambientTempC < -5.0 -> 2.4; ambientTempC < 5.0 -> 1.6; ambientTempC < 15.0 -> 0.9
        ambientTempC <= 26.0 -> 0.45; ambientTempC <= 35.0 -> 1.3; else -> 2.1
    }
}
