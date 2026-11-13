package com.evnavigator.iran.ui.screen.home

import com.evnavigator.iran.domain.model.catalog.VehicleDomainModel
import com.evnavigator.iran.domain.repository.ActiveJourneyPersistenceRepository
import com.evnavigator.iran.domain.repository.ActiveVehicleStateRepository
import com.evnavigator.iran.domain.repository.ChargingStationRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeCockpitViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockVehicleRepository = mock(VehicleCatalogRepository::class.java)
    private val mockChargingRepository = mock(ChargingStationRepository::class.java)
    private val mockJourneyRepository = mock(ActiveJourneyPersistenceRepository::class.java)
    private val mockActiveVehicleRepository = mock(ActiveVehicleStateRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        `when`(mockActiveVehicleRepository.activeVehicle).thenReturn(MutableStateFlow(null))
        `when`(mockActiveVehicleRepository.batterySocPercent).thenReturn(MutableStateFlow(85.0))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestVehicle(
        id: String,
        capacityWh: Double,
        consumptionWhPer100Km: Double
    ): VehicleDomainModel {
        return VehicleDomainModel(
            id = id,
            name = "Test EV",
            manufacturer = "TestBrand",
            model = "Model X",
            generation = "1st",
            modelYear = 2024,
            trim = "Base",
            market = "IR",
            powertrainType = "BEV",
            curbMassKg = 2000.0,
            grossVehicleMassKg = 2400.0,
            lengthMm = 4700.0,
            widthMm = 1900.0,
            heightMm = 1600.0,
            wheelbaseMm = 2800.0,
            groundClearanceMm = 150.0,
            dragCoefficient = 0.28,
            frontalAreaM2 = 2.30,
            rollingResistanceCoefficient = 0.010,
            nominalBatteryCapacityWh = capacityWh,
            usableBatteryCapacityWh = capacityWh,
            batteryChemistry = "LFP",
            nominalVoltageV = 400.0,
            batteryConfiguration = "96S",
            motorCount = 1,
            frontMotorPowerKw = 150.0,
            rearMotorPowerKw = 0.0,
            peakMotorPowerKw = 150.0,
            continuousMotorPowerKw = 75.0,
            torqueNm = 310.0,
            drivetrainType = "FWD",
            baselineConsumptionWhPer100Km = consumptionWhPer100Km,
            acChargingMaxPowerKw = 11.0,
            dcChargingMaxPowerKw = 100.0,
            chargingVoltageRangeV = "300-450",
            chargingCurrentRangeA = "0-250",
            chargePortCount = 1,
            connectorTypes = listOf("CCS2"),
            wheelDiameterMm = 450.0,
            tireWidthMm = 235.0,
            dataQuality = "HIGH",
            source = "TEST",
            sourceVersion = "1.0",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.99,
            validationStatus = "VERIFIED",
            notes = "Test vehicle",
            schemaVersion = 1,
            dataVersion = 1,
            vehicleRecordVersion = "1.0",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testRangeCalculationAppliesSafetyReserve() = runTest(testDispatcher) {
        val testVehicle = createTestVehicle(
            id = "byd_tang_ev",
            capacityWh = 86_400.0,
            consumptionWhPer100Km = 20_000.0
        )
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(testVehicle))
        `when`(mockChargingRepository.observeAllStationsWithPorts()).thenReturn(flowOf(emptyList()))
        `when`(mockActiveVehicleRepository.getActiveVehicle()).thenReturn(testVehicle)

        val viewModel = HomeCockpitViewModel(
            mockVehicleRepository,
            mockChargingRepository,
            mockJourneyRepository,
            mockActiveVehicleRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("byd_tang_ev", state.activeVehicle?.id)
        assertEquals(324, state.estimatedRangeKm)
        assertTrue(state.rangeStandardLabel.contains("۱۰٪ رزرو"))
    }

    @Test
    fun testFirstVehicleSelectedWithoutHardcodedPreference() = runTest(testDispatcher) {
        val vehicle1 = createTestVehicle("first_vehicle", 60_000.0, 15_000.0)
        val vehicle2 = createTestVehicle("audi_q5_etron_40", 80_000.0, 17_000.0)
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(vehicle1, vehicle2))
        `when`(mockChargingRepository.observeAllStationsWithPorts()).thenReturn(flowOf(emptyList()))
        `when`(mockActiveVehicleRepository.getActiveVehicle()).thenReturn(vehicle1)

        val viewModel = HomeCockpitViewModel(
            mockVehicleRepository,
            mockChargingRepository,
            mockJourneyRepository,
            mockActiveVehicleRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("first_vehicle", viewModel.uiState.value.activeVehicle?.id)
    }
}
