package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.trip.intelligence.*
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class PredictiveChargingOrchestrator(
    private val vehicleCatalogRepository: VehicleCatalogRepository? = null,
    private val chargingStationRepository: ChargingStationCatalogRepository? = null,
    @Suppress("unused")
    private val predictiveEnergyOrchestrator: PredictiveEnergyOrchestrator = PredictiveEnergyOrchestrator(),
    private val roadRouteProvider: RoadRouteProvider? = null,
    private val clock: DomainClock = DomainClock.SYSTEM
) {
    data class ProjectedStationCandidate(
        val stationWithPorts: DomainChargingStationWithPorts,
        val selectedPort: DomainChargingPort,
        val canonicalConnector: CanonicalConnectorType,
        val portEligibility: StationPortEligibilityEvaluator.EligiblePortSelection,
        val distanceAlongRouteMeters: Double,
        val estimatedCrossTrackMeters: Double,
        val verifiedRoadDetourMeters: Double?,
        val detourDurationMinutes: Double?,
        val detourCalculationSource: DetourCalculationSource,
        val routeSegmentIndex: Int
    ) {
        val crossTrackMeters: Double get() = estimatedCrossTrackMeters
        val detourDistanceMeters: Double get() = verifiedRoadDetourMeters ?: estimatedCrossTrackMeters
    }

    private data class ReachableCandidateEval(
        val candidate: ProjectedStationCandidate,
        val legSimulation: SegmentPhysicsEnergyCalculator.LegEnergySimulationResult
    )

    suspend fun generateChargingPlan(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext? = null,
        candidateStationsOverride: List<DomainChargingStationWithPorts>? = null,
        clockOverride: DomainClock? = null,
        roadRouteProviderOverride: RoadRouteProvider? = null,
        requireConfirmedAvailablePort: Boolean = false,
        rejectStaleStations: Boolean = false
    ): Result<ChargingPlanCandidate> = try {
        require(request.trip.id.isNotBlank()) { "Trip information is required" }
        val effectiveClock = clockOverride ?: clock
        val effectiveRoadProvider = roadRouteProviderOverride ?: roadRouteProvider

        withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            val trip = request.trip
            val catalogVehicle = resolveCatalogVehicle(request)
            val batteryCapKwh = context?.usableBatteryCapacityKwh?.value?.takeIf { it.isFinite() && it > 0.0 }
                ?: catalogVehicle?.usableBatteryKwh?.takeIf { it.isFinite() && it > 0.0 }
                ?: context?.batteryCapacityKwh?.value?.takeIf { it.isFinite() && it > 0.0 }
                ?: request.vehicle?.batteryCapacity?.takeIf { it.isFinite() && it > 0.0 }
                ?: trip.vehicle.batteryCapacity.takeIf { it.isFinite() && it > 0.0 }
                ?: return@withContext failInsufficient("UNKNOWN_BATTERY_CAPACITY: ظرفیت قابل‌استفاده باتری خودرو نامشخص یا نامعتبر است؛ امکان محاسبه برنامه شارژ وجود ندارد.")

            val rawCurrentSoc = context?.currentSocPercent?.value ?: request.initialSocPercent
                ?: return@withContext failInsufficient("UNKNOWN_CURRENT_SOC: درصد شارژ اولیه باتری (SOC) نامشخص است.")
            if (!rawCurrentSoc.isFinite() || rawCurrentSoc !in 0.0..100.0) {
                return@withContext failInsufficient("INVALID_CURRENT_SOC: درصد شارژ باتری ($rawCurrentSoc%) خارج از بازه مجاز ۰ تا ۱۰۰ است.")
            }
            val currentSoc = rawCurrentSoc
            val isSocAuthoritative = context?.currentSocPercent?.isAuthoritative() == true || request.isInitialSocVerified

            val vehicleMaxDcKw = request.dcMaxChargingPowerKw?.takeIf { it.isFinite() && it > 0.0 }
                ?: request.vehicle?.maxChargingPower?.takeIf { it.isFinite() && it > 0.0 }
                ?: catalogVehicle?.dcMaxChargePowerKw
                ?: trip.vehicle.maxChargingPower.takeIf { it.isFinite() && it > 0.0 }
            val vehicleMaxAcKw = request.acMaxChargingPowerKw?.takeIf { it.isFinite() && it > 0.0 } ?: catalogVehicle?.acMaxChargePowerKw
            if (vehicleMaxDcKw == null && vehicleMaxAcKw == null) {
                return@withContext failInsufficient("UNKNOWN_VEHICLE_MAX_CHARGING_POWER: حداکثر توان شارژ خودرو (DC و AC) نامشخص است؛ از توان پیش‌فرض استفاده نشد.")
            }

            val explicitConnectors = request.supportedConnectors.ifEmpty { catalogVehicle?.connectorTypes.orEmpty() }
            if (StationPortEligibilityEvaluator.parseExplicitVehicleConnectors(explicitConnectors).isEmpty()) {
                return@withContext failInsufficient("UNKNOWN_VEHICLE_CONNECTOR: نوع درگاه شارژ خودرو مشخص نشده یا مبهم است؛ حدس از روی برند خودرو مجاز نیست.")
            }

            val minReserveSoc = (context?.minReserveSocPercent?.value ?: EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT).coerceIn(5.0, 40.0)
            val defaultTargetChargeSoc = EvDomainPolicyConfig.DEFAULT_TARGET_CHARGING_SOC_PERCENT
                .coerceIn(minReserveSoc + 15.0, EvDomainPolicyConfig.MAX_AUTOMATED_TARGET_CHARGING_SOC_PERCENT)
            val routeDistanceMeters = (request.routeDistanceMeters ?: context?.routeDistanceMeters ?: trip.totalDistance.meters)
                .takeIf { it.isFinite() && it > 0.0 }
                ?: return@withContext failInsufficient("INVALID_ROUTE_DISTANCE: مسافت مسیر نامعتبر یا صفر است.")

            val effectiveContext = context ?: SegmentPhysicsEnergyCalculator.buildExplicitContext(
                request, catalogVehicle, batteryCapKwh, currentSoc, isSocAuthoritative, minReserveSoc, routeDistanceMeters, effectiveClock.nowEpochMs()
            )
            val routePolyline = buildRoutePolyline(request)
            val segmentProfiles = SegmentPhysicsEnergyCalculator.buildRouteSegmentProfiles(request, effectiveContext, routePolyline, routeDistanceMeters)

            val rawStations = candidateStationsOverride ?: loadAllCatalogStations()
            val (projectedCandidates, rejectedReasons) = CorridorStationProjector.evaluateAndProjectStations(
                rawStations, routePolyline, routeDistanceMeters, request.maxRoadDetourMeters, request.maxCrossTrackMeters,
                explicitConnectors, vehicleMaxDcKw, vehicleMaxAcKw, effectiveClock, effectiveRoadProvider,
                requireConfirmedAvailablePort, rejectStaleStations
            )

            if (projectedCandidates.isEmpty()) {
                val reasonList = rejectedReasons.values.distinct().joinToString("، ").ifBlank { "فقدان ایستگاه در کاتالوگ" }
                return@withContext failInfeasible("NO_FEASIBLE_STATION: هیچ ایستگاه شارژ سازگار در حریم مسیر یافت نشد. دلایل رد: $reasonList")
            }

            val safetyBufferSoc = 3.0
            val plannedStops = mutableListOf<PlannedChargingStop>()
            val usedStationIds = LinkedHashSet<String>()
            val planWarnings = mutableListOf<String>()
            val planAssumptions = LinkedHashSet<String>()

            if (!isSocAuthoritative) {
                planWarnings.add("درصد شارژ اولیه باتری (SOC) تأییدنشده یا تخمینی است؛ حاشیه عدم‌قطعیت افزایش یافت.")
                planAssumptions.add("UNVERIFIED_INITIAL_SOC")
            }
            val rawAmbientTemp = effectiveContext.ambientTemperatureCelsius.value?.takeIf { it.isFinite() && it in -60.0..70.0 }
            val ambientTempC = rawAmbientTemp ?: EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS
            val isTempMeasured = rawAmbientTemp != null && effectiveContext.ambientTemperatureCelsius.isAuthoritative()
            if (!isTempMeasured) { planAssumptions.add("MODEL_ASSUMPTION:AMBIENT_TEMPERATURE"); planAssumptions.add("ASSUMED_AMBIENT_TEMPERATURE") }

            var currentDistMeters = 0.0
            var runningSocPercent = currentSoc
            var cumulativeElapsedSeconds = 0.0
            val startTimeEpochMs = effectiveClock.nowEpochMs()
            var stopIndex = 0

            while (currentDistMeters < routeDistanceMeters && stopIndex < 8) {
                coroutineContext.ensureActive()
                val simToDest = CanonicalEnergyEngine.calculateLegEnergy(
                    segmentProfiles, currentDistMeters, routeDistanceMeters, 0.0, runningSocPercent, batteryCapKwh, effectiveContext
                ).getOrThrow()
                planAssumptions.addAll(simToDest.assumptions)

                val needsStop = simToDest.arrivalSocMinPercent < (minReserveSoc + safetyBufferSoc) ||
                    (plannedStops.isEmpty() && simToDest.arrivalSocNominalPercent < (minReserveSoc + 10.0))
                if (!needsStop && (plannedStops.isNotEmpty() || simToDest.arrivalSocMinPercent >= minReserveSoc + 15.0)) break

                val forwardCandidates = projectedCandidates.filter {
                    it.stationWithPorts.station.id !in usedStationIds && it.distanceAlongRouteMeters > currentDistMeters + 500.0
                }
                if (forwardCandidates.isEmpty()) {
                    return@withContext failInfeasible("INSUFFICIENT_STATIONS_ON_ROUTE: ایستگاه شارژ سازگار و غیرتکراری در ادامه مسیر (پس از کیلومتر ${(currentDistMeters / 1000.0).roundToInt()}) یافت نشد.")
                }

                val reachableEvaluations = mutableListOf<ReachableCandidateEval>()
                for (cand in forwardCandidates) {
                    val detourEntryMeters = (cand.verifiedRoadDetourMeters ?: 0.0) * 0.5
                    val legSim = CanonicalEnergyEngine.calculateLegEnergy(
                        segmentProfiles, currentDistMeters, cand.distanceAlongRouteMeters, detourEntryMeters, runningSocPercent, batteryCapKwh, effectiveContext
                    ).getOrThrow()
                    if (legSim.arrivalSocNominalPercent >= minReserveSoc && legSim.arrivalSocMinPercent >= 5.0) {
                        reachableEvaluations.add(ReachableCandidateEval(cand, legSim))
                    } else {
                        rejectedReasons[cand.stationWithPorts.station.id] =
                            "UNREACHABLE_WITH_CURRENT_SOC (شارژ ورود تخمینی ${"%.1f".format(legSim.arrivalSocNominalPercent)}% کمتر از حد ذخیره ایمنی ${minReserveSoc.toInt()}%)"
                    }
                }
                if (reachableEvaluations.isEmpty()) {
                    return@withContext failInfeasible("NO_REACHABLE_STATION: هیچ‌یک از ایستگاه‌های پیش‌رو با شارژ فعلی (${"%.1f".format(runningSocPercent)}%) و رعایت ذخیره ایمنی (${minReserveSoc.toInt()}%) قابل دسترسی نیستند.")
                }

                val chosenEval = reachableEvaluations.maxWithOrNull(compareBy { eval ->
                    val elig = eval.candidate.portEligibility
                    val progressScore = (eval.candidate.distanceAlongRouteMeters - currentDistMeters) / routeDistanceMeters
                    val powerScore = elig.effectivePeakPowerKw / 150.0
                    val trustBonus = if (elig.isFullyVerifiedAndFresh) 0.30 else if (elig.isStationVerifiedWithEvidence && elig.stationFreshness.isFresh) 0.15 else 0.0
                    val detourPenalty = (eval.candidate.detourDistanceMeters / request.maxRoadDetourMeters.coerceAtLeast(1000.0)) * 0.20
                    progressScore * 0.55 + powerScore * 0.25 + trustBonus - detourPenalty
                })!!

                val chosenCand = chosenEval.candidate
                val elig = chosenCand.portEligibility
                usedStationIds.add(chosenCand.stationWithPorts.station.id)
                planAssumptions.addAll(chosenEval.legSimulation.assumptions)

                val simAfterStopToDest = CanonicalEnergyEngine.calculateLegEnergy(
                    segmentProfiles, chosenCand.distanceAlongRouteMeters, routeDistanceMeters,
                    (chosenCand.verifiedRoadDetourMeters ?: 0.0) * 0.5, defaultTargetChargeSoc, batteryCapKwh, effectiveContext
                ).getOrThrow()
                val socNeededAfterStopForDest = minReserveSoc + safetyBufferSoc + (simAfterStopToDest.maxEnergyKwh / batteryCapKwh) * 100.0
                val departureSocAtStop = socNeededAfterStopForDest
                    .coerceAtLeast(chosenEval.legSimulation.arrivalSocNominalPercent + 15.0)
                    .coerceAtMost(defaultTargetChargeSoc)
                    .coerceIn((chosenEval.legSimulation.arrivalSocNominalPercent + 5.0).coerceAtMost(95.0), EvDomainPolicyConfig.MAX_AUTOMATED_TARGET_CHARGING_SOC_PERCENT)

                val isDcPort = elig.canonicalConnector.isDcFast || elig.port.isDc
                val durationBreakdown = calculateSocTaperChargingDuration(
                    batteryCapKwh, chosenEval.legSimulation.arrivalSocNominalPercent, departureSocAtStop,
                    elig.portMaxPowerKw, elig.vehicleMaxPowerForPortKw, ambientTempC, elig.isFullyVerifiedAndFresh, isDcPort
                )
                cumulativeElapsedSeconds += chosenEval.legSimulation.durationSeconds + (chosenCand.detourDurationMinutes ?: 0.0) * 60.0
                val arrivalTimestampMs = startTimeEpochMs + (cumulativeElapsedSeconds * 1000.0).roundToLong()

                val (stop, stopWarnings) = ChargingPlanStopBuilder.buildPlannedChargingStop(
                    chosenCand, chosenEval.legSimulation, departureSocAtStop, durationBreakdown,
                    arrivalTimestampMs, isTempMeasured, isSocAuthoritative, minReserveSoc
                )
                planWarnings.addAll(stopWarnings)
                plannedStops.add(stop)
                cumulativeElapsedSeconds += durationBreakdown.estimatedMinutes * 60.0
                currentDistMeters = chosenCand.distanceAlongRouteMeters
                runningSocPercent = departureSocAtStop
                stopIndex++
            }

            val finalLegSim = CanonicalEnergyEngine.calculateLegEnergy(
                segmentProfiles, currentDistMeters, routeDistanceMeters, 0.0, runningSocPercent, batteryCapKwh, effectiveContext
            ).getOrThrow()
            val finalArrivalSoc = finalLegSim.arrivalSocNominalPercent.coerceIn(0.0, 100.0)
            if (finalArrivalSoc < minReserveSoc) {
                return@withContext failInfeasible("DESTINATION_UNREACHABLE_AFTER_STOPS: حتی پس از ${plannedStops.size} توقف شارژ، درصد شارژ ورود به مقصد (${"%.1f".format(finalArrivalSoc)}%) کمتر از ذخیره ایمنی (${minReserveSoc.toInt()}%) است.")
            }

            Result.success(
                ChargingPlanStopBuilder.assembleChargingPlanCandidate(
                    trip.id, plannedStops, segmentProfiles, finalLegSim, finalArrivalSoc, minReserveSoc,
                    isSocAuthoritative, effectiveContext, planWarnings, planAssumptions, rejectedReasons
                )
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: TripIntelligenceError) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(TripIntelligenceError.SystemFailure("خطا در تولید برنامه هوشمند شارژ: ${e.message}", e))
    }

    private fun failInsufficient(msg: String): Result<ChargingPlanCandidate> = Result.failure(TripIntelligenceError.InsufficientData(msg))
    private fun failInfeasible(msg: String): Result<ChargingPlanCandidate> = Result.failure(TripIntelligenceError.InfeasiblePlan(msg))

    private suspend fun resolveCatalogVehicle(request: TripIntelligenceRequest): DomainVehicle? {
        val repo = vehicleCatalogRepository ?: return null
        val vehId = (request.vehicle ?: request.trip.vehicle).id
        if (vehId.isBlank()) return null
        return try { repo.getVehicleById(vehId) } catch (e: Exception) { if (e is CancellationException) throw e; null }
    }

    private fun buildRoutePolyline(request: TripIntelligenceRequest): List<GeoCoordinate> {
        if (request.routePolyline.size >= 2) return request.routePolyline
        val route = request.trip.route
        if (route.waypoints.size >= 2) return route.waypoints
        if (route.segments.isNotEmpty()) return listOf(route.segments.first().start) + route.segments.map { it.end }
        return listOf(request.trip.startLocation, request.trip.endLocation)
    }

    private suspend fun loadAllCatalogStations(): List<DomainChargingStationWithPorts> {
        val catalogRepo = chargingStationRepository ?: return emptyList()
        return try { catalogRepo.observeAllStationsWithPorts().firstOrNull().orEmpty() } catch (e: Exception) { if (e is CancellationException) throw e; emptyList() }
    }

    data class ChargingDurationBreakdown(val deliveredEnergyKwh: Double, val averageEffectivePowerKw: Double, val estimatedMinutes: Double, val uncertaintyMinutes: Double)

    companion object {
        fun calculateSocTaperChargingDuration(
            batteryCapacityKwh: Double, startSocPercent: Double, targetSocPercent: Double,
            stationMaxPowerKw: Double, vehicleMaxAcceptanceKw: Double,
            ambientTemperatureCelsius: Double = EvDomainPolicyConfig.ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS,
            isStationVerified: Boolean = true,
            isDcFast: Boolean = true
        ): ChargingDurationBreakdown {
            require(batteryCapacityKwh.isFinite() && batteryCapacityKwh > 0.0) { "batteryCapacityKwh ($batteryCapacityKwh) must be positive and finite" }
            require(stationMaxPowerKw.isFinite() && stationMaxPowerKw > 0.0) { "stationMaxPowerKw ($stationMaxPowerKw) must be positive and finite" }
            require(vehicleMaxAcceptanceKw.isFinite() && vehicleMaxAcceptanceKw > 0.0) { "vehicleMaxAcceptanceKw ($vehicleMaxAcceptanceKw) must be positive and finite" }
            val hardwarePeakKw = min(stationMaxPowerKw, vehicleMaxAcceptanceKw)
            val session = CanonicalEnergyEngine.ChargeCurveEngine.simulateChargingSession(
                usableBatteryKwh = batteryCapacityKwh,
                arrivalSocPercent = startSocPercent.coerceIn(0.0, 99.0),
                targetSocPercent = targetSocPercent.coerceIn(startSocPercent + 0.5, 100.0),
                effectivePeakPowerKw = hardwarePeakKw,
                isDcFast = isDcFast,
                ambientTemperatureCelsius = ambientTemperatureCelsius
            )
            val totalMinutes = session.chargingDuration.toMinutes() + 2.0
            val uncertaintyFraction = (if (isStationVerified) 0.12 else 0.30) + (if (ambientTemperatureCelsius < 5.0 || ambientTemperatureCelsius > 38.0) 0.10 else 0.0)
            return ChargingDurationBreakdown(session.energyAddedToBattery.value, session.averagePowerKw.value, totalMinutes, totalMinutes * uncertaintyFraction)
        }
    }
}
