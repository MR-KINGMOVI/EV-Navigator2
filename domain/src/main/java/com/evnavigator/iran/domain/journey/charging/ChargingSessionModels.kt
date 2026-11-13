package com.evnavigator.iran.domain.journey.charging

/**
 * Origin of a battery state observation.
 */
enum class BatteryObservationSource {
    MEASURED_OBD_TELEMETRY,
    USER_MANUAL_ENTRY,
    ESTIMATED_CONSUMPTION
}

/**
 * Explicit observation of battery State of Charge.
 */
data class BatteryObservation(
    val socPercent: Double,
    val source: BatteryObservationSource,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val confidence: Double = when (source) {
        BatteryObservationSource.MEASURED_OBD_TELEMETRY -> 0.98
        BatteryObservationSource.USER_MANUAL_ENTRY -> 0.90
        BatteryObservationSource.ESTIMATED_CONSUMPTION -> 0.75
    }
) {
    init {
        require(socPercent in 0.0..100.0) { "SOC must be between 0.0 and 100.0 (was $socPercent)." }
    }
}

/**
 * Status of an EV charging session.
 */
enum class ChargingSessionStatus {
    IDLE,
    PREPARING,
    CHARGING,
    PAUSED,
    COMPLETED,
    ABORTED
}

/**
 * Complete state of an active or recent charging session.
 */
data class ChargingSessionState(
    val stationId: String,
    val stationName: String,
    val status: ChargingSessionStatus,
    val startSocPercent: Double,
    val currentSocPercent: Double,
    val targetSocPercent: Double,
    val maxChargerPowerKw: Double,
    val effectivePowerKw: Double,
    val energyDeliveredKwh: Double,
    val elapsedDurationSeconds: Long,
    val estimatedRemainingMinutes: Double,
    val isTaperActive: Boolean,
    val isManuallyManaged: Boolean = true,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)
