package com.evnavigator.iran.ui.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.domain.model.catalog.DomainTripHistorySummary
import com.evnavigator.iran.designsystem.components.EmptyStateView
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    viewModel: TripHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "سوابق پیمایش و سفرهای ثبت‌شده",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "←", fontSize = 20.sp, color = ColorTokens.NeonCyan)
                    }
                },
                actions = {
                    if (uiState.trips.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAllHistory() }) {
                            Text("پاکسازی", color = ColorTokens.CrimsonAlert, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // Aggregate Telemetry Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "آمار تجمعی مصرف ناوگان محلی",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.NeonCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HistoryStatItem("مسافت کل", "${uiState.totalDistanceKm.toInt()} km", MaterialTheme.colorScheme.onSurface)
                            HistoryStatItem("انرژی مصرفی", "${uiState.totalEnergyKwh.toInt()} kWh", ColorTokens.ElectricGreen)
                            HistoryStatItem("راندمان میانگین", "%.1f Wh/km".format(uiState.averageWhPerKm), ColorTokens.VoltBlue)
                            HistoryStatItem("تعداد سفرها", "${uiState.trips.size}", ColorTokens.AmberAlert)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "لیست سفرهای ثبت‌شده (${uiState.trips.size}):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (uiState.trips.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "هیچ سابقه‌ای ثبت نشده است",
                        description = "سفرهای انجام‌شده با سامانه ناوبری به شکل خودکار و به صورت تجمیعی و با حفظ حریم خصوصی در این بخش ذخیره می‌شوند.",
                        iconGlyph = "📜"
                    )
                }
            } else {
                items(uiState.trips, key = { it.tripId }) { trip ->
                    TripRecordCard(trip = trip)
                }
            }
        }
    }
}

@Composable
private fun HistoryStatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun TripRecordCard(trip: DomainTripHistorySummary) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.getDefault()) }
    val dateStr = remember(trip.startEpochMs) { dateFormat.format(Date(trip.startEpochMs)) }
    val distanceKm = (trip.distanceMeters / 1000.0).toInt()
    val energyKwh = trip.measuredEnergyWh / 1000.0
    val predictedKwh = trip.predictedEnergyWh / 1000.0
    val whPerKm = if (distanceKm > 0) (trip.measuredEnergyWh / distanceKm).toInt() else 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.NeonCyan
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ColorTokens.ElectricGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "داده واقعی کالیبره‌شده",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTokens.ElectricGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$distanceKm کیلومتر پیمایش",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "%.1f kWh واقعی".format(energyKwh),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorTokens.VoltBlue
                )
            }

            Divider(color = Color(0xFF1E293B), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "راندمان: $whPerKm Wh/km • سرعت میانگین: ${trip.averageSpeedKmh.toInt()} km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "پیش‌بینی: %.1f kWh".format(predictedKwh),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}
