package com.evnavigator.iran.ui.screen.offline

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evnavigator.iran.domain.model.catalog.DomainOfflinePackageMetadata
import com.evnavigator.iran.designsystem.components.PrimaryButton
import com.evnavigator.iran.designsystem.components.SecondaryButton
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Production-Grade Offline Map Tile & Vector Topography Manager for EV Navigator Iran.
 * Enables zero-connectivity long-distance journeys across Iranian mountain passes and deserts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapManagerScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: OfflineMapManagerViewModel = hiltViewModel()
) {
    val mapPacks by viewModel.packages.collectAsState()

    val totalDownloadedMb = mapPacks.filter { it.isDownloaded }.sumOf { it.sizeMb }

    val glassBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x6600E5FF),
            Color(0x1A00E5FF)
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "مدیریت نقشه‌های آفلاین استان‌ها",
                        fontWeight = FontWeight.Bold,
                        color = ColorTokens.TextPrimaryOnDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "←", fontSize = 22.sp, color = ColorTokens.NeonCyan, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xF20B0F19))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF070B12))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, glassBorderBrush),
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
                        Column {
                            Text(
                                text = "حافظه اشغال‌شده تایل‌های نقشه",
                                style = MaterialTheme.typography.titleSmall,
                                color = ColorTokens.TextSecondaryOnDark
                            )
                            Text(
                                text = "$totalDownloadedMb مگابایت",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = ColorTokens.NeonCyan
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = ColorTokens.NeonCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "💾", fontSize = 20.sp)
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = (totalDownloadedMb / 400f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ColorTokens.VoltBlue,
                        trackColor = Color(0xFF1E293B)
                    )

                    Text(
                        text = "تمامی نقشه‌ها و عوارض توپوگرافی پس از نصب، بدون نیاز به اینترنت و سیم‌کارت در دسترس کامل خواهند بود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTokens.TextMutedOnDark,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "بسته‌های استانی شبکه شریانی و شارژ ایران",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mapPacks, key = { it.id }) { pack ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x33131B2E),
                        border = BorderStroke(1.dp, if (pack.isDownloaded) Color(0x6600E676) else Color(0x22FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = pack.nameFa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(text = "${pack.nameEn} • ${pack.sizeMb} MB", style = MaterialTheme.typography.labelSmall, color = ColorTokens.NeonCyan)
                                }

                                if (pack.isDownloaded) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ColorTokens.ElectricGreen.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, ColorTokens.ElectricGreen)
                                    ) {
                                        Text(text = "ذخیره شده ✓", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorTokens.ElectricGreen)
                                    }
                                } else if (pack.isDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ColorTokens.NeonCyan, strokeWidth = 2.5.dp)
                                } else {
                                    PrimaryButton(
                                        onClick = { viewModel.downloadPackage(pack.id) },
                                        text = "دانلود بسته",
                                        modifier = Modifier.height(36.dp)
                                    )
                                }
                            }

                            Text(text = pack.description, style = MaterialTheme.typography.bodySmall, color = ColorTokens.TextSecondaryOnDark)

                            // Progress bar if downloading
                            if (pack.isDownloading) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = pack.progressPercent / 100f,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = ColorTokens.NeonCyan,
                                        trackColor = Color(0x33131B2E)
                                    )
                                    Text(text = "در حال دریافت تایل‌های وکتور: ${pack.progressPercent}٪", fontSize = 10.sp, color = ColorTokens.NeonCyan)
                                }
                            }

                            // Delete button if downloaded
                            if (pack.isDownloaded) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "حذف کش این استان",
                                        fontSize = 11.sp,
                                        color = ColorTokens.CrimsonAlert,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { viewModel.deletePackage(pack.id) }
                                            .padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
