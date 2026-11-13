package com.evnavigator.iran.domain.intelligence.energy

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.context.UncertaintyRange
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.*

/**
 * Breakdown of energy components for a single segment or entire route.
 */
data class EnergyBreakdownWh(
    val rollingResistanceWh: Double,
    val aerodynamicDragWh: Double,
    val elevationWh: Double,
    val auxiliaryHvacWh: Double,
    val grossMechanicalWh: Double,
    val electricalDrivetrainWh: Double,
    val regeneratedWh: Double,
    val netBatteryEnergyWh: Double,
    val demUncertaintyWh: Double = 0.0
) {
    val netBatteryKwh: Double get() = CanonicalEnergyUnitConverter.whToKwh(netBatteryEnergyWh)
}

/**
 * Segment-level energy prediction result.
 */
data class SegmentEnergyEstimate(
    val segmentIndex: Int,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val durationSeconds: Double,
    val elevationChangeMeters: Double,
    val breakdown: EnergyBreakdownWh,
    val startSocPercent: Double,
    val endSocPercent: Double
)

/**
 * Provenance classification for energy prediction inputs (V13.2 Section 2 & 12).
 */
enum class InputProvenance {
    MEASURED,
    USER_PROVIDED,
    DERIVED_FROM_ROUTE_TIME,
    ESTIMATED,
    MODEL_ASSUMPTION,
    ASSUMED,
    UNKNOWN
}

/**
 * High-level trip energy prediction result with explicit uncertainty range and provenance map.
 */
data class TripEnergyPrediction(
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val breakdown: EnergyBreakdownWh,
    val startSocPercent: Double,
    val predictedArrivalSocPercent: Double,
    val reserveEnergyKwh: Double,
    val reserveSocPercent: Double,
    val isFeasibleWithoutCharging: Boolean,
    val segments: List<SegmentEnergyEstimate>,
    val dominantConsumptionFactor: String,
    val diagnosticsNotes: List<String>,
    val totalDemUncertaintyWh: Double = 0.0,
    val dataQuality: String = "VERIFIED",
    val uncertaintyPercent: Double = 5.0,
    val isExactCalculation: Boolean = true,
    val confidenceScore: Double = 0.90,
    val assumptions: List<String> = emptyList(),
    val inputProvenance: Map<String, InputProvenance> = emptyMap(),
    val energyUncertaintyRangeWh: UncertaintyRange = buildEnergyUncertaintyRange(
        netWh = breakdown.netBatteryEnergyWh,
        uncertaintyPct = uncertaintyPercent,
        demUncertaintyWh = totalDemUncertaintyWh
    )
) {
    val totalEnergyUsedKwh: Double get() = breakdown.netBatteryKwh
    val isLiveTelemetryGrade: Boolean get() = isExactCalculation && dataQuality == "VERIFIED"

    companion object {
        fun buildEnergyUncertaintyRange(
            netWh: Double,
            uncertaintyPct: Double,
            demUncertaintyWh: Double = 0.0
        ): UncertaintyRange {
            if (!netWh.isFinite()) {
                return UncertaintyRange(0.0, 0.0, 0.0, "Wh")
            }
            val safePct = if (uncertaintyPct.isFinite()) uncertaintyPct.coerceIn(0.0, 100.0) else 50.0
            val spreadWh = abs(netWh) * (safePct / 100.0) + (if (demUncertaintyWh.isFinite()) abs(demUncertaintyWh) else 0.0)
            val minWh = minOf(netWh, netWh - spreadWh)
            val maxWh = maxOf(netWh, netWh + spreadWh)
            return UncertaintyRange(min = minWh, nominal = netWh, max = maxWh, unit = "Wh")
        }
    }
}

/**
 * TripIntelligenceContext adapter that delegates physical energy calculation to [canonicalEnergyService]
 * ([EnergyPredictionService]) while enforcing strict provenance propagation from [TripIntelligenceContext].
 *
 * V13.2 Non-Negotiable Guarantees (Section 2 & 14):
 * 1. Never silently substitutes `90.0 km/h` for missing vehicle speed or `22.0 °C` for missing ambient temperature.
 * 2. When speed or temperature is missing, records `InputProvenance.MODEL_ASSUMPTION` and `"MODEL_ASSUMPTION:..."`,
 *    lowers confidence, increases uncertainty, and marks output `ESTIMATED` (never `VERIFIED` or live).
 * 3. Rejects negative, `NaN`, or `Infinite` physical inputs.
 * 4. Separates vehicle average speed (`averageSpeed`) from road legal speed limit (`speedLimit`).
 * 5. Never double-counts HVAC or thermal derating between orchestrator and service.
 */
class PredictiveEnergyOrchestrator(
    val canonicalEnergyService: EnergyPredictionService = DefaultEnergyPredictionService()
) {

    companion object {
        const val GRAVITY = 9.80665 // m/s²
        const val SEA_LEVEL_TEMP_KELVIN = 288.15 // K (15°C)
        const val TEMPERATURE_LAPSE_RATE = 0.0065 // K/m
        const val GAS_CONSTANT_AIR = 287.058 // J/(kg·K)
        const val DEFAULT_DRIVE_EFFICIENCY = 0.91
        const val DEFAULT_REGEN_EFFICIENCY = 0.75

        private fun InputQualityAttribute<*>.isAuthoritative(): Boolean {
            if (value == null || isStale) return false
            return when (origin) {
                InputOrigin.MEASURED_TELEMETRY,
                InputOrigin.VERIFIED_DATABASE,
                InputOrigin.NETWORK_PROVIDER,
                InputOrigin.USER_INPUT,
                InputOrigin.USER_CONFIGURED -> true
                InputOrigin.DERIVED_FROM_ROUTE_TIME,
                InputOrigin.ESTIMATED_MODEL,
                InputOrigin.MODEL_ASSUMPTION,
                InputOrigin.ASSUMED_DEFAULT,
                InputOrigin.UNKNOWN -> false
            }
        }

        private fun InputQualityAttribute<*>.toInputProvenance(): InputProvenance {
            if (value == null) return InputProvenance.UNKNOWN
            if (isStale) return InputProvenance.ASSUMED
            return when (origin) {
                InputOrigin.MEASURED_TELEMETRY,
                InputOrigin.VERIFIED_DATABASE,
                InputOrigin.NETWORK_PROVIDER -> InputProvenance.MEASURED
                InputOrigin.USER_INPUT,
                InputOrigin.USER_CONFIGURED -> InputProvenance.USER_PROVIDED
                InputOrigin.DERIVED_FROM_ROUTE_TIME -> InputProvenance.DERIVED_FROM_ROUTE_TIME
                InputOrigin.ESTIMATED_MODEL -> InputProvenance.ESTIMATED
                InputOrigin.MODEL_ASSUMPTION -> InputProvenance.MODEL_ASSUMPTION
                InputOrigin.ASSUMED_DEFAULT -> InputProvenance.ASSUMED
                InputOrigin.UNKNOWN -> InputProvenance.UNKNOWN
            }
        }
    }

    /**
     * Compute comprehensive energy prediction across a route given the trip context.
     * Delegates segment physics calculation to [canonicalEnergyService] and combines it with
     * provenance-aware quality and uncertainty evaluation.
     */
    fun predictTripEnergy(
        context: TripIntelligenceContext,
        @Suppress("UNUSED_PARAMETER") routeElevationChanges: List<Double>? = null
    ): TripEnergyPrediction {
        val notes = mutableListOf<String>()
        val extraAssumptions = LinkedHashSet<String>()

        val batteryCapKwh = context.batteryCapacityKwh.value
            ?: throw IllegalArgumentException("Battery capacity must be specified.")
        if (!batteryCapKwh.isFinite() || batteryCapKwh <= 0.0) {
            throw IllegalArgumentException("Battery capacity ($batteryCapKwh kWh) must be positive and finite.")
        }
        val startSoc = context.currentSocPercent.value
            ?: throw IllegalArgumentException("Current SOC must be specified.")
        if (!startSoc.isFinite() || startSoc !in 0.0..100.0) {
            throw IllegalArgumentException("Current SOC ($startSoc%) must be finite and within [0.0, 100.0].")
        }
        if (!context.routeDistanceMeters.isFinite() || context.routeDistanceMeters <= 0.0) {
            throw IllegalArgumentException("Route distance (${context.routeDistanceMeters} m) must be positive and finite.")
        }

        val rawAvgSpeed = context.averageSpeedKmh.value
        if (rawAvgSpeed != null && (!rawAvgSpeed.isFinite() || rawAvgSpeed <= 0.0 || rawAvgSpeed > 250.0)) {
            throw IllegalArgumentException("Average speed ($rawAvgSpeed km/h) must be finite and within (0.0, 250.0].")
        }

        val rawAmbientTemp = context.ambientTemperatureCelsius.value
        if (rawAmbientTemp != null && (!rawAmbientTemp.isFinite() || rawAmbientTemp !in -60.0..70.0)) {
            throw IllegalArgumentException("Ambient temperature ($rawAmbientTemp °C) must be finite and within [-60.0, 70.0].")
        }

        val rawWindSpeed = context.windSpeedMps.value
        if (rawWindSpeed != null && (!rawWindSpeed.isFinite() || rawWindSpeed < 0.0 || rawWindSpeed > 85.0)) {
            throw IllegalArgumentException("Wind speed ($rawWindSpeed m/s) must be finite and within [0.0, 85.0].")
        }

        val rawCurbMass = context.curbWeightKg.value
        if (rawCurbMass != null && (!rawCurbMass.isFinite() || rawCurbMass <= 0.0)) {
            throw IllegalArgumentException("Curb weight ($rawCurbMass kg) must be positive and finite.")
        }

        val minReserve = (context.minReserveSocPercent.value ?: EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT)
            .coerceIn(0.0, 50.0)

        val isVehicleAuthoritative = context.vehicleId.isNotBlank() &&
            context.vehicleId != "assumed_fallback" &&
            context.vehicleId != "no_active_vehicle" &&
            context.batteryCapacityKwh.isAuthoritative() &&
            context.curbWeightKg.isAuthoritative() &&
            context.dragCoefficient.isAuthoritative() &&
            context.frontalAreaM2.isAuthoritative() &&
            context.rollingResistanceCoeff.isAuthoritative() &&
            context.driveEfficiency.isAuthoritative()

        if (!isVehicleAuthoritative) {
            extraAssumptions.add("ASSUMED_OR_INCOMPLETE_VEHICLE_SPECS")
        }

        val isSocVerified = context.currentSocPercent.isAuthoritative()
        if (!isSocVerified) {
            extraAssumptions.add("UNVERIFIED_DEPARTURE_SOC")
        }

        val distanceMeters = context.routeDistanceMeters.coerceAtLeast(1.0)

        // V13.2 Section 2: Resolve speed without hidden 90.0 km/h substitution!
        val derivedRouteSpeedKmh: Double? = if (rawAvgSpeed == null &&
            context.routeEstimatedDurationSeconds.isFinite() &&
            context.routeEstimatedDurationSeconds > 0.0
        ) {
            ((distanceMeters / context.routeEstimatedDurationSeconds) * 3.6)
                .coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH)
        } else null

        val isSpeedAuthoritative = rawAvgSpeed != null && context.averageSpeedKmh.isAuthoritative()
        val isSpeedMissingOrModelAssumed = rawAvgSpeed == null && derivedRouteSpeedKmh == null
        val resolvedTargetSpeedKmh: Double? = when {
            rawAvgSpeed != null -> rawAvgSpeed.coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH)
            derivedRouteSpeedKmh != null -> derivedRouteSpeedKmh
            else -> null
        }

        val speedProvenance: InputProvenance = when {
            rawAvgSpeed != null -> {
                if (context.averageSpeedKmh.origin == InputOrigin.ASSUMED_DEFAULT ||
                    context.averageSpeedKmh.origin == InputOrigin.MODEL_ASSUMPTION
                ) {
                    extraAssumptions.add("MODEL_ASSUMPTION:AVERAGE_SPEED")
                    InputProvenance.MODEL_ASSUMPTION
                } else {
                    context.averageSpeedKmh.toInputProvenance()
                }
            }
            derivedRouteSpeedKmh != null -> {
                extraAssumptions.add("DERIVED_SPEED_FROM_ROUTE_DURATION")
                notes.add("سرعت متوسط حرکت از نسبت مسافت به زمان تخمینی مسیر (DERIVED_FROM_ROUTE_TIME) استخراج شد.")
                InputProvenance.DERIVED_FROM_ROUTE_TIME
            }
            else -> {
                extraAssumptions.add("MODEL_ASSUMPTION:MISSING_AVERAGE_SPEED")
                extraAssumptions.add("ASSUMED_AVERAGE_SPEED")
                notes.add("سرعت متوسط حرکت نامشخص است؛ فرض مدل سرعت مرجع با اعمال جریمه عدم‌قطعیت ثبت شد.")
                InputProvenance.MODEL_ASSUMPTION
            }
        }

        // V13.2 Section 2: Resolve ambient temperature without hidden 22.0 °C substitution!
        val isTempMeasured = rawAmbientTemp != null && context.ambientTemperatureCelsius.isAuthoritative()
        val tempProvenance: InputProvenance = when {
            rawAmbientTemp == null -> {
                extraAssumptions.add("MODEL_ASSUMPTION:MISSING_AMBIENT_TEMPERATURE")
                extraAssumptions.add("ASSUMED_AMBIENT_TEMPERATURE")
                notes.add("دمای محیط نامشخص است؛ فرض مدل استاندارد اتمسفر (MODEL_ASSUMPTION) با کاهش اطمینان اعمال شد.")
                InputProvenance.MODEL_ASSUMPTION
            }
            context.ambientTemperatureCelsius.origin == InputOrigin.ASSUMED_DEFAULT ||
                context.ambientTemperatureCelsius.origin == InputOrigin.MODEL_ASSUMPTION -> {
                extraAssumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE")
                extraAssumptions.add("ASSUMED_AMBIENT_TEMPERATURE")
                InputProvenance.MODEL_ASSUMPTION
            }
            else -> context.ambientTemperatureCelsius.toInputProvenance()
        }

        val effectiveWindMps = rawWindSpeed ?: 0.0
        val headingDeg = context.routeHeadingDegrees
        val isHeadingValid = headingDeg != null && headingDeg.isFinite()
        val isWindMeasured = context.windSpeedMps.isAuthoritative() &&
            (effectiveWindMps == 0.0 || (context.windDirectionDegrees.isAuthoritative() && isHeadingValid))
        if (!isWindMeasured) {
            extraAssumptions.add("ASSUMED_OR_UNRESOLVED_WIND_VECTOR")
        }

        val isHvacMeasured = context.hvacPowerKw.isAuthoritative()
        if (!isHvacMeasured) {
            extraAssumptions.add("ESTIMATED_HVAC_POWER")
        }

        val totalMassKg = context.totalMassKg ?: VehiclePhysicsParams.REFERENCE_MASS_KG
        val cd = context.dragCoefficient.value ?: VehiclePhysicsParams.REFERENCE_CD
        val frontalArea = context.frontalAreaM2.value ?: VehiclePhysicsParams.REFERENCE_FRONTAL_AREA_M2
        val crr = context.rollingResistanceCoeff.value ?: VehiclePhysicsParams.REFERENCE_CRR
        val driveEff = (context.driveEfficiency.value ?: DEFAULT_DRIVE_EFFICIENCY).coerceIn(0.70, 0.98)
        val regenEff = DEFAULT_REGEN_EFFICIENCY

        val avgElev = context.averageElevationMeters.value
        val isDemAvailable = avgElev != null && avgElev.isFinite() && context.averageElevationMeters.isAuthoritative()
        val isElevationGainAuthoritative = context.elevationGainMeters.isAuthoritative()
        val isElevationLossAuthoritative = context.elevationLossMeters.isAuthoritative()
        val isElevationEstimated = !isDemAvailable || !isElevationGainAuthoritative || !isElevationLossAuthoritative

        var contextDemUncertaintyWh = 0.0
        if (!isDemAvailable) {
            val potentialRiseM = (distanceMeters * 0.015).coerceAtMost(300.0)
            contextDemUncertaintyWh = (totalMassKg * GRAVITY * potentialRiseM) / (3600.0 * driveEff)
            notes.add("هشدار: داده رقومی ارتفاعی (DEM) موجود نیست. ارتفاع واقعی نامعلوم (Unknown) باقی ماند؛ اثر شیب با عدم قطعیت تخمینی (±${contextDemUncertaintyWh.roundToInt()} Wh) علامت‌گذاری شد.")
            extraAssumptions.add("MISSING_VERIFIED_DEM")
        }

        if (rawAmbientTemp != null && rawAmbientTemp < 10.0) {
            val tempDeratingFactor = calculateTemperatureDerating(rawAmbientTemp)
            val pct = ((tempDeratingFactor - 1.0) * 100.0).roundToInt()
            notes.add("Ambient temperature ($rawAmbientTemp°C) increases battery internal resistance by $pct%.")
        }

        if (!isHeadingValid) {
            notes.add("جهت مسیر (Heading) نامشخص است؛ باد مدل نشد.")
        }
        notes.add("تقریب سرعت میانگین (v²) تغییرات سرعت لحظه‌ای را کم‌برآورد می‌کند.")

        if (startSoc >= 90.0) {
            notes.add("بازیافت انرژی (Regen) به دلیل پر بودن باتری (${startSoc.toInt()}٪) محدود شد (throttled).")
        }

        val totalGainMeters = (context.elevationGainMeters.value ?: 0.0).coerceAtLeast(0.0)
        val totalLossMeters = (context.elevationLossMeters.value ?: 0.0).coerceAtLeast(0.0)
        val baseElevation = if (isDemAvailable) avgElev!! else 0.0

        // V13.2 Section 2: Keep road legal speed limit (`speedLimitKmh = null` unless explicitly known)
        // strictly separate from vehicle average/target speed (`targetSpeedKmh = resolvedTargetSpeedKmh`).
        val segments: List<RouteSegmentInput> = when {
            totalGainMeters > 0.0 && totalLossMeters > 0.0 -> {
                val halfDist = (distanceMeters / 2.0).coerceAtLeast(1.0)
                listOf(
                    RouteSegmentInput(
                        distanceMeters = halfDist,
                        startElevationMeters = baseElevation,
                        endElevationMeters = baseElevation + totalGainMeters,
                        speedLimitKmh = null,
                        targetSpeedKmh = resolvedTargetSpeedKmh
                    ),
                    RouteSegmentInput(
                        distanceMeters = halfDist,
                        startElevationMeters = baseElevation + totalGainMeters,
                        endElevationMeters = (baseElevation + totalGainMeters - totalLossMeters),
                        speedLimitKmh = null,
                        targetSpeedKmh = resolvedTargetSpeedKmh
                    )
                )
            }
            totalGainMeters > 0.0 -> {
                listOf(
                    RouteSegmentInput(
                        distanceMeters = distanceMeters,
                        startElevationMeters = baseElevation,
                        endElevationMeters = baseElevation + totalGainMeters,
                        speedLimitKmh = null,
                        targetSpeedKmh = resolvedTargetSpeedKmh
                    )
                )
            }
            totalLossMeters > 0.0 -> {
                listOf(
                    RouteSegmentInput(
                        distanceMeters = distanceMeters,
                        startElevationMeters = baseElevation + totalLossMeters,
                        endElevationMeters = baseElevation,
                        speedLimitKmh = null,
                        targetSpeedKmh = resolvedTargetSpeedKmh
                    )
                )
            }
            else -> {
                listOf(
                    RouteSegmentInput(
                        distanceMeters = distanceMeters,
                        startElevationMeters = if (isDemAvailable) baseElevation else null,
                        endElevationMeters = if (isDemAvailable) baseElevation else null,
                        speedLimitKmh = null,
                        targetSpeedKmh = resolvedTargetSpeedKmh
                    )
                )
            }
        }

        val vehiclePhysics = VehiclePhysicsParams(
            vehicleId = context.vehicleId,
            usableBatteryKwh = batteryCapKwh,
            totalMassKg = totalMassKg,
            dragCoefficient = cd,
            frontalAreaM2 = frontalArea,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = driveEff,
            regenEfficiency = regenEff,
            isAssumedFallback = !isVehicleAuthoritative,
            dataQuality = if (isVehicleAuthoritative) "VERIFIED" else "ASSUMED",
            uncertaintyPercent = if (isVehicleAuthoritative) 5.0 else 25.0
        )

        // V13.2 Section 14: Do NOT pre-calculate temperature HVAC load here and then pass ambientTempC
        // to EnergyPredictionService (which would double-count thermal HVAC load). Pass explicit hvacPowerKw
        // only if present in context, otherwise Double.NaN so EnergyPredictionService computes it in a single pass.
        val envConditions = EnvironmentalConditions(
            departureSocPercent = startSoc,
            minReserveSocPercent = minReserve,
            targetSpeedKmh = resolvedTargetSpeedKmh ?: Double.NaN,
            ambientTempC = rawAmbientTemp ?: Double.NaN,
            windSpeedMps = effectiveWindMps,
            windFromDegrees = if (isHeadingValid) (context.windDirectionDegrees.value ?: 0.0) else null,
            hvacPowerKw = context.hvacPowerKw.value ?: Double.NaN,
            isTemperatureMeasured = isTempMeasured,
            isWindMeasured = isWindMeasured,
            isHvacMeasured = isHvacMeasured,
            isDepartureSocVerified = isSocVerified
        )

        val canonicalPred = canonicalEnergyService.predict(
            route = RouteProfileInput(
                segments = segments,
                routeHeadingDegrees = if (isHeadingValid) headingDeg else 0.0,
                isRouteAverageApproximation = true
            ),
            vehicle = vehiclePhysics,
            conditions = envConditions
        )

        val mergedDemUncertaintyWh = maxOf(canonicalPred.totalDemUncertaintyWh, contextDemUncertaintyWh)
        val mergedAssumptions = (canonicalPred.assumptions + extraAssumptions).distinct()
        val mergedNotes = (notes + canonicalPred.diagnosticsNotes).distinct().toMutableList()

        val resolvedDataQuality = when {
            canonicalPred.dataQuality == "INSUFFICIENT_VEHICLE_DATA" ||
                canonicalPred.dataQuality == "INSUFFICIENT_DATA" -> canonicalPred.dataQuality
            isElevationEstimated || canonicalPred.dataQuality == "LOW_CONFIDENCE" -> "LOW_CONFIDENCE"
            !isVehicleAuthoritative || !isSocVerified || canonicalPred.dataQuality == "ASSUMED" -> "ASSUMED"
            isSpeedMissingOrModelAssumed || rawAmbientTemp == null ||
                !isTempMeasured || !isWindMeasured || !isHvacMeasured || !isSpeedAuthoritative ||
                canonicalPred.dataQuality == "ESTIMATED" -> "ESTIMATED"
            else -> "VERIFIED"
        }

        var orchestratorPenaltyPct = canonicalPred.uncertaintyPercent
        if (isSpeedMissingOrModelAssumed) orchestratorPenaltyPct = maxOf(orchestratorPenaltyPct, 18.0)
        if (rawAmbientTemp == null) orchestratorPenaltyPct = maxOf(orchestratorPenaltyPct, 14.0)
        if (isSpeedMissingOrModelAssumed && rawAmbientTemp == null) orchestratorPenaltyPct = maxOf(orchestratorPenaltyPct, 24.0)

        val resolvedUncertaintyPercent = when (resolvedDataQuality) {
            "VERIFIED" -> 5.0
            "ESTIMATED" -> maxOf(orchestratorPenaltyPct, 12.0)
            "ASSUMED" -> maxOf(orchestratorPenaltyPct, 20.0)
            else -> maxOf(orchestratorPenaltyPct, 25.0)
        }
        val resolvedConfidenceScore = (1.0 - (resolvedUncertaintyPercent / 100.0)).coerceIn(0.15, 0.95)
        val isStrictlyExact = resolvedDataQuality == "VERIFIED"

        if (!canonicalPred.isFeasibleWithoutCharging) {
            mergedNotes.add("شارژ باتری به کمتر از حد رزرو ایمن (${minReserve.toInt()}٪) افت خواهد کرد؛ توقف شارژ میان‌راهی الزامی است.")
        }

        val mergedProvenance = canonicalPred.inputProvenance.toMutableMap().apply {
            put("vehicle", if (isVehicleAuthoritative) InputProvenance.USER_PROVIDED else InputProvenance.ASSUMED)
            put("departureSoc", context.currentSocPercent.toInputProvenance())
            put("averageSpeed", speedProvenance)
            put("targetSpeed", speedProvenance)
            put("speedLimit", InputProvenance.UNKNOWN)
            put("ambientTemp", tempProvenance)
            put("wind", context.windSpeedMps.toInputProvenance())
            put("hvac", if (isHvacMeasured) InputProvenance.MEASURED else InputProvenance.ESTIMATED)
            put("elevation", if (isDemAvailable) InputProvenance.MEASURED else InputProvenance.UNKNOWN)
        }

        val climbWh = (totalMassKg * GRAVITY * totalGainMeters) / 3600.0
        val factorMap = mapOf(
            "Aerodynamic Drag" to canonicalPred.breakdown.aerodynamicDragWh,
            "Rolling Resistance" to canonicalPred.breakdown.rollingResistanceWh,
            "Elevation Climbing" to maxOf(canonicalPred.breakdown.elevationWh, climbWh),
            "Auxiliary / HVAC" to canonicalPred.breakdown.auxiliaryHvacWh
        )
        val dominantFactor = factorMap.maxByOrNull { it.value }?.key ?: canonicalPred.dominantConsumptionFactor

        val updatedBreakdown = canonicalPred.breakdown.copy(
            elevationWh = if (totalGainMeters > 0.0 && totalLossMeters == 0.0) {
                canonicalPred.breakdown.elevationWh.coerceAtLeast(0.0)
            } else if (totalGainMeters == 0.0 && totalLossMeters == 0.0) {
                0.0
            } else {
                canonicalPred.breakdown.elevationWh
            },
            demUncertaintyWh = mergedDemUncertaintyWh
        )

        return canonicalPred.copy(
            breakdown = updatedBreakdown,
            dominantConsumptionFactor = dominantFactor,
            diagnosticsNotes = mergedNotes,
            totalDemUncertaintyWh = mergedDemUncertaintyWh,
            dataQuality = resolvedDataQuality,
            uncertaintyPercent = resolvedUncertaintyPercent,
            isExactCalculation = isStrictlyExact,
            confidenceScore = resolvedConfidenceScore,
            assumptions = mergedAssumptions,
            inputProvenance = mergedProvenance,
            energyUncertaintyRangeWh = TripEnergyPrediction.buildEnergyUncertaintyRange(
                netWh = updatedBreakdown.netBatteryEnergyWh,
                uncertaintyPct = resolvedUncertaintyPercent,
                demUncertaintyWh = mergedDemUncertaintyWh
            )
        )
    }

    /**
     * Compute barometric air density accounting for elevation and temperature.
     */
    fun calculateAirDensity(elevationMeters: Double, tempC: Double): Double {
        val tempKelvin = tempC + 273.15
        val pressureRatio = (1.0 - (TEMPERATURE_LAPSE_RATE * elevationMeters.coerceAtLeast(0.0)) / SEA_LEVEL_TEMP_KELVIN)
            .coerceAtLeast(0.01)
        val pressurePa = 101325.0 * pressureRatio.pow(5.25588)
        return (pressurePa / (GAS_CONSTANT_AIR * tempKelvin)).coerceIn(0.70, 1.45)
    }

    private fun calculateTemperatureDerating(tempC: Double): Double {
        return when {
            tempC >= 15.0 -> 1.00
            tempC >= 0.0 -> 1.00 + (15.0 - tempC) * 0.005
            tempC >= -20.0 -> 1.075 + (0.0 - tempC) * 0.010
            else -> 1.30
        }
    }

    fun estimateHvacPower(tempC: Double): Double {
        return when {
            tempC in 18.0..24.0 -> 0.35
            tempC > 38.0 -> 2.60
            tempC > 28.0 -> 1.40
            tempC < -5.0 -> 2.80
            tempC < 10.0 -> 1.50
            else -> 0.80
        }
    }
}
