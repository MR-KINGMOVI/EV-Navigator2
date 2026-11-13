package com.evnavigator.iran.ui.screen.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.gps.ActiveGpsNavigationEngine
import com.evnavigator.iran.core.routing.gps.LiveGpsNavigationState
import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.model.ManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.core.routing.model.RouteCoverageException
import com.evnavigator.iran.domain.geocoding.GeocodingAndReverseGeocodingService
import com.evnavigator.iran.domain.geocoding.GeocodingPlaceType
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.TripEnergyPrediction
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.intelligence.trip.BatteryAndSocState
import com.evnavigator.iran.domain.intelligence.trip.CanonicalFinalRouteAssembler
import com.evnavigator.iran.domain.intelligence.trip.CorridorStationProjector
import com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.intelligence.trip.TripExecutionContext
import com.evnavigator.iran.domain.journey.multistop.JourneyWaypoint
import com.evnavigator.iran.domain.journey.multistop.LegRouteResult
import com.evnavigator.iran.domain.journey.multistop.MultiStopJourneyPlan
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.MultiStopRoutePlan
import com.evnavigator.iran.domain.journey.multistop.PlannedLeg
import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.WaypointNode
import com.evnavigator.iran.domain.journey.multistop.WaypointType
import com.evnavigator.iran.domain.model.*
import com.evnavigator.iran.domain.model.catalog.ConnectorNormalizer
import com.evnavigator.iran.domain.model.catalog.DomainActiveVehicleSelectionState
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.DomainVehicleSocSource
import com.evnavigator.iran.domain.model.catalog.VehicleDataQualityEvaluator
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import com.evnavigator.iran.domain.repository.ChargingStationRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import com.evnavigator.iran.domain.routing.importer.ImportedRoutePreset
import com.evnavigator.iran.ui.screen.journey.DestinationSearchCoordinator
import com.evnavigator.iran.ui.screen.map.location.AutomotiveGpsEngine
import com.evnavigator.iran.ui.screen.map.location.GpsLocation
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import com.evnavigator.iran.ui.screen.map.model.RouteCalculationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Top-level UI state for MapScreen, MapCockpitCommandCenter, MapOsmOverlayRenderer, and MapNavigationViewModelTest
 * (V13.3.1 Sections 1, 2, 4, 7, 11, 16 & 17).
 */
data class MapNavigationUiState(
    val searchQuery: String = "",
    val searchResults: List<DestinationSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedDestination: DestinationSearchResult? = null,
    val routeState: RouteCalculationState = RouteCalculationState.Idle,
    val isChargingLayerVisible: Boolean = true,
    val userBatterySocPercent: Double = 85.0,
    val ambientTemperatureCelsius: Double? = null,
    val vehicleModelName: String = "خودرویی انتخاب نشده است",
    val vehicleBatteryKwh: Double = 75.0,
    val vehicleConsumptionWhPerKm: Double = 165.0,
    val chargingStations: List<DomainChargingStationWithPorts> = emptyList(),
    val selectedStation: DomainChargingStationWithPorts? = null,
    val zoomLevel: Double = 11.5,
    val isHeadingUp: Boolean = false,
    val isImportDialogOpen: Boolean = false,
    val currentLocation: GeoCoordinate? = null,
    val destinationLocation: GeoCoordinate? = null,
    val destinationQuery: String = "",
    val selectedDestinationName: String = "",
    val routeSummaryText: String = "",
    val currentRoute: Route? = null,
    val isNavigating: Boolean = false,
    val mapCenter: GeoCoordinate = GeoCoordinate(35.6997, 51.3380),
    val mapZoom: Float = 11.5f,
    val showChargingStations: Boolean = true,
    val showTrafficLayer: Boolean = false,
    val activeVehicleName: String = "خودرویی انتخاب نشده است",
    val activeVehicleUsableKwh: Double = 75.0,
    val activeVehicleGrossKwh: Double = 82.0,
    val currentBatterySocPercent: Int = 85,
    val isSocVerified: Boolean = false,
    val estimatedRangeKm: Int = 386,
    val vehicleQualityStatus: String = "USABLE_WITH_ASSUMPTIONS",
    val error: String? = null
)

/**
 * ViewModel for Map and Real-Time GPS Navigation Screen (V13.3.1 Sections 1, 2, 4, 7, 11, 16 & 17).
 *
 * V13.3.1 Architectural Guarantees:
 * 1. Eliminates all synthetic elevation/grade fallbacks (`1000.0 m`, `1200.0 m`, `0.5%` grade).
 *    When road graph segments lack elevation, passes `null` altitude and `null` grade so
 *    [CanonicalEnergyEngine] marks grade as unknown (`hasUnknownGradeSegments = true`) instead of
 *    inventing a 0.5% uphill climb at 1200 m.
 * 2. Integrates [GeocodingAndReverseGeocodingService] for full-Iran street, POI, city, coordinate,
 *    and map-tap reverse geocoding (`selectMapCoordinate`).
 * 3. Builds a canonical multi-leg [FinalPlannedRoute] and [TripExecutionContext] with a versioned
 *    `routeId` and synchronizes Map polyline, GPS navigation route, ETA, arrival SoC, and charging stops.
 */
@HiltViewModel
class MapNavigationViewModel @Inject constructor(
    private val vehicleCatalogRepository: VehicleCatalogRepository,
    private val chargingStationRepository: ChargingStationCatalogRepository?,
    private val realRoadRoutingEngine: IranRealRoadRoutingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false)
) : ViewModel() {

    private var customRouteCoordinator: MultiStopRouteCoordinator? = null
    private var legacyChargingRepository: ChargingStationRepository? = null
    private var sharedAutomotiveGpsEngine: AutomotiveGpsEngine? = null

    /**
     * Secondary constructor supporting [MapNavigationViewModelTest] and legacy DI callers.
     */
    constructor(
        context: Context,
        chargingStationRepository: ChargingStationRepository,
        vehicleRepository: VehicleCatalogRepository,
        realRoadRoutingEngine: IranRealRoadRoutingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false),
        routeCoordinator: MultiStopRouteCoordinator? = null
    ) : this(
        vehicleCatalogRepository = vehicleRepository,
        chargingStationRepository = chargingStationRepository as? ChargingStationCatalogRepository,
        realRoadRoutingEngine = realRoadRoutingEngine
    ) {
        this.legacyChargingRepository = chargingStationRepository
        this.customRouteCoordinator = routeCoordinator
        this.sharedAutomotiveGpsEngine = runCatching { AutomotiveGpsEngine.acquireSharedEngine(context) }.getOrNull()
    }

    private val gpsEngine = ActiveGpsNavigationEngine()
    private val routeVersionCounter = AtomicLong(0L)

    private val _gpsState = MutableStateFlow<GpsLocationState>(GpsLocationState.SearchingForFix)
    val gpsState: StateFlow<GpsLocationState> = _gpsState.asStateFlow()

    private val _recenterEvent = MutableSharedFlow<GeoCoordinate>(extraBufferCapacity = 1)
    val recenterEvent: SharedFlow<GeoCoordinate> = _recenterEvent.asSharedFlow()

    private val roadRouteProvider = RoadRouteProvider { from, to ->
        try {
            val res = realRoadRoutingEngine.calculateRealRoute(from, to)
            val sourceClass = classifyRouteSource(res)
            LegRouteResult(
                distanceMeters = res.totalDistanceMeters,
                durationSeconds = res.totalDurationSeconds,
                polyline = res.polyline,
                isRealRoadGraph = sourceClass.isTrueRoadNetwork,
                sourceClassification = sourceClass
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    private val defaultRouteCoordinator = MultiStopRouteCoordinator(
        roadRouteProvider = roadRouteProvider
    )

    private val routeCoordinator: MultiStopRouteCoordinator
        get() = customRouteCoordinator ?: defaultRouteCoordinator

    private var activeCalculationJob: Job? = null
    private var allCatalogStations: List<DomainChargingStationWithPorts> = emptyList()

    val activeVehicleSelectionState: StateFlow<DomainActiveVehicleSelectionState> =
        (runCatching { vehicleCatalogRepository.observeActiveVehicleSelectionState() }.getOrNull() ?: emptyFlow())
            .stateIn(viewModelScope, SharingStarted.Eagerly, DomainActiveVehicleSelectionState.NoActiveVehicle)

    val activeVehicle: StateFlow<DomainVehicle?> =
        (runCatching { vehicleCatalogRepository.observeActiveVehicle() }.getOrNull() ?: emptyFlow())
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeVehiclePhysics: StateFlow<VehiclePhysicsParams?> = activeVehicle
        .map { active ->
            active?.toPhysicsParamsOrNull(assumedPayloadKg = 150.0)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _departureSocPercent = MutableStateFlow(85.0)
    val departureSocPercent: StateFlow<Double> = _departureSocPercent.asStateFlow()

    private val _isSocUserConfirmed = MutableStateFlow(false)
    val isSocUserConfirmed: StateFlow<Boolean> = _isSocUserConfirmed.asStateFlow()

    private val _routeEnergyPrediction = MutableStateFlow<TripEnergyPrediction?>(null)
    val routeEnergyPrediction: StateFlow<TripEnergyPrediction?> = _routeEnergyPrediction.asStateFlow()

    private val _multiStopPlan = MutableStateFlow<MultiStopRoutePlan?>(null)
    val multiStopPlan: StateFlow<MultiStopRoutePlan?> = _multiStopPlan.asStateFlow()

    private val _activeFinalPlannedRoute = MutableStateFlow<FinalPlannedRoute?>(null)
    val activeFinalPlannedRoute: StateFlow<FinalPlannedRoute?> = _activeFinalPlannedRoute.asStateFlow()

    private val _activeExecutionContext = MutableStateFlow<TripExecutionContext?>(null)
    val activeExecutionContext: StateFlow<TripExecutionContext?> = _activeExecutionContext.asStateFlow()

    private val _chargingStopsAlongRoute = MutableStateFlow<List<ChargingStopSummary>>(emptyList())
    val chargingStopsAlongRoute: StateFlow<List<ChargingStopSummary>> = _chargingStopsAlongRoute.asStateFlow()

    private val _uiState = MutableStateFlow(MapNavigationUiState())
    val uiState: StateFlow<MapNavigationUiState> = _uiState.asStateFlow()

    private val _routeState = MutableStateFlow<RouteCalculationState>(RouteCalculationState.Idle)
    val routeState: StateFlow<RouteCalculationState> = _routeState.asStateFlow()

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    val liveGpsState: StateFlow<LiveGpsNavigationState> = gpsEngine.state

    private val _currentLocation = MutableStateFlow<GeoCoordinate?>(null)
    val currentLocation: StateFlow<GeoCoordinate?> = _currentLocation.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchLocationResult>>(emptyList())
    val searchResults: StateFlow<List<SearchLocationResult>> = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { chargingStationRepository?.observeAllStationsWithPorts() }
                .getOrNull()
                ?.collect { stations ->
                    allCatalogStations = stations
                    _uiState.update { it.copy(chargingStations = stations) }
                }
        }
        viewModelScope.launch {
            runCatching { vehicleCatalogRepository.observeCurrentSocState() }
                .getOrNull()
                ?.collect { socState ->
                    if (socState != null) {
                        val validSoc = socState.socPercent.coerceIn(5.0, 100.0)
                        _departureSocPercent.value = validSoc
                        _isSocUserConfirmed.value = socState.isVerified
                        _uiState.update {
                            it.copy(
                                userBatterySocPercent = validSoc,
                                currentBatterySocPercent = validSoc.roundToInt(),
                                isSocVerified = socState.isVerified
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(activeVehicleSelectionState, activeVehicle, _departureSocPercent, _isSocUserConfirmed) { selState, veh, soc, confirmed ->
                val vehQuality = VehicleDataQualityEvaluator.evaluate(veh)
                val label = when (selState) {
                    is DomainActiveVehicleSelectionState.Selected -> "${selState.vehicle.manufacturer} ${selState.vehicle.model}"
                    is DomainActiveVehicleSelectionState.InvalidSelection -> "خودروی نامعتبر (نیازمند انتخاب مجدد)"
                    is DomainActiveVehicleSelectionState.NoActiveVehicle -> veh?.let { "${it.manufacturer} ${it.model}" } ?: "خودرویی انتخاب نشده است"
                }
                val usableKwh = veh?.usableBatteryKwh ?: 75.0
                val whPerKm = veh?.consumptionWhPerKm?.takeIf { it > 0.0 } ?: 165.0
                val estRangeKm = ((usableKwh * 1000.0 * (soc / 100.0)) / whPerKm).roundToInt()
                Triple(label, estRangeKm, vehQuality)
            }.collect { (label, estRangeKm, vehQuality) ->
                val veh = activeVehicle.value
                _uiState.update { state ->
                    state.copy(
                        vehicleModelName = label,
                        activeVehicleName = label,
                        vehicleBatteryKwh = veh?.usableBatteryKwh ?: state.vehicleBatteryKwh,
                        vehicleConsumptionWhPerKm = veh?.consumptionWhPerKm?.takeIf { it > 0.0 } ?: state.vehicleConsumptionWhPerKm,
                        activeVehicleUsableKwh = veh?.usableBatteryKwh ?: state.activeVehicleUsableKwh,
                        activeVehicleGrossKwh = veh?.grossBatteryKwh ?: state.activeVehicleGrossKwh,
                        userBatterySocPercent = _departureSocPercent.value,
                        currentBatterySocPercent = _departureSocPercent.value.roundToInt(),
                        isSocVerified = _isSocUserConfirmed.value,
                        estimatedRangeKm = estRangeKm,
                        vehicleQualityStatus = vehQuality.status.name
                    )
                }
            }
        }
        viewModelScope.launch {
            liveGpsState.collect { gps ->
                if (gps.isNavigating && gps.needsReroute && _uiState.value.destinationLocation != null) {
                    triggerAutomaticReroute(gps.snappedPosition)
                }
            }
        }
        viewModelScope.launch {
            runCatching { vehicleCatalogRepository.consumePendingNavigationHandoff() }
                .getOrNull()
                ?.let { handoff ->
                    val origin = GeoCoordinate(handoff.originLat, handoff.originLon)
                    val dest = GeoCoordinate(handoff.destinationLat, handoff.destinationLon)
                    setGpsFixForTesting(origin.latitude, origin.longitude)
                    val target = DestinationSearchResult(
                        id = "handoff_${dest.latitude}_${dest.longitude}",
                        title = handoff.destinationName,
                        subtitle = "مسیر انتقالی",
                        coordinate = dest,
                        type = DestinationType.CITY_HUB
                    )
                    _uiState.update {
                        it.copy(
                            searchQuery = handoff.destinationName,
                            destinationQuery = handoff.destinationName,
                            selectedDestination = target,
                            destinationLocation = dest,
                            selectedDestinationName = handoff.destinationName,
                            mapCenter = origin
                        )
                    }
                    calculateRoute(origin, dest, handoff.destinationName, autoStartGps = handoff.autoStart)
                }
        }
    }

    private fun classifyRouteSource(realRoute: RealRoadRouteResult): RouteSourceClassification = when {
        realRoute.isOnlineRouting -> RouteSourceClassification.ONLINE_ROUTING_PROVIDER
        OsmDatasetManager.isUsingImportedOsmDataset() -> RouteSourceClassification.FULL_OFFLINE_OSM
        else -> RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK
    }

    fun setGpsFixForTesting(lat: Double, lon: Double, speedKmh: Float = 60f) {
        val coord = GeoCoordinate(lat, lon)
        _currentLocation.value = coord
        _gpsState.value = GpsLocationState.FixAcquired(
            location = GpsLocation(latitude = lat, longitude = lon, altitudeMeters = null, timestampEpochMillis = System.currentTimeMillis()),
            accuracyMeters = 8f,
            speedKmh = speedKmh,
            bearingDegrees = 0f
        )
        sharedAutomotiveGpsEngine?.emitGpsFixForTesting(lat, lon, speedKmh = speedKmh)
        _uiState.update { it.copy(currentLocation = coord, mapCenter = coord) }
    }

    fun onPermissionGranted() {
        startGps()
    }

    fun startGps() {
        runCatching { sharedAutomotiveGpsEngine?.startEngine() }
    }

    fun stopGps() {
        runCatching { sharedAutomotiveGpsEngine?.stopEngine() }
    }

    fun recenterOnUserLocation() {
        val loc = getActiveGpsCoordinate() ?: _uiState.value.mapCenter
        _recenterEvent.tryEmit(loc)
    }

    private fun getActiveGpsCoordinate(): GeoCoordinate? {
        _currentLocation.value?.let { return it }
        return when (val g = _gpsState.value) {
            is GpsLocationState.FixAcquired -> GeoCoordinate(g.location.latitude, g.location.longitude)
            is GpsLocationState.MapMatchedFix -> GeoCoordinate(g.snappedLocation.latitude, g.snappedLocation.longitude)
            is GpsLocationState.DeadReckoning -> GeoCoordinate(g.estimatedLocation.latitude, g.estimatedLocation.longitude)
            else -> null
        }
    }

    fun updateCurrentLocation(coordinate: GeoCoordinate, speedKmh: Double = 60.0, headingDeg: Double = 0.0) {
        setGpsFixForTesting(coordinate.latitude, coordinate.longitude, speedKmh.toFloat())
        if (liveGpsState.value.isNavigating) {
            gpsEngine.onNewGpsLocation(rawCoordinate = coordinate, speedKmh = speedKmh, bearingDegrees = headingDeg)
        }
    }

    fun updateBatterySoc(socPercent: Double) {
        val clamped = socPercent.coerceIn(5.0, 100.0)
        _departureSocPercent.value = clamped
        _isSocUserConfirmed.value = true
        val usableKwh = _uiState.value.vehicleBatteryKwh.coerceAtLeast(10.0)
        val whPerKm = _uiState.value.vehicleConsumptionWhPerKm.coerceAtLeast(100.0)
        val estRangeKm = ((usableKwh * 1000.0 * (clamped / 100.0)) / whPerKm).roundToInt()
        _uiState.update {
            it.copy(
                userBatterySocPercent = clamped,
                currentBatterySocPercent = clamped.roundToInt(),
                isSocVerified = true,
                estimatedRangeKm = estRangeKm
            )
        }
        viewModelScope.launch {
            runCatching { vehicleCatalogRepository.setCurrentSocPercent(clamped, DomainVehicleSocSource.USER_MANUAL_INPUT) }
        }
    }

    fun setDepartureSoc(socPercent: Double) {
        updateBatterySoc(socPercent)
        val orig = getActiveGpsCoordinate()
        val dest = _uiState.value.destinationLocation
        if (orig != null && dest != null) {
            calculateRoute(orig, dest, _uiState.value.selectedDestinationName)
        }
    }

    fun onSearchQueryChanged(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            _uiState.update {
                it.copy(
                    searchQuery = "",
                    destinationQuery = "",
                    searchResults = emptyList(),
                    isSearching = false,
                    error = null
                )
            }
            return
        }

        val geocodedResults = GeocodingAndReverseGeocodingService.searchPlaces(trimmed, allCatalogStations, limit = 12)
            .map { place ->
                val type = when (place.placeType) {
                    GeocodingPlaceType.CHARGING_STATION -> DestinationType.CHARGING_STATION
                    GeocodingPlaceType.CITY_CENTROID -> DestinationType.CITY_HUB
                    else -> DestinationType.CUSTOM_MAP_PIN
                }
                val userLoc = getActiveGpsCoordinate()
                val distKm = userLoc?.let { CorridorStationProjector.haversineMeters(it, place.coordinate) / 1000.0 }
                DestinationSearchResult(
                    id = place.placeId,
                    title = place.title,
                    subtitle = place.subtitle,
                    coordinate = place.coordinate,
                    type = type,
                    distanceFromUserKm = distKm
                )
            }

        val hubResults = DestinationSearchCoordinator.filterResults(trimmed, emptyList())
        val combined = (geocodedResults + hubResults).distinctBy { "${it.title}_${"%.4f".format(it.coordinate.latitude)}" }

        _searchResults.value = combined.map { SearchLocationResult(it.id, it.title, it.coordinate) }
        _uiState.update {
            it.copy(
                searchQuery = query,
                destinationQuery = query,
                searchResults = combined,
                isSearching = true,
                error = null
            )
        }
    }

    fun selectDestination(destination: DestinationSearchResult) {
        _uiState.update {
            it.copy(
                selectedDestination = destination,
                destinationLocation = destination.coordinate,
                selectedDestinationName = destination.title,
                searchQuery = destination.title,
                destinationQuery = destination.title,
                isSearching = false,
                error = null
            )
        }
        val origin = getActiveGpsCoordinate()
        if (origin == null) {
            val errMsg = "موقعیت مکانی شما (GPS) هنوز دریافت نشده است. لطفاً ابتدا GPS را فعال کنید یا مبدأ را مشخص نمایید."
            val errState = RouteCalculationState.Error(errMsg)
            _routeState.value = errState
            _uiState.update { it.copy(routeState = errState, error = errMsg) }
            return
        }
        calculateRoute(origin, destination.coordinate, destination.title)
    }

    fun selectMapCoordinate(coordinate: GeoCoordinate) {
        val reversePlace = GeocodingAndReverseGeocodingService.reverseGeocode(coordinate, allCatalogStations)
        val destination = DestinationSearchResult(
            id = reversePlace.placeId,
            title = reversePlace.title,
            subtitle = reversePlace.subtitle,
            coordinate = coordinate,
            type = DestinationType.CUSTOM_MAP_PIN
        )
        selectDestination(destination)
    }

    fun routeToNearestFastCharger() {
        val origin = getActiveGpsCoordinate()
        if (origin == null) {
            val errMsg = "موقعیت GPS خودرو دریافت نشده است؛ امکان مسیریابی به نزدیک‌ترین شارژر سریع وجود ندارد."
            val errState = RouteCalculationState.Error(errMsg)
            _routeState.value = errState
            _uiState.update { it.copy(routeState = errState, error = errMsg) }
            return
        }
        val nearest = allCatalogStations
            .filter { it.station.operationalStatus.name != "OUT_OF_SERVICE" }
            .minByOrNull { CorridorStationProjector.haversineMeters(origin, GeoCoordinate(it.station.latitude, it.station.longitude)) }
        if (nearest != null) {
            val dest = DestinationSearchResult(
                id = nearest.station.id,
                title = nearest.station.canonicalName,
                subtitle = "${nearest.station.city} • شارژر سریع",
                coordinate = GeoCoordinate(nearest.station.latitude, nearest.station.longitude),
                type = DestinationType.CHARGING_STATION
            )
            selectDestination(dest)
        } else {
            val fallbackDest = DestinationSearchResult(
                id = "hub_qom_mehromah",
                title = "قم (مجتمع مهروماه)",
                subtitle = "آزادراه تهران-قم • شارژ فوق‌سریع",
                coordinate = GeoCoordinate(34.6401, 50.8764),
                type = DestinationType.CHARGING_STATION
            )
            selectDestination(fallbackDest)
        }
    }

    fun clearDestination() {
        clearRoute()
    }

    fun selectStation(station: DomainChargingStationWithPorts) {
        _uiState.update { it.copy(selectedStation = station) }
    }

    fun clearSelectedStation() {
        _uiState.update { it.copy(selectedStation = null) }
    }

    fun setImportDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isImportDialogOpen = open) }
    }

    fun importRoutePreset(preset: ImportedRoutePreset) {
        _uiState.update { it.copy(isImportDialogOpen = false) }
        val origin = preset.waypoints.firstOrNull() ?: GeoCoordinate(35.6997, 51.3380)
        val dest = preset.waypoints.lastOrNull() ?: GeoCoordinate(32.6546, 51.6680)
        setGpsFixForTesting(origin.latitude, origin.longitude)
        val destResult = DestinationSearchResult(
            id = "preset_${preset.id}",
            title = preset.title,
            subtitle = "${preset.distanceKm.toInt()} km",
            coordinate = dest,
            type = DestinationType.CITY_HUB
        )
        selectDestination(destResult)
    }

    fun toggleChargingLayer() {
        _uiState.update {
            val next = !it.isChargingLayerVisible
            it.copy(isChargingLayerVisible = next, showChargingStations = next)
        }
    }

    fun toggleCameraPerspective() {
        _uiState.update { it.copy(isHeadingUp = !it.isHeadingUp) }
    }

    fun selectPresetDestination(preset: MapDestinationAndCorridorHelper.DestinationPreset) {
        val dest = DestinationSearchResult(
            id = preset.id,
            title = preset.displayNameFa,
            subtitle = "کریدور بین‌شهری",
            coordinate = preset.coordinate,
            type = DestinationType.CITY_HUB
        )
        if (getActiveGpsCoordinate() == null) {
            setGpsFixForTesting(35.6997, 51.3380)
        }
        selectDestination(dest)
    }

    fun searchDestination(query: String) {
        onSearchQueryChanged(query)
        val resolved = MapDestinationAndCorridorHelper.resolveDestinationQuery(query)
        if (resolved != null) {
            val (destCoord, destTitle) = resolved
            val origin = getActiveGpsCoordinate() ?: GeoCoordinate(35.6997, 51.3380)
            calculateRoute(origin, destCoord, destTitle)
        } else if (_uiState.value.searchResults.isEmpty()) {
            _uiState.update {
                it.copy(error = "مقصد «${query.trim()}» در سیستم آدرس‌یابی و شهرهای ایران یافت نشد.")
            }
        }
    }

    fun selectSearchResult(item: SearchLocationResult) {
        val dest = DestinationSearchResult(
            id = item.id,
            title = item.title,
            subtitle = "",
            coordinate = item.coordinate,
            type = DestinationType.CITY_HUB
        )
        if (getActiveGpsCoordinate() == null) {
            setGpsFixForTesting(35.6997, 51.3380)
        }
        selectDestination(dest)
    }

    fun calculateRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        destinationTitle: String = "مقصد انتخابی",
        autoStartGps: Boolean = false
    ) {
        activeCalculationJob?.cancel()
        activeCalculationJob = viewModelScope.launch {
            _routeState.value = RouteCalculationState.Calculating
            _uiState.update { it.copy(routeState = RouteCalculationState.Calculating, error = null) }
            try {
                val realRoute = realRoadRoutingEngine.calculateRealRoute(origin, destination)
                val sourceClassification = classifyRouteSource(realRoute)
                val baseDomainRoute = mapToDomainRoute(realRoute, origin, destination, sourceClassification)

                val selectedDomainVehicle = activeVehicle.value
                val vehicleQuality = VehicleDataQualityEvaluator.evaluate(selectedDomainVehicle)
                val depSoc = _departureSocPercent.value
                val isSocVerified = _isSocUserConfirmed.value

                val effectivePhysics: VehiclePhysicsParams = if (selectedDomainVehicle != null && vehicleQuality.isValidForPlanning) {
                    val normalizedConnectors = ConnectorNormalizer.normalizeConnectorStrings(selectedDomainVehicle.connectorTypes)
                    (activeVehiclePhysics.value ?: VehiclePhysicsParams.assumedReference(selectedDomainVehicle.id, selectedDomainVehicle.usableBatteryKwh))
                        .copy(supportedConnectors = normalizedConnectors)
                } else {
                    VehiclePhysicsParams.assumedReference(
                        vehicleId = selectedDomainVehicle?.id ?: "reference_ev_75kwh",
                        usableBatteryKwh = _uiState.value.vehicleBatteryKwh.takeIf { it > 10.0 } ?: 75.0
                    )
                }

                val batteryAndSoc = BatteryAndSocState(
                    grossCapacityKwh = selectedDomainVehicle?.grossBatteryKwh ?: (effectivePhysics.usableBatteryKwh * 1.08),
                    usableCapacityKwh = effectivePhysics.usableBatteryKwh,
                    currentSocPercent = depSoc,
                    reserveSocPercent = EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
                    isSocAuthoritative = isSocVerified
                )

                // V13.3.1 Section 7: Zero synthetic 1000.0 m / 1200.0 m altitude or 0.5% grade fallback!
                val routeInput = RouteProfileInput(
                    segments = if (realRoute.segments.isNotEmpty()) {
                        realRoute.segments.map { seg ->
                            RouteSegmentInput(
                                distanceMeters = seg.lengthMeters.coerceAtLeast(1.0),
                                startElevationMeters = seg.startElevationMeters,
                                endElevationMeters = seg.endElevationMeters,
                                gradePercent = seg.gradePercent.takeIf { !seg.isGradeUnknown && it.isFinite() },
                                speedLimitKmh = seg.speedLimitKmh.takeIf { it in 5..140 }?.toDouble(),
                                targetSpeedKmh = seg.speedLimitKmh.takeIf { it in 5..140 }?.toDouble(),
                                altitudeMeters = if (seg.startElevationMeters != null && seg.endElevationMeters != null) {
                                    (seg.startElevationMeters + seg.endElevationMeters) / 2.0
                                } else null
                            )
                        }
                    } else {
                        realRoute.maneuvers.mapIndexed { idx, m ->
                            val nextM = realRoute.maneuvers.getOrNull(idx + 1)
                            val startAlt = m.coordinate.altitude
                            val endAlt = nextM?.coordinate?.altitude
                            val distM = m.distanceMeters.coerceAtLeast(50.0)
                            val grade = if (startAlt != null && endAlt != null && startAlt.isFinite() && endAlt.isFinite()) {
                                (((endAlt - startAlt) / distM) * 100.0).coerceIn(-25.0, 25.0)
                            } else null
                            RouteSegmentInput(
                                distanceMeters = distM,
                                startElevationMeters = startAlt,
                                endElevationMeters = endAlt,
                                gradePercent = grade,
                                speedLimitKmh = m.speedLimitKmh.takeIf { it in 5..140 }?.toDouble(),
                                targetSpeedKmh = m.speedLimitKmh.takeIf { it in 5..140 }?.toDouble(),
                                altitudeMeters = if (startAlt != null && endAlt != null) (startAlt + endAlt) / 2.0 else startAlt
                            )
                        }.ifEmpty {
                            listOf(
                                RouteSegmentInput(
                                    distanceMeters = realRoute.totalDistanceMeters,
                                    startElevationMeters = null,
                                    endElevationMeters = null,
                                    gradePercent = null,
                                    speedLimitKmh = null,
                                    targetSpeedKmh = null,
                                    altitudeMeters = null
                                )
                            )
                        }
                    }
                )

                val energyPred = CanonicalEnergyEngine.predictRouteEnergy(
                    route = routeInput,
                    vehicle = effectivePhysics,
                    conditions = EnvironmentalConditions(
                        ambientTemperatureCelsius = _uiState.value.ambientTemperatureCelsius,
                        targetSpeedKmh = null,
                        departureSocPercent = depSoc,
                        isDepartureSocVerified = isSocVerified
                    )
                )
                _routeEnergyPrediction.value = energyPred

                val candidateNodes = MapDestinationAndCorridorHelper.buildCorridorWaypointNodes(
                    allStations = allCatalogStations,
                    routePolyline = realRoute.polyline,
                    vehicle = selectedDomainVehicle
                )
                val plan = routeCoordinator.createPlan(
                    origin = WaypointNode("origin", "مبدأ", origin),
                    destination = WaypointNode("dest", destinationTitle, destination),
                    candidateChargingStations = candidateNodes,
                    initialSocPercent = depSoc,
                    usableBatteryKwh = batteryAndSoc.usableCapacityKwh,
                    minSafeSocPercent = batteryAndSoc.reserveSocPercent,
                    targetChargeSocPercent = EvDomainPolicyConfig.DEFAULT_TARGET_CHARGING_SOC_PERCENT,
                    vehiclePhysics = effectivePhysics,
                    ambientTemperatureCelsius = _uiState.value.ambientTemperatureCelsius
                )
                _multiStopPlan.value = plan

                val version = routeVersionCounter.incrementAndGet()
                val finalPlannedRoute = CanonicalFinalRouteAssembler.assembleFromMultiStopPlan(
                    baseRouteId = realRoute.routeId.ifBlank { baseDomainRoute.id },
                    routeVersion = version,
                    baseRoute = baseDomainRoute,
                    plan = plan,
                    batteryAndSoc = batteryAndSoc,
                    sourceClassification = sourceClassification
                )
                val executionContext = if (selectedDomainVehicle != null) {
                    CanonicalFinalRouteAssembler.buildExecutionContext(
                        vehicle = selectedDomainVehicle,
                        batteryAndSoc = batteryAndSoc,
                        finalPlannedRoute = finalPlannedRoute,
                        ambientTemperatureCelsius = _uiState.value.ambientTemperatureCelsius,
                        isTemperatureAuthoritative = _uiState.value.ambientTemperatureCelsius != null
                    )
                } else null
                _activeFinalPlannedRoute.value = finalPlannedRoute
                _activeExecutionContext.value = executionContext

                var runningDistMeters = 0.0
                val selectedStopInputs = mutableListOf<MapDestinationAndCorridorHelper.PlannedChargingStopSummaryInput>()
                for (leg in plan.legs) {
                    runningDistMeters += leg.distanceMeters
                    if (leg.to.isChargingStop && leg.chargingDurationMinutes > 0.0) {
                        val stObj = allCatalogStations.firstOrNull { it.station.id == leg.to.id }?.station
                        selectedStopInputs.add(
                            MapDestinationAndCorridorHelper.PlannedChargingStopSummaryInput(
                                id = leg.to.id,
                                name = leg.to.name,
                                operator = stObj?.operatorName ?: "شبکه شارژ کشور",
                                powerKw = leg.to.maxChargingPowerKw.roundToInt(),
                                distanceFromOriginKm = (runningDistMeters / 1000.0).roundToInt(),
                                arrivalSocPercent = leg.arrivalSocPercent.roundToInt(),
                                chargingDurationMinutes = leg.chargingDurationMinutes
                            )
                        )
                    }
                }
                val stopSummaries = MapDestinationAndCorridorHelper.buildPlannedChargingStopSummaries(
                    selectedChargingStops = selectedStopInputs,
                    routeEnergyPrediction = energyPred,
                    departureSocPercent = depSoc,
                    usableBatteryKwh = batteryAndSoc.usableCapacityKwh
                )
                _chargingStopsAlongRoute.value = stopSummaries

                val unifiedRealRouteForGps = realRoute.copy(
                    routeId = finalPlannedRoute.routeId,
                    polyline = finalPlannedRoute.polyline,
                    totalDistanceMeters = finalPlannedRoute.totalDistanceMeters,
                    totalDurationSeconds = finalPlannedRoute.baseDrivingDurationSeconds
                )
                gpsEngine.setActiveRoute(unifiedRealRouteForGps)

                val journeyWaypoints = buildList {
                    add(JourneyWaypoint("origin", "مبدأ", origin, WaypointType.ORIGIN))
                    for (leg in plan.legs) {
                        if (leg.to.isChargingStop) {
                            add(
                                JourneyWaypoint(
                                    id = leg.to.id,
                                    name = leg.to.name,
                                    coordinate = leg.to.coordinate,
                                    type = WaypointType.CHARGING_STOP,
                                    targetDepartureSocPercent = leg.postChargeDepartureSocPercent
                                )
                            )
                        }
                    }
                    add(JourneyWaypoint("dest", destinationTitle, destination, WaypointType.DESTINATION))
                }
                val plannedLegs = plan.legs.mapIndexed { idx, leg ->
                    val fromWp = journeyWaypoints.getOrElse(idx) { JourneyWaypoint(leg.from.id, leg.from.name, leg.from.coordinate, WaypointType.ORIGIN) }
                    val toWp = journeyWaypoints.getOrElse(idx + 1) { JourneyWaypoint(leg.to.id, leg.to.name, leg.to.coordinate, if (leg.to.isChargingStop) WaypointType.CHARGING_STOP else WaypointType.DESTINATION) }
                    val legManeuvers = finalPlannedRoute.legs.getOrNull(idx)?.maneuvers.orEmpty()
                    PlannedLeg(
                        fromWaypoint = fromWp,
                        toWaypoint = toWp,
                        distanceMeters = leg.distanceMeters,
                        durationSeconds = leg.durationSeconds,
                        energyRequiredKwh = leg.energyConsumedKwh,
                        departureSocPercent = leg.departureSocPercent,
                        arrivalSocPercent = leg.arrivalSocPercent,
                        postChargeDepartureSocPercent = leg.postChargeDepartureSocPercent,
                        chargingDurationMinutes = leg.chargingDurationMinutes,
                        energyAddedKwh = leg.energyAddedKwh,
                        polyline = leg.polyline,
                        maneuvers = legManeuvers,
                        isRealRoadGraph = leg.isRealRoadGraph,
                        sourceClassification = sourceClassification
                    )
                }
                val uiJourneyPlan = MultiStopJourneyPlan(
                    vehicleId = effectivePhysics.vehicleId,
                    waypoints = journeyWaypoints,
                    legs = plannedLegs,
                    totalDistanceMeters = finalPlannedRoute.totalDistanceMeters,
                    totalDrivingDurationSeconds = finalPlannedRoute.baseDrivingDurationSeconds.toLong(),
                    totalChargingDurationMinutes = (finalPlannedRoute.chargingDurationSeconds / 60.0).roundToInt(),
                    totalEnergyUsedKwh = finalPlannedRoute.totalEnergyRequiredKwh,
                    finalArrivalSocPercent = finalPlannedRoute.finalArrivalSocPercent,
                    isFeasible = plan.isComplete && finalPlannedRoute.finalArrivalSocPercent >= batteryAndSoc.reserveSocPercent,
                    warnings = plan.warnings,
                    isRealRoadGraph = finalPlannedRoute.isRealRoadGraph,
                    sourceClassification = sourceClassification,
                    finalPlannedRoute = finalPlannedRoute
                )

                val coreManeuvers = if (realRoute.maneuvers.isNotEmpty() && finalPlannedRoute.legs.size <= 1) {
                    realRoute.maneuvers
                } else {
                    finalPlannedRoute.maneuvers.map { pm ->
                        val mType = runCatching { ManeuverType.valueOf(pm.maneuverType) }.getOrDefault(ManeuverType.CONTINUE)
                        RealRoadManeuver(
                            stepIndex = pm.stepIndex,
                            instructionFarsi = pm.instructionFarsi,
                            roadName = pm.roadName,
                            distanceMeters = pm.distanceMeters,
                            durationSeconds = pm.durationSeconds,
                            coordinate = pm.coordinate,
                            maneuverType = mType,
                            bearingAfter = pm.bearingAfter,
                            speedLimitKmh = pm.speedLimitKmh
                        )
                    }
                }

                val unifiedDomainRoute = finalPlannedRoute.toDomainRoute()
                val successState = RouteCalculationState.Success(
                    plan = uiJourneyPlan,
                    originName = "مبدأ فعلی",
                    destinationName = destinationTitle,
                    polylinePoints = finalPlannedRoute.polyline,
                    totalDistanceKm = finalPlannedRoute.totalDistanceMeters / 1000.0,
                    totalDurationMinutes = (finalPlannedRoute.totalTripDurationSeconds / 60.0).roundToInt().coerceAtLeast(1),
                    totalEnergyKwh = (finalPlannedRoute.totalEnergyRequiredKwh * 10.0).roundToInt() / 10.0,
                    arrivalSocPercent = finalPlannedRoute.finalArrivalSocPercent,
                    chargingStopsCount = finalPlannedRoute.chargingStopsCount,
                    isFeasible = uiJourneyPlan.isFeasible,
                    warnings = plan.warnings,
                    maneuvers = coreManeuvers,
                    finalPlannedRoute = finalPlannedRoute,
                    route = unifiedDomainRoute,
                    realRoadResult = unifiedRealRouteForGps,
                    estimatedDuration = finalPlannedRoute.totalTripDurationSeconds.toLong(),
                    distance = Distance.fromMeters(finalPlannedRoute.totalDistanceMeters),
                    energyRequired = finalPlannedRoute.totalEnergyRequiredKwh,
                    alternativeRoutesCount = realRoute.alternativeRoutes.size,
                    isOnlineRoute = realRoute.isOnlineRouting,
                    routeSourceClassification = sourceClassification
                )
                _routeState.value = successState

                _uiState.update {
                    it.copy(
                        routeState = successState,
                        currentRoute = unifiedDomainRoute,
                        destinationLocation = destination,
                        selectedDestinationName = destinationTitle,
                        routeSummaryText = realRoute.routeSummary,
                        error = if (!isSocVerified && selectedDomainVehicle != null) {
                            "توجه: درصد شارژ اولیه (${depSoc.toInt()}٪) تأییدنشده است؛ پیش از حرکت مقدار دقیق شارژ باتری را تأیید کنید."
                        } else null
                    )
                }

                if (autoStartGps) {
                    startNavigation()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (coverageEx: RouteCoverageException) {
                val msg = coverageEx.message ?: "نقطه انتخاب‌شده خارج از پوشش شبکه جاده‌ای آفلاین است."
                val errState = RouteCalculationState.Error(msg)
                _routeState.value = errState
                _uiState.update { it.copy(routeState = errState, error = msg) }
            } catch (e: Exception) {
                val msg = e.message ?: "خطا در محاسبه مسیر واقعی جاده‌ای"
                val errState = RouteCalculationState.Error(msg)
                _routeState.value = errState
                _uiState.update { it.copy(routeState = errState, error = msg) }
            }
        }
    }

    private fun triggerAutomaticReroute(snappedPos: GeoCoordinate) {
        val dest = _uiState.value.destinationLocation ?: return
        gpsEngine.acknowledgeReroute()
        calculateRoute(snappedPos, dest, _uiState.value.selectedDestinationName, autoStartGps = true)
    }

    fun startNavigation() {
        val currentState = _routeState.value
        if (currentState is RouteCalculationState.Success) {
            val planned = currentState.finalPlannedRoute
            if (planned != null) {
                val check = planned.validatePreNavigationConsistency()
                if (!check.isConsistent) {
                    val errText = "خطای عدم تطابق مسیر پیش از شروع ناوبری (${check.violations.joinToString(", ")})"
                    _uiState.update { it.copy(error = errText) }
                    return
                }
            }
            gpsEngine.startNavigation(getActiveGpsCoordinate() ?: currentState.polylinePoints.firstOrNull())
            val activeRoute = currentState.route ?: planned?.toDomainRoute()
            if (activeRoute != null) {
                _navigationState.value = NavigationState.Active(
                    route = activeRoute,
                    currentStep = 0,
                    distanceToNextTurn = Distance.fromMeters(
                        currentState.maneuvers.firstOrNull()?.distanceMeters ?: 0.0
                    ),
                    estimatedTimeRemaining = currentState.estimatedDuration
                )
            }
            _uiState.update { it.copy(isNavigating = true) }
        }
    }

    fun simulateAdvanceAlongRoute() {
        gpsEngine.stepSimulationForward(pointsToAdvance = 2)
        val snap = gpsEngine.state.value.snappedPosition
        setGpsFixForTesting(snap.latitude, snap.longitude)
    }

    fun stopNavigation() {
        gpsEngine.stopNavigation()
        _navigationState.value = NavigationState.Idle
        _uiState.update { it.copy(isNavigating = false) }
    }

    fun clearRoute() {
        activeCalculationJob?.cancel()
        gpsEngine.stopNavigation()
        _routeState.value = RouteCalculationState.Idle
        _navigationState.value = NavigationState.Idle
        _activeFinalPlannedRoute.value = null
        _activeExecutionContext.value = null
        _chargingStopsAlongRoute.value = emptyList()
        _searchResults.value = emptyList()
        _uiState.update {
            it.copy(
                routeState = RouteCalculationState.Idle,
                currentRoute = null,
                selectedDestination = null,
                destinationLocation = null,
                searchQuery = "",
                destinationQuery = "",
                selectedDestinationName = "",
                searchResults = emptyList(),
                isSearching = false,
                isNavigating = false,
                error = null
            )
        }
    }

    fun updateMapViewport(center: GeoCoordinate, zoom: Float) {
        _uiState.update { it.copy(mapCenter = center, mapZoom = zoom, zoomLevel = zoom.toDouble()) }
    }

    fun toggleChargingStationsVisibility() {
        toggleChargingLayer()
    }

    fun toggleTrafficLayer() {
        _uiState.update { it.copy(showTrafficLayer = !it.showTrafficLayer) }
    }

    private fun mapToDomainRoute(
        realResult: RealRoadRouteResult,
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        sourceClassification: RouteSourceClassification
    ): Route {
        val segments = if (realResult.segments.isNotEmpty()) {
            realResult.segments.map { seg ->
                val startPt = seg.polyline.firstOrNull() ?: origin
                val endPt = seg.polyline.lastOrNull() ?: destination
                val elevPoints = buildList {
                    if (seg.startElevationMeters != null) {
                        add(ElevationPoint(startPt, Elevation(Altitude.ofMeters(seg.startElevationMeters)), Distance.fromMeters(seg.cumulativeStartMeters)))
                    }
                    if (seg.endElevationMeters != null) {
                        add(ElevationPoint(endPt, Elevation(Altitude.ofMeters(seg.endElevationMeters)), Distance.fromMeters(seg.cumulativeEndMeters)))
                    }
                }
                RouteSegment(
                    id = "seg_${seg.segmentIndex}_${seg.edgeId}",
                    start = startPt,
                    end = endPt,
                    distance = Distance.fromMeters(seg.lengthMeters),
                    bearing = Bearing(0.0),
                    elevationProfile = elevPoints,
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
            val pts = realResult.polyline
            var cumMeters = 0.0
            pts.zipWithNext().mapIndexed { idx, pair ->
                val maneuver = realResult.maneuvers.getOrNull(idx)
                val segLen = maneuver?.distanceMeters?.takeIf { it > 0.0 }
                    ?: CorridorStationProjector.haversineMeters(pair.first, pair.second).coerceAtLeast(1.0)
                val startOff = cumMeters
                val endOff = cumMeters + segLen
                cumMeters = endOff
                RouteSegment(
                    id = "seg_$idx",
                    start = pair.first,
                    end = pair.second,
                    distance = Distance.fromMeters(segLen),
                    bearing = Bearing(maneuver?.bearingAfter ?: 0.0),
                    roadName = maneuver?.roadName ?: realResult.routeSummary,
                    roadType = if (sourceClassification == RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK) "CORRIDOR_FALLBACK" else "UNKNOWN",
                    startOffsetMeters = startOff,
                    endOffsetMeters = endOff,
                    speedLimitKmh = maneuver?.speedLimitKmh?.takeIf { it in 5..140 } ?: 0
                )
            }
        }
        return Route(
            id = realResult.routeId,
            start = origin,
            end = destination,
            segments = segments,
            distance = Distance.fromMeters(realResult.totalDistanceMeters),
            estimatedTime = realResult.totalDurationSeconds.toLong(),
            elevationProfile = emptyList(),
            waypoints = realResult.polyline
        )
    }

    data class SearchLocationResult(
        val id: String,
        val title: String,
        val coordinate: GeoCoordinate
    )

    data class ChargingStopSummary(
        val id: String,
        val name: String,
        val operator: String,
        val powerKw: Int,
        val distanceFromOriginKm: Int,
        val estimatedArrivalSoc: Int,
        val recommendedChargeMinutes: Int
    )

    sealed class NavigationState {
        object Idle : NavigationState()
        data class Active(
            val route: Route,
            val currentStep: Int,
            val distanceToNextTurn: Distance,
            val estimatedTimeRemaining: Long
        ) : NavigationState()
    }
}
