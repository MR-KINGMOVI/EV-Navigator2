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
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.catalog.DomainConnectorType
import com.evnavigator.iran.designsystem.components.GlassSurface
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.ui.screen.charging.ChargingDiscoveryViewModel
import com.evnavigator.iran.ui.screen.charging.ConnectorFilter
import com.evnavigator.iran.ui.screen.charging.PowerFilter

/**
 * Next-Generation Charging Station Radar & Discovery Screen.
 * Features ultra-luxury Frosted Glassmorphism, multi-tier connector & power filters,
 * verified Iranian DC fast-charging network database, and full Dark/Light theme reactivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingScreen(
    viewModel: ChargingDiscoveryViewModel = hiltViewModel(),
    onNavigateToRoute: ((Double, Double) -> Unit)? = null,
    onNavigateToDetail: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = com.evnavigator.iran.designsystem.locale.appStrings()
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 85.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.chargingNetworkTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.chargingNetworkSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Real-Time Infrastructure Cockpit Glass Banner
            item {
                ChargingCockpitGlassBanner(
                    totalStations = uiState.totalStationsCount,
                    fastStations = uiState.fastStationsCount,
                    totalPorts = uiState.totalPortsCount,
                    isDark = isDark,
                    onAddStationClick = { viewModel.setCreateDialogOpen(true) }
                )
            }

            // Glassmorphic Search Bar
            item {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = EVIcons.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    text = "جستجوی شهر، ایستگاه یا آزادراه (تهران، مهروماه، صفه...)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChanged("") },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = EVIcons.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips: Connector Protocol
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "نوع درگاه و پروتکل شارژ:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ConnectorFilter.values()) { filter ->
                            val isSelected = uiState.connectorFilter == filter
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else if (isDark) Color(0xD9101726) else Color(0xEBFFFFFF),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable { viewModel.onConnectorFilterSelected(filter) }
                            ) {
                                Text(
                                    text = filter.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Filter Chips: Power Tier
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "توان خروجی دیسپنسر:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(PowerFilter.values()) { filter ->
                            val isSelected = uiState.powerFilter == filter
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else if (isDark) Color(0xD9101726) else Color(0xEBFFFFFF),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable { viewModel.onPowerFilterSelected(filter) }
                            ) {
                                Text(
                                    text = filter.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Results Counter Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ایستگاه‌های منطبق (${uiState.filteredStations.size} ایستگاه)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.searchQuery.isNotBlank() || uiState.connectorFilter != ConnectorFilter.ALL || uiState.powerFilter != PowerFilter.ALL) {
                        TextButton(
                            onClick = {
                                viewModel.onSearchQueryChanged("")
                                viewModel.onConnectorFilterSelected(ConnectorFilter.ALL)
                                viewModel.onPowerFilterSelected(PowerFilter.ALL)
                            }
                        ) {
                            Text("پاکسازی فیلترها", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Stations List
            if (uiState.filteredStations.isEmpty() && !uiState.isLoading) {
                item {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "هیچ ایستگاهی با فیلترهای انتخابی یافت نشد.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    viewModel.onConnectorFilterSelected(ConnectorFilter.ALL)
                                    viewModel.onPowerFilterSelected(PowerFilter.ALL)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("نمایش تمام ایستگاه‌ها", fontWeight = FontWeight.Bold, color = if (isDark) Color.Black else Color.White)
                            }
                        }
                    }
                }
            } else {
                items(uiState.filteredStations, key = { it.station.id }) { stationWithPorts ->
                    StationGlassCard(
                        stationWithPorts = stationWithPorts,
                        isDark = isDark,
                        onViewDetails = { viewModel.onSelectStation(stationWithPorts) },
                        onNavigate = {
                            onNavigateToRoute?.invoke(
                                stationWithPorts.station.latitude,
                                stationWithPorts.station.longitude
                            )
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Detail Modal Dialog
    uiState.selectedStation?.let { selected ->
        StationDetailGlassDialog(
            stationWithPorts = selected,
            isDark = isDark,
            onDismiss = { viewModel.onSelectStation(null) },
            onDelete = if (selected.station.source == "USER_CUSTOM") {
                { viewModel.deleteStation(selected.station.id) }
            } else null,
            onNavigateToFullDetail = if (onNavigateToDetail != null) {
                { onNavigateToDetail(selected.station.id) }
            } else null
        )
    }

    // Create Custom Station Dialog
    if (uiState.isCreateDialogOpen) {
        CreateCustomStationDialog(
            onDismiss = { viewModel.setCreateDialogOpen(false) },
            onConfirm = { name, op, city, prov, addr, lat, lon, power, conns, is247 ->
                val primaryConn = conns.firstOrNull() ?: ConnectorType.CCS_COMBO_2
                viewModel.createCustomStation(name, op, city, prov, addr, lat, lon, power, primaryConn, is247)
            }
        )
    }
}

