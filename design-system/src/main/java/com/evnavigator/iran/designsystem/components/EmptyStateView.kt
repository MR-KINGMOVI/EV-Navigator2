package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Automotive empty state view with structured Persian messaging and primary call to action.
 */
@Composable
fun EmptyStateView(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconGlyph: String = "⚡",
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorTokens.CockpitSurface, RoundedCornerShape(16.dp))
            .border(1.dp, ColorTokens.CockpitCardBorder, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = iconGlyph,
            fontSize = 36.sp
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ColorTokens.TextPrimaryOnDark,
            textAlign = TextAlign.Center
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = ColorTokens.TextSecondaryOnDark,
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.Primary)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Automotive error state view with diagnostics and recovery action.
 */
@Composable
fun ErrorStateView(
    errorMessage: String,
    modifier: Modifier = Modifier,
    errorTitle: String = "خطا در دریافت اطلاعات",
    retryLabel: String = "تلاش مجدد",
    onRetryClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorTokens.CockpitSurface, RoundedCornerShape(16.dp))
            .border(1.dp, ColorTokens.CrimsonAlert.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "⚠️", fontSize = 32.sp)

        Text(
            text = errorTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ColorTokens.CrimsonAlert,
            textAlign = TextAlign.Center
        )

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = ColorTokens.TextSecondaryOnDark,
            textAlign = TextAlign.Center
        )

        if (onRetryClick != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetryClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.CrimsonAlert)
            ) {
                Text(retryLabel, fontSize = 12.sp)
            }
        }
    }
}
