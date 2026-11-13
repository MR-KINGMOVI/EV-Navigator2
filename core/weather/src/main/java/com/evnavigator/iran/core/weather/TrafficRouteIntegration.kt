package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.TrafficObservation
import com.evnavigator.iran.core.weather.model.TrafficClass
import com.evnavigator.iran.core.weather.model.TrafficIncident
import com.evnavigator.iran.core.weather.model.TravelTimeEstimate
import com.evnavigator.iran.core.weather.provider.CompositeTrafficProvider
import com.evnavigator.iran.core.weather.provider.TrafficProvider
import java.time.Duration
import java.time.Instant

/**
 * Integrates traffic data with route segments for navigation and planning purposes.
 *
 * This component handles the mapping of traffic observations to specific route segments,
 * enabling accurate ETA calculations, energy consumption modeling, and route optimization.
 */
class TrafficRouteIntegration(
    private val trafficProvider: TrafficProvider = CompositeTrafficProvider()
) {
    
    /**
     * Maps traffic observations to route segments
     *
     * @param routeSegments List of route segments that need traffic information
     * @param trafficObservations List of available traffic observations
     * @return Map of route segments with associated traffic data
     */
    fun mapTrafficToRouteSegments(
        routeSegments: List<RouteSegment>,
        trafficObservations: List<TrafficObservation>
    ): Map<RouteSegment, TrafficDataForSegment> {
        val segmentMap = mutableMapOf<RouteSegment, TrafficDataForSegment>()
        
        // For each route segment, find the most relevant traffic data
        for (segment in routeSegments) {
            val trafficData = findRelevantTrafficForSegment(segment, trafficObservations)
            segmentMap[segment] = trafficData
        }
        
        return segmentMap
    }
    
    /**
     * Finds the most relevant traffic data for a specific route segment
     *
     * @param segment The route segment to analyze
     * @param trafficObservations Available traffic observations
     * @return Traffic data for the segment or default unknown state
     */
    private fun findRelevantTrafficForSegment(
        segment: RouteSegment,
        trafficObservations: List<TrafficObservation>
    ): TrafficDataForSegment {
        // Find traffic observations that are spatially and temporally relevant to this segment
        val relevantTraffic = trafficObservations.filter { observation ->
            isTrafficRelevantToSegment(observation, segment)
        }
        
        // If we have relevant traffic data, process it
        if (relevantTraffic.isNotEmpty()) {
            return processTrafficForSegment(segment, relevantTraffic)
        }
        
        // Return default unknown state if no relevant traffic found
        return TrafficDataForSegment(
            trafficClass = TrafficClass.UNKNOWN,
            speed = null,
            freeFlowSpeed = null,
            congestionRatio = null,
            travelTimeMultiplier = null,
            incidents = emptyList(),
            confidence = 0.0,
            uncertainty = 1.0,
            freshness = Freshness.UNKNOWN
        )
    }
    
    /**
     * Determines if a traffic observation is relevant to a route segment
     */
    private fun isTrafficRelevantToSegment(
        observation: TrafficObservation,
        segment: RouteSegment
    ): Boolean {
        val p = observation.coordinate ?: return true
        val a = segment.startCoordinate
        val b = segment.endCoordinate

        // Quick bounding box check with 0.02 deg (~2km) buffer
        val minLat = minOf(a.latitude, b.latitude) - 0.02
        val maxLat = maxOf(a.latitude, b.latitude) + 0.02
        val minLon = minOf(a.longitude, b.longitude) - 0.02
        val maxLon = maxOf(a.longitude, b.longitude) + 0.02

        if (p.latitude !in minLat..maxLat || p.longitude !in minLon..maxLon) {
            return false
        }

        val dist = distancePointToSegmentMeters(p, a, b)
        return dist <= 500.0 // 500 meter road corridor buffer
    }

    private fun distancePointToSegmentMeters(
        p: GeoCoordinate,
        a: GeoCoordinate,
        b: GeoCoordinate
    ): Double {
        val distAB = a.distanceTo(b)
        if (distAB < 1.0) return p.distanceTo(a)

        val midLat = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosLat = kotlin.math.cos(midLat)
        val r = 6371000.0

        val ax = Math.toRadians(a.longitude) * cosLat * r
        val ay = Math.toRadians(a.latitude) * r
        val bx = Math.toRadians(b.longitude) * cosLat * r
        val by = Math.toRadians(b.latitude) * r
        val px = Math.toRadians(p.longitude) * cosLat * r
        val py = Math.toRadians(p.latitude) * r

        val dx = bx - ax
        val dy = by - ay
        val segLengthSq = dx * dx + dy * dy

        val t = if (segLengthSq > 0.0) {
            (((px - ax) * dx + (py - ay) * dy) / segLengthSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val projX = ax + t * dx
        val projY = ay + t * dy

        val distX = px - projX
        val distY = py - projY
        return kotlin.math.sqrt(distX * distX + distY * distY)
    }
    
    /**
     * Processes multiple traffic observations to determine the best representation for a segment
     */
    internal fun processTrafficForSegment(
        segment: RouteSegment,
        relevantTraffic: List<TrafficObservation>
    ): TrafficDataForSegment {
        val now = Instant.now()
        // Filter out expired observations (> 15 min old) and invalid future timestamps (> 30s clock skew)
        val validTraffic = relevantTraffic.filter { obs ->
            !obs.timestamp.isAfter(now.plusSeconds(30)) &&
            Duration.between(obs.timestamp, now).toMinutes() <= 15
        }

        // Aggregate traffic data from valid unexpired observations
        val speeds = validTraffic.mapNotNull { it.speedMps ?: it.speed }
        val freeFlowSpeeds = validTraffic.mapNotNull { it.freeFlowSpeedMps ?: it.freeFlowSpeed }

        val avgSpeed = if (speeds.isNotEmpty()) speeds.average() else null
        val avgFreeFlowSpeed = if (freeFlowSpeeds.isNotEmpty()) freeFlowSpeeds.average() else null

        val congestionRatio = if (avgSpeed != null && avgFreeFlowSpeed != null && avgFreeFlowSpeed > 0) {
            ((avgFreeFlowSpeed - avgSpeed) / avgFreeFlowSpeed).coerceIn(0.0, 1.0)
        } else {
            null
        }

        val trafficClass = validTraffic.firstOrNull()?.trafficClass?.takeIf { it != TrafficClass.UNKNOWN } ?: when {
            validTraffic.isEmpty() || congestionRatio == null -> TrafficClass.UNKNOWN
            congestionRatio < 0.1 -> TrafficClass.FREE_FLOW
            congestionRatio < 0.3 -> TrafficClass.LIGHT
            congestionRatio < 0.5 -> TrafficClass.MODERATE
            congestionRatio < 0.7 -> TrafficClass.HEAVY
            else -> TrafficClass.SEVERE
        }

        val travelTimeMultiplier = validTraffic.firstOrNull()?.travelTimeMultiplier ?: if (avgSpeed != null && avgFreeFlowSpeed != null && avgSpeed > 0) {
            (avgFreeFlowSpeed / avgSpeed).coerceAtLeast(1.0)
        } else {
            null
        }

        val incidents = emptyList<TrafficIncident>()

        val avgConfidence = if (validTraffic.isNotEmpty()) {
            validTraffic.map { it.confidence }.average().coerceIn(0.0, 1.0)
        } else {
            0.0 // No fabricated confidence
        }
        val avgUncertainty = if (validTraffic.isNotEmpty()) {
            validTraffic.map { it.uncertainty }.average().coerceIn(0.0, 1.0)
        } else {
            1.0 // Complete uncertainty when no valid data
        }

        val freshness = when {
            validTraffic.isEmpty() -> Freshness.UNKNOWN
            validTraffic.any { Duration.between(it.timestamp, now).abs().toMinutes() > 10 } -> Freshness.STALE_BUT_USABLE
            else -> Freshness.FRESH
        }

        return TrafficDataForSegment(
            trafficClass = trafficClass,
            speed = avgSpeed,
            freeFlowSpeed = avgFreeFlowSpeed,
            congestionRatio = congestionRatio,
            travelTimeMultiplier = travelTimeMultiplier,
            incidents = incidents,
            confidence = avgConfidence,
            uncertainty = avgUncertainty,
            freshness = freshness,
            observation = validTraffic.firstOrNull()
        )
    }

    /**
     * Calculates the travel time estimate for a route based on traffic conditions
     *
     * @param segments Route segments with traffic data
     * @param baseTravelTime Base travel time without traffic
     * @return Travel time estimate including traffic impact
     */
    fun calculateTravelTimeWithTraffic(
        segments: List<RouteSegment>,
        baseTravelTime: Double,
        trafficObservations: List<TrafficObservation>? = null
    ): TravelTimeEstimate {
        val segmentTrafficData = if (trafficObservations != null) {
            mapTrafficToRouteSegments(segments, trafficObservations)
        } else {
            segments.associateWith { getTrafficForSegment(it) }
        }
        
        // Calculate total travel time including traffic impacts
        var totalTravelTime = baseTravelTime
        val delays = mutableListOf<Double>()
        
        for ((segment, trafficData) in segmentTrafficData) {
            val segmentDuration = segment.estimatedDurationSeconds.toDouble()
            val trafficMultiplier = trafficData.travelTimeMultiplier ?: 1.0
            
            if (trafficMultiplier > 1.0) {
                val delay = (segmentDuration * (trafficMultiplier - 1.0))
                delays.add(delay)
                totalTravelTime += delay
            }
        }
        
        val multiplier = if (baseTravelTime > 0) totalTravelTime / baseTravelTime else 1.0
        val now = Instant.now()
        
        val avgConfidence = if (segmentTrafficData.isNotEmpty()) {
            segmentTrafficData.values.map { it.confidence }.average().coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val avgUncertainty = (1.0 - avgConfidence).coerceIn(0.0, 1.0)
        val overallFreshness = if (avgConfidence > 0.0) Freshness.FRESH else Freshness.UNKNOWN

        return TravelTimeEstimate(
            baseDuration = baseTravelTime,
            trafficAdjustedDuration = totalTravelTime,
            travelTimeMultiplier = multiplier,
            delay = delays.sum(),
            confidence = avgConfidence,
            uncertainty = avgUncertainty,
            freshness = overallFreshness,
            source = "TrafficRouteIntegration",
            generatedAt = now,
            validFrom = now,
            validTo = now.plusSeconds(3600),
            provenance = "TrafficRouteIntegration"
        )
    }
    
    /**
     * Retrieves baseline segment metadata when no live traffic observations exist.
     * Strictly avoids fabricating 90 km/h, FREE_FLOW, or 0.85 confidence (V13.0 Audit — Item 14 & Item 81).
     */
    private fun getTrafficForSegment(segment: RouteSegment): TrafficDataForSegment {
        val speedLimitMps = segment.speedLimitKmh.takeIf { it > 0 }?.let { it / 3.6 }
        return TrafficDataForSegment(
            trafficClass = TrafficClass.UNKNOWN,
            speed = null,
            freeFlowSpeed = speedLimitMps,
            congestionRatio = null,
            travelTimeMultiplier = null,
            incidents = emptyList(),
            confidence = 0.0,
            uncertainty = 1.0,
            freshness = Freshness.UNKNOWN
        )
    }
}

/**
 * Data class representing traffic information for a specific route segment
 */
data class TrafficDataForSegment(
    val trafficClass: TrafficClass,
    val speed: Double?, // m/s
    val freeFlowSpeed: Double?, // m/s
    val congestionRatio: Double?, // 0.0 to 1.0
    val travelTimeMultiplier: Double?, // > 1.0 means slower than free-flow
    val incidents: List<TrafficIncident>,
    val confidence: Double, // 0.0 to 1.0
    val uncertainty: Double, // 0.0 to 1.0
    val freshness: Freshness, // Freshness state of the data
    val observation: TrafficObservation? = null
)
