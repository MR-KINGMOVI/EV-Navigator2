package com.evnavigator.iran.ui.screen.charging

import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.domain.model.catalog.CanonicalAccessType
import com.evnavigator.iran.domain.model.catalog.CanonicalSourceType
import com.evnavigator.iran.domain.model.catalog.ChargingPortDomainModel
import com.evnavigator.iran.domain.model.catalog.ChargingStationDomainModel
import com.evnavigator.iran.domain.model.catalog.ChargingStationWithPortsDomainModel
import com.evnavigator.iran.domain.model.catalog.DomainConnectorType
import com.evnavigator.iran.domain.repository.ChargingStationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingDiscoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockChargingRepository: ChargingStationRepository
    private lateinit var viewModel: ChargingDiscoveryViewModel

    private val sampleStation = ChargingStationWithPortsDomainModel(
        station = ChargingStationDomainModel(
            id = "station_qom_mehromah",
            externalId = "MAPNA_002",
            canonicalName = "ایستگاه فوق‌سریع مجتمع مهر و ماه",
            operatorName = "مپنا",
            latitude = 34.6401,
            longitude = 50.8764,
            address = "کیلومتر ۶ آزادراه قم - تهران، مجتمع گردشگری مهروماه",
            city = "قم",
            province = "قم",
            accessType = CanonicalAccessType.PUBLIC,
            is247 = true,
            openingHoursJson = "24/7",
            source = "MAPNA_OFFICIAL",
            sourceType = CanonicalSourceType.NETWORK_PROVIDER,
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.99,
            validationStatus = "VERIFIED",
            datasetVersion = "2024.1"
        ),
        ports = listOf(
            ChargingPortDomainModel(
                id = "port_qom_1",
                stationId = "station_qom_mehromah",
                connectorType = DomainConnectorType.CCS_COMBO_2,
                connectorCount = 1,
                voltage = 750.0,
                current = 250.0,
                powerKw = 120.0,
                isAc = false,
                isDc = true,
                status = "AVAILABLE",
                source = "MAPNA_OFFICIAL",
                validationStatus = "VERIFIED"
            ),
            ChargingPortDomainModel(
                id = "port_qom_2",
                stationId = "station_qom_mehromah",
                connectorType = DomainConnectorType.GB_T_DC,
                connectorCount = 1,
                voltage = 750.0,
                current = 250.0,
                powerKw = 120.0,
                isAc = false,
                isDc = true,
                status = "AVAILABLE",
                source = "MAPNA_OFFICIAL",
                validationStatus = "VERIFIED"
            )
        )
    )

    private val sampleAcStation = ChargingStationWithPortsDomainModel(
        station = ChargingStationDomainModel(
            id = "station_tehran_ac",
            canonicalName = "ایستگاه پارکینگ شهرداری",
            operatorName = "شهرداری",
            latitude = 35.7000,
            longitude = 51.4000,
            city = "تهران",
            province = "تهران",
            accessType = CanonicalAccessType.PUBLIC,
            source = "MUNICIPALITY",
            sourceType = CanonicalSourceType.GOVERNMENT,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VERIFIED"
        ),
        ports = listOf(
            ChargingPortDomainModel(
                id = "port_ac_1",
                stationId = "station_tehran_ac",
                connectorType = DomainConnectorType.TYPE_2,
                connectorCount = 1,
                voltage = 400.0,
                current = 32.0,
                powerKw = 22.0,
                isAc = true,
                isDc = false,
                status = "AVAILABLE",
                source = "MUNICIPALITY",
                validationStatus = "VERIFIED"
            )
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockChargingRepository = mock(ChargingStationRepository::class.java)
        `when`(mockChargingRepository.observeAllStationsWithPorts()).thenReturn(flowOf(listOf(sampleStation, sampleAcStation)))
        `when`(mockChargingRepository.getAllStationsWithPorts()).thenReturn(flowOf(listOf(sampleStation, sampleAcStation)))
        viewModel = ChargingDiscoveryViewModel(mockChargingRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialLoadingAndCounts() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.totalStationsCount)
        assertEquals(1, state.fastStationsCount)
        assertEquals(3, state.totalPortsCount)
        assertEquals(2, state.filteredStations.size)
    }

    @Test
    fun testSearchQueryFiltering() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("مهروماه")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredStations.size)
        assertEquals("station_qom_mehromah", state.filteredStations.first().station.id)
    }

    @Test
    fun testConnectorFilter() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onConnectorFilterSelected(ConnectorFilter.TYPE_2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredStations.size)
        assertEquals("station_tehran_ac", state.filteredStations.first().station.id)
    }

    @Test
    fun testPowerTierFilter() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onPowerFilterSelected(PowerFilter.ULTRA_FAST)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredStations.size)
        assertEquals("station_qom_mehromah", state.filteredStations.first().station.id)
    }

    @Test
    fun testCreateCustomStation() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.createCustomStation(
            name = "ایستگاه سفارشی من",
            operator = "شخصی",
            city = "کرج",
            province = "البرز",
            address = "بزرگراه کرج-قزوین",
            latitude = 35.8500,
            longitude = 50.9500,
            powerKw = 60.0,
            connectorType = ConnectorType.CCS_COMBO_2,
            is247 = true
        )
        advanceUntilIdle()

        verify(mockChargingRepository).insertStation(any())
        verify(mockChargingRepository).insertPort(any())
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun testDeleteStation() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.deleteStation("station_qom_mehromah")
        advanceUntilIdle()

        verify(mockChargingRepository).deletePortsForStation("station_qom_mehromah")
        verify(mockChargingRepository).deleteStationById("station_qom_mehromah")
    }
}
