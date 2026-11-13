package com.evnavigator.iran.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.ui.screen.map.MapNavigationViewModel
import com.evnavigator.iran.ui.screen.map.WindshieldHudOverlay
import com.evnavigator.iran.ui.screen.map.components.CockpitMapTileSource
import com.evnavigator.iran.ui.screen.map.components.CockpitRouteDrawer
import com.evnavigator.iran.ui.screen.map.components.MapImportCorridorDialog
import com.evnavigator.iran.ui.screen.map.components.MapOsmOverlayRenderer
import com.evnavigator.iran.ui.screen.map.components.MapRightGlassControlDock
import com.evnavigator.iran.ui.screen.map.components.MapTopGlassCommandCenter
import com.evnavigator.iran.ui.screen.map.components.StationIntelligenceSheet
import com.evnavigator.iran.ui.screen.map.components.TrueDarkTileFilter
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import com.evnavigator.iran.ui.screen.map.model.RouteCalculationState
import com.evnavigator.iran.ui.screen.map.voice.PersianVoiceNavigator
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.roundToInt

/**
 * Ultra-Luxury Dark Obsidian Glassmorphic Cockpit Map & EV Navigation Hub (V12.5 Decomposed).
 *
 * Overlay rendering and corridor import dialogs are delegated to [MapOsmOverlayRenderer]
 * and [MapImportCorridorDialog] to maintain strict single-responsibility modularity.
 */
@Composable
fun MapScreen(
    viewModel: MapNavigationViewModel = hiltViewModel(),
    onStartNavigation: () -> Unit = {},
    onNavigateToChargerDetail: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val gpsState by viewModel.gpsState.collectAsState()

    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

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
        if (granted) viewModel.onPermissionGranted()
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var currentMapTheme by remember { mutableStateOf("DARK_OBSIDIAN") }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showSocTuner by remember { mutableStateOf(false) }
    var filterUltraFastOnly by remember { mutableStateOf(false) }
    var showRangeIsochrone by remember { mutableStateOf(true) }
    var isVoiceEnabled by remember { mutableStateOf(true) }
    var isPreconditioning by remember { mutableStateOf(false) }
    var showWindshieldHud by remember { mutableStateOf(false) }
    val voiceNavigator = remember { PersianVoiceNavigator(context) }

    val quickCorridors = remember {
        listOf(
            DestinationSearchResult("hub_isfahan", "اصفهان", "کریدور مرکزی • پل خواجو", GeoCoordinate(32.6370, 51.6830), DestinationType.CITY_HUB),
            DestinationSearchResult("hub_qom", "مهروماه قم", "ایستگاه فوق‌سریع ۱۲۰kW", GeoCoordinate(34.6401, 50.8764), DestinationType.CHARGING_STATION),
            DestinationSearchResult("hub_rasht", "رشت", "کریدور شمال • گیلان", GeoCoordinate(37.2800, 49.5830), DestinationType.CITY_HUB),
            DestinationSearchResult("hub_mashhad", "مشهد", "کریدور شرق • خراسان", GeoCoordinate(36.2870, 59.6150), DestinationType.CITY_HUB),
            DestinationSearchResult("hub_shiraz", "شیراز", "دروازه قرآن • فارس", GeoCoordinate(29.6360, 52.5590), DestinationType.CITY_HUB),
            DestinationSearchResult("hub_tabriz", "تبریز", "ائل‌گلی • شمال‌غرب", GeoCoordinate(38.0260, 46.3680), DestinationType.CITY_HUB),
            DestinationSearchResult("hub_milad", "برج میلاد", "هاب شارژ سریع مپنا", GeoCoordinate(35.7448, 51.3753), DestinationType.CHARGING_STATION)
        )
    }

    LaunchedEffect(Unit) {
        val osmDir = File(context.noBackupFilesDir, "osmdroid")
        val tileCacheDir = File(osmDir, "tiles")
        osmDir.mkdirs()
        tileCacheDir.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmDir
        Configuration.getInstance().osmdroidTileCache = tileCacheDir
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Linux; Android) EVNavigatorIran/12.5"
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
        viewModel.recenterEvent.collect { targetCoord ->
            mapViewRef?.controller?.animateTo(GeoPoint(targetCoord.latitude, targetCoord.longitude), 15.8, 850L)
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
            voiceNavigator.shutdown()
            mapViewRef?.onDetach()
        }
    }

    val obsidianGlassBrush = Brush.verticalGradient(
        colors = if (isDark) listOf(Color(0xEB0B1222), Color(0xF5060A13)) else listOf(Color(0xF2FFFFFF), Color(0xEBF1F5F9))
    )

    val cyanGlassBorder = Brush.horizontalGradient(
        colors = listOf(
            ColorTokens.NeonCyan.copy(alpha = 0.65f),
            ColorTokens.ElectricGreen.copy(alpha = 0.35f),
            ColorTokens.NeonCyan.copy(alpha = 0.65f)
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050811))) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(CockpitMapTileSource.DarkMatter)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(uiState.zoomLevel)
                    controller.setCenter(GeoPoint(uiState.mapCenter.latitude, uiState.mapCenter.longitude))
                    overlayManager.tilesOverlay.setColorFilter(
                        TrueDarkTileFilter.createForSource(CockpitMapTileSource.DarkMatter, isDark)
                    )
                    mapViewRef = this
                }
            },
            update = { mapView ->
                MapOsmOverlayRenderer.updateMapOverlays(
                    context = context,
                    mapView = mapView,
                    uiState = uiState,
                    gpsState = gpsState,
                    currentMapTheme = currentMapTheme,
                    isDark = isDark,
                    showRangeIsochrone = showRangeIsochrone,
                    filterUltraFastOnly = filterUltraFastOnly,
                    onDismissMenusOrSelection = {
                        if (uiState.selectedStation != null) {
                            viewModel.clearSelectedStation()
                            true
                        } else if (showLayerMenu || showSocTuner) {
                            showLayerMenu = false
                            showSocTuner = false
                            true
                        } else {
                            false
                        }
                    },
                    onSelectCoordinate = { coord -> viewModel.selectMapCoordinate(coord) },
                    onSelectStation = { station -> viewModel.selectStation(station) }
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(colors = listOf(Color(0xCC040812), Color.Transparent)))
        )

        MapTopGlassCommandCenter(
            uiState = uiState,
            gpsState = gpsState,
            isDark = isDark,
            obsidianGlassBrush = obsidianGlassBrush,
            cyanGlassBorder = cyanGlassBorder,
            filterUltraFastOnly = filterUltraFastOnly,
            onToggleFilterUltraFast = { filterUltraFastOnly = !filterUltraFastOnly },
            showSocTuner = showSocTuner,
            onToggleSocTuner = { showSocTuner = !showSocTuner },
            quickCorridors = quickCorridors,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onClearDestination = { viewModel.clearDestination() },
            onOpenImportDialog = { viewModel.setImportDialogOpen(true) },
            onSelectDestination = { dest ->
                viewModel.selectDestination(dest)
                mapViewRef?.controller?.animateTo(
                    GeoPoint(dest.coordinate.latitude, dest.coordinate.longitude),
                    11.0,
                    850L
                )
            },
            onUpdateBatterySoc = { viewModel.updateBatterySoc(it) }
        )

        MapRightGlassControlDock(
            uiState = uiState,
            obsidianGlassBrush = obsidianGlassBrush,
            cyanGlassBorder = cyanGlassBorder,
            currentMapTheme = currentMapTheme,
            onSelectMapTheme = { theme ->
                currentMapTheme = theme
                showLayerMenu = false
            },
            showLayerMenu = showLayerMenu,
            onToggleLayerMenu = { showLayerMenu = !showLayerMenu },
            showRangeIsochrone = showRangeIsochrone,
            onToggleRangeIsochrone = { showRangeIsochrone = !showRangeIsochrone },
            isVoiceEnabled = isVoiceEnabled,
            onToggleVoice = {
                isVoiceEnabled = !isVoiceEnabled
                voiceNavigator.isMuted = !isVoiceEnabled
                if (isVoiceEnabled) {
                    voiceNavigator.speakGuidance("هدایت صوتی فارسی کاپیت فعال شد")
                }
            },
            isPreconditioning = isPreconditioning,
            onTogglePreconditioning = {
                isPreconditioning = !isPreconditioning
                if (isPreconditioning && isVoiceEnabled) {
                    voiceNavigator.speakGuidance("پیش‌گرمایش هوشمند باتری برای شارژ فوق‌سریع صد و بیست کیلووات فعال شد")
                }
            },
            onToggleCameraPerspective = { viewModel.toggleCameraPerspective() },
            onToggleChargingLayer = { viewModel.toggleChargingLayer() },
            onZoomIn = { mapViewRef?.controller?.zoomIn() },
            onZoomOut = { mapViewRef?.controller?.zoomOut() },
            onRecenterUser = {
                if (!hasPermission) {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                } else {
                    viewModel.recenterOnUserLocation()
                }
            }
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val selectedStation = uiState.selectedStation
            if (selectedStation != null) {
                Box(modifier = Modifier.padding(12.dp)) {
                    StationIntelligenceSheet(
                        stationWithPorts = selectedStation,
                        onRouteToStation = {
                            val dest = DestinationSearchResult(
                                id = selectedStation.station.id,
                                title = selectedStation.station.canonicalName,
                                subtitle = "${selectedStation.station.city} • ${selectedStation.station.operatorName ?: "ایستگاه شارژ"}",
                                coordinate = GeoCoordinate(selectedStation.station.latitude, selectedStation.station.longitude),
                                type = DestinationType.CHARGING_STATION
                            )
                            viewModel.selectDestination(dest)
                        },
                        onClose = { viewModel.clearSelectedStation() }
                    )
                }
            } else {
                when (val rState = uiState.routeState) {
                    is RouteCalculationState.Calculating -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(obsidianGlassBrush)
                                .border(1.2.dp, cyanGlassBorder, RoundedCornerShape(22.dp))
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = ColorTokens.NeonCyan,
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 3.dp
                                )
                                Column {
                                    Text(
                                        text = "در حال محاسبه مسیر و بهینه‌سازی شارژ کریدور...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "شبیه‌سازی مصرف انرژی توپوگرافی برای ${uiState.vehicleModelName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    is RouteCalculationState.Success -> {
                        CockpitRouteDrawer(
                            routeState = rState,
                            vehicleName = uiState.vehicleModelName,
                            startSoc = uiState.userBatterySocPercent,
                            onStartNavigation = onStartNavigation,
                            onCloseRoute = { viewModel.clearDestination() },
                            onStationClick = { stationId -> onNavigateToChargerDetail?.invoke(stationId) }
                        )
                    }

                    is RouteCalculationState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(obsidianGlassBrush)
                                .border(1.2.dp, ColorTokens.CrimsonAlert, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rState.message,
                                        color = ColorTokens.CrimsonAlert,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.clearDestination() }) {
                                        Text("بستن", color = Color.White)
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.setGpsFixForTesting(uiState.mapCenter.latitude, uiState.mapCenter.longitude)
                                        uiState.selectedDestination?.let { viewModel.selectDestination(it) }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.NeonCyan),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "📍 محاسبه مسیر از مرکز فعلی نقشه (تهران)",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    RouteCalculationState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(obsidianGlassBrush)
                                .border(1.2.dp, cyanGlassBorder, RoundedCornerShape(22.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${uiState.vehicleModelName}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ColorTokens.ElectricGreen.copy(alpha = 0.2f),
                                            border = BorderStroke(0.8.dp, ColorTokens.ElectricGreen)
                                        ) {
                                            Text(
                                                text = "${uiState.userBatterySocPercent.toInt()}%",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorTokens.ElectricGreen
                                            )
                                        }
                                    }
                                    Text(
                                        text = "نقشه آفلاین و آنلاین فعال • برای شروع سریع یک کریدور را لمس کنید",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.clickable { onStartNavigation() }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Brush.horizontalGradient(colors = listOf(ColorTokens.NeonCyan, ColorTokens.ElectricGreen)),
                                                RoundedCornerShape(14.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = "🚀", fontSize = 12.sp)
                                            Text(
                                                text = "ناوبری زنده",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF040812)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showWindshieldHud) {
            val activeSuccess = uiState.routeState as? RouteCalculationState.Success
            val nextManeuver = activeSuccess?.maneuvers?.firstOrNull()
            val nextChargingWp = activeSuccess?.plan?.waypoints?.firstOrNull { it.isChargingStop }
            val firstLegKm = (activeSuccess?.plan?.legs?.firstOrNull()?.distanceMeters ?: 0.0) / 1000.0
            WindshieldHudOverlay(
                speedKmh = when (val g = gpsState) {
                    is GpsLocationState.MapMatchedFix -> g.speedKmh.roundToInt()
                    is GpsLocationState.FixAcquired -> (g.speedKmh ?: 0f).roundToInt()
                    is GpsLocationState.DeadReckoning -> g.estimatedSpeedKmh.roundToInt()
                    else -> 0
                },
                speedLimitKmh = nextManeuver?.speedLimitKmh ?: 0,
                upcomingTurnArrow = "⬆️",
                upcomingTurnDistance = nextManeuver?.let { "${"%.1f".format(it.distanceMeters / 1000.0)} کیلومتر" } ?: "—",
                upcomingTurnInstructionFa = nextManeuver?.instructionFarsi ?: activeSuccess?.destinationName ?: "مسیر فعال نیست",
                distanceToStationKm = firstLegKm,
                nextStationNameFa = nextChargingWp?.name ?: "بدون توقف شارژ",
                nextStationPowerKw = nextChargingWp?.stationDetails?.maxPowerKw?.roundToInt() ?: 0,
                currentSocPercent = uiState.userBatterySocPercent.toInt(),
                arrivalSocPercent = activeSuccess?.arrivalSocPercent?.roundToInt() ?: uiState.userBatterySocPercent.toInt(),
                remainingRangeKm = uiState.estimatedRangeKm,
                onExitHud = { showWindshieldHud = false }
            )
        }

        if (uiState.isImportDialogOpen) {
            MapImportCorridorDialog(
                onDismiss = { viewModel.setImportDialogOpen(false) },
                onSelectPreset = { preset -> viewModel.importRoutePreset(preset) }
            )
        }
    }
}
