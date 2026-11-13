package com.evnavigator.iran.ui.screen.journey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.evnavigator.iran.core.routing.engine.TrafficRadarEngine
import com.evnavigator.iran.designsystem.locale.appStrings
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.ui.screen.map.components.AutomotiveMapPuck
import com.evnavigator.iran.ui.screen.map.components.ChargerPinFactory
import com.evnavigator.iran.ui.screen.map.components.CockpitMapTileSource
import com.evnavigator.iran.ui.screen.map.components.IranOfflineVectorOverlay
import com.evnavigator.iran.ui.screen.map.components.StationIntelligenceSheet
import com.evnavigator.iran.ui.screen.map.components.TrueDarkTileFilter
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.roundToInt

/**
 * Production-Grade Dark Premium Automotive Navigation Screen for EV Navigator Iran (V11 Decomposed).
 *
 * Visual & Functional Architecture:
 * - Osmdroid map canvas with vehicle puck, laser cyan route polyline, and station markers
 * - Delegated `ActiveJourneyTopHudPanel` & `ActiveJourneyFloatingMapControls`
 * - Delegated `ActiveJourneyModeBottomPanels` covering all 10 explicit navigation modes
 */
@Composable
fun ActiveJourneyScreen(
    viewModel: ActiveJourneyViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val gpsState by viewModel.gpsState.collectAsState()
    val strings = appStrings()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLayerSelector by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }
    var currentTileSource by remember { mutableStateOf<ITileSource>(CockpitMapTileSource.DarkMatter) }

    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val glassBorderBrush = if (isDark) {
        Brush.linearGradient(colors = listOf(Color(0x6600E5FF), Color(0x1A00E5FF)))
    } else {
        Brush.linearGradient(colors = listOf(Color(0x660091EA), Color(0x33CBD5E1)))
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) viewModel.startGps()
    }

    LaunchedEffect(Unit) {
        val osmDir = File(context.noBackupFilesDir, "osmdroid")
        val tileCacheDir = File(osmDir, "tiles")
        osmDir.mkdirs()
        tileCacheDir.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmDir
        Configuration.getInstance().osmdroidTileCache = tileCacheDir
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Linux; Android) EVNavigatorIran/11.0"
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_config", Context.MODE_PRIVATE))

        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            viewModel.startGps()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.recenterEvent.collect { target ->
            mapViewRef?.controller?.animateTo(GeoPoint(target.latitude, target.longitude), 16.0, 800L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapViewRef?.onResume()
                    if (hasPermission) viewModel.startGps()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapViewRef?.onPause()
                    viewModel.stopGps()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ColorTokens.CockpitBackground)) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(currentTileSource)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true

                    val startPoint = uiState.plan?.waypoints?.firstOrNull()?.coordinate
                    if (startPoint != null) {
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(startPoint.latitude, startPoint.longitude))
                    } else {
                        controller.setZoom(5.8)
                        controller.setCenter(GeoPoint(32.4279, 53.6880))
                    }
                    mapViewRef = this
                }
            },
            update = { mapView ->
                if (mapView.tileProvider.tileSource != currentTileSource) {
                    mapView.setTileSource(currentTileSource)
                }
                mapView.overlayManager.tilesOverlay.setColorFilter(
                    TrueDarkTileFilter.createForSource(currentTileSource, isDark)
                )

                mapView.overlays.clear()
                mapView.overlays.add(IranOfflineVectorOverlay())

                val plan = uiState.plan
                if (plan != null && (plan.waypoints.size >= 2 || uiState.roadPolyline.isNotEmpty())) {
                    val polylinePoints = if (uiState.roadPolyline.isNotEmpty()) {
                        uiState.roadPolyline.map { GeoPoint(it.latitude, it.longitude) }
                    } else {
                        plan.waypoints.map { GeoPoint(it.coordinate.latitude, it.coordinate.longitude) }
                    }

                    val glowPolyline = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#4D00E5FF")
                        outlinePaint.strokeWidth = 24f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        setPoints(polylinePoints)
                    }
                    mapView.overlays.add(glowPolyline)

                    val corePolyline = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        setPoints(polylinePoints)
                    }
                    mapView.overlays.add(corePolyline)

                    plan.waypoints.forEachIndexed { index, wp ->
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(wp.coordinate.latitude, wp.coordinate.longitude)
                            title = wp.name
                            if (wp.isChargingStop) {
                                val kw = wp.stationDetails?.maxPowerKw?.takeIf { it > 0.0 }
                                icon = ChargerPinFactory.createStationPin(context, kw ?: 0.0, isSelected = false)
                                snippet = if (kw != null) {
                                    "ایستگاه شارژ سریع: ${kw.toInt()} kW"
                                } else {
                                    "ایستگاه شارژ (توان نامشخص)"
                                }
                                setAnchor(Marker.ANCHOR_CENTER, 0.90f)
                            } else {
                                snippet = if (index == 0) "مبدأ سفر" else "مقصد نهایی"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                        }
                        mapView.overlays.add(marker)
                    }
                }

                if (uiState.navigationMode == NavigationScreenMode.IDLE_FREE_DRIVE || uiState.navigationMode == NavigationScreenMode.DESTINATION_SEARCH) {
                    uiState.chargingStations.take(25).forEach { stationWithPorts ->
                        val maxKw = stationWithPorts.ports.mapNotNull { it.powerKw?.takeIf { p -> p > 0.0 } }.maxOrNull()
                        val isSelected = uiState.selectedStation?.station?.id == stationWithPorts.station.id
                        val city = stationWithPorts.station.city ?: "ایران"
                        val operator = stationWithPorts.station.operatorName ?: "اپراتور ثبت‌نشده"
                        val powerLabel = maxKw?.let { "${it.toInt()} kW" } ?: "توان نامشخص"
                        val stationMarker = Marker(mapView).apply {
                            position = GeoPoint(stationWithPorts.station.latitude, stationWithPorts.station.longitude)
                            title = stationWithPorts.station.canonicalName
                            snippet = "$city • $powerLabel • $operator"
                            icon = ChargerPinFactory.createStationPin(context, maxKw ?: 0.0, isSelected)
                            setAnchor(Marker.ANCHOR_CENTER, 0.90f)
                            setOnMarkerClickListener { _, _ ->
                                viewModel.selectStation(stationWithPorts)
                                true
                            }
                        }
                        mapView.overlays.add(stationMarker)
                    }
                }

                val currentGps = gpsState
                val userPoint: GeoPoint?
                val accuracyMeters: Float
                val bearing: Float?
                val isDeadReckoning: Boolean
                val vehicleMarkerTitle: String
                val vehicleMarkerSnippet: String
                val vehicleSpeedKmh: Double

                if (uiState.isSimulatingDrive && uiState.simulatedCarCoordinate != null) {
                    val simCoord = uiState.simulatedCarCoordinate!!
                    userPoint = GeoPoint(simCoord.latitude, simCoord.longitude)
                    accuracyMeters = 0f
                    bearing = uiState.simulatedCarBearing
                    isDeadReckoning = false
                    vehicleSpeedKmh = uiState.simulatedSpeedKmh
                    vehicleMarkerTitle = "${uiState.vehicleModelName} (شبیه‌سازی زنده)"
                    vehicleMarkerSnippet = "سرعت: ${vehicleSpeedKmh.roundToInt()} km/h • ناوبری خودکار"
                } else when (currentGps) {
                    is GpsLocationState.MapMatchedFix -> {
                        userPoint = GeoPoint(currentGps.snappedLocation.latitude, currentGps.snappedLocation.longitude)
                        accuracyMeters = currentGps.snappedLocation.accuracyMeters ?: 5f
                        bearing = currentGps.bearingDegrees
                        isDeadReckoning = false
                        vehicleSpeedKmh = currentGps.speedKmh.toDouble()
                        vehicleMarkerTitle = "${uiState.vehicleModelName} (قفل جاده)"
                        vehicleMarkerSnippet = "${currentGps.matchedRoadName ?: "مسیر"} • سرعت: ${currentGps.speedKmh.toInt()} km/h"
                    }
                    is GpsLocationState.FixAcquired -> {
                        userPoint = GeoPoint(currentGps.location.latitude, currentGps.location.longitude)
                        accuracyMeters = currentGps.accuracyMeters
                        bearing = currentGps.bearingDegrees
                        isDeadReckoning = false
                        val speedVal = currentGps.speedKmh?.toDouble() ?: 0.0
                        vehicleSpeedKmh = speedVal
                        vehicleMarkerTitle = uiState.vehicleModelName
                        val filterTag = if (currentGps.isKalmanFiltered) " • فیلتر کالمان" else ""
                        vehicleMarkerSnippet = "سرعت: ${speedVal.roundToInt()} km/h$filterTag"
                    }
                    is GpsLocationState.DeadReckoning -> {
                        userPoint = GeoPoint(currentGps.estimatedLocation.latitude, currentGps.estimatedLocation.longitude)
                        accuracyMeters = (20f + (currentGps.outageDurationSeconds * 2.5f)).coerceAtMost(100f)
                        bearing = currentGps.estimatedBearing
                        isDeadReckoning = true
                        vehicleSpeedKmh = currentGps.estimatedSpeedKmh.toDouble()
                        vehicleMarkerTitle = "${uiState.vehicleModelName} (ناوبری تخمینی اینرسیال)"
                        vehicleMarkerSnippet = "تونل • مدت قطعی: ${currentGps.outageDurationSeconds} ثانیه"
                    }
                    else -> {
                        val firstCoord = uiState.roadPolyline.firstOrNull() ?: uiState.plan?.waypoints?.firstOrNull()?.coordinate
                        if (firstCoord != null) {
                            userPoint = GeoPoint(firstCoord.latitude, firstCoord.longitude)
                            accuracyMeters = 0f
                            bearing = 0f
                            isDeadReckoning = false
                            vehicleSpeedKmh = 0.0
                            vehicleMarkerTitle = uiState.vehicleModelName
                            vehicleMarkerSnippet = "موقعیت آغاز مسیر"
                        } else {
                            userPoint = null
                            accuracyMeters = 0f
                            bearing = 0f
                            isDeadReckoning = false
                            vehicleSpeedKmh = 0.0
                            vehicleMarkerTitle = uiState.vehicleModelName
                            vehicleMarkerSnippet = ""
                        }
                    }
                }

                if (userPoint != null) {
                    if (uiState.isSimulatingDrive) {
                        mapView.controller.animateTo(userPoint)
                        if (bearing != null) mapView.mapOrientation = -bearing
                    } else if (uiState.isHeadingUpCamera && bearing != null) {
                        mapView.mapOrientation = -bearing
                    } else {
                        mapView.mapOrientation = 0f
                    }

                    if (accuracyMeters > 0) {
                        val circlePoints = Polygon.pointsAsCircle(userPoint, accuracyMeters.toDouble())
                        val accuracyPolygon = Polygon(mapView).apply {
                            points = circlePoints
                            val fillColor = if (isDeadReckoning) "#26FF9800" else "#2600E5FF"
                            val strokeColor = if (isDeadReckoning) "#99FF9800" else "#8000E5FF"
                            fillPaint.color = android.graphics.Color.parseColor(fillColor)
                            outlinePaint.color = android.graphics.Color.parseColor(strokeColor)
                            outlinePaint.strokeWidth = 2f
                        }
                        mapView.overlays.add(accuracyPolygon)
                    }

                    val puckDrawable = AutomotiveMapPuck.createVehiclePuck(context, isDeadReckoning, vehicleSpeedKmh)
                    val vehicleMarker = Marker(mapView).apply {
                        position = userPoint
                        icon = puckDrawable
                        title = vehicleMarkerTitle
                        snippet = vehicleMarkerSnippet
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        if (bearing != null) rotation = bearing
                    }
                    mapView.overlays.add(vehicleMarker)
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        ActiveJourneyFloatingMapControls(
            uiState = uiState,
            glassBorderBrush = glassBorderBrush,
            showLayerSelector = showLayerSelector,
            onToggleLayerSelector = { showLayerSelector = !showLayerSelector },
            currentTileSource = currentTileSource,
            onSelectTileSource = { source ->
                currentTileSource = source
                showLayerSelector = false
            },
            onToggleAudio = { viewModel.toggleAudio() },
            onZoomIn = {
                val currentZoom = mapViewRef?.zoomLevelDouble ?: 15.0
                mapViewRef?.controller?.setZoom(currentZoom + 1.0)
            },
            onZoomOut = {
                val currentZoom = mapViewRef?.zoomLevelDouble ?: 15.0
                mapViewRef?.controller?.setZoom(currentZoom - 1.0)
            },
            onToggleCameraPerspective = { viewModel.toggleCameraPerspective() },
            onRecenterOnVehicle = { viewModel.recenterOnVehicle() }
        )

        val currentGpsForRadar = gpsState
        val radarLat = when (currentGpsForRadar) {
            is GpsLocationState.MapMatchedFix -> currentGpsForRadar.snappedLocation.latitude
            is GpsLocationState.FixAcquired -> currentGpsForRadar.location.latitude
            is GpsLocationState.DeadReckoning -> currentGpsForRadar.estimatedLocation.latitude
            else -> null
        }
        val radarLon = when (currentGpsForRadar) {
            is GpsLocationState.MapMatchedFix -> currentGpsForRadar.snappedLocation.longitude
            is GpsLocationState.FixAcquired -> currentGpsForRadar.location.longitude
            is GpsLocationState.DeadReckoning -> currentGpsForRadar.estimatedLocation.longitude
            else -> null
        }
        val radarSpeed = when (currentGpsForRadar) {
            is GpsLocationState.MapMatchedFix -> currentGpsForRadar.speedKmh.toDouble()
            is GpsLocationState.FixAcquired -> currentGpsForRadar.speedKmh?.toDouble() ?: 0.0
            is GpsLocationState.DeadReckoning -> currentGpsForRadar.estimatedSpeedKmh.toDouble()
            else -> 0.0
        }
        val activeRadarAlert = remember(radarLat, radarLon, radarSpeed) {
            if (radarLat != null && radarLon != null) {
                TrafficRadarEngine.evaluateHazards(
                    currentLatitude = radarLat,
                    currentLongitude = radarLon,
                    currentSpeedKmh = radarSpeed
                )
            } else null
        }

        ActiveJourneyTopHudPanel(
            uiState = uiState,
            gpsState = gpsState,
            activeRadarAlert = activeRadarAlert,
            glassBorderBrush = glassBorderBrush,
            showModeSelector = showModeSelector,
            onToggleModeSelector = { showModeSelector = !showModeSelector },
            onOpenSearch = {
                viewModel.openSearch()
                showModeSelector = false
            },
            onCloseSearch = { viewModel.closeSearch() },
            onSetNavigationMode = { mode ->
                viewModel.setNavigationMode(mode)
                showModeSelector = false
            },
            onStartActiveNavigation = {
                viewModel.startActiveNavigation()
                showModeSelector = false
            },
            onSetupDemoPlan = {
                viewModel.setupDemoSamplePlan()
                showModeSelector = false
            },
            onPauseNavigation = {
                viewModel.pauseNavigation()
                showModeSelector = false
            },
            onToggleGpsSignalQuality = { isWeak ->
                viewModel.toggleGpsSignalQuality(isWeak)
                showModeSelector = false
            },
            onToggleOfflineMode = { isOffline ->
                viewModel.toggleOfflineMode(isOffline)
                showModeSelector = false
            },
            onToggleOfflineMapCache = { isCached ->
                viewModel.toggleOfflineMapCache(isCached)
                showModeSelector = false
            }
        )

        ActiveJourneyModeBottomPanels(
            uiState = uiState,
            gpsState = gpsState,
            glassBorderBrush = glassBorderBrush,
            viewModel = viewModel,
            onRequestExitConfirmation = { showExitDialog = true }
        )

        uiState.selectedStation?.let { selectedStationWithPorts ->
            StationIntelligenceSheet(
                stationWithPorts = selectedStationWithPorts,
                onRouteToStation = {
                    val maxKw = selectedStationWithPorts.ports.mapNotNull { it.powerKw?.takeIf { kw -> kw.isFinite() && kw > 0.0 } }.maxOrNull()
                    val city = selectedStationWithPorts.station.city ?: "ایران"
                    val powerLabel = maxKw?.let { "شارژ ${it.toInt()} kW" } ?: "توان شارژ نامشخص"
                    val dest = DestinationSearchResult(
                        id = selectedStationWithPorts.station.id,
                        title = selectedStationWithPorts.station.canonicalName,
                        subtitle = "$city • $powerLabel",
                        coordinate = GeoCoordinate(selectedStationWithPorts.station.latitude, selectedStationWithPorts.station.longitude),
                        type = DestinationType.CHARGING_STATION
                    )
                    viewModel.clearSelectedStation()
                    viewModel.selectDestination(dest)
                },
                onClose = { viewModel.clearSelectedStation() }
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(text = strings.exitConfirmTitle, fontWeight = FontWeight.Bold) },
                text = { Text(text = strings.exitConfirmMessage) },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            viewModel.exitNavigation()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.CrimsonAlert)
                    ) {
                        Text(text = strings.confirmExit, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(text = strings.cancel)
                    }
                },
                containerColor = ColorTokens.CockpitSurface
            )
        }
    }
}
