package com.evnavigator.iran.ui.screen.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainAccessType
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.catalog.DomainChargingStation
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainSourceType
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ConnectorFilter(val label: String) {
    ALL("همه کانکتورها"),
    CCS2("CCS2 DC"),
    GB_T("GB/T DC"),
    TYPE_2("Type 2 AC")
}

enum class PowerFilter(val label: String) {
    ALL("همه توان‌ها"),
    ULTRA_FAST("فوق‌سریع (۱۰۰+ kW)"),
    FAST("سریع (۵۰+ kW)"),
    AC_NORMAL("استاندارد AC (تا ۲۲ kW)")
}

data class ChargingDiscoveryUiState(
    val stations: List<DomainChargingStationWithPorts> = emptyList(),
    val filteredStations: List<DomainChargingStationWithPorts> = emptyList(),
    val searchQuery: String = "",
    val connectorFilter: ConnectorFilter = ConnectorFilter.ALL,
    val powerFilter: PowerFilter = PowerFilter.ALL,
    val selectedStation: DomainChargingStationWithPorts? = null,
    val isCreateDialogOpen: Boolean = false,
    val isLoading: Boolean = true,
    val totalStationsCount: Int = 0,
    val fastStationsCount: Int = 0,
    val totalPortsCount: Int = 0,
    val userMessage: String? = null
)

@HiltViewModel
class ChargingDiscoveryViewModel @Inject constructor(
    private val chargingStationRepository: ChargingStationCatalogRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _connectorFilter = MutableStateFlow(ConnectorFilter.ALL)
    private val _powerFilter = MutableStateFlow(PowerFilter.ALL)
    private val _selectedStation = MutableStateFlow<DomainChargingStationWithPorts?>(null)
    private val _isCreateDialogOpen = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)

    private val _filterParams = combine(_searchQuery, _connectorFilter, _powerFilter) { q, c, p ->
        Triple(q, c, p)
    }

    private val _stationsWithFilter = combine(
        chargingStationRepository.observeAllStationsWithPorts(),
        _filterParams
    ) { allStations, (query, connFilter, pwrFilter) ->
        val filtered = allStations.filter { stationWithPorts ->
            val station = stationWithPorts.station
            val ports = stationWithPorts.ports

            val matchesQuery = query.isBlank() ||
                    station.canonicalName.contains(query, ignoreCase = true) ||
                    (station.city?.contains(query, ignoreCase = true) == true) ||
                    (station.province?.contains(query, ignoreCase = true) == true) ||
                    (station.operatorName?.contains(query, ignoreCase = true) == true) ||
                    (station.address?.contains(query, ignoreCase = true) == true)

            val matchesConnector = when (connFilter) {
                ConnectorFilter.ALL -> true
                ConnectorFilter.CCS2 -> ports.any { it.connectorType == DomainConnectorType.CCS_COMBO_2 }
                ConnectorFilter.GB_T -> ports.any { it.connectorType == DomainConnectorType.GB_T_DC || it.connectorType == DomainConnectorType.GB_T_AC }
                ConnectorFilter.TYPE_2 -> ports.any { it.connectorType == DomainConnectorType.TYPE_2 }
            }

            val maxPower = ports.maxOfOrNull { it.powerKw ?: 0.0 } ?: 0.0
            val matchesPower = when (pwrFilter) {
                PowerFilter.ALL -> true
                PowerFilter.ULTRA_FAST -> maxPower >= 100.0
                PowerFilter.FAST -> maxPower >= 50.0
                PowerFilter.AC_NORMAL -> maxPower in 1.0..25.0
            }

            matchesQuery && matchesConnector && matchesPower
        }
        allStations to filtered
    }

    val uiState: StateFlow<ChargingDiscoveryUiState> = combine(
        _stationsWithFilter,
        _filterParams,
        _selectedStation,
        _isCreateDialogOpen,
        _userMessage
    ) { (allStations, filtered), (q, c, p), selected, isCreateOpen, message ->
        val totalPorts = allStations.sumOf { it.ports.size }
        val fastCount = allStations.count { station ->
            station.ports.any { (it.powerKw ?: 0.0) >= 50.0 }
        }

        ChargingDiscoveryUiState(
            stations = allStations,
            filteredStations = filtered,
            searchQuery = q,
            connectorFilter = c,
            powerFilter = p,
            selectedStation = selected,
            isCreateDialogOpen = isCreateOpen,
            isLoading = false,
            totalStationsCount = allStations.size,
            fastStationsCount = fastCount,
            totalPortsCount = totalPorts,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChargingDiscoveryUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onConnectorFilterSelected(filter: ConnectorFilter) {
        _connectorFilter.value = filter
    }

    fun onPowerFilterSelected(filter: PowerFilter) {
        _powerFilter.value = filter
    }

    fun onSelectStation(station: DomainChargingStationWithPorts?) {
        _selectedStation.value = station
    }

    fun setCreateDialogOpen(open: Boolean) {
        _isCreateDialogOpen.value = open
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun createCustomStation(
        name: String,
        operator: String,
        city: String,
        province: String,
        address: String,
        latitude: Double,
        longitude: Double,
        powerKw: Double,
        connectorType: ConnectorType,
        is247: Boolean
    ) {
        viewModelScope.launch {
            val stationId = "custom_station_${UUID.randomUUID().toString().take(8)}"
            val now = System.currentTimeMillis()
            val domainConnectorType = DomainConnectorType.fromRawName(connectorType.name)

            val station = DomainChargingStation(
                id = stationId,
                externalId = "USER_ADDED",
                canonicalName = name.ifBlank { "ایستگاه سفارشی کاربر" },
                operatorName = operator.ifBlank { "شخصی / عمومی" },
                latitude = latitude,
                longitude = longitude,
                province = province.ifBlank { "ایران" },
                city = city.ifBlank { "نامشخص" },
                address = address.ifBlank { "ثبت‌شده توسط کاربر" },
                accessType = DomainAccessType.PUBLIC,
                is247 = is247,
                openingHoursJson = if (is247) "24/7" else "نامشخص",
                source = "USER_CUSTOM",
                sourceType = DomainSourceType.USER_CONTRIBUTION,
                retrievedDate = now,
                confidence = 1.0,
                validationStatus = "VALIDATED",
                isPrimarySource = true,
                datasetVersion = "CUSTOM_1.0"
            )

            val port = DomainChargingPort(
                id = "port_${stationId}_1",
                stationId = stationId,
                connectorType = domainConnectorType,
                connectorCount = 1,
                voltage = if (domainConnectorType == DomainConnectorType.TYPE_2) 400.0 else 500.0,
                current = (powerKw * 1000.0 / 400.0).coerceAtLeast(16.0),
                powerKw = powerKw,
                isAc = domainConnectorType == DomainConnectorType.TYPE_2 || domainConnectorType == DomainConnectorType.GB_T_AC,
                isDc = domainConnectorType == DomainConnectorType.CCS_COMBO_2 || domainConnectorType == DomainConnectorType.GB_T_DC,
                phaseCount = if (domainConnectorType == DomainConnectorType.TYPE_2) 3 else null,
                protocol = domainConnectorType.name,
                status = "AVAILABLE",
                source = "USER_CUSTOM",
                retrievedDate = now,
                confidence = 1.0,
                validationStatus = "VALIDATED"
            )

            chargingStationRepository.insertStation(station)
            chargingStationRepository.insertPort(port)

            _isCreateDialogOpen.value = false
            _userMessage.value = "ایستگاه جدید «$name» با موفقیت ثبت شد."
        }
    }

    fun deleteStation(stationId: String) {
        viewModelScope.launch {
            chargingStationRepository.deletePortsForStation(stationId)
            chargingStationRepository.deleteStationById(stationId)
            _selectedStation.value = null
            _userMessage.value = "ایستگاه با موفقیت حذف گردید."
        }
    }
}
