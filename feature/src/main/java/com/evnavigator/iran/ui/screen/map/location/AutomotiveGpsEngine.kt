package com.evnavigator.iran.ui.screen.map.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location as AndroidLocation
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Location
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * High-performance Automotive GPS, Sensor-Fusion & Map-Matching Engine.
 * 
 * Features:
 * 1. 2D Metric Extended Kalman Filter (EKF) to eliminate GPS jitter and multipath noise.
 * 2. 9-DoF IMU Sensor Fusion for jitter-free car cursor rotation at any speed.
 * 3. Automotive Dead Reckoning (Tunnel Mode) during GPS outages using velocity vector extrapolation.
 * 4. Snap-To-Road Map Matching algorithm pinning vehicle position to active navigation route corridors.
 */
private const val TAG = "AutomotiveGpsEngine"

class AutomotiveGpsEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SensorEventListener {

    companion object {
        private const val EARTH_RADIUS_METERS = 6378137.0
        const val MAX_DEAD_RECKONING_DURATION_MS = 10_000L // Strict 10s cap before SIGNAL_LOST
        const val MAX_DEAD_RECKONING_DISTANCE_METERS = 350.0 // Strict 350m cap before SIGNAL_LOST
        private const val SNAP_TO_ROAD_CORRIDOR_METERS = 35.0 // Snap threshold
        private const val OUTAGE_DETECTION_THRESHOLD_MS = 2500L
        private const val LOW_SPEED_THRESHOLD_MPS = 1.5f // 5.4 km/h

        private val sharedLock = Any()
        private var sharedApplicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var sharedInstance: AutomotiveGpsEngine? = null
        private var activeClientCount: Int = 0

        /**
         * Returns the process-wide shared AutomotiveGpsEngine instance backed by an application-level
         * SupervisorJob scope (never tied to a single ViewModel's viewModelScope), incrementing the active
         * client reference count so MapNavigationViewModel and ActiveJourneyViewModel never register
         * duplicate hardware listeners or prematurely tear down the engine while another consumer is active.
         */
        fun acquireSharedEngine(context: Context): AutomotiveGpsEngine {
            synchronized(sharedLock) {
                if (!sharedApplicationScope.isActive) {
                    sharedApplicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                }
                val engine = sharedInstance ?: AutomotiveGpsEngine(
                    context = context.applicationContext ?: context,
                    scope = sharedApplicationScope
                ).also {
                    sharedInstance = it
                }
                activeClientCount += 1
                return engine
            }
        }

        /**
         * Overload accepting an optional callerScope parameter for call-site compatibility, while strictly
         * delegating to the application-scoped shared engine so individual ViewModel cancellation never kills
         * the shared engine while another consumer remains active.
         */
        fun acquireSharedEngine(
            context: Context,
            @Suppress("UNUSED_PARAMETER") callerScope: CoroutineScope?
        ): AutomotiveGpsEngine = acquireSharedEngine(context)

        /**
         * Decrements client reference count and stops the hardware listener only when no active consumers remain.
         */
        fun releaseSharedEngine() {
            synchronized(sharedLock) {
                if (activeClientCount > 0) {
                    activeClientCount -= 1
                }
                if (activeClientCount == 0) {
                    sharedInstance?.stopEngineInternal()
                }
            }
        }

        fun getActiveClientCountForTesting(): Int = synchronized(sharedLock) { activeClientCount }

        fun resetSharedEngineForTesting() {
            synchronized(sharedLock) {
                sharedInstance?.stopEngineInternal()
                sharedInstance?.clearActiveRoute()
                sharedInstance = null
                activeClientCount = 0
                sharedApplicationScope.coroutineContext.cancelChildren()
                CentralLocationRegistry.resetForTesting()
            }
        }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _locationState = MutableStateFlow<GpsLocationState>(CentralLocationRegistry.state.value)
    val locationState: StateFlow<GpsLocationState> = _locationState.asStateFlow()

    private fun emitSynchronizedState(newState: GpsLocationState, fixTimestampMs: Long? = null): Boolean {
        val accepted = CentralLocationRegistry.publish(newState, fixTimestampMs)
        if (accepted) {
            _locationState.value = newState
        }
        return accepted
    }

    // Active route points and segment metadata for Snap-to-Road matching
    @Volatile
    private var activeRoutePolyline: List<GeoCoordinate> = emptyList()
    @Volatile
    private var activeRouteSegments: List<com.evnavigator.iran.core.routing.model.RealRoadRouteSegment> = emptyList()
    @Volatile
    private var activeRouteId: String? = null
    @Volatile
    private var activeRouteGenerationId: Long = 0L
    @Volatile
    private var lastMatchedPolylineIndex: Int = -1

    // 2D Kalman Filter
    private var kalmanFilter: MetricKalmanFilter2D? = null

    // Sensor Fusion Heading state
    private var lastRawSensorHeading = 0f
    private var smoothedSensorHeading = 0f
    private var hasSensorData = false

    // State Tracking
    private var isEngineRunning = false
    private var lastGpsFixTimestamp = 0L
    private var lastRawLocationTimeMs = 0L
    private var accumulatedDeadReckoningMeters = 0.0
    private var lastEmittedSpeedKmh = 0f
    private var lastEmittedBearing = 0f
    private var lastEmittedLat = 0.0
    private var lastEmittedLng = 0.0
    private var lastEmittedAlt: Double? = null
    private var lastKnownRealAccuracyMeters: Float? = null

    private var deadReckoningJob: Job? = null

    // Android Location Listener
    private val rawLocationListener = object : LocationListener {
        override fun onLocationChanged(androidLoc: AndroidLocation) {
            processRawGpsFix(androidLoc)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {
            if (isEngineRunning && _locationState.value is GpsLocationState.GpsDisabled) {
                emitSynchronizedState(GpsLocationState.SearchingForFix)
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER && !isLocationHardwareEnabled()) {
                emitSynchronizedState(GpsLocationState.GpsDisabled)
            }
        }
    }

    /**
     * Set active route geometry and segment metadata to enable continuity-aware snap-to-road matching.
     * Ensures that a non-empty route always has a strictly positive `routeGenerationId > 0L`.
     */
    fun setActiveRoute(
        polyline: List<GeoCoordinate>,
        segments: List<com.evnavigator.iran.core.routing.model.RealRoadRouteSegment> = emptyList(),
        routeId: String? = null,
        routeGenerationId: Long = 1L,
        generationId: Long = routeGenerationId
    ) {
        val candidateGenId = if (generationId > 0L) generationId else routeGenerationId
        val resolvedGenId = if (candidateGenId > 0L) candidateGenId else maxOf(1L, this.activeRouteGenerationId + 1L)
        this.activeRoutePolyline = polyline
        this.activeRouteSegments = segments
        this.activeRouteId = routeId
        this.activeRouteGenerationId = resolvedGenId
        this.lastMatchedPolylineIndex = -1
    }

    fun getActiveRouteGenerationId(): Long = activeRouteGenerationId

    /**
     * Clear route when trip finishes or is cancelled.
     */
    fun clearActiveRoute() {
        this.activeRoutePolyline = emptyList()
        this.activeRouteSegments = emptyList()
        this.activeRouteId = null
        this.activeRouteGenerationId = 0L
        this.lastMatchedPolylineIndex = -1
    }

    fun emitGpsFixForTesting(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float? = 5f,
        speedKmh: Float = 60f,
        bearingDegrees: Float = 0f,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastRawLocationTimeMs > 0L && timestampEpochMs < lastRawLocationTimeMs) {
            CentralLocationRegistry.publish(_locationState.value, timestampEpochMs)
            return false
        }
        lastRawLocationTimeMs = timestampEpochMs
        lastGpsFixTimestamp = timestampEpochMs
        accumulatedDeadReckoningMeters = 0.0
        lastEmittedSpeedKmh = speedKmh
        lastEmittedBearing = bearingDegrees
        val speedMps = speedKmh / 3.6f
        val bearingConf = if (speedMps < LOW_SPEED_THRESHOLD_MPS) {
            if (hasSensorData) 0.7f else 0.25f
        } else 1.0f

        val loc = com.evnavigator.iran.domain.model.Location(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            speedMps = speedMps,
            bearingDegrees = bearingDegrees,
            timestampEpochMs = timestampEpochMs
        )
        if (activeRoutePolyline.size >= 2) {
            val snapped = snapToRoute(loc, activeRoutePolyline, bearingDegrees, bearingConf)
            if (snapped != null) {
                return emitSynchronizedState(snapped, fixTimestampMs = timestampEpochMs)
            }
        }
        return emitSynchronizedState(
            GpsLocationState.FixAcquired(
                location = loc,
                accuracyMeters = accuracyMeters ?: Float.NaN,
                altitudeM = null,
                speedKmh = speedKmh,
                bearingDegrees = bearingDegrees,
                isAccuracyKnown = accuracyMeters != null,
                bearingConfidence = bearingConf
            ),
            fixTimestampMs = timestampEpochMs
        )
    }

    fun hasPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationHardwareEnabled(): Boolean {
        val lm = locationManager ?: return false
        val gps = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { android.util.Log.w(TAG, "GPS check failed", e); false }
        val net = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { android.util.Log.w(TAG, "Network location check failed", e); false }
        return gps || net
    }

    /**
     * Start the complete automotive GPS and sensor fusion engine.
     */
    fun startEngine() {
        if (isEngineRunning) {
            _locationState.value = CentralLocationRegistry.state.value
            return
        }

        if (!hasPermissions()) {
            emitSynchronizedState(GpsLocationState.PermissionDenied())
            return
        }

        if (!isLocationHardwareEnabled()) {
            emitSynchronizedState(GpsLocationState.GpsDisabled)
            return
        }

        emitSynchronizedState(GpsLocationState.SearchingForFix)

        val lm = locationManager ?: return
        try {
            // Register sensors for heading fusion
            sensorManager?.let { sm ->
                val rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                if (rotSensor != null) {
                    sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_UI)
                } else {
                    val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                    val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    if (mag != null && acc != null) {
                        sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
                    }
                }
                Unit
            }

            // Inspect last known location
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = pickBestLocation(lastGps, lastNet)
            if (bestLast != null && isLocationPlausible(bestLast)) {
                val ageMs = System.currentTimeMillis() - bestLast.time
                if (ageMs in 0L..60_000L) { // Only accept fresh last-known fix (< 60s old)
                    processRawGpsFix(bestLast)
                }
            }

            // Request high-rate 1 Hz GPS updates
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    rawLocationListener,
                    Looper.getMainLooper()
                )
            }

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    3.0f,
                    rawLocationListener,
                    Looper.getMainLooper()
                )
            }

            isEngineRunning = true
            startDeadReckoningSupervisor()

        } catch (se: SecurityException) {
            emitSynchronizedState(GpsLocationState.PermissionDenied())
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to start GPS engine", e)
            emitSynchronizedState(GpsLocationState.GpsDisabled)
        }
    }

    /**
     * Stop the engine when not shared or when only a single consumer is active.
     * Does not decrement activeClientCount (lifecycle release is handled by releaseSharedEngine()).
     */
    fun stopEngine() {
        synchronized(sharedLock) {
            if (this === sharedInstance && activeClientCount > 1) {
                return
            }
            stopEngineInternal()
        }
    }

    private fun stopEngineInternal() {
        if (!isEngineRunning) return
        isEngineRunning = false
        deadReckoningJob?.cancel()
        deadReckoningJob = null

        try {
            locationManager?.removeUpdates(rawLocationListener)
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) { android.util.Log.w(TAG, "Failed to stop location updates", e) }

        kalmanFilter = null
        emitSynchronizedState(GpsLocationState.Idle)
    }

    // --- SENSOR LISTENER IMPLEMENTATION ---
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastAccValues: FloatArray? = null
    private var lastMagValues: FloatArray? = null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthDegrees = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f
                updateSensorHeading(azimuthDegrees)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccValues = event.values.clone()
                computeHeadingFromAccMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMagValues = event.values.clone()
                computeHeadingFromAccMag()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun computeHeadingFromAccMag() {
        val acc = lastAccValues ?: return
        val mag = lastMagValues ?: return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, acc, mag)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthDegrees = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f
            updateSensorHeading(azimuthDegrees)
        }
    }

    private fun updateSensorHeading(rawAzimuth: Float) {
        lastRawSensorHeading = rawAzimuth
        if (!hasSensorData) {
            smoothedSensorHeading = rawAzimuth
            hasSensorData = true
        } else {
            val diff = (rawAzimuth - smoothedSensorHeading + 540f) % 360f - 180f
            smoothedSensorHeading = (smoothedSensorHeading + diff * 0.18f + 360f) % 360f
        }
    }

    /**
     * Processes incoming raw GPS fixes from LocationManager, passing through out-of-order validation,
     * Kalman filter, low-speed bearing confidence evaluation, and Map Matcher.
     */
    private fun processRawGpsFix(rawLoc: AndroidLocation) {
        if (!isLocationPlausible(rawLoc)) return
        val fixTimeMs = if (rawLoc.time > 0L) rawLoc.time else System.currentTimeMillis()

        // 0. Strict out-of-order timestamp rejection
        if (lastRawLocationTimeMs > 0L && fixTimeMs < lastRawLocationTimeMs) {
            CentralLocationRegistry.publish(_locationState.value, fixTimeMs)
            return
        }
        lastRawLocationTimeMs = fixTimeMs
        val now = System.currentTimeMillis()
        lastGpsFixTimestamp = now
        accumulatedDeadReckoningMeters = 0.0

        // 1. Preserve genuine OS accuracy (null when unknown; never fabricate 12f/15f/25f as reported accuracy)
        val realAccuracyMeters: Float? = if (rawLoc.hasAccuracy() && rawLoc.accuracy > 0f) rawLoc.accuracy else null
        lastKnownRealAccuracyMeters = realAccuracyMeters
        val kalmanCovarianceInput = realAccuracyMeters ?: 25.0f

        val kf = kalmanFilter ?: MetricKalmanFilter2D(rawLoc.latitude, rawLoc.longitude).also {
            kalmanFilter = it
        }
        val filtered = kf.update(rawLoc.latitude, rawLoc.longitude, kalmanCovarianceInput, fixTimeMs)

        // 2. Compute Fused Speed (km/h and m/s)
        val rawSpeedMps = if (rawLoc.hasSpeed()) rawLoc.speed else null
        val fusedSpeedMps = max(0f, rawSpeedMps ?: kf.getSpeedMps())
        val fusedSpeedKmh = fusedSpeedMps * 3.6f
        lastEmittedSpeedKmh = fusedSpeedKmh

        // 3. Low-speed (< 1.5 m/s) bearing confidence & fusion
        val isLowSpeed = fusedSpeedMps < LOW_SPEED_THRESHOLD_MPS
        val rawGpsBearing = if (rawLoc.hasBearing() && !isLowSpeed) rawLoc.bearing else null
        val bearingConfidence = when {
            !isLowSpeed && rawGpsBearing != null -> 0.95f
            isLowSpeed && hasSensorData -> 0.70f
            hasSensorData -> 0.75f
            else -> 0.20f
        }
        val fusedBearing = when {
            rawGpsBearing != null && fusedSpeedKmh > 10f -> {
                if (hasSensorData) {
                    val diff = (smoothedSensorHeading - rawGpsBearing + 540f) % 360f - 180f
                    (rawGpsBearing + diff * 0.25f + 360f) % 360f
                } else {
                    rawGpsBearing
                }
            }
            hasSensorData -> smoothedSensorHeading // Low speed (< 1.5 m/s): IMU compass primary, ignore noisy GPS bearing
            rawGpsBearing != null -> rawGpsBearing
            else -> lastEmittedBearing
        }
        lastEmittedBearing = fusedBearing
        lastEmittedLat = filtered.latitude
        lastEmittedLng = filtered.longitude
        lastEmittedAlt = if (rawLoc.hasAltitude()) rawLoc.altitude else null

        val filteredLocation = Location(
            latitude = filtered.latitude,
            longitude = filtered.longitude,
            altitudeM = lastEmittedAlt,
            accuracyMeters = realAccuracyMeters,
            speedMps = fusedSpeedMps,
            bearingDegrees = fusedBearing,
            timestampEpochMs = fixTimeMs
        )

        // 4. Map Matching / Snap-to-Road
        if (activeRoutePolyline.isNotEmpty()) {
            val outcome = RouteCorridorSnapMatcher.snapToRoute(
                loc = filteredLocation,
                polyline = activeRoutePolyline,
                segments = activeRouteSegments,
                bearing = fusedBearing,
                bearingConfidence = bearingConfidence,
                currentSpeedKmh = lastEmittedSpeedKmh,
                prevPolylineIndex = lastMatchedPolylineIndex,
                snapCorridorMeters = SNAP_TO_ROAD_CORRIDOR_METERS,
                routeId = activeRouteId,
                routeGenerationId = activeRouteGenerationId
            )
            if (outcome != null) {
                lastMatchedPolylineIndex = outcome.matchedPolylineIndex
                emitSynchronizedState(outcome.matchedFix, fixTimeMs)
                return
            }
        }

        // Standard Kalman-filtered fix (accuracyMeters = Float.NaN if unknown by OS, isAccuracyKnown = realAccuracyMeters != null)
        emitSynchronizedState(
            GpsLocationState.FixAcquired(
                location = filteredLocation,
                accuracyMeters = realAccuracyMeters ?: Float.NaN,
                altitudeM = lastEmittedAlt,
                speedKmh = lastEmittedSpeedKmh,
                bearingDegrees = fusedBearing,
                isStale = (now - fixTimeMs) > 15_000L,
                provider = rawLoc.provider ?: "gps",
                isKalmanFiltered = true,
                isAccuracyKnown = realAccuracyMeters != null,
                bearingConfidence = bearingConfidence
            ),
            fixTimeMs
        )
    }

    /**
     * Background supervisor that detects tunnel entries / GPS blackouts and engages bounded Dead Reckoning.
     * Strictly terminates extrapolation once MAX_DEAD_RECKONING_DURATION_MS (10s) or
     * MAX_DEAD_RECKONING_DISTANCE_METERS (350m) is exceeded.
     */
    private fun startDeadReckoningSupervisor() {
        deadReckoningJob?.cancel()
        deadReckoningJob = scope.launch {
            while (isActive && isEngineRunning) {
                delay(200L) // 5 Hz Dead-Reckoning ticker
                val now = System.currentTimeMillis()
                val outageMs = now - lastGpsFixTimestamp

                if (lastGpsFixTimestamp > 0 &&
                    (outageMs >= MAX_DEAD_RECKONING_DURATION_MS ||
                        accumulatedDeadReckoningMeters >= MAX_DEAD_RECKONING_DISTANCE_METERS)
                ) {
                    if (_locationState.value !is GpsLocationState.SearchingForFix) {
                        emitSynchronizedState(GpsLocationState.SearchingForFix)
                    }
                } else if (lastGpsFixTimestamp > 0 && outageMs > OUTAGE_DETECTION_THRESHOLD_MS) {
                    if (lastEmittedSpeedKmh > 5.4f) { // > 1.5 m/s
                        val deltaSeconds = 0.2
                        val stepMeters = (lastEmittedSpeedKmh / 3.6) * deltaSeconds
                        accumulatedDeadReckoningMeters += stepMeters

                        if (accumulatedDeadReckoningMeters >= MAX_DEAD_RECKONING_DISTANCE_METERS) {
                            emitSynchronizedState(GpsLocationState.SearchingForFix)
                            continue
                        }

                        val radBearing = Math.toRadians(lastEmittedBearing.toDouble())
                        val dLat = (stepMeters * cos(radBearing)) / EARTH_RADIUS_METERS
                        val dLng = (stepMeters * sin(radBearing)) / (EARTH_RADIUS_METERS * cos(Math.toRadians(lastEmittedLat)))

                        lastEmittedLat += Math.toDegrees(dLat)
                        lastEmittedLng += Math.toDegrees(dLng)

                        val outageSeconds = outageMs / 1000L
                        val confidence = max(0.2f, 1.0f - (outageMs.toFloat() / MAX_DEAD_RECKONING_DURATION_MS.toFloat()))
                        val baseAcc = lastKnownRealAccuracyMeters ?: 20f
                        val uncertaintyMeters = baseAcc + (outageSeconds * 3.5f) + (accumulatedDeadReckoningMeters.toFloat() * 0.1f)

                        val deadReckonLoc = Location(
                            latitude = lastEmittedLat,
                            longitude = lastEmittedLng,
                            altitudeM = lastEmittedAlt,
                            accuracyMeters = uncertaintyMeters,
                            speedMps = lastEmittedSpeedKmh / 3.6f,
                            bearingDegrees = lastEmittedBearing,
                            timestampEpochMs = now
                        )

                        emitSynchronizedState(
                            GpsLocationState.DeadReckoning(
                                estimatedLocation = deadReckonLoc,
                                estimatedSpeedKmh = lastEmittedSpeedKmh,
                                estimatedBearing = lastEmittedBearing,
                                outageDurationSeconds = outageSeconds,
                                confidenceFactor = confidence,
                                deadReckoningDistanceMeters = accumulatedDeadReckoningMeters,
                                uncertaintyMeters = uncertaintyMeters
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isLocationPlausible(loc: AndroidLocation): Boolean {
        if (loc.latitude < -90.0 || loc.latitude > 90.0) return false
        if (loc.longitude < -180.0 || loc.longitude > 180.0) return false
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        if (loc.hasAccuracy() && loc.accuracy > 150f) return false
        return true
    }

    private fun pickBestLocation(a: AndroidLocation?, b: AndroidLocation?): AndroidLocation? {
        if (a == null) return b
        if (b == null) return a

        val nowElapsedNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val aAgeSec = (nowElapsedNanos - a.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000_000L
        val bAgeSec = (nowElapsedNanos - b.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000_000L

        val timeDeltaSec = aAgeSec - bAgeSec
        if (timeDeltaSec < -60) return a
        if (timeDeltaSec > 60) return b

        val aAcc = if (a.hasAccuracy()) a.accuracy else Float.MAX_VALUE
        val bAcc = if (b.hasAccuracy()) b.accuracy else Float.MAX_VALUE
        val accDelta = aAcc - bAcc

        if (accDelta < -15.0f) return a
        if (accDelta > 15.0f) return b

        val aIsGps = a.provider == LocationManager.GPS_PROVIDER
        val bIsGps = b.provider == LocationManager.GPS_PROVIDER
        if (aIsGps && !bIsGps) return a
        if (!aIsGps && bIsGps) return b

        return if (aAcc <= bAcc) a else b
    }
}
