package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.core.routing.model.RouteCoverageException
import com.evnavigator.iran.core.routing.model.SnapConfig
import com.evnavigator.iran.core.routing.model.TurnRestriction
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * V11 Decomposed Real-World Road Network Routing Engine for Iran.
 *
 * Orchestrates 5 focused single-responsibility collaborators:
 * - [OsrmOnlineRouteClient]: Online OSRM HTTP request & GeoJSON step parsing
 * - [RoadGraphSnappingEngine]: Perpendicular segment projection & node snapping
 * - [RoadGraphCostingModel]: Effective speed & turn-maneuver penalty costing
 * - [DirectedGraphPathSearchEngine]: Directed edge-state A* pathfinding & turn restrictions
 * - [RouteGeometryReconstructor]: Polyline trimming, deduplication, and maneuver/segment assembly
 */
class IranRealRoadRoutingEngine(
    private val enableOnlineRouting: Boolean = false,
    private val connectionTimeoutMs: Int = 4000,
    private val defaultMaxSnapDistanceMeters: Double = 1_500.0,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    data class SnappedNodeResult(
        val node: CorridorNode,
        val snapDistanceMeters: Double
    )

    data class SnappedSegmentResult(
        val snappedPoint: GeoCoordinate,
        val snapDistanceMeters: Double,
        val edge: CorridorEdge,
        val edgeIndex: Int = -1,
        val closestNode: CorridorNode,
        val distanceAlongEdgeMeters: Double
    )

    data class DirectedEdgeTraversal(
        val edgeIndex: Int,
        val edgeId: String,
        val fromNodeId: String,
        val toNodeId: String,
        val isForward: Boolean,
        val edge: CorridorEdge
    )

    suspend fun calculateRealRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        originGpsAccuracyMeters: Double? = null,
        destinationGpsAccuracyMeters: Double? = null,
        snapConfig: SnapConfig = SnapConfig(defaultMaxSnapDistanceMeters = defaultMaxSnapDistanceMeters)
    ): RealRoadRouteResult = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        if (enableOnlineRouting) {
            try {
                val onlineResult = OsrmOnlineRouteClient.fetchOnlineOsrmRoute(origin, destination, connectionTimeoutMs)
                if (onlineResult != null && onlineResult.polyline.size >= 2) {
                    return@withContext onlineResult
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                System.err.println("IranRealRoadRoutingEngine: Online OSRM failed (${e.message}), falling back to offline highway corridors.")
            }
        }

        currentCoroutineContext().ensureActive()
        return@withContext calculateOfflineCorridorRoute(
            origin = origin,
            destination = destination,
            originGpsAccuracyMeters = originGpsAccuracyMeters,
            destinationGpsAccuracyMeters = destinationGpsAccuracyMeters,
            snapConfig = snapConfig
        )
    }

    internal fun parseOsrmResponse(
        json: String,
        origin: GeoCoordinate,
        destination: GeoCoordinate
    ): RealRoadRouteResult? = OsrmOnlineRouteClient.parseOsrmResponse(json, origin, destination)

    fun projectPointOntoSegment(
        p: GeoCoordinate,
        a: GeoCoordinate,
        b: GeoCoordinate
    ): Pair<GeoCoordinate, Double> = RoadGraphSnappingEngine.projectPointOntoSegment(p, a, b)

    fun findClosestRoadWithSnap(
        pointName: String,
        coord: GeoCoordinate,
        edges: List<CorridorEdge>,
        nodes: List<CorridorNode>,
        maxAllowedSnapDistanceMeters: Double
    ): SnappedSegmentResult = RoadGraphSnappingEngine.findClosestRoadWithSnap(
        pointName = pointName,
        coord = coord,
        edges = edges,
        nodes = nodes,
        maxAllowedSnapDistanceMeters = maxAllowedSnapDistanceMeters
    )

    fun findClosestNodeWithDistance(
        pointName: String,
        coord: GeoCoordinate,
        nodes: List<CorridorNode>,
        maxAllowedSnapDistanceMeters: Double
    ): SnappedNodeResult = RoadGraphSnappingEngine.findClosestNodeWithDistance(
        pointName = pointName,
        coord = coord,
        nodes = nodes,
        maxAllowedSnapDistanceMeters = maxAllowedSnapDistanceMeters
    )

    fun findClosestNode(
        coord: GeoCoordinate,
        nodes: List<CorridorNode>,
        maxSnapDistanceMeters: Double = 1_000.0
    ): CorridorNode? = RoadGraphSnappingEngine.findClosestNode(coord, nodes, maxSnapDistanceMeters)

    fun findShortestPath(
        startId: String,
        targetId: String,
        edges: List<CorridorEdge>,
        nodes: List<CorridorNode> = emptyList(),
        multiEdgeRestrictions: List<TurnRestriction.MultiEdge> = emptyList()
    ): List<String> = DirectedGraphPathSearchEngine.findShortestPathDirected(
        startId = startId,
        targetId = targetId,
        edges = edges,
        nodes = nodes,
        multiEdgeRestrictions = multiEdgeRestrictions
    )

    fun findShortestPathDirectedEdges(
        startId: String,
        targetId: String,
        edges: List<CorridorEdge>,
        nodes: List<CorridorNode> = emptyList(),
        multiEdgeRestrictions: List<TurnRestriction.MultiEdge> = emptyList()
    ): List<DirectedEdgeTraversal> = DirectedGraphPathSearchEngine.findShortestPathDirectedEdges(
        startId = startId,
        targetId = targetId,
        edges = edges,
        nodes = nodes,
        multiEdgeRestrictions = multiEdgeRestrictions
    )

    fun deduplicateAdjacentPoints(
        points: List<GeoCoordinate>,
        toleranceMeters: Double = 0.5
    ): List<GeoCoordinate> = RouteGeometryReconstructor.deduplicateAdjacentPoints(points, toleranceMeters)

    fun trimPolylineByOffset(
        coords: List<GeoCoordinate>,
        totalEdgeLengthMeters: Double,
        startOffsetMeters: Double,
        endOffsetMeters: Double,
        startPointOverride: GeoCoordinate? = null,
        endPointOverride: GeoCoordinate? = null
    ): List<GeoCoordinate> = RouteGeometryReconstructor.trimPolylineByOffset(
        coords = coords,
        totalEdgeLengthMeters = totalEdgeLengthMeters,
        startOffsetMeters = startOffsetMeters,
        endOffsetMeters = endOffsetMeters,
        startPointOverride = startPointOverride,
        endPointOverride = endPointOverride
    )

    private suspend fun calculateOfflineCorridorRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        originGpsAccuracyMeters: Double? = null,
        destinationGpsAccuracyMeters: Double? = null,
        snapConfig: SnapConfig = SnapConfig()
    ): RealRoadRouteResult {
        currentCoroutineContext().ensureActive()
        val activeDataset = OsmDatasetManager.getActiveDataset()
        val roadNetwork = activeDataset.nodes
        val edges = activeDataset.edges
        val nodeMap = roadNetwork.associateBy { it.id }
        val multiEdgeRestrictions = activeDataset.turnRestrictions
            .filterIsInstance<TurnRestriction.MultiEdge>()

        val originMaxSnap = snapConfig.calculateMaxSnap(originGpsAccuracyMeters)
        val destMaxSnap = snapConfig.calculateMaxSnap(destinationGpsAccuracyMeters)

        val candidateOriginEdges = activeDataset.spatialIndex
            .queryCandidateEdgesNear(origin, originMaxSnap * 2.0)
            .ifEmpty { edges }
        val candidateDestEdges = activeDataset.spatialIndex
            .queryCandidateEdgesNear(destination, destMaxSnap * 2.0)
            .ifEmpty { edges }

        val startSnap = findClosestRoadWithSnap("مبدأ (Origin)", origin, candidateOriginEdges, roadNetwork, originMaxSnap)
        val endSnap = findClosestRoadWithSnap("مقصد (Destination)", destination, candidateDestEdges, roadNetwork, destMaxSnap)

        val startNode = startSnap.closestNode
        val endNode = endSnap.closestNode

        currentCoroutineContext().ensureActive()

        val traversedSteps = findShortestPathDirectedEdges(
            startId = startNode.id,
            targetId = endNode.id,
            edges = edges,
            nodes = roadNetwork,
            multiEdgeRestrictions = multiEdgeRestrictions
        )
        if (traversedSteps.isEmpty() && startNode.id != endNode.id) {
            throw RouteCoverageException("هیچ مسیر پیوسته‌ای در شبکه جاده‌های آفلاین بین مبدا و مقصد یافت نشد (Disconnected road graph).")
        }

        currentCoroutineContext().ensureActive()

        val altSteps = DirectedGraphPathSearchEngine.findAlternativePathDirectedEdges(
            startId = startNode.id,
            targetId = endNode.id,
            edges = edges,
            primarySteps = traversedSteps,
            nodes = roadNetwork,
            multiEdgeRestrictions = multiEdgeRestrictions
        )

        return RouteGeometryReconstructor.assembleOfflineRouteResult(
            startSnap = startSnap,
            endSnap = endSnap,
            traversedSteps = traversedSteps,
            altSteps = altSteps,
            nodeMap = nodeMap,
            coverageRegionFallback = activeDataset.metadata.coverageRegion
        )
    }
}
