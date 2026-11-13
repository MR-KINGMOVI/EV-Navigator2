package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.SnapExceededException
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V11 Extracted Collaborator: Segment-level perpendicular road snapping and geodesic math.
 */
object RoadGraphSnappingEngine {

    fun projectPointOntoSegment(
        p: GeoCoordinate,
        a: GeoCoordinate,
        b: GeoCoordinate
    ): Pair<GeoCoordinate, Double> {
        val r = 6371000.0
        val midLat = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosLat = cos(midLat)

        val ax = Math.toRadians(a.longitude) * cosLat * r
        val ay = Math.toRadians(a.latitude) * r
        val bx = Math.toRadians(b.longitude) * cosLat * r
        val by = Math.toRadians(b.latitude) * r
        val px = Math.toRadians(p.longitude) * cosLat * r
        val py = Math.toRadians(p.latitude) * r

        val dx = bx - ax
        val dy = by - ay
        val segLenSq = dx * dx + dy * dy

        val t = if (segLenSq > 0.0) {
            (((px - ax) * dx + (py - ay) * dy) / segLenSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val projX = ax + t * dx
        val projY = ay + t * dy

        val projLon = Math.toDegrees(projX / (cosLat * r))
        val projLat = Math.toDegrees(projY / r)
        val projCoord = GeoCoordinate(projLat, projLon)
        val dist = calculateHaversineDistanceMeters(p, projCoord)

        return Pair(projCoord, dist)
    }

    fun findClosestRoadWithSnap(
        pointName: String,
        coord: GeoCoordinate,
        edges: List<CorridorEdge>,
        nodes: List<CorridorNode>,
        maxAllowedSnapDistanceMeters: Double
    ): IranRealRoadRoutingEngine.SnappedSegmentResult {
        var bestSnapCoord: GeoCoordinate? = null
        var bestDist = Double.MAX_VALUE
        var bestEdge: CorridorEdge? = null
        var bestEdgeIndex = -1
        var bestNode: CorridorNode? = null
        var bestDistAlongEdge = 0.0

        val nodeMap = nodes.associateBy { it.id }

        edges.forEachIndexed { idx, edge ->
            if (edge.access == RoadAccess.RESTRICTED || edge.access == RoadAccess.PEDESTRIAN_ONLY || edge.access == RoadAccess.CYCLE_ONLY) {
                return@forEachIndexed
            }
            val path = edge.detailedPath
            if (path.size < 2) return@forEachIndexed

            var accumulatedDist = 0.0
            for (i in 0 until path.size - 1) {
                val p1 = path[i]
                val p2 = path[i + 1]
                val segLen = calculateHaversineDistanceMeters(p1, p2)
                val (proj, dist) = projectPointOntoSegment(coord, p1, p2)
                if (dist < bestDist) {
                    bestDist = dist
                    bestSnapCoord = proj
                    bestEdge = edge
                    bestEdgeIndex = idx
                    bestDistAlongEdge = accumulatedDist + calculateHaversineDistanceMeters(p1, proj)
                    val fromNode = nodeMap[edge.from]
                    val toNode = nodeMap[edge.to]
                    val dFrom = fromNode?.let { calculateHaversineDistanceMeters(proj, it.coordinate) } ?: Double.MAX_VALUE
                    val dTo = toNode?.let { calculateHaversineDistanceMeters(proj, it.coordinate) } ?: Double.MAX_VALUE
                    bestNode = if (dFrom <= dTo) fromNode ?: toNode else toNode ?: fromNode
                }
                accumulatedDist += segLen
            }
        }

        if (bestDist > maxAllowedSnapDistanceMeters || bestEdge == null || bestNode == null || bestSnapCoord == null) {
            val closestNode = nodes.minByOrNull { calculateHaversineDistanceMeters(coord, it.coordinate) }
            val nodeDist = closestNode?.let { calculateHaversineDistanceMeters(coord, it.coordinate) } ?: Double.MAX_VALUE

            if (nodeDist <= maxAllowedSnapDistanceMeters && closestNode != null) {
                val connectedIdx = edges.indexOfFirst { it.from == closestNode.id || it.to == closestNode.id }
                if (connectedIdx >= 0) {
                    val connectedEdge = edges[connectedIdx]
                    return IranRealRoadRoutingEngine.SnappedSegmentResult(
                        snappedPoint = closestNode.coordinate,
                        snapDistanceMeters = nodeDist,
                        edge = connectedEdge,
                        edgeIndex = connectedIdx,
                        closestNode = closestNode,
                        distanceAlongEdgeMeters = if (connectedEdge.from == closestNode.id) 0.0 else connectedEdge.distanceMeters
                    )
                }
            }

            throw SnapExceededException(
                pointType = pointName,
                coordinate = coord,
                snapDistanceMeters = if (bestDist < Double.MAX_VALUE) bestDist else nodeDist,
                maxAllowedSnapDistanceMeters = maxAllowedSnapDistanceMeters
            )
        }

        return IranRealRoadRoutingEngine.SnappedSegmentResult(
            snappedPoint = bestSnapCoord!!,
            snapDistanceMeters = bestDist,
            edge = bestEdge!!,
            edgeIndex = bestEdgeIndex,
            closestNode = bestNode!!,
            distanceAlongEdgeMeters = bestDistAlongEdge.coerceIn(0.0, bestEdge!!.distanceMeters)
        )
    }

    fun findClosestNodeWithDistance(
        pointName: String,
        coord: GeoCoordinate,
        nodes: List<CorridorNode>,
        maxAllowedSnapDistanceMeters: Double
    ): IranRealRoadRoutingEngine.SnappedNodeResult {
        val closest = nodes.minByOrNull { calculateHaversineDistanceMeters(coord, it.coordinate) }
            ?: throw IllegalStateException("شبکه جاده‌ای خالی است (Road network is empty).")
        val dist = calculateHaversineDistanceMeters(coord, closest.coordinate)
        if (dist > maxAllowedSnapDistanceMeters) {
            throw SnapExceededException(
                pointType = pointName,
                coordinate = coord,
                snapDistanceMeters = dist,
                maxAllowedSnapDistanceMeters = maxAllowedSnapDistanceMeters
            )
        }
        return IranRealRoadRoutingEngine.SnappedNodeResult(closest, dist)
    }

    fun findClosestNode(
        coord: GeoCoordinate,
        nodes: List<CorridorNode>,
        maxSnapDistanceMeters: Double = 1_000.0
    ): CorridorNode? {
        val closest = nodes.minByOrNull { calculateHaversineDistanceMeters(coord, it.coordinate) } ?: return null
        val dist = calculateHaversineDistanceMeters(coord, closest.coordinate)
        return if (dist <= maxSnapDistanceMeters) closest else null
    }

    fun computeBearingDegrees(from: GeoCoordinate, to: GeoCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun calculateHaversineDistanceMeters(c1: GeoCoordinate, c2: GeoCoordinate): Double {
        val r = 6371000.0
        val lat1Rad = Math.toRadians(c1.latitude)
        val lat2Rad = Math.toRadians(c2.latitude)
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
