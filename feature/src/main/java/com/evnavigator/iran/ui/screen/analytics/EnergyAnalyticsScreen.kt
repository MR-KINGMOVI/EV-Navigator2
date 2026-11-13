package com.evnavigator.iran.ui.screen.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Advanced Automotive Energy Analytics Screen for EV Navigator Iran.
 * Visualizes physics decomposition: aerodynamic drag, rolling resistance, topography,
 * HVAC auxiliary consumption, and temperature derating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyAnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: EnergyAnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val testSpeedKmh = uiState.testSpeedKmh
    val ambientTempC = uiState.ambientTempC
    val isHvacActive = uiState.isHvacActive

    val aeroPowerKw = uiState.aeroPowerKw
    val rollingPowerKw = uiState.rollingPowerKw
    val hvacPowerKw = uiState.hvacPowerKw
    val totalPowerKw = uiState.totalPowerKw
    val consumptionWhPerKm = uiState.consumptionWhPerKm

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تحلیل پیشرفته انرژی و مصرف باتری",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "←", fontSize = 20.sp, color = ColorTokens.NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header summary
            Text(
                text = "شبیه‌ساز رفتار مصرف انرژی • ${uiState.vehicleModelDisplayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ColorTokens.NeonCyan
            )
            Text(
                text = "تجزیه فیزیکی تلفات آیرودینامیک، اصطکاک لاستیک‌ها، شیب جاده و بار سرمایش/گرمایش کابین",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Result Cockpit Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, ColorTokens.NeonCyan.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "میانگین مصرف شبیه‌سازی‌شده در این شرایط:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.1f Wh/km".format(consumptionWhPerKm),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = ColorTokens.NeonCyan
                    )
                    Text(
                        text = "معادل ${(consumptionWhPerKm / 10.0).toInt()} kWh در ۱۰۰ کیلومتر • توان کل: %.1f kW".format(totalPowerKw),
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTokens.ElectricGreen
                    )
                }
            }

            // Interactive Speed Slider
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "سرعت پیمایش خودرو:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${testSpeedKmh.toInt()} km/h",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.VoltBlue
                        )
                    }

                    Slider(
                        value = testSpeedKmh,
                        onValueChange = { viewModel.updateParameters(it, ambientTempC, isHvacActive) },
                        valueRange = 50f..140f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorTokens.NeonCyan,
                            activeTrackColor = ColorTokens.NeonCyan,
                            inactiveTrackColor = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = "سرعت‌های بالای ۱۰۰ کیلومتر بر ساعت به دلیل رابطه توانی پسا (v³)، مصرف انرژی را به شکل تصاعدی افزایش می‌دهند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            // Interactive Temperature Slider
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "دمای محیط بیرونی:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ambientTempC.toInt()} °C",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (ambientTempC > 35f) ColorTokens.CrimsonAlert else ColorTokens.AmberAlert
                        )
                    }

                    Slider(
                        value = ambientTempC,
                        onValueChange = { viewModel.updateParameters(testSpeedKmh, it, isHvacActive) },
                        valueRange = -10f..48f,
                        steps = 28,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorTokens.AmberAlert,
                            activeTrackColor = ColorTokens.AmberAlert,
                            inactiveTrackColor = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = "راندمان شیمیایی پک باتری در دمای ۲۵ درجه به ۱۰۰٪ می‌رسد. در تابستان‌های سوزان کویری ایران، بار تهویه و خنک‌کاری پک باتری افزایش می‌یابد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            // Energy Breakdown Table
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "سهم تلفات انرژی در این نقطه کاری:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    EnergyLossRow(
                        name = "پسا آیرودینامیک هوا (Aerodynamic Drag)",
                        value = "%.2f kW".format(aeroPowerKw),
                        percent = ((aeroPowerKw / totalPowerKw) * 100).toInt(),
                        color = ColorTokens.NeonCyan
                    )

                    EnergyLossRow(
                        name = "مقاومت غلتشی تایرها (Rolling Resistance)",
                        value = "%.2f kW".format(rollingPowerKw),
                        percent = ((rollingPowerKw / totalPowerKw) * 100).toInt(),
                        color = ColorTokens.VoltBlue
                    )

                    EnergyLossRow(
                        name = "سیستم تهویه و حرارتی باتری (HVAC & Thermal)",
                        value = "%.2f kW".format(hvacPowerKw),
                        percent = ((hvacPowerKw / totalPowerKw) * 100).toInt(),
                        color = ColorTokens.AmberAlert
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EnergyLossRow(
    name: String,
    value: String,
    percent: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$value ($percent%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = (percent / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = Color(0xFF1E293B)
        )
    }
}
