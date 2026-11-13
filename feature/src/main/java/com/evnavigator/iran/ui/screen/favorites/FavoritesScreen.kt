package com.evnavigator.iran.ui.screen.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.components.EmptyStateView
import com.evnavigator.iran.designsystem.tokens.ColorTokens

data class FavoriteStationItem(
    val id: String,
    val name: String,
    val city: String,
    val powerKw: Double,
    val connector: String
)

data class FavoriteRouteItem(
    val id: String,
    val title: String,
    val origin: String,
    val destination: String,
    val distanceKm: Int
)

/**
 * Dedicated Favorites Hub for EV Navigator Iran.
 * Manages driver's favorite charging stations and preferred road corridors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToRoute: ((String, String) -> Unit)? = null,
    onNavigateToStation: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ایستگاه‌های منتخب", "مسیرهای پرکاربرد")

    val favoriteStations = remember {
        mutableStateListOf(
            FavoriteStationItem("station_qom_mehromah", "ایستگاه فوق‌سریع مجتمع مهر و ماه", "قم", 120.0, "CCS2, GB/T"),
            FavoriteStationItem("station_tehran_milad", "ایستگاه شارژ برج میلاد", "تهران", 60.0, "CCS2, GB/T, Type 2"),
            FavoriteStationItem("station_isfahan_sofeh", "ایستگاه پارک کوهستانی صفه", "اصفهان", 60.0, "CCS2, Type 2")
        )
    }

    val favoriteRoutes = remember {
        mutableStateListOf(
            FavoriteRouteItem("route_tehran_isfahan", "تهران (میدان آزادی) ⇄ اصفهان (پل خواجو)", "تهران", "اصفهان", 448),
            FavoriteRouteItem("route_tehran_rasht", "تهران (برج میلاد) ⇄ رشت (میدان شهرداری)", "تهران", "رشت", 325)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "علاقه‌مندی‌ها و مسیرهای منتخب",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "←", fontSize = 20.sp, color = ColorTokens.NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ColorTokens.NeonCyan
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) ColorTokens.NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            if (selectedTab == 0) {
                // Stations tab
                if (favoriteStations.isEmpty()) {
                    EmptyStateView(
                        title = "ایستگاه منتخبی افزوده نشده است",
                        description = "با کلیک روی آیکون ستاره در صفحه ایستگاه‌های شارژ، می‌توانید ایستگاه‌های پرتکرار خود را در این بخش نشانه‌گذاری کنید.",
                        iconGlyph = "⭐"
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(favoriteStations) { station ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = station.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${station.city} • ${station.powerKw.toInt()} kW • ${station.connector}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { onNavigateToStation?.invoke() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.Primary)
                                    ) {
                                        Text("ناوبری", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Routes tab
                if (favoriteRoutes.isEmpty()) {
                    EmptyStateView(
                        title = "مسیر منتخبی وجود ندارد",
                        description = "مسیرهای پرتردد خود را می‌توانید برای برنامه‌ریزی یک‌کلیکه ذخیره نمایید.",
                        iconGlyph = "🛣️"
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(favoriteRoutes) { route ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToRoute?.invoke(route.origin, route.destination) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = route.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${route.distanceKm} کیلومتر مسافت بین‌شهری",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { onNavigateToRoute?.invoke(route.origin, route.destination) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.VoltBlue)
                                    ) {
                                        Text("طرح سفر", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
