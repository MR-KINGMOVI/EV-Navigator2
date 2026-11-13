package com.evnavigator.iran.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.catalog.DomainVehicleSocSource
import com.evnavigator.iran.domain.repository.ActiveJourneyStateRepository
import com.evnavigator.iran.domain.repository.ActiveVehicleStateRepository
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import com.evnavigator.iran.domain.repository.VehicleCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StrategicCorridor(
    val title: String,
    val origin: String,
    val destination: String,
    val distanceKm: Int,
    val chargersSummary: String,
    val coveragePercent: Int
)

data class HomeCockpitUiState(
    val isLoading: Boolean = true,
    val activeVehicle: DomainVehicle? = null,
    val batterySocPercent: Double = 85.0,
    val estimatedRangeKm: Int = 380,
    val rangeStandardLabel: String = "WLTP (با ۱۰٪ رزرو ایمنی)",
    val isCharging: Boolean = false,
    val nearbyStations: List<DomainChargingStationWithPorts> = emptyList(),
    val totalVerifiedStations: Int = 0,
    val hasActiveJourney: Boolean = false,
    val activeJourneyStatus: String? = null,
    val strategicCorridors: List<StrategicCorridor> = emptyList()
)

@HiltViewModel
class HomeCockpitViewModel @Inject constructor(
    private val vehicleCatalogRepository: VehicleCatalogRepository,
    private val chargingStationRepository: ChargingStationCatalogRepository,
    private val activeJourneyRepository: ActiveJourneyStateRepository,
    private val activeVehicleRepository: ActiveVehicleStateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeCockpitUiState())
    val uiState: StateFlow<HomeCockpitUiState> = _uiState.asStateFlow()

    init {
        loadCockpitData()
        viewModelScope.launch {
            activeVehicleRepository.activeVehicle.collect { vehicle ->
                if (vehicle != null && _uiState.value.activeVehicle?.id != vehicle.id) {
                    val usableKwh = vehicle.usableBatteryCapacityWh / 1000.0
                    val whPerKm = (vehicle.baselineConsumptionWhPer100Km ?: 16_000.0) / 100.0
                    val currentSoc = _uiState.value.batterySocPercent
                    val usableSocRatio = ((currentSoc - 10.0).coerceAtLeast(0.0)) / 100.0
                    val calculatedRange = ((usableKwh * usableSocRatio * 1000.0) / whPerKm).toInt().coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            activeVehicle = vehicle,
                            estimatedRangeKm = calculatedRange
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            activeVehicleRepository.batterySocPercent.collect { sharedSoc ->
                if (_uiState.value.batterySocPercent != sharedSoc) {
                    val active = _uiState.value.activeVehicle
                    val usableKwh = (active?.usableBatteryCapacityWh ?: 70_000.0) / 1000.0
                    val whPerKm = (active?.baselineConsumptionWhPer100Km ?: 16_000.0) / 100.0
                    val usableSocRatio = ((sharedSoc - 10.0).coerceAtLeast(0.0)) / 100.0
                    val newRange = ((usableKwh * usableSocRatio * 1000.0) / whPerKm).toInt().coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            batterySocPercent = sharedSoc,
                            estimatedRangeKm = newRange
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            try {
                vehicleCatalogRepository.observeAllVehicles().collect { dbVehicles ->
                    if (dbVehicles.isNotEmpty() && _uiState.value.activeVehicle == null) {
                        val active = activeVehicleRepository.getActiveVehicle()
                        if (active != null) {
                            val usableKwh = active.usableBatteryCapacityWh / 1000.0
                            val whPerKm = (active.baselineConsumptionWhPer100Km ?: 16_000.0) / 100.0
                            val currentSoc = _uiState.value.batterySocPercent
                            val usableSocRatio = ((currentSoc - 10.0).coerceAtLeast(0.0)) / 100.0
                            val calculatedRange = ((usableKwh * usableSocRatio * 1000.0) / whPerKm).toInt().coerceAtLeast(0)
                            _uiState.update {
                                it.copy(
                                    activeVehicle = active,
                                    estimatedRangeKm = calculatedRange
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    private fun computeCorridors(allStations: List<DomainChargingStationWithPorts>): List<StrategicCorridor> {
        fun countInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Int {
            return allStations.count { s ->
                s.station.latitude in minLat..maxLat && s.station.longitude in minLon..maxLon
            }
        }
        val c1 = countInBox(32.5, 35.8, 50.7, 51.8)
        val c2 = countInBox(35.5, 37.4, 49.4, 51.5)
        val c3 = countInBox(35.3, 36.5, 51.3, 59.8)
        val c4 = countInBox(29.4, 32.7, 51.5, 52.7)

        return listOf(
            StrategicCorridor("تهران ⇄ قم ⇄ اصفهان", "تهران (میدان آزادی)", "اصفهان (پل خواجو)", 448, if (c1 > 0) "$c1 ایستگاه ثبت‌شده در دیتابیس" else "بدون ایستگاه ثبت‌شده", if (c1 >= 3) 100 else (c1 * 33).coerceAtMost(100)),
            StrategicCorridor("تهران ⇄ قزوین ⇄ رشت", "تهران (برج میلاد)", "رشت (میدان شهرداری)", 325, if (c2 > 0) "$c2 ایستگاه ثبت‌شده در دیتابیس" else "بدون ایستگاه ثبت‌شده", if (c2 >= 2) 100 else (c2 * 50).coerceAtMost(100)),
            StrategicCorridor("تهران ⇄ سمنان ⇄ مشهد", "تهران (آزادراه همت)", "مشهد (حرم مطهر)", 895, if (c3 > 0) "$c3 ایستگاه ثبت‌شده در دیتابیس" else "بدون ایستگاه ثبت‌شده", if (c3 >= 5) 100 else (c3 * 20).coerceAtMost(100)),
            StrategicCorridor("اصفهان ⇄ شیراز", "اصفهان (پل خواجو)", "شیراز (دروازه قرآن)", 480, if (c4 > 0) "$c4 ایستگاه ثبت‌شده در دیتابیس" else "بدون ایستگاه ثبت‌شده", if (c4 >= 3) 100 else (c4 * 33).coerceAtMost(100))
        )
    }

    fun loadCockpitData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Load authoritative active vehicle from ActiveVehicleStateRepository (never blindly .firstOrNull()!)
            val active = try {
                activeVehicleRepository.getActiveVehicle()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("HomeCockpitViewModel", "Failed to load active vehicle", e)
                null
            }

            // 2. Compute range based on active vehicle usable capacity with 10% safety reserve buffer
            val usableKwh = (active?.usableBatteryCapacityWh ?: 0.0) / 1000.0
            val whPerKm = (active?.baselineConsumptionWhPer100Km ?: 16_000.0) / 100.0
            val currentSoc = _uiState.value.batterySocPercent
            val usableSocRatio = ((currentSoc - 10.0).coerceAtLeast(0.0)) / 100.0
            val calculatedRange = if (active != null && whPerKm > 0.0) {
                ((usableKwh * usableSocRatio * 1000.0) / whPerKm).toInt().coerceAtLeast(0)
            } else {
                0
            }

            // 3. Load charging stations from repository
            val stationsWithPorts = try {
                chargingStationRepository.observeAllStationsWithPorts()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("HomeCockpitViewModel", "Failed to load stations", e)
                null
            }

            // 4. Check active journey in repository
            val lastJourney = try {
                activeJourneyRepository.getLatestActiveJourney()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("HomeCockpitViewModel", "Failed to load active journey", e)
                null
            }

            val defaultCorridors = computeCorridors(emptyList())

            stationsWithPorts?.collect { allStations ->
                val sortedFast = allStations
                    .filter { s -> s.ports.any { (it.powerKw ?: 0.0) >= 50.0 } }
                    .take(4)

                val dynamicCorridors = computeCorridors(allStations)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeVehicle = active,
                        estimatedRangeKm = calculatedRange,
                        nearbyStations = sortedFast,
                        totalVerifiedStations = allStations.size,
                        hasActiveJourney = lastJourney != null && lastJourney.lifecycleState != "COMPLETED",
                        activeJourneyStatus = lastJourney?.lifecycleState,
                        strategicCorridors = dynamicCorridors
                    )
                }
            } ?: run {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeVehicle = active,
                        estimatedRangeKm = calculatedRange,
                        strategicCorridors = defaultCorridors
                    )
                }
            }
        }
    }

    fun updateSoc(newSoc: Double) {
        val clamped = newSoc.coerceIn(5.0, 100.0)
        activeVehicleRepository.updateBatterySoc(
            newSoc = clamped,
            source = DomainVehicleSocSource.USER_MANUAL_INPUT
        )
        val active = _uiState.value.activeVehicle
        val usableKwh = (active?.usableBatteryCapacityWh ?: 70_000.0) / 1000.0
        val whPerKm = (active?.baselineConsumptionWhPer100Km ?: 16_000.0) / 100.0
        val usableSocRatio = ((clamped - 10.0).coerceAtLeast(0.0)) / 100.0
        val newRange = ((usableKwh * usableSocRatio * 1000.0) / whPerKm).toInt().coerceAtLeast(0)

        _uiState.update {
            it.copy(
                batterySocPercent = clamped,
                estimatedRangeKm = newRange
            )
        }
    }

    fun toggleChargingState() {
        _uiState.update { it.copy(isCharging = !it.isCharging) }
    }
}
