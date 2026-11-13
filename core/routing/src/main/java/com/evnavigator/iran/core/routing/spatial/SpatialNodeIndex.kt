package com.evnavigator.iran.core.routing.spatial

import com.evnavigator.iran.core.routing.engine.CorridorEdge
import com.evnavigator.iran.core.routing.engine.CorridorNode
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 2D Spatial Grid Index for RoadNodes, CorridorNodes, and CorridorEdges.
 *
 * Replaces O(N) full-graph scans with spatial cell bucket queries (Items 11, 12, 61):
 * - Nearest node lookup within a bounded `maxSnapDistanceMeters` radius.
 * - Bounding-box edge/segment spatial queries.
 * - Dataset geographic coverage bounding-box validation.
 */
class SpatialNodeIndex(
    val roadNodes: Collection<RoadNode>,
    val corridorNodes: List<CorridorNode> = emptyList(),
    val corridorEdges: List<CorridorEdge> = emptyList(),
    private val cellSizeDegrees: Double = 0.1 // ~11.1 km grid cell resolution
) {

    data class CoverageBoundingBox(
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double
    ) {
        fun contains(coord: GeoCoordinate, marginDegrees: Double = 0.15): Boolean {
            return coord.latitude in (minLatitude - marginDegrees)..(maxLatitude + marginDegrees) &&
                coord.longitude in (minLongitude - marginDegrees)..(maxLongitude + marginDegrees)
        }
    }

    data class NearestRoadNodeMatch(
        val node: RoadNode,
        val distanceMeters: Double
    )

    data class NearestCorridorNodeMatch(
        val node: CorridorNode,
        val distanceMeters: Double
    )

    private val roadNodeBuckets = HashMap<Long, MutableList<RoadNode>>()
    private val corridorNodeBuckets = HashMap<Long, MutableList<CorridorNode>>()
    private val corridorEdgeBuckets = HashMap<Long, MutableList<CorridorEdge>>()

    val coverageBounds: CoverageBoundingBox?

    init {
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY

        for (rn in roadNodes) {
            val c = rn.coordinate
            minLat = min(minLat, c.latitude)
            maxLat = max(maxLat, c.latitude)
            minLon = min(minLon, c.longitude)
            maxLon = max(maxLon, c.longitude)
            val key = cellKey(c.latitude, c.longitude)
            roadNodeBuckets.getOrPut(key) { ArrayList() }.add(rn)
        }

        for (cn in corridorNodes) {
            val c = cn.coordinate
            minLat = min(minLat, c.latitude)
            maxLat = max(maxLat, c.latitude)
            minLon = min(minLon, c.longitude)
            maxLon = max(maxLon, c.longitude)
            val key = cellKey(c.latitude, c.longitude)
            corridorNodeBuckets.getOrPut(key) { ArrayList() }.add(cn)
        }

        for (edge in corridorEdges) {
            val visitedKeys = HashSet<Long>()
            for (pt in edge.detailedPath) {
                minLat = min(minLat, pt.latitude)
                maxLat = max(maxLat, pt.latitude)
                minLon = min(minLon, pt.longitude)
                maxLon = max(maxLon, pt.longitude)
                val key = cellKey(pt.latitude, pt.longitude)
                if (visitedKeys.add(key)) {
                    corridorEdgeBuckets.getOrPut(key) { ArrayList() }.add(edge)
                }
            }
        }

        coverageBounds = if (minLat <= maxLat && minLon <= maxLon) {
            CoverageBoundingBox(minLat, maxLat, minLon, maxLon)
        } else {
            null
        }
    }

    fun isCoordinateInCoverage(coord: GeoCoordinate, marginDegrees: Double = 0.25): Boolean {
        val bounds = coverageBounds ?: return false
        return bounds.contains(coord, marginDegrees)
    }

    /**
     * Finds the nearest [RoadNode] within [maxSnapDistanceMeters] using expanding spatial ring search.
     * Returns `null` if no node exists within [maxSnapDistanceMeters].
     */
    fun findNearestRoadNode(
        coordinate: GeoCoordinate,
        maxSnapDistanceMeters: Double = 2_500.0
    ): NearestRoadNodeMatch? {
        if (roadNodes.isEmpty()) return null
        val centerRow = cellIndex(coordinate.latitude)
        val centerCol = cellIndex(coordinate.longitude)
        val maxRingRadius = max(1, kotlin.math.ceil((maxSnapDistanceMeters / 111_000.0) / cellSizeDegrees).toInt() + 1)

        var bestNode: RoadNode? = null
        var bestDist = Double.MAX_VALUE

        for (ring in 0..maxRingRadius) {
            for (r in (centerRow - ring)..(centerRow + ring)) {
                for (c in (centerCol - ring)..(centerCol + ring)) {
                    if (ring > 0 && r in (centerRow - ring + 1) until (centerRow + ring) &&
                        c in (centerCol - ring + 1) until (centerCol + ring)
                    ) {
                        continue
                    }
                    val bucket = roadNodeBuckets[packKey(r, c)] ?: continue
                    for (node in bucket) {
                        val d = haversineMeters(coordinate, node.coordinate)
                        if (d < bestDist) {
                            bestDist = d
                            bestNode = node
                        }
                    }
                }
            }
            // If we found a node in the current ring whose distance is smaller than the next ring boundary, stop early
            val ringMinBoundMeters = ring * cellSizeDegrees * 85_000.0
            if (bestNode != null && bestDist <= ringMinBoundMeters) {
                break
            }
        }

        val resolved = bestNode ?: return null
        return if (bestDist <= maxSnapDistanceMeters) {
            NearestRoadNodeMatch(resolved, bestDist)
        } else {
            null
        }
    }

    fun findNearestCorridorNode(
        coordinate: GeoCoordinate,
        maxSnapDistanceMeters: Double = 2_500.0
    ): NearestCorridorNodeMatch? {
        if (corridorNodes.isEmpty()) return null
        val centerRow = cellIndex(coordinate.latitude)
        val centerCol = cellIndex(coordinate.longitude)
        val maxRingRadius = max(1, kotlin.math.ceil((maxSnapDistanceMeters / 111_000.0) / cellSizeDegrees).toInt() + 1)

        var bestNode: CorridorNode? = null
        var bestDist = Double.MAX_VALUE

        for (ring in 0..maxRingRadius) {
            for (r in (centerRow - ring)..(centerRow + ring)) {
                for (c in (centerCol - ring)..(centerCol + ring)) {
                    if (ring > 0 && r in (centerRow - ring + 1) until (centerRow + ring) &&
                        c in (centerCol - ring + 1) until (centerCol + ring)
                    ) {
                        continue
                    }
                    val bucket = corridorNodeBuckets[packKey(r, c)] ?: continue
                    for (node in bucket) {
                        val d = haversineMeters(coordinate, node.coordinate)
                        if (d < bestDist) {
                            bestDist = d
                            bestNode = node
                        }
                    }
                }
            }
            val ringMinBoundMeters = ring * cellSizeDegrees * 85_000.0
            if (bestNode != null && bestDist <= ringMinBoundMeters) {
                break
            }
        }

        val resolved = bestNode ?: return null
        return if (bestDist <= maxSnapDistanceMeters) {
            NearestCorridorNodeMatch(resolved, bestDist)
        } else {
            null
        }
    }

    /**
     * Queries all [CorridorEdge]s intersecting the bounding box `[minLat..maxLat, minLng..maxLng]`
     * using spatial grid buckets instead of scanning the entire graph.
     */
    fun queryEdgesInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<CorridorEdge> {
        val rMin = cellIndex(minLat)
        val rMax = cellIndex(maxLat)
        val cMin = cellIndex(minLng)
        val cMax = cellIndex(maxLng)

        val candidates = LinkedHashSet<CorridorEdge>()
        for (r in rMin..rMax) {
            for (c in cMin..cMax) {
                corridorEdgeBuckets[packKey(r, c)]?.let { candidates.addAll(it) }
            }
        }

        return candidates.filter { edge ->
            edge.detailedPath.any { pt ->
                pt.latitude in minLat..maxLat && pt.longitude in minLng..maxLng
            }
        }
    }

    /**
     * Queries candidate [CorridorEdge]s near [coordinate] within [radiusMeters].
     */
    fun queryCandidateEdgesNear(
        coordinate: GeoCoordinate,
        radiusMeters: Double
    ): List<CorridorEdge> {
        val degDelta = (radiusMeters / 85_000.0).coerceAtLeast(cellSizeDegrees)
        return queryEdgesInBounds(
            minLat = coordinate.latitude - degDelta,
            maxLat = coordinate.latitude + degDelta,
            minLng = coordinate.longitude - degDelta,
            maxLng = coordinate.longitude + degDelta
        )
    }

    fun findNearestNode(
        coordinate: GeoCoordinate,
        maxSnapDistanceMeters: Double = 2_500.0
    ): NearestRoadNodeMatch? = findNearestRoadNode(coordinate, maxSnapDistanceMeters)

    private fun cellIndex(deg: Double): Int = floor(deg / cellSizeDegrees).toInt()

    private fun cellKey(lat: Double, lon: Double): Long = packKey(cellIndex(lat), cellIndex(lon))

    private fun packKey(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)

    private fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    companion object {
        fun fromRoadGraph(graph: com.evnavigator.iran.core.routing.model.RoadGraph): SpatialNodeIndex {
            return SpatialNodeIndex(roadNodes = graph.getAllNodes())
        }
    }
}
