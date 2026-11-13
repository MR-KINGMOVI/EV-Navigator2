package com.evnavigator.iran.ui.screen.vehicle

import com.evnavigator.iran.domain.model.catalog.ActiveVehicleDomainStateHolder
import com.evnavigator.iran.domain.model.catalog.DomainActiveVehicleSelectionState
import com.evnavigator.iran.domain.model.catalog.VehicleDomainModel
import com.evnavigator.iran.domain.repository.ActiveVehicleStateRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleGarageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockVehicleRepository: VehicleCatalogRepository
    private lateinit var mockActiveVehicleRepository: ActiveVehicleStateRepository
    private lateinit var viewModel: VehicleGarageViewModel

    private val sampleVehicle = VehicleDomainModel(
        id = "skywell_et5_2023",
        name = "Skywell ET5",
        manufacturer = "Skywell",
        model = "ET5",
        generation = "1st",
        modelYear = 2023,
        trim = "Comfort",
        market = "IR",
        powertrainType = "BEV",
        curbMassKg = 1920.0,
        grossVehicleMassKg = 2300.0,
        lengthMm = 4698.0,
        widthMm = 1908.0,
        heightMm = 1696.0,
        wheelbaseMm = 2800.0,
        groundClearanceMm = 160.0,
        dragCoefficient = 0.30,
        frontalAreaM2 = 2.30,
        rollingResistanceCoefficient = 0.011,
        nominalBatteryCapacityWh = 72_000.0,
        usableBatteryCapacityWh = 69_000.0,
        batteryChemistry = "NMC",
        nominalVoltageV = 380.0,
        batteryConfiguration = "96S",
        motorCount = 1,
        frontMotorPowerKw = 0.0,
        rearMotorPowerKw = 150.0,
        peakMotorPowerKw = 150.0,
        continuousMotorPowerKw = 75.0,
        torqueNm = 320.0,
        drivetrainType = "FWD",
        baselineConsumptionWhPer100Km = 17_000.0,
        acChargingMaxPowerKw = 11.0,
        dcChargingMaxPowerKw = 80.0,
        chargingVoltageRangeV = "300-450",
        chargingCurrentRangeA = "0-200",
        chargePortCount = 2,
        connectorTypes = listOf("CCS2", "Type 2"),
        wheelDiameterMm = 482.0,
        tireWidthMm = 235.0,
        dataQuality = "HIGH",
        source = "NAVIGATOR_VERIFIED",
        sourceVersion = "2024.1",
        retrievedDate = System.currentTimeMillis(),
        confidence = 0.99,
        validationStatus = "VERIFIED",
        notes = "تست خودرو",
        schemaVersion = 1,
        dataVersion = 1,
        vehicleRecordVersion = "1.0",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ActiveVehicleDomainStateHolder.resetSharedStateForTesting()
        mockVehicleRepository = mock(VehicleCatalogRepository::class.java)
        mockActiveVehicleRepository = mock(ActiveVehicleStateRepository::class.java)
        `when`(mockVehicleRepository.observeAllVehicles()).thenReturn(flowOf(listOf(sampleVehicle)))
        `when`(mockActiveVehicleRepository.activeVehicleId).thenReturn(MutableStateFlow(""))
        `when`(mockActiveVehicleRepository.batterySocPercent).thenReturn(MutableStateFlow(85.0))
    }

    @After
    fun tearDown() {
        ActiveVehicleDomainStateHolder.resetSharedStateForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoadingLoadsVehiclesFromDaoWithoutSilentAutoSelection() = runTest {
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(sampleVehicle))
        `when`(mockActiveVehicleRepository.getActiveVehicle()).thenReturn(null)

        viewModel = VehicleGarageViewModel(mockVehicleRepository, mockActiveVehicleRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.vehicles.size)
        // Cold boot must NOT auto-select first() vehicle without explicit user selection
        assertEquals("", state.activeVehicleId)
    }

    @Test
    fun testSelectActiveVehicleUpdatesStateWhenValid() = runTest {
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(sampleVehicle))
        `when`(mockVehicleRepository.getVehicleById("skywell_et5_2023")).thenReturn(sampleVehicle)
        `when`(mockActiveVehicleRepository.setActiveVehicleId("skywell_et5_2023"))
            .thenReturn(DomainActiveVehicleSelectionState.Selected(sampleVehicle))

        viewModel = VehicleGarageViewModel(mockVehicleRepository, mockActiveVehicleRepository)
        advanceUntilIdle()

        viewModel.selectActiveVehicle("skywell_et5_2023")
        advanceUntilIdle()
        assertEquals("skywell_et5_2023", viewModel.uiState.value.activeVehicleId)
    }

    @Test
    fun testUpdateBatterySocClamped() = runTest {
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(sampleVehicle))

        viewModel = VehicleGarageViewModel(mockVehicleRepository, mockActiveVehicleRepository)
        advanceUntilIdle()

        viewModel.updateBatterySoc(150.0)
        assertEquals(100.0, viewModel.uiState.value.currentSocPercent, 0.01)

        viewModel.updateBatterySoc(1.0)
        assertEquals(5.0, viewModel.uiState.value.currentSocPercent, 0.01)

        viewModel.updateBatterySoc(75.0)
        assertEquals(75.0, viewModel.uiState.value.currentSocPercent, 0.01)
    }

    @Test
    fun testSearchAndFilterQueryUpdates() = runTest {
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(sampleVehicle))

        viewModel = VehicleGarageViewModel(mockVehicleRepository, mockActiveVehicleRepository)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Skywell")
        assertEquals("Skywell", viewModel.uiState.value.searchQuery)

        viewModel.onConnectorFilterSelected("CCS2")
        assertEquals("CCS2", viewModel.uiState.value.selectedConnectorFilter)
    }

    @Test
    fun testSaveCustomVehiclePersistsToDao() = runTest {
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(sampleVehicle))

        viewModel = VehicleGarageViewModel(mockVehicleRepository, mockActiveVehicleRepository)
        advanceUntilIdle()

        viewModel.openCustomVehicleDialog()
        assertTrue(viewModel.uiState.value.isCustomVehicleDialogOpen)

        val form = CustomVehicleForm(
            name = "My Custom EV",
            manufacturer = "Custom Corp",
            model = "Prototype 1",
            batteryGrossKwh = 80.0,
            batteryUsableKwh = 75.0,
            maxDcPowerKw = 120.0
        )
        viewModel.onCustomFormChanged(form)
        viewModel.saveCustomVehicle()
        advanceUntilIdle()

        verify(mockVehicleRepository, atLeastOnce()).insertVehicle(any())
        assertFalse(viewModel.uiState.value.isCustomVehicleDialogOpen)
    }

    @Test
    fun testDeleteVehicleCallsDao() = runTest {
        `when`(mockVehicleRepository.getAllVehicles()).thenReturn(listOf(sampleVehicle))

        viewModel = VehicleGarageViewModel(mockVehicleRepository, mockActiveVehicleRepository)
        advanceUntilIdle()

        viewModel.deleteVehicle("custom_123")
        advanceUntilIdle()

        verify(mockVehicleRepository).deleteVehicleById("custom_123")
    }
}
