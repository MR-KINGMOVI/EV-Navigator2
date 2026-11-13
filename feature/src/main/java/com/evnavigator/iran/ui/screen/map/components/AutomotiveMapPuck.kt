package com.evnavigator.iran.ui.screen.map.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable

/**
 * Creates dynamic high-performance navigation vehicle pucks with
 * heading chevron, glowing cyan aura, and directional accuracy cone.
 */
object AutomotiveMapPuck {

    fun createVehiclePuck(
        context: Context,
        isDeadReckoning: Boolean = false,
        speedKmh: Double = 0.0
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (60 * density).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val accentColor = if (isDeadReckoning) Color.parseColor("#FF9800") else Color.parseColor("#00E5FF")
        val glowColor = if (isDeadReckoning) Color.parseColor("#33FF9800") else Color.parseColor("#3300E5FF")

        // 1. Outer Pulse Aura
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glowColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 26f * density, pulsePaint)

        // 2. Base Dark Disk
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0F19")
            style = Paint.Style.FILL
            setShadowLayer(6f * density, 0f, 2f * density, Color.parseColor("#99000000"))
        }
        canvas.drawCircle(center, center, 18f * density, basePaint)

        // 3. Glowing Outer Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }
        canvas.drawCircle(center, center, 18f * density, borderPaint)

        // 4. White Navigation Chevron / Arrow pointing NORTH (0 degrees)
        val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val chevron = Path().apply {
            moveTo(center, center - (10f * density)) // Tip
            lineTo(center + (8f * density), center + (7f * density)) // Bottom right
            lineTo(center, center + (3f * density)) // Inset center
            lineTo(center - (8f * density), center + (7f * density)) // Bottom left
            close()
        }
        canvas.drawPath(chevron, chevronPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
