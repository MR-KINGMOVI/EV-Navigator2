package com.evnavigator.iran.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.ShapeTokens

/**
 * Dialog component for displaying important information or prompts
 *
 * @param onDismissRequest Callback when dialog is dismissed
 * @param confirmButton Callback for confirm action
 * @param modifier Modifier for styling and positioning
 * @param title Title text for the dialog
 * @param text Content text for the dialog
 * @param shape Shape of the dialog
 * @param containerColor Container color for the dialog
 * @param titleColor Color for the dialog title
 * @param textColor Color for the dialog content
 * @param confirmButtonContent Content for the confirm button
 * @param dismissButtonContent Content for the dismiss button (optional)
 */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    confirmButton: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    text: String? = null,
    shape: Shape = ShapeTokens.CornerLarge,
    containerColor: Color = ColorTokens.Surface,
    titleColor: Color = ColorTokens.TextPrimary,
    textColor: Color = ColorTokens.TextSecondary,
    confirmButtonContent: @Composable () -> Unit,
    dismissButtonContent: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButtonContent,
        dismissButton = dismissButtonContent,
        modifier = modifier,
        title = if (title != null) { { Text(text = title, color = titleColor) } } else { null },
        text = if (text != null) { { Text(text = text, color = textColor) } } else { null },
        shape = shape,
        containerColor = containerColor,
        titleContentColor = titleColor,
        textContentColor = textColor,
        tonalElevation = AlertDialogDefaults.TonalElevation
    )
}
