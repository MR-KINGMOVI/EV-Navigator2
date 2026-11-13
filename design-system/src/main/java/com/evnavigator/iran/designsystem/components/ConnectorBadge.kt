package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

enum class ConnectorBadgeType(val code: String, val standardLabel: String, val isDc: Boolean) {
    CCS2("CCS2", "اروپایی Combo 2", true),
    GB_T_DC("GB/T DC", "استاندارد ملی چین DC", true),
    GB_T_AC("GB/T AC", "استاندارد چین AC", false),
    TYPE_2("Type 2", "استاندارد Mennekes AC", false),
    CHAdeMO("CHAdeMO", "استاندارد ژاپن DC", true),
    TESLA_NACS("NACS", "استاندارد تسلا آمریکای شمالی", true)
}

/**
 * High-legibility automotive charging connector badge with rated power and electrical standard.
 */
@Composable
fun ConnectorBadge(
    type: ConnectorBadgeType,
    powerKw: Double,
    modifier: Modifier = Modifier,
    isAvailable: Boolean = true
) {
    val accentColor = if (type.isDc) {
        if (powerKw >= 100.0) ColorTokens.NeonCyan else ColorTokens.VoltBlue
    } else {
        ColorTokens.AmberAlert
    }

    val statusDotColor = if (isAvailable) ColorTokens.ElectricGreen else ColorTokens.CrimsonAlert

    Row(
        modifier = modifier
            .background(Color(0xFF161F33), RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusDotColor, RoundedCornerShape(3.dp))
        )

        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = type.code,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    fontSize = 11.sp
                )
                Text(
                    text = "${powerKw.toInt()} kW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorTokens.TextPrimaryOnDark,
                    fontSize = 11.sp
                )
            }
            Text(
                text = if (type.isDc) "شارژ سریع DC" else "شارژ AC",
                style = MaterialTheme.typography.labelSmall,
                color = ColorTokens.TextMutedOnDark,
                fontSize = 9.sp
            )
        }
    }
}
