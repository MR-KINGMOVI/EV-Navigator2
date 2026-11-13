package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens
import com.evnavigator.iran.designsystem.tokens.SpacingTokens

/**
 * Secondary button component for less prominent actions in the UI
 *
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for styling and positioning
 * @param enabled Whether the button is enabled
 * @param text Text to display on the button
 * @param icon Icon to display on the button (optional)
 * @param shape Shape of the button
 * @param colors Button color configuration
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = ShapeTokens.CornerMedium,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = ColorTokens.Surface,
        contentColor = ColorTokens.Primary,
        disabledContainerColor = ColorTokens.SurfaceVariant,
        disabledContentColor = ColorTokens.TextDisabled
    )
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ColorTokens.Primary)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                icon()
                Spacer(modifier = Modifier.width(SpacingTokens.XSmall))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
