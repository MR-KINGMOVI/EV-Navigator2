package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.EdgeDirection
import com.evnavigator.iran.core.routing.model.LaneGuidanceInfo
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.core.routing.model.RealRoadRouteSegment
import com.evnavigator.iran.core.routing.model.RealRoadSnapMetadata
import com.evnavigator.iran.core.routing.model.RouteCoverageException
import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * V11 Extracted Collaborator: Reconstructs route polylines, segments, elevations, and maneuvers
 * from directed edge traversals.
 */
object RouteGeometryReconstructor {

    fun assembleOfflineRouteResult(
        startSnap: IranRealRoadRoutingEngine.SnappedSegmentResult,
        endSnap: IranRealRoadRoutingEngine.SnappedSegmentResult,
        traversedSteps: List<IranRealRoadRoutingEngine.DirectedEdgeTraversal>,
        altSteps: List<IranRealRoadRoutingEngine.DirectedEdgeTraversal>,
        nodeMap: Map<String, CorridorNode>,
        coverageRegionFallback: String
    ): RealRoadRouteResult {
        val startNode = startSnap.closestNode
        val endNode = endSnap.closestNode

        val polyline = mutableListOf<GeoCoordinate>()
        val maneuvers = mutableListOf<RealRoadManeuver>()
        val routeSegments = mutableListOf<RealRoadRouteSegment>()
        var totalDistMeters = 0.0
        var totalDurationSec = 0.0
        var cumulativeDistMeters = 0.0

        polyline.add(startSnap.snappedPoint)

        var previousBearing = 0.0
        var previousRoadName: String? = null
        var previousRoadClass: String? = null

        val effectiveSteps: List<IranRealRoadRoutingEngine.DirectedEdgeTraversal> =
            if (traversedSteps.isEmpty() && startNode.id == endNode.id) {
                val sharedEdge = startSnap.edge
                val forwardOnEdge = endSnap.distanceAlongEdgeMeters >= startSnap.distanceAlongEdgeMeters
                listOf(
                    IranRealRoadRoutingEngine.DirectedEdgeTraversal(
                        edgeIndex = startSnap.edgeIndex.coerceAtLeast(0),
                        edgeId = "edge_${startSnap.edgeIndex.coerceAtLeast(0) + 1}",
                        fromNodeId = if (forwardOnEdge) sharedEdge.from else sharedEdge.to,
                        toNodeId = if (forwardOnEdge) sharedEdge.to else sharedEdge.from,
                        isForward = forwardOnEdge,
                        edge = sharedEdge
                    )
                )
            } else {
                traversedSteps
            }

        for (i in effectiveSteps.indices) {
            val step = effectiveSteps[i]
            val edge = step.edge
            val rawSegmentCoords = if (step.isForward) edge.detailedPath else edge.detailedPath.reversed()
            val fullSegmentCoords = if (rawSegmentCoords.size >= 2) rawSegmentCoords else listOfNotNull(
                nodeMap[step.fromNodeId]?.coordinate,
                nodeMap[step.toNodeId]?.coordinate
            )

            val isFirstStep = (i == 0)
            val isLastStep = (i == effectiveSteps.lastIndex)
            val matchesStartEdge = isFirstStep && startSnap.edge.from == edge.from && startSnap.edge.to == edge.to
            val matchesEndEdge = isLastStep && endSnap.edge.from == edge.from && endSnap.edge.to == edge.to

            val startOffsetMeters = if (matchesStartEdge) {
                if (step.isForward) {
                    startSnap.distanceAlongEdgeMeters.coerceIn(0.0, edge.distanceMeters)
                } else {
                    (edge.distanceMeters - startSnap.distanceAlongEdgeMeters).coerceIn(0.0, edge.distanceMeters)
                }
            } else {
                0.0
            }

            val endOffsetMeters = if (matchesEndEdge) {
                if (step.isForward) {
                    endSnap.distanceAlongEdgeMeters.coerceIn(startOffsetMeters, edge.distanceMeters)
                } else {
                    (edge.distanceMeters - endSnap.distanceAlongEdgeMeters).coerceIn(startOffsetMeters, edge.distanceMeters)
                }
            } else {
                edge.distanceMeters
            }

            val segmentCoords = trimPolylineByOffset(
                coords = fullSegmentCoords,
                totalEdgeLengthMeters = edge.distanceMeters,
                startOffsetMeters = startOffsetMeters,
                endOffsetMeters = endOffsetMeters,
                startPointOverride = if (matchesStartEdge) startSnap.snappedPoint else null,
                endPointOverride = if (matchesEndEdge) endSnap.snappedPoint else null
            )

            val effectiveLengthMeters = (endOffsetMeters - startOffsetMeters)
                .takeIf { it > 0.5 }
                ?: segmentCoords.zipWithNext { a, b -> RoadGraphSnappingEngine.calculateHaversineDistanceMeters(a, b) }
                    .sum().coerceAtLeast(1.0)

            polyline.addAll(segmentCoords)
            val segCumulativeStart = cumulativeDistMeters
            cumulativeDistMeters += effectiveLengthMeters
            totalDistMeters += effectiveLengthMeters

            val entryBearing = if (segmentCoords.size >= 2) {
                RoadGraphSnappingEngine.computeBearingDegrees(segmentCoords.first(), segmentCoords[1])
            } else previousBearing

            val exitBearing = if (segmentCoords.size >= 2) {
                RoadGraphSnappingEngine.computeBearingDegrees(segmentCoords[segmentCoords.size - 2], segmentCoords.last())
            } else entryBearing

            val maneuverType = ManeuverClassifier.classify(
                ManeuverClassifier.ClassificationInput(
                    stepIndex = i,
                    isLastStep = false,
                    prevRoadName = previousRoadName,
                    currentRoadName = edge.roadName,
                    prevRoadClass = previousRoadClass,
                    currentRoadClass = edge.roadClass.name,
                    bearingBefore = if (i == 0) entryBearing else previousBearing,
                    bearingAfter = entryBearing,
                    isRoundabout = edge.roadName.contains("میدان") || edge.roadName.contains("فلکه"),
                    isRamp = edge.roadName.contains("خروجی") || edge.roadName.contains("ورودی") || edge.roadName.contains("رمپ"),
                    speedLimitKmh = edge.speedLimitKmh
                )
            )
            val thresholds = ManeuverClassifier.computeAnnouncementThresholds(
                speedLimitKmh = edge.speedLimitKmh,
                roadClass = edge.roadClass.name
            )

            val effectiveSpeedKmh = RoadGraphCostingModel.computeEffectiveSegmentSpeedKmh(edge)
            val maneuverPenaltySec = RoadGraphCostingModel.computeManeuverPenaltySeconds(maneuverType)
            val segDurationSec = (effectiveLengthMeters / (effectiveSpeedKmh * (1000.0 / 3600.0))) + maneuverPenaltySec
            totalDurationSec += segDurationSec

            val fromElev = nodeMap[step.fromNodeId]?.elevationMeters?.toDouble()
            val toElev = nodeMap[step.toNodeId]?.elevationMeters?.toDouble()
            val gradePct = if (fromElev != null && toElev != null && effectiveLengthMeters >= 10.0) {
                (((toElev - fromElev) / effectiveLengthMeters) * 100.0).coerceIn(-25.0, 25.0)
            } else {
                Double.NaN
            }

            routeSegments.add(
                RealRoadRouteSegment(
                    segmentIndex = i,
                    edgeId = step.edgeId,
                    fromNodeId = step.fromNodeId,
                    toNodeId = step.toNodeId,
                    direction = if (step.isForward) EdgeDirection.FORWARD else EdgeDirection.REVERSE,
                    startOffsetMeters = startOffsetMeters,
                    endOffsetMeters = endOffsetMeters,
                    lengthMeters = effectiveLengthMeters,
                    cumulativeStartMeters = segCumulativeStart,
                    cumulativeEndMeters = cumulativeDistMeters,
                    speedLimitKmh = edge.speedLimitKmh,
                    roadClass = edge.roadClass.name,
                    roadName = edge.roadName,
                    surface = edge.surface,
                    startElevationMeters = fromElev,
                    endElevationMeters = toElev,
                    gradePercent = gradePct,
                    isGradeUnknown = gradePct.isNaN(),
                    polyline = segmentCoords
                )
            )

            maneuvers.add(
                RealRoadManeuver(
                    stepIndex = i,
                    coordinate = segmentCoords.firstOrNull() ?: startSnap.snappedPoint,
                    roadName = edge.roadName,
                    instructionFarsi = OsrmOnlineRouteClient.generatePersianInstruction(maneuverType, edge.roadName, effectiveLengthMeters),
                    instructionEn = OsrmOnlineRouteClient.generateEnglishInstruction(maneuverType, edge.roadName, effectiveLengthMeters),
                    maneuverType = maneuverType,
                    distanceMeters = effectiveLengthMeters,
                    durationSeconds = segDurationSec,
                    speedLimitKmh = edge.speedLimitKmh,
                    laneGuidance = LaneGuidanceInfo.UNKNOWN,
                    edgeId = step.edgeId,
                    segmentIndex = i,
                    bearingBefore = if (i == 0) entryBearing else previousBearing,
                    bearingAfter = entryBearing,
                    initialWarningMeters = thresholds.initialWarningMeters,
                    reminderMeters = thresholds.reminderMeters,
                    executionMeters = thresholds.executionMeters,
                    cumulativeDistanceMeters = segCumulativeStart
                )
            )

            previousBearing = exitBearing
            previousRoadName = edge.roadName
            previousRoadClass = edge.roadClass.name
        }

        polyline.add(endSnap.snappedPoint)

        maneuvers.add(
            RealRoadManeuver(
                stepIndex = maneuvers.size,
                coordinate = endSnap.snappedPoint,
                roadName = "مقصد",
                instructionFarsi = "به مقصد رسیدید",
                instructionEn = "You have arrived at your destination",
                maneuverType = RealManeuverType.ARRIVE,
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                speedLimitKmh = 50,
                laneGuidance = LaneGuidanceInfo.UNKNOWN,
                edgeId = effectiveSteps.lastOrNull()?.edgeId,
                segmentIndex = (routeSegments.size - 1).coerceAtLeast(0),
                bearingBefore = previousBearing,
                bearingAfter = previousBearing,
                cumulativeDistanceMeters = cumulativeDistMeters
            )
        )

        val cleanPolyline = deduplicateAdjacentPoints(polyline, toleranceMeters = 0.5)
        if (cleanPolyline.size < 2) {
            throw RouteCoverageException("مسیر جاده‌ای پیوسته‌ای برای نقاط مشخص‌شده تشکیل نشد (Unable to construct continuous road path).")
        }

        val polylineSumDist = cleanPolyline.zipWithNext { a, b -> RoadGraphSnappingEngine.calculateHaversineDistanceMeters(a, b) }.sum()
        val finalCalculatedDist = if (totalDistMeters > 0.0) totalDistMeters else polylineSumDist

        val alternativeResults = mutableListOf<RealRoadRouteResult>()
        if (altSteps.isNotEmpty() && altSteps.map { it.edgeId } != traversedSteps.map { it.edgeId }) {
            val altPolyline = mutableListOf<GeoCoordinate>()
            altPolyline.add(startSnap.snappedPoint)
            var altDist = 0.0
            var altDur = 0.0
            val altManeuvers = mutableListOf<RealRoadManeuver>()
            val altSegments = mutableListOf<RealRoadRouteSegment>()
            var altCumDist = 0.0
            var altPrevBearing = 0.0
            var altPrevRoadName: String? = null
            var altPrevRoadClass: String? = null

            for (i in altSteps.indices) {
                val step = altSteps[i]
                val edge = step.edge
                val rawAltCoords = if (step.isForward) edge.detailedPath else edge.detailedPath.reversed()
                val fullAltCoords = if (rawAltCoords.size >= 2) rawAltCoords else listOfNotNull(
                    nodeMap[step.fromNodeId]?.coordinate,
                    nodeMap[step.toNodeId]?.coordinate
                )

                val matchesAltStart = (i == 0) && startSnap.edge.from == edge.from && startSnap.edge.to == edge.to
                val matchesAltEnd = (i == altSteps.lastIndex) && endSnap.edge.from == edge.from && endSnap.edge.to == edge.to

                val altStartOffset = if (matchesAltStart) {
                    if (step.isForward) {
                        startSnap.distanceAlongEdgeMeters.coerceIn(0.0, edge.distanceMeters)
                    } else {
                        (edge.distanceMeters - startSnap.distanceAlongEdgeMeters).coerceIn(0.0, edge.distanceMeters)
                    }
                } else 0.0

                val altEndOffset = if (matchesAltEnd) {
                    if (step.isForward) {
                        endSnap.distanceAlongEdgeMeters.coerceIn(altStartOffset, edge.distanceMeters)
                    } else {
                        (edge.distanceMeters - endSnap.distanceAlongEdgeMeters).coerceIn(altStartOffset, edge.distanceMeters)
                    }
                } else edge.distanceMeters

                val segCoords = trimPolylineByOffset(
                    coords = fullAltCoords,
                    totalEdgeLengthMeters = edge.distanceMeters,
                    startOffsetMeters = altStartOffset,
                    endOffsetMeters = altEndOffset,
                    startPointOverride = if (matchesAltStart) startSnap.snappedPoint else null,
                    endPointOverride = if (matchesAltEnd) endSnap.snappedPoint else null
                )
                val altEffectiveLen = (altEndOffset - altStartOffset)
                    .takeIf { it > 0.5 }
                    ?: segCoords.zipWithNext { a, b -> RoadGraphSnappingEngine.calculateHaversineDistanceMeters(a, b) }
                        .sum().coerceAtLeast(1.0)

                altPolyline.addAll(segCoords)
                val segStart = altCumDist
                altCumDist += altEffectiveLen
                altDist += altEffectiveLen

                val entryB = if (segCoords.size >= 2) RoadGraphSnappingEngine.computeBearingDegrees(segCoords.first(), segCoords[1]) else altPrevBearing
                val exitB = if (segCoords.size >= 2) RoadGraphSnappingEngine.computeBearingDegrees(segCoords[segCoords.size - 2], segCoords.last()) else entryB
                val mType = ManeuverClassifier.classify(
                    ManeuverClassifier.ClassificationInput(
                        stepIndex = i,
                        isLastStep = false,
                        prevRoadName = altPrevRoadName,
                        currentRoadName = edge.roadName,
                        prevRoadClass = altPrevRoadClass,
                        currentRoadClass = edge.roadClass.name,
                        bearingBefore = if (i == 0) entryB else altPrevBearing,
                        bearingAfter = entryB,
                        speedLimitKmh = edge.speedLimitKmh
                    )
                )
                val effSpeed = RoadGraphCostingModel.computeEffectiveSegmentSpeedKmh(edge)
                val segDur = (altEffectiveLen / (effSpeed * (1000.0 / 3600.0))) + RoadGraphCostingModel.computeManeuverPenaltySeconds(mType)
                altDur += segDur

                altSegments.add(
                    RealRoadRouteSegment(
                        segmentIndex = i,
                        edgeId = step.edgeId,
                        fromNodeId = step.fromNodeId,
                        toNodeId = step.toNodeId,
                        direction = if (step.isForward) EdgeDirection.FORWARD else EdgeDirection.REVERSE,
                        startOffsetMeters = altStartOffset,
                        endOffsetMeters = altEndOffset,
                        lengthMeters = altEffectiveLen,
                        cumulativeStartMeters = segStart,
                        cumulativeEndMeters = altCumDist,
                        speedLimitKmh = edge.speedLimitKmh,
                        roadClass = edge.roadClass.name,
                        roadName = edge.roadName,
                        surface = edge.surface,
                        polyline = segCoords
                    )
                )
                altManeuvers.add(
                    RealRoadManeuver(
                        stepIndex = i,
                        coordinate = segCoords.firstOrNull() ?: startSnap.snappedPoint,
                        roadName = edge.roadName,
                        instructionFarsi = OsrmOnlineRouteClient.generatePersianInstruction(mType, edge.roadName, altEffectiveLen),
                        instructionEn = OsrmOnlineRouteClient.generateEnglishInstruction(mType, edge.roadName, altEffectiveLen),
                        maneuverType = mType,
                        distanceMeters = altEffectiveLen,
                        durationSeconds = segDur,
                        speedLimitKmh = edge.speedLimitKmh,
                        laneGuidance = LaneGuidanceInfo.UNKNOWN,
                        edgeId = step.edgeId,
                        segmentIndex = i,
                        bearingBefore = if (i == 0) entryB else altPrevBearing,
                        bearingAfter = entryB,
                        cumulativeDistanceMeters = segStart
                    )
                )
                altPrevBearing = exitB
                altPrevRoadName = edge.roadName
                altPrevRoadClass = edge.roadClass.name
            }
            altPolyline.add(endSnap.snappedPoint)
            val cleanAltPoly = deduplicateAdjacentPoints(altPolyline, toleranceMeters = 0.5)
            if (cleanAltPoly.size >= 2) {
                alternativeResults.add(
                    RealRoadRouteResult(
                        polyline = cleanAltPoly,
                        totalDistanceMeters = altDist.coerceAtLeast(1.0),
                        totalDurationSeconds = altDur.coerceAtLeast(1.0),
                        maneuvers = altManeuvers,
                        isOnlineRouting = false,
                        routeSummary = altManeuvers.firstOrNull()?.roadName ?: "مسیر جایگزین",
                        originSnapDistanceMeters = startSnap.snapDistanceMeters,
                        destinationSnapDistanceMeters = endSnap.snapDistanceMeters,
                        segments = altSegments
                    )
                )
            }
        }

        val startEdgeId = effectiveSteps.firstOrNull()?.edgeId ?: "edge_${startSnap.edgeIndex.coerceAtLeast(0) + 1}"
        val endEdgeId = effectiveSteps.lastOrNull()?.edgeId ?: "edge_${endSnap.edgeIndex.coerceAtLeast(0) + 1}"

        return RealRoadRouteResult(
            polyline = cleanPolyline,
            totalDistanceMeters = finalCalculatedDist,
            totalDurationSeconds = totalDurationSec.coerceAtLeast(1.0),
            maneuvers = maneuvers,
            isOnlineRouting = false,
            routeSummary = if (maneuvers.isNotEmpty()) maneuvers.first().roadName else coverageRegionFallback,
            originSnapDistanceMeters = startSnap.snapDistanceMeters,
            destinationSnapDistanceMeters = endSnap.snapDistanceMeters,
            alternativeRoutes = alternativeResults,
            segments = routeSegments,
            startSnap = RealRoadSnapMetadata(
                nodeId = startNode.id,
                edgeId = startEdgeId,
                snapPoint = startSnap.snappedPoint,
                lateralSnapDistanceMeters = startSnap.snapDistanceMeters,
                distanceAlongEdgeMeters = startSnap.distanceAlongEdgeMeters,
                edgeLengthMeters = startSnap.edge.distanceMeters,
                isForward = effectiveSteps.firstOrNull()?.isForward ?: true
            ),
            endSnap = RealRoadSnapMetadata(
                nodeId = endNode.id,
                edgeId = endEdgeId,
                snapPoint = endSnap.snappedPoint,
                lateralSnapDistanceMeters = endSnap.snapDistanceMeters,
                distanceAlongEdgeMeters = endSnap.distanceAlongEdgeMeters,
                edgeLengthMeters = endSnap.edge.distanceMeters,
                isForward = effectiveSteps.lastOrNull()?.isForward ?: true
            ),
            startElevationMeters = startNode.elevationMeters.toDouble(),
            endElevationMeters = endNode.elevationMeters.toDouble()
        )
    }

    fun deduplicateAdjacentPoints(
        points: List<GeoCoordinate>,
        toleranceMeters: Double = 0.5
    ): List<GeoCoordinate> {
        if (points.size <= 1) return points
        val result = ArrayList<GeoCoordinate>(points.size)
        for (pt in points) {
            val prev = result.lastOrNull()
            if (prev == null || RoadGraphSnappingEngine.calculateHaversineDistanceMeters(prev, pt) > toleranceMeters) {
                result.add(pt)
            }
        }
        if (result.size == 1 && points.size >= 2) {
            result.add(points.last())
        }
        return result
    }

    fun trimPolylineByOffset(
        coords: List<GeoCoordinate>,
        totalEdgeLengthMeters: Double,
        startOffsetMeters: Double,
        endOffsetMeters: Double,
        startPointOverride: GeoCoordinate? = null,
        endPointOverride: GeoCoordinate? = null
    ): List<GeoCoordinate> {
        if (coords.size < 2) {
            return listOfNotNull(startPointOverride ?: coords.firstOrNull(), endPointOverride ?: coords.lastOrNull())
        }
        val isFullStart = startOffsetMeters <= 0.5 && startPointOverride == null
        val isFullEnd = endOffsetMeters >= (totalEdgeLengthMeters - 0.5) && endPointOverride == null
        if (isFullStart && isFullEnd) return coords

        val legLengths = coords.zipWithNext { a, b -> RoadGraphSnappingEngine.calculateHaversineDistanceMeters(a, b) }
        val geometricTotal = legLengths.sum().coerceAtLeast(1.0)
        val scale = if (totalEdgeLengthMeters > 1.0) geometricTotal / totalEdgeLengthMeters else 1.0
        val scaledStart = (startOffsetMeters * scale).coerceIn(0.0, geometricTotal)
        val scaledEnd = (endOffsetMeters * scale).coerceIn(scaledStart, geometricTotal)

        val trimmed = mutableListOf<GeoCoordinate>()
        trimmed.add(startPointOverride ?: interpolateAlongPolyline(coords, legLengths, scaledStart))

        var cum = 0.0
        for (idx in legLengths.indices) {
            cum += legLengths[idx]
            if (cum > scaledStart + 0.5 && cum < scaledEnd - 0.5) {
                trimmed.add(coords[idx + 1])
            }
        }

        trimmed.add(endPointOverride ?: interpolateAlongPolyline(coords, legLengths, scaledEnd))
        return deduplicateAdjacentPoints(trimmed, toleranceMeters = 0.25)
    }

    private fun interpolateAlongPolyline(
        coords: List<GeoCoordinate>,
        legLengths: List<Double>,
        targetDistMeters: Double
    ): GeoCoordinate {
        if (targetDistMeters <= 0.0 || coords.isEmpty()) return coords.first()
        var acc = 0.0
        for (i in legLengths.indices) {
            val len = legLengths[i]
            if (acc + len >= targetDistMeters && len > 0.0) {
                val ratio = ((targetDistMeters - acc) / len).coerceIn(0.0, 1.0)
                val a = coords[i]
                val b = coords[i + 1]
                return GeoCoordinate(
                    latitude = a.latitude + (b.latitude - a.latitude) * ratio,
                    longitude = a.longitude + (b.longitude - a.longitude) * ratio
                )
            }
            acc += len
        }
        return coords.last()
    }
}
