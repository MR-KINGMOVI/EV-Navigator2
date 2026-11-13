package com.evnavigator.iran.ui.screen.journey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.components.PrimaryButton
import com.evnavigator.iran.designsystem.components.SecondaryButton
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.locale.appStrings
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.DestinationType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.ActiveJourneyModeBottomPanels(
    uiState: ActiveJourneyUiState,
    gpsState: GpsLocationState,
    glassBorderBrush: Brush,
    viewModel: ActiveJourneyViewModel,
    onRequestExitConfirmation: () -> Unit
) {
    val strings = appStrings()

    when (uiState.navigationMode) {
        NavigationScreenMode.IDLE_FREE_DRIVE -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.dp, glassBorderBrush, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF20B0F19),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val speedVal = when (val g = gpsState) {
                            is GpsLocationState.FixAcquired -> g.speedKmh?.roundToInt() ?: 0
                            is GpsLocationState.MapMatchedFix -> g.speedKmh.toInt()
                            is GpsLocationState.DeadReckoning -> g.estimatedSpeedKmh.toInt()
                            else -> 0
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "$speedVal", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Color.White)
                            Text(text = "km/h", style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${uiState.currentSocPercent.roundToInt()}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.currentSocPercent < 20.0) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen
                                )
                                val rangeEstimate = ((uiState.vehicleBatteryKwh * (uiState.currentSocPercent / 100.0) * 1000.0) / uiState.averageConsumptionWhPerKm).roundToInt()
                                Text(text = "برد: $rangeEstimate km", style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark)
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background((if (uiState.currentSocPercent < 20.0) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = EVIcons.Charging,
                                    contentDescription = "Battery",
                                    tint = if (uiState.currentSocPercent < 20.0) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x3300E5FF),
                        border = BorderStroke(1.dp, Color(0x6600E5FF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSearch() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "🔍", fontSize = 16.sp)
                            Text(
                                text = "جستجوی مقصد، شهرها و ایستگاه‌های شارژ ایران...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        NavigationScreenMode.DESTINATION_SEARCH -> {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9070A12)),
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .border(1.dp, glassBorderBrush, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = Color(0xF80B0F19)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "انتخاب مقصد یا ایستگاه شارژ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTokens.NeonCyan
                                )
                                IconButton(onClick = { viewModel.closeSearch() }) {
                                    Text(text = "✕", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(text = "جستجوی شهر، ایستگاه یا بزرگراه...", color = ColorTokens.TextSecondaryOnDark) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ColorTokens.NeonCyan,
                                    unfocusedBorderColor = ColorTokens.CockpitCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0x33131B2E),
                                    unfocusedContainerColor = Color(0x22131B2E)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    label = { Text("همه مقاصد", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.onSearchQueryChanged("شارژ") },
                                    label = { Text("⚡ ایستگاه‌های DC", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.onSearchQueryChanged("اصفهان") },
                                    label = { Text("اصفهان", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.onSearchQueryChanged("قم") },
                                    label = { Text("قم", fontSize = 11.sp) }
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.searchResults) { result ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0x33131B2E),
                                        border = BorderStroke(1.dp, Color(0x2200E5FF)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectDestination(result) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (result.type == DestinationType.CHARGING_STATION) ColorTokens.ElectricGreen.copy(alpha = 0.15f)
                                                        else ColorTokens.NeonCyan.copy(alpha = 0.15f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = if (result.type == DestinationType.CHARGING_STATION) "⚡" else "🏙️", fontSize = 18.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = result.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(text = result.subtitle, style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(text = "انتخاب ›", fontSize = 12.sp, color = ColorTokens.NeonCyan, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        NavigationScreenMode.ROUTE_PREVIEW -> {
            val plan = uiState.plan
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.dp, glassBorderBrush, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF20B0F19),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "پیش‌نمایش مسیر خودروی برقی", style = MaterialTheme.typography.labelSmall, color = ColorTokens.NeonCyan, fontWeight = FontWeight.Bold)
                            val orig = plan?.waypoints?.firstOrNull()?.name ?: "مبدأ"
                            val dest = plan?.waypoints?.lastOrNull()?.name ?: "مقصد"
                            Text(text = "$orig ➔ $dest", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        IconButton(onClick = { viewModel.exitNavigation() }) {
                            Text(text = "✕", fontSize = 18.sp, color = Color.White)
                        }
                    }

                    Divider(color = ColorTokens.CockpitCardBorder)

                    val distKm = ((plan?.totalDistanceMeters ?: 0.0) / 1000.0).roundToInt()
                    val durMins = ((plan?.totalDrivingDurationSeconds ?: 0.0) / 60.0).roundToInt()
                    val durHours = durMins / 60
                    val durRemainingMins = durMins % 60
                    val arrivalSoc = uiState.predictedArrivalSocPercent?.roundToInt() ?: 42

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill("مسافت", "$distKm km", Color.White)
                        MetricPill("زمان حرکت", if (durHours > 0) "$durHours س $durRemainingMins د" else "$durMins دقیقه", Color.White)
                        MetricPill("شارژ مقصد", "$arrivalSoc%", if (arrivalSoc < 20) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen)
                        MetricPill("مصرف میانگین", "${uiState.averageConsumptionWhPerKm.toInt()} Wh/km", ColorTokens.NeonCyan)
                    }

                    val chargingStops = plan?.waypoints?.filter { it.isChargingStop } ?: emptyList()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0x33131B2E),
                        border = BorderStroke(1.dp, if (chargingStops.isNotEmpty()) Color(0x6600E676) else Color(0x33FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = if (chargingStops.isNotEmpty()) "⚡" else "✅", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = if (chargingStops.isNotEmpty()) "نیاز به ${chargingStops.size} توقف شارژ در مسیر" else "بدون نیاز به شارژ بین‌راهی (شارژ کافی است)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (chargingStops.isNotEmpty()) {
                                    Text(
                                        text = chargingStops.joinToString("، ") { "${it.name} (${it.stationDetails?.maxPowerKw?.toInt() ?: 120} kW)" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ColorTokens.TextSecondaryOnDark
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = { viewModel.startActiveNavigation() },
                            text = "شروع ناوبری زنده",
                            modifier = Modifier.weight(1.2f)
                        )
                        Button(
                            onClick = { viewModel.startDriveSimulation() },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.EnergyAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("شبیه‌سازی حرکت", fontWeight = FontWeight.Black, color = Color(0xFF070A12), fontSize = 12.sp)
                        }
                        SecondaryButton(
                            onClick = { viewModel.exitNavigation() },
                            text = "انصراف",
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
            }
        }

        NavigationScreenMode.ACTIVE_NAVIGATION -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.dp, glassBorderBrush, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF20B0F19),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val progress = uiState.progress
                    val currentSpeed = (progress?.currentSpeedKmh ?: 0.0).roundToInt()
                    val remainingKm = ((progress?.remainingDistanceToDestinationMeters ?: (uiState.plan?.totalDistanceMeters ?: 0.0)) / 1000.0).roundToInt()
                    val remainingMinutes = ((progress?.estimatedRemainingSeconds ?: (uiState.plan?.totalDrivingDurationSeconds ?: 0.0)) / 60.0).roundToInt()
                    val speedLimit = uiState.speedLimitKmh
                    val isSpeeding = currentSpeed > speedLimit

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "$currentSpeed",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isSpeeding) ColorTokens.CrimsonAlert else Color.White
                            )
                            Text(text = "km/h", style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${uiState.currentSocPercent.roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.currentSocPercent < 20.0) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen
                                )
                                val rangeEstimate = ((uiState.vehicleBatteryKwh * (uiState.currentSocPercent / 100.0) * 1000.0) / uiState.averageConsumptionWhPerKm).roundToInt()
                                Text(text = "برد: $rangeEstimate km", style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark)
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background((if (uiState.currentSocPercent < 20.0) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = EVIcons.Charging, contentDescription = null, tint = if (uiState.currentSocPercent < 20.0) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen, modifier = Modifier.size(18.dp))
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "$remainingKm km", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.NeonCyan)
                            val etaHours = remainingMinutes / 60
                            val etaMins = remainingMinutes % 60
                            val etaText = if (etaHours > 0) "$etaHours س $etaMins د" else "$etaMins دقیقه"
                            Text(text = "زمان: $etaText", style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark)
                        }
                    }

                    Divider(color = ColorTokens.CockpitCardBorder)

                    val nextCharger = progress?.nextChargingStop ?: uiState.plan?.waypoints?.firstOrNull { it.isChargingStop }
                    if (nextCharger != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x80131B2E),
                            border = BorderStroke(1.dp, ColorTokens.ElectricGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(imageVector = EVIcons.Charging, contentDescription = null, tint = ColorTokens.ElectricGreen, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text(text = nextCharger.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        val chargerDistKm = ((progress?.distanceToNextChargingStopMeters ?: 135_000.0) / 1000.0).roundToInt()
                                        Text(text = "شارژ سریع ${nextCharger.stationDetails?.maxPowerKw?.toInt() ?: 120} kW • فاصله: $chargerDistKm km", style = MaterialTheme.typography.labelSmall, color = ColorTokens.TextSecondaryOnDark)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ColorTokens.ElectricGreen,
                                    modifier = Modifier.clickable { viewModel.startChargingAtStation() }
                                ) {
                                    Text(
                                        text = strings.enterChargingSession,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            onClick = { viewModel.pauseNavigation() },
                            text = "توقف موقت",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.triggerReplanning() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTokens.NeonCyan),
                            border = BorderStroke(1.dp, ColorTokens.NeonCyan)
                        ) {
                            Text(text = "مسیریابی مجدد", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onRequestExitConfirmation,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTokens.CrimsonAlert),
                            border = BorderStroke(1.dp, ColorTokens.CrimsonAlert)
                        ) {
                            Text(text = "پایان", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        NavigationScreenMode.NAVIGATION_PAUSED -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.5.dp, ColorTokens.AmberAlert, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B0F19),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "⏸️", fontSize = 24.sp)
                        Column {
                            Text(text = "ناوبری موقتاً متوقف شد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.AmberAlert)
                            Text(text = "سفر معلق است. موقعیت و مصرف باتری پایش می‌شوند.", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    Divider(color = ColorTokens.CockpitCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = { viewModel.resumeNavigation() },
                            text = "ادامه ناوبری",
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            onClick = { viewModel.triggerReplanning() },
                            text = "مسیریابی مجدد",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.exitNavigation() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTokens.CrimsonAlert),
                            border = BorderStroke(1.dp, ColorTokens.CrimsonAlert)
                        ) {
                            Text(text = "خروج", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        NavigationScreenMode.GPS_WEAK_OR_DISABLED -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.5.dp, ColorTokens.CrimsonAlert, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B0F19),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "📡", fontSize = 24.sp)
                        Column {
                            Text(text = "سیگنال GPS ضعیف است یا موقعیت‌یاب خاموش است", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.CrimsonAlert)
                            Text(text = "امواج ماهواره دریافت نمی‌شود. لطفاً دسترسی دقیق موقعیت مکانی را بررسی فرمایید.", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = { viewModel.startGps() },
                            text = "تلاش مجدد برای دریافت سیگنال",
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            onClick = { viewModel.setNavigationMode(NavigationScreenMode.IDLE_FREE_DRIVE) },
                            text = "ادامه در گشت آزاد",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        NavigationScreenMode.OFFLINE_MODE -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B0F19),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🌐", fontSize = 24.sp)
                        Column {
                            Text(text = "حالت ناوبری آفلاین (بدون اینترنت)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.NeonCyan)
                            Text(text = "مسیریابی بر اساس دیتابیس محلی ایستگاه‌ها و تایل‌های ذخیره شده انجام می‌شود.", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = { viewModel.toggleOfflineMode(false) },
                            text = "اتصال مجدد آنلاین",
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            onClick = { viewModel.setNavigationMode(NavigationScreenMode.ACTIVE_NAVIGATION) },
                            text = "ادامه ناوبری آفلاین",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        NavigationScreenMode.MISSING_OFFLINE_MAP -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.5.dp, ColorTokens.AmberAlert, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B0F19),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "💾", fontSize = 24.sp)
                        Column {
                            Text(text = "کش آفلاین نقشه این منطقه دانلود نشده است", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.AmberAlert)
                            Text(text = "تایل‌های نقشه برای مشاهده بدون اینترنت نیاز به ذخیره‌سازی دارند.", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = { viewModel.toggleOfflineMapCache(true) },
                            text = "دریافت تایل‌های ابری گوگل",
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            onClick = { viewModel.setNavigationMode(NavigationScreenMode.IDLE_FREE_DRIVE) },
                            text = "ادامه",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        NavigationScreenMode.MISSING_ROUTE_DATA -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.5.dp, ColorTokens.NeonCyan, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B0F19),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "هدایت مستقیم قطب‌نما (Direct Bearing)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.NeonCyan)
                            Text(text = "شبکه معابر برای این نقطه موجود نیست؛ جهت و بردار مستقیم نشان داده می‌شود.", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    val bearingDeg = uiState.directBearingDegrees ?: 45f
                    val directDistKm = ((uiState.directDistanceMeters ?: 42000.0) / 1000.0).roundToInt()
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color(0x33131B2E))
                            .border(2.dp, ColorTokens.NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = EVIcons.Navigation,
                            contentDescription = "Compass Needle",
                            tint = ColorTokens.NeonCyan,
                            modifier = Modifier
                                .size(48.dp)
                                .rotate(bearingDeg)
                        )
                    }

                    Text(
                        text = "زاویه مستقیم: ${bearingDeg.roundToInt()}° • فاصله هوایی: $directDistKm کیلومتر",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = { viewModel.retryRouteCalculation() },
                            text = "محاسبه مجدد معابر",
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            onClick = { viewModel.setNavigationMode(NavigationScreenMode.IDLE_FREE_DRIVE) },
                            text = "بازگشت",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        NavigationScreenMode.ROUTE_CALCULATION_ERROR -> {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .border(1.5.dp, ColorTokens.CrimsonAlert, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF50B0F19),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "❌", fontSize = 24.sp)
                        Column {
                            Text(text = "خطا در محاسبه مسیر ناوبری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorTokens.CrimsonAlert)
                            Text(
                                text = uiState.routeCalculationError ?: "امکان یافتن مسیر متصل به معابر برای مقصد مورد نظر وجود ندارد.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Divider(color = ColorTokens.CockpitCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryButton(
                            onClick = {
                                viewModel.setGpsFixForTesting(35.6997, 51.3380)
                                viewModel.retryRouteCalculation()
                            },
                            text = "تلاش مجدد (مبدأ تهران)",
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            onClick = { viewModel.setNavigationMode(NavigationScreenMode.MISSING_ROUTE_DATA) },
                            text = "هدایت مستقیم قطب‌نما",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.setNavigationMode(NavigationScreenMode.IDLE_FREE_DRIVE) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Text(text = "لغو", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(title: String, value: String, valueColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = title, fontSize = 10.sp, color = ColorTokens.TextSecondaryOnDark)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
