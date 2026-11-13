package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ElevationTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens

/**
 * Ultra-Luxury Glass surface component providing authentic frosted acrylic depth.
 * Preserves 100% internal child text/icon opacity while rendering a frosted glass background
 * with specular boundary refraction.
 *
 * @param modifier Modifier for the glass surface
 * @param color Explicit glass color override (defaults to dynamic theme glass)
 * @param shape Shape of the glass surface
 * @param elevation Elevation level for shadow effects
 * @param content Content to display inside the glass surface
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shape: Shape = RectangleShape,
    elevation: Float = ElevationTokens.Level1,
    content: @Composable BoxScope.() -> Unit
) {
    val currentTheme by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(currentTheme, isSystemInDarkTheme())

    val glassBackground = if (color != Color.Unspecified) {
        color
    } else if (isDark) {
        Color(0xD9101726) // Deep obsidian frosted glass (85% opacity)
    } else {
        Color(0xEBFFFFFF) // Pure frosted crystal white glass (92% opacity)
    }

    val glassBorderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0x5500E5FF),
                Color(0x1A00E5FF),
                Color(0x3300E676)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0x660091EA),
                Color(0x2294A3B8),
                Color(0x3300C853)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(glassBackground, shape)
            .border(width = 1.dp, brush = glassBorderBrush, shape = shape)
    ) {
        content()
    }
}

/**
 * Glass card component with frosted glass effect and large rounded corners.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shape: Shape = ShapeTokens.CornerLarge,
    elevation: Float = ElevationTokens.Level1,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        color = color,
        shape = shape,
        elevation = elevation,
        content = content
    )
}

/**
 * Glass button component with frosted glass effect and medium rounded corners.
 */
@Composable
fun GlassButton(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shape: Shape = ShapeTokens.CornerMedium,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        color = color,
        shape = shape,
        content = content
    )
}

/**
 * Glass chip component with frosted glass effect and small rounded corners.
 */
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shape: Shape = ShapeTokens.CornerSmall,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        color = color,
        shape = shape,
        content = content
    )
}
