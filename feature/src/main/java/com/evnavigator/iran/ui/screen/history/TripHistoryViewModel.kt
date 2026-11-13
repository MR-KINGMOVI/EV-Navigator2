package com.evnavigator.iran.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evnavigator.iran.domain.model.catalog.DomainTripHistorySummary
import com.evnavigator.iran.domain.repository.TripHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

data class TariffSchedule(
    val rateIrrPerKwh: Long = 35_000L,
    val currency: String = "IRR",
    val source: String = "TAVANIR_PUBLIC_DC_FAST_CHARGE_SCHEDULE",
    val region: String = "IR-NATIONAL",
    val effectiveFromEpochMs: Long = 1710979200000L, // 2024-03-21 (1403/01/01)
    val effectiveToEpochMs: Long? = null,
    val isEstimated: Boolean = true,
    val displayLabelFa: String = "تعرفه مرجع توانیر (ایستگاه شارژ سریع عمومی - ۳۵,۰۰۰ ریال/kWh - تخمینی)"
)

data class TripHistoryUiState(
    val trips: List<DomainTripHistorySummary> = emptyList(),
    val verifiedTripsCount: Int = 0,
    val demoTripsCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalEnergyKwh: Double = 0.0,
    val averageWhPerKm: Double = 0.0,
    val estimatedChargingCostIrr: Long = 0L,
    val tariffSchedule: TariffSchedule = TariffSchedule(),
    val tariffIrrPerKwh: Long = tariffSchedule.rateIrrPerKwh,
    val currencyUnit: String = tariffSchedule.currency,
    val tariffSourceLabel: String = tariffSchedule.displayLabelFa,
    val isCostEstimatedFromTariff: Boolean = tariffSchedule.isEstimated,
    val isLoading: Boolean = true,
    val statusMessage: String? = null
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val tripHistoryRepository: TripHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripHistoryUiState())
    val uiState: StateFlow<TripHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    /**
     * Loads recorded trips from repository without auto-seeding synthetic demo records.
     * Demo trips (isVerifiedTelemetry == false) are strictly excluded from aggregated analytics.
     */
    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val existing = try {
                tripHistoryRepository.getAllSummaries()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("TripHistoryViewModel", "Failed to load history summaries", e)
                emptyList()
            }
            computeAndSetState(existing)
        }
    }

    /**
     * Explicit opt-in action for loading sample demonstration trips (marked isVerifiedTelemetry = false).
     * Never invoked automatically on startup, and excluded from real telemetry averages.
     */
    fun loadSampleDemoTripsExplicitly() {
        viewModelScope.launch {
            val sampleTrips = createSampleDemoTrips()
            for (t in sampleTrips) {
                try {
                    tripHistoryRepository.insertSummary(t)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.w("TripHistoryViewModel", "Failed to insert demo summary: ${t.tripId}", e)
                }
            }
            val reloaded = try {
                tripHistoryRepository.getAllSummaries()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
            computeAndSetState(
                trips = reloaded,
                customMessage = "سفرهای نمونه (Demo) صرفاً جهت نمایش بارگذاری شدند و در میانگین تله‌متری واقعی لحاظ نمی‌شوند."
            )
        }
    }

    private fun computeAndSetState(
        trips: List<DomainTripHistorySummary>,
        customMessage: String? = null
    ) {
        // Strict isolation: only verified telemetry trips contribute to real historical consumption metrics
        val verifiedTrips = trips.filter { it.isVerifiedTelemetry }
        val demoCount = trips.count { !it.isVerifiedTelemetry }

        val totalDistanceKm = verifiedTrips.sumOf { it.distanceMeters } / 1000.0
        val totalEnergyKwh = verifiedTrips.sumOf { it.measuredEnergyWh } / 1000.0
        val avgWhKm = if (totalDistanceKm > 0.0) {
            verifiedTrips.sumOf { it.measuredEnergyWh } / totalDistanceKm
        } else {
            0.0
        }
        val tariffIrr = _uiState.value.tariffIrrPerKwh
        val estimatedCostIrr = (totalEnergyKwh * tariffIrr).roundToLong().coerceAtLeast(0L)

        val resolvedStatus = customMessage ?: when {
            trips.isEmpty() -> "هیچ سفر ثبت‌شده‌ای وجود ندارد."
            verifiedTrips.isEmpty() && demoCount > 0 -> "فقط سفرهای نمایشی (Demo) موجود است؛ آمار تله‌متری واقعی صفر است."
            else -> null
        }

        _uiState.update {
            it.copy(
                trips = trips,
                verifiedTripsCount = verifiedTrips.size,
                demoTripsCount = demoCount,
                totalDistanceKm = totalDistanceKm,
                totalEnergyKwh = totalEnergyKwh,
                averageWhPerKm = avgWhKm,
                estimatedChargingCostIrr = estimatedCostIrr,
                isLoading = false,
                statusMessage = resolvedStatus
            )
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                tripHistoryRepository.clearAll()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _uiState.update {
                it.copy(
                    trips = emptyList(),
                    verifiedTripsCount = 0,
                    demoTripsCount = 0,
                    totalDistanceKm = 0.0,
                    totalEnergyKwh = 0.0,
                    averageWhPerKm = 0.0,
                    estimatedChargingCostIrr = 0L,
                    statusMessage = "تمامی سوابق با موفقیت پاکسازی شدند."
                )
            }
        }
    }

    private fun createSampleDemoTrips(): List<DomainTripHistorySummary> {
        val now = System.currentTimeMillis()
        val oneDay = 86_400_000L
        return listOf(
            DomainTripHistorySummary(
                tripId = "demo_trip_tehran_isfahan_01",
                vehicleId = "vw_id4_crozz_2024",
                startEpochMs = now - (oneDay * 2),
                endEpochMs = now - (oneDay * 2) + 18_000_000L,
                distanceMeters = 448_000.0,
                predictedEnergyWh = 78_000.0,
                measuredEnergyWh = 76_500.0,
                averageSpeedKmh = 92.0,
                ambientTempCelsius = 24.0,
                isVerifiedTelemetry = false,
                modelCalibrationVersion = 1
            ),
            DomainTripHistorySummary(
                tripId = "demo_trip_tehran_rasht_02",
                vehicleId = "vw_id4_crozz_2024",
                startEpochMs = now - (oneDay * 5),
                endEpochMs = now - (oneDay * 5) + 14_000_000L,
                distanceMeters = 325_000.0,
                predictedEnergyWh = 55_000.0,
                measuredEnergyWh = 53_800.0,
                averageSpeedKmh = 88.0,
                ambientTempCelsius = 19.0,
                isVerifiedTelemetry = false,
                modelCalibrationVersion = 1
            )
        )
    }
}
