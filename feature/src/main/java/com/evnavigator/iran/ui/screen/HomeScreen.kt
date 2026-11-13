package com.evnavigator.iran.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.designsystem.components.GlassCard
import com.evnavigator.iran.designsystem.components.GlassSurface
import com.evnavigator.iran.designsystem.components.TelemetryDial
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.home.HomeCockpitViewModel
import com.evnavigator.iran.ui.screen.home.StrategicCorridor

/**
 * Next-Generation Automotive Cockpit Dashboard for EV Navigator Iran.
 * Features ultra-luxury Frosted Acrylic & Crystal Glassmorphism, real-time battery telemetry,
 * active vehicle powertrain telemetry, verified Iranian fast charger radar, transit corridors,
 * and seamless Dark Glass / Light Crystal responsiveness.
 */
@Composable
fun HomeScreen(
    viewModel: HomeCockpitViewModel = hiltViewModel(),
    onNavigateToMap: () -> Unit = {},
    onNavigateToTripPlanner: () -> Unit = {},
    onNavigateToNavigation: () -> Unit = {},
    onNavigateToCharging: () -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onLaunchCorridorTrip: ((String, String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())
    val strings = com.evnavigator.iran.designsystem.locale.appStrings()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // =====================================================================
            // 1. Top Cockpit Brand Header & Connectivity Status
            // =====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "کاک‌پیت هوشمند ناوبری و پایش انرژی خودروهای الکتریکی",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xD9101726) else Color(0xEBFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) ColorTokens.ElectricGreen.copy(alpha = 0.5f) else Color(0xFF00C853).copy(alpha = 0.6f)
                    ),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isDark) ColorTokens.ElectricGreen else Color(0xFF00C853),
                                    CircleShape
                                )
                        )
                        Text(
                            text = "۱۰۰٪ آفلاین آماده",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) ColorTokens.ElectricGreen else Color(0xFF007E33),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // =====================================================================
            // 2. Active Vehicle Telemetry Hero Glass Card
            // =====================================================================
            val activeVehicle = uiState.activeVehicle
            val vehicleName = activeVehicle?.let { "${it.manufacturer} ${it.model}" } ?: "Volkswagen ID.4 CROZZ"
            val usableBattery = (activeVehicle?.usableBatteryCapacityWh ?: 77_000.0) / 1000.0
            val maxDcPower = activeVehicle?.dcChargingMaxPowerKw ?: 115.0
            val connectorTypes = activeVehicle?.connectorTypes?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "CCS2, GB/T"

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "خودروی فعال در ناوگان",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = vehicleName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToVehicles,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("گاراژ خودروها", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Dial & Quick Telemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryDial(
                            socPercent = uiState.batterySocPercent,
                            estimatedRangeKm = uiState.estimatedRangeKm,
                            batteryKwh = usableBattery * (uiState.batterySocPercent / 100.0),
                            isCharging = uiState.isCharging
                        )

                        // Vehicle Technical Highlights
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CockpitMiniMetric("ظرفیت باتری مفید", "%.1f kWh".format(usableBattery), MaterialTheme.colorScheme.primary)
                            CockpitMiniMetric("حداکثر شارژ DC", "${maxDcPower.toInt()} kW", if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854))
                            CockpitMiniMetric("پورت استاندارد", connectorTypes, MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), thickness = 0.5.dp)

                    // Battery Adjustment Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تنظیم شارژ فعلی باتری:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SocAdjustChip("-10%", isDark) { viewModel.updateSoc(uiState.batterySocPercent - 10) }
                            SocAdjustChip("-5%", isDark) { viewModel.updateSoc(uiState.batterySocPercent - 5) }
                            SocAdjustChip("+5%", isDark) { viewModel.updateSoc(uiState.batterySocPercent + 5) }
                            SocAdjustChip("+10%", isDark) { viewModel.updateSoc(uiState.batterySocPercent + 10) }
                        }
                    }
                }
            }

            // =====================================================================
            // 3. Active Journey Resume Banner (when navigation is active)
            // =====================================================================
            if (uiState.hasActiveJourney) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToNavigation() },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🧭", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "ناوبری زنده فعال است",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "وضعیت: ${uiState.activeJourneyStatus ?: "پایش مسیر و انرژی"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToNavigation,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("ادامه سفر", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
                        }
                    }
                }
            }

            // =====================================================================
            // 4. Strategic Corridors Quick-Launch (Trans-Iran Highways)
            // =====================================================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "کریدورهای استراتژیک شارژ بین‌شهری ایران",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.strategicCorridors) { corridor ->
                        StrategicCorridorCard(
                            corridor = corridor,
                            isDark = isDark,
                            onClick = {
                                if (onLaunchCorridorTrip != null) {
                                    onLaunchCorridorTrip(corridor.origin, corridor.destination)
                                } else {
                                    onNavigateToTripPlanner()
                                }
                            }
                        )
                    }
                }
            }

            // =====================================================================
            // 5. High-Voltage Fast Charging Radar (Nearby DC Chargers)
            // =====================================================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ایستگاه‌های شارژ سریع DC فعال (${uiState.totalVerifiedStations} هاب کشوری)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(onClick = onNavigateToCharging) {
                        Text("مشاهده همه", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.nearbyStations.isEmpty()) {
                    Text(
                        text = "در حال بارگذاری هاب‌های شارژ از پایگاه داده محلی...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    for (station in uiState.nearbyStations.take(4)) {
                        QuickStationCard(
                            stationWithPorts = station,
                            isDark = isDark,
                            onNavigate = onNavigateToMap
                        )
                    }
                }
            }

            // =====================================================================
            // 6. Navigation Actions Grid (8 Cockpit Destinations)
            // =====================================================================
            Text(
                text = "دسترسی سریع به ماژول‌های سامانه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationActionTile(
                    title = "نقشه و GPS",
                    subtitle = "ناوبری تعاملی",
                    glyph = "🗺️",
                    accentColor = ColorTokens.VoltBlue,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMap
                )
                NavigationActionTile(
                    title = "سفر هوشمند",
                    subtitle = "محاسبه انرژی و توقف",
                    glyph = "⚡",
                    accentColor = MaterialTheme.colorScheme.primary,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTripPlanner
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationActionTile(
                    title = "ایستگاه‌های شارژ",
                    subtitle = "کشف و فیلتر هاب‌ها",
                    glyph = "🔌",
                    accentColor = if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCharging
                )
                NavigationActionTile(
                    title = "گاراژ خودروها",
                    subtitle = "مشخصات فنی و فیزیک",
                    glyph = "🚗",
                    accentColor = ColorTokens.AmberAlert,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToVehicles
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationActionTile(
                    title = "تحلیل انرژی",
                    subtitle = "گراف‌های مصرف و باتری",
                    glyph = "📊",
                    accentColor = Color(0xFFAB47BC),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAnalytics
                )
                NavigationActionTile(
                    title = "سوابق سفرها",
                    subtitle = "تاریخچه رانندگی",
                    glyph = "🕒",
                    accentColor = Color(0xFF26A69A),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHistory
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NavigationActionTile(
                    title = "علاقه‌مندی‌ها",
                    subtitle = "مسیرها و ایستگاه‌ها",
                    glyph = "⭐",
                    accentColor = Color(0xFFFFB300),
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToFavorites
                )
                NavigationActionTile(
                    title = "تنظیمات",
                    subtitle = "زبان و پوسته شیشه‌ای",
                    glyph = "⚙️",
                    accentColor = MaterialTheme.colorScheme.primary,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
private fun CockpitMiniMetric(label: String, value: String, accentColor: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

@Composable
private fun SocAdjustChip(label: String, isDark: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StrategicCorridorCard(
    corridor: StrategicCorridor,
    isDark: Boolean,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .width(230.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${corridor.distanceKm} km",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${corridor.coveragePercent}% پوشش",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854)
                )
            }

            Text(
                text = corridor.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = corridor.chargersSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickStationCard(
    stationWithPorts: DomainChargingStationWithPorts,
    isDark: Boolean,
    onNavigate: () -> Unit
) {
    val station = stationWithPorts.station
    val maxPower = stationWithPorts.ports.maxOfOrNull { it.powerKw ?: 0.0 } ?: 0.0

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.canonicalName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${station.city ?: "ایران"} • ${station.operatorName ?: "مپنا"} • ${maxPower.toInt()} kW سریع",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onNavigate,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("مسیریابی", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun NavigationActionTile(
    title: String,
    subtitle: String,
    glyph: String,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassSurface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = accentColor.copy(alpha = if (isDark) 0.20f else 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = glyph, fontSize = 18.sp)
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}
