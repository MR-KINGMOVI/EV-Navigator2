package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

data class ElevationPointDto(
    val distanceKm: Double,
    val elevationMeters: Double
)

/**
 * High-precision automotive elevation profile canvas for EV route topography analysis.
 * Visualizes climbing gradients and regenerative downhill corridors.
 */
@Composable
fun ElevationProfileCanvas(
    elevationPoints: List<ElevationPointDto>,
    totalDistanceKm: Double,
    totalClimbMeters: Double,
    totalDescentMeters: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ColorTokens.CockpitSurface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "پروفایل توپوگرافی و شیب مسیر",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ColorTokens.TextPrimaryOnDark
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "▲ ${totalClimbMeters.toInt()}m صعود",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.CrimsonAlert,
                    fontSize = 10.sp
                )
                Text(
                    text = "▼ ${totalDescentMeters.toInt()}m بازیابی",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.ElectricGreen,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (elevationPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "داده توپوگرافی در دسترس نیست",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTokens.TextMutedOnDark
                )
            }
        } else {
            val minEle = elevationPoints.minOf { it.elevationMeters }.coerceAtLeast(0.0)
            val maxEle = elevationPoints.maxOf { it.elevationMeters }.coerceAtLeast(minEle + 50.0)
            val eleRange = (maxEle - minEle).coerceAtLeast(1.0)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val width = size.width
                val height = size.height

                val path = Path()
                val fillPath = Path()

                fillPath.moveTo(0f, height)

                elevationPoints.forEachIndexed { index, pt ->
                    val x = (pt.distanceKm / totalDistanceKm.coerceAtLeast(1.0)).toFloat() * width
                    val normalizedY = ((pt.elevationMeters - minEle) / eleRange).toFloat()
                    val y = height - (normalizedY * (height - 10f)) - 5f

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw gradient under the curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ColorTokens.NeonCyan.copy(alpha = 0.25f),
                            ColorTokens.NeonCyan.copy(alpha = 0.02f)
                        )
                    )
                )

                // Draw stroke line
                drawPath(
                    path = path,
                    color = ColorTokens.NeonCyan,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minEle.toInt()}m (کمترین)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.TextSecondaryOnDark,
                    fontSize = 9.sp
                )
                Text(
                    text = "${totalDistanceKm.toInt()} km مسافت کل",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.TextMutedOnDark,
                    fontSize = 9.sp
                )
                Text(
                    text = "${maxEle.toInt()}m (بیشترین)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.TextSecondaryOnDark,
                    fontSize = 9.sp
                )
            }
        }
    }
}
