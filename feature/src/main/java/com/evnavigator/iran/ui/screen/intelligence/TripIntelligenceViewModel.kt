package com.evnavigator.iran.ui.screen.intelligence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.domain.geocoding.GeocodingAndReverseGeocodingService
import com.evnavigator.iran.domain.geocoding.GeocodingPlaceType
import com.evnavigator.iran.domain.geocoding.GeocodingResolvedPlace
import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.trip.BatteryAndSocState
import com.evnavigator.iran.domain.intelligence.trip.CanonicalFinalRouteAssembler
import com.evnavigator.iran.domain.intelligence.trip.CorridorStationProjector
import com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute
import com.evnavigator.iran.domain.intelligence.trip.ParameterProvenanceStatus
import com.evnavigator.iran.domain.intelligence.trip.ProvenanceAndDiagnosticsReport
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.intelligence.trip.TripExecutionContext
import com.evnavigator.iran.domain.intelligence.trip.TripIntelligenceOrchestrator
import com.evnavigator.iran.domain.model.*
import com.evnavigator.iran.domain.model.catalog.ConnectorNormalizer
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainPendingNavigationHandoff
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.DomainVehicleSocSource
import com.evnavigator.iran.domain.model.catalog.VehicleDataQualityEvaluator
import com.evnavigator.iran.domain.model.catalog.VehicleQualityAssessment
import com.evnavigator.iran.domain.model.trip.intelligence.*
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * ViewModel for the Trip Intelligence & Adaptive Route Analysis Screen (V13.3.1 Sections 2, 4, 11, 16 & 21).
 *
 * V13.3.1 Architectural Guarantees:
 * 1. Full-Iran geocoding and reverse geocoding via [GeocodingAndReverseGeocodingService] supporting
 *    exact street addresses, house numbers, POIs, charging stations, provincial/county cities, and
 *    arbitrary `"lat, lon"` coordinates (eliminating the 11-city hardcoded dictionary restriction).
 * 2. Multi-leg [FinalPlannedRoute] construction via [CanonicalFinalRouteAssembler.assembleFromChargingStops]
 *    so `N` charging stops produce `N + 1` legs (`Origin -> Stop 1 -> ... -> Destination`) with
 *    sliced polylines, rebuilt segments, and rebuilt maneuvers.
 * 3. Unified default initial SoC ([EvDomainPolicyConfig.DEFAULT_INITIAL_SOC_PERCENT] = 80.0%) and
 *    strict validation rejecting out-of-range SoC (`< 0%` or `> 100%`) instead of silent clamping.
 */
@HiltViewModel
class TripIntelligenceViewModel @Inject constructor(
    private val tripIntelligenceOrchestrator: TripIntelligenceOrchestrator,
    private val vehicleCatalogRepository: VehicleCatalogRepository,
    private val chargingStationRepository: ChargingStationCatalogRepository,
    private val realRoadRoutingEngine: IranRealRoadRoutingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false)
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripIntelligenceUiState())
    val uiState: StateFlow<TripIntelligenceUiState> = _uiState.asStateFlow()

    private val _tripIntelligenceResult = MutableStateFlow<TripIntelligenceResult?>(null)
    val tripIntelligenceResult: StateFlow<TripIntelligenceResult?> = _tripIntelligenceResult.asStateFlow()

    private val _activeFinalPlannedRoute = MutableStateFlow<FinalPlannedRoute?>(null)
    val activeFinalPlannedRoute: StateFlow<FinalPlannedRoute?> = _activeFinalPlannedRoute.asStateFlow()

    private val _activeExecutionContext = MutableStateFlow<TripExecutionContext?>(null)
    val activeExecutionContext: StateFlow<TripExecutionContext?> = _activeExecutionContext.asStateFlow()

    private val routeVersionCounter = AtomicLong(0L)
    private var activeAnalysisJob: Job? = null
    private var activeVehicleDomain: DomainVehicle? = null
    private var activeVehicleModel: Vehicle? = null
    private var activeVehicleSupportedConnectors: List<String> = emptyList()
    private var activeVehicleMaxDcKw: Double? = null
    private var activeVehicleMaxAcKw: Double? = null
    private var activeCatalogStations: List<DomainChargingStationWithPorts> = emptyList()

    init {
        viewModelScope.launch {
            chargingStationRepository.observeAllStationsWithPorts().collect { stations ->
                activeCatalogStations = stations
            }
        }
        viewModelScope.launch {
            vehicleCatalogRepository.observeCurrentSocState().collect { socState ->
                if (socState != null && socState.socPercent.isFinite() && socState.socPercent in 0.0..100.0) {
                    _uiState.update {
                        it.copy(
                            initialSocPercent = socState.socPercent,
                            isInitialSocVerified = socState.isVerified
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            vehicleCatalogRepository.observeActiveVehicle().collect { domVeh ->
                activeVehicleDomain = domVeh
                val quality = VehicleDataQualityEvaluator.evaluate(domVeh)
                if (domVeh != null) {
                    activeVehicleSupportedConnectors = ConnectorNormalizer.normalizeConnectorStrings(domVeh.connectorTypes)
                    activeVehicleMaxDcKw = domVeh.dcMaxChargePowerKw
                    activeVehicleMaxAcKw = domVeh.acMaxChargePowerKw
                    val maxChargePower = domVeh.dcMaxChargePowerKw ?: domVeh.acMaxChargePowerKw ?: 0.0
                    activeVehicleModel = Vehicle(
                        id = domVeh.id,
                        make = domVeh.manufacturer,
                        model = domVeh.model,
                        year = domVeh.modelYear,
                        batteryCapacity = domVeh.usableBatteryKwh,
                        usableCapacity = domVeh.usableBatteryKwh,
                        efficiency = domVeh.consumptionWhPerKm,
                        maxChargingPower = maxChargePower,
                        connectorTypes = activeVehicleSupportedConnectors,
                        weight = domVeh.curbMassKg
                    )
                    _uiState.update {
                        it.copy(
                            activeVehicleLabel = "${domVeh.manufacturer} ${domVeh.model} (${domVeh.usableBatteryKwh.toInt()} kWh قابل‌استفاده / ${domVeh.grossBatteryKwh.toInt()} kWh کل)",
                            vehicleQualityAssessment = quality
                        )
                    }
                } else {
                    activeVehicleModel = null
                    activeVehicleSupportedConnectors = emptyList()
                    activeVehicleMaxDcKw = null
                    activeVehicleMaxAcKw = null
                    _uiState.update {
                        it.copy(
                            activeVehicleLabel = "خودرویی انتخاب نشده است",
                            vehicleQualityAssessment = quality
                        )
                    }
                }
            }
        }
    }

    fun updateStartLocation(location: String) {
        val resolved = GeocodingAndReverseGeocodingService.geocodeQuery(location, activeCatalogStations)
        _uiState.update {
            it.copy(
                startLocation = location,
                resolvedOriginPlace = resolved,
                error = null
            )
        }
    }

    fun updateDestination(destination: String) {
        val resolved = GeocodingAndReverseGeocodingService.geocodeQuery(destination, activeCatalogStations)
        _uiState.update {
            it.copy(
                destination = destination,
                resolvedDestinationPlace = resolved,
                error = null
            )
        }
    }

    fun updateInitialSocPercent(socPercent: Double) {
        if (!socPercent.isFinite() || socPercent < 0.0 || socPercent > 100.0) {
            _uiState.update {
                it.copy(
                    error = "درصد شارژ باتری ($socPercent%) خارج از بازه مجاز [0% تا 100%] است."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                initialSocPercent = socPercent,
                isInitialSocVerified = true,
                error = null
            )
        }
        viewModelScope.launch {
            vehicleCatalogRepository.setCurrentSocPercent(socPercent, DomainVehicleSocSource.USER_MANUAL_INPUT)
        }
    }

    fun updateAmbientTemperatureCelsius(temperatureCelsius: Double?) {
        if (temperatureCelsius != null && (!temperatureCelsius.isFinite() || temperatureCelsius !in -50.0..65.0)) {
            _uiState.update {
                it.copy(error = "دمای محیط ($temperatureCelsius°C) خارج از بازه فیزیکی مجاز [-50°C تا +65°C] است.")
            }
            return
        }
        _uiState.update {
            it.copy(
                ambientTemperatureCelsius = temperatureCelsius,
                error = null
            )
        }
    }

    fun selectPresetCorridor(originFa: String, destFa: String) {
        val originResolved = GeocodingAndReverseGeocodingService.geocodeQuery(originFa, activeCatalogStations)
        val destResolved = GeocodingAndReverseGeocodingService.geocodeQuery(destFa, activeCatalogStations)
        _uiState.update {
            it.copy(
                startLocation = originFa,
                destination = destFa,
                resolvedOriginPlace = originResolved,
                resolvedDestinationPlace = destResolved,
                error = null
            )
        }
        calculateTrip()
    }

    fun prepareNavigationHandoff(onReadyToNavigate: () -> Unit) {
        val startPlace = GeocodingAndReverseGeocodingService.geocodeQuery(_uiState.value.startLocation, activeCatalogStations)
        val endPlace = GeocodingAndReverseGeocodingService.geocodeQuery(_uiState.value.destination, activeCatalogStations)
        if (startPlace == null || endPlace == null) {
            _uiState.update {
                it.copy(error = "مبدأ یا مقصد انتخابی معتبر نیست؛ امکان انتقال به ناوبری وجود ندارد.")
            }
            return
        }
        viewModelScope.launch {
            vehicleCatalogRepository.setPendingNavigationHandoff(
                DomainPendingNavigationHandoff(
                    originName = startPlace.title,
                    originLat = startPlace.coordinate.latitude,
                    originLon = startPlace.coordinate.longitude,
                    destinationName = endPlace.title,
                    destinationLat = endPlace.coordinate.latitude,
                    destinationLon = endPlace.coordinate.longitude,
                    autoStart = true
                )
            )
            onReadyToNavigate()
        }
    }

    fun calculateTrip() {
        val startQuery = _uiState.value.startLocation.trim()
        val destQuery = _uiState.value.destination.trim()
        if (startQuery.isEmpty() || destQuery.isEmpty()) {
            _uiState.update { it.copy(error = "لطفاً مبدأ و مقصد را وارد کنید") }
            return
        }

        val startPlace = GeocodingAndReverseGeocodingService.geocodeQuery(startQuery, activeCatalogStations)
        val endPlace = GeocodingAndReverseGeocodingService.geocodeQuery(destQuery, activeCatalogStations)
        if (startPlace == null || endPlace == null) {
            val missingName = if (startPlace == null) "مبدأ «$startQuery»" else "مقصد «$destQuery»"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "$missingName در سیستم آدرس‌یابی و شهرهای ایران یافت نشد. لطفاً آدرس، نام خیابان، نام شهر یا مختصات جغرافیایی (lat, lon) معتبر وارد کنید."
                )
            }
            return
        }

        val startCoord = startPlace.coordinate
        val endCoord = endPlace.coordinate
        if (CorridorStationProjector.haversineMeters(startCoord, endCoord) < 25.0) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "مبدأ و مقصد یکسان یا بسیار نزدیک هستند. لطفاً دو نقطه متفاوت انتخاب کنید."
                )
            }
            return
        }

        val domVeh = activeVehicleDomain
        val vehQuality = VehicleDataQualityEvaluator.evaluate(domVeh)
        val selectedVehicle = activeVehicleModel
        if (domVeh == null || selectedVehicle == null || !vehQuality.isValidForPlanning) {
            val detail = if (domVeh == null) {
                "هیچ خودروی فعالی انتخاب نشده است."
            } else {
                "مشخصات فنی خودروی «${domVeh.manufacturer} ${domVeh.model}» ناقص یا نامعتبر است (${vehQuality.missingOrInvalidFields.joinToString(", ")})."
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    vehicleQualityAssessment = vehQuality,
                    error = "$detail لطفاً ابتدا از بخش «انتخاب خودرو» یک خودروی معتبر انتخاب کنید."
                )
            }
            return
        }

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    resolvedOriginPlace = startPlace,
                    resolvedDestinationPlace = endPlace,
                    vehicleQualityAssessment = vehQuality
                )
            }

            try {
                // Route exclusively through IranRealRoadRoutingEngine — zero synthetic circuityFactor or 95 km/h fallback
                val realRoute = realRoadRoutingEngine.calculateRealRoute(startCoord, endCoord)
                val sourceClassification = classifyRouteSource(realRoute)
                val request = buildAuthenticTripIntelligenceRequest(
                    startName = startPlace.title,
                    destName = endPlace.title,
                    startCoord = startCoord,
                    endCoord = endCoord,
                    realRoute = realRoute,
                    sourceClassification = sourceClassification,
                    domVeh = domVeh,
                    selectedVehicle = selectedVehicle
                )

                val result = tripIntelligenceOrchestrator.processTripIntelligence(request, null)

                result.fold(
                    onSuccess = { intelligenceResult ->
                        _tripIntelligenceResult.value = intelligenceResult
                        val version = routeVersionCounter.incrementAndGet()
                        val plannedRoute = buildFinalPlannedRouteFromIntelligenceResult(
                            realRoute = realRoute,
                            routeVersion = version,
                            sourceClassification = sourceClassification,
                            domVeh = domVeh,
                            vehQuality = vehQuality,
                            startPlace = startPlace,
                            endPlace = endPlace,
                            intelligenceResult = intelligenceResult
                        )
                        val batteryState = BatteryAndSocState(
                            grossCapacityKwh = domVeh.grossBatteryKwh,
                            usableCapacityKwh = domVeh.usableBatteryKwh,
                            currentSocPercent = _uiState.value.initialSocPercent,
                            reserveSocPercent = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                            isSocAuthoritative = _uiState.value.isInitialSocVerified
                        )
                        val execContext = CanonicalFinalRouteAssembler.buildExecutionContext(
                            vehicle = domVeh,
                            batteryAndSoc = batteryState,
                            finalPlannedRoute = plannedRoute,
                            ambientTemperatureCelsius = _uiState.value.ambientTemperatureCelsius,
                            isTemperatureAuthoritative = _uiState.value.ambientTemperatureCelsius != null
                        )
                        _activeFinalPlannedRoute.value = plannedRoute
                        _activeExecutionContext.value = execContext

                        val partialWarning = if (intelligenceResult.isPartialSuccess) {
                            intelligenceResult.chargingPlanError?.message
                                ?: "هشدار (PARTIAL_SUCCESS): تحلیل انرژی انجام شد اما تولید برنامه شارژ برای این مسیر امکان‌پذیر نبود."
                        } else null

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasResults = true,
                                executionStatus = intelligenceResult.executionStatus,
                                routeSourceClassification = sourceClassification,
                                activeRouteId = plannedRoute.routeId,
                                activeRouteVersion = plannedRoute.routeVersion,
                                error = partialWarning
                            )
                        }
                    },
                    onFailure = { exception ->
                        if (exception is CancellationException) throw exception
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                executionStatus = TripIntelligenceExecutionStatus.FAILED,
                                error = exception.message ?: "خطا در محاسبه اطلاعات هوشمند سفر"
                            )
                        }
                    }
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        executionStatus = TripIntelligenceExecutionStatus.FAILED,
                        error = e.message ?: "خطای غیرمنتظره در تحلیل مسیر جاده‌ای"
                    )
                }
            }
        }
    }

    private fun classifyRouteSource(realRoute: RealRoadRouteResult): RouteSourceClassification = when {
        realRoute.isOnlineRouting -> RouteSourceClassification.ONLINE_ROUTING_PROVIDER
        OsmDatasetManager.isUsingImportedOsmDataset() -> RouteSourceClassification.FULL_OFFLINE_OSM
        else -> RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK
    }

    fun resolveCityCoordinate(query: String): GeoCoordinate? =
        GeocodingAndReverseGeocodingService.geocodeQuery(query, activeCatalogStations)?.coordinate

    /**
     * Builds an authentic [TripIntelligenceRequest] exclusively from real [RealRoadRouteResult]
     * segments, authentic segment elevations, and real catalog stations projected via [CorridorStationProjector].
     */
    private fun buildAuthenticTripIntelligenceRequest(
        startName: String,
        destName: String,
        startCoord: GeoCoordinate,
        endCoord: GeoCoordinate,
        realRoute: RealRoadRouteResult,
        sourceClassification: RouteSourceClassification,
        domVeh: DomainVehicle,
        selectedVehicle: Vehicle
    ): TripIntelligenceRequest {
        val totalDistanceMeters = realRoute.totalDistanceMeters.coerceAtLeast(1.0)
        val totalTimeSeconds = realRoute.totalDurationSeconds.toLong().coerceAtLeast(1L)

        val elevationPoints = mutableListOf<ElevationPoint>()
        var totalGainM = 0.0
        var totalLossM = 0.0
        var hasAuthenticElevation = false

        val domainSegments = buildDomainSegmentsFromRealRoute(
            realRoute = realRoute,
            startCoord = startCoord,
            endCoord = endCoord,
            sourceClassification = sourceClassification,
            onElevationPoint = { pt ->
                hasAuthenticElevation = true
                elevationPoints.add(pt)
            },
            onElevationDelta = { delta ->
                if (delta > 0.0) totalGainM += delta else totalLossM += (-delta)
            }
        )

        val route = Route(
            id = realRoute.routeId,
            start = startCoord,
            end = endCoord,
            segments = domainSegments,
            distance = Distance.fromMeters(totalDistanceMeters),
            estimatedTime = totalTimeSeconds,
            elevationProfile = elevationPoints,
            waypoints = realRoute.polyline
        )

        val projectedStations = buildProjectedStationRecords(realRoute.polyline, totalDistanceMeters)

        val trip = Trip(
            id = "trip_${realRoute.routeId}",
            name = "$startName به $destName",
            startLocation = startCoord,
            endLocation = endCoord,
            waypoints = realRoute.polyline,
            vehicle = selectedVehicle,
            route = route,
            chargingStations = emptyList(),
            chargingStationRecords = projectedStations,
            totalDistance = Distance.fromMeters(totalDistanceMeters),
            totalTime = totalTimeSeconds,
            estimatedEnergyConsumption = 0.0
        )

        val userTemp = _uiState.value.ambientTemperatureCelsius
        val isVerifiedCatalogSpec = domVeh.isVerified

        return TripIntelligenceRequest(
            trip = trip,
            vehicle = selectedVehicle,
            initialSocPercent = _uiState.value.initialSocPercent,
            isInitialSocVerified = _uiState.value.isInitialSocVerified,
            ambientTemperatureCelsius = userTemp,
            elevationGainMeters = if (hasAuthenticElevation) totalGainM else null,
            elevationDescentMeters = if (hasAuthenticElevation) totalLossM else null,
            routePolyline = realRoute.polyline,
            routeDistanceMeters = totalDistanceMeters,
            supportedConnectors = activeVehicleSupportedConnectors,
            dcMaxChargingPowerKw = activeVehicleMaxDcKw,
            acMaxChargingPowerKw = activeVehicleMaxAcKw,
            vehicleSpecOrigin = if (isVerifiedCatalogSpec) InputOrigin.VERIFIED_DATABASE else InputOrigin.USER_INPUT,
            elevationOrigin = if (hasAuthenticElevation) InputOrigin.EMBEDDED_CORRIDOR_APPROXIMATION else InputOrigin.UNKNOWN,
            ambientTemperatureOrigin = if (userTemp != null) InputOrigin.USER_INPUT else InputOrigin.UNKNOWN
        )
    }

    private fun buildDomainSegmentsFromRealRoute(
        realRoute: RealRoadRouteResult,
        startCoord: GeoCoordinate,
        endCoord: GeoCoordinate,
        sourceClassification: RouteSourceClassification,
        onElevationPoint: (ElevationPoint) -> Unit = {},
        onElevationDelta: (Double) -> Unit = {}
    ): List<RouteSegment> {
        return if (realRoute.segments.isNotEmpty()) {
            realRoute.segments.map { seg ->
                val p1 = seg.polyline.firstOrNull() ?: startCoord
                val p2 = seg.polyline.lastOrNull() ?: endCoord
                val sElev = seg.startElevationMeters
                val eElev = seg.endElevationMeters
                val segElevPts = buildList {
                    if (sElev != null && sElev.isFinite()) {
                        val pt = ElevationPoint(p1, Elevation(Altitude.ofMeters(sElev)), Distance.fromMeters(seg.cumulativeStartMeters))
                        onElevationPoint(pt)
                        add(pt)
                    }
                    if (eElev != null && eElev.isFinite()) {
                        val pt = ElevationPoint(p2, Elevation(Altitude.ofMeters(eElev)), Distance.fromMeters(seg.cumulativeEndMeters))
                        onElevationPoint(pt)
                        add(pt)
                    }
                }
                if (sElev != null && eElev != null && sElev.isFinite() && eElev.isFinite()) {
                    onElevationDelta(eElev - sElev)
                }
                RouteSegment(
                    id = "seg_${seg.segmentIndex}_${seg.edgeId}",
                    start = p1,
                    end = p2,
                    distance = Distance.fromMeters(seg.lengthMeters.coerceAtLeast(1.0)),
                    bearing = Bearing(0.0),
                    elevationProfile = segElevPts,
                    roadName = seg.roadName,
                    roadType = if (sourceClassification == RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK) "CORRIDOR_FALLBACK" else seg.roadClass,
                    edgeId = seg.edgeId,
                    direction = seg.direction.name,
                    startOffsetMeters = seg.cumulativeStartMeters,
                    endOffsetMeters = seg.cumulativeEndMeters,
                    speedLimitKmh = seg.speedLimitKmh.takeIf { it in 5..140 } ?: 0,
                    gradePercent = seg.gradePercent,
                    isGradeUnknown = seg.isGradeUnknown
                )
            }
        } else {
            var cumM = 0.0
            realRoute.polyline.zipWithNext().mapIndexed { idx, (p1, p2) ->
                val segDist = CorridorStationProjector.haversineMeters(p1, p2).coerceAtLeast(1.0)
                val sOff = cumM
                val eOff = cumM + segDist
                cumM = eOff
                RouteSegment(
                    id = "seg_$idx",
                    start = p1,
                    end = p2,
                    distance = Distance.fromMeters(segDist),
                    bearing = Bearing(0.0),
                    roadName = realRoute.routeSummary,
                    roadType = if (sourceClassification == RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK) "CORRIDOR_FALLBACK" else "UNKNOWN",
                    startOffsetMeters = sOff,
                    endOffsetMeters = eOff,
                    speedLimitKmh = 0
                )
            }
        }
    }

    private fun buildProjectedStationRecords(
        polyline: List<GeoCoordinate>,
        totalDistanceMeters: Double
    ): List<ChargingStationRecord> {
        if (polyline.size < 2 || activeCatalogStations.isEmpty()) return emptyList()
        return activeCatalogStations.mapNotNull { stationWithPorts ->
            val st = stationWithPorts.station
            if (st.operationalStatus.name == "OUT_OF_SERVICE") return@mapNotNull null
            val stCoord = GeoCoordinate(st.latitude, st.longitude)
            val proj = CorridorStationProjector.projectOntoPolyline(stCoord, polyline)
            if (proj.crossTrackMeters > EvDomainPolicyConfig.DEFAULT_MAX_CROSS_TRACK_METERS) return@mapNotNull null

            val bestPort = stationWithPorts.ports.maxByOrNull { it.powerKw ?: 0.0 }
            val portPowerKw = bestPort?.powerKw ?: 0.0
            if (portPowerKw <= 0.0) return@mapNotNull null
            val portStatusUpper = bestPort?.status?.trim()?.uppercase() ?: "UNKNOWN"
            val isPortAvailable = if (portStatusUpper == "AVAILABLE") true else if (portStatusUpper in setOf("OUT_OF_SERVICE", "UNAVAILABLE", "OCCUPIED", "FAULTED")) false else null
            val allConns = stationWithPorts.ports.map { it.canonicalConnector.displayName }.distinct()

            ChargingStationRecord(
                id = st.id,
                name = st.canonicalName,
                location = stCoord,
                operatorName = st.operatorName ?: "اپراتور شبکه شارژ",
                connectorTypes = allConns,
                maxPowerKw = portPowerKw,
                availablePortsCount = if (isPortAvailable == true) 1 else null,
                totalPortsCount = stationWithPorts.ports.size,
                isReportedAvailable = isPortAvailable,
                verificationState = if (st.isVerified && st.hasTraceableExternalEvidence) "VERIFIED" else st.validationStatus,
                lastVerifiedEpochMillis = st.verifiedAt,
                liveStatusEpochMillis = bestPort?.liveStatusUpdatedAtEpochMs,
                confidenceScore = st.confidence,
                distanceAlongRoute = Distance.fromMeters(proj.distanceAlongRouteMeters.coerceIn(0.0, totalDistanceMeters)),
                detourDistance = Distance.fromMeters(proj.crossTrackMeters * 2.0),
                detourTimeSeconds = (proj.crossTrackMeters * 2.0) / (EvDomainPolicyConfig.URBAN_REFERENCE_SPEED_KMH / 3.6)
            )
        }.sortedBy { it.distanceAlongRoute?.meters ?: Double.MAX_VALUE }
    }

    /**
     * Builds a canonical multi-leg [FinalPlannedRoute] using [CanonicalFinalRouteAssembler.assembleFromChargingStops]
     * so that `0`, `1`, or `2+` charging stops produce `stops.size + 1` distinct legs (`Origin -> Stop 1 -> ... -> Destination`)
     * with sliced polylines, rebuilt segments, and rebuilt maneuvers (V13.3.1 Section 2).
     */
    private fun buildFinalPlannedRouteFromIntelligenceResult(
        realRoute: RealRoadRouteResult,
        routeVersion: Long,
        sourceClassification: RouteSourceClassification,
        domVeh: DomainVehicle,
        vehQuality: VehicleQualityAssessment,
        startPlace: GeocodingResolvedPlace,
        endPlace: GeocodingResolvedPlace,
        intelligenceResult: TripIntelligenceResult
    ): FinalPlannedRoute {
        val canonicalId = "${realRoute.routeId}_v$routeVersion"
        val stops = intelligenceResult.chargingPlanCandidate?.stops.orEmpty()
        val energyKwh = intelligenceResult.chargingPlanCandidate?.totalRouteEnergyKwh
            ?: ((intelligenceResult.energyPrediction?.expectedWh ?: 0.0) / 1000.0)
        val arrSoc = intelligenceResult.chargingPlanCandidate?.finalArrivalSocPercent
            ?: intelligenceResult.arrivalSocPrediction?.expectedSoc?.value
            ?: _uiState.value.initialSocPercent

        val domainSegments = buildDomainSegmentsFromRealRoute(
            realRoute = realRoute,
            startCoord = startPlace.coordinate,
            endCoord = endPlace.coordinate,
            sourceClassification = sourceClassification
        )

        val paramProvenance = LinkedHashMap<String, ParameterProvenanceStatus>()
        vehQuality.propertyProvenance.forEach { (k, v) -> paramProvenance[k] = v.status }
        paramProvenance["originAddress"] = startPlace.provenance
        paramProvenance["destinationAddress"] = endPlace.provenance
        paramProvenance["initialSocPercent"] = if (_uiState.value.isInitialSocVerified) {
            ParameterProvenanceStatus.USER_PROVIDED
        } else {
            ParameterProvenanceStatus.ESTIMATED
        }
        paramProvenance["ambientTemperatureCelsius"] = if (_uiState.value.ambientTemperatureCelsius != null) {
            ParameterProvenanceStatus.USER_PROVIDED
        } else {
            ParameterProvenanceStatus.UNKNOWN
        }

        val diagnosticsWarnings = buildList {
            if (startPlace.placeType == GeocodingPlaceType.CITY_CENTROID) {
                add("مبدأ «${startPlace.title}» به‌صورت مرکز شهر (CITY_CENTROID) با شعاع دقت ${startPlace.spatialAccuracyMeters.toInt()} متر تفکیک شده است.")
            }
            if (endPlace.placeType == GeocodingPlaceType.CITY_CENTROID) {
                add("مقصد «${endPlace.title}» به‌صورت مرکز شهر (CITY_CENTROID) با شعاع دقت ${endPlace.spatialAccuracyMeters.toInt()} متر تفکیک شده است.")
            }
            addAll(vehQuality.warnings)
            addAll(intelligenceResult.dataQualityAndFreshness?.dataGaps.orEmpty())
        }

        val provenanceReport = ProvenanceAndDiagnosticsReport(
            parameterProvenance = paramProvenance,
            routeSource = sourceClassification,
            elevationSource = if (domainSegments.any { it.elevationProfile.isNotEmpty() }) "ROAD_GRAPH_DEM" else "UNAVAILABLE_FLAT_FALLBACK",
            weatherSource = if (_uiState.value.ambientTemperatureCelsius != null) "USER_INPUT" else "ISA_MODEL_ASSUMPTION",
            stationVerificationSummary = if (stops.isEmpty()) "NO_STOPS_REQUIRED" else " STOPS_${stops.size}_EVALUATED",
            overallConfidencePercent = (intelligenceResult.arrivalSocPrediction?.confidence ?: (domVeh.confidence * 100.0)),
            uncertaintyReasons = intelligenceResult.dataQualityAndFreshness?.dataGaps.orEmpty(),
            warnings = diagnosticsWarnings
        )

        return CanonicalFinalRouteAssembler.assembleFromChargingStops(
            routeId = canonicalId,
            routeVersion = routeVersion,
            origin = realRoute.polyline.firstOrNull() ?: startPlace.coordinate,
            destination = realRoute.polyline.lastOrNull() ?: endPlace.coordinate,
            fullPolyline = realRoute.polyline,
            fullSegments = domainSegments,
            baseDrivingDurationSeconds = realRoute.totalDurationSeconds,
            baseTotalDistanceMeters = realRoute.totalDistanceMeters,
            totalEnergyRequiredKwh = energyKwh,
            departureSocPercent = _uiState.value.initialSocPercent,
            finalArrivalSocPercent = arrSoc,
            minReserveSocPercent = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
            chargingStops = stops,
            sourceClassification = sourceClassification,
            confidenceScore = intelligenceResult.chargingPlanCandidate?.overallConfidenceScore ?: domVeh.confidence,
            provenanceAndDiagnostics = provenanceReport
        )
    }

    fun reset() {
        activeAnalysisJob?.cancel()
        _uiState.value = TripIntelligenceUiState(
            activeVehicleLabel = _uiState.value.activeVehicleLabel,
            vehicleQualityAssessment = _uiState.value.vehicleQualityAssessment
        )
        _tripIntelligenceResult.value = null
        _activeFinalPlannedRoute.value = null
        _activeExecutionContext.value = null
    }
}

data class TripIntelligenceUiState(
    val startLocation: String = "تهران",
    val destination: String = "اصفهان",
    val resolvedOriginPlace: GeocodingResolvedPlace? = null,
    val resolvedDestinationPlace: GeocodingResolvedPlace? = null,
    val activeVehicleLabel: String = "خودرویی انتخاب نشده است",
    val vehicleQualityAssessment: VehicleQualityAssessment? = null,
    val initialSocPercent: Double = EvDomainPolicyConfig.DEFAULT_INITIAL_SOC_PERCENT,
    val isInitialSocVerified: Boolean = false,
    val ambientTemperatureCelsius: Double? = null,
    val isLoading: Boolean = false,
    val hasResults: Boolean = false,
    val executionStatus: TripIntelligenceExecutionStatus = TripIntelligenceExecutionStatus.FULL_SUCCESS,
    val routeSourceClassification: RouteSourceClassification = RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK,
    val activeRouteId: String? = null,
    val activeRouteVersion: Long = 0L,
    val error: String? = null
)
