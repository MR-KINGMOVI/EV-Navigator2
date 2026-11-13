package com.evnavigator.iran.ui.screen.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.icons.EVIcons
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Standard classification for Iranian roadways and thoroughfares.
 */
enum class IranianRoadType {
    FREEWAY_FREEWAY,   // آزادراه (Green Shield: e.g. تهران-قم، تهران-شمال، قزوین-رشت)
    EXPRESSWAY_URBAN,  // بزرگراه شهری (Blue Shield: e.g. همت، مدرس، چمران، رسالت)
    TRANS_IRAN_HIGHWAY,// جاده شریانی ملی / ترانزیت (Red/White Shield: e.g. جاده فیروزکوه، جاده مخصوص)
    SCENIC_PASS,       // گذرگاه کوهستانی (Emerald/Slate: e.g. جاده چالوس، دیزین، حیران)
    RING_BELTWAY       // کمربندی و کنارگذر (Amber/Obsidian Shield)
}

/**
 * High-fidelity highway shield and interchange badge conforming to Iranian Roadway Standard Signage.
 * Rendered with authentic glassmorphic bevels, high-contrast typography, and official Iranian iconography.
 */
@Composable
fun HighwayShieldBadge(
    roadName: String,
    roadNumber: String? = null,
    exitNumber: String? = null,
    roadType: IranianRoadType = IranianRoadType.FREEWAY_FREEWAY,
    modifier: Modifier = Modifier
) {
    val (primaryColor, secondaryColor, borderColor, textColor) = when (roadType) {
        IranianRoadType.FREEWAY_FREEWAY -> QuadColors(
            Color(0xFF007A3D), Color(0xFF005A2B), Color(0xFF80E27E), Color.White
        )
        IranianRoadType.EXPRESSWAY_URBAN -> QuadColors(
            Color(0xFF005BAA), Color(0xFF003D7A), Color(0xFF40C4FF), Color.White
        )
        IranianRoadType.TRANS_IRAN_HIGHWAY -> QuadColors(
            Color(0xFFB71C1C), Color(0xFF7F0000), Color(0xFFFF8A80), Color.White
        )
        IranianRoadType.SCENIC_PASS -> QuadColors(
            Color(0xFF1B5E20), Color(0xFF0D3311), Color(0xFF69F0AE), Color.White
        )
        IranianRoadType.RING_BELTWAY -> QuadColors(
            Color(0xFFFF8F00), Color(0xFFC56000), Color(0xFFFFD54F), Color.Black
        )
    }

    Surface(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        color = Color(0xF20B1222),
        border = BorderStroke(1.2.dp, Color(0x6638BDF8))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A. Iranian Road Type Emblem / Shield Emblem
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(primaryColor, secondaryColor)
                        )
                    )
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (roadType) {
                            IranianRoadType.FREEWAY_FREEWAY -> "آزادراه"
                            IranianRoadType.EXPRESSWAY_URBAN -> "بزرگراه"
                            IranianRoadType.TRANS_IRAN_HIGHWAY -> "جاده"
                            IranianRoadType.SCENIC_PASS -> "محور"
                            IranianRoadType.RING_BELTWAY -> "کنارگذر"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )
                    if (!roadNumber.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = roadNumber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // B. Road Name Display (RTL Persian)
            Text(
                text = roadName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            // C. Exit Junction Number (if approaching interchange)
            if (!exitNumber.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFD97706))
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "خروجی", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(text = exitNumber, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }
    }
}

private data class QuadColors(
    val primary: Color,
    val secondary: Color,
    val border: Color,
    val text: Color
)
