package com.evnavigator.iran.ui.screen.intelligence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.components.Card

@Composable
fun ArrivalSocCard(
    predictedSoc: Double?,
    conservativeSoc: Double?,
    optimisticSoc: Double?,
    totalEnergyKwh: Double?,
    reserveKwh: Double?,
    isFeasible: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "پیش‌بینی شارژ در مقصد",
                    style = MaterialTheme.typography.titleMedium
                )
                val statusColor = if (isFeasible) Color(0xFF2E7D32) else Color(0xFFC62828)
                Text(
                    text = if (isFeasible) "قابل انجام بدون توقف" else "نیازمند توقف شارژ",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }

            if (predictedSoc != null) {
                Text(
                    text = "${predictedSoc.toInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (predictedSoc > 20.0) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                )

                if (conservativeSoc != null && optimisticSoc != null) {
                    Text(
                        text = "بازه حاشیه اطمینان: ${conservativeSoc.toInt()}% (بدترین حالت) تا ${optimisticSoc.toInt()}% (بهترین حالت)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "انرژی مصرفی: ${String.format("%.1f", totalEnergyKwh ?: 0.0)} kWh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "ذخیره اطمینان: ${String.format("%.1f", reserveKwh ?: 0.0)} kWh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = "داده‌های مسیر برای محاسبه در دسترس نیست.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
