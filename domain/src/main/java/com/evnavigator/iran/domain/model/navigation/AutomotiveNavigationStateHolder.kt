package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provenance source of the current vehicle State-of-Charge (SoC) reading.
 */
enum class SocDataSource {
    USER_INPUT,
    VEHICLE_PROFILE,
    BMS_HARDWARE,
    ENERGY_MODEL_ESTIMATE,
    UNKNOWN
}

/**
 * Request emitted from the Android Auto Head-Unit when a user selects a charging station
 * or destination so the primary navigation engine ([ActiveJourneyViewModel]) starts a
 * continuous GPS-tracked navigation session.
 */
data class AutomotiveDestinationRequest(
    val stationId: String,
    val title: String,
    val coordinate: GeoCoordinate
)

/**
 * Shared, thread-safe reactive bridge synchronizing active navigation state,
 * vehicle telemetry, and GPS status between the mobile navigation producer ([ActiveJourneyViewModel])
 * and the Android Auto / AAOS Head-Unit consumer ([AutomotiveSession]).
 *
 * Strictly enforces Rule Zero:
 * - Vehicle battery capacity and consumption remain `null` until an actual vehicle profile is loaded.
 * - Hardware BMS temperature remains `null` unless provided by an OBD-II / CAN sensor.
 * - Every state update records its timestamp and session ID to detect stale or terminated sessions.
 */
object AutomotiveNavigationStateHolder {

    private val _navigationState = MutableStateFlow<NavigationState?>(null)
    val navigationState: StateFlow<NavigationState?> = _navigationState.asStateFlow()

    private val _lastKnownLocation = MutableStateFlow<GeoCoordinate?>(null)
    val lastKnownLocation: StateFlow<GeoCoordinate?> = _lastKnownLocation.asStateFlow()

    private val _lastLocationTimestampMs = MutableStateFlow<Long?>(null)
    val lastLocationTimestampMs: StateFlow<Long?> = _lastLocationTimestampMs.asStateFlow()

    private val _lastLocationAccuracyMeters = MutableStateFlow<Float?>(null)
    val lastLocationAccuracyMeters: StateFlow<Float?> = _lastLocationAccuracyMeters.asStateFlow()

    private val _currentVehicleSocPercent = MutableStateFlow<Double?>(null)
    val currentVehicleSocPercent: StateFlow<Double?> = _currentVehicleSocPercent.asStateFlow()

    private val _socDataSource = MutableStateFlow(SocDataSource.UNKNOWN)
    val socDataSource: StateFlow<SocDataSource> = _socDataSource.asStateFlow()

    /**
     * Null when no vehicle profile has been selected or loaded from the database.
     * Never defaults to arbitrary 60.0 kWh.
     */
    private val _vehicleBatteryCapacityKwh = MutableStateFlow<Double?>(null)
    val vehicleBatteryCapacityKwh: StateFlow<Double?> = _vehicleBatteryCapacityKwh.asStateFlow()

    /**
     * Null when no vehicle profile has been selected or loaded from the database.
     * Never defaults to arbitrary 165.0 Wh/km.
     */
    private val _vehicleConsumptionWhPerKm = MutableStateFlow<Double?>(null)
    val vehicleConsumptionWhPerKm: StateFlow<Double?> = _vehicleConsumptionWhPerKm.asStateFlow()

    /**
     * Real BMS pack temperature in Celsius from hardware OBD-II / AAOS CarHardwareManager.
     * Null when no hardware sensor is connected (must never be faked as 25.0°C).
     */
    private val _bmsHardwareTemperatureCelsius = MutableStateFlow<Double?>(null)
    val bmsHardwareTemperatureCelsius: StateFlow<Double?> = _bmsHardwareTemperatureCelsius.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _lastUpdatedTimestampMs = MutableStateFlow<Long>(0L)
    val lastUpdatedTimestampMs: StateFlow<Long> = _lastUpdatedTimestampMs.asStateFlow()

    private val _destinationRequests = MutableSharedFlow<AutomotiveDestinationRequest>(extraBufferCapacity = 1)
    val destinationRequests: SharedFlow<AutomotiveDestinationRequest> = _destinationRequests.asSharedFlow()

    fun updateNavigationState(state: NavigationState?) {
        _navigationState.value = state
        _activeSessionId.value = state?.sessionId?.value
        _lastUpdatedTimestampMs.value = System.currentTimeMillis()
    }

    fun updateLastKnownLocation(
        coordinate: GeoCoordinate?,
        timestampEpochMs: Long = System.currentTimeMillis(),
        accuracyMeters: Float? = null
    ) {
        _lastKnownLocation.value = coordinate
        _lastLocationTimestampMs.value = if (coordinate != null) timestampEpochMs else null
        _lastLocationAccuracyMeters.value = if (coordinate != null) accuracyMeters else null
    }

    fun updateVehicleTelemetry(
        socPercent: Double?,
        batteryCapacityKwh: Double?,
        consumptionWhPerKm: Double?,
        socSource: SocDataSource = SocDataSource.VEHICLE_PROFILE
    ) {
        _currentVehicleSocPercent.value = socPercent?.coerceIn(0.0, 100.0)
        _socDataSource.value = if (socPercent != null) socSource else SocDataSource.UNKNOWN
        _vehicleBatteryCapacityKwh.value = batteryCapacityKwh?.takeIf { it > 0.0 }
        _vehicleConsumptionWhPerKm.value = consumptionWhPerKm?.takeIf { it > 0.0 }
    }

    fun updateBmsHardwareTemperature(temperatureCelsius: Double?) {
        _bmsHardwareTemperatureCelsius.value = temperatureCelsius
    }

    fun requestDestinationNavigation(request: AutomotiveDestinationRequest) {
        _destinationRequests.tryEmit(request)
    }

    fun isStateStale(maxAgeMs: Long = 30_000L, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val lastUpdate = _lastUpdatedTimestampMs.value
        if (lastUpdate <= 0L) return true
        return (nowEpochMs - lastUpdate) > maxAgeMs
    }

    fun clearSession(clearVehicleTelemetry: Boolean = false) {
        _navigationState.value = null
        _activeSessionId.value = null
        _lastKnownLocation.value = null
        _lastLocationTimestampMs.value = null
        _lastLocationAccuracyMeters.value = null
        if (clearVehicleTelemetry) {
            _currentVehicleSocPercent.value = null
            _socDataSource.value = SocDataSource.UNKNOWN
            _vehicleBatteryCapacityKwh.value = null
            _vehicleConsumptionWhPerKm.value = null
            _bmsHardwareTemperatureCelsius.value = null
        }
        _lastUpdatedTimestampMs.value = 0L
    }
}
