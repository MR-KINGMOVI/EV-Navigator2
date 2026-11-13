package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ElevationTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens

/**
 * Standard card component with reactive frosted glass styling for both Dark and Light modes.
 *
 * @param modifier Modifier for styling and positioning
 * @param shape Shape of the card
 * @param elevation Elevation level for shadow effects
 * @param content Content to display inside the card
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeTokens.CornerMedium,
    elevation: Float = ElevationTokens.Level1,
    content: @Composable () -> Unit
) {
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val containerColor = if (isDark) ColorTokens.CockpitSurface else ColorTokens.CrystalGlassCard
    val contentColor = if (isDark) ColorTokens.TextPrimaryOnDark else ColorTokens.TextPrimaryOnLight
    val borderColor = if (isDark) ColorTokens.CockpitCardBorder else ColorTokens.CrystalCardBorder

    androidx.compose.material3.Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}
