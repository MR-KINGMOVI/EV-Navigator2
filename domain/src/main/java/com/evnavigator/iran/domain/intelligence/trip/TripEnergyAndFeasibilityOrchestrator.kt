package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.IntelligenceCoverageStatus
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.routing.IntelligentRouteScorer
import com.evnavigator.iran.domain.intelligence.uncertainty.ConfidenceAndUncertaintyEngine
import com.evnavigator.iran.domain.intelligence.uncertainty.QualitativeUncertainty
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.Soc
import com.evnavigator.iran.domain.model.Vehicle
import com.evnavigator.iran.domain.model.trip.intelligence.*
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.roundToInt

/**
 * Orchestrates energy and feasibility calculations for trip intelligence.
 *
 * Delegates all physical energy calculations to [PredictiveEnergyOrchestrator] (which wraps
 * the single canonical [com.evnavigator.iran.domain.intelligence.energy.EnergyPredictionService])
 * and computes calibrated uncertainty intervals, data quality scores, and staleness indicators
 * via [ConfidenceAndUncertaintyEngine] — never stamping hardcoded confidence or accuracy flags.
 */
class TripEnergyAndFeasibilityOrchestrator(
    private val predictiveEnergyOrchestrator: PredictiveEnergyOrchestrator,
    private val intelligentRouteScorer: IntelligentRouteScorer,
    private val confidenceAndUncertaintyEngine: ConfidenceAndUncertaintyEngine
) {

    /**
     * Calculates energy consumption and evaluates feasibility of a trip.
     */
    suspend fun calculateEnergyAndFeasibility(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result<TripIntelligenceResult> {
        return try {
            val route = request.trip.route
            val vehicle = request.vehicle ?: request.trip.vehicle

            request.initialSocPercent?.let { rawSoc ->
                EvDomainPolicyConfig.validateSocPercentOrThrow(rawSoc, "initialSocPercent")
            }
            context?.currentSocPercent?.value?.let { ctxSoc ->
                EvDomainPolicyConfig.validateSocPercentOrThrow(ctxSoc, "context.currentSocPercent")
            }

            val isDefaultSynthesizedContext = (context == null)
            val effectiveContext = context ?: buildDefaultContext(request, vehicle, route)
            val prediction = predictiveEnergyOrchestrator.predictTripEnergy(effectiveContext)

            // Evaluate bounded uncertainty and risk factors via ConfidenceAndUncertaintyEngine
            val isCalibratedVehicle = !isDefaultSynthesizedContext &&
                effectiveContext.dragCoefficient.isAuthoritative() &&
                effectiveContext.curbWeightKg.isAuthoritative() &&
                effectiveContext.batteryCapacityKwh.isAuthoritative()

            val uncertainty = confidenceAndUncertaintyEngine.evaluateUncertainty(
                prediction = prediction,
                context = effectiveContext,
                isCalibrated = isCalibratedVehicle
            )

            // Evaluate input freshness against domain policy TTLs
            val dataGaps = mutableListOf<String>()
            dataGaps.addAll(uncertainty.primaryRiskFactors)

            val weatherAgeMs = effectiveContext.weatherFreshnessTimestamp?.let { nowEpochMs - it }
            val isWeatherStale = weatherAgeMs == null ||
                weatherAgeMs < -EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS ||
                weatherAgeMs > EvDomainPolicyConfig.WEATHER_OBSERVATION_FRESHNESS_TTL_MS

            val trafficAgeMs = effectiveContext.trafficFreshnessTimestamp?.let { nowEpochMs - it }
            val isTrafficStale = trafficAgeMs == null ||
                trafficAgeMs < -EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS ||
                trafficAgeMs > EvDomainPolicyConfig.TRAFFIC_OBSERVATION_FRESHNESS_TTL_MS

            val socTimestamp = effectiveContext.currentSocPercent.timestampEpochMs
            val isSocStaleOrUnverified = !effectiveContext.currentSocPercent.isAuthoritative() ||
                socTimestamp == null ||
                (nowEpochMs - socTimestamp) !in -EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS..EvDomainPolicyConfig.SOC_FRESHNESS_TTL_MS

            if (isSocStaleOrUnverified) {
                dataGaps.add("Starting battery SoC is unverified or stale.")
            }

            val isDataStale = isWeatherStale || isTrafficStale || isSocStaleOrUnverified ||
                effectiveContext.coverageStatus == IntelligenceCoverageStatus.OFFLINE_STALE ||
                effectiveContext.coverageStatus == IntelligenceCoverageStatus.UNAVAILABLE

            // Compute calibrated qualityScore [0.0 .. 1.0] from authoritative input coverage & freshness
            val trackedAttributes = listOf(
                effectiveContext.batteryCapacityKwh,
                effectiveContext.curbWeightKg,
                effectiveContext.dragCoefficient,
                effectiveContext.frontalAreaM2,
                effectiveContext.rollingResistanceCoeff,
                effectiveContext.currentSocPercent,
                effectiveContext.elevationGainMeters,
                effectiveContext.ambientTemperatureCelsius,
                effectiveContext.windSpeedMps
            )
            val authoritativeCount = trackedAttributes.count { it.isAuthoritative() }
            val baseCoverageRatio = authoritativeCount.toDouble() / trackedAttributes.size.toDouble()
            val freshnessFactor = when {
                !isWeatherStale && !isTrafficStale && !isSocStaleOrUnverified -> 1.0
                !isSocStaleOrUnverified -> 0.80
                else -> 0.55
            }
            val rawQualityScore = (baseCoverageRatio * 0.75 + freshnessFactor * 0.25).coerceIn(0.10, 0.99)
            val calibratedQualityScore = if (prediction.dataQuality == "VERIFIED" && !isDefaultSynthesizedContext) {
                rawQualityScore
            } else {
                rawQualityScore.coerceAtMost(0.55)
            }

            // Accuracy requires exact calculation, verified data quality, fresh inputs, and low uncertainty
            val isAccurate = prediction.isExactCalculation &&
                prediction.dataQuality == "VERIFIED" &&
                !isDataStale &&
                !isDefaultSynthesizedContext &&
                uncertainty.qualitativeLevel == QualitativeUncertainty.LOW_UNCERTAINTY

            // Calibrated confidence percentage [15.0 .. 96.0], capped at <= 55% when inputs are assumed/stale
            val rawConfidencePercent = when (uncertainty.qualitativeLevel) {
                QualitativeUncertainty.LOW_UNCERTAINTY -> 75.0 + (calibratedQualityScore * 20.0)
                QualitativeUncertainty.MODERATE_UNCERTAINTY -> 55.0 + (calibratedQualityScore * 20.0)
                QualitativeUncertainty.HIGH_UNCERTAINTY -> 35.0 + (calibratedQualityScore * 18.0)
                QualitativeUncertainty.VERY_HIGH_UNCERTAINTY -> 20.0 + (calibratedQualityScore * 15.0)
            }
            val calibratedConfidencePercent = if (isAccurate) {
                rawConfidencePercent.coerceIn(60.0, 96.0)
            } else {
                rawConfidencePercent.coerceAtMost(55.0).coerceAtLeast(15.0)
            }

            val arrivalSoc = Soc(prediction.predictedArrivalSocPercent.coerceIn(0.0, 100.0))
            val isNumericallyFeasible = prediction.predictedArrivalSocPercent >= prediction.reserveSocPercent &&
                prediction.totalDistanceMeters > 0.0
            val isConservativeLowerBoundFeasible =
                uncertainty.arrivalSocIntervalPercent.lowerBound >= prediction.reserveSocPercent
            val isUncertain = !isAccurate ||
                prediction.dataQuality != "VERIFIED" ||
                (isNumericallyFeasible && !isConservativeLowerBoundFeasible)
            val isOperationallyFeasible = prediction.isFeasibleWithoutCharging &&
                isNumericallyFeasible &&
                isConservativeLowerBoundFeasible &&
                !isSocStaleOrUnverified

            val energyPrediction = EnergyPrediction(
                predictedConsumptionWh = prediction.breakdown.netBatteryEnergyWh,
                efficiencyKwhPer100km = if (prediction.totalDistanceMeters > 0) {
                    (prediction.breakdown.netBatteryEnergyWh / 1000.0) / (prediction.totalDistanceMeters / 100_000.0)
                } else null,
                isAccurate = isAccurate,
                lowerBoundWh = uncertainty.energyIntervalWh.lowerBound,
                upperBoundWh = uncertainty.energyIntervalWh.upperBound,
                dataQuality = prediction.dataQuality
            )

            val arrivalSocPrediction = ArrivalSocPrediction(
                predictedArrivalSoc = arrivalSoc,
                isAccurate = isAccurate,
                confidence = calibratedConfidencePercent,
                lowerBoundSocPercent = uncertainty.arrivalSocIntervalPercent.lowerBound.coerceIn(0.0, 100.0),
                upperBoundSocPercent = uncertainty.arrivalSocIntervalPercent.upperBound.coerceIn(0.0, 100.0),
                confidenceTier = when {
                    isAccurate -> "VERIFIED_HIGH_CONFIDENCE"
                    calibratedConfidencePercent >= 50.0 -> "MODERATE_CONFIDENCE"
                    else -> "LOW_CONFIDENCE_UNCERTAIN"
                }
            )

            val feasibilityReasons = buildList {
                addAll(prediction.diagnosticsNotes)
                if (isNumericallyFeasible && isUncertain) {
                    add("امکان‌پذیری عددی برقرار است اما به دلیل ورودی‌های تخمینی یا عدم قطعیت محیطی، وضعیت سفر نامطمئن (UNCERTAIN) است.")
                }
                if (!isNumericallyFeasible) {
                    add("درصد شارژ پیش‌بینی‌شده در مقصد کمتر از حداقل ذخیره ایمنی (${prediction.reserveSocPercent.roundToInt()}%) است.")
                }
            }

            val reserveFeasibility = ReserveFeasibility(
                isFeasible = isOperationallyFeasible,
                requiredReserveSoc = Soc(prediction.reserveSocPercent.coerceIn(0.0, 100.0)),
                availableReserveSoc = arrivalSoc,
                feasibilityReasons = feasibilityReasons,
                isNumericallyFeasible = isNumericallyFeasible,
                isUncertain = isUncertain
            )

            val usableEnergyAtStartWh = (vehicle.batteryCapacity * 1000.0) *
                ((prediction.startSocPercent - prediction.reserveSocPercent).coerceAtLeast(0.0) / 100.0)
            val energyDeficitWh = (prediction.breakdown.netBatteryEnergyWh - usableEnergyAtStartWh).coerceAtLeast(0.0)

            val chargingRequirement = ChargingRequirement(
                requiresCharging = !isNumericallyFeasible || !isConservativeLowerBoundFeasible,
                estimatedEnergyDeficitWh = if (!isNumericallyFeasible || !isConservativeLowerBoundFeasible) energyDeficitWh else 0.0,
                recommendedMinimumReserveSoc = Soc(EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT),
                reasonsForRequiringCharging = when {
                    !isNumericallyFeasible -> listOf("Predicted arrival SoC below minimum reserve margin")
                    !isConservativeLowerBoundFeasible -> listOf("Worst-case uncertainty bound drops below reserve margin")
                    else -> emptyList()
                }
            )

            val freshnessHours = weatherAgeMs?.let { (it / (3600L * 1000L)).toInt().coerceAtLeast(0) }

            val dataQualityModel = DataQualityAndFreshness(
                dataFetchedAt = weatherAgeMs?.let { nowEpochMs - it } ?: nowEpochMs,
                isDataStale = isDataStale,
                dataFreshnessHours = freshnessHours,
                qualityScore = calibratedQualityScore,
                dataGaps = dataGaps.distinct()
            )

            val travelTimeBreakdown = CanonicalTravelTimeEngine.computeFromSegmentEstimates(
                segments = prediction.segments,
                totalDetourMeters = 0.0,
                totalChargingSeconds = 0.0,
                chargingStopCount = 0,
                weatherSpeedReductionRatio = 0.0,
                trafficCongestionMultiplier = 1.0,
                hasEstimatedSpeed = !effectiveContext.averageSpeedKmh.isAuthoritative()
            )

            val assumptionsModel = PredictionAssumptions(
                isRouteAssumptionValid = prediction.totalDistanceMeters > 0.0 && travelTimeBreakdown.isValid,
                assumedDrivingStyle = effectiveContext.drivingMode.name.lowercase(),
                assumedWeatherConditions = if (effectiveContext.ambientTemperatureCelsius.isAuthoritative()) "observed" else "assumed_standard_climate",
                assumedTrafficConditions = if (effectiveContext.trafficFreshnessTimestamp != null && !isTrafficStale) "observed_live" else "assumed_free_flow",
                vehicleConditionAssumption = if (isCalibratedVehicle) "verified_profile" else "assumed_fallback_profile",
                additionalNotes = "Dominant factor: ${prediction.dominantConsumptionFactor}; DataQuality=${prediction.dataQuality}; " +
                    "CanonicalTravelTimeExpectedSec=${travelTimeBreakdown.expectedTotalDuration.value.roundToInt()}"
            )

            val result = TripIntelligenceResult(
                isSuccess = true,
                requestTimestamp = nowEpochMs,
                completionTimestamp = System.currentTimeMillis(),
                vehicleAndBatteryInputs = VehicleAndBatteryInputs(
                    vehicle = vehicle,
                    batteryCapacityWh = vehicle.batteryCapacity * 1000.0,
                    currentSoc = Soc(prediction.startSocPercent.coerceIn(0.0, 100.0)),
                    minReserveSoc = prediction.reserveSocPercent
                ),
                routeAndSegmentInputs = RouteAndSegmentInputs(
                    route = route,
                    routeDistanceMeter = prediction.totalDistanceMeters,
                    elevationGainMeters = route.totalClimb,
                    elevationDescentMeters = route.totalDescent
                ),
                energyPrediction = energyPrediction,
                arrivalSocPrediction = arrivalSocPrediction,
                reserveFeasibility = reserveFeasibility,
                chargingRequirement = chargingRequirement,
                dataQualityAndFreshness = dataQualityModel,
                predictionAssumptions = assumptionsModel,
                decisionExplanation = DecisionExplanation(
                    keyInsight = when {
                        isOperationallyFeasible ->
                            "مسیر بدون نیاز به توقف شارژ میانی قابل پیمایش است (SoC مقصد: ${arrivalSoc.percentage.toInt()}%)"
                        isNumericallyFeasible && isUncertain ->
                            "مسیر از نظر عددی قابل پیمایش است (${arrivalSoc.percentage.toInt()}%) اما به دلیل داده‌های تخمینی نیازمند تأیید وضعیت باتری/ایستگاه است"
                        else ->
                            "برای پیمایش ایمن این مسیر توقف شارژ میانی الزامی است"
                    },
                    supportingDetails = feasibilityReasons + dataGaps.distinct() + listOf(
                        "زمان سفر کانونیکال (CanonicalTravelTimeEngine): ${(travelTimeBreakdown.expectedTotalDuration.value / 60.0).roundToInt()} دقیقه " +
                            "(بازه خوش‌بینانه ${(travelTimeBreakdown.optimisticTotalDuration.value / 60.0).roundToInt()} تا بدبینانه ${(travelTimeBreakdown.pessimisticTotalDuration.value / 60.0).roundToInt()} دقیقه)"
                    ),
                    confidenceLevel = calibratedConfidencePercent,
                    dataQuality = dataQualityModel,
                    assumptions = assumptionsModel
                )
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synthesizes an explicitly-assumed fallback context when the caller did not supply a [TripIntelligenceContext].
     * Every fallback attribute is tagged as [InputQualityAttribute.assumed] or [InputQualityAttribute.unknown]
     * so downstream confidence is capped and operational feasibility is marked uncertain.
     */
    private fun buildDefaultContext(
        request: TripIntelligenceRequest,
        vehicle: Vehicle,
        route: Route
    ): TripIntelligenceContext {
        val distance = request.routeDistanceMeters ?: route.distance.value
        val duration = route.estimatedTime.toDouble()
        val avgSpeedAttr = if (duration > 0.0 && distance > 0.0) {
            InputQualityAttribute.derived(
                value = ((distance / duration) * 3.6).coerceIn(10.0, 130.0),
                rationale = "Derived from route distance and duration"
            )
        } else {
            InputQualityAttribute.unknown("Route duration unavailable; average speed unknown")
        }

        val resolvedStartSoc = request.initialSocPercent?.let {
            EvDomainPolicyConfig.validateSocPercentOrThrow(it, "request.initialSocPercent")
        } ?: EvDomainPolicyConfig.DEFAULT_INITIAL_SOC_PERCENT

        val socAttribute = if (request.initialSocPercent != null && request.isInitialSocVerified) {
            InputQualityAttribute.userProvided(
                value = resolvedStartSoc,
                timestampEpochMs = System.currentTimeMillis(),
                rationale = "User-verified initial SoC from TripIntelligenceRequest"
            )
        } else {
            InputQualityAttribute.assumed(
                value = resolvedStartSoc,
                rationale = "Unverified fallback starting SoC (EvDomainPolicyConfig.DEFAULT_INITIAL_SOC_PERCENT)"
            )
        }

        val tempAttribute = request.ambientTemperatureCelsius?.let { tempC ->
            InputQualityAttribute.userProvided(
                value = tempC,
                timestampEpochMs = System.currentTimeMillis(),
                rationale = "User/request ambient temperature"
            )
        } ?: InputQualityAttribute.unknown("No live weather observation provided")

        return TripIntelligenceContext(
            vehicleId = vehicle.id,
            batteryCapacityKwh = if (vehicle.batteryCapacity > 5.0) {
                InputQualityAttribute.assumed(vehicle.batteryCapacity, "Vehicle model battery capacity (unconfirmed trim)")
            } else {
                InputQualityAttribute.unknown("Vehicle battery capacity missing")
            },
            curbWeightKg = if (vehicle.weight > 400.0) {
                InputQualityAttribute.assumed(vehicle.weight, "Vehicle model curb weight")
            } else {
                InputQualityAttribute.unknown("Vehicle weight missing")
            },
            additionalPayloadKg = InputQualityAttribute.unknown("Passenger/cargo payload not specified by user"),
            dragCoefficient = InputQualityAttribute.unknown("Aerodynamic drag coefficient not specified in fallback context"),
            frontalAreaM2 = InputQualityAttribute.unknown("Frontal area not specified in fallback context"),
            rollingResistanceCoeff = InputQualityAttribute.unknown("Tire rolling resistance coefficient not specified in fallback context"),
            driveEfficiency = InputQualityAttribute.unknown("Drivetrain efficiency not specified in fallback context"),
            currentSocPercent = socAttribute,
            targetArrivalSocPercent = InputQualityAttribute.assumed(
                EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                "Policy default target arrival SoC"
            ),
            minReserveSocPercent = InputQualityAttribute.assumed(
                EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                "Policy default minimum reserve SoC"
            ),
            routeDistanceMeters = distance,
            routeEstimatedDurationSeconds = duration,
            elevationGainMeters = request.elevationGainMeters?.let {
                InputQualityAttribute.assumed(it, "Route elevation gain from request")
            } ?: if (route.totalClimb > 0.0) {
                InputQualityAttribute.assumed(route.totalClimb, "Route elevation gain")
            } else {
                InputQualityAttribute.unknown("Route elevation gain unknown")
            },
            elevationLossMeters = request.elevationDescentMeters?.let {
                InputQualityAttribute.assumed(it, "Route elevation loss from request")
            } ?: if (route.totalDescent > 0.0) {
                InputQualityAttribute.assumed(route.totalDescent, "Route elevation loss")
            } else {
                InputQualityAttribute.unknown("Route elevation loss unknown")
            },
            averageSpeedKmh = avgSpeedAttr,
            ambientTemperatureCelsius = tempAttribute,
            windSpeedMps = InputQualityAttribute.unknown("No live wind observation provided"),
            windDirectionDegrees = InputQualityAttribute.unknown("No live wind direction provided"),
            hvacPowerKw = InputQualityAttribute.unknown("HVAC auxiliary load not observed; derived from thermal model"),
            drivingMode = DrivingMode.NORMAL,
            coverageStatus = IntelligenceCoverageStatus.PARTIAL_ROADS_ONLY
        )
    }
}
