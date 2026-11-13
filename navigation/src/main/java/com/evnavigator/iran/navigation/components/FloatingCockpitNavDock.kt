package com.evnavigator.iran.navigation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.navigation.PrimaryNavItem

/**
 * Ultra-Luxury Glassmorphic Floating Cockpit Navigation Dock.
 * Inspired by Porsche Taycan, Apple CarPlay Next-Gen, and Cyberpunk HUD interfaces.
 *
 * Supports reactive Dark Glass and Crystal Light Glass modes.
 *
 * Features:
 * - Floating suspended capsule pill (14dp margin from screen edges)
 * - Multi-stop frosted acrylic/crystal glass background with glowing boundary highlights
 * - Animated spring-loaded glowing indicator capsule behind active selection
 * - Live dynamic telemetry badges (e.g. 110+ fast-chargers, active route pulse dot)
 * - Micro-interaction scale-tap dynamics and high-contrast bilingual typography
 */
@Composable
fun FloatingCockpitNavDock(
    items: List<PrimaryNavItem>,
    currentRoute: String?,
    onItemSelected: (PrimaryNavItem) -> Unit,
    chargerBadgeCount: String = "110+",
    hasActiveRoute: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strings = com.evnavigator.iran.designsystem.locale.appStrings()
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val dockBackground = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF00D131F), // Frosted Titanium Midnight
                Color(0xF8080C14)  // Deep Obsidian Core
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF2FFFFFF), // Daylight Frosted White Crystal
                Color(0xF7F1F5F9)  // Subtle Frosted Ice Base
            )
        )
    }

    val dockBorder = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x6600E5FF), // Subtle neon cyan top edge highlight
                Color(0x1FFFFFFF), // Mid glass boundary
                Color(0x3300E676)  // Emerald bottom trace
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x660091EA), // Daylight cyan top specular highlight
                Color(0x33CBD5E1), // Mid platinum glass boundary
                Color(0x4400C853)  // Emerald bottom trace
            )
        )
    }

    val ambientColor = if (isDark) Color(0x4000E5FF) else Color(0x200091EA)
    val spotColor = if (isDark) Color(0x8000E5FF) else Color(0x300091EA)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing shadow boundary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = ambientColor,
                    spotColor = spotColor
                )
                .clip(RoundedCornerShape(26.dp))
                .background(dockBackground)
                .border(
                    width = 1.2.dp,
                    brush = dockBorder,
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = when (item.route) {
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Map.route ->
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Map.route
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Plan.route ->
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Plan.route ||
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.TripPlanner.route
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Chargers.route ->
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Chargers.route ||
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Charging.route
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Garage.route ->
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Garage.route ||
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Vehicles.route
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Activity.route ->
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Activity.route ||
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.Home.route ||
                            currentRoute == com.evnavigator.iran.navigation.EVNavigatorDestinations.History.route
                        else -> currentRoute == item.route
                    }

                    val badge = when (item.route) {
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Chargers.route -> chargerBadgeCount
                        else -> null
                    }

                    val showPulse = when (item.route) {
                        com.evnavigator.iran.navigation.EVNavigatorDestinations.Plan.route -> hasActiveRoute
                        else -> false
                    }

                    DockItem(
                        item = item,
                        title = item.titleExtractor(strings),
                        isSelected = isSelected,
                        isDark = isDark,
                        badge = badge,
                        showPulse = showPulse,
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    item: PrimaryNavItem,
    title: String,
    isSelected: Boolean,
    isDark: Boolean,
    badge: String?,
    showPulse: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Smooth animation transitions
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "dock_scale"
    )

    val activeIconColor = if (isDark) ColorTokens.NeonCyan else ColorTokens.CyanAccentLight
    val inactiveIconColor = if (isDark) Color(0xFF75859E) else Color(0xFF64748B)

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeIconColor else inactiveIconColor,
        animationSpec = tween(220),
        label = "dock_icon_color"
    )

    val activeTextColor = if (isDark) ColorTokens.TextPrimaryOnDark else ColorTokens.TextPrimaryOnLight
    val inactiveTextColor = if (isDark) Color(0xFF627187) else Color(0xFF475569)

    val textColor by animateColorAsState(
        targetValue = if (isSelected) activeTextColor else inactiveTextColor,
        animationSpec = tween(220),
        label = "dock_text_color"
    )

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250),
        label = "dock_bg_alpha"
    )

    val capsuleBackground = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x3800E5FF).copy(alpha = 0.28f * backgroundAlpha),
                Color(0x1400E5FF).copy(alpha = 0.12f * backgroundAlpha)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x280091EA).copy(alpha = 0.30f * backgroundAlpha),
                Color(0x120091EA).copy(alpha = 0.14f * backgroundAlpha)
            )
        )
    }

    val capsuleBorder = if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0x5500E5FF).copy(alpha = 0.45f * backgroundAlpha),
                Color(0x2200E676).copy(alpha = 0.30f * backgroundAlpha)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0x660091EA).copy(alpha = 0.50f * backgroundAlpha),
                Color(0x3300C853).copy(alpha = 0.35f * backgroundAlpha)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glowing capsule active indicator
        if (backgroundAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(capsuleBackground)
                    .border(
                        width = 1.dp,
                        brush = capsuleBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }

        Column(
            modifier = Modifier
                .scale(animatedScale)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(23.dp)
                )

                // Micro Badge (e.g. 110+ chargers)
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = 10.dp, y = (-5).dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ColorTokens.EnergyAmber, Color(0xFFFF9100))
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                // Route pulse dot
                if (showPulse) {
                    Box(
                        modifier = Modifier
                            .offset(x = 5.dp, y = (-2).dp)
                            .size(6.dp)
                            .background(ColorTokens.ElectricGreen, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = title,
                fontSize = if (isSelected) 10.5.sp else 9.5.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
