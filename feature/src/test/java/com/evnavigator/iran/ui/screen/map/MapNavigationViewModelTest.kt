package com.evnavigator.iran.ui.screen.map

import android.content.Context
import android.location.LocationManager
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.repository.ChargingStationRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import com.evnavigator.iran.ui.screen.map.model.RouteCalculationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
class MapNavigationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockChargingRepository: ChargingStationRepository
    private lateinit var mockVehicleRepository: VehicleCatalogRepository
    private lateinit var viewModel: MapNavigationViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mock(Context::class.java)
        mockLocationManager = mock(LocationManager::class.java)
        mockChargingRepository = mock(ChargingStationRepository::class.java)
        mockVehicleRepository = mock(VehicleCatalogRepository::class.java)

        `when`(mockContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockLocationManager)
        `when`(mockChargingRepository.getAllStations()).thenReturn(flowOf(emptyList()))

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
        viewModel = MapNavigationViewModel(
            context = mockContext,
            chargingStationRepository = mockChargingRepository,
            vehicleRepository = mockVehicleRepository,
            realRoadRoutingEngine = offlineEngine,
            routeCoordinator = testRouteCoordinator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsIdle() {
        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertFalse(state.isSearching)
        assertNull(state.selectedDestination)
        assertTrue(state.routeState is RouteCalculationState.Idle)
        assertTrue(state.isChargingLayerVisible)
        assertEquals(85.0, state.userBatterySocPercent, 0.01)
    }

    @Test
    fun testSearchFilterHubs() {
        viewModel.onSearchQueryChanged("اصفهان")
        val state = viewModel.uiState.value
        assertTrue(state.isSearching)
        assertFalse(state.searchResults.isEmpty())
        assertTrue(state.searchResults.any { it.title.contains("اصفهان") })
    }

    @Test
    fun testSearchEmptyQueryClearsResults() {
        viewModel.onSearchQueryChanged("تهران")
        assertFalse(viewModel.uiState.value.searchResults.isEmpty())

        viewModel.onSearchQueryChanged("")
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun testSelectDestinationCalculatesFeasibleRoute() = runTest(testDispatcher) {
        val destination = DestinationSearchResult(
            id = "hub_qom",
            title = "قم (مجتمع مهروماه)",
            subtitle = "آزادراه تهران-قم",
            coordinate = GeoCoordinate(34.6401, 50.8764),
            type = DestinationType.CHARGING_STATION
        )

        viewModel.setGpsFixForTesting(35.6997, 51.3380)
        viewModel.selectDestination(destination)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        System.err.println("MAP_DEBUG: selected=${state.selectedDestination}, routeState=${state.routeState}")
        assertEquals(destination, state.selectedDestination)
        assertTrue("Route calculation should be successful", state.routeState is RouteCalculationState.Success)

        val route = state.routeState as RouteCalculationState.Success
        assertTrue("Distance should be greater than 50 km", route.totalDistanceKm > 50.0)
        assertTrue("Duration should be positive", route.totalDurationMinutes > 0)
        assertTrue("Energy should be positive", route.totalEnergyKwh > 0.0)
        assertTrue("Arrival SOC should be positive", route.arrivalSocPercent > 0.0)
        assertFalse("Polyline points should not be empty", route.polylinePoints.isEmpty())
        assertTrue("Route should be feasible with 85% starting SOC", route.isFeasible)
    }

    @Test
    fun testSelectMapCoordinateCreatesDestination() = runTest(testDispatcher) {
        val tappedCoord = GeoCoordinate(34.6401, 50.8764)
        viewModel.setGpsFixForTesting(35.6997, 51.3380)
        viewModel.selectMapCoordinate(tappedCoord)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.selectedDestination)
        assertEquals(DestinationType.CUSTOM_MAP_PIN, state.selectedDestination?.type)
        assertEquals(tappedCoord.latitude, state.selectedDestination?.coordinate?.latitude ?: 0.0, 0.001)
        assertTrue(state.routeState is RouteCalculationState.Success)
    }

    @Test
    fun testSelectDestinationFailsWhenGpsNotAcquired() = runTest(testDispatcher) {
        val destination = DestinationSearchResult(
            id = "hub_qom",
            title = "قم (مجتمع مهروماه)",
            subtitle = "آزادراه تهران-قم",
            coordinate = GeoCoordinate(34.6401, 50.8764),
            type = DestinationType.CHARGING_STATION
        )

        // Without setting GPS fix, origin must not be fabricated at Tehran
        viewModel.selectDestination(destination)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Route calculation must fail without genuine GPS fix", state.routeState is RouteCalculationState.Error)
        val errorMsg = (state.routeState as RouteCalculationState.Error).message
        assertTrue("Error message must notify user about GPS", errorMsg.contains("GPS"))
    }

    @Test
    fun testRouteToNearestFastChargerFailsWhenGpsNotAcquired() = runTest(testDispatcher) {
        viewModel.routeToNearestFastCharger()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Routing to nearest charger must fail when GPS is not acquired", state.routeState is RouteCalculationState.Error)
        val errorMsg = (state.routeState as RouteCalculationState.Error).message
        assertTrue("Error message must notify user about GPS", errorMsg.contains("GPS"))
    }

    @Test
    fun testClearDestinationResetsRoute() = runTest(testDispatcher) {
        val destination = DestinationSearchResult(
            id = "hub_tehran",
            title = "تهران",
            subtitle = "مرکز",
            coordinate = GeoCoordinate(35.6997, 51.3380),
            type = DestinationType.CITY_HUB
        )
        viewModel.selectDestination(destination)
        advanceUntilIdle()

        viewModel.clearDestination()
        val state = viewModel.uiState.value
        assertNull(state.selectedDestination)
        assertEquals("", state.searchQuery)
        assertTrue(state.routeState is RouteCalculationState.Idle)
    }

    @Test
    fun testUpdateBatterySocClamped() {
        viewModel.updateBatterySoc(150.0)
        assertEquals(100.0, viewModel.uiState.value.userBatterySocPercent, 0.01)

        viewModel.updateBatterySoc(-10.0)
        assertEquals(5.0, viewModel.uiState.value.userBatterySocPercent, 0.01)

        viewModel.updateBatterySoc(65.0)
        assertEquals(65.0, viewModel.uiState.value.userBatterySocPercent, 0.01)
    }

    @Test
    fun testToggleChargingLayer() {
        val initial = viewModel.uiState.value.isChargingLayerVisible
        viewModel.toggleChargingLayer()
        assertEquals(!initial, viewModel.uiState.value.isChargingLayerVisible)
        viewModel.toggleChargingLayer()
        assertEquals(initial, viewModel.uiState.value.isChargingLayerVisible)
    }
}
