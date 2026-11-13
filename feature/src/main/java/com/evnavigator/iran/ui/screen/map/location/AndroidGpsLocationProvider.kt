package com.evnavigator.iran.ui.screen.map.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.evnavigator.iran.domain.model.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Production Android LocationManager-based GPS provider.
 * Interacts directly with hardware GPS and Network location providers without proprietary GMS dependencies.
 * Fully lifecycle-aware with staleness monitoring and invalid fix suppression.
 */
private const val TAG = "AndroidGpsLocationProvider"

class AndroidGpsLocationProvider(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _locationState = MutableStateFlow<GpsLocationState>(GpsLocationState.Idle)
    val locationState: StateFlow<GpsLocationState> = _locationState.asStateFlow()

    private var isListening = false
    private var lastFixTimestamp = 0L
    private var stalenessJob: Job? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: AndroidLocation) {
            handleNewLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // Deprecated callback maintained for compatibility with API < 29
        }

        override fun onProviderEnabled(provider: String) {
            if (isListening && _locationState.value is GpsLocationState.GpsDisabled) {
                _locationState.value = GpsLocationState.SearchingForFix
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER && !isAnyLocationProviderEnabled()) {
                _locationState.value = GpsLocationState.GpsDisabled
            }
        }
    }

    /**
     * Checks whether location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        val finePerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarsePerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return finePerm || coarsePerm
    }

    /**
     * Checks whether device location hardware is enabled.
     */
    fun isAnyLocationProviderEnabled(): Boolean {
        val lm = locationManager ?: return false
        val gpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) { android.util.Log.w(TAG, "GPS check error", e); false }

        val networkEnabled = try {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) { android.util.Log.w(TAG, "Network provider check error", e); false }

        return gpsEnabled || networkEnabled
    }

    /**
     * Starts listening for live location updates.
     */
    fun startLocationUpdates() {
        if (isListening) return

        if (!hasLocationPermission()) {
            _locationState.value = GpsLocationState.PermissionDenied()
            return
        }

        val lm = locationManager
        if (lm == null || !isAnyLocationProviderEnabled()) {
            _locationState.value = GpsLocationState.GpsDisabled
            return
        }

        _locationState.value = GpsLocationState.SearchingForFix

        try {
            // First inspect last known location from either GPS or Network
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = pickBestLocation(lastGps, lastNetwork)
            if (bestLast != null && isLocationValid(bestLast)) {
                // If last known location is less than 5 minutes old, publish it immediately as starting point
                val ageMs = System.currentTimeMillis() - bestLast.time
                if (ageMs < 300_000L) {
                    emitFix(bestLast, isStale = ageMs > 30_000L)
                }
            }

            // Register for active updates (1 second min interval, 2 meters min distance)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    2.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    5.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            isListening = true
            startStalenessWatchdog()

        } catch (e: SecurityException) {
            _locationState.value = GpsLocationState.PermissionDenied()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to request location updates", e)
            _locationState.value = GpsLocationState.GpsDisabled
        }
    }

    /**
     * Stops listening for location updates and clears watchdog resources.
     */
    fun stopLocationUpdates() {
        if (!isListening) return
        isListening = false
        stalenessJob?.cancel()
        stalenessJob = null

        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to remove location updates", e)
        }
    }

    private var lastRawLocationTimeMs = 0L

    private fun handleNewLocation(androidLoc: AndroidLocation) {
        if (!isLocationValid(androidLoc)) return
        val fixTimeMs = if (androidLoc.time > 0L) androidLoc.time else System.currentTimeMillis()
        if (lastRawLocationTimeMs > 0L && fixTimeMs < lastRawLocationTimeMs) {
            CentralLocationRegistry.publish(_locationState.value, fixTimeMs)
            return
        }
        lastRawLocationTimeMs = fixTimeMs
        lastFixTimestamp = System.currentTimeMillis()
        emitFix(androidLoc, isStale = false)
    }

    private fun emitFix(androidLoc: AndroidLocation, isStale: Boolean) {
        val realAcc: Float? = if (androidLoc.hasAccuracy() && androidLoc.accuracy > 0f) androidLoc.accuracy else null
        val speedMps: Float? = if (androidLoc.hasSpeed()) androidLoc.speed else null
        val speedKmh: Float? = speedMps?.let { it * 3.6f }
        val isLowSpeed = (speedMps ?: 0f) < 1.5f
        val bearing: Float? = if (androidLoc.hasBearing() && !isLowSpeed) androidLoc.bearing else null
        val bearingConf = if (!isLowSpeed && bearing != null) 0.9f else 0.25f

        val domainLoc = Location(
            latitude = androidLoc.latitude,
            longitude = androidLoc.longitude,
            altitudeM = if (androidLoc.hasAltitude()) androidLoc.altitude else null,
            accuracyMeters = realAcc,
            speedMps = speedMps,
            bearingDegrees = bearing,
            timestampEpochMs = androidLoc.time
        )

        val newState = GpsLocationState.FixAcquired(
            location = domainLoc,
            accuracyMeters = realAcc ?: Float.NaN,
            altitudeM = domainLoc.altitudeM,
            speedKmh = speedKmh,
            bearingDegrees = bearing,
            isStale = isStale,
            provider = androidLoc.provider ?: "unknown",
            isAccuracyKnown = realAcc != null,
            bearingConfidence = bearingConf
        )
        if (CentralLocationRegistry.publish(newState, androidLoc.time)) {
            _locationState.value = newState
        }
    }

    private fun isLocationValid(loc: AndroidLocation): Boolean {
        // Enforce basic geographic sanity and accuracy bounds
        if (loc.latitude !in -90.0..90.0 || loc.longitude !in -180.0..180.0) return false
        // Exclude fixes with null island (0, 0) coordinates which often indicate uninitialized GPS chipsets
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        // Reject inaccurate fixes > 250 meters
        if (loc.hasAccuracy() && loc.accuracy > 250.0f) return false
        return true
    }

    private fun pickBestLocation(a: AndroidLocation?, b: AndroidLocation?): AndroidLocation? {
        if (a == null) return b
        if (b == null) return a

        // Monotonic elapsed realtime check immune to clock shifts
        val nowElapsedNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val aAgeSec = (nowElapsedNanos - a.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000_000L
        val bAgeSec = (nowElapsedNanos - b.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000_000L

        // If one fix is significantly newer (by > 60s), prefer it
        val timeDeltaSec = aAgeSec - bAgeSec
        if (timeDeltaSec < -60) return a
        if (timeDeltaSec > 60) return b

        // Accuracy comparison (smaller is better)
        val aAcc = if (a.hasAccuracy()) a.accuracy else Float.MAX_VALUE
        val bAcc = if (b.hasAccuracy()) b.accuracy else Float.MAX_VALUE
        val accDelta = aAcc - bAcc

        if (accDelta < -15.0f) return a
        if (accDelta > 15.0f) return b

        // Provider priority: GPS preferred over Network
        val aIsGps = a.provider == LocationManager.GPS_PROVIDER
        val bIsGps = b.provider == LocationManager.GPS_PROVIDER
        if (aIsGps && !bIsGps) return a
        if (!aIsGps && bIsGps) return b

        return if (aAcc <= bAcc) a else b
    }

    private fun startStalenessWatchdog() {
        stalenessJob?.cancel()
        stalenessJob = scope.launch {
            while (isActive && isListening) {
                delay(15_000L) // check every 15 seconds
                val current = _locationState.value
                if (current is GpsLocationState.FixAcquired && !current.isStale) {
                    val ageMs = System.currentTimeMillis() - lastFixTimestamp
                    if (ageMs > 25_000L) {
                        // Fix has gone stale (e.g. entering a long tunnel or parking structure)
                        _locationState.value = current.copy(isStale = true)
                    }
                }
            }
        }
    }
}
