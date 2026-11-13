package com.evnavigator.iran.navigation.engine.impl

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.GeoPoint
import com.evnavigator.iran.domain.model.GeoUtils
import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.navigation.MatchedLocation
import com.evnavigator.iran.navigation.engine.MapMatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Production Hidden Markov Model (HMM) / Viterbi Map Matcher ([MapMatcher]).
 *
 * Computes the most likely road segment and projected coordinate using:
 * 1. Gaussian Emission Probability: perpendicular projection distance scaled by dynamic GPS horizontal accuracy (sigma_z).
 * 2. Exponential Transition Probability (Newson & Krumm HMM formulation): compares great-circle displacement
 *    against topological path distance along candidate segments, weighted by heading alignment.
 * 3. Stationary Vehicle Jitter Filter: locks onto the previous matched position when speed/displacement is below 1.5m.
 * 4. GPS Spike & Teleportation Guard: detects physically impossible velocity jumps (> 65 m/s / 234 km/h).
 */
@Singleton
class HmmMapMatcherImpl @Inject constructor() : MapMatcher {

    data class RoadCandidateSegment(
        val id: String,
        val start: GeoCoordinate,
        val end: GeoCoordinate,
        val isOneWayForward: Boolean = true
    )

    private data class CandidateProjection(
        val segmentId: String,
        val segmentIndex: Int,
        val projectedPoint: GeoCoordinate,
        val perpendicularDistanceMeters: Double,
        val distanceAlongSegmentMeters: Double,
        val segmentLengthMeters: Double,
        val segmentBearingDegrees: Double,
        val emissionProbability: Double
    )

    private data class ViterbiState(
        val candidate: CandidateProjection,
        val logProbability: Double,
        val timestampMs: Long,
        val rawCoordinate: GeoCoordinate
    )

    private var previousViterbiStates: List<ViterbiState> = emptyList()
    private var lastMatchedLocation: MatchedLocation? = null
    private var lastRawLocation: Location? = null

    private val defaultCandidateSearchRadiusMeters = 120.0
    private val betaTransitionScaleMeters = 15.0
    private val maxPlausibleVehicleSpeedMps = 65.0 // 234 km/h

    override fun matchToRoute(location: Location, route: Route): MatchedLocation? {
        if (route.segments.isEmpty()) return null

        val rawCoord = location.toGeoCoordinate()
        val prevRaw = lastRawLocation
        val prevMatch = lastMatchedLocation

        // 0. Reject out-of-order (stale) GPS packets strictly
        if (prevRaw != null && location.timestampEpochMs > 0L && prevRaw.timestampEpochMs > 0L &&
            location.timestampEpochMs < prevRaw.timestampEpochMs
        ) {
            return prevMatch
        }

        // 1. Stationary vehicle lock: prevent heading/position jitter when stopped at traffic light
        if (prevRaw != null && prevMatch != null) {
            val dtSec = ((location.timestampEpochMs - prevRaw.timestampEpochMs) / 1000.0).coerceAtLeast(0.0)
            val rawDisplacement = GeoUtils.haversineMeters(prevRaw.toGeoCoordinate(), rawCoord)
            if (rawDisplacement < 1.5 && dtSec in 0.0..30.0) {
                val stationaryMatch = prevMatch.copy(
                    location = location,
                    bearingConfidence = 0.2
                )
                lastRawLocation = location
                lastMatchedLocation = stationaryMatch
                return stationaryMatch
            }
            // 2. Reject extreme GPS teleportation spikes (> 65 m/s over short intervals)
            if (dtSec in 0.1..10.0 && (rawDisplacement / dtSec) > maxPlausibleVehicleSpeedMps && rawDisplacement > 250.0) {
                return prevMatch.copy(
                    location = location,
                    confidence = (prevMatch.confidence * 0.5).coerceIn(0.05, 1.0)
                )
            }
        }

        val sigmaZ = (location.accuracyMeters?.toDouble() ?: 10.0).coerceIn(4.0, 50.0)
        val maxSearchRadius = max(defaultCandidateSearchRadiusMeters, sigmaZ * 3.5)

        // Build candidate projections across route segments
        val candidates = mutableListOf<CandidateProjection>()
        route.segments.forEachIndexed { index, seg ->
            val proj = projectOntoSegment(rawCoord, seg.start, seg.end)
            if (proj.perpendicularDistanceMeters <= maxSearchRadius) {
                val segLen = max(1.0, seg.distance.value)
                val emission = gaussianProbability(proj.perpendicularDistanceMeters, sigmaZ)
                candidates.add(
                    CandidateProjection(
                        segmentId = seg.id,
                        segmentIndex = index,
                        projectedPoint = proj.point,
                        perpendicularDistanceMeters = proj.perpendicularDistanceMeters,
                        distanceAlongSegmentMeters = proj.distanceFromStartMeters.coerceIn(0.0, segLen),
                        segmentLengthMeters = segLen,
                        segmentBearingDegrees = seg.bearing.degrees,
                        emissionProbability = emission
                    )
                )
            }
        }

        // Estimate current speed (m/s) to determine GPS bearing reliability (< 1.5 m/s has noisy GPS heading)
        val estimatedSpeedMps: Double = location.speedMps?.toDouble() ?: run {
            if (prevRaw != null) {
                val dt = ((location.timestampEpochMs - prevRaw.timestampEpochMs) / 1000.0).coerceAtLeast(0.1)
                GeoUtils.haversineMeters(prevRaw.toGeoCoordinate(), rawCoord) / dt
            } else 0.0
        }
        val isLowSpeed = estimatedSpeedMps < 1.5
        val bearingConfidence = if (isLowSpeed) 0.25 else (estimatedSpeedMps / 8.0).coerceIn(0.4, 1.0)

        // If all segments are beyond search radius, pick the single closest segment with degraded confidence
        if (candidates.isEmpty()) {
            val closest = route.segments.mapIndexed { idx, seg ->
                val proj = projectOntoSegment(rawCoord, seg.start, seg.end)
                val segLen = max(1.0, seg.distance.value)
                CandidateProjection(
                    segmentId = seg.id,
                    segmentIndex = idx,
                    projectedPoint = proj.point,
                    perpendicularDistanceMeters = proj.perpendicularDistanceMeters,
                    distanceAlongSegmentMeters = proj.distanceFromStartMeters.coerceIn(0.0, segLen),
                    segmentLengthMeters = segLen,
                    segmentBearingDegrees = seg.bearing.degrees,
                    emissionProbability = gaussianProbability(proj.perpendicularDistanceMeters, sigmaZ)
                )
            }.minByOrNull { it.perpendicularDistanceMeters } ?: return null

            val farConfidence = exp(-closest.perpendicularDistanceMeters / (sigmaZ * 3.0)).coerceIn(0.01, 0.45)
            val segDir = route.segments.getOrNull(closest.segmentIndex)?.direction ?: "FORWARD"
            val result = MatchedLocation(
                location = location,
                matchedPoint = GeoPoint(closest.projectedPoint.latitude, closest.projectedPoint.longitude, location.altitudeM),
                confidence = farConfidence,
                roadSegmentId = closest.segmentId,
                distanceAlongSegmentMeters = closest.distanceAlongSegmentMeters,
                segmentIndex = closest.segmentIndex,
                direction = segDir,
                bearingConfidence = bearingConfidence
            )
            lastRawLocation = location
            lastMatchedLocation = result
            return result
        }

        // Compute movement bearing only when displacement >= 2.0m and speed >= 1.5 m/s
        val movementBearingDeg: Double? = if (!isLowSpeed && prevRaw != null &&
            GeoUtils.haversineMeters(prevRaw.toGeoCoordinate(), rawCoord) >= 2.0
        ) {
            GeoUtils.bearingDegrees(prevRaw.toGeoCoordinate(), rawCoord)
        } else if (!isLowSpeed && location.bearingDegrees != null) {
            location.bearingDegrees.toDouble()
        } else {
            null
        }

        // Viterbi trellis step
        val currentViterbiStates = if (previousViterbiStates.isEmpty()) {
            candidates.map { cand ->
                val headingFactor = movementBearingDeg?.let { movBrg ->
                    headingAlignmentFactor(movBrg, cand.segmentBearingDegrees)
                } ?: 1.0
                val score = kotlin.math.ln((cand.emissionProbability * headingFactor).coerceAtLeast(1e-12))
                ViterbiState(cand, score, location.timestampEpochMs, rawCoord)
            }
        } else {
            candidates.map { cand ->
                val headingFactor = movementBearingDeg?.let { movBrg ->
                    headingAlignmentFactor(movBrg, cand.segmentBearingDegrees)
                } ?: 1.0

                var bestTransitionLogProb = Double.NEGATIVE_INFINITY
                for (prevState in previousViterbiStates) {
                    val linearDist = GeoUtils.haversineMeters(prevState.rawCoordinate, rawCoord)
                    val routeDist = computeTopologicalDistance(route.segments, prevState.candidate, cand)
                    val diff = abs(linearDist - routeDist)
                    // Penalize backward progression on a directed route
                    val directionPenalty = if (cand.segmentIndex < prevState.candidate.segmentIndex ||
                        (cand.segmentIndex == prevState.candidate.segmentIndex &&
                            cand.distanceAlongSegmentMeters + 3.0 < prevState.candidate.distanceAlongSegmentMeters)
                    ) {
                        0.15
                    } else {
                        1.0
                    }
                    val transProb = (1.0 / betaTransitionScaleMeters) *
                        exp(-diff / betaTransitionScaleMeters) *
                        directionPenalty *
                        headingFactor
                    val totalLogProb = prevState.logProbability +
                        kotlin.math.ln((cand.emissionProbability * transProb).coerceAtLeast(1e-15))
                    if (totalLogProb > bestTransitionLogProb) {
                        bestTransitionLogProb = totalLogProb
                    }
                }
                ViterbiState(cand, bestTransitionLogProb, location.timestampEpochMs, rawCoord)
            }
        }

        previousViterbiStates = currentViterbiStates.sortedByDescending { it.logProbability }.take(5)
        val bestState = previousViterbiStates.first()
        val bestCand = bestState.candidate

        val distScore = exp(-0.5 * (bestCand.perpendicularDistanceMeters / sigmaZ) * (bestCand.perpendicularDistanceMeters / sigmaZ))
        val headingScore = movementBearingDeg?.let { headingAlignmentFactor(it, bestCand.segmentBearingDegrees) } ?: 0.9
        val confidence = (0.75 * distScore + 0.25 * headingScore).coerceIn(0.05, 1.0)
        val segDir = route.segments.getOrNull(bestCand.segmentIndex)?.direction ?: "FORWARD"

        val matched = MatchedLocation(
            location = location,
            matchedPoint = GeoPoint(bestCand.projectedPoint.latitude, bestCand.projectedPoint.longitude, location.altitudeM),
            confidence = confidence,
            roadSegmentId = bestCand.segmentId,
            distanceAlongSegmentMeters = bestCand.distanceAlongSegmentMeters,
            segmentIndex = bestCand.segmentIndex,
            direction = segDir,
            bearingConfidence = bearingConfidence
        )
        lastRawLocation = location
        lastMatchedLocation = matched
        return matched
    }

    override fun matchToRoadNetwork(
        location: Location,
        previousLocation: Location?
    ): MatchedLocation? {
        if (previousLocation != null && location.timestampEpochMs > 0L && previousLocation.timestampEpochMs > 0L &&
            location.timestampEpochMs < previousLocation.timestampEpochMs
        ) {
            return lastMatchedLocation
        }
        val rawCoord = location.toGeoCoordinate()
        val sigmaZ = (location.accuracyMeters?.toDouble() ?: 10.0).coerceIn(4.0, 50.0)

        val movementBearingDeg = if (previousLocation != null &&
            GeoUtils.haversineMeters(previousLocation.toGeoCoordinate(), rawCoord) >= 2.0
        ) {
            GeoUtils.bearingDegrees(previousLocation.toGeoCoordinate(), rawCoord)
        } else {
            null
        }

        val prevMatch = lastMatchedLocation
        if (previousLocation != null && prevMatch != null) {
            val disp = GeoUtils.haversineMeters(previousLocation.toGeoCoordinate(), rawCoord)
            if (disp < 1.5) {
                return prevMatch.copy(location = location, bearingConfidence = 0.2)
            }
        }

        val confidence = (1.0 / (1.0 + (sigmaZ / 25.0))).coerceIn(0.1, 0.98)
        val matched = MatchedLocation(
            location = location,
            matchedPoint = GeoPoint(location.latitude, location.longitude, location.altitudeM),
            confidence = if (movementBearingDeg != null) min(0.99, confidence + 0.05) else confidence,
            roadSegmentId = prevMatch?.roadSegmentId ?: "road_seg_${(location.latitude * 1000).toInt()}_${(location.longitude * 1000).toInt()}",
            distanceAlongSegmentMeters = prevMatch?.distanceAlongSegmentMeters ?: 0.0,
            segmentIndex = prevMatch?.segmentIndex ?: 0,
            direction = prevMatch?.direction ?: "FORWARD",
            bearingConfidence = if (movementBearingDeg != null) 0.9 else 0.3
        )
        lastRawLocation = location
        lastMatchedLocation = matched
        return matched
    }

    fun resetState() {
        previousViterbiStates = emptyList()
        lastMatchedLocation = null
        lastRawLocation = null
    }

    private data class SegmentProjectionResult(
        val point: GeoCoordinate,
        val perpendicularDistanceMeters: Double,
        val distanceFromStartMeters: Double
    )

    private fun projectOntoSegment(
        p: GeoCoordinate,
        a: GeoCoordinate,
        b: GeoCoordinate
    ): SegmentProjectionResult {
        val r = 6371000.0
        val midLatRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosLat = kotlin.math.cos(midLatRad)

        val ax = Math.toRadians(a.longitude) * cosLat * r
        val ay = Math.toRadians(a.latitude) * r
        val bx = Math.toRadians(b.longitude) * cosLat * r
        val by = Math.toRadians(b.latitude) * r
        val px = Math.toRadians(p.longitude) * cosLat * r
        val py = Math.toRadians(p.latitude) * r

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq > 0.0) {
            (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val projX = ax + t * dx
        val projY = ay + t * dy
        val projLon = Math.toDegrees(projX / (cosLat * r))
        val projLat = Math.toDegrees(projY / r)
        val projPoint = GeoCoordinate(projLat.coerceIn(-90.0, 90.0), projLon.coerceIn(-180.0, 180.0))

        val perpDist = GeoUtils.haversineMeters(p, projPoint)
        val fromStart = GeoUtils.haversineMeters(a, projPoint)
        return SegmentProjectionResult(projPoint, perpDist, fromStart)
    }

    private fun computeTopologicalDistance(
        segments: List<RouteSegment>,
        from: CandidateProjection,
        to: CandidateProjection
    ): Double {
        if (from.segmentIndex == to.segmentIndex) {
            return abs(to.distanceAlongSegmentMeters - from.distanceAlongSegmentMeters)
        }
        val startIdx = min(from.segmentIndex, to.segmentIndex)
        val endIdx = max(from.segmentIndex, to.segmentIndex)
        var dist = if (from.segmentIndex < to.segmentIndex) {
            (from.segmentLengthMeters - from.distanceAlongSegmentMeters).coerceAtLeast(0.0) + to.distanceAlongSegmentMeters
        } else {
            from.distanceAlongSegmentMeters + (to.segmentLengthMeters - to.distanceAlongSegmentMeters).coerceAtLeast(0.0)
        }
        for (i in (startIdx + 1) until endIdx) {
            dist += segments[i].distance.value
        }
        return dist
    }

    private fun gaussianProbability(distanceMeters: Double, sigmaMeters: Double): Double {
        val coeff = 1.0 / (sqrt(2.0 * Math.PI) * sigmaMeters)
        val exponent = -0.5 * (distanceMeters / sigmaMeters) * (distanceMeters / sigmaMeters)
        return (coeff * exp(exponent)).coerceAtLeast(1e-12)
    }

    private fun headingAlignmentFactor(vehicleBearingDeg: Double, segmentBearingDeg: Double): Double {
        val diffDeg = abs(((vehicleBearingDeg - segmentBearingDeg + 540.0) % 360.0) - 180.0)
        val diffRad = Math.toRadians(diffDeg)
        return ((1.0 + kotlin.math.cos(diffRad)) / 2.0).coerceIn(0.05, 1.0)
    }
}
