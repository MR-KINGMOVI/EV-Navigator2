package com.evnavigator.iran.core.routing.importer

import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V12.5 Extracted Collaborator: OSM Highway, Access Hierarchy, Direction, Layer & Speed-Limit Tag Parser.
 */
internal object OsmRoadTagParser {

    fun parseLayer(tags: Map<String, String>): Int {
        val explicit = tags["layer"]?.trim()?.toIntOrNull()
        if (explicit != null) return explicit.coerceIn(-5, 5)
        if (tags["bridge"] == "yes" || tags["bridge"] == "viaduct") return 1
        if (tags["tunnel"] == "yes" || tags["tunnel"] == "building_passage") return -1
        return 0
    }

    fun parseRoadClass(highway: String?): RoadClass? {
        return when (highway?.lowercase()?.trim()) {
            "motorway" -> RoadClass.MOTORWAY
            "motorway_link" -> RoadClass.MOTORWAY_LINK
            "trunk" -> RoadClass.TRUNK
            "trunk_link" -> RoadClass.TRUNK_LINK
            "primary" -> RoadClass.PRIMARY
            "primary_link" -> RoadClass.PRIMARY_LINK
            "secondary" -> RoadClass.SECONDARY
            "secondary_link" -> RoadClass.SECONDARY_LINK
            "tertiary" -> RoadClass.TERTIARY
            "tertiary_link" -> RoadClass.TERTIARY_LINK
            "residential", "living_street", "unclassified" -> RoadClass.RESIDENTIAL
            "service" -> RoadClass.SERVICE
            "track" -> RoadClass.TRACK
            "pedestrian", "footway", "steps", "path", "bridleway" -> RoadClass.PEDESTRIAN
            "cycleway" -> RoadClass.CYCLEWAY
            "construction", "proposed" -> RoadClass.UNKNOWN
            else -> null
        }
    }

    fun parseRoadAccess(tags: Map<String, String>, roadClass: RoadClass): RoadAccess {
        if (roadClass == RoadClass.PEDESTRIAN) return RoadAccess.PEDESTRIAN_ONLY
        if (roadClass == RoadClass.CYCLEWAY) return RoadAccess.CYCLE_ONLY
        if (roadClass == RoadClass.UNKNOWN || tags["highway"]?.lowercase() == "construction") {
            return RoadAccess.RESTRICTED
        }
        if (tags["service"]?.lowercase() in setOf("emergency_access", "bus", "busway")) {
            return RoadAccess.RESTRICTED
        }

        val maxHeight = tags["maxheight"]?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
        if (maxHeight != null && maxHeight < 1.65) return RoadAccess.RESTRICTED
        val maxWidth = tags["maxwidth"]?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
        if (maxWidth != null && maxWidth < 1.85) return RoadAccess.RESTRICTED

        val specificAccess = (
            tags["motorcar"]
                ?: tags["motor_vehicle"]
                ?: tags["vehicle"]
                ?: tags["access"]
            )?.lowercase()?.trim()

        val conditionalAccess = (
            tags["motor_vehicle:conditional"]
                ?: tags["access:conditional"]
            )?.lowercase()?.trim()

        if (conditionalAccess != null && conditionalAccess.startsWith("no @") && specificAccess == null) {
            return RoadAccess.DESTINATION_ONLY
        }

        return when (specificAccess) {
            "no", "false", "0", "agricultural", "forestry", "bus", "psv", "emergency" -> RoadAccess.RESTRICTED
            "private", "customers", "delivery" -> RoadAccess.PRIVATE
            "destination", "permissive" -> RoadAccess.DESTINATION_ONLY
            "ev", "electric" -> RoadAccess.EV_ONLY
            else -> if (tags["ev"] == "only") RoadAccess.EV_ONLY else RoadAccess.PUBLIC
        }
    }

    fun parseEdgeDirection(tags: Map<String, String>, roadClass: RoadClass): EdgeDirection {
        val oneway = (tags["oneway:motor_vehicle"] ?: tags["oneway"])?.lowercase()?.trim()
        return when (oneway) {
            "yes", "true", "1" -> EdgeDirection.FORWARD
            "-1", "reverse" -> EdgeDirection.REVERSE
            "no", "false", "0" -> EdgeDirection.BIDIRECTIONAL
            else -> {
                val isRoundabout = tags["junction"]?.lowercase() in setOf("roundabout", "circular")
                if (isRoundabout || roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.MOTORWAY_LINK) {
                    EdgeDirection.FORWARD
                } else {
                    EdgeDirection.BIDIRECTIONAL
                }
            }
        }
    }

    fun parseSpeedLimitKmh(tags: Map<String, String>, roadClass: RoadClass): Int {
        val rawMaxSpeed = (tags["maxspeed:motorcar"] ?: tags["maxspeed:forward"] ?: tags["maxspeed"])
            ?.trim()
            ?.lowercase()

        if (!rawMaxSpeed.isNullOrBlank()) {
            when (rawMaxSpeed) {
                "ir:urban", "urban" -> return 50
                "ir:rural", "rural" -> return 90
                "ir:trunk", "trunk" -> return 110
                "ir:motorway", "motorway", "none" -> return 120
                "walk", "living_street" -> return 10
            }
            val parts = rawMaxSpeed.split(';', '|', ',').map { it.trim() }.filter { it.isNotEmpty() }
            val parsedValues = parts.mapNotNull { part ->
                val numStr = part.takeWhile { it.isDigit() || it == '.' }
                val value = numStr.toDoubleOrNull() ?: return@mapNotNull null
                val kmh = when {
                    part.contains("mph") -> (value * 1.60934).roundToInt()
                    part.contains("knots") -> (value * 1.852).roundToInt()
                    else -> value.roundToInt()
                }
                kmh.takeIf { it in 5..140 }
            }
            if (parsedValues.isNotEmpty()) {
                return parsedValues.min()
            }
        }
        return roadClass.defaultSpeedKmh.toInt().coerceIn(10, 130)
    }

    fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
