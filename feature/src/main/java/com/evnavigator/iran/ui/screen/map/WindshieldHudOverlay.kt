package com.evnavigator.iran.ui.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-Contrast Minimalist Windshield Projection HUD (Head-Up Display)
 * Designed for optical reflection onto a vehicle windshield with pitch-black (#000000)
 * background and phosphor telemetry (Speed, Upcoming Turn, Distance-to-Station).
 */
@Composable
fun WindshieldHudOverlay(
    speedKmh: Int,
    speedLimitKmh: Int,
    upcomingTurnArrow: String,
    upcomingTurnDistance: String,
    upcomingTurnInstructionFa: String,
    distanceToStationKm: Double,
    nextStationNameFa: String,
    nextStationPowerKw: Int,
    currentSocPercent: Int,
    arrivalSocPercent: Int,
    remainingRangeKm: Int,
    onExitHud: () -> Unit
) {
    var isMirrorFlipped by remember { mutableStateOf(false) }
    val phosphorCyan = Color(0xFF22D3EE)
    val phosphorEmerald = Color(0xFF34D399)
    val overspeed = speedKmh > speedLimitKmh

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top HUD Calibration Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "● WINDSHIELD PROJECTION HUD",
                color = phosphorCyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { isMirrorFlipped = !isMirrorFlipped },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMirrorFlipped) Color(0xFFFBBF24) else Color(0xFF0F172A)
                    )
                ) {
                    Text(
                        text = if (isMirrorFlipped) "حالت مستقیم" else "بازتاب آینه‌ای شیشه (Mirror)",
                        color = if (isMirrorFlipped) Color.Black else Color.White,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onExitHud,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("خروج از HUD", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Main Projection Stage (Flips horizontally when placed flat under windshield)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = if (isMirrorFlipped) -1f else 1f },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zone 1: Speedometer & Limit
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, phosphorCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("سرعت لحظه‌ای (KM/H)", color = Color.Gray, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = speedKmh.toString(),
                        color = if (overspeed) Color(0xFFF87171) else phosphorCyan,
                        fontSize = 56.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(3.dp, Color(0xFFEF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = speedLimitKmh.toString(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Zone 2: Upcoming Turn Maneuver
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, phosphorCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("مانور بعدی (UPCOMING TURN)", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$upcomingTurnArrow  $upcomingTurnDistance",
                    color = phosphorCyan,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = upcomingTurnInstructionFa,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Zone 3: Distance-to-Station & SoC
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, phosphorCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("فاصله تا ایستگاه شارژ", color = Color.Gray, fontSize = 11.sp)
                Text(
                    text = "${distanceToStationKm} km",
                    color = phosphorEmerald,
                    fontSize = 36.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "$nextStationNameFa (${nextStationPowerKw} kW)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "باتری: $currentSocPercent% • برد: $remainingRangeKm km • مقصد: $arrivalSocPercent%",
                    color = phosphorCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
