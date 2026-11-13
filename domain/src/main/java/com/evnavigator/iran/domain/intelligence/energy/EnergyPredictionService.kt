package com.evnavigator.iran.domain.intelligence.energy

import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * V13.2 Section 14: Canonical energy unit conversion utility.
 * Prevents unit confusion between Wh/km, kWh/100km, Wh, and kWh across the application.
 */
object CanonicalEnergyUnitConverter {
    /**
     * Converts consumption in Wh/km to kWh/100km.
     * Example: 180 Wh/km -> 18.0 kWh/100km (180 * 100 / 1000 = 180 / 10).
     */
    fun whPerKmToKwhPer100Km(whPerKm: Double): Double {
        require(whPerKm.isFinite() && whPerKm >= 0.0) { "whPerKm must be finite and non-negative, was $whPerKm" }
        return whPerKm / 10.0
    }

    /**
     * Converts consumption in kWh/100km to Wh/km.
     * Example: 18.0 kWh/100km -> 180.0 Wh/km.
     */
    fun kwhPer100KmToWhPerKm(kwhPer100Km: Double): Double {
        require(kwhPer100Km.isFinite() && kwhPer100Km >= 0.0) { "kwhPer100Km must be finite and non-negative, was $kwhPer100Km" }
        return kwhPer100Km * 10.0
    }

    /**
     * Converts Wh to kWh.
     */
    fun whToKwh(wh: Double): Double {
        require(wh.isFinite()) { "wh must be finite, was $wh" }
        return wh / 1000.0
    }

    /**
     * Converts kWh to Wh.
     */
    fun kwhToWh(kwh: Double): Double {
        require(kwh.isFinite()) { "kwh must be finite, was $kwh" }
        return kwh * 1000.0
    }
}

/**
 * Single Unified Energy Prediction Service (V13.2 Audited).
 * Used identically by MultiStopRouteCoordinator (Map), TripIntelligenceViewModel (Planner),
 * PredictiveEnergyOrchestrator, and ActiveJourneyViewModel so arrival SoC is 100% consistent across the entire app.
 */
interface EnergyPredictionService {
    fun predict(
        route: RouteProfileInput,
        vehicle: VehiclePhysicsParams,
        conditions: EnvironmentalConditions = EnvironmentalConditions()
    ): TripEnergyPrediction
}

data class RouteSegmentInput(
    val distanceMeters: Double,
    val startElevationMeters: Double?,
    val endElevationMeters: Double?,
    val speedLimitKmh: Double? = null,
    val targetSpeedKmh: Double? = null
) {
    val isValid: Boolean
        get() = distanceMeters.isFinite() && distanceMeters > 0.0 &&
            (speedLimitKmh == null || (speedLimitKmh.isFinite() && speedLimitKmh in EvDomainPolicyConfig.MIN_VALID_SPEED_KMH..250.0)) &&
            (targetSpeedKmh == null || (targetSpeedKmh.isFinite() && targetSpeedKmh in EvDomainPolicyConfig.MIN_VALID_SPEED_KMH..250.0))
}

data class RouteProfileInput(
    val segments: List<RouteSegmentInput>,
    val routeHeadingDegrees: Double? = null,
    val isRouteAverageApproximation: Boolean = false
)

data class VehiclePhysicsParams(
    val vehicleId: String,
    val usableBatteryKwh: Double,
    val nominalGrossBatteryKwh: Double? = null,
    val totalMassKg: Double = REFERENCE_MASS_KG,
    val dragCoefficient: Double = REFERENCE_CD,
    val frontalAreaM2: Double = REFERENCE_FRONTAL_AREA_M2,
    val rollingResistanceCoeff: Double = REFERENCE_CRR,
    val drivetrainEfficiency: Double = 0.88,
    val regenEfficiency: Double = 0.78,
    val maxChargePowerKw: Double = 100.0,
    val dcMaxChargePowerKw: Double? = maxChargePowerKw.takeIf { it.isFinite() && it > 0.0 },
    val acMaxChargePowerKw: Double? = null,
    val supportedConnectors: List<String> = listOf("CCS2", "Type 2"),
    val isAssumedFallback: Boolean = (
        vehicleId.isBlank() ||
            vehicleId == "assumed_fallback" ||
            vehicleId == "no_active_vehicle" ||
            !usableBatteryKwh.isFinite() ||
            usableBatteryKwh <= 0.0 ||
            totalMassKg == REFERENCE_MASS_KG && dragCoefficient == REFERENCE_CD && frontalAreaM2 == REFERENCE_FRONTAL_AREA_M2
        ),
    val dataQuality: String = when {
        !usableBatteryKwh.isFinite() || usableBatteryKwh <= 0.0 -> "INSUFFICIENT_VEHICLE_DATA"
        isAssumedFallback -> "ASSUMED"
        else -> "VERIFIED"
    },
    val uncertaintyPercent: Double = if (isAssumedFallback) 25.0 else 5.0
) {
    val hasValidUsableBatteryCapacity: Boolean
        get() = usableBatteryKwh.isFinite() && usableBatteryKwh in MIN_VALID_BATTERY_KWH..MAX_VALID_BATTERY_KWH

    val hasValidGrossBatteryCapacity: Boolean
        get() = nominalGrossBatteryKwh != null &&
            nominalGrossBatteryKwh.isFinite() &&
            nominalGrossBatteryKwh in MIN_VALID_BATTERY_KWH..MAX_VALID_BATTERY_KWH &&
            (!hasValidUsableBatteryCapacity || nominalGrossBatteryKwh >= usableBatteryKwh)

    val isValidPhysics: Boolean
        get() = hasValidUsableBatteryCapacity &&
            totalMassKg.isFinite() && totalMassKg in 400.0..12_000.0 &&
            dragCoefficient.isFinite() && dragCoefficient in 0.12..0.85 &&
            frontalAreaM2.isFinite() && frontalAreaM2 in 0.8..8.0 &&
            rollingResistanceCoeff.isFinite() && rollingResistanceCoeff in 0.004..0.040 &&
            drivetrainEfficiency.isFinite() && drivetrainEfficiency in 0.50..0.99 &&
            regenEfficiency.isFinite() && regenEfficiency in 0.0..0.95

    companion object {
        const val REFERENCE_MASS_KG = 1900.0
        const val REFERENCE_CD = 0.28
        const val REFERENCE_FRONTAL_AREA_M2 = 2.30
        const val REFERENCE_CRR = 0.010
        const val MIN_VALID_BATTERY_KWH = 5.0
        const val MAX_VALID_BATTERY_KWH = 300.0
        const val DEFAULT_USABLE_TO_GROSS_RATIO = 0.92

        /**
         * V13.1/V13.2 Invariant (Section 3 & 14): Never invents a generic battery fallback.
         * If [usableBatteryKwh] is omitted or invalid, it remains [Double.NaN] and the
         * prediction service reports `INSUFFICIENT_VEHICLE_DATA`.
         */
        fun assumedReference(
            vehicleId: String = "no_active_vehicle",
            usableBatteryKwh: Double = Double.NaN,
            nominalGrossBatteryKwh: Double? = null
        ): VehiclePhysicsParams {
            val validUsable = usableBatteryKwh.takeIf { it.isFinite() && it > 0.0 } ?: Double.NaN
            return VehiclePhysicsParams(
                vehicleId = vehicleId,
                usableBatteryKwh = validUsable,
                nominalGrossBatteryKwh = nominalGrossBatteryKwh?.takeIf { it.isFinite() && it > 0.0 },
                totalMassKg = REFERENCE_MASS_KG,
                dragCoefficient = REFERENCE_CD,
                frontalAreaM2 = REFERENCE_FRONTAL_AREA_M2,
                rollingResistanceCoeff = REFERENCE_CRR,
                dcMaxChargePowerKw = null,
                acMaxChargePowerKw = null,
                isAssumedFallback = true,
                dataQuality = if (validUsable.isFinite()) "ASSUMED" else "INSUFFICIENT_VEHICLE_DATA",
                uncertaintyPercent = if (validUsable.isFinite()) 25.0 else 100.0
            )
        }

        fun insufficientVehicleData(vehicleId: String = "no_active_vehicle"): VehiclePhysicsParams =
            assumedReference(vehicleId = vehicleId, usableBatteryKwh = Double.NaN, nominalGrossBatteryKwh = null)
    }
}

/**
 * V13.2 Section 2: Environmental conditions without silent `90.0 km/h` or `22.0 °C` defaults.
 * When [targetSpeedKmh] or [ambientTempC] is omitted (`Double.NaN`), the engine explicitly records
 * `InputProvenance.MODEL_ASSUMPTION` and increases uncertainty accordingly.
 */
data class EnvironmentalConditions(
    val departureSocPercent: Double = 85.0,
    val minReserveSocPercent: Double = 15.0,
    val targetSpeedKmh: Double = Double.NaN,
    val ambientTempC: Double = Double.NaN,
    val windSpeedMps: Double = 0.0,
    val windFromDegrees: Double? = null,
    val hvacPowerKw: Double = Double.NaN,
    val isTemperatureMeasured: Boolean = false,
    val isWindMeasured: Boolean = false,
    val isHvacMeasured: Boolean = false,
    val isDepartureSocVerified: Boolean = false
)

class DefaultEnergyPredictionService : EnergyPredictionService {

    override fun predict(
        route: RouteProfileInput,
        vehicle: VehiclePhysicsParams,
        conditions: EnvironmentalConditions
    ): TripEnergyPrediction {
        val notes = mutableListOf<String>()
        val assumptions = mutableListOf<String>()
        val provenanceMap = mutableMapOf<String, InputProvenance>()

        val validUsableKwh = vehicle.usableBatteryKwh.takeIf {
            it.isFinite() && it in VehiclePhysicsParams.MIN_VALID_BATTERY_KWH..VehiclePhysicsParams.MAX_VALID_BATTERY_KWH
        }
        val derivedUsableFromGrossKwh = if (validUsableKwh == null) {
            vehicle.nominalGrossBatteryKwh?.takeIf {
                it.isFinite() && it in VehiclePhysicsParams.MIN_VALID_BATTERY_KWH..VehiclePhysicsParams.MAX_VALID_BATTERY_KWH
            }?.let { it * VehiclePhysicsParams.DEFAULT_USABLE_TO_GROSS_RATIO }
        } else null

        val resolvedBatteryKwh: Double? = validUsableKwh ?: derivedUsableFromGrossKwh
        val hasInsufficientBatteryData = resolvedBatteryKwh == null
        val isBatteryDerivedFromGross = validUsableKwh == null && derivedUsableFromGrossKwh != null

        if (hasInsufficientBatteryData) {
            notes.add("ظرفیت قابل استفاده باتری (usableBatteryKwh) نامشخص یا نامعتبر است؛ محاسبه درصد شارژ (SoC) و امکان‌سنجی مسیر بدون داده معتبر باتری امکان‌پذیر نیست.")
            assumptions.add("INSUFFICIENT_VEHICLE_DATA")
            assumptions.add("MISSING_OR_INVALID_BATTERY_CAPACITY")
            provenanceMap["batteryCapacity"] = InputProvenance.UNKNOWN
        } else if (isBatteryDerivedFromGross) {
            notes.add("ظرفیت قابل استفاده باتری مستقیماً مشخص نبود و از ظرفیت اسمی ناخالص (${vehicle.nominalGrossBatteryKwh} kWh با ضریب ۰.۹۲) به‌صورت تخمینی (ESTIMATED) محاسبه شد.")
            assumptions.add("ESTIMATED_USABLE_FROM_GROSS_BATTERY")
            provenanceMap["batteryCapacity"] = InputProvenance.ESTIMATED
        } else if (vehicle.nominalGrossBatteryKwh != null &&
            vehicle.nominalGrossBatteryKwh.isFinite() &&
            vehicle.nominalGrossBatteryKwh < validUsableKwh!!
        ) {
            notes.add("ظرفیت ناخالص باتری کمتر از ظرفیت قابل استفاده گزارش شده است؛ عدم تطابق مشخصات باتری ثبت شد.")
            assumptions.add("GROSS_BATTERY_LESS_THAN_USABLE_WARNING")
            provenanceMap["batteryCapacity"] = InputProvenance.ASSUMED
        } else {
            provenanceMap["batteryCapacity"] = if (vehicle.isAssumedFallback) InputProvenance.ASSUMED else InputProvenance.USER_PROVIDED
        }

        if (!vehicle.isValidPhysics) {
            notes.add("پارامترهای فیزیکی خودرو نامعتبر یا خارج از دامنه مجاز هستند؛ مقادیر مرجع با برچسب تخمینی جایگزین شدند.")
            assumptions.add("INVALID_VEHICLE_PHYSICS_FALLBACK")
        }
        if (vehicle.isAssumedFallback) {
            notes.add("مشخصات خودرو تخمینی (ASSUMED) است؛ لطفاً خودروی خود را در گاراژ انتخاب کنید.")
            assumptions.add("ASSUMED_VEHICLE_PROFILE")
            provenanceMap["vehicle"] = if (hasInsufficientBatteryData) InputProvenance.UNKNOWN else InputProvenance.ASSUMED
        } else {
            provenanceMap["vehicle"] = if (vehicle.isValidPhysics) InputProvenance.USER_PROVIDED else InputProvenance.ASSUMED
        }

        val hasInvalidDepartureSoc = !conditions.departureSocPercent.isFinite() || conditions.departureSocPercent !in 0.0..100.0
        if (hasInvalidDepartureSoc) {
            notes.add("درصد شارژ اولیه باتری (${conditions.departureSocPercent}%) نامعتبر یا خارج از بازه [0, 100] است.")
            assumptions.add("INVALID_DEPARTURE_SOC")
            provenanceMap["departureSoc"] = InputProvenance.UNKNOWN
        } else if (conditions.isDepartureSocVerified) {
            provenanceMap["departureSoc"] = InputProvenance.USER_PROVIDED
        } else {
            provenanceMap["departureSoc"] = InputProvenance.ASSUMED
            notes.add("درصد شارژ اولیه باتری (${conditions.departureSocPercent}%) تأیید نشده و فرضی (ASSUMED) است.")
            assumptions.add("UNVERIFIED_DEFAULT_DEPARTURE_SOC")
        }

        // V13.2 Section 2: Strict ambient temperature validation and provenance tracking (no hidden 22.0°C fallback!)
        val isTempMissing = conditions.ambientTempC.isNaN()
        val isTempOutOfRange = !isTempMissing && (!conditions.ambientTempC.isFinite() || conditions.ambientTempC !in -60.0..70.0)
        val effectiveAmbientTempC: Double = when {
            isTempOutOfRange -> {
                notes.add("دمای محیط ورودی (${conditions.ambientTempC} °C) خارج از بازه فیزیکی معتبر [-60, 70] است.")
                assumptions.add("INVALID_AMBIENT_TEMPERATURE")
                provenanceMap["ambientTemp"] = InputProvenance.UNKNOWN
                EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS
            }
            isTempMissing -> {
                notes.add("دمای محیط نامشخص است؛ فرض مدل استاندارد اتمسفر (MODEL_ASSUMPTION: ${EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS}°C) با اعمال عدم‌قطعیت حرارتی ثبت شد.")
                assumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE")
                provenanceMap["ambientTemp"] = InputProvenance.MODEL_ASSUMPTION
                EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS
            }
            conditions.isTemperatureMeasured -> {
                provenanceMap["ambientTemp"] = InputProvenance.MEASURED
                conditions.ambientTempC
            }
            else -> {
                notes.add("دمای محیط (${conditions.ambientTempC}°C) اندازه‌گیری زنده نیست و به‌عنوان فرض مدل (MODEL_ASSUMPTION) لحاظ شد.")
                assumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE")
                provenanceMap["ambientTemp"] = InputProvenance.MODEL_ASSUMPTION
                conditions.ambientTempC
            }
        }

        val isWindInvalid = !conditions.windSpeedMps.isFinite() || conditions.windSpeedMps < 0.0 || conditions.windSpeedMps > 85.0
        if (isWindInvalid) {
            notes.add("سرعت باد ورودی (${conditions.windSpeedMps} m/s) نامعتبر است.")
            assumptions.add("INVALID_WIND_SPEED")
            provenanceMap["wind"] = InputProvenance.UNKNOWN
        } else if (conditions.isWindMeasured) {
            provenanceMap["wind"] = InputProvenance.MEASURED
        } else {
            provenanceMap["wind"] = InputProvenance.MODEL_ASSUMPTION
            assumptions.add("ASSUMED_WIND_VECTOR")
        }

        val isHvacExplicitlyValid = conditions.hvacPowerKw.isFinite() && conditions.hvacPowerKw in 0.0..15.0
        val isHvacInvalid = !conditions.hvacPowerKw.isNaN() && !isHvacExplicitlyValid
        if (isHvacInvalid) {
            notes.add("توان مصرفی تهویه مطبوع (${conditions.hvacPowerKw} kW) نامعتبر است.")
            assumptions.add("INVALID_HVAC_POWER")
            provenanceMap["hvac"] = InputProvenance.UNKNOWN
        } else if (conditions.isHvacMeasured && isHvacExplicitlyValid) {
            provenanceMap["hvac"] = InputProvenance.MEASURED
        } else {
            provenanceMap["hvac"] = InputProvenance.ESTIMATED
            notes.add("مصرف تهویه مطبوع (HVAC) اندازه‌گیری نشده و از مدل تخمینی دما محاسبه شده است.")
            assumptions.add("ESTIMATED_HVAC_POWER")
        }

        val isConditionTargetSpeedMissing = conditions.targetSpeedKmh.isNaN()
        val isConditionTargetSpeedInvalid = !isConditionTargetSpeedMissing &&
            (!conditions.targetSpeedKmh.isFinite() || conditions.targetSpeedKmh <= 0.0 || conditions.targetSpeedKmh > 250.0)
        if (isConditionTargetSpeedInvalid) {
            notes.add("سرعت هدف ورودی (${conditions.targetSpeedKmh} km/h) نامعتبر است.")
            assumptions.add("INVALID_TARGET_SPEED")
        }

        if (route.isRouteAverageApproximation) {
            notes.add("ویژگی‌های سگمنت از میانگین کل مسیر تقریب زده شده‌اند (ROUTE_AVERAGE_APPROXIMATION).")
            assumptions.add("ROUTE_AVERAGE_SEGMENT_APPROXIMATION")
        }

        val safeMassKg = vehicle.totalMassKg.takeIf { it.isFinite() && it in 400.0..12_000.0 } ?: VehiclePhysicsParams.REFERENCE_MASS_KG
        val safeCd = vehicle.dragCoefficient.takeIf { it.isFinite() && it in 0.12..0.85 } ?: VehiclePhysicsParams.REFERENCE_CD
        val safeAreaM2 = vehicle.frontalAreaM2.takeIf { it.isFinite() && it in 0.8..8.0 } ?: VehiclePhysicsParams.REFERENCE_FRONTAL_AREA_M2
        val safeCrr = vehicle.rollingResistanceCoeff.takeIf { it.isFinite() && it in 0.004..0.040 } ?: VehiclePhysicsParams.REFERENCE_CRR
        val safeDrivetrainEff = vehicle.drivetrainEfficiency.takeIf { it.isFinite() && it in 0.50..0.99 } ?: 0.88
        val safeRegenEff = vehicle.regenEfficiency.takeIf { it.isFinite() && it in 0.0..0.95 } ?: 0.78

        val headwindMps = if (!isWindInvalid && route.routeHeadingDegrees != null && conditions.windFromDegrees != null &&
            route.routeHeadingDegrees.isFinite() && conditions.windFromDegrees.isFinite()
        ) {
            val relRad = Math.toRadians(conditions.windFromDegrees - route.routeHeadingDegrees)
            conditions.windSpeedMps * cos(relRad)
        } else {
            if (!isWindInvalid && conditions.windSpeedMps > 0.0) {
                notes.add("جهت حرکت (heading) یا زاویه باد مشخص نیست؛ اثر باد صفر فرض شد.")
                assumptions.add("MISSING_HEADING_OR_WIND_DIRECTION")
            }
            0.0
        }

        val coldDerating = when {
            effectiveAmbientTempC >= 15.0 -> 1.0
            effectiveAmbientTempC >= 0.0 -> 1.0 + (15.0 - effectiveAmbientTempC) * 0.005
            else -> 1.075 + (0.0 - effectiveAmbientTempC) * 0.01
        }

        val effectiveHvacKw = resolveSinglePassHvacKw(
            explicitHvacKw = if (isHvacExplicitlyValid) conditions.hvacPowerKw else null,
            isHvacMeasured = conditions.isHvacMeasured && isHvacExplicitlyValid,
            ambientTempC = effectiveAmbientTempC
        )

        var totalDistM = 0.0
        var totalDurS = 0.0
        var totalRollWh = 0.0
        var totalAeroWh = 0.0
        var totalGradeWh = 0.0
        var totalAuxWh = 0.0
        var totalGrossWh = 0.0
        var totalElecWh = 0.0
        var totalRegenWh = 0.0
        var runningSoc = if (hasInvalidDepartureSoc) Double.NaN else conditions.departureSocPercent.coerceIn(0.0, 100.0)
        var minRunningSoc = runningSoc
        val segmentPredictions = mutableListOf<SegmentEnergyEstimate>()
        var unknownElevationSegments = 0
        var modelAssumedSpeedSegments = 0
        var conditionProvidedSpeedSegments = 0
        var assumedTargetSpeedFromLimitSegments = 0
        var missingSpeedLimitSegments = 0
        var speedLimitCappedSegments = 0
        var invalidSegmentsSkipped = 0
        var totalDemUncertaintyWh = 0.0

        val conditionTargetSpeedValid = if (!isConditionTargetSpeedMissing && !isConditionTargetSpeedInvalid) {
            conditions.targetSpeedKmh
        } else null

        for ((idx, seg) in route.segments.withIndex()) {
            val hasInvalidSegSpeed = (seg.targetSpeedKmh != null && (!seg.targetSpeedKmh.isFinite() || seg.targetSpeedKmh <= 0.0 || seg.targetSpeedKmh > 250.0)) ||
                (seg.speedLimitKmh != null && (!seg.speedLimitKmh.isFinite() || seg.speedLimitKmh <= 0.0 || seg.speedLimitKmh > 250.0))

            if (!seg.distanceMeters.isFinite() || seg.distanceMeters <= 0.0 || hasInvalidSegSpeed) {
                invalidSegmentsSkipped++
                continue
            }
            val distM = seg.distanceMeters.coerceAtLeast(1.0)
            val validSegTargetSpeed = seg.targetSpeedKmh?.takeIf { it.isFinite() && it > 0.0 }
            val validSegSpeedLimit = seg.speedLimitKmh?.takeIf { it.isFinite() && it > 0.0 }

            if (validSegSpeedLimit == null) {
                missingSpeedLimitSegments++
            }

            // V13.2 Section 2: Strict separation between vehicle target/average speed and legal road speed limit.
            // Never silently injects 90.0 km/h! When speed is missing, records MODEL_ASSUMPTION explicitly.
            val resolvedSpeedKmh: Double = when {
                validSegTargetSpeed != null && validSegSpeedLimit != null -> {
                    if (validSegTargetSpeed > validSegSpeedLimit) {
                        speedLimitCappedSegments++
                        validSegSpeedLimit
                    } else {
                        validSegTargetSpeed
                    }
                }
                validSegTargetSpeed != null && validSegSpeedLimit == null -> {
                    validSegTargetSpeed
                }
                validSegTargetSpeed == null && conditionTargetSpeedValid != null -> {
                    conditionProvidedSpeedSegments++
                    if (validSegSpeedLimit != null && conditionTargetSpeedValid > validSegSpeedLimit) {
                        speedLimitCappedSegments++
                        validSegSpeedLimit
                    } else {
                        conditionTargetSpeedValid
                    }
                }
                validSegTargetSpeed == null && validSegSpeedLimit != null -> {
                    assumedTargetSpeedFromLimitSegments++
                    validSegSpeedLimit
                }
                else -> {
                    modelAssumedSpeedSegments++
                    EvDomainPolicyConfig.ARTERIAL_REFERENCE_SPEED_KMH
                }
            }

            val spdKmh = resolvedSpeedKmh.coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH)
            val spdMps = spdKmh / 3.6
            val knownStart = seg.startElevationMeters
            val knownEnd = seg.endElevationMeters ?: knownStart
            val elevationKnown = knownStart != null && knownEnd != null &&
                knownStart.isFinite() && knownEnd.isFinite()
            val segDemUncertaintyWh = if (!elevationKnown) {
                unknownElevationSegments++
                val potentialRiseM = (distM * 0.025).coerceAtMost(80.0)
                (safeMassKg * 9.80665 * potentialRiseM) / (3600.0 * safeDrivetrainEff)
            } else 0.0
            totalDemUncertaintyWh += segDemUncertaintyWh

            val startElev = if (elevationKnown) knownStart!! else 0.0
            val endElev = if (elevationKnown) knownEnd!! else 0.0
            val deltaElev = endElev - startElev
            val gradePct = ((deltaElev / distM) * 100.0).coerceIn(-30.0, 30.0)
            val theta = atan(gradePct / 100.0)
            val slopeLen = distM / cos(theta)
            val avgAlt = (startElev + endElev) / 2.0
            val rho = 1.225 * (1.0 - (0.0065 * avgAlt.coerceIn(0.0, 9000.0)) / 288.15).pow(4.25588)
            val vAir = (spdMps + headwindMps).coerceAtLeast(0.0)

            val fRoll = safeCrr * safeMassKg * 9.80665 * cos(theta)
            val fAero = 0.5 * rho * safeCd * safeAreaM2 * vAir.pow(2)
            val fGrade = safeMassKg * 9.80665 * sin(theta)

            val rollWh = (fRoll * slopeLen) / 3600.0
            val aeroWh = (fAero * slopeLen) / 3600.0
            val gradeWh = (fGrade * slopeLen) / 3600.0
            val wheelWh = rollWh + aeroWh + gradeWh
            val durS = slopeLen / spdMps
            val auxWh = (effectiveHvacKw * 1000.0) * (durS / 3600.0)

            val drawWh: Double
            val regenWh: Double
            if (wheelWh >= 0.0) {
                drawWh = (wheelWh / safeDrivetrainEff) * coldDerating + auxWh
                regenWh = 0.0
            } else {
                val regenAcceptance = if (runningSoc.isFinite() && runningSoc >= 90.0) 0.25 else 1.0
                val coldRegenFactor = if (effectiveAmbientTempC < 5.0) 0.65 else 1.0
                drawWh = auxWh
                regenWh = (-wheelWh) * safeRegenEff * regenAcceptance * coldRegenFactor
            }

            val netWh = drawWh - regenWh
            val segStartSoc = if (hasInsufficientBatteryData || hasInvalidDepartureSoc) Double.NaN else runningSoc
            if (resolvedBatteryKwh != null && runningSoc.isFinite()) {
                val socDrop = (netWh / (resolvedBatteryKwh * 1000.0)) * 100.0
                runningSoc = (runningSoc - socDrop).coerceAtMost(100.0)
                if (runningSoc < minRunningSoc) minRunningSoc = runningSoc
            } else {
                runningSoc = Double.NaN
                minRunningSoc = Double.NaN
            }

            totalDistM += distM
            totalDurS += durS
            totalRollWh += rollWh
            totalAeroWh += aeroWh
            totalGradeWh += gradeWh
            totalAuxWh += auxWh
            if (wheelWh > 0) totalGrossWh += wheelWh
            totalElecWh += drawWh - auxWh
            totalRegenWh += regenWh

            segmentPredictions.add(
                SegmentEnergyEstimate(
                    segmentIndex = idx,
                    distanceMeters = distM,
                    averageSpeedKmh = spdKmh,
                    durationSeconds = durS,
                    elevationChangeMeters = deltaElev,
                    breakdown = EnergyBreakdownWh(
                        rollingResistanceWh = rollWh,
                        aerodynamicDragWh = aeroWh,
                        elevationWh = gradeWh,
                        auxiliaryHvacWh = auxWh,
                        grossMechanicalWh = if (wheelWh > 0.0) wheelWh else 0.0,
                        electricalDrivetrainWh = drawWh - auxWh,
                        regeneratedWh = regenWh,
                        netBatteryEnergyWh = netWh,
                        demUncertaintyWh = segDemUncertaintyWh
                    ),
                    startSocPercent = segStartSoc,
                    endSocPercent = runningSoc
                )
            )
        }

        if (segmentPredictions.isEmpty()) {
            notes.add("هیچ سگمنت معتبری برای محاسبه انرژی وجود ندارد؛ امکان‌سنجی مسیر غیرممکن است.")
            assumptions.add("NO_VALID_ROUTE_SEGMENTS")
        }
        val netBatteryWh = (totalElecWh + totalAuxWh) - totalRegenWh
        if (invalidSegmentsSkipped > 0) {
            val baseEnergyForPenaltyWh = resolvedBatteryKwh?.let { it * 1000.0 * 0.05 } ?: maxOf(netBatteryWh * 0.15, 2500.0)
            val skippedPenaltyWh = invalidSegmentsSkipped * baseEnergyForPenaltyWh
            totalDemUncertaintyWh += skippedPenaltyWh
            notes.add("$invalidSegmentsSkipped سگمنت با فاصله یا سرعت نامعتبر نادیده گرفته شد؛ به دلیل نقص داده، امکان‌سنجی قطعی رد شد.")
            assumptions.add("SKIPPED_INVALID_SEGMENTS:$invalidSegmentsSkipped")
            provenanceMap["routeGeometry"] = InputProvenance.UNKNOWN
        } else if (segmentPredictions.isNotEmpty()) {
            provenanceMap["routeGeometry"] = InputProvenance.MEASURED
        } else {
            provenanceMap["routeGeometry"] = InputProvenance.UNKNOWN
        }

        if (unknownElevationSegments > 0) {
            notes.add("ارتفاع $unknownElevationSegments از ${route.segments.size} سگمنت نامشخص است؛ عدم قطعیت DEM (±${"%.1f".format(totalDemUncertaintyWh)} Wh) لحاظ گردید.")
            assumptions.add("MISSING_SEGMENT_ELEVATION:$unknownElevationSegments")
            provenanceMap["elevation"] = InputProvenance.UNKNOWN
        } else if (segmentPredictions.isNotEmpty()) {
            provenanceMap["elevation"] = InputProvenance.MEASURED
        } else {
            provenanceMap["elevation"] = InputProvenance.UNKNOWN
        }

        provenanceMap["speedLimit"] = when {
            segmentPredictions.isEmpty() || missingSpeedLimitSegments == segmentPredictions.size -> InputProvenance.UNKNOWN
            missingSpeedLimitSegments > 0 -> InputProvenance.ESTIMATED
            else -> InputProvenance.MEASURED
        }

        if (isConditionTargetSpeedInvalid) {
            provenanceMap["targetSpeed"] = InputProvenance.UNKNOWN
        } else if (modelAssumedSpeedSegments > 0) {
            notes.add("سرعت حرکت برای $modelAssumedSpeedSegments سگمنت نامشخص بود و با ثبت فرض مدل (MODEL_ASSUMPTION: ${EvDomainPolicyConfig.ARTERIAL_REFERENCE_SPEED_KMH} km/h) و افزایش عدم‌قطعیت محاسبه شد.")
            assumptions.add("MODEL_ASSUMPTION:TARGET_SPEED:$modelAssumedSpeedSegments")
            assumptions.add("MISSING_SPEED_LIMIT_AND_TARGET_SPEED:$modelAssumedSpeedSegments")
            provenanceMap["targetSpeed"] = InputProvenance.MODEL_ASSUMPTION
        } else if (assumedTargetSpeedFromLimitSegments > 0) {
            notes.add("سرعت حرکت (targetSpeedKmh) برای $assumedTargetSpeedFromLimitSegments سگمنت از سرعت مجاز جاده فرض شد.")
            assumptions.add("TARGET_SPEED_ASSUMED_FROM_SPEED_LIMIT:$assumedTargetSpeedFromLimitSegments")
            provenanceMap["targetSpeed"] = InputProvenance.ASSUMED
        } else if (conditionProvidedSpeedSegments > 0) {
            provenanceMap["targetSpeed"] = InputProvenance.USER_PROVIDED
        } else if (segmentPredictions.isNotEmpty()) {
            provenanceMap["targetSpeed"] = InputProvenance.USER_PROVIDED
        } else {
            provenanceMap["targetSpeed"] = InputProvenance.UNKNOWN
        }

        if (missingSpeedLimitSegments > 0 && modelAssumedSpeedSegments == 0) {
            notes.add("سرعت مجاز قانونی (speedLimitKmh) برای $missingSpeedLimitSegments سگمنت نامشخص (UNKNOWN) است.")
            assumptions.add("SPEED_LIMIT_MISSING:$missingSpeedLimitSegments")
        }
        if (speedLimitCappedSegments > 0) {
            notes.add("سرعت هدف در $speedLimitCappedSegments سگمنت بیش از حد مجاز قانونی بود و به سقف speedLimitKmh محدود شد.")
            assumptions.add("TARGET_SPEED_CAPPED_TO_SPEED_LIMIT:$speedLimitCappedSegments")
        }

        val hasInvalidInputError = hasInvalidDepartureSoc || isTempOutOfRange || isWindInvalid || isHvacInvalid || isConditionTargetSpeedInvalid

        val reserveSoc = if (resolvedBatteryKwh != null && runningSoc.isFinite()) {
            runningSoc - conditions.minReserveSocPercent
        } else Double.NaN
        val reserveKwh = if (resolvedBatteryKwh != null && reserveSoc.isFinite()) {
            (reserveSoc / 100.0) * resolvedBatteryKwh
        } else Double.NaN
        val demSocImpact = if (resolvedBatteryKwh != null) {
            (totalDemUncertaintyWh / (resolvedBatteryKwh * 1000.0)) * 100.0
        } else Double.NaN

        val isFeasible = !hasInsufficientBatteryData &&
            !hasInvalidInputError &&
            !isBatteryDerivedFromGross &&
            segmentPredictions.isNotEmpty() &&
            invalidSegmentsSkipped == 0 &&
            runningSoc.isFinite() &&
            minRunningSoc.isFinite() &&
            (runningSoc - demSocImpact) >= conditions.minReserveSocPercent &&
            minRunningSoc >= conditions.minReserveSocPercent

        val resolvedDataQuality = when {
            hasInsufficientBatteryData -> "INSUFFICIENT_VEHICLE_DATA"
            hasInvalidInputError || segmentPredictions.isEmpty() -> "INSUFFICIENT_DATA"
            invalidSegmentsSkipped > 0 -> "LOW_CONFIDENCE"
            isBatteryDerivedFromGross -> "ESTIMATED"
            vehicle.isAssumedFallback || !vehicle.isValidPhysics || !conditions.isDepartureSocVerified -> "ASSUMED"
            unknownElevationSegments > 0 || modelAssumedSpeedSegments > 0 -> "ESTIMATED"
            !conditions.isTemperatureMeasured || isTempMissing || !conditions.isWindMeasured || !conditions.isHvacMeasured ||
                assumedTargetSpeedFromLimitSegments > 0 || missingSpeedLimitSegments > 0 || route.isRouteAverageApproximation -> "ESTIMATED"
            else -> "VERIFIED"
        }

        var penaltyPct = 5.0
        if (hasInsufficientBatteryData || hasInvalidInputError) penaltyPct = 100.0
        if (segmentPredictions.isEmpty()) penaltyPct += 35.0
        if (invalidSegmentsSkipped > 0) penaltyPct += (12.0 * invalidSegmentsSkipped)
        if (isBatteryDerivedFromGross) penaltyPct += 12.0
        if (vehicle.isAssumedFallback || !vehicle.isValidPhysics) penaltyPct += 15.0
        if (!conditions.isDepartureSocVerified) penaltyPct += 8.0
        if (unknownElevationSegments > 0) penaltyPct += 8.0
        if (modelAssumedSpeedSegments > 0) penaltyPct += 10.0
        if (assumedTargetSpeedFromLimitSegments > 0 || missingSpeedLimitSegments > 0) penaltyPct += 3.0
        if (isTempMissing || !conditions.isTemperatureMeasured) penaltyPct += 6.0
        if (!conditions.isWindMeasured) penaltyPct += 2.0
        if (!conditions.isHvacMeasured) penaltyPct += 2.0
        if (route.isRouteAverageApproximation) penaltyPct += 5.0

        val resolvedUncertaintyPct = if (hasInsufficientBatteryData || hasInvalidInputError) {
            100.0
        } else {
            maxOf(
                if (vehicle.isAssumedFallback) vehicle.uncertaintyPercent else 5.0,
                penaltyPct
            ).coerceIn(5.0, 50.0)
        }
        val resolvedConfidenceScore = if (hasInsufficientBatteryData || hasInvalidInputError) {
            0.0
        } else {
            (1.0 - (resolvedUncertaintyPct / 100.0)).coerceIn(0.15, 0.95)
        }
        val isStrictlyExact = resolvedDataQuality == "VERIFIED" && invalidSegmentsSkipped == 0 && segmentPredictions.isNotEmpty()

        return TripEnergyPrediction(
            totalDistanceMeters = totalDistM,
            totalDurationSeconds = totalDurS,
            breakdown = EnergyBreakdownWh(
                rollingResistanceWh = totalRollWh,
                aerodynamicDragWh = totalAeroWh,
                elevationWh = totalGradeWh,
                auxiliaryHvacWh = totalAuxWh,
                grossMechanicalWh = totalGrossWh,
                electricalDrivetrainWh = totalElecWh,
                regeneratedWh = totalRegenWh,
                netBatteryEnergyWh = netBatteryWh,
                demUncertaintyWh = totalDemUncertaintyWh
            ),
            startSocPercent = conditions.departureSocPercent,
            predictedArrivalSocPercent = runningSoc,
            reserveEnergyKwh = reserveKwh,
            reserveSocPercent = reserveSoc,
            isFeasibleWithoutCharging = isFeasible,
            segments = segmentPredictions,
            dominantConsumptionFactor = when {
                totalAeroWh >= totalRollWh && totalAeroWh >= totalGradeWh -> "Aerodynamic Drag"
                totalRollWh >= totalGradeWh -> "Rolling Resistance"
                else -> "Elevation"
            },
            diagnosticsNotes = notes,
            totalDemUncertaintyWh = totalDemUncertaintyWh,
            dataQuality = resolvedDataQuality,
            uncertaintyPercent = resolvedUncertaintyPct,
            isExactCalculation = isStrictlyExact,
            confidenceScore = resolvedConfidenceScore,
            assumptions = assumptions,
            inputProvenance = provenanceMap
        )
    }

    /**
     * V13.2 Section 14: Single-pass cabin HVAC load computation.
     * If [isHvacMeasured] is true and [explicitHvacKw] is valid, uses the measured value directly without
     * adding a second temperature-dependent term. Otherwise estimates HVAC load from [ambientTempC] once.
     */
    private fun resolveSinglePassHvacKw(
        explicitHvacKw: Double?,
        isHvacMeasured: Boolean,
        ambientTempC: Double
    ): Double {
        if (isHvacMeasured && explicitHvacKw != null) {
            return explicitHvacKw
        }
        val baseAuxKw = explicitHvacKw ?: 0.35
        val thermalExtraKw = when {
            ambientTempC in 18.0..24.0 -> 0.0
            ambientTempC > 38.0 -> 2.25
            ambientTempC > 26.0 -> (ambientTempC - 26.0) * 0.15
            ambientTempC < -5.0 -> 2.45
            ambientTempC < 15.0 -> (15.0 - ambientTempC) * 0.11
            else -> 0.20
        }.coerceAtMost(3.5)
        return if (explicitHvacKw != null && explicitHvacKw > 0.5) {
            // Caller already provided an estimated HVAC kW; do not double-add thermal extra on top
            explicitHvacKw
        } else {
            baseAuxKw + thermalExtraKw
        }
    }
}
