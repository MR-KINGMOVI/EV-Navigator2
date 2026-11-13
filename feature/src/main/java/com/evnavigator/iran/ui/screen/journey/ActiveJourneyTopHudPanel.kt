package com.evnavigator.iran.ui.screen.journey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.core.routing.engine.RadarHazardAlert
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.map.components.CockpitMapTileSource
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.radar.SpeedCameraRadarOverlay
import org.osmdroid.tileprovider.tilesource.ITileSource
import kotlin.math.roundToInt

@Composable
fun BoxScope.ActiveJourneyFloatingMapControls(
    uiState: ActiveJourneyUiState,
    glassBorderBrush: Brush,
    showLayerSelector: Boolean,
    onToggleLayerSelector: () -> Unit,
    currentTileSource: ITileSource,
    onSelectTileSource: (ITileSource) -> Unit,
    onToggleAudio: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleCameraPerspective: () -> Unit,
    onRecenterOnVehicle: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SmallFloatingActionButton(
            onClick = onToggleLayerSelector,
            containerColor = Color(0xF0131B2E),
            contentColor = ColorTokens.NeonCyan,
            shape = CircleShape,
            modifier = Modifier.border(1.dp, glassBorderBrush, CircleShape)
        ) {
            Text(text = "🗺️", fontSize = 16.sp)
        }

        SmallFloatingActionButton(
            onClick = onToggleAudio,
            containerColor = Color(0xF0131B2E),
            contentColor = if (uiState.isAudioMuted) ColorTokens.CrimsonAlert else ColorTokens.NeonCyan,
            shape = CircleShape,
            modifier = Modifier.border(1.dp, glassBorderBrush, CircleShape)
        ) {
            Text(
                text = if (uiState.isAudioMuted) "🔇" else "🔊",
                fontSize = 16.sp
            )
        }

        SmallFloatingActionButton(
            onClick = onZoomIn,
            containerColor = Color(0xF0131B2E),
            contentColor = ColorTokens.NeonCyan,
            shape = CircleShape,
            modifier = Modifier.border(1.dp, glassBorderBrush, CircleShape)
        ) {
            Text(text = "+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        SmallFloatingActionButton(
            onClick = onZoomOut,
            containerColor = Color(0xF0131B2E),
            contentColor = ColorTokens.NeonCyan,
            shape = CircleShape,
            modifier = Modifier.border(1.dp, glassBorderBrush, CircleShape)
        ) {
            Text(text = "−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        SmallFloatingActionButton(
            onClick = onToggleCameraPerspective,
            containerColor = Color(0xF0131B2E),
            contentColor = if (uiState.isHeadingUpCamera) ColorTokens.NeonCyan else ColorTokens.TextSecondaryOnDark,
            shape = CircleShape,
            modifier = Modifier.border(1.dp, glassBorderBrush, CircleShape)
        ) {
            Text(
                text = if (uiState.isHeadingUpCamera) "3D" else "2D",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        FloatingActionButton(
            onClick = onRecenterOnVehicle,
            containerColor = ColorTokens.NeonCyan,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(imageVector = EVIcons.Navigation, contentDescription = "Recenter", modifier = Modifier.size(24.dp))
        }
    }

    if (showLayerSelector) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 76.dp)
                .border(1.dp, glassBorderBrush, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF50B0F19),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "انتخاب لایه نقشه",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTokens.NeonCyan,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                LayerOptionButton(
                    title = "🗺️ نقشه معابر گوگل",
                    isSelected = currentTileSource == CockpitMapTileSource.GoogleRoadmap
                ) {
                    onSelectTileSource(CockpitMapTileSource.GoogleRoadmap)
                }
                LayerOptionButton(
                    title = "🛰️ ماهواره ترکیبی گوگل",
                    isSelected = currentTileSource == CockpitMapTileSource.GoogleSatellite
                ) {
                    onSelectTileSource(CockpitMapTileSource.GoogleSatellite)
                }
                LayerOptionButton(
                    title = "🏔️ توپوگرافی و گردنه‌ها",
                    isSelected = currentTileSource == CockpitMapTileSource.GoogleTerrain
                ) {
                    onSelectTileSource(CockpitMapTileSource.GoogleTerrain)
                }
                LayerOptionButton(
                    title = "🌍 نقشه آزاد جهان (OSM)",
                    isSelected = currentTileSource == CockpitMapTileSource.OpenStreetMap
                ) {
                    onSelectTileSource(CockpitMapTileSource.OpenStreetMap)
                }
            }
        }
    }
}

@Composable
fun BoxScope.ActiveJourneyTopHudPanel(
    uiState: ActiveJourneyUiState,
    gpsState: GpsLocationState,
    activeRadarAlert: RadarHazardAlert?,
    glassBorderBrush: Brush,
    showModeSelector: Boolean,
    onToggleModeSelector: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSetNavigationMode: (NavigationScreenMode) -> Unit,
    onStartActiveNavigation: () -> Unit,
    onSetupDemoPlan: () -> Unit,
    onPauseNavigation: () -> Unit,
    onToggleGpsSignalQuality: (Boolean) -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onToggleOfflineMapCache: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(14.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SpeedCameraRadarOverlay(activeAlert = activeRadarAlert)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF20B0F19),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, glassBorderBrush, RoundedCornerShape(16.dp)),
            shadowElevation = 14.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val gpsBadgeColor = when (gpsState) {
                        is GpsLocationState.FixAcquired -> ColorTokens.ElectricGreen
                        is GpsLocationState.MapMatchedFix -> ColorTokens.NeonCyan
                        is GpsLocationState.DeadReckoning -> ColorTokens.AmberAlert
                        else -> ColorTokens.CrimsonAlert
                    }
                    val gpsBadgeText = when (val g = gpsState) {
                        is GpsLocationState.FixAcquired -> if (g.isAccuracyKnown && !g.accuracyMeters.isNaN()) {
                            "GPS ${g.accuracyMeters.toInt()}m"
                        } else {
                            "GPS (دقت نامشخص)"
                        }
                        is GpsLocationState.MapMatchedFix -> "قفل معبر"
                        is GpsLocationState.DeadReckoning -> "اینرسیال (DR)"
                        else -> "GPS ضعیف"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = gpsBadgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, gpsBadgeColor.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(gpsBadgeColor)
                            )
                            Text(
                                text = gpsBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = gpsBadgeColor
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (uiState.isOfflineMapAvailable) Color(0x3300E5FF) else Color(0x33FF9800),
                        border = BorderStroke(1.dp, if (uiState.isOfflineMapAvailable) Color(0x6600E5FF) else Color(0x66FF9800))
                    ) {
                        Text(
                            text = if (uiState.isOfflineMapAvailable) "تایل ابری" else "کش آفلاین",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.isOfflineMapAvailable) ColorTokens.NeonCyan else ColorTokens.AmberAlert
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (uiState.isInternetConnected) Color(0x2600E676) else Color(0x33E53935),
                        border = BorderStroke(1.dp, if (uiState.isInternetConnected) Color(0x6600E676) else Color(0x66E53935))
                    ) {
                        Text(
                            text = if (uiState.isInternetConnected) "آنلاین" else "آفلاین",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.isInternetConnected) ColorTokens.ElectricGreen else ColorTokens.CrimsonAlert
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (uiState.navigationMode) {
                            NavigationScreenMode.ACTIVE_NAVIGATION -> uiState.plan?.waypoints?.lastOrNull()?.name ?: "ناوبری فعال"
                            NavigationScreenMode.ROUTE_PREVIEW -> "پیش‌نمایش مسیر"
                            NavigationScreenMode.DESTINATION_SEARCH -> "جستجوی مقصد"
                            NavigationScreenMode.NAVIGATION_PAUSED -> "سفر متوقف شده"
                            else -> "گشت آزاد ناوبری"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x3300E5FF))
                            .border(1.dp, ColorTokens.NeonCyan, CircleShape)
                            .clickable {
                                if (uiState.navigationMode == NavigationScreenMode.DESTINATION_SEARCH) {
                                    onCloseSearch()
                                } else {
                                    onOpenSearch()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.navigationMode == NavigationScreenMode.DESTINATION_SEARCH) "✕" else "🔍",
                            fontSize = 14.sp,
                            color = ColorTokens.NeonCyan
                        )
                    }

                    if (com.evnavigator.iran.feature.BuildConfig.DEBUG) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable { onToggleModeSelector() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "حالت‌ها",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (com.evnavigator.iran.feature.BuildConfig.DEBUG && showModeSelector) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xF50B0F19),
                border = BorderStroke(1.dp, glassBorderBrush),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "تست ۱۰ حالت ناوبری:", fontSize = 11.sp, color = ColorTokens.NeonCyan, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ModeTestChip("۱. گشت آزاد", uiState.navigationMode == NavigationScreenMode.IDLE_FREE_DRIVE) {
                            onSetNavigationMode(NavigationScreenMode.IDLE_FREE_DRIVE)
                        }
                        ModeTestChip("۲. جستجو", uiState.navigationMode == NavigationScreenMode.DESTINATION_SEARCH) {
                            onOpenSearch()
                        }
                        ModeTestChip("۳. پیش‌نمایش", uiState.navigationMode == NavigationScreenMode.ROUTE_PREVIEW) {
                            if (uiState.plan != null) onSetNavigationMode(NavigationScreenMode.ROUTE_PREVIEW) else onOpenSearch()
                        }
                        ModeTestChip("۴. ناوبری فعال", uiState.navigationMode == NavigationScreenMode.ACTIVE_NAVIGATION) {
                            onStartActiveNavigation()
                        }
                        ModeTestChip("دمو (QA)", uiState.isDemoPlan) {
                            onSetupDemoPlan()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ModeTestChip("۵. توقف موقت", uiState.navigationMode == NavigationScreenMode.NAVIGATION_PAUSED) {
                            onPauseNavigation()
                        }
                        ModeTestChip("۶. سیگنال ضعیف", uiState.navigationMode == NavigationScreenMode.GPS_WEAK_OR_DISABLED) {
                            onToggleGpsSignalQuality(true)
                        }
                        ModeTestChip("۷. آفلاین", uiState.navigationMode == NavigationScreenMode.OFFLINE_MODE) {
                            onToggleOfflineMode(true)
                        }
                        ModeTestChip("۸. بدون کش نقشه", uiState.navigationMode == NavigationScreenMode.MISSING_OFFLINE_MAP) {
                            onToggleOfflineMapCache(false)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ModeTestChip("۹. قطب‌نما مستقیم", uiState.navigationMode == NavigationScreenMode.MISSING_ROUTE_DATA) {
                            onSetNavigationMode(NavigationScreenMode.MISSING_ROUTE_DATA)
                        }
                        ModeTestChip("۱۰. خطای محاسبه", uiState.navigationMode == NavigationScreenMode.ROUTE_CALCULATION_ERROR) {
                            onSetNavigationMode(NavigationScreenMode.ROUTE_CALCULATION_ERROR)
                        }
                    }
                }
            }
        }

        when (uiState.navigationMode) {
            NavigationScreenMode.ACTIVE_NAVIGATION -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF00B0F19),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, glassBorderBrush, RoundedCornerShape(18.dp)),
                    shadowElevation = 16.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(ColorTokens.NeonCyan.copy(alpha = 0.15f))
                                    .border(1.5.dp, ColorTokens.NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (uiState.currentManeuverIcon) {
                                        "CHARGER" -> EVIcons.Charging
                                        "ARRIVE" -> EVIcons.Map
                                        "TURN" -> EVIcons.Route
                                        else -> EVIcons.Navigation
                                    },
                                    contentDescription = "Maneuver",
                                    tint = ColorTokens.NeonCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                val progress = uiState.progress
                                val distText = if (progress != null) {
                                    val distMeters = progress.distanceToNextWaypointMeters
                                    if (distMeters >= 1000.0) {
                                        "${(distMeters / 1000.0 * 10).roundToInt() / 10.0} کیلومتر"
                                    } else {
                                        "${distMeters.roundToInt()} متر"
                                    }
                                } else {
                                    "${(uiState.nextManeuverDistanceMeters / 1000.0 * 10).roundToInt() / 10.0} کیلومتر"
                                }

                                Text(
                                    text = distText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = ColorTokens.NeonCyan
                                )
                                Text(
                                    text = uiState.currentManeuver,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val currentSpeedVal = (uiState.progress?.currentSpeedKmh ?: 0.0).roundToInt()
                            val isSpeeding = currentSpeedVal > uiState.speedLimitKmh
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(
                                        width = if (isSpeeding) 4.dp else 3.5.dp,
                                        color = if (isSpeeding) Color(0xFFFF1744) else Color(0xFFE53935),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${uiState.speedLimitKmh}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x66070A12), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LaneArrowBadge(symbol = "↖️", isTargetLane = uiState.currentManeuverIcon == "TURN_LEFT")
                                LaneArrowBadge(symbol = "⬆️", isTargetLane = uiState.currentManeuverIcon != "TURN_LEFT" && uiState.currentManeuverIcon != "TURN_RIGHT")
                                LaneArrowBadge(symbol = "⬆️", isTargetLane = false)
                                LaneArrowBadge(symbol = "↗️", isTargetLane = uiState.currentManeuverIcon == "TURN_RIGHT")
                            }
                            Text(
                                text = uiState.nextRoadName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = ColorTokens.TextSecondaryOnDark
                            )
                        }
                    }
                }
            }
            NavigationScreenMode.IDLE_FREE_DRIVE -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD90B0F19),
                    border = BorderStroke(1.dp, Color(0x3300E5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "🧭", fontSize = 18.sp)
                            Column {
                                Text(
                                    text = "گشت آزاد ناوبری فعال است",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "مقصدی تنظیم نشده است • برای شروع سفر جستجو کنید",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorTokens.TextSecondaryOnDark
                                )
                            }
                        }
                        TextButton(onClick = onOpenSearch) {
                            Text(text = "انتخاب مقصد", color = ColorTokens.NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            else -> {}
        }

        uiState.activeWarning?.let { warning ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ColorTokens.AmberAlert.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, ColorTokens.AmberAlert),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = EVIcons.Warning, contentDescription = null, tint = ColorTokens.AmberAlert, modifier = Modifier.size(18.dp))
                    Text(text = warning, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (uiState.isTunnelMode) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ColorTokens.AmberAlert.copy(alpha = 0.2f),
                border = BorderStroke(1.5.dp, ColorTokens.AmberAlert),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "⚠️", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "حالت تونل فعال است (Dead Reckoning)",
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorTokens.AmberAlert,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "سیگنال ماهواره قطع است؛ ناوبری با شبیه‌سازی اینرسیال بردار سرعت ادامه دارد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTestChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) Color(0x6600E5FF) else Color(0x22FFFFFF),
        border = BorderStroke(1.dp, if (isSelected) ColorTokens.NeonCyan else Color(0x33FFFFFF)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) ColorTokens.NeonCyan else Color.White
        )
    }
}

@Composable
private fun LaneArrowBadge(symbol: String, isTargetLane: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isTargetLane) Color(0x3300E5FF) else Color(0x331E2832),
        border = BorderStroke(
            1.dp,
            if (isTargetLane) ColorTokens.NeonCyan else Color.Transparent
        )
    ) {
        Text(
            text = symbol,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun LayerOptionButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0x3300E5FF) else Color(0x22131B2E),
        border = BorderStroke(
            1.dp,
            if (isSelected) ColorTokens.NeonCyan else Color(0x33FFFFFF)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) ColorTokens.NeonCyan else Color.White
        )
    }
}
