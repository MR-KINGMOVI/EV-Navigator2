package com.evnavigator.iran.ui.screen.intelligence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.components.Card
import com.evnavigator.iran.domain.intelligence.config.IntelligenceConfigurationManager
import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceSettingsSection(
    configManager: IntelligenceConfigurationManager,
    modifier: Modifier = Modifier
) {
    val config by configManager.configState.collectAsState()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "تنظیمات هوش پیش‌بینی مصرف و حریم خصوصی",
                style = MaterialTheme.typography.titleMedium
            )

            // Personalization Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "کالیبراسیون و شخصی‌سازی رانندگی (Opt-in)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "محاسبه سبک رانندگی تنها به صورت محلی بر روی حافظه دستگاه",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.isPersonalizationEnabled,
                    onCheckedChange = { configManager.setPersonalizationEnabled(it) }
                )
            }

            Divider()

            // Trip History Logging
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ثبت خلاصه سفرهای گذشته",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "ذخیره خلاصه انرژی و مسافت بدون نگهداری نقاط دقیق مکانی GPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.isTripHistoryLoggingEnabled,
                    onCheckedChange = { configManager.setTripHistoryLoggingEnabled(it) }
                )
            }

            Divider()

            // Safety Reserve SOC
            Text(
                text = "حاشیه امن حداقل شارژ مقصد: ${config.minReserveSocPercent.toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = config.minReserveSocPercent.toFloat(),
                onValueChange = { configManager.updateReserveSoc(it.toDouble()) },
                valueRange = 5f..25f,
                steps = 4
            )

            Divider()

            // Route Optimization Priority
            Text(
                text = "اولویت انتخاب مسیر:",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteOptimizationObjective.values().forEach { obj ->
                    val isSelected = config.optimizationObjective == obj
                    FilterChip(
                        selected = isSelected,
                        onClick = { configManager.updateOptimizationObjective(obj) },
                        label = {
                            Text(
                                text = when (obj) {
                                    RouteOptimizationObjective.BALANCED -> "متعادل"
                                    RouteOptimizationObjective.MINIMUM_TIME -> "سریع‌ترین"
                                    RouteOptimizationObjective.MINIMUM_ENERGY -> "کم‌مصرف‌ترین"
                                    RouteOptimizationObjective.MAXIMUM_CONFIDENCE_AND_SAFETY -> "امن‌ترین"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    }
}
