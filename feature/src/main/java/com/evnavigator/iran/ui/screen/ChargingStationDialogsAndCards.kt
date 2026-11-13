package com.evnavigator.iran.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.designsystem.components.GlassSurface
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.domain.model.catalog.ChargingStationWithPortsDomainModel as DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.ConnectorTypeDomain as DomainConnectorType

/**
 * V12.5 Extracted Collaborator: Charging station glass banner, cards, and detail/creation dialogs.
 * Enforces explicit coordinate and power validation in [CreateCustomStationDialog] instead of
 * silently substituting Tehran (35.6892, 51.3890) or 60.0 kW.
 */
@Composable
internal fun ChargingCockpitGlassBanner(
    totalStations: Int,
    fastStations: Int,
    totalPorts: Int,
    isDark: Boolean,
    onAddStationClick: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "وضعیت شبکه شارژ خودروهای برقی ایران",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "پایگاه داده زنده و تأییدشده سراسر کشور",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = onAddStationClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Text("+ ثبت ایستگاه", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BannerStatItem(title = "کل ایستگاه‌ها", value = "$totalStations", subtitle = "سراسر کشور", isDark = isDark)
                BannerStatItem(title = "شارژ سریع DC", value = "$fastStations", subtitle = "۵۰ تا ۱۲۰ کیلووات", isDark = isDark)
                BannerStatItem(title = "پورت‌های فعال", value = "$totalPorts", subtitle = "CCS2 / GB/T / T2", isDark = isDark)
            }
        }
    }
}

@Composable
private fun BannerStatItem(title: String, value: String, subtitle: String, isDark: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun StationGlassCard(
    stationWithPorts: DomainChargingStationWithPorts,
    isDark: Boolean,
    onViewDetails: () -> Unit,
    onNavigate: () -> Unit
) {
    val station = stationWithPorts.station
    val ports = stationWithPorts.ports
    val maxPower = ports.mapNotNull { it.powerKw?.takeIf { p -> p > 0.0 } }.maxOrNull() ?: 0.0
    val isUltraFast = maxPower >= 100.0
    val isFast = maxPower >= 50.0

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.canonicalName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${station.operatorName ?: "اپراتور ثبت‌نشده"} • ${station.city ?: "ایران"}، ${station.province ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val badgeColor = when {
                    isUltraFast -> if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854)
                    isFast -> MaterialTheme.colorScheme.primary
                    else -> ColorTokens.AmberAlert
                }
                val badgeText = when {
                    isUltraFast -> "${maxPower.toInt()} kW فوق‌سریع"
                    isFast -> "${maxPower.toInt()} kW سریع"
                    maxPower > 0.0 -> "${maxPower.toInt()} kW AC"
                    else -> "توان نامشخص"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            station.address?.let { addr ->
                Text(
                    text = addr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uniqueConnectors = ports.map { it.connectorType }.distinct()
                for (connector in uniqueConnectors) {
                    val label = when (connector) {
                        DomainConnectorType.CCS_COMBO_2 -> "CCS2"
                        DomainConnectorType.GB_T_DC -> "GB/T DC"
                        DomainConnectorType.GB_T_AC -> "GB/T AC"
                        DomainConnectorType.TYPE_2 -> "Type 2"
                        else -> connector.name
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                }

                if (station.is247) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854)).copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "۲۴ ساعته",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) ColorTokens.ElectricGreen else Color(0xFF007E33),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${ports.size} درگاه",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onViewDetails,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("جزئیات فنی کامل", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onNavigate,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("مسیریابی", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
                }
            }
        }
    }
}

@Composable
internal fun StationDetailGlassDialog(
    stationWithPorts: DomainChargingStationWithPorts,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onNavigateToFullDetail: (() -> Unit)? = null
) {
    val station = stationWithPorts.station
    val ports = stationWithPorts.ports

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = station.canonicalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "مشخصات فنی و درگاه‌های شارژ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    DetailRow("اپراتور شبکه:", station.operatorName ?: "نامشخص")
                    DetailRow("موقعیت:", "${station.city}، ${station.province}")
                    DetailRow("آدرس کامل:", station.address ?: "ثبت نشده")
                    DetailRow("مختصات GPS:", "%.4f, %.4f".format(station.latitude, station.longitude))
                    DetailRow("ساعات کارکرد:", if (station.is247) "شبانه روزی (24/7)" else station.openingHoursJson ?: "روزانه")
                    DetailRow("منبع داده و اعتبار:", "${station.source} • ضریب اطمینان ${(station.confidence * 100).toInt()}%")
                }

                item {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "درگاه‌ها و نازل‌های شارژ (${ports.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(ports) { port ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = port.connectorType.name,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = port.powerKw?.takeIf { it > 0.0 }?.let { "${it.toInt()} kW" } ?: "توان نامشخص",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                            val voltageText = port.voltage?.takeIf { it > 0.0 }?.let { "${it.toInt()} V" } ?: "نامشخص"
                            val currentText = port.current?.takeIf { it > 0.0 }?.let { "${it.toInt()} A" } ?: "نامشخص"
                            Text(
                                text = "نوع برق: ${if (port.isDc) "مستقیم DC" else "متناوب AC"} • ولتاژ: $voltageText • جریان: $currentText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("بستن", fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (onNavigateToFullDetail != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onNavigateToFullDetail()
                    }) {
                        Text("مشاهده در صفحه اختصاصی", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateCustomStationDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        operator: String,
        city: String,
        province: String,
        address: String,
        latitude: Double,
        longitude: Double,
        maxPowerKw: Double,
        connectors: List<ConnectorType>,
        is247: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var powerText by remember { mutableStateOf("") }
    var is247 by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val selectedConnectors = remember { mutableStateListOf(ConnectorType.CCS_COMBO_2, ConnectorType.GB_T_DC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت ایستگاه شارژ جدید", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    item {
                        Text(
                            text = validationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; validationError = null },
                        label = { Text("نام ایستگاه (الزامی)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = operator,
                        onValueChange = { operator = it; validationError = null },
                        label = { Text("نام اپراتور (مپنا، هتل، شهرداری...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it; validationError = null },
                            label = { Text("شهر") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = province,
                            onValueChange = { province = it; validationError = null },
                            label = { Text("استان") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("آدرس کامل یا کیلومتر جاده") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = { latitudeText = it; validationError = null },
                            label = { Text("عرض جغرافیایی (Lat)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = { longitudeText = it; validationError = null },
                            label = { Text("طول جغرافیایی (Lon)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = powerText,
                        onValueChange = { powerText = it; validationError = null },
                        label = { Text("حداکثر توان دیسپنسر (kW)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latitudeText.trim().toDoubleOrNull()
                    val lon = longitudeText.trim().toDoubleOrNull()
                    val pwr = powerText.trim().toDoubleOrNull()
                    when {
                        name.isBlank() -> validationError = "وارد کردن نام ایستگاه الزامی است."
                        lat == null || lat !in 24.0..40.5 -> validationError = "عرض جغرافیایی معتبر در محدوده ایران (24.0 تا 40.5) وارد کنید."
                        lon == null || lon !in 43.5..64.5 -> validationError = "طول جغرافیایی معتبر در محدوده ایران (43.5 تا 64.5) وارد کنید."
                        pwr == null || pwr !in 3.0..400.0 -> validationError = "توان معتبر دیسپنسر (۳ تا ۴۰۰ کیلووات) را وارد کنید."
                        else -> onConfirm(
                            name.trim(),
                            operator.trim().ifBlank { "ثبت‌شده توسط کاربر" },
                            city.trim().ifBlank { "نامشخص" },
                            province.trim().ifBlank { "نامشخص" },
                            address.trim(),
                            lat,
                            lon,
                            pwr,
                            selectedConnectors.toList(),
                            is247
                        )
                    }
                }
            ) {
                Text("ذخیره در پایگاه داده")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
