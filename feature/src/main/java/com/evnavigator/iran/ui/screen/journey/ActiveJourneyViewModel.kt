package com.evnavigator.iran.ui.screen.journey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.core.routing.engine.DefaultDomainRoutingEngine
import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.provider.OfflineRoadGraphRoutingProvider
import com.evnavigator.iran.domain.intelligence.energy.DefaultEnergyPredictionService
import com.evnavigator.iran.domain.intelligence.replanning.DynamicReplanningCoordinator
import com.evnavigator.iran.domain.journey.charging.LiveChargingSessionManager
import com.evnavigator.iran.domain.journey.lifecycle.JourneyEvent
import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleManager
import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState
import com.evnavigator.iran.domain.journey.multistop.DefaultRoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.network.JourneyConnectivityMode
import com.evnavigator.iran.domain.journey.tracking.GpsMatchedProgressInput
import com.evnavigator.iran.domain.journey.tracking.JourneyProgressTracker
import com.evnavigator.iran.domain.journey.tracking.RouteDeviationDetector
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.ActiveVehicleDomainStateHolder
import com.evnavigator.iran.domain.model.catalog.ChargingStationWithPortsDomainModel
import com.evnavigator.iran.domain.model.catalog.VehicleDomainModel
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import com.evnavigator.iran.domain.repository.ActiveJourneyPersistenceRepository
import com.evnavigator.iran.domain.repository.ChargingStationRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import com.evnavigator.iran.ui.screen.map.location.AutomotiveGpsEngine
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * V12 Decomposed ActiveJourneyViewModel (< 550 lines).
 *
 * Delegates specialized responsibilities to:
 * - [ActiveJourneyUiModels] (UI state & enums)
 * - [DestinationSearchCoordinator] (Iran transit hubs, bilingual search, station corridor projection)
 * - [JourneyManeuverProgressionCoordinator] (monotonic maneuver progression & GPS teleport guard)
 * - [JourneyGpsAndDeviationCoordinator] (GPS signal state machine, progress tracking & off-route evaluation)
 * - [JourneyRouteAndSimulationCoordinator] (physics route planning, reroute execution, simulation stepping, persistence snapshot)
 * - [JourneyAutomotivePublisher] (Android Auto & AAOS domain state publication)
 */
@HiltViewModel
class ActiveJourneyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeJourneyRepository: ActiveJourneyPersistenceRepository,
    private val vehicleRepository: VehicleCatalogRepository,
    private val chargingStationRepository: ChargingStationRepository,
    private val realRoadRoutingEngine: IranRealRoadRoutingEngine = IranRealRoadRoutingEngine(),
    private val routeCoordinator: MultiStopRouteCoordinator = MultiStopRouteCoordinator(
        DefaultRoadRouteProvider(DefaultDomainRoutingEngine(OfflineRoadGraphRoutingProvider(realRoadRoutingEngine)))
    )
) : ViewModel() {

    private val activeVehicleRepository = ActiveVehicleDomainStateHolder
    private val maneuverCoordinator = JourneyManeuverProgressionCoordinator()

    private var simulationJob: Job? = null
    private var activeRerouteJob: Job? = null
    private var lastRerouteCompletedEpochMs: Long = 0L
    private val routeGenerationCounter = AtomicLong(1L)

    internal fun resolveMonotonicManeuverIndex(
        maneuvers: List<RealRoadManeuver>,
        traveledMeters: Double,
        currentGenId: Long,
        currentRouteId: String = _uiState.value.activeRouteId
    ): Int = maneuverCoordinator.resolveMonotonicManeuverIndex(maneuvers, traveledMeters, currentGenId, currentRouteId)

    private val gpsEngine = AutomotiveGpsEngine.acquireSharedEngine(context)
    val gpsState: StateFlow<GpsLocationState> = gpsEngine.locationState

    fun setGpsFixForTesting(lat: Double, lon: Double, speedKmh: Float = 60f) {
        gpsEngine.emitGpsFixForTesting(lat, lon, speedKmh = speedKmh)
    }

    private val _recenterEvent = MutableSharedFlow<GeoCoordinate>(extraBufferCapacity = 1)
    val recenterEvent: SharedFlow<GeoCoordinate> = _recenterEvent.asSharedFlow()

    private val lifecycleManager = JourneyLifecycleManager(JourneyLifecycleState.READY_TO_START)
    private val progressTracker = JourneyProgressTracker()
    private val replanningCoordinator = DynamicReplanningCoordinator()
    private val deviationDetector = RouteDeviationDetector(replanningCoordinator)
    private val chargingSessionManager = LiveChargingSessionManager()

    private val _uiState = MutableStateFlow(ActiveJourneyUiState())
    val uiState: StateFlow<ActiveJourneyUiState> = _uiState.asStateFlow()

    private fun applyActiveVehicle(active: VehicleDomainModel?) {
        val (updated, shouldReroute) = JourneyRouteAndSimulationCoordinator.applyActiveVehicleToState(
            currentState = _uiState.value,
            active = active,
            socSource = activeVehicleRepository.socDataSource.value
        )
        _uiState.value = updated
        if (shouldReroute) requestReroute(manualRequest = true)
    }

    init {
        viewModelScope.launch {
            gpsEngine.locationState.collect { gps ->
                val action = JourneyGpsAndDeviationCoordinator.handleGpsLocationState(gps, _uiState.value)
                _uiState.value = action.updatedState
                if (action.locationUpdateParams != null) {
                    val p = action.locationUpdateParams
                    onLocationUpdate(p.latitude, p.longitude, p.speedKmh, p.accuracyMeters, p.matchedFix)
                } else if (action.shouldPublishAutomotive) {
                    publishNavigationStateToAutomotive()
                }
            }
        }

        viewModelScope.launch {
            lifecycleManager.currentState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    lifecycleState = state,
                    statusMessage = JourneyRouteAndSimulationCoordinator.getStatusDescription(state)
                )
                persistStateAsync()
            }
        }

        viewModelScope.launch {
            activeVehicleRepository.activeVehicleId.collect { activeId ->
                val active = if (activeId.isNotBlank()) vehicleRepository.getVehicleById(activeId) else null
                applyActiveVehicle(active)
            }
        }

        viewModelScope.launch {
            activeVehicleRepository.currentSocPercent.collect { sharedSoc ->
                if (!_uiState.value.isSimulatingDrive && _uiState.value.currentSocPercent != sharedSoc) {
                    _uiState.value = _uiState.value.copy(currentSocPercent = sharedSoc)
                }
                AutomotiveNavigationStateHolder.updateVehicleTelemetry(
                    socPercent = sharedSoc,
                    batteryCapacityKwh = _uiState.value.vehicleBatteryKwh,
                    consumptionWhPerKm = _uiState.value.averageConsumptionWhPerKm,
                    socSource = JourneyRouteAndSimulationCoordinator.mapSocSource(activeVehicleRepository.socDataSource.value)
                )
                publishNavigationStateToAutomotive()
            }
        }

        viewModelScope.launch {
            AutomotiveNavigationStateHolder.destinationRequests.collect { req ->
                val userLoc = getUserCurrentCoordinate() ?: return@collect
                val target = DestinationSearchResult(
                    id = "auto_station_${req.stationId}",
                    title = req.title,
                    subtitle = "انتخاب‌شده در نمایشگر خودرو (Android Auto)",
                    coordinate = req.coordinate,
                    type = DestinationType.CHARGING_STATION
                )
                calculateRoute(userLoc, "موقعیت فعلی خودرو", req.coordinate, req.title, target, isDemo = false)
            }
        }

        viewModelScope.launch {
            try {
                chargingStationRepository.getAllStationsWithPorts().collect { stations ->
                    _uiState.value = _uiState.value.copy(chargingStations = stations)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        viewModelScope.launch {
            chargingSessionManager.sessionState.collect { session ->
                _uiState.value = _uiState.value.copy(chargingSession = session)
            }
        }

        _uiState.value = _uiState.value.copy(searchResults = DestinationSearchCoordinator.strategicIranHubs)

        activeVehicleRepository.consumePendingNavigationHandoff()?.let { handoff ->
            val origCoord = if (handoff.originLat != null && handoff.originLon != null) {
                GeoCoordinate(handoff.originLat!!, handoff.originLon!!)
            } else {
                getUserCurrentCoordinate() ?: GeoCoordinate(35.6997, 51.3380)
            }
            val destCoord = GeoCoordinate(handoff.destinationLat, handoff.destinationLon)
            val target = DestinationSearchResult("handoff_${System.currentTimeMillis()}", handoff.destinationName, "مسیر ارسالی به ناوبری فعال", destCoord, DestinationType.CITY_HUB)
            calculateRoute(origCoord, handoff.originName ?: "مبدأ انتخابی", destCoord, handoff.destinationName, target, isDemo = false)
        }
    }

    fun navigateToCoordinate(lat: Double, lon: Double, title: String = "ایستگاه شارژ انتخابی") {
        val originCoord = getUserCurrentCoordinate() ?: GeoCoordinate(35.6997, 51.3380)
        val destCoord = GeoCoordinate(lat, lon)
        val target = DestinationSearchResult("coord_${lat}_${lon}", title, "${"%.4f".format(lat)}, ${"%.4f".format(lon)}", destCoord, DestinationType.CHARGING_STATION)
        calculateRoute(originCoord, "موقعیت فعلی خودرو", destCoord, title, target, isDemo = false)
    }

    fun openSearch() {
        _uiState.value = _uiState.value.copy(
            navigationMode = NavigationScreenMode.DESTINATION_SEARCH,
            isSearching = true,
            searchResults = DestinationSearchCoordinator.filterResults(_uiState.value.searchQuery, _uiState.value.chargingStations)
        )
    }

    fun closeSearch() {
        val currentPlan = _uiState.value.plan
        val nextMode = when {
            currentPlan != null && _uiState.value.lifecycleState == JourneyLifecycleState.ACTIVE -> NavigationScreenMode.ACTIVE_NAVIGATION
            currentPlan != null -> NavigationScreenMode.ROUTE_PREVIEW
            else -> NavigationScreenMode.IDLE_FREE_DRIVE
        }
        _uiState.value = _uiState.value.copy(navigationMode = nextMode, isSearching = false)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, searchResults = DestinationSearchCoordinator.filterResults(query, _uiState.value.chargingStations))
    }

    fun selectDestination(destination: DestinationSearchResult) {
        val userLocation = getUserCurrentCoordinate()
        if (userLocation == null) {
            _uiState.value = _uiState.value.copy(
                routeCalculationError = "موقعیت مکانی شما (GPS) هنوز دریافت نشده است. لطفاً منتظر دریافت موقعیت بمانید.",
                statusMessage = "خطا در تعیین مبدأ: موقعیت GPS نامشخص است"
            )
            return
        }
        calculateRoute(userLocation, "موقعیت فعلی خودرو", destination.coordinate, destination.title, destination)
    }

    fun selectStation(stationWithPorts: ChargingStationWithPortsDomainModel) { _uiState.value = _uiState.value.copy(selectedStation = stationWithPorts) }
    fun clearSelectedStation() { _uiState.value = _uiState.value.copy(selectedStation = null) }

    private fun getUserCurrentCoordinate(): GeoCoordinate? = when (val currentGps = gpsState.value) {
        is GpsLocationState.FixAcquired -> GeoCoordinate(currentGps.location.latitude, currentGps.location.longitude)
        is GpsLocationState.MapMatchedFix -> GeoCoordinate(currentGps.snappedLocation.latitude, currentGps.snappedLocation.longitude)
        is GpsLocationState.DeadReckoning -> GeoCoordinate(currentGps.estimatedLocation.latitude, currentGps.estimatedLocation.longitude)
        else -> null
    }

    private suspend fun getActiveVehicleFromCatalog(): VehicleDomainModel? {
        val activeId = activeVehicleRepository.activeVehicleId.value
        return if (activeId.isNotBlank()) vehicleRepository.getVehicleById(activeId) else null
    }

    private fun calculateRoute(
        originCoord: GeoCoordinate,
        originName: String,
        destCoord: GeoCoordinate,
        destName: String,
        destinationTarget: DestinationSearchResult,
        isDemo: Boolean = false
    ) {
        val genId = routeGenerationCounter.incrementAndGet()
        viewModelScope.launch {
            try {
                val activeVeh = getActiveVehicleFromCatalog()
                val bundle = JourneyRouteAndSimulationCoordinator.computeFullRoutePlan(
                    originCoord, originName, destCoord, destName, activeVeh, _uiState.value,
                    _uiState.value.chargingStations, activeVehicleRepository.isSocConfirmed(),
                    activeVehicleRepository.socDataSource.value.name, realRoadRoutingEngine, routeCoordinator
                )
                if (genId != routeGenerationCounter.get()) return@launch
                val realRouteResult = bundle.realRouteResult

                bundle.planResult.onSuccess { plan ->
                    maneuverCoordinator.reset(genId, realRouteResult.routeId)
                    _uiState.value = JourneyRouteAndSimulationCoordinator.buildRoutePreviewSuccessState(
                        _uiState.value, bundle, plan, destinationTarget, isDemo, genId
                    )
                    gpsEngine.setActiveRoute(realRouteResult.polyline, realRouteResult.segments, realRouteResult.routeId, genId)
                    _recenterEvent.tryEmit(originCoord)
                    publishNavigationStateToAutomotive()
                    persistStateAsync()
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                        routeCalculationError = error.message ?: "خطا در اتصال به شبکه معابر یا پایگاه داده جغرافیایی ایران",
                        directDistanceMeters = realRouteResult.totalDistanceMeters,
                        directBearingDegrees = bundle.directBearing,
                        routeGenerationId = genId
                    )
                    publishNavigationStateToAutomotive()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (genId == routeGenerationCounter.get()) {
                    _uiState.value = _uiState.value.copy(
                        navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                        routeCalculationError = t.message ?: "خطا در محاسبه مسیر",
                        routeGenerationId = genId
                    )
                    publishNavigationStateToAutomotive()
                }
            }
        }
    }

    fun startDriveSimulation() {
        val polyline = _uiState.value.roadPolyline
        if (polyline.size < 2) return
        val physicsParams = JourneyRouteAndSimulationCoordinator.buildVehiclePhysicsParams(null, _uiState.value)
        val simNotice = if (physicsParams.isAssumedFallback) "[حالت شبیه‌ساز آزمایشی • خودروی فرضی مرجع]" else "[حالت شبیه‌ساز آزمایشی]"

        simulationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSimulatingDrive = true,
            isDemoPlan = true,
            isHypotheticalPlan = physicsParams.isAssumedFallback,
            navigationMode = NavigationScreenMode.ACTIVE_NAVIGATION,
            lifecycleState = JourneyLifecycleState.ACTIVE,
            statusMessage = "$simNotice شبیه‌سازی رانندگی در حال اجراست"
        )

        simulationJob = viewModelScope.launch {
            var currentIndex = 0
            var remainingSoc = _uiState.value.currentSocPercent
            while (isActive && currentIndex < polyline.size) {
                val (updatedState, nextSoc) = JourneyRouteAndSimulationCoordinator.stepSimulationFrame(
                    polyline, currentIndex, remainingSoc, _uiState.value, physicsParams
                )
                remainingSoc = nextSoc
                _uiState.value = updatedState
                _recenterEvent.tryEmit(polyline[currentIndex])
                publishNavigationStateToAutomotive()
                delay(350)
                currentIndex += 2
            }
            _uiState.value = _uiState.value.copy(
                isSimulatingDrive = false,
                statusMessage = "[پایان شبیه‌سازی] به مقصد رسیدید",
                currentManeuver = "به مقصد رسیدید",
                currentManeuverIcon = "ARRIVE"
            )
            publishNavigationStateToAutomotive()
        }
    }

    fun stopDriveSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.value = _uiState.value.copy(isSimulatingDrive = false, simulatedCarCoordinate = null)
        publishNavigationStateToAutomotive()
    }

    fun setupDemoSamplePlan(
        originName: String = "تهران (میدان آزادی)",
        destinationName: String = "اصفهان (پل خواجو)",
        initialSoc: Double = 85.0
    ) {
        val originCoord = GeoCoordinate(35.6997, 51.3380)
        val destCoord = GeoCoordinate(32.6370, 51.6830)
        val targetDest = DestinationSearchResult(
            id = "hub_isfahan_default",
            title = destinationName,
            subtitle = "اصفهان • کریدور مرکزی (سفر نمونه Demo)",
            coordinate = destCoord,
            type = DestinationType.CITY_HUB
        )
        calculateRoute(originCoord, originName, destCoord, destinationName, targetDest, isDemo = true)
    }

    fun setupDefaultPlan(
        originName: String = "تهران (میدان آزادی)",
        destinationName: String = "اصفهان (پل خواجو)",
        initialSoc: Double = 85.0
    ) = setupDemoSamplePlan(originName, destinationName, initialSoc)

    fun startActiveNavigation() {
        if (_uiState.value.plan == null) {
            _uiState.value = _uiState.value.copy(
                navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                routeCalculationError = "مسیر فعال یافت نشد؛ لطفاً ابتدا مقصد مورد نظر را انتخاب و محاسبه کنید.",
                statusMessage = "خطا در شروع ناوبری: مبدأ و مقصد مشخص نشده است"
            )
            publishNavigationStateToAutomotive()
            return
        }
        lifecycleManager.processEvent(JourneyEvent.StartJourney)
        _uiState.value = _uiState.value.copy(navigationMode = NavigationScreenMode.ACTIVE_NAVIGATION, statusMessage = "در حال ناوبری زنده")
        startGps()
        recenterOnVehicle()
        publishNavigationStateToAutomotive()
    }

    fun pauseNavigation() {
        lifecycleManager.processEvent(JourneyEvent.PauseJourney())
        _uiState.value = _uiState.value.copy(navigationMode = NavigationScreenMode.NAVIGATION_PAUSED, statusMessage = "سفر موقتاً متوقف شده است")
        publishNavigationStateToAutomotive()
    }

    fun resumeNavigation() {
        lifecycleManager.processEvent(JourneyEvent.ResumeJourney)
        _uiState.value = _uiState.value.copy(navigationMode = NavigationScreenMode.ACTIVE_NAVIGATION, statusMessage = "در حال ناوبری زنده")
        startGps()
        publishNavigationStateToAutomotive()
    }

    fun exitNavigation() {
        routeGenerationCounter.incrementAndGet()
        lifecycleManager.processEvent(JourneyEvent.CancelJourney())
        stopGps()
        gpsEngine.clearActiveRoute()
        _uiState.value = _uiState.value.copy(
            navigationMode = NavigationScreenMode.IDLE_FREE_DRIVE,
            plan = null,
            progress = null,
            selectedDestination = null,
            statusMessage = "آماده حرکت در حالت گشت آزاد"
        )
        AutomotiveNavigationStateHolder.clearSession()
        viewModelScope.launch {
            try { activeJourneyRepository.clearAll() } catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    fun retryRouteCalculation() {
        val dest = _uiState.value.selectedDestination
        if (dest != null) selectDestination(dest) else {
            _uiState.value = _uiState.value.copy(
                navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                routeCalculationError = "مقصدی برای محاسبه مجدد مسیر انتخاب نشده است",
                statusMessage = "نیاز به انتخاب مقصد"
            )
        }
    }

    fun setNavigationMode(mode: NavigationScreenMode) { _uiState.value = _uiState.value.copy(navigationMode = mode) }

    fun toggleOfflineMode(isOffline: Boolean) {
        _uiState.value = _uiState.value.copy(
            isInternetConnected = !isOffline,
            connectivityMode = if (isOffline) JourneyConnectivityMode.OFFLINE_DEGRADED else JourneyConnectivityMode.ONLINE_FULL,
            navigationMode = if (isOffline) NavigationScreenMode.OFFLINE_MODE else (if (_uiState.value.plan != null) NavigationScreenMode.ACTIVE_NAVIGATION else NavigationScreenMode.IDLE_FREE_DRIVE)
        )
    }

    fun toggleOfflineMapCache(isAvailable: Boolean) {
        _uiState.value = _uiState.value.copy(
            isOfflineMapAvailable = isAvailable,
            navigationMode = if (!isAvailable) NavigationScreenMode.MISSING_OFFLINE_MAP else _uiState.value.navigationMode
        )
    }

    fun toggleGpsSignalQuality(isWeak: Boolean) {
        _uiState.value = _uiState.value.copy(
            navigationMode = if (isWeak) NavigationScreenMode.GPS_WEAK_OR_DISABLED else NavigationScreenMode.ACTIVE_NAVIGATION,
            activeWarning = if (isWeak) "سیگنال موقعیت‌یاب ضعیف است یا خاموش شده است" else null
        )
    }

    fun startGps() { try { gpsEngine.startEngine() } catch (_: Exception) {} }
    fun stopGps() { try { gpsEngine.stopEngine() } catch (_: Exception) {} }
    fun toggleCameraPerspective() { _uiState.value = _uiState.value.copy(isHeadingUpCamera = !_uiState.value.isHeadingUpCamera) }
    fun toggleAudio() { _uiState.value = _uiState.value.copy(isAudioMuted = !_uiState.value.isAudioMuted) }

    fun recenterOnVehicle() {
        val coord = getUserCurrentCoordinate()
        if (coord != null) _recenterEvent.tryEmit(coord) else {
            _uiState.value.plan?.waypoints?.firstOrNull()?.coordinate?.let { _recenterEvent.tryEmit(it) }
            startGps()
        }
    }

    fun triggerReplanning() {
        lifecycleManager.processEvent(JourneyEvent.TriggerReplanning("User or off-route requested"))
        requestReroute(manualRequest = false)
    }

    fun requestReroute(manualRequest: Boolean = true) {
        val now = System.currentTimeMillis()
        if (activeRerouteJob?.isActive == true) return
        if (!manualRequest && (now - lastRerouteCompletedEpochMs) < 10_000L) {
            _uiState.value = _uiState.value.copy(rerouteState = RerouteState.COOLDOWN)
            return
        }

        val genId = routeGenerationCounter.incrementAndGet()
        _uiState.value = _uiState.value.copy(rerouteState = RerouteState.IN_PROGRESS)
        activeRerouteJob = viewModelScope.launch {
            val currentPlan = _uiState.value.plan ?: run {
                _uiState.value = _uiState.value.copy(rerouteState = RerouteState.IDLE)
                return@launch
            }
            val currentPos = getUserCurrentCoordinate() ?: currentPlan.waypoints.first().coordinate
            val activeVeh = getActiveVehicleFromCatalog()

            val bundle = try {
                JourneyRouteAndSimulationCoordinator.computeReroutePlan(
                    currentPos, currentPlan, activeVeh, _uiState.value,
                    activeVehicleRepository.isSocConfirmed(), activeVehicleRepository.socDataSource.value.name,
                    realRoadRoutingEngine, routeCoordinator
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (genId == routeGenerationCounter.get()) {
                    _uiState.value = _uiState.value.copy(
                        navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                        routeCalculationError = e.message ?: "خطا در محاسبه مسیر جاده‌ای هنگام بازنگری مسیر",
                        routeGenerationId = genId,
                        rerouteState = RerouteState.FAILED
                    )
                    publishNavigationStateToAutomotive()
                }
                return@launch
            }

            val realRouteResult = bundle.realRouteResult
            if (realRouteResult.polyline.size < 2) {
                if (genId == routeGenerationCounter.get()) {
                    _uiState.value = _uiState.value.copy(
                        navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                        routeCalculationError = "هندسه مسیر جدید نامعتبر است؛ جایگزینی مسیر انجام نشد",
                        routeGenerationId = genId,
                        rerouteState = RerouteState.FAILED
                    )
                    publishNavigationStateToAutomotive()
                }
                return@launch
            }
            if (genId != routeGenerationCounter.get()) return@launch

            bundle.planResult.onSuccess { newPlan ->
                lastRerouteCompletedEpochMs = System.currentTimeMillis()
                deviationDetector.reset()
                maneuverCoordinator.reset(genId, realRouteResult.routeId)
                _uiState.value = JourneyRouteAndSimulationCoordinator.buildRerouteSuccessState(
                    _uiState.value, bundle, newPlan, genId, lastRerouteCompletedEpochMs
                )
                gpsEngine.setActiveRoute(realRouteResult.polyline, realRouteResult.segments, realRouteResult.routeId, genId)
                lifecycleManager.processEvent(JourneyEvent.ReplanSucceeded("replan_${lastRerouteCompletedEpochMs}"))
                publishNavigationStateToAutomotive()
                persistStateAsync()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                    routeCalculationError = error.message ?: "خطا در بازنگری مسیر",
                    routeGenerationId = genId,
                    rerouteState = RerouteState.FAILED
                )
                publishNavigationStateToAutomotive()
            }
        }
    }

    fun onLocationUpdate(latitude: Double, longitude: Double, speedKmh: Double, accuracyMeters: Double, matchedFix: GpsMatchedProgressInput? = null) {
        val outcome = JourneyGpsAndDeviationCoordinator.evaluateLocationUpdate(
            _uiState.value, latitude, longitude, speedKmh, accuracyMeters, matchedFix,
            progressTracker, deviationDetector, maneuverCoordinator
        ) ?: return
        _uiState.value = outcome.updatedState
        if (outcome.shouldTriggerReplanning) triggerReplanning()
        publishNavigationStateToAutomotive()
        persistStateAsync()
    }

    private fun publishNavigationStateToAutomotive() {
        JourneyAutomotivePublisher.publish(_uiState.value, gpsState.value, maneuverCoordinator)
    }

    fun startChargingAtStation() {
        val nextStation = _uiState.value.plan?.waypoints?.firstOrNull { it.isChargingStop }
        val stationId = nextStation?.stationDetails?.stationId ?: "station_highway"
        val powerKw = nextStation?.stationDetails?.maxPowerKw?.takeIf { it.isFinite() && it > 0.0 }
        val vehicleCapacityKwh = _uiState.value.vehicleBatteryKwh.takeIf { it.isFinite() && it > 0.0 }

        if (vehicleCapacityKwh == null) {
            _uiState.value = _uiState.value.copy(
                activeWarning = "اطلاعات خودرو ناقص است؛ لطفاً ابتدا خودروی فعال را از گاراژ انتخاب کنید.",
                statusMessage = "خطا در شروع شارژ: اطلاعات خودرو ناقص است"
            )
            return
        }
        if (powerKw == null) {
            _uiState.value = _uiState.value.copy(
                activeWarning = "توان شارژ ایستگاه مشخص نیست؛ امکان محاسبه دقیق جلسه شارژ وجود ندارد.",
                statusMessage = "خطا در شروع شارژ: توان شارژ مشخص نیست"
            )
            return
        }
        lifecycleManager.processEvent(JourneyEvent.StartCharging(stationId, _uiState.value.currentSocPercent, powerKw))
        chargingSessionManager.startSession(stationId, nextStation.name, _uiState.value.currentSocPercent, 85.0, powerKw, vehicleCapacityKwh, _uiState.value.vehicleMaxChargeKw)
    }

    fun finishChargingAndDepart() {
        chargingSessionManager.finishSession()
        lifecycleManager.processEvent(JourneyEvent.FinishCharging("station_done", _uiState.value.currentSocPercent))
        lifecycleManager.processEvent(JourneyEvent.DepartChargingStop("station_done"))
    }

    private fun persistStateAsync() {
        viewModelScope.launch {
            try {
                val snapshot = JourneyRouteAndSimulationCoordinator.buildPersistenceSnapshot(_uiState.value, getUserCurrentCoordinate()) ?: return@launch
                activeJourneyRepository.saveActiveJourney(snapshot)
            } catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    fun launchCorridorNavigation(corridorKey: String = "tehran_qom") {
        val userLocation = getUserCurrentCoordinate() ?: run {
            _uiState.value = _uiState.value.copy(
                navigationMode = NavigationScreenMode.ROUTE_CALCULATION_ERROR,
                routeCalculationError = "موقعیت GPS خودرو دریافت نشده است. ناوبری کریدور نیاز به سیگنال معتبر GPS دارد.",
                statusMessage = "خطا در شروع ناوبری کریدور: موقعیت GPS نامشخص است"
            )
            return
        }
        val destination = when (corridorKey) {
            "isfahan" -> GeoCoordinate(32.6370, 51.6830)
            "rasht" -> GeoCoordinate(37.2800, 49.5830)
            "mashhad" -> GeoCoordinate(36.2870, 59.6150)
            "chalus" -> GeoCoordinate(36.6550, 51.4204)
            else -> GeoCoordinate(34.6401, 50.8764)
        }
        val target = DestinationSearchResult("corridor_$corridorKey", "مقصد کریدور", corridorKey, destination, DestinationType.CITY_HUB)
        calculateRoute(userLocation, "موقعیت فعلی", destination, "مقصد کریدور", target, isDemo = false)
    }

    override fun onCleared() {
        super.onCleared()
        AutomotiveGpsEngine.releaseSharedEngine()
    }
}
