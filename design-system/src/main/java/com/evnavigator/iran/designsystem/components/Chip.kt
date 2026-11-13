package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens
import com.evnavigator.iran.designsystem.tokens.SpacingTokens

/**
 * Chip component for selection and tagging
 *
 * @param selected Whether the chip is selected
 * @param onClick Callback when chip is clicked
 * @param modifier Modifier for styling and positioning
 * @param enabled Whether the chip is enabled
 * @param label Text to display on the chip
 * @param icon Icon to display on the chip (optional)
 * @param shape Shape of the chip
 * @param colors Chip color configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = ShapeTokens.CornerSmall,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = ColorTokens.Surface,
        labelColor = ColorTokens.TextPrimary,
        selectedContainerColor = ColorTokens.Primary,
        selectedLabelColor = ColorTokens.OnPrimary,
        disabledContainerColor = ColorTokens.SurfaceVariant,
        disabledLabelColor = ColorTokens.TextDisabled
    )
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        label = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    icon()
                    Spacer(modifier = Modifier.width(SpacingTokens.XSmall))
                }
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}
