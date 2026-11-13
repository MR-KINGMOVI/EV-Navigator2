package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.components.GlassSurface
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.map.model.RouteCalculationState
import kotlin.math.roundToInt

/**
 * Ultra-Luxury Dark Glassmorphic Multi-Stage Cockpit Route & Topography Drawer.
 * Features:
 * - Specular Obsidian Acrylic Surface with Neon Cyan & Emerald Hairline Borders
 * - 4 Glanceable Glass Telemetry Pods (Distance, Duration, Arrival SoC %, Net Energy kWh)
 * - Synchronized Topographic Elevation & Battery SoC Curve (with real synthesized corridor points)
 * - Interactive Scheduled DC Fast-Charging Stop Timeline Cards
 * - Glowing Gradient One-Tap 3D Turn-by-Turn Navigation Trigger
 */
@Composable
fun CockpitRouteDrawer(
    routeState: RouteCalculationState.Success,
    vehicleName: String,
    startSoc: Double,
    onStartNavigation: () -> Unit,
    onCloseRoute: () -> Unit,
    onStationClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val plan = routeState.plan
    val isFeasible = routeState.isFeasible
    val arrivalSoc = routeState.arrivalSocPercent
    val chargingWaypoints = remember(plan) { plan.waypoints.filter { it.isChargingStop } }

    // Build verified waypoint & leg profile points (no synthetic/fake elevations)
    val corridorProfilePoints = remember(routeState, startSoc, plan) {
        if (plan.legs.isNotEmpty()) {
            var cumulativeDistKm = 0.0
            val list = mutableListOf<RouteProfilePoint>()
            plan.legs.forEachIndexed { idx, leg ->
                if (idx == 0) {
                    list.add(
                        RouteProfilePoint(
                            distanceKm = 0.0,
                            elevationMeters = Double.NaN,
                            socPercent = leg.departureSocPercent,
                            locationName = leg.fromWaypoint.name.ifBlank { routeState.originName },
                            isChargingStop = leg.fromWaypoint.isChargingStop
                        )
                    )
                }
                cumulativeDistKm += (leg.distanceMeters / 1000.0)
                list.add(
                    RouteProfilePoint(
                        distanceKm = cumulativeDistKm,
                        elevationMeters = Double.NaN,
                        socPercent = leg.arrivalSocPercent,
                        locationName = leg.toWaypoint.name.ifBlank {
                            if (idx == plan.legs.lastIndex) routeState.destinationName else null
                        },
                        isChargingStop = leg.toWaypoint.isChargingStop
                    )
                )
            }
            list
        } else {
            listOf(
                RouteProfilePoint(
                    distanceKm = 0.0,
                    elevationMeters = Double.NaN,
                    socPercent = startSoc,
                    locationName = routeState.originName,
                    isChargingStop = false
                ),
                RouteProfilePoint(
                    distanceKm = routeState.totalDistanceKm,
                    elevationMeters = Double.NaN,
                    socPercent = arrivalSoc,
                    locationName = routeState.destinationName,
                    isChargingStop = false
                )
            )
        }
    }

    val heightModifier = if (isExpanded) {
        Modifier.fillMaxHeight(0.86f)
    } else {
        Modifier.wrapContentHeight()
    }

    val obsidianGlassBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF00B1222),
                Color(0xFA060A13)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF5FFFFFF),
                Color(0xF0F1F5F9)
            )
        )
    }

    val specularBorderBrush = Brush.horizontalGradient(
        colors = listOf(
            ColorTokens.NeonCyan.copy(alpha = 0.65f),
            ColorTokens.ElectricGreen.copy(alpha = 0.45f),
            ColorTokens.NeonCyan.copy(alpha = 0.65f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(heightModifier)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(obsidianGlassBrush)
            .border(
                width = 1.2.dp,
                brush = specularBorderBrush,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // --- 1. Interactive Glass Drag Handle & Expand Toggle ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(4.dp)
                            .background(
                                ColorTokens.NeonCyan.copy(alpha = 0.55f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 2. Header Row: Corridor Title, Feasibility Pill & Close Action ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = routeState.destinationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color.White else Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isFeasible) ColorTokens.ElectricGreen else ColorTokens.AmberAlert).copy(alpha = 0.16f),
                            border = BorderStroke(
                                1.dp,
                                (if (isFeasible) ColorTokens.ElectricGreen else ColorTokens.AmberAlert).copy(alpha = 0.55f)
                            )
                        ) {
                            Text(
                                text = if (isFeasible) "مسیر ایمن با خودروی برقی" else "نیازمند توقف و شارژ بین‌راهی",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFeasible) ColorTokens.ElectricGreen else ColorTokens.AmberAlert
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "از ${routeState.originName} • $vehicleName",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ColorTokens.NeonCyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ColorTokens.NeonCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    ) {
                        Text(
                            text = if (isExpanded) "خلاصه" else "جزئیات و معابر",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.NeonCyan
                        )
                    }

                    IconButton(
                        onClick = onCloseRoute,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isDark) Color(0xFF151F32) else Color(0xFFE2E8F0),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = EVIcons.Close,
                            contentDescription = "بستن مسیر",
                            tint = if (isDark) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 3. 4 Frosted Glass Telemetry Pods ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) Color(0xB30F172A) else Color(0xFFF8FAFC),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        if (isDark) Color(0x3300E5FF) else Color(0xFFCBD5E1),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryPod(
                    label = "مسافت کل",
                    value = "${routeState.totalDistanceKm.roundToInt()} km",
                    valueColor = ColorTokens.NeonCyan,
                    isDark = isDark
                )
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color(0x3364748B)))
                TelemetryPod(
                    label = "زمان رانندگی",
                    value = formatDurationPersian(routeState.totalDurationMinutes),
                    valueColor = if (isDark) Color.White else Color(0xFF0F172A),
                    isDark = isDark
                )
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color(0x3364748B)))
                TelemetryPod(
                    label = "شارژ در مقصد",
                    value = "${arrivalSoc.roundToInt()}%",
                    valueColor = when {
                        arrivalSoc >= 25.0 -> ColorTokens.ElectricGreen
                        arrivalSoc >= 12.0 -> ColorTokens.AmberAlert
                        else -> ColorTokens.CrimsonAlert
                    },
                    isDark = isDark
                )
                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color(0x3364748B)))
                TelemetryPod(
                    label = "مصرف خالص",
                    value = "${routeState.totalEnergyKwh} kWh",
                    valueColor = ColorTokens.EnergyAmber,
                    isDark = isDark
                )
            }

            // --- 4. Expandable Deep Intelligence: Topographic Canvas & Charging Timeline ---
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TopographicEnergyCanvas(
                        profilePoints = corridorProfilePoints,
                        totalDistanceKm = routeState.totalDistanceKm,
                        departureSoc = startSoc,
                        arrivalSoc = arrivalSoc
                    )

                    if (chargingWaypoints.isNotEmpty()) {
                        Text(
                            text = "ایستگاه‌های شارژ برنامه‌ریزی‌شده در کریدور (${chargingWaypoints.size} توقف):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.NeonCyan
                        )

                        chargingWaypoints.forEachIndexed { index, wp ->
                            val maxKw = wp.stationDetails?.maxPowerKw?.takeIf { it > 0.0 }
                            val powerText = if (maxKw != null) "توان ${maxKw.toInt()} kW" else "توان نامشخص"
                            val connectors = wp.stationDetails?.compatibleConnectors?.takeIf { it.isNotEmpty() }?.joinToString(" • ") ?: "درگاه استاندارد"
                            val matchingLeg = plan.legs.firstOrNull { it.toWaypoint.id == wp.id || it.toWaypoint.name == wp.name }
                            val durationMin = matchingLeg?.chargingDurationMinutes?.roundToInt()?.takeIf { it > 0 }
                            val durationBadgeText = if (durationMin != null) "⚡ ~$durationMin دقیقه" else "⚡ زمان شارژ متغیر"
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xCC111C30) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, ColorTokens.ElectricGreen.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        wp.stationDetails?.stationId?.let(onStationClick)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(ColorTokens.ElectricGreen.copy(alpha = 0.18f), CircleShape)
                                                .border(1.dp, ColorTokens.ElectricGreen, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontWeight = FontWeight.Black,
                                                color = ColorTokens.ElectricGreen,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = wp.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$powerText • $connectors • شارژ تا ${(wp.targetDepartureSocPercent ?: 80.0).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ColorTokens.ElectricGreen.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = durationBadgeText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorTokens.ElectricGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (routeState.warnings.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorTokens.AmberAlert.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ColorTokens.AmberAlert.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                routeState.warnings.forEach { w ->
                                    Text(
                                        text = "⚠️ $w",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorTokens.AmberAlert
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 5. Dual Automotive Navigation CTAs (Live HUD + Autopilot Drive Simulation) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartNavigation,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ColorTokens.NeonCyan, ColorTokens.ElectricGreen)
                                ),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = EVIcons.Navigation,
                                contentDescription = null,
                                tint = Color(0xFF040812),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "شروع ناوبری (HUD)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF040812)
                            )
                        }
                    }
                }

                Button(
                    onClick = onStartNavigation,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0284C7), Color(0xFF00E5FF))
                                ),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🎮",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "شبیه‌سازی رانندگی",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryPod(
    label: String,
    value: String,
    valueColor: Color,
    isDark: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = valueColor
        )
    }
}

private fun formatDurationPersian(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m} دقیقه"
}
