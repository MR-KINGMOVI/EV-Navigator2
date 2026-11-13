package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.evnavigator.iran.designsystem.tokens.ShapeTokens

/**
 * Glass panel component - a flexible frosted acrylic glass surface for dashboard and overlay UI.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shape: Shape = RectangleShape,
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
 * Glass segmented control component for map UI and settings.
 */
@Composable
fun GlassSegmentedControl(
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
