package com.evnavigator.iran.ui.screen.map.location

import com.evnavigator.iran.domain.model.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central process-wide authoritative GPS & sensor-fusion location state registry.
 * Ensures MapNavigationViewModel, ActiveJourneyViewModel, AndroidGpsLocationProvider,
 * and Android Auto observe the exact same single source of truth without duplicate listeners.
 */
object CentralLocationRegistry {
    private val lock = Any()
    private val _state = MutableStateFlow<GpsLocationState>(GpsLocationState.Idle)
    val state: StateFlow<GpsLocationState> = _state.asStateFlow()

    @Volatile
    var lastAcceptedLocationTimestampMs: Long = 0L
        private set

    @Volatile
    var rejectedOutOfOrderFixCount: Long = 0L
        private set

    fun publish(newState: GpsLocationState, fixTimestampMs: Long? = null): Boolean {
        synchronized(lock) {
            if (fixTimestampMs != null && fixTimestampMs > 0L && lastAcceptedLocationTimestampMs > 0L) {
                if (fixTimestampMs < lastAcceptedLocationTimestampMs) {
                    rejectedOutOfOrderFixCount += 1L
                    return false
                }
            }
            if (fixTimestampMs != null && fixTimestampMs > 0L) {
                lastAcceptedLocationTimestampMs = fixTimestampMs
            }
            _state.value = newState
            return true
        }
    }

    fun resetForTesting() {
        synchronized(lock) {
            lastAcceptedLocationTimestampMs = 0L
            rejectedOutOfOrderFixCount = 0L
            _state.value = GpsLocationState.Idle
        }
    }
}

/**
 * Automotive-grade representation of GPS, motion sensor fusion, and map-matching states.
 * Guarantees zero fabricated data and provides real-time confidence metrics for automotive navigation.
 */
sealed class GpsLocationState {
    /**
     * Initial idle state before GPS tracking has been requested.
     */
    data object Idle : GpsLocationState()

    /**
     * Location permission has not been granted.
     * @param permanentlyDenied true if user checked "Don't ask again" or system denied permanently.
     */
    data class PermissionDenied(val permanentlyDenied: Boolean = false) : GpsLocationState()

    /**
     * Device location services (GPS hardware switch) are turned off in system settings.
     */
    data object GpsDisabled : GpsLocationState()

    /**
     * Permission granted and GPS receiver active, currently searching for satellite fix (or signal lost after DR cap).
     */
    data object SearchingForFix : GpsLocationState()

    /**
     * A genuine GPS or Network location fix is actively available with raw/filtered telemetry.
     */
    data class FixAcquired(
        val location: Location,
        val accuracyMeters: Float,
        val altitudeM: Double?,
        val speedKmh: Float?,
        val bearingDegrees: Float?,
        val isStale: Boolean = false,
        val provider: String = "gps",
        val satellitesInView: Int? = null,
        val isKalmanFiltered: Boolean = false,
        val isAccuracyKnown: Boolean = location.accuracyMeters != null,
        val bearingConfidence: Float = 1.0f
    ) : GpsLocationState()

    /**
     * Dead-Reckoning active in tunnels, underpasses, or urban canyons with lost satellite signals.
     * Strictly bounded by duration (max 10s) and distance (max 350m) with growing uncertainty.
     */
    data class DeadReckoning(
        val estimatedLocation: Location,
        val estimatedSpeedKmh: Float,
        val estimatedBearing: Float,
        val outageDurationSeconds: Long,
        val confidenceFactor: Float, // 1.0 (fresh) down to 0.2 (degraded)
        val deadReckoningDistanceMeters: Double = 0.0,
        val uncertaintyMeters: Float = 30.0f
    ) : GpsLocationState()

    /**
     * High-precision map-matched fix snapped onto the verified road centerline.
     */
    data class MapMatchedFix(
        val snappedLocation: Location,
        val rawLocation: Location,
        val crossTrackOffsetMeters: Double,
        val speedKmh: Float,
        val bearingDegrees: Float,
        val matchedRoadName: String?,
        val speedLimitKmh: Int? = null,
        val isSnapped: Boolean = true,
        val bearingConfidence: Float = 1.0f,
        val segmentIndex: Int = 0,
        val distanceAlongSegmentMeters: Double = 0.0,
        val distanceAlongRouteMeters: Double = 0.0,
        val routeId: String? = null,
        val routeGenerationId: Long = 0L,
        val confidence: Float = 1.0f
    ) : GpsLocationState()
}
