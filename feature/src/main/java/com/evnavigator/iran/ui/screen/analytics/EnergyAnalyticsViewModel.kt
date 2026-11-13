package com.evnavigator.iran.ui.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.intelligence.energy.DefaultEnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnergyPredictionService
import com.evnavigator.iran.domain.intelligence.energy.EnvironmentalConditions
import com.evnavigator.iran.domain.intelligence.energy.RouteProfileInput
import com.evnavigator.iran.domain.intelligence.energy.RouteSegmentInput
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.model.catalog.DomainActiveVehicleSelectionState
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.repository.ActiveVehicleStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnergyAnalyticsUiState(
    val activeVehicle: DomainVehicle? = null,
    val testSpeedKmh: Float = 100f,
    val ambientTempC: Float = 25f,
    val isHvacActive: Boolean = true,
    val aeroPowerKw: Double = 0.0,
    val rollingPowerKw: Double = 0.0,
    val hvacPowerKw: Double = 0.0,
    val totalPowerKw: Double = 0.0,
    val consumptionWhPerKm: Double = 0.0,
    val isAssumedData: Boolean = false,
    val vehicleModelDisplayName: String = "خودرو انتخاب نشده"
)

@HiltViewModel
class EnergyAnalyticsViewModel @Inject constructor(
    private val activeVehicleRepository: ActiveVehicleStateRepository
) : ViewModel() {

    private val energyService: EnergyPredictionService = DefaultEnergyPredictionService()

    private val _uiState = MutableStateFlow(EnergyAnalyticsUiState())
    val uiState: StateFlow<EnergyAnalyticsUiState> = _uiState.asStateFlow()

    init {
        recalculate()
        viewModelScope.launch {
            try {
                activeVehicleRepository.refreshActiveVehicle()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        viewModelScope.launch {
            activeVehicleRepository.activeVehicle.collect { vehicle ->
                if (vehicle != null) {
                    _uiState.update {
                        it.copy(
                            activeVehicle = vehicle,
                            vehicleModelDisplayName = "${vehicle.manufacturer} ${vehicle.model} (${(vehicle.usableBatteryCapacityWh / 1000.0).toInt()} kWh)"
                        )
                    }
                } else {
                    val selState = activeVehicleRepository.selectionState.value
                    val label = when (selState) {
                        is DomainActiveVehicleSelectionState.InvalidSelection -> "خودروی نامعتبر (${selState.requestedId})"
                        else -> "خودرو انتخاب نشده (فرضی)"
                    }
                    _uiState.update {
                        it.copy(
                            activeVehicle = null,
                            vehicleModelDisplayName = label
                        )
                    }
                }
                recalculate()
            }
        }
    }

    fun updateParameters(speedKmh: Float, tempC: Float, hvac: Boolean) {
        _uiState.update {
            it.copy(
                testSpeedKmh = speedKmh,
                ambientTempC = tempC,
                isHvacActive = hvac
            )
        }
        recalculate()
    }

    /**
     * Delegates segment energy and power calculation to the single canonical [EnergyPredictionService]
     * over a 1,000-meter reference segment at sea level so numbers match route predictions 100%.
     */
    private fun recalculate() {
        val state = _uiState.value
        val vehicle = state.activeVehicle
        val massKg = (vehicle?.curbMassKg ?: 1950.0) + 150.0
        val cd = vehicle?.dragCoefficient ?: 0.28
        val frontalArea = vehicle?.frontalAreaM2 ?: 2.30
        val crr = vehicle?.rollingResistanceCoefficient ?: 0.010
        val usableKwh = (vehicle?.usableBatteryCapacityWh ?: 75_000.0) / 1000.0
        val isAssumed = vehicle == null || (vehicle.validationStatus != "VERIFIED" && vehicle.validationStatus != "VALIDATED")

        val speedKmh = state.testSpeedKmh.toDouble().coerceIn(10.0, 140.0)
        val baseHvacKw = if (state.isHvacActive) 0.8 else 0.15

        val prediction = energyService.predict(
            route = RouteProfileInput(
                segments = listOf(
                    RouteSegmentInput(
                        distanceMeters = 1000.0,
                        startElevationMeters = 0.0,
                        endElevationMeters = 0.0,
                        speedLimitKmh = speedKmh
                    )
                ),
                routeHeadingDegrees = 0.0
            ),
            vehicle = VehiclePhysicsParams(
                vehicleId = vehicle?.id ?: "assumed_reference",
                usableBatteryKwh = usableKwh,
                totalMassKg = massKg,
                dragCoefficient = cd,
                frontalAreaM2 = frontalArea,
                rollingResistanceCoeff = crr,
                drivetrainEfficiency = vehicle?.drivetrainEfficiency ?: 0.88,
                regenEfficiency = vehicle?.regenEfficiency ?: 0.78,
                isAssumedFallback = isAssumed
            ),
            conditions = EnvironmentalConditions(
                departureSocPercent = 80.0,
                minReserveSocPercent = 15.0,
                targetSpeedKmh = speedKmh,
                ambientTempC = state.ambientTempC.toDouble(),
                hvacPowerKw = baseHvacKw
            )
        )

        val durationHours = (prediction.totalDurationSeconds / 3600.0).coerceAtLeast(1e-6)
        val aeroKw = (prediction.breakdown.aerodynamicDragWh / 1000.0) / durationHours
        val rollingKw = (prediction.breakdown.rollingResistanceWh / 1000.0) / durationHours
        val hvacKw = (prediction.breakdown.auxiliaryHvacWh / 1000.0) / durationHours
        val totalKw = (prediction.breakdown.netBatteryEnergyWh / 1000.0) / durationHours
        val whPerKm = prediction.breakdown.netBatteryEnergyWh

        _uiState.update {
            it.copy(
                aeroPowerKw = aeroKw,
                rollingPowerKw = rollingKw,
                hvacPowerKw = hvacKw,
                totalPowerKw = totalKw,
                consumptionWhPerKm = whPerKm,
                isAssumedData = isAssumed
            )
        }
    }
}
