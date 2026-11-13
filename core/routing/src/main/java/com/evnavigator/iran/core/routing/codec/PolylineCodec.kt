package com.evnavigator.iran.core.routing.codec

import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * High-precision Google / Valhalla / OSRM / GraphHopper encoded polyline codec (V13.0 Audit — Item 1, 2, 165).
 *
 * - Valhalla uses `precision = 6` (`1e-6` degrees, ~11 cm resolution).
 * - Standard Google / OSRM / GraphHopper encoded polylines use `precision = 5` (`1e-5` degrees).
 */
object PolylineCodec {

    /**
     * Decodes an encoded polyline string into a sequence of validated [GeoCoordinate] points.
     *
     * @param encoded Encoded ASCII polyline string.
     * @param precision Decimal precision exponent (`6` for Valhalla, `5` for OSRM/GraphHopper/Google).
     * @return Decoded list of valid [GeoCoordinate] points, or empty list if [encoded] is blank or malformed.
     */
    fun decode(encoded: String, precision: Int = 6): List<GeoCoordinate> {
        if (encoded.isBlank()) return emptyList()
        require(precision in 1..8) { "Precision must be between 1 and 8, was $precision" }

        val factor = 10.0.pow(precision)
        val coordinates = ArrayList<GeoCoordinate>(encoded.length / 4 + 2)
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                if (index >= len) return emptyList() // Truncated varint sequence
                b = encoded[index++].code - 63
                if (b < 0) return emptyList() // Invalid ASCII character in encoded polyline
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && shift < 35)

            val dlat = if ((result and 1) != 0) (result ushr 1).inv() else (result ushr 1)
            lat += dlat

            result = 0
            shift = 0
            do {
                if (index >= len) return emptyList() // Truncated longitude varint sequence
                b = encoded[index++].code - 63
                if (b < 0) return emptyList()
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && shift < 35)

            val dlng = if ((result and 1) != 0) (result ushr 1).inv() else (result ushr 1)
            lng += dlng

            val latitude = lat / factor
            val longitude = lng / factor
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                return emptyList()
            }
            val coord = GeoCoordinate(latitude, longitude)
            val prev = coordinates.lastOrNull()
            if (prev == null || prev.latitude != coord.latitude || prev.longitude != coord.longitude) {
                coordinates.add(coord)
            }
        }
        return coordinates
    }

    /**
     * Encodes a list of [GeoCoordinate] points into an ASCII polyline string at [precision].
     */
    fun encode(coordinates: List<GeoCoordinate>, precision: Int = 6): String {
        if (coordinates.isEmpty()) return ""
        require(precision in 1..8) { "Precision must be between 1 and 8, was $precision" }

        val factor = 10.0.pow(precision)
        val sb = StringBuilder(coordinates.size * 8)
        var prevLat = 0
        var prevLng = 0

        for (coord in coordinates) {
            val lat = (coord.latitude * factor).roundToInt()
            val lng = (coord.longitude * factor).roundToInt()
            encodeSignedNumber(lat - prevLat, sb)
            encodeSignedNumber(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    /**
     * Computes a compact structural signature of a polyline for alternative-route deduplication (Item 3 & 170).
     */
    fun geometrySignature(polyline: List<GeoCoordinate>): String {
        if (polyline.isEmpty()) return "EMPTY"
        val n = polyline.size
        val first = polyline.first()
        val mid = polyline[n / 2]
        val q1 = polyline[n / 4]
        val q3 = polyline[(n * 3) / 4]
        val last = polyline.last()
        return buildString {
            append(n).append(':')
            append(format5(first.latitude)).append(',').append(format5(first.longitude)).append('|')
            append(format5(q1.latitude)).append(',').append(format5(q1.longitude)).append('|')
            append(format5(mid.latitude)).append(',').append(format5(mid.longitude)).append('|')
            append(format5(q3.latitude)).append(',').append(format5(q3.longitude)).append('|')
            append(format5(last.latitude)).append(',').append(format5(last.longitude))
        }
    }

    private fun format5(value: Double): Long = (value * 100_000.0).roundToInt().toLong()

    private fun encodeSignedNumber(num: Int, sb: StringBuilder) {
        var sgnNum = num shl 1
        if (num < 0) {
            sgnNum = sgnNum.inv()
        }
        while (sgnNum >= 0x20) {
            val nextValue = (0x20 or (sgnNum and 0x1f)) + 63
            sb.append(nextValue.toChar())
            sgnNum = sgnNum ushr 5
        }
        sb.append((sgnNum + 63).toChar())
    }
}
