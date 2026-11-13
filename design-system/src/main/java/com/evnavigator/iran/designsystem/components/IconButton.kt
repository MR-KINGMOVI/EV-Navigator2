package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens

/**
 * Icon button component for compact actions
 *
 * @param onClick Callback when icon button is clicked
 * @param modifier Modifier for styling and positioning
 * @param enabled Whether the button is enabled
 * @param icon Icon to display on the button
 * @param shape Shape of the button
 * @param colors Button color configuration
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
    shape: Shape = ShapeTokens.CornerMedium,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        containerColor = ColorTokens.Surface,
        contentColor = ColorTokens.Primary,
        disabledContainerColor = ColorTokens.SurfaceVariant,
        disabledContentColor = ColorTokens.TextDisabled
    )
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.clip(shape),
        enabled = enabled,
        colors = colors
    ) {
        icon()
    }
}
