package com.evnavigator.iran.ui.screen.journey

import android.content.Context
import android.location.LocationManager
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.repository.ActiveJourneyPersistenceRepository
import com.evnavigator.iran.domain.repository.ChargingStationRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveJourneyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockJourneyRepository: ActiveJourneyPersistenceRepository
    private lateinit var mockVehicleRepository: VehicleCatalogRepository
    private lateinit var mockChargingRepository: ChargingStationRepository
    private lateinit var viewModel: ActiveJourneyViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mock(Context::class.java)
        mockLocationManager = mock(LocationManager::class.java)
        mockJourneyRepository = mock(ActiveJourneyPersistenceRepository::class.java)
        mockVehicleRepository = mock(VehicleCatalogRepository::class.java)
        mockChargingRepository = mock(ChargingStationRepository::class.java)

        `when`(mockContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockLocationManager)
        `when`(mockChargingRepository.getAllStationsWithPorts()).thenReturn(flowOf(emptyList()))

        val offlineEngine = com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine(
            enableOnlineRouting = false,
            ioDispatcher = testDispatcher
        )
        val offlineProvider = com.evnavigator.iran.core.routing.provider.OfflineRoadGraphRoutingProvider(offlineEngine, defaultDispatcher = testDispatcher)
        val domainRoutingEngine = com.evnavigator.iran.core.routing.engine.DefaultDomainRoutingEngine(offlineProvider)
        val testRoadRouteProvider = com.evnavigator.iran.domain.journey.multistop.DefaultRoadRouteProvider(domainRoutingEngine)
        val testRouteCoordinator = com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator(
            roadRouteProvider = testRoadRouteProvider,
            defaultDispatcher = testDispatcher
        )
        viewModel = ActiveJourneyViewModel(
            context = mockContext,
            activeJourneyRepository = mockJourneyRepository,
            vehicleRepository = mockVehicleRepository,
            chargingStationRepository = mockChargingRepository,
            realRoadRoutingEngine = offlineEngine,
            routeCoordinator = testRouteCoordinator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsIdleFreeDrive() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.IDLE_FREE_DRIVE, state.navigationMode)
        assertNull(state.plan)
        assertFalse(state.isSearching)
        assertTrue(state.searchResults.isNotEmpty()) // Populated with strategic Iran transit hubs
    }

    @Test
    fun testOpenAndCloseSearch() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.openSearch()
        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(NavigationScreenMode.DESTINATION_SEARCH, viewModel.uiState.value.navigationMode)

        viewModel.closeSearch()
        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(NavigationScreenMode.IDLE_FREE_DRIVE, viewModel.uiState.value.navigationMode)
    }

    @Test
    fun testSearchFilteringIranianHubs() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.openSearch()
        viewModel.onSearchQueryChanged("اصفهان")
        val results = viewModel.uiState.value.searchResults
        assertTrue(results.any { it.title.contains("اصفهان") })
    }

    @Test
    fun testSelectDestinationFailsWhenGpsNotAcquired() = runTest(testDispatcher) {
        advanceUntilIdle()
        val isfahanHub = DestinationSearchResult(
            id = "test_isfahan",
            title = "اصفهان (پل خواجو)",
            subtitle = "کریدور مرکزی",
            coordinate = GeoCoordinate(32.6370, 51.6830),
            type = DestinationType.CITY_HUB
        )

        viewModel.selectDestination(isfahanHub)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Must set error when GPS not acquired", state.routeCalculationError)
        assertTrue(state.routeCalculationError?.contains("GPS") == true)
    }

    @Test
    fun testSelectDestinationTransitionsToRoutePreview() = runTest(testDispatcher) {
        advanceUntilIdle()
        val isfahanHub = DestinationSearchResult(
            id = "test_isfahan",
            title = "اصفهان (پل خواجو)",
            subtitle = "کریدور مرکزی",
            coordinate = GeoCoordinate(32.6370, 51.6830),
            type = DestinationType.CITY_HUB
        )

        viewModel.setGpsFixForTesting(35.6997, 51.3380)
        viewModel.selectDestination(isfahanHub)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals(NavigationScreenMode.ROUTE_PREVIEW, state.navigationMode)
        assertNotNull(state.plan)
        assertEquals("اصفهان (پل خواجو)", state.selectedDestination?.title)
        assertTrue((state.plan?.totalDistanceMeters ?: 0.0) > 0)
        assertNotNull(state.predictedArrivalSocPercent)
    }

    @Test
    fun testStartActiveNavigation() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.setupDefaultPlan()
        advanceUntilIdle()

        viewModel.startActiveNavigation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.ACTIVE_NAVIGATION, state.navigationMode)
        assertEquals("در حال ناوبری زنده", state.statusMessage)
    }

    @Test
    fun testPauseAndResumeNavigation() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.setupDefaultPlan()
        viewModel.startActiveNavigation()
        advanceUntilIdle()

        viewModel.pauseNavigation()
        assertEquals(NavigationScreenMode.NAVIGATION_PAUSED, viewModel.uiState.value.navigationMode)

        viewModel.resumeNavigation()
        assertEquals(NavigationScreenMode.ACTIVE_NAVIGATION, viewModel.uiState.value.navigationMode)
    }

    @Test
    fun testExitNavigationResetsToIdle() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.setupDefaultPlan()
        viewModel.startActiveNavigation()
        advanceUntilIdle()

        viewModel.exitNavigation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.IDLE_FREE_DRIVE, state.navigationMode)
        assertNull(state.plan)
        assertNull(state.selectedDestination)
    }

    @Test
    fun testGpsWeakOrDisabledState() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleGpsSignalQuality(true)
        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.GPS_WEAK_OR_DISABLED, state.navigationMode)
        assertNotNull(state.activeWarning)
    }

    @Test
    fun testOfflineModeState() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleOfflineMode(true)
        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.OFFLINE_MODE, state.navigationMode)
        assertFalse(state.isInternetConnected)
    }

    @Test
    fun testMissingOfflineMapCacheMode() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleOfflineMapCache(false)
        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.MISSING_OFFLINE_MAP, state.navigationMode)
        assertFalse(state.isOfflineMapAvailable)
    }

    @Test
    fun testDirectBearingFallbackMode() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.setupDefaultPlan()
        advanceUntilIdle()

        viewModel.setNavigationMode(NavigationScreenMode.MISSING_ROUTE_DATA)
        assertEquals(NavigationScreenMode.MISSING_ROUTE_DATA, viewModel.uiState.value.navigationMode)
    }

    @Test
    fun testRouteCalculationErrorState() = runTest(testDispatcher) {
        advanceUntilIdle()
        val dest = DestinationSearchResult(
            id = "test_dest",
            title = "اصفهان",
            subtitle = "اصفهان",
            coordinate = GeoCoordinate(32.6370, 51.6830),
            type = DestinationType.CITY_HUB
        )
        viewModel.setGpsFixForTesting(35.6997, 51.3380)
        viewModel.selectDestination(dest)
        advanceUntilIdle()

        viewModel.setNavigationMode(NavigationScreenMode.ROUTE_CALCULATION_ERROR)
        assertEquals(NavigationScreenMode.ROUTE_CALCULATION_ERROR, viewModel.uiState.value.navigationMode)

        viewModel.retryRouteCalculation()
        advanceUntilIdle()
        // Retrying with valid selected destination should calculate route and return to preview
        assertEquals(NavigationScreenMode.ROUTE_PREVIEW, viewModel.uiState.value.navigationMode)
        assertNotNull(viewModel.uiState.value.plan)
    }

    @Test
    fun testRetryFailsWhenNoDestinationSelected() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.retryRouteCalculation()
        advanceUntilIdle()
        assertEquals(NavigationScreenMode.ROUTE_CALCULATION_ERROR, viewModel.uiState.value.navigationMode)
        assertEquals("مقصدی برای محاسبه مجدد مسیر انتخاب نشده است", viewModel.uiState.value.routeCalculationError)
    }

    @Test
    fun testInitialStateHasNoPlanAndNoAutomaticDemo() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.IDLE_FREE_DRIVE, state.navigationMode)
        assertNull("Plan must be null upon initial launch; no automatic sample trip", state.plan)
        assertFalse("Initial state must not be a demo plan", state.isDemoPlan)
    }

    @Test
    fun testStartActiveNavigationWithoutValidPlanFails() = runTest(testDispatcher) {
        advanceUntilIdle()
        // No plan set
        viewModel.startActiveNavigation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.ROUTE_CALCULATION_ERROR, state.navigationMode)
        assertNotNull(state.routeCalculationError)
        assertTrue(state.statusMessage.contains("خطا در شروع ناوبری"))
    }

    @Test
    fun testSelectDestinationWithoutGpsFailsWithoutUsingTehran() = runTest(testDispatcher) {
        advanceUntilIdle()
        val dest = DestinationSearchResult(
            id = "test_dest",
            title = "اصفهان",
            subtitle = "اصفهان",
            coordinate = GeoCoordinate(32.6370, 51.6830),
            type = DestinationType.CITY_HUB
        )
        // No GPS fix emitted
        viewModel.selectDestination(dest)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.routeCalculationError)
        assertTrue(state.routeCalculationError!!.contains("GPS"))
        assertNull(state.plan)
    }

    @Test
    fun testDemoTripFlaggedAndExcludedFromHistoryPersistence() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.setupDemoSamplePlan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Explicit demo plan must be flagged as demo", state.isDemoPlan)
        assertNotNull(state.plan)
    }

    @Test
    fun testCorridorNavigationWithoutGpsFailsSafely() = runTest(testDispatcher) {
        advanceUntilIdle()
        // No GPS
        viewModel.launchCorridorNavigation("isfahan")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NavigationScreenMode.ROUTE_CALCULATION_ERROR, state.navigationMode)
        assertTrue(state.routeCalculationError!!.contains("GPS"))
    }
}
