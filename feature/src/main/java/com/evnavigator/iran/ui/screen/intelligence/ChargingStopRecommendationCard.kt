package com.evnavigator.iran.ui.screen.intelligence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.components.Card
import com.evnavigator.iran.domain.intelligence.charging.EvaluatedChargingStation

@Composable
fun ChargingStopRecommendationCard(
    stations: List<EvaluatedChargingStation>,
    modifier: Modifier = Modifier
) {
    if (stations.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ایستگاه‌های شارژ پیشنهادی در مسیر",
                style = MaterialTheme.typography.titleMedium
            )

            stations.forEach { evaluated ->
                val c = evaluated.candidate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = c.stationName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "توان شارژ موثر: ${evaluated.effectiveChargingPowerKw.toInt()} kW | انحراف از مسیر: ${(c.detourDistanceMeters / 1000).toInt()} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = evaluated.recommendationSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (evaluated.isSafelyReachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
