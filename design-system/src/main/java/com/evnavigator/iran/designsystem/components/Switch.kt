package com.evnavigator.iran.designsystem.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Switch component for boolean toggles
 *
 * @param checked Whether the switch is currently checked
 * @param onCheckedChange Callback when switch state changes
 * @param modifier Modifier for styling and positioning
 * @param enabled Whether the switch is enabled
 * @param colors Switch color configuration
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = ColorTokens.Primary,
        uncheckedThumbColor = ColorTokens.SurfaceVariant,
        checkedTrackColor = ColorTokens.Primary.copy(alpha = 0.5f),
        uncheckedTrackColor = ColorTokens.SurfaceVariant.copy(alpha = 0.5f),
        disabledCheckedThumbColor = ColorTokens.TextDisabled,
        disabledUncheckedThumbColor = ColorTokens.SurfaceVariant,
        disabledCheckedTrackColor = ColorTokens.SurfaceVariant.copy(alpha = 0.12f),
        disabledUncheckedTrackColor = ColorTokens.SurfaceVariant.copy(alpha = 0.12f)
    )
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors
    )
}
