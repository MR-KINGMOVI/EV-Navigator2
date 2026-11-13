package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.OpacityTokens

/**
 * Glass map overlay component designed for map UI elements
 *
 * @param modifier Modifier for the glass map overlay
 * @param color Color of the glass overlay (defaults to dark glass for better contrast on maps)
 * @param shape Shape of the glass overlay
 * @param content Content to display inside the glass overlay
 */
@Composable
fun GlassMapOverlay(
    modifier: Modifier = Modifier,
    color: Color = ColorTokens.GlassSurfaceDark,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(color, shape)
            .alpha(OpacityTokens.MapOverlayOpacity)
    ) {
        content()
    }
}

/**
 * Glass bottom sheet component designed for map overlays and information panels
 *
 * @param modifier Modifier for the glass bottom sheet
 * @param color Color of the glass bottom sheet (defaults to dark glass)
 * @param shape Shape of the glass bottom sheet
 * @param content Content to display inside the glass bottom sheet
 */
@Composable
fun GlassBottomSheet(
    modifier: Modifier = Modifier,
    color: Color = ColorTokens.GlassSurfaceDark,
    shape: Shape = com.evnavigator.iran.designsystem.tokens.ShapeTokens.CornerLarge,
    content: @Composable BoxScope.() -> Unit
) {
    GlassMapOverlay(
        modifier = modifier,
        color = color,
        shape = shape,
        content = content
    )
}

/**
 * Glass top bar component for map UI
 *
 * @param modifier Modifier for the glass top bar
 * @param color Color of the glass top bar (defaults to dark glass)
 * @param shape Shape of the glass top bar
 * @param content Content to display inside the glass top bar
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    color: Color = ColorTokens.GlassSurfaceDark,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit
) {
    GlassMapOverlay(
        modifier = modifier,
        color = color,
        shape = shape,
        content = content
    )
}