package com.evnavigator.iran.domain.intelligence.energy

import com.evnavigator.iran.domain.intelligence.trip.ParameterProvenanceStatus
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Complete physical energy breakdown required by V13.3.1 Section 3.
 *
 * Exposes all 9 physical loss/recovery components:
 * - [rollingResistanceWh]
 * - [aerodynamicDragWh]
 * - [gradePotentialWh]
 * - [accelerationKineticWh]
 * - [drivetrainLossesWh]
 * - [hvacAuxiliaryWh]
 * - [batteryInternalLossesWh]
 * - [chargingLossesWh]
 * - [regenerativeEnergyWh]
 */
data class EnergyBreakdown(
    val rollingResistanceWh: Double,
    val aerodynamicDragWh: Double,
    val gradePotentialWh: Double,
    val accelerationKineticWh: Double,
    val drivetrainLossesWh: Double,
    val hvacAuxiliaryWh: Double,
    val batteryInternalLossesWh: Double,
    val chargingLossesWh: Double = 0.0,
    val regenerativeEnergyWh: Double
) {
    val grossMechanicalTractionWh: Double
        get() = max(0.0, rollingResistanceWh + aerodynamicDragWh + gradePotentialWh + accelerationKineticWh)

    val netEnergyWh: Double
        get() = rollingResistanceWh +
            aerodynamicDragWh +
            max(0.0, gradePotentialWh) +
            accelerationKineticWh +
            drivetrainLossesWh +
            hvacAuxiliaryWh +
            batteryInternalLossesWh -
            regenerativeEnergyWh
}

/**
 * Per-segment input for [EnergyCalculationRequest] (V13.3.1 Section 3).
 */
data class ReferenceEnergySegmentInput(
    val segmentIndex: Int = 0,
    val distanceMeters: Double,
    val startElevationMeters: Double? = null,
    val endElevationMeters: Double? = null,
    val explicitGradePercent: Double? = null,
    val legalSpeedLimitKmh: Double? = null,
    val targetSpeedKmh: Double? = null,
    val observedSpeedKmh: Double? = null,
    val entrySpeedKmh: Double? = null,
    val exitSpeedKmh: Double? = null,
    val roadSurfaceFactor: Double = 1.0,
    val trafficStopGoFactor: Double = 0.0
)

/**
 * Canonical input contract for the single reference energy engine (V13.3.1 Section 3).
 */
data class EnergyCalculationRequest(
    val segments: List<ReferenceEnergySegmentInput>,
    val usableBatteryKwh: Double,
    val grossBatteryKwh: Double? = null,
    val departureSocPercent: Double,
    val minReserveSocPercent: Double = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
    val isDepartureSocVerified: Boolean = true,
    val vehicleMassKg: Double,
    val isMassVerified: Boolean = true,
    val dragCoefficient: Double,
    val isDragVerified: Boolean = true,
    val frontalAreaM2: Double,
    val isFrontalAreaVerified: Boolean = true,
    val rollingResistanceCoeff: Double,
    val isRollingResistanceVerified: Boolean = true,
    val drivetrainEfficiency: Double = 0.90,
    val regenSystemEfficiency: Double = 0.78,
    val regenBrakingSplitFactor: Double = 0.85,
    val maxMotorRegenPowerKw: Double = 120.0,
    val maxBatteryAcceptancePowerKw: Double = 110.0,
    val ambientTemperatureCelsius: Double? = null,
    val isTemperatureAuthoritative: Boolean = false,
    val windSpeedMps: Double = 0.0,
    val windFromDegrees: Double? = null,
    val routeHeadingDegrees: Double? = null,
    val explicitHvacPowerKw: Double? = null,
    val isHvacAuthoritative: Boolean = false,
    val chargingEnergyAddedKwh: Double = 0.0,
    val isDcCharging: Boolean = true
)

/**
 * Per-segment physical energy result in [EnergyCalculationResult].
 */
data class ReferenceSegmentEnergyResult(
    val segmentIndex: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val effectiveSpeedKmh: Double,
    val legalSpeedLimitKmh: Double?,
    val gradePercent: Double,
    val isElevationKnown: Boolean,
    val breakdown: EnergyBreakdown,
    val startSocPercent: Double,
    val endSocPercent: Double
)

/**
 * Canonical output contract of the single reference energy engine (V13.3.1 Section 3).
 */
data class EnergyCalculationResult(
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val breakdown: EnergyBreakdown,
    val segments: List<ReferenceSegmentEnergyResult>,
    val departureSocPercent: Double,
    val arrivalSocNominalPercent: Double,
    val arrivalSocLowerBoundPercent: Double,
    val arrivalSocUpperBoundPercent: Double,
    val expectedEnergyWh: Double,
    val lowerBoundEnergyWh: Double,
    val upperBoundEnergyWh: Double,
    val uncertaintyPercent: Double,
    val confidenceScore: Double,
    val dataQuality: String,
    val isFeasibleWithoutCharging: Boolean,
    val parameterProvenance: Map<String, ParameterProvenanceStatus>,
    val assumptions: List<String>,
    val warnings: List<String>
) {
    val expectedEnergyKwh: Double get() = expectedEnergyWh / 1000.0
}

/**
 * Single Unified Reference Longitudinal Dynamics & Battery Energy Calculator (V13.3.1 Section 3).
 *
 * Enforces physical conservation laws and regeneration limits:
 * 1. Rolling Resistance: `F_roll = m * g * C_rr * cos(theta) * surfaceFactor`
 * 2. Aerodynamic Drag: `F_aero = 0.5 * rho(alt, T) * C_d * A * v_air^2`
 * 3. Grade Potential: `F_grade = m * g * sin(theta)`
 * 4. Kinetic Acceleration + Stop/Go: `E_accel = 0.5 * m * max(0, v_exit^2 - v_entry^2) + stopGoWork`
 * 5. Drivetrain & Internal Battery Losses separated explicitly
 * 6. Regenerative Braking constrained by:
 *    - Mechanical/hydraulic braking split (`regenBrakingSplitFactor`)
 *    - System efficiency (`regenSystemEfficiency`)
 *    - Motor peak regeneration power (`maxMotorRegenPowerKw`)
 *    - Battery charge acceptance power (`maxBatteryAcceptancePowerKw`)
 *    - High-SOC taper ceiling (`0%` at `SOC >= 98%`, linear taper from `85%..98%`)
 *    - Cold-temperature lithium plating derating (`ambientTemperatureCelsius`)
 */
object UnifiedReferenceEnergyCalculator {

    private const val GRAVITY = 9.80665

    fun calculate(request: EnergyCalculationRequest): EnergyCalculationResult {
        require(request.usableBatteryKwh.isFinite() && request.usableBatteryKwh > 0.0) {
            "usableBatteryKwh (${request.usableBatteryKwh}) must be positive and finite"
        }
        require(request.departureSocPercent.isFinite() && request.departureSocPercent in 0.0..100.0) {
            "departureSocPercent (${request.departureSocPercent}) must be within 0..100"
        }
        require(request.vehicleMassKg.isFinite() && request.vehicleMassKg in 400.0..12_000.0) {
            "vehicleMassKg (${request.vehicleMassKg}) must be within physical bounds [400, 12000]"
        }

        val usableBatteryWh = request.usableBatteryKwh * 1000.0
        val assumptions = LinkedHashSet<String>()
        val warnings = LinkedHashSet<String>()
        val provenance = LinkedHashMap<String, ParameterProvenanceStatus>()

        provenance["usableBatteryKwh"] = ParameterProvenanceStatus.VERIFIED
        provenance["departureSocPercent"] = if (request.isDepartureSocVerified) {
            ParameterProvenanceStatus.USER_PROVIDED
        } else {
            assumptions.add("UNVERIFIED_DEFAULT_DEPARTURE_SOC")
            ParameterProvenanceStatus.ESTIMATED
        }
        provenance["vehicleMassKg"] = if (request.isMassVerified) ParameterProvenanceStatus.VERIFIED else {
            assumptions.add("ESTIMATED_VEHICLE_MASS_KG")
            ParameterProvenanceStatus.ESTIMATED
        }
        provenance["dragCoefficient"] = if (request.isDragVerified) ParameterProvenanceStatus.VERIFIED else {
            assumptions.add("ESTIMATED_DRAG_COEFFICIENT")
            ParameterProvenanceStatus.ESTIMATED
        }
        provenance["frontalAreaM2"] = if (request.isFrontalAreaVerified) ParameterProvenanceStatus.VERIFIED else {
            assumptions.add("ESTIMATED_FRONTAL_AREA_M2")
            ParameterProvenanceStatus.ESTIMATED
        }
        provenance["rollingResistanceCoeff"] = if (request.isRollingResistanceVerified) ParameterProvenanceStatus.VERIFIED else {
            assumptions.add("ESTIMATED_ROLLING_RESISTANCE")
            ParameterProvenanceStatus.ESTIMATED
        }

        val effectiveTempC = if (request.ambientTemperatureCelsius != null &&
            request.ambientTemperatureCelsius.isFinite() &&
            request.ambientTemperatureCelsius in -60.0..70.0
        ) {
            provenance["ambientTemperature"] = if (request.isTemperatureAuthoritative) {
                ParameterProvenanceStatus.VERIFIED
            } else {
                assumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE")
                ParameterProvenanceStatus.ESTIMATED
            }
            request.ambientTemperatureCelsius
        } else {
            assumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE")
            provenance["ambientTemperature"] = ParameterProvenanceStatus.UNKNOWN
            EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS
        }

        val hvacKw = if (request.explicitHvacPowerKw != null &&
            request.explicitHvacPowerKw.isFinite() &&
            request.explicitHvacPowerKw in 0.0..15.0
        ) {
            provenance["hvacPowerKw"] = if (request.isHvacAuthoritative) {
                ParameterProvenanceStatus.VERIFIED
            } else {
                ParameterProvenanceStatus.ESTIMATED
            }
            request.explicitHvacPowerKw
        } else {
            provenance["hvacPowerKw"] = ParameterProvenanceStatus.ESTIMATED
            assumptions.add("ESTIMATED_HVAC_POWER")
            estimateSinglePassHvacKw(effectiveTempC)
        }

        val coldInternalLossFactor = when {
            effectiveTempC >= 20.0 -> 0.025
            effectiveTempC >= 5.0 -> 0.025 + (20.0 - effectiveTempC) * 0.002
            effectiveTempC >= -10.0 -> 0.055 + (5.0 - effectiveTempC) * 0.004
            else -> 0.125
        }
        val coldRegenDerating = when {
            effectiveTempC >= 12.0 -> 1.00
            effectiveTempC >= 0.0 -> 0.75
            effectiveTempC >= -10.0 -> 0.45
            else -> 0.20
        }

        val headwindMps = if (request.routeHeadingDegrees != null &&
            request.windFromDegrees != null &&
            request.windSpeedMps.isFinite() &&
            request.windSpeedMps > 0.0
        ) {
            request.windSpeedMps * cos(Math.toRadians(request.windFromDegrees - request.routeHeadingDegrees))
        } else 0.0

        val driveEff = request.drivetrainEfficiency.coerceIn(0.65, 0.98)
        val regenSysEff = request.regenSystemEfficiency.coerceIn(0.0, 0.92)
        val brakeSplit = request.regenBrakingSplitFactor.coerceIn(0.0, 1.0)
        val maxRegenHardwareKw = min(
            request.maxMotorRegenPowerKw.coerceAtLeast(0.0),
            request.maxBatteryAcceptancePowerKw.coerceAtLeast(0.0)
        )

        var totalDistM = 0.0
        var totalDurS = 0.0
        var sumRollWh = 0.0
        var sumAeroWh = 0.0
        var sumGradeWh = 0.0
        var sumAccelWh = 0.0
        var sumDriveLossWh = 0.0
        var sumHvacWh = 0.0
        var sumBatInternalLossWh = 0.0
        var sumRegenWh = 0.0
        var runningSoc = request.departureSocPercent
        var unknownElevationCount = 0
        var estimatedSpeedCount = 0

        val segmentResults = mutableListOf<ReferenceSegmentEnergyResult>()

        for ((idx, seg) in request.segments.withIndex()) {
            if (!seg.distanceMeters.isFinite() || seg.distanceMeters <= 0.0) continue
            val distM = seg.distanceMeters

            // Speed resolution: observed > target (capped by legal limit if present) > legal limit > reference arterial
            val legalLimit = seg.legalSpeedLimitKmh?.takeIf { it.isFinite() && it in 5.0..200.0 }
            val rawChosenSpeed = when {
                seg.observedSpeedKmh != null && seg.observedSpeedKmh.isFinite() && seg.observedSpeedKmh > 0.0 -> seg.observedSpeedKmh
                seg.targetSpeedKmh != null && seg.targetSpeedKmh.isFinite() && seg.targetSpeedKmh > 0.0 -> {
                    if (legalLimit != null) min(seg.targetSpeedKmh, legalLimit) else seg.targetSpeedKmh
                }
                legalLimit != null -> {
                    estimatedSpeedCount++
                    legalLimit
                }
                else -> {
                    estimatedSpeedCount++
                    assumptions.add("MODEL_ASSUMPTION:TARGET_SPEED")
                    EvDomainPolicyConfig.ARTERIAL_REFERENCE_SPEED_KMH
                }
            }
            val spdKmh = rawChosenSpeed.coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH)
            val spdMps = spdKmh / 3.6

            val elevKnown = (seg.startElevationMeters != null && seg.endElevationMeters != null &&
                seg.startElevationMeters.isFinite() && seg.endElevationMeters.isFinite()) ||
                (seg.explicitGradePercent != null && seg.explicitGradePercent.isFinite())

            if (!elevKnown) unknownElevationCount++

            val gradePct = when {
                seg.explicitGradePercent != null && seg.explicitGradePercent.isFinite() ->
                    seg.explicitGradePercent.coerceIn(-30.0, 30.0)
                seg.startElevationMeters != null && seg.endElevationMeters != null &&
                    seg.startElevationMeters.isFinite() && seg.endElevationMeters.isFinite() ->
                    (((seg.endElevationMeters - seg.startElevationMeters) / distM) * 100.0).coerceIn(-30.0, 30.0)
                else -> 0.0
            }

            val theta = atan(gradePct / 100.0)
            val slopeLen = distM / cos(theta)
            val durS = slopeLen / spdMps
            val avgAlt = if (seg.startElevationMeters != null && seg.endElevationMeters != null &&
                seg.startElevationMeters.isFinite() && seg.endElevationMeters.isFinite()
            ) {
                ((seg.startElevationMeters + seg.endElevationMeters) * 0.5).coerceIn(-400.0, 9000.0)
            } else 0.0

            val rho = 1.225 * (1.0 - (0.0065 * avgAlt.coerceAtLeast(0.0)) / 288.15).pow(4.25588) *
                (288.15 / (effectiveTempC + 273.15).coerceAtLeast(220.0))
            val vAir = (spdMps + headwindMps).coerceAtLeast(0.0)

            val fRollN = request.rollingResistanceCoeff * request.vehicleMassKg * GRAVITY * cos(theta) * seg.roadSurfaceFactor
            val fAeroN = 0.5 * rho * request.dragCoefficient * request.frontalAreaM2 * vAir.pow(2)
            val fGradeN = request.vehicleMassKg * GRAVITY * sin(theta)

            val rollWh = (fRollN * slopeLen) / 3600.0
            val aeroWh = (fAeroN * slopeLen) / 3600.0
            val gradeWh = (fGradeN * slopeLen) / 3600.0

            val vEntryMps = (seg.entrySpeedKmh ?: spdKmh) / 3.6
            val vExitMps = (seg.exitSpeedKmh ?: spdKmh) / 3.6
            val deltaKineticJoules = max(0.0, 0.5 * request.vehicleMassKg * (vExitMps.pow(2) - vEntryMps.pow(2)))
            val stopGoJoules = request.vehicleMassKg * (0.08 * seg.trafficStopGoFactor.coerceAtLeast(0.0)) * slopeLen
            val accelWh = (deltaKineticJoules + stopGoJoules) / 3600.0

            val netWheelMechanicalWh = rollWh + aeroWh + gradeWh + accelWh
            val hvacWh = (hvacKw * 1000.0) * (durS / 3600.0)

            val driveLossWh: Double
            val batInternalLossWh: Double
            val regenWh: Double

            if (netWheelMechanicalWh >= 0.0) {
                val electricalTractionWh = netWheelMechanicalWh / driveEff
                driveLossWh = electricalTractionWh - netWheelMechanicalWh
                batInternalLossWh = electricalTractionWh * coldInternalLossFactor
                regenWh = 0.0
            } else {
                // Downhill net negative wheel work: physical regeneration limits apply
                driveLossWh = 0.0
                val availableBrakingWh = abs(netWheelMechanicalWh)
                val socRegenFactor = when {
                    runningSoc >= 98.0 -> 0.0
                    runningSoc >= 85.0 -> (98.0 - runningSoc) / 13.0
                    else -> 1.0
                }
                val rawRecoverableWh = availableBrakingWh * brakeSplit * regenSysEff * coldRegenDerating * socRegenFactor
                val maxPowerLimitedRegenWh = (maxRegenHardwareKw * 1000.0 * socRegenFactor * coldRegenDerating) * (durS / 3600.0)
                // Also cap so recovered energy never pushes battery above 100% SOC
                val maxCapacityHeadroomWh = ((100.0 - runningSoc).coerceAtLeast(0.0) / 100.0) * usableBatteryWh
                regenWh = minOf(rawRecoverableWh, maxPowerLimitedRegenWh, maxCapacityHeadroomWh).coerceAtLeast(0.0)
                batInternalLossWh = regenWh * (coldInternalLossFactor * 0.5)
            }

            val segBreakdown = EnergyBreakdown(
                rollingResistanceWh = rollWh,
                aerodynamicDragWh = aeroWh,
                gradePotentialWh = gradeWh,
                accelerationKineticWh = accelWh,
                drivetrainLossesWh = driveLossWh,
                hvacAuxiliaryWh = hvacWh,
                batteryInternalLossesWh = batInternalLossWh,
                chargingLossesWh = 0.0,
                regenerativeEnergyWh = regenWh
            )

            val segStartSoc = runningSoc
            val segNetWh = segBreakdown.netEnergyWh
            runningSoc = (runningSoc - (segNetWh / usableBatteryWh) * 100.0).coerceIn(-50.0, 100.0)

            totalDistM += distM
            totalDurS += durS
            sumRollWh += rollWh
            sumAeroWh += aeroWh
            sumGradeWh += gradeWh
            sumAccelWh += accelWh
            sumDriveLossWh += driveLossWh
            sumHvacWh += hvacWh
            sumBatInternalLossWh += batInternalLossWh
            sumRegenWh += regenWh

            segmentResults.add(
                ReferenceSegmentEnergyResult(
                    segmentIndex = idx,
                    distanceMeters = distM,
                    durationSeconds = durS,
                    effectiveSpeedKmh = spdKmh,
                    legalSpeedLimitKmh = legalLimit,
                    gradePercent = gradePct,
                    isElevationKnown = elevKnown,
                    breakdown = segBreakdown,
                    startSocPercent = segStartSoc,
                    endSocPercent = runningSoc
                )
            )
        }

        provenance["elevationProfile"] = when {
            segmentResults.isEmpty() || unknownElevationCount == segmentResults.size -> ParameterProvenanceStatus.UNKNOWN
            unknownElevationCount > 0 -> ParameterProvenanceStatus.ESTIMATED
            else -> ParameterProvenanceStatus.VERIFIED
        }
        provenance["speedProfile"] = when {
            segmentResults.isEmpty() -> ParameterProvenanceStatus.UNKNOWN
            estimatedSpeedCount > 0 -> ParameterProvenanceStatus.ESTIMATED
            else -> ParameterProvenanceStatus.VERIFIED
        }

        val chargingEfficiency = if (request.isDcCharging) 0.93 else 0.89
        val chargingLossWh = if (request.chargingEnergyAddedKwh > 0.0) {
            (request.chargingEnergyAddedKwh * 1000.0) * ((1.0 / chargingEfficiency) - 1.0)
        } else 0.0

        val totalBreakdown = EnergyBreakdown(
            rollingResistanceWh = sumRollWh,
            aerodynamicDragWh = sumAeroWh,
            gradePotentialWh = sumGradeWh,
            accelerationKineticWh = sumAccelWh,
            drivetrainLossesWh = sumDriveLossWh,
            hvacAuxiliaryWh = sumHvacWh,
            batteryInternalLossesWh = sumBatInternalLossWh,
            chargingLossesWh = chargingLossWh,
            regenerativeEnergyWh = sumRegenWh
        )

        var uncertaintyPct = 5.0
        if (!request.isDepartureSocVerified) uncertaintyPct += 8.0
        if (!request.isMassVerified || !request.isDragVerified || !request.isFrontalAreaVerified || !request.isRollingResistanceVerified) {
            uncertaintyPct += 12.0
        }
        if (unknownElevationCount > 0) {
            uncertaintyPct += 8.0
            assumptions.add("MISSING_SEGMENT_ELEVATION:$unknownElevationCount")
        }
        if (estimatedSpeedCount > 0) {
            uncertaintyPct += 6.0
        }
        if (!request.isTemperatureAuthoritative) {
            uncertaintyPct += 6.0
        }
        if (!request.isHvacAuthoritative) {
            uncertaintyPct += 2.0
        }
        uncertaintyPct = uncertaintyPct.coerceIn(5.0, 50.0)

        val expectedWh = totalBreakdown.netEnergyWh
        val spreadWh = max(abs(expectedWh), totalDistM * 0.05) * (uncertaintyPct / 100.0)
        val lowerWh = expectedWh - spreadWh * 0.65
        val upperWh = expectedWh + spreadWh

        val arrNominal = runningSoc.coerceIn(-50.0, 100.0)
        val arrLower = (request.departureSocPercent - (upperWh / usableBatteryWh) * 100.0).coerceIn(-50.0, 100.0)
        val arrUpper = (request.departureSocPercent - (lowerWh / usableBatteryWh) * 100.0).coerceIn(-50.0, 100.0)

        val hasAnyUnknownOrEst = provenance.values.any {
            it == ParameterProvenanceStatus.ESTIMATED || it == ParameterProvenanceStatus.UNKNOWN
        }
        val dataQuality = when {
            segmentResults.isEmpty() -> "INSUFFICIENT_DATA"
            !request.isDepartureSocVerified || !request.isMassVerified -> "ASSUMED"
            hasAnyUnknownOrEst -> "ESTIMATED"
            else -> "VERIFIED"
        }
        val confidence = (1.0 - (uncertaintyPct / 100.0)).coerceIn(0.15, 0.95)
        val isFeasible = segmentResults.isNotEmpty() &&
            arrNominal >= request.minReserveSocPercent &&
            arrLower >= request.minReserveSocPercent

        return EnergyCalculationResult(
            totalDistanceMeters = totalDistM,
            totalDurationSeconds = totalDurS,
            breakdown = totalBreakdown,
            segments = segmentResults,
            departureSocPercent = request.departureSocPercent,
            arrivalSocNominalPercent = arrNominal,
            arrivalSocLowerBoundPercent = arrLower,
            arrivalSocUpperBoundPercent = arrUpper,
            expectedEnergyWh = expectedWh,
            lowerBoundEnergyWh = lowerWh,
            upperBoundEnergyWh = upperWh,
            uncertaintyPercent = uncertaintyPct,
            confidenceScore = confidence,
            dataQuality = dataQuality,
            isFeasibleWithoutCharging = isFeasible,
            parameterProvenance = provenance,
            assumptions = assumptions.toList(),
            warnings = warnings.toList()
        )
    }

    fun estimateSinglePassHvacKw(ambientTempC: Double): Double {
        val baseAuxKw = 0.35
        val thermalExtraKw = when {
            ambientTempC in 18.0..24.0 -> 0.0
            ambientTempC > 38.0 -> 2.25
            ambientTempC > 26.0 -> (ambientTempC - 26.0) * 0.15
            ambientTempC < -5.0 -> 2.45
            ambientTempC < 15.0 -> (15.0 - ambientTempC) * 0.11
            else -> 0.20
        }.coerceAtMost(3.5)
        return baseAuxKw + thermalExtraKw
    }
}
