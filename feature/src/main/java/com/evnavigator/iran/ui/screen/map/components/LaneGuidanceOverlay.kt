package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Directional action for an individual highway lane.
 */
enum class LaneDirection {
    STRAIGHT,
    SLIGHT_RIGHT,
    RIGHT,
    HARD_RIGHT,
    SLIGHT_LEFT,
    LEFT,
    HARD_LEFT,
    UTURN
}

/**
 * State representation for a single lane in a multi-lane highway configuration.
 */
data class LaneConfiguration(
    val laneIndex: Int,
    val direction: LaneDirection,
    val isRecommended: Boolean,
    val isExitOnly: Boolean = false
)

/**
 * Premium Automotive Lane Guidance System for complex highway bifurcations and interchanges.
 * Provides glanceable lane recommendations with glowing emerald path arrows and high-contrast styling.
 */
@Composable
fun LaneGuidanceOverlay(
    lanes: List<LaneConfiguration>,
    distanceToManeuverMeters: Double,
    nextInstructionText: String,
    modifier: Modifier = Modifier
) {
    if (lanes.isEmpty()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        color = Color(0xF20B1222),
        border = BorderStroke(1.2.dp, Brush.horizontalGradient(
            listOf(
                ColorTokens.NeonCyan.copy(alpha = 0.6f),
                ColorTokens.ElectricGreen.copy(alpha = 0.8f),
                ColorTokens.NeonCyan.copy(alpha = 0.4f)
            )
        )),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Maneuver Distance & Instructions
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
                            .size(8.dp)
                            .background(ColorTokens.ElectricGreen, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = if (distanceToManeuverMeters < 1000)
                            "${distanceToManeuverMeters.toInt()} متر دیگر"
                        else
                            "${"%.1f".format(distanceToManeuverMeters / 1000.0)} کیلومتر دیگر",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorTokens.ElectricGreen
                    )
                }

                Text(
                    text = nextInstructionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Lane Arrow Array
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x66060A13), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                lanes.forEach { lane ->
                    LaneArrowBox(lane = lane)
                }
            }
        }
    }
}

@Composable
private fun LaneArrowBox(
    lane: LaneConfiguration,
    modifier: Modifier = Modifier
) {
    val activeColor = if (lane.isRecommended) ColorTokens.ElectricGreen else Color(0xFF64748B)
    val glowColor = if (lane.isRecommended) Color(0x6600E676) else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (lane.isRecommended) Color(0x2E00E676) else Color(0x1A1E293B))
            .border(
                1.dp,
                if (lane.isRecommended) ColorTokens.ElectricGreen else Color(0x40334155),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(modifier = Modifier.size(28.dp, 36.dp)) {
            val w = size.width
            val h = size.height

            when (lane.direction) {
                LaneDirection.STRAIGHT -> {
                    // Straight Arrow
                    val stemPath = Path().apply {
                        moveTo(w / 2f, h)
                        lineTo(w / 2f, h * 0.32f)
                    }
                    drawPath(
                        path = stemPath,
                        color = activeColor,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Head
                    val headPath = Path().apply {
                        moveTo(w * 0.2f, h * 0.42f)
                        lineTo(w / 2f, h * 0.12f)
                        lineTo(w * 0.8f, h * 0.42f)
                    }
                    drawPath(
                        path = headPath,
                        color = activeColor,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                LaneDirection.SLIGHT_RIGHT -> {
                    val path = Path().apply {
                        moveTo(w * 0.3f, h)
                        quadraticBezierTo(w * 0.35f, h * 0.5f, w * 0.75f, h * 0.3f)
                    }
                    drawPath(path = path, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    val head = Path().apply {
                        moveTo(w * 0.5f, h * 0.22f)
                        lineTo(w * 0.85f, h * 0.28f)
                        lineTo(w * 0.72f, h * 0.55f)
                    }
                    drawPath(path = head, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                LaneDirection.RIGHT, LaneDirection.HARD_RIGHT -> {
                    val path = Path().apply {
                        moveTo(w * 0.25f, h)
                        lineTo(w * 0.25f, h * 0.5f)
                        lineTo(w * 0.78f, h * 0.5f)
                    }
                    drawPath(path = path, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    val head = Path().apply {
                        moveTo(w * 0.6f, h * 0.32f)
                        lineTo(w * 0.88f, h * 0.5f)
                        lineTo(w * 0.6f, h * 0.68f)
                    }
                    drawPath(path = head, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                LaneDirection.SLIGHT_LEFT -> {
                    val path = Path().apply {
                        moveTo(w * 0.7f, h)
                        quadraticBezierTo(w * 0.65f, h * 0.5f, w * 0.25f, h * 0.3f)
                    }
                    drawPath(path = path, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    val head = Path().apply {
                        moveTo(w * 0.5f, h * 0.22f)
                        lineTo(w * 0.15f, h * 0.28f)
                        lineTo(w * 0.28f, h * 0.55f)
                    }
                    drawPath(path = head, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                LaneDirection.LEFT, LaneDirection.HARD_LEFT -> {
                    val path = Path().apply {
                        moveTo(w * 0.75f, h)
                        lineTo(w * 0.75f, h * 0.5f)
                        lineTo(w * 0.22f, h * 0.5f)
                    }
                    drawPath(path = path, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    val head = Path().apply {
                        moveTo(w * 0.4f, h * 0.32f)
                        lineTo(w * 0.12f, h * 0.5f)
                        lineTo(w * 0.4f, h * 0.68f)
                    }
                    drawPath(path = head, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                LaneDirection.UTURN -> {
                    val path = Path().apply {
                        moveTo(w * 0.65f, h)
                        lineTo(w * 0.65f, h * 0.4f)
                        arcTo(
                            androidx.compose.ui.geometry.Rect(w * 0.25f, h * 0.15f, w * 0.65f, h * 0.55f),
                            0f, -180f, false
                        )
                        lineTo(w * 0.25f, h * 0.6f)
                    }
                    drawPath(path = path, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    val head = Path().apply {
                        moveTo(w * 0.12f, h * 0.48f)
                        lineTo(w * 0.25f, h * 0.7f)
                        lineTo(w * 0.38f, h * 0.48f)
                    }
                    drawPath(path = head, color = activeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        if (lane.isExitOnly) {
            Text(
                text = "خروج",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B)
            )
        }
    }
}
