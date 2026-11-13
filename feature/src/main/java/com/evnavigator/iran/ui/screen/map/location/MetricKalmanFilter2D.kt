package com.evnavigator.iran.ui.screen.map.location

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * V12.5 Extracted Collaborator: 2D Metric Extended Kalman Filter in Local Tangent Plane coordinates.
 */
internal class MetricKalmanFilter2D(val refLat: Double, val refLng: Double) {
    private var x = 0.0
    private var y = 0.0
    private var vx = 0.0
    private var vy = 0.0
    private var p00 = 100.0
    private var p11 = 100.0
    private var p22 = 10.0
    private var p33 = 10.0
    private var lastTimeMs = System.currentTimeMillis()

    data class FilteredResult(val latitude: Double, val longitude: Double)

    fun update(
        measLat: Double,
        measLng: Double,
        accuracyMeters: Float,
        timeMs: Long,
        earthRadiusMeters: Double = 6371000.0
    ): FilteredResult {
        val dt = max(0.1, min(2.0, (timeMs - lastTimeMs) / 1000.0))
        lastTimeMs = timeMs

        x += vx * dt
        y += vy * dt
        p00 += p22 * dt * dt + 4.0 * dt
        p11 += p33 * dt * dt + 4.0 * dt

        val cosRef = cos(Math.toRadians(refLat))
        val zx = (measLng - refLng) * (Math.PI / 180.0) * earthRadiusMeters * cosRef
        val zy = (measLat - refLat) * (Math.PI / 180.0) * earthRadiusMeters

        val r = max(4.0, (accuracyMeters * accuracyMeters).toDouble())

        val kx = p00 / (p00 + r)
        val ky = p11 / (p11 + r)

        val innovX = zx - x
        val innovY = zy - y

        x += kx * innovX
        y += ky * innovY
        vx += (kx / dt) * innovX * 0.4
        vy += (ky / dt) * innovY * 0.4

        p00 *= (1.0 - kx)
        p11 *= (1.0 - ky)

        val outLat = refLat + (y / earthRadiusMeters) * (180.0 / Math.PI)
        val outLng = refLng + (x / (earthRadiusMeters * cosRef)) * (180.0 / Math.PI)

        return FilteredResult(outLat, outLng)
    }

    fun getSpeedMps(): Float = hypot(vx, vy).toFloat()
}
