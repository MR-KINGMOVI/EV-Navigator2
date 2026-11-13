package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import kotlin.math.roundToInt

data class RouteProfilePoint(
    val distanceKm: Double,
    val elevationMeters: Double,
    val socPercent: Double,
    val locationName: String? = null,
    val isChargingStop: Boolean = false
)

/**
 * Advanced Topography & Battery SOC Dynamic Visualization Engine.
 * Dual-axis synchronized canvas visualizing real elevation profile, battery discharge slope,
 * regenerative descent gains, charging stop injections, and interactive touch scrubbing.
 */
@Composable
fun TopographicEnergyCanvas(
    profilePoints: List<RouteProfilePoint>,
    totalDistanceKm: Double,
    departureSoc: Double = 85.0,
    arrivalSoc: Double = 28.0,
    modifier: Modifier = Modifier
) {
    var touchFraction by remember { mutableFloatStateOf(0.5f) }
    var isTouching by remember { mutableStateOf(false) }

    val points = remember(profilePoints) { profilePoints }

    if (points.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ColorTokens.CockpitSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorTokens.CockpitCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "پروفایل انرژی و توپوگرافی مسیر",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorTokens.TextPrimaryOnDark
                )
                Text(
                    text = "داده‌های توپوگرافی مسیر بارگذاری نشده یا مدل ارتفاعی آفلاین موجود نیست.",
                    fontSize = 12.sp,
                    color = ColorTokens.TextMutedOnDark
                )
            }
        }
        return
    }

    val selectedPoint = remember(touchFraction, points) {
        if (points.isEmpty()) null
        else {
            val targetDist = totalDistanceKm * touchFraction
            points.minByOrNull { kotlin.math.abs(it.distanceKm - targetDist) } ?: points.first()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ColorTokens.CockpitSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorTokens.CockpitCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // --- 1. Graph Header & Key Indicators ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(ColorTokens.NeonCyan, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "پروفایل انرژی و توپوگرافی مسیر",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorTokens.TextPrimaryOnDark
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(ColorTokens.ElectricGreen, RoundedCornerShape(2.dp)))
                        Text(
                            text = "باتری (SOC)",
                            fontSize = 11.sp,
                            color = ColorTokens.TextMutedOnDark
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF8A99AD), RoundedCornerShape(2.dp)))
                        Text(
                            text = "ارتفاع (متر)",
                            fontSize = 11.sp,
                            color = ColorTokens.TextMutedOnDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 2. Interactive Scrubber Inspection Card ---
            selectedPoint?.let { pt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x2600E5FF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کیلومتر ${pt.distanceKm.roundToInt()}${if (pt.locationName != null) " (${pt.locationName})" else ""}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTokens.NeonCyan
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (pt.elevationMeters.isNaN()) "ارتفاع: نامشخص" else "ارتفاع: ${pt.elevationMeters.roundToInt()} م",
                            fontSize = 12.sp,
                            color = ColorTokens.TextPrimaryOnDark
                        )
                        Text(
                            text = "شارژ: ${pt.socPercent.roundToInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pt.socPercent > 20) ColorTokens.ElectricGreen else ColorTokens.HighVoltageRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 3. Dual-Axis Visual Canvas ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isTouching = true
                                touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = { isTouching = false },
                            onDragCancel = { isTouching = false }
                        ) { change, _ ->
                            change.consume()
                            touchFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (points.size < 2) return@Canvas

                    val w = size.width
                    val h = size.height
                    val validElev = points.map { it.elevationMeters }.filterNot { it.isNaN() }

                    // A. Elevation Profile Area Fill & Path (rendered only if valid elevation exists)
                    if (validElev.size >= 2) {
                        val maxElev = (validElev.maxOrNull() ?: 2000.0).coerceAtLeast(500.0)
                        val minElev = (validElev.minOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val elevRange = (maxElev - minElev).coerceAtLeast(100.0)

                        val elevPath = Path()
                        val elevFillPath = Path()

                        points.forEachIndexed { i, pt ->
                            val x = (pt.distanceKm / totalDistanceKm).toFloat() * w
                            val elev = if (pt.elevationMeters.isNaN()) minElev else pt.elevationMeters
                            val elevNormalized = ((elev - minElev) / elevRange).toFloat().coerceIn(0f, 1f)
                            val y = h - (elevNormalized * (h * 0.55f)) - (h * 0.05f)

                            if (i == 0) {
                                elevPath.moveTo(x, y)
                                elevFillPath.moveTo(x, h)
                                elevFillPath.lineTo(x, y)
                            } else {
                                elevPath.lineTo(x, y)
                                elevFillPath.lineTo(x, y)
                            }
                        }
                        elevFillPath.lineTo(w, h)
                        elevFillPath.close()

                        // Draw Mountain Fill
                        drawPath(
                            path = elevFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x334A607A),
                                    Color(0x0A101622)
                                ),
                                startY = h * 0.4f,
                                endY = h
                            )
                        )

                        // Draw Mountain Line
                        drawPath(
                            path = elevPath,
                            color = Color(0xFF6B7E96),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // B. Battery SOC Curve Line
                    val socPath = Path()
                    val socGlowPath = Path()

                    points.forEachIndexed { i, pt ->
                        val x = (pt.distanceKm / totalDistanceKm).toFloat() * w
                        val socNormalized = (pt.socPercent / 100.0).toFloat().coerceIn(0f, 1f)
                        val y = h - (socNormalized * (h * 0.85f)) - (h * 0.05f)

                        if (i == 0) {
                            socPath.moveTo(x, y)
                            socGlowPath.moveTo(x, y)
                        } else {
                            socPath.lineTo(x, y)
                            socGlowPath.lineTo(x, y)
                        }
                    }

                    // Draw Outer Glow for SOC Line
                    drawPath(
                        path = socGlowPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x4000E5FF),
                                Color(0x4000E676)
                            )
                        ),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Draw Crisp Core SOC Line
                    drawPath(
                        path = socPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                ColorTokens.NeonCyan,
                                ColorTokens.ElectricGreen,
                                if (arrivalSoc > 20) ColorTokens.ElectricGreen else ColorTokens.HighVoltageRed
                            )
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // C. Render Charging Stops Along Path
                    points.filter { it.isChargingStop }.forEach { stop ->
                        val stopX = (stop.distanceKm / totalDistanceKm).toFloat() * w
                        val stopY = h - ((stop.socPercent / 100.0).toFloat() * (h * 0.85f)) - (h * 0.05f)

                        // Vertical Guide Line for Station
                        drawLine(
                            color = ColorTokens.EnergyAmber,
                            start = Offset(stopX, 0f),
                            end = Offset(stopX, h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Charger Icon Marker Dot
                        drawCircle(
                            color = ColorTokens.EnergyAmber,
                            radius = 6.dp.toPx(),
                            center = Offset(stopX, stopY)
                        )
                        drawCircle(
                            color = ColorTokens.CockpitBackground,
                            radius = 3.dp.toPx(),
                            center = Offset(stopX, stopY)
                        )
                    }

                    // D. Render Active Scrubber Line & Dot
                    val scrubX = touchFraction * w
                    drawLine(
                        color = Color.White,
                        start = Offset(scrubX, 0f),
                        end = Offset(scrubX, h),
                        strokeWidth = 1.dp.toPx()
                    )

                    selectedPoint?.let { pt ->
                        val scrubY = h - ((pt.socPercent / 100.0).toFloat() * (h * 0.85f)) - (h * 0.05f)
                        drawCircle(
                            color = ColorTokens.NeonCyan,
                            radius = 5.dp.toPx(),
                            center = Offset(scrubX, scrubY)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- 4. Bottom Distance X-Axis ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "مبدا: ۰ کیلومتر",
                    fontSize = 10.sp,
                    color = ColorTokens.TextMutedOnDark
                )
                Text(
                    text = "مسافت کل: ${totalDistanceKm.roundToInt()} کیلومتر",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorTokens.TextSecondaryOnDark
                )
                Text(
                    text = "مقصد: ورود با ${arrivalSoc.roundToInt()}%",
                    fontSize = 10.sp,
                    color = if (arrivalSoc > 20) ColorTokens.ElectricGreen else ColorTokens.HighVoltageRed
                )
            }
        }
    }
}


