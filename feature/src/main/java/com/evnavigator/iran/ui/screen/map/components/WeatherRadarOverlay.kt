package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import kotlin.math.roundToInt

/**
 * Weather condition classification on Iranian EV corridors.
 */
enum class CorridorWeatherCondition {
    CLEAR_SUNNY,
    PARTLY_CLOUDY,
    RAIN_DRIZZLE,
    HEAVY_SNOW,
    MOUNTAIN_FOG,
    DESERT_DUST_STORM
}

/**
 * Comprehensive Weather, Wind Vector & Atmospheric Density Capsule.
 * Displays physical atmospheric telemetry directly impacting EV range:
 * - Wind velocity & headwind/crosswind azimuth
 * - Temperature delta & battery thermal heating/cooling penalty
 * - Air density (rho) at high plateau altitudes
 */
@Composable
fun WeatherRadarOverlay(
    temperatureCelsius: Double,
    windSpeedKmh: Double,
    windHeadingDegrees: Double,
    vehicleHeadingDegrees: Double,
    airDensityKgM3: Double,
    weatherCondition: CorridorWeatherCondition,
    estimatedRangeImpactPercent: Double,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Relative wind angle (0 = pure headwind, 180 = pure tailwind, 90 = crosswind)
    val relativeAngle = (windHeadingDegrees - vehicleHeadingDegrees + 360) % 360
    val isHeadwind = relativeAngle in 315.0..360.0 || relativeAngle in 0.0..45.0
    val isTailwind = relativeAngle in 135.0..225.0

    val windTypeDescription = when {
        isHeadwind -> "باد مخالف صریح"
        isTailwind -> "باد موافق کمکی"
        else -> "باد جانبی ناپایدار"
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded },
        color = Color(0xF20B1222),
        border = BorderStroke(1.dp, Color(0x4038BDF8)),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Glanceable Compact Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Weather Condition Emoji / Icon
                Text(
                    text = when (weatherCondition) {
                        CorridorWeatherCondition.CLEAR_SUNNY -> "☀️"
                        CorridorWeatherCondition.PARTLY_CLOUDY -> "⛅"
                        CorridorWeatherCondition.RAIN_DRIZZLE -> "🌧️"
                        CorridorWeatherCondition.HEAVY_SNOW -> "❄️"
                        CorridorWeatherCondition.MOUNTAIN_FOG -> "🌫️"
                        CorridorWeatherCondition.DESERT_DUST_STORM -> "🌪️"
                    },
                    fontSize = 15.sp
                )

                // Temperature
                Text(
                    text = "${temperatureCelsius.roundToInt()}°C",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                // Divider Dot
                Box(modifier = Modifier.size(3.dp).background(Color(0xFF64748B), CircleShape))

                // Wind Indicator with Direction Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💨 ${windSpeedKmh.roundToInt()} km/h",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTokens.NeonCyan
                    )
                    Text(
                        text = "➤",
                        fontSize = 10.sp,
                        color = if (isHeadwind) ColorTokens.CrimsonAlert else ColorTokens.ElectricGreen,
                        modifier = Modifier.rotate(relativeAngle.toFloat() + 180f)
                    )
                }

                // Range Impact Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (estimatedRangeImpactPercent < -5.0)
                                Color(0x33FF1744)
                            else if (estimatedRangeImpactPercent > 2.0)
                                Color(0x3300E676)
                            else
                                Color(0x3300E5FF)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${if (estimatedRangeImpactPercent > 0) "+" else ""}${estimatedRangeImpactPercent.roundToInt()}% برد",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (estimatedRangeImpactPercent < -5.0) ColorTokens.CrimsonAlert else if (estimatedRangeImpactPercent > 2.0) ColorTokens.ElectricGreen else ColorTokens.NeonCyan
                    )
                }
            }

            // Expanded Atmosphere Telemetry Drawer
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "جهت جریان باد:", fontSize = 11.sp, color = ColorTokens.TextSecondaryOnDark)
                        Text(text = "$windTypeDescription (${windHeadingDegrees.roundToInt()}°)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "چگالی هوای فلات (ρ):", fontSize = 11.sp, color = ColorTokens.TextSecondaryOnDark)
                        Text(text = "${"%.3f".format(airDensityKgM3)} kg/m³", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorTokens.NeonCyan)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "تأثیر باد و تهویه بر مصرف:", fontSize = 11.sp, color = ColorTokens.TextSecondaryOnDark)
                        Text(
                            text = if (estimatedRangeImpactPercent < 0) "کاهش برد به میزان ${kotlin.math.abs(estimatedRangeImpactPercent.roundToInt())}٪" else "شرایط آیرودینامیک بهینه",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (estimatedRangeImpactPercent < 0) ColorTokens.HighVoltageRed else ColorTokens.ElectricGreen
                        )
                    }
                }
            }
        }
    }
}
