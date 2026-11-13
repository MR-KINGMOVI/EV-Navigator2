package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.map.MapNavigationUiState
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import kotlin.math.roundToInt

@Composable
fun BoxScope.MapTopGlassCommandCenter(
    uiState: MapNavigationUiState,
    gpsState: GpsLocationState,
    isDark: Boolean,
    obsidianGlassBrush: Brush,
    cyanGlassBorder: Brush,
    filterUltraFastOnly: Boolean,
    onToggleFilterUltraFast: () -> Unit,
    showSocTuner: Boolean,
    onToggleSocTuner: () -> Unit,
    quickCorridors: List<DestinationSearchResult>,
    onSearchQueryChanged: (String) -> Unit,
    onClearDestination: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onSelectDestination: (DestinationSearchResult) -> Unit,
    onUpdateBatterySoc: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(obsidianGlassBrush)
                .border(1.2.dp, cyanGlassBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(ColorTokens.NeonCyan.copy(alpha = 0.16f), CircleShape)
                        .border(1.dp, ColorTokens.NeonCyan.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = EVIcons.Search,
                        contentDescription = "جستجو",
                        tint = ColorTokens.NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                TextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = "جستجوی شهر، بزرگراه یا ۱۹۲ ایستگاه شارژ...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF0F172A),
                        cursorColor = ColorTokens.NeonCyan,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (uiState.searchQuery.isNotEmpty() || uiState.selectedDestination != null) {
                    IconButton(
                        onClick = onClearDestination,
                        modifier = Modifier
                            .size(32.dp)
                            .background(ColorTokens.CrimsonAlert.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(
                            imageVector = EVIcons.Close,
                            contentDescription = "پاک کردن",
                            tint = ColorTokens.CrimsonAlert,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ColorTokens.ElectricGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ColorTokens.ElectricGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.clickable { onOpenImportDialog() }
                    ) {
                        Text(
                            text = "GPX",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = ColorTokens.ElectricGreen
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (filterUltraFastOnly) ColorTokens.ElectricGreen.copy(alpha = 0.22f) else Color(0xD90B1222),
                border = BorderStroke(
                    1.dp,
                    if (filterUltraFastOnly) ColorTokens.ElectricGreen else ColorTokens.NeonCyan.copy(alpha = 0.35f)
                ),
                modifier = Modifier.clickable { onToggleFilterUltraFast() }
            ) {
                Text(
                    text = if (filterUltraFastOnly) "🚀 فوق‌سریع ≥۱۰۰kW ✓" else "⚡ همه ۱۹۲ شارژر",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (filterUltraFastOnly) ColorTokens.ElectricGreen else ColorTokens.NeonCyan
                )
            }

            quickCorridors.forEach { hub ->
                val isSelected = uiState.selectedDestination?.id == hub.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) ColorTokens.NeonCyan.copy(alpha = 0.24f) else Color(0xD90B1222),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) ColorTokens.NeonCyan else Color(0x4038BDF8)
                    ),
                    modifier = Modifier.clickable { onSelectDestination(hub) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = if (hub.type == DestinationType.CHARGING_STATION) "⚡" else "🧭",
                            fontSize = 11.sp
                        )
                        Text(
                            text = hub.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) ColorTokens.NeonCyan else Color.White
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xE60B1222),
                border = BorderStroke(1.dp, ColorTokens.ElectricGreen.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { onToggleSocTuner() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ColorTokens.ElectricGreen, CircleShape)
                    )
                    Text(
                        text = if (uiState.estimatedRangeKm > 0) {
                            "🔋 ${uiState.userBatterySocPercent.roundToInt()}% • برد ${uiState.estimatedRangeKm} km"
                        } else {
                            "🔋 ${uiState.userBatterySocPercent.roundToInt()}% • برد نامشخص"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorTokens.ElectricGreen
                    )
                    Text(text = "⚙️", fontSize = 11.sp)
                }
            }

            GpsCockpitBadge(gpsState = gpsState)
        }

        val observedTempC = uiState.ambientTemperatureCelsius
        if (observedTempC != null) {
            WeatherRadarOverlay(
                temperatureCelsius = observedTempC,
                windSpeedKmh = 0.0,
                windHeadingDegrees = 0.0,
                vehicleHeadingDegrees = (gpsState as? GpsLocationState.FixAcquired)?.bearingDegrees?.toDouble() ?: 0.0,
                airDensityKgM3 = 1.225,
                weatherCondition = CorridorWeatherCondition.CLEAR_SUNNY,
                estimatedRangeImpactPercent = 0.0,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedVisibility(visible = showSocTuner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(obsidianGlassBrush)
                    .border(1.dp, cyanGlassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تنظیم درصد شارژ فعلی (${uiState.vehicleModelName})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${uiState.userBatterySocPercent.roundToInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = ColorTokens.ElectricGreen
                        )
                    }
                    Slider(
                        value = uiState.userBatterySocPercent.toFloat(),
                        onValueChange = { onUpdateBatterySoc(it.toDouble()) },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorTokens.ElectricGreen,
                            activeTrackColor = ColorTokens.ElectricGreen,
                            inactiveTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isSearching && uiState.searchResults.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(obsidianGlassBrush)
                    .border(1.2.dp, cyanGlassBorder, RoundedCornerShape(20.dp))
            ) {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(uiState.searchResults, key = { it.id }) { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDestination(result) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (result.type == DestinationType.CHARGING_STATION)
                                                ColorTokens.ElectricGreen.copy(alpha = 0.16f)
                                            else
                                                ColorTokens.NeonCyan.copy(alpha = 0.16f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            if (result.type == DestinationType.CHARGING_STATION)
                                                ColorTokens.ElectricGreen.copy(alpha = 0.5f)
                                            else
                                                ColorTokens.NeonCyan.copy(alpha = 0.5f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (result.type == DestinationType.CHARGING_STATION) EVIcons.Charger else EVIcons.Location,
                                        contentDescription = null,
                                        tint = if (result.type == DestinationType.CHARGING_STATION) ColorTokens.ElectricGreen else ColorTokens.NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = result.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            result.distanceFromUserKm?.let { dist ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ColorTokens.NeonCyan.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "${dist.roundToInt()} km",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTokens.NeonCyan
                                    )
                                }
                            }
                        }
                        Divider(color = Color(0x2264748B))
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.MapRightGlassControlDock(
    uiState: MapNavigationUiState,
    obsidianGlassBrush: Brush,
    cyanGlassBorder: Brush,
    currentMapTheme: String,
    onSelectMapTheme: (String) -> Unit,
    showLayerMenu: Boolean,
    onToggleLayerMenu: () -> Unit,
    showRangeIsochrone: Boolean,
    onToggleRangeIsochrone: () -> Unit,
    isVoiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    isPreconditioning: Boolean,
    onTogglePreconditioning: () -> Unit,
    onToggleCameraPerspective: () -> Unit,
    onToggleChargingLayer: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenterUser: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = showLayerMenu,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(obsidianGlassBrush)
                    .border(1.2.dp, cyanGlassBorder, RoundedCornerShape(20.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "سبک کارتوگرافی نقشه",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTokens.NeonCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    LayerMenuOption("🌑 شیشه‌ای دارک (Obsidian HD)", currentMapTheme == "DARK_OBSIDIAN") {
                        onSelectMapTheme("DARK_OBSIDIAN")
                    }
                    LayerMenuOption("🗺️ گوگل مپ (Google Road)", currentMapTheme == "GOOGLE_ROAD") {
                        onSelectMapTheme("GOOGLE_ROAD")
                    }
                    LayerMenuOption("🛰️ ماهواره (Satellite HD)", currentMapTheme == "SATELLITE") {
                        onSelectMapTheme("SATELLITE")
                    }
                    LayerMenuOption("⛰️ توپوگرافی (Terrain)", currentMapTheme == "TERRAIN") {
                        onSelectMapTheme("TERRAIN")
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(obsidianGlassBrush)
                .border(1.2.dp, cyanGlassBorder, RoundedCornerShape(24.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassDockButton(
                label = when (currentMapTheme) {
                    "DARK_OBSIDIAN" -> "DARK"
                    "GOOGLE_ROAD" -> "MAP"
                    "SATELLITE" -> "SAT"
                    else -> "TOPO"
                },
                isActive = showLayerMenu,
                activeColor = ColorTokens.NeonCyan,
                onClick = onToggleLayerMenu
            )

            GlassDockButton(
                label = if (uiState.isHeadingUp) "3D" else "2D",
                isActive = uiState.isHeadingUp,
                activeColor = ColorTokens.NeonCyan,
                onClick = onToggleCameraPerspective
            )

            IconButton(
                onClick = onToggleChargingLayer,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (uiState.isChargingLayerVisible) ColorTokens.ElectricGreen.copy(alpha = 0.22f) else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (uiState.isChargingLayerVisible) ColorTokens.ElectricGreen else Color(0x3394A3B8),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = EVIcons.Charger,
                    contentDescription = "لایه شارژرها",
                    tint = if (uiState.isChargingLayerVisible) ColorTokens.ElectricGreen else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            GlassDockButton(
                label = "◎",
                isActive = showRangeIsochrone,
                activeColor = ColorTokens.ElectricGreen,
                onClick = onToggleRangeIsochrone
            )

            GlassDockButton(
                label = if (isVoiceEnabled) "🔊" else "🔇",
                isActive = isVoiceEnabled,
                activeColor = ColorTokens.NeonCyan,
                onClick = onToggleVoice
            )

            GlassDockButton(
                label = "🔥",
                isActive = isPreconditioning,
                activeColor = ColorTokens.EnergyAmber,
                onClick = onTogglePreconditioning
            )

            GlassDockButton(
                label = "+",
                isActive = false,
                activeColor = ColorTokens.NeonCyan,
                onClick = onZoomIn
            )

            GlassDockButton(
                label = "−",
                isActive = false,
                activeColor = ColorTokens.NeonCyan,
                onClick = onZoomOut
            )

            IconButton(
                onClick = onRecenterUser,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.linearGradient(colors = listOf(ColorTokens.NeonCyan, ColorTokens.ElectricGreen)),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = EVIcons.CurrentLocation,
                    contentDescription = "مکان من",
                    tint = Color(0xFF040812),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassDockButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isActive) activeColor.copy(alpha = 0.22f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor else Color(0x3394A3B8)
        ),
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = if (isActive) activeColor else Color.White
            )
        }
    }
}

@Composable
private fun LayerMenuOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) ColorTokens.NeonCyan.copy(alpha = 0.2f) else Color(0x80111A2E),
        border = BorderStroke(
            1.dp,
            if (isSelected) ColorTokens.NeonCyan else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
            color = if (isSelected) ColorTokens.NeonCyan else Color.White
        )
    }
}

@Composable
private fun GpsCockpitBadge(gpsState: GpsLocationState) {
    val (label, dotColor, speedText) = when (gpsState) {
        is GpsLocationState.MapMatchedFix -> Triple(
            "GPS قفل آزادراه",
            ColorTokens.ElectricGreen,
            "${gpsState.speedKmh.roundToInt()} km/h"
        )
        is GpsLocationState.FixAcquired -> Triple(
            "GPS دقیق (±${gpsState.accuracyMeters.roundToInt()}m)",
            ColorTokens.ElectricGreen,
            "${(gpsState.speedKmh ?: 0f).roundToInt()} km/h"
        )
        is GpsLocationState.DeadReckoning -> Triple(
            "تخمین تونل (DR)",
            ColorTokens.AmberAlert,
            "${gpsState.estimatedSpeedKmh.roundToInt()} km/h"
        )
        is GpsLocationState.SearchingForFix -> Triple(
            "جستجوی ماهواره...",
            ColorTokens.NeonCyan,
            "0 km/h"
        )
        else -> Triple(
            "شبیه‌ساز کاپیت فعال",
            ColorTokens.NeonCyan,
            "READY"
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE60B1222),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "• $speedText",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = dotColor
            )
        }
    }
}
