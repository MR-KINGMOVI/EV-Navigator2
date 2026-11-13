package com.evnavigator.iran.ui.screen.map.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable

/**
 * Generates high-definition automotive vector-style station pin icons.
 * Replaces generic map pins with high-contrast automotive cockpit charging badges
 * featuring live power ratings (120 kW, 60 kW), status indicators, and glowing borders.
 */
object ChargerPinFactory {

    private val cache = mutableMapOf<String, BitmapDrawable>()

    fun createStationPin(
        context: Context,
        maxPowerKw: Double,
        isSelected: Boolean = false
    ): BitmapDrawable {
        val cacheKey = "${maxPowerKw.toInt()}_$isSelected"
        cache[cacheKey]?.let { return it }

        val density = context.resources.displayMetrics.density
        val width = (72 * density).toInt()
        val height = (46 * density).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isUltraFast = maxPowerKw >= 100.0
        val isFast = maxPowerKw in 45.0..99.0

        val primaryColor = when {
            isUltraFast -> Color.parseColor("#00E676") // Electric Emerald
            isFast -> Color.parseColor("#00E5FF")      // Neon Cyan
            else -> Color.parseColor("#FFB300")        // Volt Amber
        }

        val bgColor = Color.parseColor("#0F172A") // Deep Cockpit Slate

        // Shadow / Glow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) primaryColor else Color.parseColor("#40000000")
            style = Paint.Style.STROKE
            strokeWidth = if (isSelected) 3f * density else 1.5f * density
            if (isSelected) {
                maskFilter = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
            }
        }

        val pillRect = RectF(
            4f * density,
            3f * density,
            width - 4f * density,
            height - 12f * density
        )

        // Draw selection halo
        if (isSelected) {
            val haloRect = RectF(pillRect).apply { inset(-2f * density, -2f * density) }
            canvas.drawRoundRect(haloRect, 10f * density, 10f * density, shadowPaint)
        }

        // Draw pill background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, 8f * density, 8f * density, bgPaint)

        // Draw pill border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawRoundRect(pillRect, 8f * density, 8f * density, borderPaint)

        // Draw needle pointer triangle at bottom
        val needlePath = Path().apply {
            moveTo(width / 2f - (6f * density), height - 12f * density)
            lineTo(width / 2f, height - 2f * density)
            lineTo(width / 2f + (6f * density), height - 12f * density)
            close()
        }
        canvas.drawPath(needlePath, bgPaint)
        canvas.drawPath(needlePath, borderPaint)

        // Draw Lightning Bolt Icon
        val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        val boltPath = Path().apply {
            val bx = 11f * density
            val by = 9f * density
            moveTo(bx + 6f * density, by)
            lineTo(bx, by + 10f * density)
            lineTo(bx + 4.5f * density, by + 10f * density)
            lineTo(bx + 3f * density, by + 18f * density)
            lineTo(bx + 9f * density, by + 7f * density)
            lineTo(bx + 5f * density, by + 7f * density)
            close()
        }
        canvas.drawPath(boltPath, boltPaint)

        // Draw Power Text e.g. "120 kW"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val text = "${maxPowerKw.toInt()} kW"
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textY = pillRect.centerY() + (textBounds.height() / 2f) - (1f * density)
        canvas.drawText(text, 26f * density, textY, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cache[cacheKey] = drawable
        return drawable
    }

    fun createCompactRadarDot(
        context: Context,
        maxPowerKw: Double,
        isSelected: Boolean = false
    ): BitmapDrawable {
        val cacheKey = "dot_${maxPowerKw.toInt()}_$isSelected"
        cache[cacheKey]?.let { return it }

        val density = context.resources.displayMetrics.density
        val size = (if (isSelected) 28 * density else 20 * density).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isUltraFast = maxPowerKw >= 100.0
        val isFast = maxPowerKw in 45.0..99.0

        val primaryColor = when {
            isUltraFast -> Color.parseColor("#00E676")
            isFast -> Color.parseColor("#00E5FF")
            else -> Color.parseColor("#FFB300")
        }

        val center = size / 2f
        val radius = (if (isSelected) 8f else 6f) * density

        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            alpha = if (isSelected) 120 else 60
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 1f, auraPaint)

        val coreBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0F19")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius + 2f * density, coreBgPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, dotPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cache[cacheKey] = drawable
        return drawable
    }
}
