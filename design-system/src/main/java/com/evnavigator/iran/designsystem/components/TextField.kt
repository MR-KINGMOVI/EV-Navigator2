package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens

/**
 * Outlined text field component for user input
 *
 * @param value Current value of the text field
 * @param onValueChange Callback when text value changes
 * @param modifier Modifier for styling and positioning
 * @param label Label to display for the text field
 * @param placeholder Placeholder text when empty
 * @param shape Shape of the text field
 * @param enabled Whether the text field is enabled
 * @param readOnly Whether the text field is read-only
 * @param isError Whether the text field is in error state
 * @param supportingText Supporting text to display
 * @param visualTransformation Visual transformation for the text
 * @param keyboardOptions Keyboard options for input
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    shape: Shape = ShapeTokens.CornerMedium,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = if (label != null) { { Text(label) } } else { null },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else { null },
        shape = shape,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ColorTokens.Primary,
            unfocusedBorderColor = ColorTokens.SurfaceVariant,
            disabledBorderColor = ColorTokens.SurfaceVariant,
            errorBorderColor = ColorTokens.Error,
            focusedLabelColor = ColorTokens.Primary,
            unfocusedLabelColor = ColorTokens.TextSecondary,
            errorLabelColor = ColorTokens.Error,
            cursorColor = ColorTokens.Primary,
            focusedTrailingIconColor = ColorTokens.Primary,
            unfocusedTrailingIconColor = ColorTokens.TextSecondary
        )
    )
}
