package com.evnavigator.iran.ui.screen

import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.charging.RiskAwareChargingEvaluator
import com.evnavigator.iran.domain.intelligence.config.IntelligenceConfigurationManager
import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.routing.IntelligentRouteScorer
import com.evnavigator.iran.domain.intelligence.routing.RouteCandidate
import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective
import com.evnavigator.iran.domain.intelligence.uncertainty.ConfidenceAndUncertaintyEngine
import com.evnavigator.iran.ui.screen.intelligence.TripIntelligenceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripIntelligenceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TripIntelligenceViewModel
    private val configManager = IntelligenceConfigurationManager()
    private val mockVehicleRepository = org.mockito.Mockito.mock(com.evnavigator.iran.domain.repository.VehicleCatalogRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TripIntelligenceViewModel(
            energyOrchestrator = PredictiveEnergyOrchestrator(),
            uncertaintyEngine = ConfidenceAndUncertaintyEngine(),
            routeScorer = IntelligentRouteScorer(),
            chargingEvaluator = RiskAwareChargingEvaluator(),
            configManager = configManager,
            vehicleRepository = mockVehicleRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSampleContext(): TripIntelligenceContext {
        val now = System.currentTimeMillis()
        return TripIntelligenceContext(
            vehicleId = "test_ev",
            batteryCapacityKwh = InputQualityAttribute.verified(60.0),
            curbWeightKg = InputQualityAttribute.verified(1600.0),
            additionalPayloadKg = InputQualityAttribute.verified(100.0),
            dragCoefficient = InputQualityAttribute.verified(0.28),
            frontalAreaM2 = InputQualityAttribute.verified(2.2),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010),
            driveEfficiency = InputQualityAttribute.verified(0.90),
            currentSocPercent = InputQualityAttribute.measured(80.0, now),
            targetArrivalSocPercent = InputQualityAttribute.userConfigured(15.0),
            minReserveSocPercent = InputQualityAttribute.userConfigured(10.0),
            routeDistanceMeters = 100_000.0,
            routeEstimatedDurationSeconds = 3600.0,
            elevationGainMeters = InputQualityAttribute.verified(100.0),
            elevationLossMeters = InputQualityAttribute.verified(100.0),
            averageSpeedKmh = InputQualityAttribute.verified(100.0),
            ambientTemperatureCelsius = InputQualityAttribute.measured(20.0, now),
            windSpeedMps = InputQualityAttribute.measured(0.0, now),
            windDirectionDegrees = InputQualityAttribute.measured(0.0, now),
            hvacPowerKw = InputQualityAttribute.verified(0.5),
            drivingMode = DrivingMode.NORMAL,
            weatherFreshnessTimestamp = now,
            trafficFreshnessTimestamp = now
        )
    }

    @Test
    fun testInitialUiState() {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(RouteOptimizationObjective.BALANCED, state.selectedObjective)
        assertEquals(10.0, state.minReserveSocPercent, 0.001)
    }

    @Test
    fun testEvaluateTripUpdatesUiState() {
        val ctx = createSampleContext()
        val candidate = RouteCandidate(
            routeId = "route_1",
            distanceMeters = 100_000.0,
            durationSeconds = 3600.0,
            predictedEnergyKwh = 18.0,
            predictedArrivalSocPercent = 50.0,
            chargingStopCount = 0,
            totalChargingAndDetourMinutes = 0.0,
            uncertaintyScore = 0.10
        )
        val station = CandidateStation(
            stationId = "st_1",
            stationName = "Test Fast Charger",
            compatibleConnectors = listOf("CCS2"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 500.0,
            detourDurationSeconds = 60.0,
            distanceAlongRouteMeters = 50_000.0
        )

        viewModel.evaluateTrip(
            context = ctx,
            routeCandidates = listOf(candidate),
            candidateStations = listOf(station)
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.predictedArrivalSocPercent)
        assertTrue(state.predictedArrivalSocPercent!! > 0.0)
        assertTrue(state.isFeasibleWithoutCharging)
        assertEquals(1, state.scoredRoutes.size)
        assertEquals(1, state.recommendedChargingStops.size)
        assertTrue(state.explanations.isNotEmpty())
    }

    @Test
    fun testUpdateOptimizationObjectivePropagates() {
        viewModel.setOptimizationObjective(RouteOptimizationObjective.MINIMUM_ENERGY)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RouteOptimizationObjective.MINIMUM_ENERGY, viewModel.uiState.value.selectedObjective)
    }

    @Test
    fun testPlanTripUnknownRouteSetsError() {
        viewModel.planTrip(origin = "ناکجا آباد", destination = "شهر دوردست", departureSocPercent = 80.0)
        val state = viewModel.uiState.value
        assertEquals("برای این مسیر داده نداریم", state.errorMessage)
        assertNull(state.totalDistanceKm)
        assertNull(state.routeDistanceKm)
    }
}
