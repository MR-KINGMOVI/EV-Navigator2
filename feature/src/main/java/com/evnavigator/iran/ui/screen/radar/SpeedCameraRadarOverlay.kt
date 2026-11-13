package com.evnavigator.iran.ui.screen.radar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.core.routing.engine.TrafficRadarEngine
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import kotlin.math.roundToInt

/**
 * Ultra-Glassmorphic Highway Speed Camera & Traffic Safety Radar HUD Overlay.
 * Appears dynamically when vehicle approaches an enforcement gantry, mobile radar, or dangerous mountain pass.
 */
@Composable
fun SpeedCameraRadarOverlay(
    activeAlert: TrafficRadarEngine.ActiveRadarAlert?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeAlert != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        if (activeAlert == null) return@AnimatedVisibility

        val hazard = activeAlert.hazard
        val isOverspeed = activeAlert.isOverspeeding
        val isCritical = activeAlert.alertSeverity == TrafficRadarEngine.AlertSeverity.CRITICAL

        val borderBrush = if (isOverspeed) {
            Brush.linearGradient(
                listOf(
                    ColorTokens.CrimsonAlert,
                    Color(0xFFFF5252)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    ColorTokens.NeonCyan,
                    Color(0x3300E5FF)
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF2070A12), // Deep Obsidian Glass
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(if (isOverspeed) 2.dp else 1.dp, borderBrush, RoundedCornerShape(16.dp)),
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hazard Category Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOverspeed) ColorTokens.CrimsonAlert.copy(alpha = 0.2f)
                            else ColorTokens.NeonCyan.copy(alpha = 0.15f)
                        )
                        .border(
                            1.5.dp,
                            if (isOverspeed) ColorTokens.CrimsonAlert else ColorTokens.NeonCyan,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconText = when (hazard.type) {
                        TrafficRadarEngine.HazardType.SPEED_CAMERA_FIXED -> "📷"
                        TrafficRadarEngine.HazardType.AVERAGE_SPEED_ZONE_START,
                        TrafficRadarEngine.HazardType.AVERAGE_SPEED_ZONE_END -> "⏱️"
                        TrafficRadarEngine.HazardType.POLICE_SPEED_TRAP -> "👮"
                        TrafficRadarEngine.HazardType.DANGEROUS_MOUNTAIN_PASS -> "🏔️"
                        TrafficRadarEngine.HazardType.TUNNEL_APPROACH -> "🚇"
                        else -> "⚠️"
                    }
                    Text(text = iconText, fontSize = 20.sp)
                }

                // Hazard Description & Distance
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = hazard.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverspeed) ColorTokens.CrimsonAlert else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val distanceMeters = activeAlert.distanceToHazardMeters.roundToInt()
                    val distText = if (distanceMeters >= 1000) {
                        "${String.format("%.1f", distanceMeters / 1000.0)} کیلومتر جلوتر"
                    } else {
                        "$distanceMeters متر جلوتر"
                    }

                    Text(
                        text = "$distText • ${hazard.highwayName}",
                        fontSize = 11.sp,
                        color = ColorTokens.TextSecondaryOnDark
                    )

                    if (isOverspeed) {
                        Text(
                            text = "کاهش سرعت! سرعت شما: ${activeAlert.currentSpeedKmh.roundToInt()} km/h (مجاز: ${hazard.speedLimitKmh})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTokens.CrimsonAlert
                        )
                    }
                }

                // Speed Limit Sign
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.5.dp, Color(0xFFE53935), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${hazard.speedLimitKmh}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
