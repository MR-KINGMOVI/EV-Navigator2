package com.evnavigator.iran.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.theme.ThemeManager

/** Base colour of the aurora (also used for system bars so they blend in). */
fun glassBaseColor(isDark: Boolean): Color = if (isDark) Color(0xFF070B1F) else Color(0xFFEAF0FF)

/**
 * Full-screen animated aurora that every translucent surface sits on.
 * Place ONE of these at the root; screens then keep transparent backgrounds.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val mode by ThemeManager.currentThemeMode.collectAsState()
    val isDark = ThemeManager.isDark(mode, isSystemInDarkTheme())
    val transition = rememberInfiniteTransition(label = "aurora")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraPhase"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glassBaseColor(isDark))
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(c, Color.Transparent),
                            center = Offset(cx, cy),
                            radius = r
                        ),
                        radius = r,
                        center = Offset(cx, cy)
                    )
                }
                if (isDark) {
                    blob(Color(0x730A84FF), w * (0.10f + 0.25f * p), h * (0.10f + 0.10f * p), w * 0.95f)
                    blob(Color(0x5CBF5AF2), w * (0.95f - 0.25f * p), h * (0.38f + 0.08f * p), w * 0.85f)
                    blob(Color(0x4D30D158), w * (0.20f + 0.15f * p), h * (0.92f - 0.10f * p), w * 0.80f)
                } else {
                    blob(Color(0x8064D2FF), w * (0.10f + 0.25f * p), h * (0.10f + 0.10f * p), w * 0.95f)
                    blob(Color(0x66BF5AF2), w * (0.95f - 0.25f * p), h * (0.38f + 0.08f * p), w * 0.85f)
                    blob(Color(0x5930D158), w * (0.20f + 0.15f * p), h * (0.92f - 0.10f * p), w * 0.80f)
                }
            }
    ) {
        content()
    }
}

/**
 * Liquid-glass look without real backdrop blur (not available in Compose):
 * translucent tint + top specular sheen + gradient rim light.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    isDark: Boolean,
    fill: Color = Color.Unspecified
): Modifier {
    val base = if (fill != Color.Unspecified) fill else if (isDark) Color(0x990E1430) else Color(0xB3FFFFFF)
    val sheen = if (isDark) {
        Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF), Color(0x00FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x26FFFFFF), Color(0x00FFFFFF)))
    }
    val rim = Brush.linearGradient(
        listOf(Color(0xA6FFFFFF), Color(0x14FFFFFF), Color(0x59FFFFFF))
    )
    return this
        .clip(shape)
        .background(base, shape)
        .background(sheen, shape)
        .border(width = 1.dp, brush = rim, shape = shape)
}
