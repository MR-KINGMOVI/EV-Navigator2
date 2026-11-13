package com.evnavigator.iran.domain.journey.charging

import com.evnavigator.iran.domain.intelligence.charging.ChargingSessionOptimizer
import com.evnavigator.iran.domain.intelligence.charging.VehicleChargingProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Thread-safe manager for EV charging sessions.
 * Accurately models energy delivery, vehicle acceptance power limits, and Li-ion taper above 80%.
 */
class LiveChargingSessionManager(
    private val optimizer: ChargingSessionOptimizer = ChargingSessionOptimizer()
) {
    private val _sessionState = MutableStateFlow<ChargingSessionState?>(null)
    val sessionState: StateFlow<ChargingSessionState?> = _sessionState.asStateFlow()

    fun startSession(
        stationId: String,
        stationName: String,
        currentSocPercent: Double,
        targetSocPercent: Double = 80.0,
        chargerPowerLimitKw: Double = 60.0,
        vehicleCapacityKwh: Double = 72.0,
        maxVehicleAcceptanceKw: Double = 120.0,
        isManual: Boolean = true
    ): ChargingSessionState {
        val target = max(targetSocPercent, currentSocPercent).coerceIn(0.0, 100.0)
        val profile = VehicleChargingProfile(
            vehicleId = "active_ev",
            batteryCapacityKwh = vehicleCapacityKwh,
            maxDCPowerKw = maxVehicleAcceptanceKw
        )

        val opt = optimizer.optimizeSession(
            profile = profile,
            chargerPowerLimitKw = chargerPowerLimitKw,
            startSocPercent = currentSocPercent,
            targetSocPercent = target
        )

        val initial = ChargingSessionState(
            stationId = stationId,
            stationName = stationName,
            status = ChargingSessionStatus.CHARGING,
            startSocPercent = currentSocPercent,
            currentSocPercent = currentSocPercent,
            targetSocPercent = target,
            maxChargerPowerKw = chargerPowerLimitKw,
            effectivePowerKw = opt.effectiveAveragePowerKw,
            energyDeliveredKwh = 0.0,
            elapsedDurationSeconds = 0L,
            estimatedRemainingMinutes = opt.estimatedDurationMinutes,
            isTaperActive = opt.isTaperingActive,
            isManuallyManaged = isManual
        )
        _sessionState.value = initial
        return initial
    }

    fun updateProgress(
        observedSocPercent: Double,
        batteryCapacityKwh: Double = 72.0,
        maxVehicleAcceptanceKw: Double = 120.0
    ): ChargingSessionState? {
        val current = _sessionState.value ?: return null
        if (current.status != ChargingSessionStatus.CHARGING) return current

        val newSoc = observedSocPercent.coerceIn(current.startSocPercent, 100.0)
        val energyAdded = ((newSoc - current.startSocPercent) / 100.0) * batteryCapacityKwh

        val profile = VehicleChargingProfile(
            vehicleId = "active_ev",
            batteryCapacityKwh = batteryCapacityKwh,
            maxDCPowerKw = maxVehicleAcceptanceKw
        )

        val remainingOpt = if (newSoc < current.targetSocPercent) {
            optimizer.optimizeSession(
                profile = profile,
                chargerPowerLimitKw = current.maxChargerPowerKw,
                startSocPercent = newSoc,
                targetSocPercent = current.targetSocPercent
            )
        } else null

        val isTargetReached = newSoc >= current.targetSocPercent

        val updated = current.copy(
            status = if (isTargetReached) ChargingSessionStatus.COMPLETED else ChargingSessionStatus.CHARGING,
            currentSocPercent = newSoc,
            effectivePowerKw = remainingOpt?.effectiveAveragePowerKw ?: 0.0,
            energyDeliveredKwh = energyAdded,
            elapsedDurationSeconds = current.elapsedDurationSeconds + 60L,
            estimatedRemainingMinutes = remainingOpt?.estimatedDurationMinutes ?: 0.0,
            isTaperActive = newSoc >= 80.0,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )

        _sessionState.value = updated
        return updated
    }

    fun pauseSession(): ChargingSessionState? {
        val current = _sessionState.value ?: return null
        val paused = current.copy(status = ChargingSessionStatus.PAUSED)
        _sessionState.value = paused
        return paused
    }

    fun resumeSession(): ChargingSessionState? {
        val current = _sessionState.value ?: return null
        val resumed = current.copy(status = ChargingSessionStatus.CHARGING)
        _sessionState.value = resumed
        return resumed
    }

    fun finishSession(): ChargingSessionState? {
        val current = _sessionState.value ?: return null
        val finished = current.copy(status = ChargingSessionStatus.COMPLETED)
        _sessionState.value = finished
        return finished
    }

    fun abortSession(): ChargingSessionState? {
        val current = _sessionState.value ?: return null
        val aborted = current.copy(status = ChargingSessionStatus.ABORTED)
        _sessionState.value = aborted
        return aborted
    }
}
