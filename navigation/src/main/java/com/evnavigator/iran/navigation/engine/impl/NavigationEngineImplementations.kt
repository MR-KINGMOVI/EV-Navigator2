package com.evnavigator.iran.navigation.engine.impl

import com.evnavigator.iran.domain.model.Battery
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration
import com.evnavigator.iran.domain.model.GeoUtils
import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Power
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.SOCPrediction
import com.evnavigator.iran.domain.model.Temperature
import com.evnavigator.iran.domain.model.navigation.DeviationAction
import com.evnavigator.iran.domain.model.navigation.EtaEstimate
import com.evnavigator.iran.domain.model.navigation.InstructionType
import com.evnavigator.iran.domain.model.navigation.MatchedLocation
import com.evnavigator.iran.domain.model.navigation.NavigationDataQuality
import com.evnavigator.iran.domain.model.navigation.NavigationInstruction
import com.evnavigator.iran.domain.model.navigation.NavigationProgress
import com.evnavigator.iran.domain.model.navigation.RouteDeviation
import com.evnavigator.iran.domain.model.navigation.RoutePosition
import com.evnavigator.iran.domain.model.navigation.maneuver.ManeuverType
import com.evnavigator.iran.domain.model.navigation.maneuver.UpcomingManeuver
import com.evnavigator.iran.navigation.engine.DeviationDetector
import com.evnavigator.iran.navigation.engine.EtaEngine
import com.evnavigator.iran.navigation.engine.InstructionEngine
import com.evnavigator.iran.navigation.engine.LocationQuality
import com.evnavigator.iran.navigation.engine.LocationValidator
import com.evnavigator.iran.navigation.engine.ManeuverEngine
import com.evnavigator.iran.navigation.engine.NavigationEnergyProjector
import com.evnavigator.iran.navigation.engine.RoutePositionResolver
import com.evnavigator.iran.navigation.engine.RouteProgressTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Singleton
class DefaultLocationValidator @Inject constructor() : LocationValidator {

    override fun validateLocation(location: Location): LocationQuality {
        if (location.latitude.isNaN() || location.longitude.isNaN()) return LocationQuality.INVALID
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return LocationQuality.INVALID
        if (location.latitude == 0.0 && location.longitude == 0.0) return LocationQuality.INVALID

        if (isLocationStale(location, maxAgeSeconds = 30L)) {
            return LocationQuality.STALE
        }

        val acc = location.accuracyMeters
        return when {
            acc == null -> LocationQuality.UNKNOWN
            acc <= 0f || acc > 200f -> LocationQuality.INVALID
            acc > 45f -> LocationQuality.DEGRADED
            else -> LocationQuality.VALID
        }
    }

    override fun isLocationStale(location: Location, maxAgeSeconds: Long): Boolean {
        val ageMs = System.currentTimeMillis() - location.timestampEpochMs
        return ageMs > maxAgeSeconds * 1000L
    }

    override fun isLocationJumpSuspicious(
        previousLocation: Location?,
        currentLocation: Location,
        maxDistanceMeters: Double
    ): Boolean {
        if (previousLocation == null) return false
        val distMeters = GeoUtils.haversineMeters(
            previousLocation.toGeoCoordinate(),
            currentLocation.toGeoCoordinate()
        )
        if (distMeters > maxDistanceMeters) return true

        val dtSeconds = ((currentLocation.timestampEpochMs - previousLocation.timestampEpochMs) / 1000.0)
        if (dtSeconds > 0.05) {
            val speedMps = distMeters / dtSeconds
            if (speedMps > 65.0 && distMeters > 100.0) return true // > 234 km/h
        }
        return false
    }
}

@Singleton
class DefaultRoutePositionResolver @Inject constructor() : RoutePositionResolver {

    override fun determineRoutePosition(
        route: Route,
        matchedLocation: MatchedLocation
    ): RoutePosition? {
        return resolvePositionWithContinuity(route, previousPosition = null, matchedLocation = matchedLocation)
    }

    override fun updateRoutePosition(
        route: Route,
        previousPosition: RoutePosition?,
        currentLocation: Location,
        matchedLocation: MatchedLocation
    ): RoutePosition? {
        return resolvePositionWithContinuity(route, previousPosition = previousPosition, matchedLocation = matchedLocation)
    }

    private fun resolvePositionWithContinuity(
        route: Route,
        previousPosition: RoutePosition?,
        matchedLocation: MatchedLocation
    ): RoutePosition? {
        if (route.segments.isEmpty()) return null
        val matchedCoord = matchedLocation.matchedPoint.toGeoCoordinate()

        // 1. Prefer explicit segmentIndex from HMM map matcher when valid
        var segIndex = if (matchedLocation.segmentIndex in route.segments.indices &&
            route.segments[matchedLocation.segmentIndex].id == matchedLocation.roadSegmentId
        ) {
            matchedLocation.segmentIndex
        } else if (previousPosition != null) {
            // Search forward from previous segment index first to prevent jumping backward on loops/parallel roads
            val prevIdx = previousPosition.segmentIndex.coerceIn(0, route.segments.lastIndex)
            val forwardIdx = (prevIdx..route.segments.lastIndex).firstOrNull { idx ->
                route.segments[idx].id == matchedLocation.roadSegmentId ||
                    route.segments[idx].edgeId == matchedLocation.roadSegmentId
            }
            forwardIdx ?: route.segments.indexOfFirst {
                it.id == matchedLocation.roadSegmentId || it.edgeId == matchedLocation.roadSegmentId
            }
        } else {
            route.segments.indexOfFirst {
                it.id == matchedLocation.roadSegmentId || it.edgeId == matchedLocation.roadSegmentId
            }
        }

        if (segIndex < 0) {
            segIndex = route.segments.indices.minByOrNull { idx ->
                val s = route.segments[idx]
                minOf(
                    GeoUtils.haversineMeters(matchedCoord, s.start),
                    GeoUtils.haversineMeters(matchedCoord, s.end)
                )
            } ?: 0
        }

        // Prevent accidental backward jump onto an earlier segment if confidence is moderate and vehicle didn't U-turn
        if (previousPosition != null &&
            segIndex < previousPosition.segmentIndex &&
            matchedLocation.confidence < 0.85
        ) {
            segIndex = previousPosition.segmentIndex.coerceIn(0, route.segments.lastIndex)
        }

        val currentSegment = route.segments[segIndex]
        val segLen = max(1.0, currentSegment.distance.value)

        // Use longitudinal distanceAlongSegmentMeters from map matcher when available;
        // prevent minor backward jitter (< 4m) on the same segment.
        val rawDistOnSeg = if (matchedLocation.distanceAlongSegmentMeters > 0.0 ||
            matchedLocation.segmentIndex == segIndex
        ) {
            matchedLocation.distanceAlongSegmentMeters.coerceIn(0.0, segLen)
        } else {
            GeoUtils.haversineMeters(currentSegment.start, matchedCoord).coerceIn(0.0, segLen)
        }

        val distFromSegStart = if (previousPosition != null &&
            previousPosition.segmentIndex == segIndex &&
            rawDistOnSeg < previousPosition.offsetMeters &&
            (previousPosition.offsetMeters - rawDistOnSeg) < 5.0
        ) {
            previousPosition.offsetMeters
        } else {
            rawDistOnSeg
        }

        val progressOnSeg = (distFromSegStart / segLen).coerceIn(0.0, 1.0)
        val remOnSeg = (segLen - distFromSegStart).coerceAtLeast(0.0)

        var traveledBeforeSeg = 0.0
        for (i in 0 until segIndex) {
            traveledBeforeSeg += route.segments[i].distance.value
        }
        val totalTraveled = traveledBeforeSeg + distFromSegStart
        val totalRouteDist = max(1.0, route.distance.value)
        val totalRemaining = (totalRouteDist - totalTraveled).coerceAtLeast(0.0)
        val routePct = (totalTraveled / totalRouteDist).coerceIn(0.0, 1.0)

        return RoutePosition(
            segment = currentSegment,
            progressAlongSegment = progressOnSeg,
            distanceTraveled = Distance.fromMeters(totalTraveled),
            distanceRemainingOnSegment = Distance.fromMeters(remOnSeg),
            totalDistanceRemaining = Distance.fromMeters(totalRemaining),
            routePercentage = routePct,
            segmentIndex = segIndex,
            edgeId = currentSegment.edgeId,
            direction = currentSegment.direction,
            offsetMeters = distFromSegStart
        )
    }
}

@Singleton
class DefaultDeviationDetector @Inject constructor() : DeviationDetector {

    private var firstDeviationTimestampMs: Long? = null
    private var consecutiveOffRouteSamples: Int = 0
    private var currentOffRouteState: com.evnavigator.iran.domain.model.navigation.OffRouteState =
        com.evnavigator.iran.domain.model.navigation.OffRouteState.NORMAL

    private val recoveryThresholdMeters = 18.0
    private val offRouteThresholdMeters = 45.0
    private val autoRerouteThresholdMeters = 80.0
    private val requiredSamplesForAutoReroute = 3

    fun getCurrentOffRouteState(): com.evnavigator.iran.domain.model.navigation.OffRouteState = currentOffRouteState

    override fun detectDeviation(
        route: Route,
        routePosition: RoutePosition,
        matchedLocation: MatchedLocation,
        currentLocation: Location
    ): RouteDeviation? {
        val distFromRoute = calculateDistanceFromRoute(route, matchedLocation)

        // Hysteresis recovery: if within 18m (or <= 35m after normal driving), reset off-route state
        val isRecovered = if (currentOffRouteState != com.evnavigator.iran.domain.model.navigation.OffRouteState.NORMAL) {
            distFromRoute <= recoveryThresholdMeters
        } else {
            distFromRoute <= offRouteThresholdMeters
        }

        if (isRecovered) {
            firstDeviationTimestampMs = null
            consecutiveOffRouteSamples = 0
            currentOffRouteState = if (currentOffRouteState == com.evnavigator.iran.domain.model.navigation.OffRouteState.SUSPECTED_OFF_ROUTE ||
                currentOffRouteState == com.evnavigator.iran.domain.model.navigation.OffRouteState.REROUTING
            ) {
                com.evnavigator.iran.domain.model.navigation.OffRouteState.ROUTE_RECOVERED
            } else {
                com.evnavigator.iran.domain.model.navigation.OffRouteState.NORMAL
            }
            return RouteDeviation(
                isOffRoute = false,
                deviationDistance = Distance.fromMeters(distFromRoute),
                confidence = matchedLocation.confidence.coerceIn(0.0, 1.0),
                detectionTime = currentLocation.timestampEpochMs,
                suggestedAction = DeviationAction.NO_ACTION
            )
        }

        if (distFromRoute <= offRouteThresholdMeters) {
            // In hysteresis band (18m..45m): hold suspected state without escalating
            return RouteDeviation(
                isOffRoute = false,
                deviationDistance = Distance.fromMeters(distFromRoute),
                confidence = matchedLocation.confidence.coerceIn(0.0, 1.0),
                detectionTime = currentLocation.timestampEpochMs,
                suggestedAction = DeviationAction.CONTINUE
            )
        }

        consecutiveOffRouteSamples += 1
        val now = currentLocation.timestampEpochMs
        val detectedAt = firstDeviationTimestampMs ?: now.also { firstDeviationTimestampMs = it }
        val durationSec = ((now - detectedAt) / 1000.0).coerceAtLeast(0.0)

        val action = when {
            distFromRoute >= autoRerouteThresholdMeters &&
                (durationSec >= 3.0 || consecutiveOffRouteSamples >= requiredSamplesForAutoReroute) -> {
                currentOffRouteState = com.evnavigator.iran.domain.model.navigation.OffRouteState.REROUTING
                DeviationAction.AUTO_REROUTE
            }
            distFromRoute >= autoRerouteThresholdMeters -> {
                currentOffRouteState = com.evnavigator.iran.domain.model.navigation.OffRouteState.SUSPECTED_OFF_ROUTE
                DeviationAction.CONFIRM_REROUTE
            }
            else -> {
                currentOffRouteState = com.evnavigator.iran.domain.model.navigation.OffRouteState.SUSPECTED_OFF_ROUTE
                DeviationAction.CONTINUE
            }
        }

        val devConfidence = ((distFromRoute - offRouteThresholdMeters) / 60.0).coerceIn(0.3, 1.0)

        return RouteDeviation(
            isOffRoute = true,
            deviationDistance = Distance.fromMeters(distFromRoute),
            confidence = devConfidence,
            detectionTime = detectedAt,
            suggestedAction = action
        )
    }

    override fun isRerouteRequired(deviation: RouteDeviation?): Boolean {
        if (deviation == null || !deviation.isOffRoute) return false
        return deviation.suggestedAction == DeviationAction.AUTO_REROUTE ||
            (deviation.deviationDistance.value >= autoRerouteThresholdMeters &&
                deviation.confidence >= 0.6 &&
                consecutiveOffRouteSamples >= requiredSamplesForAutoReroute)
    }

    override fun calculateDistanceFromRoute(
        route: Route,
        matchedLocation: MatchedLocation
    ): Double {
        val rawCoord = matchedLocation.location.toGeoCoordinate()
        val matchedCoord = matchedLocation.matchedPoint.toGeoCoordinate()
        return GeoUtils.haversineMeters(rawCoord, matchedCoord)
    }
}

@Singleton
class DefaultEtaEngine @Inject constructor() : EtaEngine {

    override fun calculateEta(
        route: Route,
        routePosition: RoutePosition,
        dataQuality: NavigationDataQuality
    ): EtaEstimate? {
        val totalDist = route.distance.value
        if (totalDist <= 0.0) return null

        val remainingFraction = (routePosition.totalDistanceRemaining.value / totalDist).coerceIn(0.0, 1.0)
        val remainingSeconds = (route.estimatedTime * remainingFraction).roundToLong().coerceAtLeast(0L)
        val nowMs = System.currentTimeMillis()

        val confidence = when (dataQuality) {
            NavigationDataQuality.LIVE -> 0.92
            NavigationDataQuality.CACHED -> 0.78
            NavigationDataQuality.PARTIAL -> 0.65
            NavigationDataQuality.UNAVAILABLE -> 0.50
            NavigationDataQuality.UNKNOWN -> 0.50
        }

        return EtaEstimate(
            eta = nowMs + remainingSeconds * 1000L,
            travelTime = Duration.ofSeconds(remainingSeconds),
            confidence = confidence,
            isBasedOnLiveTraffic = dataQuality == NavigationDataQuality.LIVE,
            adjustmentReason = if (dataQuality == NavigationDataQuality.LIVE) null else "برآورد آفلاین بر اساس سرعت مجاز معابر"
        )
    }

    override fun updateEta(
        currentEta: EtaEstimate?,
        route: Route,
        routePosition: RoutePosition,
        dataQuality: NavigationDataQuality
    ): EtaEstimate? {
        return calculateEta(route, routePosition, dataQuality)
    }
}

@Singleton
class DefaultManeuverEngine @Inject constructor() : ManeuverEngine {

    override fun determineUpcomingManeuver(
        route: Route,
        routePosition: RoutePosition
    ): UpcomingManeuver? {
        return getNextManeuvers(route, routePosition, count = 1).firstOrNull()
    }

    override fun isManeuverCompleted(
        route: Route,
        routePosition: RoutePosition,
        previousManeuver: UpcomingManeuver?
    ): Boolean {
        if (previousManeuver == null) return false
        return routePosition.routePercentage >= 0.995 ||
            (routePosition.distanceRemainingOnSegment.value < 10.0 && routePosition.progressAlongSegment > 0.95)
    }

    override fun getNextManeuvers(
        route: Route,
        routePosition: RoutePosition,
        count: Int
    ): List<UpcomingManeuver> {
        if (route.segments.isEmpty() || count <= 0) return emptyList()
        val currentIdx = routePosition.segmentIndex.takeIf { it in route.segments.indices }
            ?: route.segments.indexOfFirst { it.id == routePosition.segment.id }.coerceAtLeast(0)
        val results = mutableListOf<UpcomingManeuver>()

        val currentSegSpeedMps = (routePosition.segment.speedLimitKmh.toDouble() / 3.6).coerceIn(5.0, 36.0)
        var accumulatedDist = routePosition.distanceRemainingOnSegment.value
        var accumulatedTimeSec = (accumulatedDist / currentSegSpeedMps)

        for (idx in (currentIdx + 1) until route.segments.size) {
            if (results.size >= count) break
            val prevSeg = route.segments[idx - 1]
            val nextSeg = route.segments[idx]
            val maneuverType = classifyTurnManeuver(
                fromBearing = prevSeg.bearing.degrees,
                toBearing = nextSeg.bearing.degrees,
                prevRoadName = prevSeg.roadName,
                nextRoadName = nextSeg.roadName,
                prevRoadType = prevSeg.roadType,
                nextRoadType = nextSeg.roadType
            )
            val timeSec = accumulatedTimeSec.roundToLong().coerceAtLeast(1L)

            results.add(
                UpcomingManeuver(
                    type = maneuverType,
                    roadName = nextSeg.roadName ?: "مسیر اصلی",
                    exitNumber = null,
                    distanceToManeuver = Distance.fromMeters(accumulatedDist),
                    timeToManeuver = Duration.ofSeconds(timeSec),
                    direction = maneuverType.name.lowercase()
                )
            )
            val segSpeedMps = (nextSeg.speedLimitKmh.toDouble() / 3.6).coerceIn(5.0, 36.0)
            accumulatedDist += nextSeg.distance.value
            accumulatedTimeSec += (nextSeg.distance.value / segSpeedMps)
        }

        if (results.isEmpty()) {
            val remDist = routePosition.totalDistanceRemaining.value
            val remSec = (remDist / currentSegSpeedMps).roundToLong().coerceAtLeast(0L)
            results.add(
                UpcomingManeuver(
                    type = ManeuverType.ARRIVE,
                    roadName = routePosition.segment.roadName ?: "مقصد",
                    exitNumber = null,
                    distanceToManeuver = Distance.fromMeters(remDist),
                    timeToManeuver = Duration.ofSeconds(remSec),
                    direction = "straight"
                )
            )
        }

        return results
    }

    private fun classifyTurnManeuver(
        fromBearing: Double,
        toBearing: Double,
        prevRoadName: String? = null,
        nextRoadName: String? = null,
        prevRoadType: String? = null,
        nextRoadType: String? = null
    ): ManeuverType {
        val nextNameLower = nextRoadName?.lowercase() ?: ""
        if (nextNameLower.contains("میدان") || nextNameLower.contains("فلکه") || nextNameLower.contains("roundabout")) {
            return ManeuverType.ROUNDABOUT
        }
        val delta = ((toBearing - fromBearing + 540.0) % 360.0) - 180.0
        val absDelta = abs(delta)
        if (absDelta >= 155.0 || nextNameLower.contains("دوربرگردان")) {
            return ManeuverType.UTURN
        }
        if (nextNameLower.contains("خروجی") || nextRoadType?.contains("link", ignoreCase = true) == true) {
            return if (delta < 0) ManeuverType.EXIT_LEFT else ManeuverType.EXIT_RIGHT
        }
        if (prevRoadType != nextRoadType && nextRoadType?.contains("motorway", ignoreCase = true) == true && absDelta < 45.0) {
            return ManeuverType.MERGE
        }
        return when {
            absDelta < 18.0 -> ManeuverType.CONTINUE
            delta in 18.0..45.0 -> ManeuverType.SLIGHT_RIGHT
            delta in 45.0..120.0 -> ManeuverType.TURN_RIGHT
            delta > 120.0 -> ManeuverType.SHARP_RIGHT
            delta in -45.0..-18.0 -> ManeuverType.SLIGHT_LEFT
            delta in -120.0..-45.0 -> ManeuverType.TURN_LEFT
            else -> ManeuverType.SHARP_LEFT
        }
    }
}

@Singleton
class DefaultInstructionEngine @Inject constructor() : InstructionEngine {

    override fun generateInstruction(maneuver: UpcomingManeuver): NavigationInstruction {
        val road = maneuver.roadName?.takeIf { it.isNotBlank() } ?: "مسیر اصلی"
        val distMeters = maneuver.distanceToManeuver.value
        val distLabel = if (distMeters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f کیلومتر", distMeters / 1000.0)
        } else {
            "${distMeters.roundToInt()} متر"
        }

        val (text, instType) = when (maneuver.type) {
            ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT ->
                "در $distLabel به چپ بپیچید به سمت $road" to InstructionType.TURN
            ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT ->
                "در $distLabel به راست بپیچید به سمت $road" to InstructionType.TURN
            ManeuverType.SLIGHT_LEFT, ManeuverType.KEEP_LEFT ->
                "در $distLabel کمی به چپ متمایل شوید در $road" to InstructionType.TURN
            ManeuverType.SLIGHT_RIGHT, ManeuverType.KEEP_RIGHT ->
                "در $distLabel کمی به راست متمایل شوید در $road" to InstructionType.TURN
            ManeuverType.U_TURN ->
                "در $distLabel دور برگردانید به سمت $road" to InstructionType.TURN
            ManeuverType.ENTER_ROUNDABOUT, ManeuverType.EXIT_ROUNDABOUT ->
                "در $distLabel وارد میدان شده و به سمت $road خارج شوید" to InstructionType.ROUNDABOUT
            ManeuverType.TAKE_EXIT, ManeuverType.TAKE_RAMP ->
                "در $distLabel از خروجی به سمت $road خارج شوید" to InstructionType.EXIT
            ManeuverType.ARRIVE ->
                "به مقصد رسیدید ($road)" to InstructionType.DESTINATION
            else ->
                "به مدت $distLabel در $road مستقیم ادامه دهید" to InstructionType.CONTINUE
        }

        return NavigationInstruction(
            text = text,
            type = instType,
            distanceToNextManeuver = maneuver.distanceToManeuver,
            timeToNextManeuver = maneuver.timeToManeuver
        )
    }

    override fun generateInstructions(maneuvers: List<UpcomingManeuver>): List<NavigationInstruction> {
        return maneuvers.map { generateInstruction(it) }
    }

    override fun generateArrivalInstruction(): NavigationInstruction {
        return NavigationInstruction(
            text = "به مقصد رسیدید",
            type = InstructionType.DESTINATION,
            distanceToNextManeuver = Distance.ZERO,
            timeToNextManeuver = Duration.ZERO
        )
    }
}

@Singleton
class DefaultRouteProgressTracker @Inject constructor() : RouteProgressTracker {

    private var startTimestampMs: Long = System.currentTimeMillis()

    override fun trackProgress(
        route: Route,
        routePosition: RoutePosition
    ): NavigationProgress? {
        return calculateProgress(route, routePosition)
    }

    override fun calculateProgress(
        route: Route,
        routePosition: RoutePosition
    ): NavigationProgress? {
        if (route.segments.isEmpty()) return null
        val segIdx = route.segments.indexOfFirst { it.id == routePosition.segment.id }.coerceAtLeast(0)
        val elapsedSec = ((System.currentTimeMillis() - startTimestampMs) / 1000L).coerceAtLeast(0L)

        return NavigationProgress(
            distanceTraveled = routePosition.distanceTraveled,
            totalDistance = route.distance,
            routePercentage = routePosition.routePercentage.coerceIn(0.0, 1.0),
            elapsedTime = Duration.ofSeconds(elapsedSec),
            currentSegmentIndex = segIdx,
            totalSegments = route.segments.size
        )
    }
}

@Singleton
class DefaultNavigationEnergyProjector @Inject constructor() : NavigationEnergyProjector {

    private val defaultBatteryCapacityKwh = 60.0
    private val baseConsumptionKwhPerKm = 0.165 // 165 Wh/km WLTP reference

    override fun projectEnergy(
        route: Route,
        routePosition: RoutePosition,
        currentSoc: Double
    ): SOCPrediction? {
        val safeSoc = currentSoc.coerceIn(0.0, 100.0)
        val remainingKm = (routePosition.totalDistanceRemaining.value / 1000.0).coerceAtLeast(0.0)
        val traveledKm = (routePosition.distanceTraveled.value / 1000.0).coerceAtLeast(0.0)

        // Grade factor from route climb/descent
        val gradeFactor = (1.0 + (route.averageGrade * 0.035)).coerceIn(0.65, 1.85)
        val remainingEnergyKwh = remainingKm * baseConsumptionKwhPerKm * gradeFactor
        val consumedSoFarKwh = traveledKm * baseConsumptionKwhPerKm

        val socDropPct = (remainingEnergyKwh / defaultBatteryCapacityKwh) * 100.0
        val predictedArrivalSoc = (safeSoc - socDropPct).coerceIn(0.0, 100.0)

        val remainingFraction = if (route.distance.value > 0) {
            (routePosition.totalDistanceRemaining.value / route.distance.value).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val remainingTimeSec = (route.estimatedTime * remainingFraction).roundToLong().coerceAtLeast(0L)

        val predictedBattery = Battery(
            capacity = defaultBatteryCapacityKwh,
            currentSoC = predictedArrivalSoc,
            temperature = Temperature.fromCelsius(25.0),
            health = 100.0,
            maxChargePower = Power.fromKilowatts(120.0),
            minSoC = 10.0,
            optimalMaxSoC = 80.0
        )

        return SOCPrediction(
            timestamp = System.currentTimeMillis() + remainingTimeSec * 1000L,
            soc = predictedArrivalSoc,
            distanceTraveled = traveledKm,
            energyConsumed = consumedSoFarKwh,
            estimatedRemainingTime = remainingTimeSec,
            estimatedRemainingDistance = remainingKm,
            predictedBatteryState = predictedBattery
        )
    }

    override fun updateEnergyProjection(
        currentProjection: SOCPrediction?,
        route: Route,
        routePosition: RoutePosition,
        currentSoc: Double
    ): SOCPrediction? {
        return projectEnergy(route, routePosition, currentSoc)
    }
}
