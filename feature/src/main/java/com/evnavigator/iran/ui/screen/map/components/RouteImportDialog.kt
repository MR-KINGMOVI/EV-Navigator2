package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evnavigator.iran.designsystem.components.PrimaryButton
import com.evnavigator.iran.designsystem.components.SecondaryButton
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.domain.routing.importer.PresetIranRoute
import com.evnavigator.iran.domain.routing.importer.RouteFileImporter

enum class ImportTab(val title: String) {
    PRESETS("مسیرهای منتخب ایران"),
    PASTE_FILE("ایمپورت GPX / GeoJSON"),
    COORDINATES("ورود دستی مختصات")
}

@Composable
fun RouteImportDialog(
    onDismiss: () -> Unit,
    onSelectPreset: (PresetIranRoute) -> Unit,
    onImportRawContent: (content: String, customName: String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(ImportTab.PRESETS) }
    var rawText by remember { mutableStateOf("") }
    var routeNameInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val presets = remember { RouteFileImporter.getPresetRoutes() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = ColorTokens.CockpitBackground,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ColorTokens.NeonCyan.copy(alpha = 0.6f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ColorTokens.NeonCyan.copy(alpha = 0.15f))
                                .border(1.dp, ColorTokens.NeonCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = EVIcons.Route,
                                contentDescription = "Route Importer",
                                tint = ColorTokens.NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "سامانه هوشمند ناوبری و تحلیل مسیر ایران",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = ColorTokens.TextPrimaryOnDark
                            )
                            Text(
                                text = "سازگار با GPX، GeoJSON، نشان، بلد و مسیرهای ترانزیتی ایران",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTokens.TextSecondaryOnDark,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = EVIcons.Close,
                            contentDescription = "Close",
                            tint = ColorTokens.TextSecondaryOnDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorTokens.CockpitSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ImportTab.values().forEach { tab ->
                        val isSelected = tab == selectedTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ColorTokens.NeonCyan.copy(alpha = 0.20f) else Color.Transparent)
                                .clickable {
                                    selectedTab = tab
                                    errorMessage = null
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ColorTokens.NeonCyan else ColorTokens.TextSecondaryOnDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error Message if any
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { error ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = ColorTokens.CrimsonAlert.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ColorTokens.CrimsonAlert)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorTokens.CrimsonAlert,
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        ImportTab.PRESETS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(presets) { preset ->
                                    PresetRouteCard(
                                        preset = preset,
                                        onSelect = {
                                            onSelectPreset(preset)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }

                        ImportTab.PASTE_FILE, ImportTab.COORDINATES -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = routeNameInput,
                                    onValueChange = { routeNameInput = it },
                                    label = { Text("نام دلخواه مسیر (اختیاری)", color = ColorTokens.TextSecondaryOnDark) },
                                    placeholder = { Text("مثال: سفر تهران به ماسوله", color = ColorTokens.TextMutedOnDark) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ColorTokens.NeonCyan,
                                        unfocusedBorderColor = ColorTokens.CockpitCardBorder,
                                        focusedTextColor = ColorTokens.TextPrimaryOnDark,
                                        unfocusedTextColor = ColorTokens.TextPrimaryOnDark
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = rawText,
                                    onValueChange = { rawText = it },
                                    label = {
                                        Text(
                                            if (selectedTab == ImportTab.PASTE_FILE) "محتوای فایل GPX یا GeoJSON" else "خطوط مختصات (Lat, Lon)",
                                            color = ColorTokens.TextSecondaryOnDark
                                        )
                                    },
                                    placeholder = {
                                        Text(
                                            if (selectedTab == ImportTab.PASTE_FILE)
                                                "متن کامل فایل xml یا json مسیر را اینجا جای‌گذاری (Paste) کنید..."
                                            else
                                                "35.6997, 51.3380\n34.6401, 50.8764\n32.6370, 51.6830",
                                            color = ColorTokens.TextMutedOnDark,
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ColorTokens.NeonCyan,
                                        unfocusedBorderColor = ColorTokens.CockpitCardBorder,
                                        focusedTextColor = ColorTokens.TextPrimaryOnDark,
                                        unfocusedTextColor = ColorTokens.TextPrimaryOnDark
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                PrimaryButton(
                                    text = "تحلیل توپوگرافی و تزریق به نقشه",
                                    onClick = {
                                        if (rawText.isBlank()) {
                                            errorMessage = "لطفاً متن مسیر را وارد کنید."
                                        } else {
                                            val parsed = RouteFileImporter.autoDetectAndParse(rawText, routeNameInput.ifBlank { null })
                                            parsed.onSuccess {
                                                onImportRawContent(rawText, routeNameInput.ifBlank { null })
                                                onDismiss()
                                            }.onFailure { err ->
                                                errorMessage = err.message ?: "خطا در خواندن فرمت فایل"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRouteCard(
    preset: PresetIranRoute,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        color = ColorTokens.CockpitSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorTokens.CockpitCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ColorTokens.TextPrimaryOnDark
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ColorTokens.NeonCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorTokens.NeonCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${preset.distanceKm.toInt()} km",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorTokens.NeonCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTokens.TextSecondaryOnDark,
                fontSize = 12.sp
            )

            // Charging Stops Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = EVIcons.Charging,
                    contentDescription = null,
                    tint = ColorTokens.ElectricGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = preset.chargingStopsSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTokens.ElectricGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
