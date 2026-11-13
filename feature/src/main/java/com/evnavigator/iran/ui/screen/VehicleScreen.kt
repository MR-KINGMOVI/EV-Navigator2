package com.evnavigator.iran.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.domain.model.catalog.ConnectorNormalizer
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.designsystem.components.GlassSurface
import com.evnavigator.iran.designsystem.components.PrimaryButton
import com.evnavigator.iran.designsystem.components.SecondaryButton
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.vehicle.CustomVehicleForm
import com.evnavigator.iran.ui.screen.vehicle.VehicleGarageViewModel
import kotlin.math.roundToInt

/**
 * Next-Generation Vehicle Garage & Fleet Management Screen.
 * Features ultra-luxury Frosted Glassmorphism, comprehensive Iranian BEV & PHEV catalog,
 * active vehicle powertrain telemetry, and full Dark/Light theme reactivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    viewModel: VehicleGarageViewModel = hiltViewModel(),
    onNavigateToDetail: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = com.evnavigator.iran.designsystem.locale.appStrings()
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val activeVehicle: DomainVehicle? = uiState.vehicles.find { it.id == uiState.activeVehicleId }
        ?: uiState.vehicles.firstOrNull()

    // Filter vehicles based on search, connector, and powertrain
    val filteredVehicles: List<DomainVehicle> = uiState.vehicles.filter { v ->
        val matchesQuery = uiState.searchQuery.isBlank() ||
                v.name.contains(uiState.searchQuery, ignoreCase = true) ||
                v.manufacturer.contains(uiState.searchQuery, ignoreCase = true) ||
                v.model.contains(uiState.searchQuery, ignoreCase = true)

        val matchesConnector = when (uiState.selectedConnectorFilter) {
            "CCS2" -> v.connectorTypes.any { it.contains("CCS2", ignoreCase = true) }
            "GB/T" -> v.connectorTypes.any { it.contains("GB/T", ignoreCase = true) || it.contains("GBT", ignoreCase = true) }
            "Type 2" -> v.connectorTypes.any { it.contains("Type 2", ignoreCase = true) || it.contains("TYPE_2", ignoreCase = true) }
            else -> true
        }

        val matchesPowertrain = when (uiState.selectedPowertrainFilter) {
            "BEV" -> v.powertrainType.equals("BEV", ignoreCase = true)
            "PHEV" -> v.powertrainType.equals("PHEV", ignoreCase = true) || v.powertrainType.equals("EREV", ignoreCase = true)
            else -> true
        }

        matchesQuery && matchesConnector && matchesPowertrain
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Header: Fleet Status & Custom Vehicle Action ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = strings.garageTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "مدیریت ناوگان و پروفایل مصرف انرژی خودرو",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Custom Vehicle Button
                    Button(
                        onClick = { viewModel.openCustomVehicleDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "+ افزودن خودرو",
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.Black else Color.White
                        )
                    }
                }
            }

            // Status message banner if any
            uiState.statusMessage?.let { msg ->
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "✕",
                                modifier = Modifier
                                    .clickable { viewModel.dismissStatusMessage() }
                                    .padding(4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- 1. Active Vehicle Cockpit Hero Card ---
            if (activeVehicle != null) {
                item {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "خودروی فعال در سامانه ناوبری",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = activeVehicle.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeVehicle.manufacturer} ${activeVehicle.model} • ${activeVehicle.modelYear}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Powertrain Badge
                                val isPhev = activeVehicle.powertrainType.equals("PHEV", ignoreCase = true) ||
                                        activeVehicle.powertrainType.equals("EREV", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isPhev) Color(0x33FFB300) else Color(0x3300E5FF),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isPhev) ColorTokens.AmberAlert else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = if (isPhev) "PHEV هیبرید پلاگین" else "BEV تمام الکتریکی",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPhev) ColorTokens.AmberAlert else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Dynamic Live Telemetry Gauge
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isDark) Color(0x33101828) else Color(0x1A000000),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "وضعیت شارژ باتری فعلی (SOC)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${uiState.currentSocPercent.roundToInt()}%",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (uiState.currentSocPercent <= 20.0) ColorTokens.CrimsonAlert
                                            else if (uiState.currentSocPercent <= 40.0) ColorTokens.AmberAlert
                                            else if (isDark) ColorTokens.ElectricGreen else Color(0xFF00897B)
                                        )
                                    }

                                    // SOC Slider
                                    Slider(
                                        value = uiState.currentSocPercent.toFloat(),
                                        onValueChange = { viewModel.updateBatterySoc(it.toDouble()) },
                                        valueRange = 5f..100f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    // Estimated Range Calculation
                                    val usableKwh = activeVehicle.usableBatteryCapacityWh / 1000.0
                                    val currentEnergyKwh = (usableKwh * (uiState.currentSocPercent / 100.0))
                                    val consumptionWhKm = (activeVehicle.baselineConsumptionWhPer100Km ?: 16_500.0) / 100.0
                                    val estimatedRangeKm = if (consumptionWhKm > 0) ((currentEnergyKwh * 1000.0) / consumptionWhKm).roundToInt() else 0

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "پیمایش تقریبی باقی‌مانده:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$estimatedRangeKm کیلومتر (بر اساس مصرف استاندارد)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Powertrain Specs Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SpecPill(
                                    label = "ظرفیت باتری",
                                    value = "${(activeVehicle.usableBatteryCapacityWh / 1000.0).roundToInt()} kWh",
                                    modifier = Modifier.weight(1f),
                                    isDark = isDark
                                )
                                SpecPill(
                                    label = "حداکثر DC",
                                    value = "${activeVehicle.dcChargingMaxPowerKw?.roundToInt() ?: "-"} kW",
                                    modifier = Modifier.weight(1f),
                                    isDark = isDark
                                )
                                SpecPill(
                                    label = "شارژ خانگی AC",
                                    value = "${activeVehicle.acChargingMaxPowerKw?.roundToInt() ?: "7"} kW",
                                    modifier = Modifier.weight(1f),
                                    isDark = isDark
                                )
                                SpecPill(
                                    label = "نوع درگاه",
                                    value = ConnectorNormalizer.normalizeConnectorStrings(activeVehicle.connectorTypes).firstOrNull() ?: "CCS2",
                                    modifier = Modifier.weight(1f),
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
            }

            // --- 2. Vehicle Search & Filter Cockpit ---
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("جستجو در بین خودروهای برقی ایران (مثال: آریزو، اسکای‌ول، دیپال)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedPowertrainFilter == "ALL",
                                onClick = { viewModel.onPowertrainFilterSelected("ALL") },
                                label = { Text("همه پیشرانه‌ها") }
                            )
                            FilterChip(
                                selected = uiState.selectedPowertrainFilter == "BEV",
                                onClick = { viewModel.onPowertrainFilterSelected("BEV") },
                                label = { Text("تمام برقی (BEV)") }
                            )
                            FilterChip(
                                selected = uiState.selectedPowertrainFilter == "PHEV",
                                onClick = { viewModel.onPowertrainFilterSelected("PHEV") },
                                label = { Text("پلاگین هیبرید (PHEV)") }
                            )
                        }

                        // Connector Filter Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedConnectorFilter == "ALL",
                                onClick = { viewModel.onConnectorFilterSelected("ALL") },
                                label = { Text("همه کانکتورها") }
                            )
                            FilterChip(
                                selected = uiState.selectedConnectorFilter == "CCS2",
                                onClick = { viewModel.onConnectorFilterSelected("CCS2") },
                                label = { Text("CCS Combo 2") }
                            )
                            FilterChip(
                                selected = uiState.selectedConnectorFilter == "GB/T",
                                onClick = { viewModel.onConnectorFilterSelected("GB/T") },
                                label = { Text("GB/T چینی") }
                            )
                        }
                    }
                }
            }

            // --- 3. Vehicle Catalog List ---
            item {
                Text(
                    text = "کاتالوگ خودروهای الکتریکی تایید شده در ایران (${filteredVehicles.size} مدل)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(filteredVehicles, key = { it.id }) { vehicle ->
                val isActive = vehicle.id == uiState.activeVehicleId
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = vehicle.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    val isPhev = vehicle.powertrainType.equals("PHEV", ignoreCase = true) || vehicle.powertrainType.equals("EREV", ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPhev) Color(0x33FFB300) else Color(0x3300E5FF),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPhev) ColorTokens.AmberAlert else MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            text = if (isPhev) "PHEV هیبرید" else "BEV برقی",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPhev) ColorTokens.AmberAlert else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "${vehicle.manufacturer} • سال ${vehicle.modelYear} • وزن ${vehicle.curbMassKg.toInt()} kg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (isDark) ColorTokens.ElectricGreen else Color(0xFF00A854)).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "✓ خودروی فعال",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) ColorTokens.ElectricGreen else Color(0xFF007E33),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Specs line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "باتری: ${(vehicle.usableBatteryCapacityWh / 1000.0).roundToInt()} kWh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "توان DC: ${vehicle.dcChargingMaxPowerKw?.toInt() ?: "-"} kW",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "کانکتور: ${vehicle.connectorTypes.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isActive) {
                                Button(
                                    onClick = { viewModel.selectActiveVehicle(vehicle.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("انتخاب به عنوان خودروی فعال", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
                                }
                            }
                            OutlinedButton(
                                onClick = { onNavigateToDetail?.invoke(vehicle.id) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text("مشخصات", fontSize = 11.5.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.duplicateVehicle(vehicle) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text("کپی", fontSize = 11.5.sp)
                            }
                            if (vehicle.source == "USER_INPUT") {
                                OutlinedButton(
                                    onClick = { viewModel.deleteVehicle(vehicle.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTokens.CrimsonAlert),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text("حذف", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Custom Vehicle Creation Modal Dialog ---
        if (uiState.isCustomVehicleDialogOpen) {
            val form = uiState.customForm
            Dialog(onDismissRequest = { viewModel.closeCustomVehicleDialog() }) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "تعریف خودروی الکتریکی سفارشی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { viewModel.onCustomFormChanged(form.copy(name = it)) },
                            label = { Text("نام نمایشی خودرو (مثال: تسلا مدل Y یا ری‌را برقی)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = form.manufacturer,
                                onValueChange = { viewModel.onCustomFormChanged(form.copy(manufacturer = it)) },
                                label = { Text("سازنده") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = form.model,
                                onValueChange = { viewModel.onCustomFormChanged(form.copy(model = it)) },
                                label = { Text("مدل") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = if (form.batteryUsableKwh > 0.0) form.batteryUsableKwh.toString() else "",
                                onValueChange = {
                                    val num = it.toDoubleOrNull() ?: 0.0
                                    viewModel.onCustomFormChanged(form.copy(batteryUsableKwh = num))
                                },
                                label = { Text("ظرفیت باتری مفید (kWh)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = if (form.maxDcPowerKw > 0.0) form.maxDcPowerKw.toString() else "",
                                onValueChange = {
                                    val num = it.toDoubleOrNull() ?: 0.0
                                    viewModel.onCustomFormChanged(form.copy(maxDcPowerKw = num))
                                },
                                label = { Text("حداکثر توان DC (kW)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = if (form.baselineConsumptionWhPer100Km > 0.0) (form.baselineConsumptionWhPer100Km / 100.0).toString() else "",
                                onValueChange = {
                                    val num = it.toDoubleOrNull() ?: 0.0
                                    viewModel.onCustomFormChanged(form.copy(baselineConsumptionWhPer100Km = num * 100.0))
                                },
                                label = { Text("مصرف مبنا (Wh/km)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = if (form.massKg > 0.0) form.massKg.toString() else "",
                                onValueChange = {
                                    val num = it.toDoubleOrNull() ?: 0.0
                                    viewModel.onCustomFormChanged(form.copy(massKg = num))
                                },
                                label = { Text("وزن خالص (kg)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.closeCustomVehicleDialog() }) {
                                Text("انصراف")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.saveCustomVehicle() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("ذخیره خودرو", fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0x22101828) else Color(0x14000000),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
