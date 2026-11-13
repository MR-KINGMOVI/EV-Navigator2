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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.components.GlassSurface
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.intelligence.ArrivalSocCard
import com.evnavigator.iran.ui.screen.intelligence.ChargingStopRecommendationCard
import com.evnavigator.iran.ui.screen.intelligence.DecisionExplanationCard
import com.evnavigator.iran.ui.screen.intelligence.TripIntelligenceViewModel
import com.evnavigator.iran.ui.screen.map.components.RouteProfilePoint
import com.evnavigator.iran.ui.screen.map.components.TopographicEnergyCanvas
import com.evnavigator.iran.ui.screen.map.components.WeatherRadarOverlay
import com.evnavigator.iran.ui.screen.map.components.CorridorWeatherCondition

/**
 * Next-Generation AI Trip & Energy Planner Screen.
 * Features ultra-luxury Frosted Glassmorphism, topographic elevation physics,
 * battery SOC arrival projection, optimal DC charging stops, and full Dark/Light theme reactivity.
 */
@Composable
fun TripPlannerScreen(
    viewModel: TripIntelligenceViewModel? = null,
    initialOrigin: String = "تهران (میدان آزادی)",
    initialDestination: String = "اصفهان (پل خواجو)",
    onStartNavigation: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())
    val strings = com.evnavigator.iran.designsystem.locale.appStrings()

    var origin by remember { mutableStateOf(if (initialOrigin.isNotBlank()) initialOrigin else "تهران (میدان آزادی)") }
    var destination by remember { mutableStateOf(if (initialDestination.isNotBlank()) initialDestination else "اصفهان (پل خواجو)") }
    var departureSoc by remember { mutableFloatStateOf(85f) }
    var isCalculated by remember { mutableStateOf(true) }

    val intelligenceState by viewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.evnavigator.iran.ui.screen.intelligence.TripIntelligenceUiState())
    }

    LaunchedEffect(origin, destination, departureSoc) {
        kotlinx.coroutines.delay(400)
        viewModel?.planTrip(origin, destination, departureSoc.toDouble())
    }

    val quickRoutes = listOf(
        "تهران (میدان آزادی)" to "اصفهان (پل خواجو)",
        "تهران (میدان آزادی)" to "رشت (میدان شهرداری)",
        "تهران (میدان آزادی)" to "مشهد (حرم مطهر)",
        "اصفهان (پل خواجو)" to "شیراز (دروازه قرآن)",
        "قزوین (میدان مینودر)" to "تبریز (میدان ساعت)",
        "شیراز (دروازه قرآن)" to "بندرعباس (بلوار ساحلی)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header ---
        Column {
            Text(
                text = strings.tripPlannerTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = strings.tripPlannerSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- 2. Active Vehicle Spec Glass Banner ---
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "🚗", fontSize = 24.sp)
                    Column {
                        Text(
                            text = strings.activeVehicleForPhysics,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = intelligenceState.activeVehicleName.ifBlank { strings.activeVehicleBadge },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "${intelligenceState.activeVehicleBatteryCapacityKwh.takeIf { it > 0 }?.toInt() ?: 0} kWh",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- 3. Quick Strategic Routes Bar ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = strings.quickTransitCorridorsTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickRoutes) { (orig, dest) ->
                    val isSelected = origin == orig && destination == dest
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else if (isDark) Color(0xD9101726) else Color(0xEBFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable {
                            origin = orig
                            destination = dest
                        }
                    ) {
                        Text(
                            text = "${orig.split(" ").first()} ← ${dest.split(" ").first()}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // --- 4. Route Inputs Glass Card ---
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
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
                    Text(
                        text = strings.routeParametersSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            val temp = origin
                            origin = destination
                            destination = temp
                        }
                    ) {
                        Text(text = "⇅", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = { Text(strings.tripOriginLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text(strings.tripDestinationLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Departure SOC Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.tripDepartureSocLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${departureSoc.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = departureSoc,
                        onValueChange = { departureSoc = it },
                        valueRange = 10f..100f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Button(
                    onClick = {
                        viewModel?.planTrip(origin, destination, departureSoc.toDouble())
                        isCalculated = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "⚡ " + strings.recalculateEnergyAndStops, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
                }
            }
        }

        // --- 5. Calculation Results & Topographic Profile ---
        if (isCalculated) {
            val state = intelligenceState
            if (state.errorMessage != null) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "⚠️ " + state.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (state.totalDistanceKm != null && state.predictedArrivalSocPercent != null) {
                val totalDistKm = state.totalDistanceKm
                val arrSoc = state.predictedArrivalSocPercent
                val profilePoints = remember(state.elevationProfilePoints, totalDistKm, departureSoc, arrSoc) {
                    if (state.elevationProfilePoints.isNotEmpty()) {
                        val distNonZero = totalDistKm.coerceAtLeast(1.0)
                        val socDelta = departureSoc.toDouble() - arrSoc
                        state.elevationProfilePoints.mapIndexed { index, (distKm, elevMeters) ->
                            val fraction = (distKm / distNonZero).coerceIn(0.0, 1.0)
                            val linearSoc = departureSoc.toDouble() - (fraction * socDelta)
                            val soc = linearSoc.coerceIn(0.0, 100.0)
                            RouteProfilePoint(
                                distanceKm = distKm,
                                elevationMeters = elevMeters,
                                socPercent = soc,
                                locationName = when (index) {
                                    0 -> origin.ifBlank { "مبدا" }
                                    state.elevationProfilePoints.lastIndex -> destination.ifBlank { "مقصد" }
                                    else -> null
                                }
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

                ArrivalSocCard(
                    predictedSoc = arrSoc,
                    conservativeSoc = state.conservativeArrivalSocPercent ?: (arrSoc - 5.0).coerceAtLeast(0.0),
                    optimisticSoc = state.optimisticArrivalSocPercent ?: (arrSoc + 5.0).coerceAtMost(100.0),
                    totalEnergyKwh = state.totalEnergyKwh ?: 0.0,
                    reserveKwh = state.reserveEnergyKwh ?: 0.0,
                    isFeasible = state.isFeasibleWithoutCharging
                )

                TopographicEnergyCanvas(
                    profilePoints = profilePoints,
                    totalDistanceKm = totalDistKm,
                    departureSoc = departureSoc.toDouble(),
                    arrivalSoc = arrSoc
                )

                // Atmospheric & Wind Vector Radar Capsule
                WeatherRadarOverlay(
                    temperatureCelsius = 22.5,
                    windSpeedKmh = 24.0,
                    windHeadingDegrees = 215.0,
                    vehicleHeadingDegrees = 180.0,
                    airDensityKgM3 = 1.085,
                    weatherCondition = CorridorWeatherCondition.CLEAR_SUNNY,
                    estimatedRangeImpactPercent = -3.8,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.recommendedChargingStops.isNotEmpty()) {
                    ChargingStopRecommendationCard(
                        stations = state.recommendedChargingStops
                    )
                }

                if (state.explanations.isNotEmpty()) {
                    DecisionExplanationCard(
                        explanations = state.explanations
                    )
                }

                Button(
                    onClick = onStartNavigation,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🚀 " + strings.startNavigationAction,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color.Black else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}
