package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.RouteCategory
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.trip.intelligence.DetourCalculationSource
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Projects candidate charging stations onto the active route polyline, delegates port/connector/freshness
 * eligibility to [StationPortEligibilityEvaluator], and separates geometric cross-track distance from
 * verified road-graph detour distance (V12.6 P0-2, P0-3, P0-5, P1-6, P1-7).
 *
 * V12.6 P0-2 Invariants:
 * 1. Never multiplies cross-track distance by a synthetic detour factor and calls it `roadDetourMeters`.
 * 2. Never assumes a hardcoded detour speed.
 * 3. Compares geometric cross-track distance strictly against `maxCrossTrackMeters`, and compares
 *    verified road detour distance strictly against `maxRoadDetourMeters`.
 * 4. When [roadRouteProvider] is provided and returns a valid road-graph route (`toStation` + `fromStation`),
 *    reports `verifiedRoadDetourMeters`, `detourDurationMinutes`, and [DetourCalculationSource.ROAD_GRAPH_VERIFIED].
 *    When [roadRouteProvider] is null or returns no route, `verifiedRoadDetourMeters` and `detourDurationMinutes`
 *    remain `null` with [DetourCalculationSource.CROSS_TRACK_GEOMETRIC_ESTIMATE] or [DetourCalculationSource.ROUTE_UNAVAILABLE].
 */
object CorridorStationProjector {

    suspend fun evaluateAndProjectStations(
        stations: List<DomainChargingStationWithPorts>,
        routePolyline: List<GeoCoordinate>,
        routeDistanceMeters: Double,
        maxRoadDetourMeters: Double = EvDomainPolicyConfig.DEFAULT_MAX_ROAD_DETOUR_METERS,
        maxCrossTrackMeters: Double = EvDomainPolicyConfig.DEFAULT_MAX_CROSS_TRACK_METERS,
        explicitConnectors: List<String>,
        vehicleMaxDcKw: Double?,
        vehicleMaxAcKw: Double?,
        clock: DomainClock = DomainClock.SYSTEM,
        roadRouteProvider: RoadRouteProvider? = null,
        requireConfirmedAvailablePort: Boolean = false,
        rejectStaleStations: Boolean = false
    ): Pair<List<PredictiveChargingOrchestrator.ProjectedStationCandidate>, MutableMap<String, String>> {
        val rejectedReasons = LinkedHashMap<String, String>()
        if (stations.isEmpty() || routePolyline.size < 2) {
            return emptyList<PredictiveChargingOrchestrator.ProjectedStationCandidate>() to rejectedReasons
        }

        val segmentLengths = DoubleArray(routePolyline.size - 1)
        var polyTotalMeters = 0.0
        for (i in 0 until routePolyline.size - 1) {
            val d = haversineMeters(routePolyline[i], routePolyline[i + 1])
            segmentLengths[i] = d
            polyTotalMeters += d
        }
        val effectivePolyTotal = polyTotalMeters.coerceAtLeast(1.0)

        val accepted = mutableListOf<PredictiveChargingOrchestrator.ProjectedStationCandidate>()
        val seenStationIds = HashSet<String>()
        val seenCoordinates = HashSet<Pair<Long, Long>>()

        for (entry in stations) {
            val st = entry.station
            if (!seenStationIds.add(st.id)) {
                rejectedReasons[st.id] = "DUPLICATE_STATION_ID"
                continue
            }
            val roundedCoord = Pair((st.latitude * 10_000).roundToLong(), (st.longitude * 10_000).roundToLong())
            if (!seenCoordinates.add(roundedCoord)) {
                rejectedReasons[st.id] = "DUPLICATE_STATION_COORDINATES"
                continue
            }

            // 1. Evaluate station & port eligibility (no brand connector guessing, no AC-from-DC guessing)
            val eligibilityOutcome = StationPortEligibilityEvaluator.evaluateStationAndSelectPort(
                stationWithPorts = entry,
                explicitConnectors = explicitConnectors,
                vehicleMaxDcKw = vehicleMaxDcKw,
                vehicleMaxAcKw = vehicleMaxAcKw,
                clock = clock,
                requireConfirmedAvailablePort = requireConfirmedAvailablePort,
                rejectStaleStations = rejectStaleStations
            )

            val portSelection = when (eligibilityOutcome) {
                is StationPortEligibilityEvaluator.StationEvaluationOutcome.Rejected -> {
                    rejectedReasons[st.id] = eligibilityOutcome.reasonCode
                    continue
                }
                is StationPortEligibilityEvaluator.StationEvaluationOutcome.Eligible -> eligibilityOutcome.selection
            }

            // 2. Project station onto route polyline
            var bestCrossTrackMeters = Double.MAX_VALUE
            var bestAlongPolyMeters = 0.0
            var bestSegIdx = 0
            var bestProjLat = routePolyline.first().latitude
            var bestProjLon = routePolyline.first().longitude
            var cumulativeBeforeSeg = 0.0

            for (segIdx in 0 until routePolyline.size - 1) {
                val a = routePolyline[segIdx]
                val b = routePolyline[segIdx + 1]
                val segLen = segmentLengths[segIdx]
                val (fraction, crossMeters) = projectOntoSegment(
                    pLat = st.latitude,
                    pLon = st.longitude,
                    aLat = a.latitude,
                    aLon = a.longitude,
                    bLat = b.latitude,
                    bLon = b.longitude
                )
                if (crossMeters < bestCrossTrackMeters) {
                    bestCrossTrackMeters = crossMeters
                    bestAlongPolyMeters = cumulativeBeforeSeg + fraction * segLen
                    bestSegIdx = segIdx
                    bestProjLat = a.latitude + fraction * (b.latitude - a.latitude)
                    bestProjLon = a.longitude + fraction * (b.longitude - a.longitude)
                }
                cumulativeBeforeSeg += segLen
            }

            val progressFraction = (bestAlongPolyMeters / effectivePolyTotal).coerceIn(0.0, 1.0)
            val distanceAlongRoute = progressFraction * routeDistanceMeters

            if (progressFraction <= 0.01 && bestCrossTrackMeters > 2_000.0) {
                rejectedReasons[st.id] = "STATION_BEHIND_ORIGIN"
                continue
            }
            if (progressFraction >= 0.99 && bestCrossTrackMeters > 2_000.0) {
                rejectedReasons[st.id] = "STATION_BEYOND_DESTINATION"
                continue
            }

            // 3. Geometric cross-track filter (strictly compared against maxCrossTrackMeters)
            if (bestCrossTrackMeters > maxCrossTrackMeters) {
                rejectedReasons[st.id] =
                    "OFF_ROUTE_EXCEEDS_MAX_CROSS_TRACK (${(bestCrossTrackMeters / 1000.0).roundToInt()} km > ${(maxCrossTrackMeters / 1000.0).roundToInt()} km)"
                continue
            }

            // 4. Optional road-graph detour calculation via RoadRouteProvider
            var verifiedRoadDetourMeters: Double? = null
            var detourDurationMinutes: Double? = null
            var detourSource = DetourCalculationSource.CROSS_TRACK_GEOMETRIC_ESTIMATE

            if (roadRouteProvider != null) {
                val projCoord = GeoCoordinate(bestProjLat, bestProjLon)
                val stationCoord = GeoCoordinate(st.latitude, st.longitude)
                val toStation = try {
                    roadRouteProvider.calculateRoute(projCoord, stationCoord)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val fromStation = try {
                    roadRouteProvider.calculateRoute(stationCoord, projCoord)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }

                if (toStation != null && fromStation != null &&
                    toStation.isRealRoadGraph && fromStation.isRealRoadGraph &&
                    (toStation.routeCategory == RouteCategory.AUTHENTIC_ROAD_GRAPH || toStation.sourceClassification.isTrueRoadNetwork) &&
                    (fromStation.routeCategory == RouteCategory.AUTHENTIC_ROAD_GRAPH || fromStation.sourceClassification.isTrueRoadNetwork) &&
                    toStation.distanceMeters.isFinite() && toStation.distanceMeters >= 0.0 &&
                    fromStation.distanceMeters.isFinite() && fromStation.distanceMeters >= 0.0
                ) {
                    val totalRoadDetour = toStation.distanceMeters + fromStation.distanceMeters
                    if (totalRoadDetour > maxRoadDetourMeters) {
                        rejectedReasons[st.id] =
                            "OFF_ROUTE_EXCEEDS_MAX_ROAD_DETOUR (${(totalRoadDetour / 1000.0).roundToInt()} km > ${(maxRoadDetourMeters / 1000.0).roundToInt()} km)"
                        continue
                    }
                    verifiedRoadDetourMeters = totalRoadDetour
                    val totalDurSec = toStation.durationSeconds + fromStation.durationSeconds
                    detourDurationMinutes = if (totalDurSec.isFinite() && totalDurSec >= 0.0) {
                        totalDurSec / 60.0
                    } else {
                        null
                    }
                    detourSource = DetourCalculationSource.ROAD_GRAPH_VERIFIED
                } else {
                    detourSource = DetourCalculationSource.ROUTE_UNAVAILABLE
                }
            }

            accepted.add(
                PredictiveChargingOrchestrator.ProjectedStationCandidate(
                    stationWithPorts = entry,
                    selectedPort = portSelection.port,
                    canonicalConnector = portSelection.canonicalConnector,
                    portEligibility = portSelection,
                    distanceAlongRouteMeters = distanceAlongRoute.coerceIn(
                        100.0,
                        (routeDistanceMeters - 100.0).coerceAtLeast(100.0)
                    ),
                    estimatedCrossTrackMeters = bestCrossTrackMeters,
                    verifiedRoadDetourMeters = verifiedRoadDetourMeters,
                    detourDurationMinutes = detourDurationMinutes,
                    detourCalculationSource = detourSource,
                    routeSegmentIndex = bestSegIdx
                )
            )
        }

        return accepted.sortedBy { it.distanceAlongRouteMeters } to rejectedReasons
    }

    /**
     * Slices a polyline between two cumulative distances `[startDistanceMeters, endDistanceMeters]`
     * and optionally prepends/appends station coordinates so each leg of a multi-stop route has its own
     * distinct road geometry (V13.3.1 Section 2).
     */
    fun slicePolylineForLeg(
        fullPolyline: List<GeoCoordinate>,
        startDistanceMeters: Double,
        endDistanceMeters: Double,
        overrideStartCoord: GeoCoordinate? = null,
        overrideEndCoord: GeoCoordinate? = null
    ): List<GeoCoordinate> {
        if (fullPolyline.size < 2) {
            val s = overrideStartCoord ?: fullPolyline.firstOrNull() ?: return emptyList()
            val e = overrideEndCoord ?: fullPolyline.lastOrNull() ?: s
            return if (s == e) listOf(s) else listOf(s, e)
        }
        val pts = mutableListOf<GeoCoordinate>()
        overrideStartCoord?.let { pts.add(it) }
        var cum = 0.0
        for (i in 0 until fullPolyline.size - 1) {
            val p1 = fullPolyline[i]
            val p2 = fullPolyline[i + 1]
            val segLen = haversineMeters(p1, p2)
            val segStart = cum
            val segEnd = cum + segLen
            cum = segEnd
            if (segEnd < startDistanceMeters) continue
            if (segStart > endDistanceMeters) break
            if (pts.isEmpty() || pts.last() != p1) pts.add(p1)
            if (pts.last() != p2) pts.add(p2)
        }
        overrideEndCoord?.let { endPt ->
            if (pts.isEmpty() || pts.last() != endPt) pts.add(endPt)
        }
        if (pts.size == 1) {
            val fallbackEnd = overrideEndCoord ?: fullPolyline.last()
            if (pts.first() != fallbackEnd) pts.add(fallbackEnd)
        }
        return pts
    }

    fun projectOntoSegment(
        pLat: Double,
        pLon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double
    ): Pair<Double, Double> {
        val midLatRad = Math.toRadians((aLat + bLat) * 0.5)
        val metersPerDegLat = 111_132.0
        val metersPerDegLon = 111_320.0 * cos(midLatRad).coerceAtLeast(0.1)

        val ax = aLon * metersPerDegLon
        val ay = aLat * metersPerDegLat
        val bx = bLon * metersPerDegLon
        val by = bLat * metersPerDegLat
        val px = pLon * metersPerDegLon
        val py = pLat * metersPerDegLat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq > 1e-6) {
            (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val projX = ax + t * dx
        val projY = ay + t * dy
        val distMeters = hypot(px - projX, py - projY)
        return t to distMeters
    }

    fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2.0 * r * atan2(sqrt(h), sqrt(1.0 - h))
    }
}
