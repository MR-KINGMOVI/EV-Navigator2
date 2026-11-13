package com.evnavigator.iran.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Automotive Cockpit Telemetry Dial for Battery SOC and Power Flow.
 * Features smooth animated arcs, gradient glow, and clear EV telemetry readouts
 * adapted dynamically for both Dark Glass and Crystal Light themes.
 */
@Composable
fun TelemetryDial(
    socPercent: Double,
    estimatedRangeKm: Int,
    batteryKwh: Double,
    modifier: Modifier = Modifier,
    dialSize: Dp = 160.dp,
    strokeWidth: Dp = 12.dp,
    isCharging: Boolean = false
) {
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val animatedProgress by animateFloatAsState(
        targetValue = (socPercent.toFloat() / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "socProgress"
    )

    val arcColor = when {
        isCharging -> if (isDark) ColorTokens.NeonCyan else ColorTokens.CyanAccentLight
        socPercent >= 50.0 -> if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854)
        socPercent >= 20.0 -> ColorTokens.AmberAlert
        else -> ColorTokens.CrimsonAlert
    }

    val trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier.size(dialSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val width = size.width
            val height = size.height
            val strokePx = strokeWidth.toPx()

            // Background track (240 degrees arc)
            drawArc(
                color = trackColor,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                size = Size(width, height)
            )

            // Active glowing gradient arc
            val sweep = 240f * animatedProgress
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to arcColor.copy(alpha = 0.5f),
                        0.5f to arcColor,
                        1.0f to arcColor,
                        center = Offset(width / 2, height / 2)
                    ),
                    startAngle = 150f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    size = Size(width, height)
                )
            }
        }

        // Center Telemetry Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${socPercent.toInt()}%",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "$estimatedRangeKm km برد",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            )
            Text(
                text = "%.1f kWh".format(batteryKwh),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
            if (isCharging) {
                Text(
                    text = "در حال شارژ ⚡",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
