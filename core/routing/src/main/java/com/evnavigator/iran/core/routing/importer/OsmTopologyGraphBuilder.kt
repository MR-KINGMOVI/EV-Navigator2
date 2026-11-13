package com.evnavigator.iran.core.routing.importer

import com.evnavigator.iran.core.routing.CorridorEdge
import com.evnavigator.iran.core.routing.CorridorNode
import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.RoadAccess
import com.evnavigator.iran.core.routing.model.RoadClass
import com.evnavigator.iran.core.routing.model.RoadEdge
import com.evnavigator.iran.core.routing.model.RoadGraph
import com.evnavigator.iran.core.routing.model.RoadNode
import com.evnavigator.iran.core.routing.model.TurnRestriction
import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * V12.5 Extracted Collaborator: Grade-separated topological road graph builder & turn-restriction resolver.
 */
internal object OsmTopologyGraphBuilder {

    fun buildDatasetFromRecords(
        nodes: Map<Long, OsmNodeRecord>,
        ways: List<OsmWayRecord>,
        relations: List<OsmRelationRecord>
    ): ImportedCorridorDataset {
        val nodeLayerUsage = mutableMapOf<Pair<Long, Int>, Int>()
        val endpointNodeIds = mutableSetOf<Long>()
        val viaNodeIds = relations.flatMap { rel ->
            rel.members.filter { it.type == "node" && it.role == "via" }.map { it.ref }
        }.toSet()

        for (way in ways) {
            val roadClass = OsmRoadTagParser.parseRoadClass(way.tags["highway"]) ?: continue
            val access = OsmRoadTagParser.parseRoadAccess(way.tags, roadClass)
            if (access == RoadAccess.RESTRICTED && way.tags["highway"]?.lowercase() == "construction") continue

            val validRefs = way.nodeIds.filter { nodes.containsKey(it) }
            if (validRefs.size < 2) continue

            endpointNodeIds.add(validRefs.first())
            endpointNodeIds.add(validRefs.last())

            val layer = OsmRoadTagParser.parseLayer(way.tags)
            validRefs.forEach { nid ->
                val key = Pair(nid, layer)
                nodeLayerUsage[key] = (nodeLayerUsage[key] ?: 0) + 1
            }
        }

        val sameLayerIntersectionNodeIds = nodeLayerUsage.entries
            .filter { it.value >= 2 }
            .map { it.key.first }
            .toSet()

        val graphVertices = LinkedHashMap<String, RoadNode>()
        val corridorNodes = LinkedHashMap<String, CorridorNode>()

        for ((id, record) in nodes) {
            if (endpointNodeIds.contains(id) || viaNodeIds.contains(id) || sameLayerIntersectionNodeIds.contains(id)) {
                val coord = GeoCoordinate(record.latitude, record.longitude)
                val nodeKey = "osm_node_$id"
                graphVertices[nodeKey] = RoadNode(
                    id = nodeKey,
                    coordinate = coord,
                    elevationMeters = record.elevationMeters
                )
                corridorNodes[nodeKey] = CorridorNode(
                    id = nodeKey,
                    name = record.name ?: "تقاطع $id",
                    coordinate = coord,
                    elevationMeters = record.elevationMeters
                )
            }
        }

        data class RawSegmentEdge(
            val edgeId: Long,
            val osmWayId: Long,
            val fromNodeId: Long,
            val toNodeId: Long,
            val roadName: String,
            val distanceMeters: Double,
            val speedLimitKmh: Int,
            val geometry: List<GeoCoordinate>,
            val direction: EdgeDirection,
            val access: RoadAccess,
            val roadClass: RoadClass,
            val surface: String,
            val hasToll: Boolean,
            val isTunnel: Boolean,
            val isBridge: Boolean,
            val layer: Int
        )

        val rawSegments = mutableListOf<RawSegmentEdge>()
        val wayToEdgeIds = mutableMapOf<Long, MutableList<Long>>()
        var nextEdgeId = 1L

        for (way in ways) {
            val highwayTag = way.tags["highway"] ?: continue
            val roadClass = OsmRoadTagParser.parseRoadClass(highwayTag) ?: continue
            val access = OsmRoadTagParser.parseRoadAccess(way.tags, roadClass)
            val direction = OsmRoadTagParser.parseEdgeDirection(way.tags, roadClass)
            val speedLimit = OsmRoadTagParser.parseSpeedLimitKmh(way.tags, roadClass)
            val roadName = way.tags["name:fa"] ?: way.tags["name"] ?: way.tags["ref"] ?: "معبر ${way.id}"
            val surface = way.tags["surface"] ?: "asphalt"
            val hasToll = way.tags["toll"] == "yes" || way.tags["toll"] == "true"
            val isTunnel = way.tags["tunnel"] == "yes" || way.tags["tunnel"] == "building_passage"
            val isBridge = way.tags["bridge"] == "yes" || way.tags["bridge"] == "viaduct"
            val layer = OsmRoadTagParser.parseLayer(way.tags)

            val validNodeIds = way.nodeIds.filter { nodes.containsKey(it) }
            if (validNodeIds.size < 2) continue

            var segStartIdx = 0
            for (idx in 1 until validNodeIds.size) {
                val nid = validNodeIds[idx]
                val isSameLayerSplit = (nodeLayerUsage[Pair(nid, layer)] ?: 0) >= 2
                val isSplitVertex = idx == validNodeIds.lastIndex ||
                    endpointNodeIds.contains(nid) ||
                    viaNodeIds.contains(nid) ||
                    isSameLayerSplit

                if (isSplitVertex) {
                    val subNodeIds = validNodeIds.subList(segStartIdx, idx + 1)
                    val geometry = subNodeIds.map { nodes[it]!!.let { r -> GeoCoordinate(r.latitude, r.longitude) } }
                    val distMeters = geometry.zipWithNext { a, b -> OsmRoadTagParser.haversineMeters(a, b) }.sum().coerceAtLeast(1.0)
                    val edgeId = nextEdgeId++
                    val fromId = subNodeIds.first()
                    val toId = subNodeIds.last()

                    rawSegments.add(
                        RawSegmentEdge(
                            edgeId = edgeId,
                            osmWayId = way.id,
                            fromNodeId = fromId,
                            toNodeId = toId,
                            roadName = roadName,
                            distanceMeters = distMeters,
                            speedLimitKmh = speedLimit,
                            geometry = geometry,
                            direction = direction,
                            access = access,
                            roadClass = roadClass,
                            surface = surface,
                            hasToll = hasToll,
                            isTunnel = isTunnel,
                            isBridge = isBridge,
                            layer = layer
                        )
                    )
                    wayToEdgeIds.getOrPut(way.id) { mutableListOf() }.add(edgeId)
                    segStartIdx = idx
                }
            }
        }

        val turnRestrictions = mutableListOf<TurnRestriction>()
        val edgeRestrictionsById = mutableMapOf<String, MutableList<TurnRestriction>>()
        val prohibitedTargetNodeStringsByEdge = mutableMapOf<Long, MutableSet<String>>()
        val mandatoryTargetNodeStringsByEdge = mutableMapOf<Long, MutableSet<String>>()
        val segmentById = rawSegments.associateBy { it.edgeId }

        for (rel in relations) {
            val restrictionValue = (
                rel.tags["restriction:motorcar"]
                    ?: rel.tags["restriction:motor_vehicle"]
                    ?: rel.tags["restriction"]
                    ?: ""
                ).lowercase().trim()
            if (restrictionValue.isBlank()) continue

            val fromWayId = rel.members.find { it.role == "from" && it.type == "way" }?.ref ?: continue
            val toWayId = rel.members.find { it.role == "to" && it.type == "way" }?.ref ?: continue
            val viaNodeId = rel.members.find { it.role == "via" && it.type == "node" }?.ref
            val viaWayIds = rel.members.filter { it.role == "via" && it.type == "way" }.map { it.ref }

            val fromEdges = wayToEdgeIds[fromWayId].orEmpty().mapNotNull { segmentById[it] }
            val toEdges = wayToEdgeIds[toWayId].orEmpty().mapNotNull { segmentById[it] }
            if (fromEdges.isEmpty() || toEdges.isEmpty()) continue

            if (viaNodeId != null) {
                val matchingFrom = fromEdges.find { seg ->
                    (seg.toNodeId == viaNodeId && (seg.direction == EdgeDirection.FORWARD || seg.direction == EdgeDirection.BIDIRECTIONAL)) ||
                        (seg.fromNodeId == viaNodeId && (seg.direction == EdgeDirection.REVERSE || seg.direction == EdgeDirection.BIDIRECTIONAL))
                } ?: fromEdges.find { it.toNodeId == viaNodeId || it.fromNodeId == viaNodeId }

                val matchingTo = toEdges.find { seg ->
                    (seg.fromNodeId == viaNodeId && (seg.direction == EdgeDirection.FORWARD || seg.direction == EdgeDirection.BIDIRECTIONAL)) ||
                        (seg.toNodeId == viaNodeId && (seg.direction == EdgeDirection.REVERSE || seg.direction == EdgeDirection.BIDIRECTIONAL))
                } ?: toEdges.find { it.fromNodeId == viaNodeId || it.toNodeId == viaNodeId }

                if (matchingFrom != null && matchingTo != null) {
                    val fromEdgeStr = "edge_${matchingFrom.edgeId}"
                    val toEdgeStr = "edge_${matchingTo.edgeId}"
                    val viaNodeStr = "osm_node_$viaNodeId"
                    val targetNodeAfterVia = if (matchingTo.fromNodeId == viaNodeId) matchingTo.toNodeId else matchingTo.fromNodeId
                    when {
                        restrictionValue.startsWith("no_") -> {
                            val pRestr = TurnRestriction.Prohibited(fromEdgeStr, toEdgeStr)
                            val vRestr = TurnRestriction.ViaNode(fromEdgeStr, viaNodeStr, toEdgeStr)
                            turnRestrictions.add(pRestr)
                            turnRestrictions.add(vRestr)
                            edgeRestrictionsById.getOrPut(fromEdgeStr) { mutableListOf() }.add(pRestr)
                            prohibitedTargetNodeStringsByEdge
                                .getOrPut(matchingFrom.edgeId) { mutableSetOf() }
                                .add("osm_node_$targetNodeAfterVia")
                        }
                        restrictionValue.startsWith("only_") -> {
                            val mRestr = TurnRestriction.Mandatory(fromEdgeStr, toEdgeStr)
                            turnRestrictions.add(mRestr)
                            edgeRestrictionsById.getOrPut(fromEdgeStr) { mutableListOf() }.add(mRestr)
                            mandatoryTargetNodeStringsByEdge
                                .getOrPut(matchingFrom.edgeId) { mutableSetOf() }
                                .add("osm_node_$targetNodeAfterVia")

                            val otherOutgoing = rawSegments.filter {
                                it.edgeId != matchingTo.edgeId &&
                                    (it.fromNodeId == viaNodeId || it.toNodeId == viaNodeId)
                            }
                            for (other in otherOutgoing) {
                                val otherTarget = if (other.fromNodeId == viaNodeId) other.toNodeId else other.fromNodeId
                                prohibitedTargetNodeStringsByEdge
                                    .getOrPut(matchingFrom.edgeId) { mutableSetOf() }
                                    .add("osm_node_$otherTarget")
                            }
                        }
                    }
                }
            } else if (viaWayIds.isNotEmpty()) {
                val viaEdgeChain = mutableListOf<RawSegmentEdge>()
                for (vwId in viaWayIds) {
                    val vwEdges = wayToEdgeIds[vwId].orEmpty().mapNotNull { segmentById[it] }
                    viaEdgeChain.addAll(vwEdges)
                }
                if (viaEdgeChain.isNotEmpty()) {
                    val firstVia = viaEdgeChain.first()
                    val lastVia = viaEdgeChain.last()

                    val entryFromEdge = fromEdges.find {
                        it.toNodeId == firstVia.fromNodeId || it.toNodeId == firstVia.toNodeId ||
                            it.fromNodeId == firstVia.fromNodeId || it.fromNodeId == firstVia.toNodeId
                    } ?: fromEdges.last()

                    val exitToEdge = toEdges.find {
                        it.fromNodeId == lastVia.toNodeId || it.fromNodeId == lastVia.fromNodeId ||
                            it.toNodeId == lastVia.toNodeId || it.toNodeId == lastVia.fromNodeId
                    } ?: toEdges.first()

                    val fullEdgeSequence = buildList {
                        add("edge_${entryFromEdge.edgeId}")
                        viaEdgeChain.forEach { add("edge_${it.edgeId}") }
                        add("edge_${exitToEdge.edgeId}")
                    }

                    if (restrictionValue.startsWith("no_")) {
                        val meRestr = TurnRestriction.MultiEdge(fullEdgeSequence, isMandatory = false)
                        turnRestrictions.add(meRestr)
                        val exitJunctionNode = if (exitToEdge.fromNodeId == lastVia.toNodeId || exitToEdge.fromNodeId == lastVia.fromNodeId) {
                            exitToEdge.fromNodeId
                        } else {
                            exitToEdge.toNodeId
                        }
                        val targetAfterExit = if (exitToEdge.fromNodeId == exitJunctionNode) exitToEdge.toNodeId else exitToEdge.fromNodeId
                        prohibitedTargetNodeStringsByEdge
                            .getOrPut(lastVia.edgeId) { mutableSetOf() }
                            .add("osm_node_$targetAfterExit")
                    } else if (restrictionValue.startsWith("only_")) {
                        turnRestrictions.add(TurnRestriction.Mandatory("edge_${entryFromEdge.edgeId}", "edge_${firstVia.edgeId}"))
                        turnRestrictions.add(TurnRestriction.Mandatory("edge_${lastVia.edgeId}", "edge_${exitToEdge.edgeId}"))
                    }
                }
            }
        }

        val roadEdges = LinkedHashMap<String, RoadEdge>()
        val adjacency = LinkedHashMap<String, MutableList<String>>()
        val corridorEdges = ArrayList<CorridorEdge>(rawSegments.size)

        for (seg in rawSegments) {
            val edgeIdStr = "edge_${seg.edgeId}"
            val fromNodeStr = "osm_node_${seg.fromNodeId}"
            val toNodeStr = "osm_node_${seg.toNodeId}"
            val dist = com.evnavigator.iran.domain.model.Distance.fromMeters(seg.distanceMeters)
            val spd = com.evnavigator.iran.domain.model.Speed.ofKilometersPerHour(seg.speedLimitKmh.toDouble())
            val meta = mapOf<String, Any>(
                "name" to seg.roadName,
                "surface" to seg.surface,
                "hasToll" to seg.hasToll,
                "isTunnel" to seg.isTunnel,
                "isBridge" to seg.isBridge,
                "direction" to seg.direction.name
            )

            if (seg.direction == EdgeDirection.FORWARD || seg.direction == EdgeDirection.BIDIRECTIONAL) {
                roadEdges[edgeIdStr] = RoadEdge(
                    id = edgeIdStr,
                    startNode = fromNodeStr,
                    endNode = toNodeStr,
                    distance = dist,
                    speed = spd,
                    roadClass = seg.roadClass,
                    access = seg.access,
                    restrictions = edgeRestrictionsById[edgeIdStr] ?: emptyList(),
                    metadata = meta
                )
                adjacency.getOrPut(fromNodeStr) { mutableListOf() }.add(edgeIdStr)
            }
            if (seg.direction == EdgeDirection.REVERSE || seg.direction == EdgeDirection.BIDIRECTIONAL) {
                val revEdgeId = if (seg.direction == EdgeDirection.REVERSE) edgeIdStr else "${edgeIdStr}_rev"
                roadEdges[revEdgeId] = RoadEdge(
                    id = revEdgeId,
                    startNode = toNodeStr,
                    endNode = fromNodeStr,
                    distance = dist,
                    speed = spd,
                    roadClass = seg.roadClass,
                    access = seg.access,
                    restrictions = emptyList(),
                    metadata = meta
                )
                adjacency.getOrPut(toNodeStr) { mutableListOf() }.add(revEdgeId)
            }

            corridorEdges.add(
                CorridorEdge(
                    from = fromNodeStr,
                    to = toNodeStr,
                    roadName = seg.roadName,
                    distanceMeters = seg.distanceMeters,
                    speedLimitKmh = seg.speedLimitKmh,
                    detailedPath = seg.geometry,
                    direction = seg.direction,
                    access = seg.access,
                    roadClass = seg.roadClass,
                    surface = seg.surface,
                    hasToll = hasTollOrBridge(seg.hasToll),
                    isTunnel = seg.isTunnel,
                    isBridge = seg.isBridge,
                    prohibitedTurns = prohibitedTargetNodeStringsByEdge[seg.edgeId]?.toList() ?: emptyList(),
                    mandatoryTurns = mandatoryTargetNodeStringsByEdge[seg.edgeId]?.toList() ?: emptyList(),
                    osmWayId = seg.osmWayId
                )
            )
        }

        val roadGraph = RoadGraph(
            nodes = graphVertices,
            edges = roadEdges,
            nodeToEdges = adjacency,
            turnRestrictions = turnRestrictions
        )

        return ImportedCorridorDataset(
            nodes = corridorNodes.values.toList(),
            edges = corridorEdges,
            turnRestrictions = turnRestrictions,
            roadGraph = roadGraph
        )
    }

    private fun hasTollOrBridge(hasToll: Boolean): Boolean = hasToll
}
