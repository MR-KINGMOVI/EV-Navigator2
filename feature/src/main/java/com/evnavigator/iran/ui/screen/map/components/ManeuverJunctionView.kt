package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Direction branch for a complex highway junction interchange.
 */
data class JunctionSignboard(
    val title: String,
    val subtitle: String? = null,
    val roadCode: String? = null,
    val isTargetRoute: Boolean,
    val arrowDirection: LaneDirection = LaneDirection.STRAIGHT,
    val isFreewayGreen: Boolean = true
)

/**
 * Ultra-Realistic 3D Perspective Junction View for Highway Interchanges.
 * Recreates the visual perspective of overhead gantry signage (تابلوهای پل هوایی) and road lane forks,
 * giving drivers absolute certainty at highway bifurcations.
 */
@Composable
fun ManeuverJunctionView(
    interchangeName: String,
    signboards: List<JunctionSignboard>,
    distanceToForkMeters: Double,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "JunctionPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAnim"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = Color(0xF7080D1A),
        border = BorderStroke(1.2.dp, Color(0x6638BDF8)),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Interchange Name & Approach Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(ColorTokens.NeonCyan, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = interchangeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Text(
                    text = "${distanceToForkMeters.toInt()} متر تا تقاطع",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorTokens.ElectricGreen
                )
            }

            // 2. Realistic Overhead Gantry Signboards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                signboards.forEach { sign ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (sign.isFreewayGreen) {
                                    if (sign.isTargetRoute)
                                        Brush.verticalGradient(listOf(Color(0xFF007A3D), Color(0xFF004D25)))
                                    else
                                        Brush.verticalGradient(listOf(Color(0xFF1E3A2B), Color(0xFF112219)))
                                } else {
                                    if (sign.isTargetRoute)
                                        Brush.verticalGradient(listOf(Color(0xFF005BAA), Color(0xFF00386B)))
                                    else
                                        Brush.verticalGradient(listOf(Color(0xFF192C42), Color(0xFF0D1724)))
                                }
                            )
                            .border(
                                width = if (sign.isTargetRoute) 1.5.dp else 1.dp,
                                color = if (sign.isTargetRoute) ColorTokens.NeonCyan else Color(0x40FFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!sign.roadCode.isNullOrBlank()) {
                                Text(
                                    text = sign.roadCode,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (sign.isTargetRoute) Color.White else Color(0xFF94A3B8)
                                )
                            }
                            Text(
                                text = sign.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (sign.isTargetRoute) Color.White else Color(0xFFCBD5E1),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (!sign.subtitle.isNullOrBlank()) {
                                Text(
                                    text = sign.subtitle,
                                    fontSize = 9.sp,
                                    color = if (sign.isTargetRoute) Color(0xFFE2E8F0) else Color(0xFF64748B),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 3. 3D Perspective Road Canvas with Fork & Pulsing Neon Guide Arrow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0F1D))
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Vanishing Horizon
                    val horizonY = h * 0.15f

                    // Road Surface
                    val roadPath = Path().apply {
                        moveTo(w * 0.45f, horizonY)
                        lineTo(w * 0.55f, horizonY)
                        lineTo(w * 0.95f, h)
                        lineTo(w * 0.05f, h)
                        close()
                    }
                    drawPath(
                        path = roadPath,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                            startY = horizonY,
                            endY = h
                        )
                    )

                    // Right Fork Split
                    val forkPath = Path().apply {
                        moveTo(w * 0.52f, horizonY + (h * 0.35f))
                        lineTo(w * 0.85f, h)
                        lineTo(w * 0.55f, h)
                        close()
                    }
                    drawPath(path = forkPath, color = Color(0xFF162032))

                    // Dashed Road Markings (Perspective)
                    for (i in 0..4) {
                        val fraction = i / 4f
                        val dashY = horizonY + ((h - horizonY) * (fraction * fraction))
                        val dashH = 4.dp.toPx() + (fraction * 10.dp.toPx())
                        val dashW = 1.5.dp.toPx() + (fraction * 3.dp.toPx())
                        val centerX = w * 0.5f - ((0.5f - fraction) * 10f)

                        drawRect(
                            color = Color(0xCCFFFFFF),
                            topLeft = Offset(centerX - dashW / 2, dashY),
                            size = androidx.compose.ui.geometry.Size(dashW, dashH)
                        )
                    }

                    // Active Target Arrow (Glowing Neon Cyan with Pulse)
                    val targetSign = signboards.firstOrNull { sign -> sign.isTargetRoute }
                    val isForkRight = targetSign?.arrowDirection in listOf(LaneDirection.RIGHT, LaneDirection.SLIGHT_RIGHT)

                    val arrowPath = Path().apply {
                        if (isForkRight) {
                            moveTo(w * 0.38f, h * 0.88f)
                            quadraticBezierTo(w * 0.5f, h * 0.6f, w * 0.72f, h * 0.38f)
                        } else {
                            moveTo(w * 0.5f, h * 0.9f)
                            lineTo(w * 0.5f, h * 0.35f)
                        }
                    }

                    // Outer Glow
                    drawPath(
                        path = arrowPath,
                        color = ColorTokens.ElectricGreen.copy(alpha = 0.4f * (1f - pulseProgress * 0.5f)),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Core Laser Line
                    drawPath(
                        path = arrowPath,
                        brush = Brush.verticalGradient(listOf(ColorTokens.NeonCyan, ColorTokens.ElectricGreen)),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
