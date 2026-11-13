package com.evnavigator.iran.core.routing.provider

import com.evnavigator.iran.core.routing.codec.PolylineCodec
import com.evnavigator.iran.core.routing.model.LaneGuidanceInfo
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.core.routing.model.RoutePreferences
import com.evnavigator.iran.core.routing.model.RoutingError
import com.evnavigator.iran.core.routing.network.NetworkRoutingHttpClient
import com.evnavigator.iran.core.routing.validation.RouteIntegrityValidator
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.RoutingPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Online routing provider backed by OSRM (Open Source Routing Machine) (V13.0 Audit — Items 3, 4, 28–39, 91–93, 165).
 */
class OsrmRoutingProvider(
    private val baseUrls: List<String> = listOf(
        "https://router.project-osrm.org/route/v1/driving",
        "https://routing.openstreetmap.de/routed-car/route/v1/driving"
    ),
    override val priority: Int = 1
) : RoutingProvider {

    constructor(baseUrl: String, priority: Int = 1) : this(
        baseUrls = listOf(baseUrl),
        priority = priority
    )

    override val providerName: String = "OSRM-Online"
    override val sourceType: RoutingSourceType = RoutingSourceType.ONLINE_PRIMARY

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        providerName = providerName,
        supportsAlternativeRoutes = true,
        supportsAvoidTolls = true,
        supportsAvoidFerries = true,
        supportsAvoidTunnels = false,
        supportsAvoidUnpaved = false,
        supportsShortestOptimization = false,
        supportsWaypoints = true,
        supportsElevationProfile = false
    )

    override suspend fun isAvailable(origin: GeoCoordinate, destination: GeoCoordinate): Boolean {
        return baseUrls.any { it.isNotBlank() } &&
            RouteIntegrityValidator.isValidCoordinate(origin) &&
            RouteIntegrityValidator.isValidCoordinate(destination) &&
            NetworkRoutingHttpClient.sharedCircuitBreaker.allowRequest(providerName)
    }

    override suspend fun calculateRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences
    ): Result<EnrichedRouteResult> = withContext(Dispatchers.IO) {
        fetchOsrmRoutes(origin, destination, waypoints, preferences, maxAlternatives = 3).fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    Result.failure(RoutingError.NoPathFound(origin, destination, "OSRM returned empty route list"))
                } else {
                    val primary = list.first()
                    val alts = list.drop(1)
                    Result.success(
                        primary.copy(
                            route = primary.route.copy(alternativeRoutes = alts.map { it.route }),
                            alternatives = alts
                        )
                    )
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun calculateAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<List<EnrichedRouteResult>> = withContext(Dispatchers.IO) {
        fetchOsrmRoutes(origin, destination, emptyList(), preferences, maxAlternatives.coerceIn(1, 5))
    }

    private suspend fun fetchOsrmRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<List<EnrichedRouteResult>> {
        RouteIntegrityValidator.validateWaypoints(origin, destination, waypoints)
            .onFailure { return Result.failure(it) }

        val appliedPrefs = mutableListOf<String>()
        val unsupportedPrefs = mutableListOf<String>()
        val excludes = mutableListOf<String>()

        if (preferences.avoidTolls) { excludes.add("toll"); appliedPrefs.add("avoidTolls") }
        if (preferences.avoidFerries) { excludes.add("ferry"); appliedPrefs.add("avoidFerries") }
        if (preferences.avoidTunnels) {
            if (preferences.strictPreferenceEnforcement) return Result.failure(RoutingError.UnsupportedPreference(providerName, "avoidTunnels"))
            unsupportedPrefs.add("avoidTunnels")
        }
        if (preferences.avoidUnpavedRoads) unsupportedPrefs.add("avoidUnpavedRoads")
        if (preferences.optimizeFor == RoutingPreference.SHORTEST) {
            if (preferences.strictPreferenceEnforcement) return Result.failure(RoutingError.UnsupportedPreference(providerName, "optimizeFor=SHORTEST"))
            unsupportedPrefs.add("optimizeFor=SHORTEST")
        } else {
            appliedPrefs.add("optimizeFor=${preferences.optimizeFor.name}")
        }

        val allPoints = buildList { add(origin); addAll(waypoints); add(destination) }
        val coordsSegment = allPoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val queryParams = buildList {
            add("overview" to "full")
            add("geometries" to "geojson")
            add("steps" to "true")
            add("annotations" to "true")
            add("alternatives" to if (maxAlternatives > 1) "true" else "false")
            if (excludes.isNotEmpty()) add("exclude" to excludes.joinToString(","))
        }

        var lastError: Throwable? = null
        for (baseUrl in baseUrls) {
            if (baseUrl.isBlank()) continue
            val endpointUrl = try {
                NetworkRoutingHttpClient.buildSafeUrl(baseUrl, coordsSegment, queryParams)
            } catch (e: Exception) {
                lastError = RoutingError.NetworkFailure("Invalid OSRM URL: ${e.message}", e)
                continue
            }
            val responseResult = NetworkRoutingHttpClient.executeGet(providerName, endpointUrl)
            if (responseResult.isFailure) {
                lastError = responseResult.exceptionOrNull()
                continue
            }
            val parsed = parseOsrmResponse(
                jsonText = responseResult.getOrThrow(),
                origin = origin,
                destination = destination,
                maxAlternatives = maxAlternatives,
                appliedPrefs = appliedPrefs,
                unsupportedPrefs = unsupportedPrefs
            )
            if (parsed.isSuccess) return parsed else lastError = parsed.exceptionOrNull()
        }

        return Result.failure(
            lastError ?: RoutingError.NetworkFailure("All OSRM endpoints failed to return a valid road route")
        )
    }

    internal fun parseOsrmResponse(
        jsonText: String,
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        maxAlternatives: Int,
        appliedPrefs: List<String> = emptyList(),
        unsupportedPrefs: List<String> = emptyList()
    ): Result<List<EnrichedRouteResult>> {
        try {
            val root = JSONObject(jsonText)
            val code = root.optString("code", "")
            if (!code.equals("Ok", ignoreCase = true)) {
                return Result.failure(RoutingError.NoPathFound(origin, destination, root.optString("message", "OSRM status: $code")))
            }
            val routesArray = root.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                return Result.failure(RoutingError.NoPathFound(origin, destination, "OSRM returned zero routes"))
            }

            val waypointsArray = root.optJSONArray("waypoints")
            val reportedOriginSnap = waypointsArray?.optJSONObject(0)?.optDouble("distance", Double.NaN)?.takeIf { it.isFinite() && it >= 0.0 }
            val reportedDestSnap = waypointsArray?.let {
                if (it.length() > 1) it.optJSONObject(it.length() - 1)?.optDouble("distance", Double.NaN) else null
            }?.takeIf { it.isFinite() && it >= 0.0 }

            val results = ArrayList<EnrichedRouteResult>()
            val seenSignatures = LinkedHashSet<String>()
            val limit = minOf(routesArray.length(), maxAlternatives.coerceAtLeast(1))

            for (rIdx in 0 until routesArray.length()) {
                if (results.size >= limit) break
                val routeObj = routesArray.optJSONObject(rIdx) ?: continue
                val totalDistance = routeObj.optDouble("distance", Double.NaN)
                val totalDuration = routeObj.optDouble("duration", Double.NaN)

                val geometryObj = routeObj.optJSONObject("geometry")
                    ?: return Result.failure(RoutingError.RouteGeometryMissing(providerName, "OSRM route[$rIdx] missing 'geometry'"))
                val coordsArray = geometryObj.optJSONArray("coordinates")
                    ?: return Result.failure(RoutingError.RouteGeometryMissing(providerName, "OSRM route[$rIdx] missing 'coordinates'"))

                val polyline = ArrayList<GeoCoordinate>(coordsArray.length())
                for (i in 0 until coordsArray.length()) {
                    val pair = coordsArray.optJSONArray(i) ?: continue
                    if (pair.length() < 2) continue
                    val lon = pair.optDouble(0, Double.NaN)
                    val lat = pair.optDouble(1, Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val pt = GeoCoordinate(lat, lon)
                    val prev = polyline.lastOrNull()
                    if (prev == null || prev.latitude != pt.latitude || prev.longitude != pt.longitude) polyline.add(pt)
                }

                RouteIntegrityValidator.validateDecodedRouteGeometry(providerName, polyline, totalDistance, totalDuration)
                    .onFailure { if (rIdx == 0) return Result.failure(it) else continue }

                val sig = PolylineCodec.geometrySignature(polyline)
                if (!seenSignatures.add(sig)) continue

                val maneuvers = ArrayList<RealRoadManeuver>()
                val legsArray = routeObj.optJSONArray("legs")
                var mainSummary = ""
                var stepCounter = 0
                var cumulativeDistance = 0.0

                if (legsArray != null) {
                    for (lIdx in 0 until legsArray.length()) {
                        val leg = legsArray.optJSONObject(lIdx) ?: continue
                        val legSummary = leg.optString("summary", "")
                        if (legSummary.isNotBlank() && mainSummary.isBlank()) mainSummary = legSummary
                        val stepsArray = leg.optJSONArray("steps") ?: continue
                        for (sIdx in 0 until stepsArray.length()) {
                            val step = stepsArray.optJSONObject(sIdx) ?: continue
                            val stepDist = step.optDouble("distance", 0.0).coerceAtLeast(0.0)
                            val stepDur = step.optDouble("duration", 0.0).coerceAtLeast(0.0)
                            val roadName = step.optString("name", "").ifBlank { step.optString("ref", "مسیر اصلی").ifBlank { "مسیر اصلی" } }
                            val maneuverObj = step.optJSONObject("maneuver")
                            val locArray = maneuverObj?.optJSONArray("location")
                            val stepCoord = if (locArray != null && locArray.length() >= 2) {
                                GeoCoordinate(locArray.optDouble(1, origin.latitude), locArray.optDouble(0, origin.longitude))
                            } else polyline.first()

                            val rawType = maneuverObj?.optString("type", "") ?: ""
                            val rawModifier = maneuverObj?.optString("modifier", "") ?: ""
                            val roundaboutExit = if (maneuverObj?.has("exit") == true) maneuverObj.optInt("exit").takeIf { it > 0 } else null
                            val maneuverType = parseOsrmManeuverType(rawType, rawModifier)

                            val explicitMaxSpeed = step.optJSONObject("maxspeed")
                                ?.optInt("speed", 0)
                                ?.takeIf { it in 5..140 } ?: 0

                            maneuvers.add(
                                RealRoadManeuver(
                                    stepIndex = stepCounter,
                                    coordinate = stepCoord,
                                    roadName = roadName,
                                    instructionFarsi = buildFarsiInstruction(maneuverType, roadName, stepDist, roundaboutExit),
                                    instructionEn = buildEnglishInstruction(maneuverType, roadName, stepDist, roundaboutExit),
                                    maneuverType = maneuverType,
                                    distanceMeters = stepDist,
                                    durationSeconds = stepDur,
                                    speedLimitKmh = explicitMaxSpeed,
                                    laneGuidance = parseLaneGuidance(step),
                                    edgeId = "osrm_edge_${rIdx}_$stepCounter",
                                    segmentIndex = stepCounter,
                                    bearingBefore = maneuverObj?.optDouble("bearing_before", 0.0) ?: 0.0,
                                    bearingAfter = maneuverObj?.optDouble("bearing_after", 0.0) ?: 0.0,
                                    exitNumber = roundaboutExit?.toString(),
                                    roundaboutExitNumber = roundaboutExit,
                                    cumulativeDistanceMeters = cumulativeDistance
                                )
                            )
                            cumulativeDistance += stepDist
                            stepCounter++
                        }
                    }
                }

                val originSnap = reportedOriginSnap ?: RouteIntegrityValidator.haversineMeters(origin, polyline.first())
                val destSnap = reportedDestSnap ?: RouteIntegrityValidator.haversineMeters(destination, polyline.last())
                val segments = RouteIntegrityValidator.buildContiguousSegmentsFromManeuvers("osrm_$rIdx", polyline, maneuvers, totalDistance)

                val roadRoute = RealRoadRouteResult(
                    polyline = polyline,
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    maneuvers = maneuvers,
                    isOnlineRouting = true,
                    routeSummary = mainSummary.ifBlank { "مسیر جاده‌ای مستقیم" },
                    originSnapDistanceMeters = originSnap,
                    destinationSnapDistanceMeters = destSnap,
                    routeId = "osrm_${rIdx}_${sig.hashCode()}",
                    segments = segments
                )

                results.add(
                    EnrichedRouteResult(
                        route = roadRoute,
                        provenance = RouteProvenance(
                            providerName = providerName,
                            sourceType = sourceType,
                            graphVersion = "osrm-live",
                            confidenceScore = 0.95,
                            alternativeRouteSupport = if (routesArray.length() > 1) AlternativeRouteSupport.SUPPORTED_AND_RETURNED else AlternativeRouteSupport.SUPPORTED_NONE_AVAILABLE,
                            unsupportedPreferences = unsupportedPrefs,
                            appliedPreferences = appliedPrefs,
                            originSnapDistanceMeters = originSnap,
                            destinationSnapDistanceMeters = destSnap
                        )
                    )
                )
            }

            if (results.isEmpty()) {
                return Result.failure(RoutingError.RouteGeometryMissing(providerName, "No valid OSRM routes survived geometry validation"))
            }
            return Result.success(results)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            return Result.failure(RoutingError.MalformedResponse(providerName, "Failed to parse OSRM JSON: ${e.message}", e))
        }
    }

    internal fun parseOsrmManeuverType(type: String, modifier: String): RealManeuverType = when (type.lowercase()) {
        "depart" -> RealManeuverType.DEPART
        "arrive" -> RealManeuverType.ARRIVE
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> RealManeuverType.ROUNDABOUT
        "merge" -> RealManeuverType.MERGE
        "on ramp" -> RealManeuverType.RAMP_ENTER
        "off ramp" -> RealManeuverType.RAMP_EXIT
        "fork" -> if (modifier.contains("left")) RealManeuverType.FORK_LEFT else RealManeuverType.FORK_RIGHT
        "continue", "new name", "notification" -> RealManeuverType.CONTINUE
        "turn", "end of road" -> when (modifier.lowercase()) {
            "left" -> RealManeuverType.TURN_LEFT
            "right" -> RealManeuverType.TURN_RIGHT
            "slight left" -> RealManeuverType.SLIGHT_LEFT
            "slight right" -> RealManeuverType.SLIGHT_RIGHT
            "sharp left" -> RealManeuverType.SHARP_LEFT
            "sharp right" -> RealManeuverType.SHARP_RIGHT
            "uturn" -> RealManeuverType.UTURN
            "straight" -> RealManeuverType.CONTINUE
            else -> RealManeuverType.UNKNOWN
        }
        else -> RealManeuverType.UNKNOWN
    }

    private fun parseLaneGuidance(step: JSONObject): LaneGuidanceInfo {
        val intersections = step.optJSONArray("intersections") ?: return LaneGuidanceInfo.UNKNOWN
        for (i in 0 until intersections.length()) {
            val lanes = intersections.optJSONObject(i)?.optJSONArray("lanes") ?: continue
            if (lanes.length() == 0) continue
            val activeIndices = mutableListOf<Int>()
            val arrows = mutableListOf<String>()
            for (lIdx in 0 until lanes.length()) {
                val lane = lanes.optJSONObject(lIdx) ?: continue
                if (lane.optBoolean("valid", false)) activeIndices.add(lIdx)
                val indications = lane.optJSONArray("indications")
                arrows.add(if (indications != null && indications.length() > 0) indications.optString(0, "straight") else "straight")
            }
            return LaneGuidanceInfo(lanes.length(), activeIndices, arrows, true)
        }
        return LaneGuidanceInfo.UNKNOWN
    }

    private fun buildFarsiInstruction(type: RealManeuverType, roadName: String, distMeters: Double, roundaboutExit: Int? = null): String {
        val distText = if (distMeters >= 1000) String.format("%.1f کیلومتر", distMeters / 1000.0) else "${distMeters.toInt()} متر"
        return when (type) {
            RealManeuverType.DEPART -> "حرکت در $roadName به مدت $distText"
            RealManeuverType.TURN_LEFT -> "گردش به چپ به سمت $roadName"
            RealManeuverType.TURN_RIGHT -> "گردش به راست به سمت $roadName"
            RealManeuverType.SLIGHT_LEFT -> "متمایل به چپ در $roadName"
            RealManeuverType.SLIGHT_RIGHT -> "متمایل به راست در $roadName"
            RealManeuverType.SHARP_LEFT -> "گردش تند به چپ به سمت $roadName"
            RealManeuverType.SHARP_RIGHT -> "گردش تند به راست به سمت $roadName"
            RealManeuverType.CONTINUE -> "ادامه مسیر مستقیم در $roadName ($distText)"
            RealManeuverType.MERGE -> "ورود به بزرگراه $roadName"
            RealManeuverType.FORK_LEFT -> "در دوراهی به چپ به سمت $roadName"
            RealManeuverType.FORK_RIGHT -> "در دوراهی به راست به سمت $roadName"
            RealManeuverType.RAMP_ENTER -> "ورود به رمپ $roadName"
            RealManeuverType.RAMP_EXIT -> "خروج از بزرگراه به سمت $roadName"
            RealManeuverType.ROUNDABOUT -> if (roundaboutExit != null) "در میدان از خروجی $roundaboutExit به سمت $roadName خارج شوید" else "عبور از میدان به سمت $roadName"
            RealManeuverType.UTURN -> "دور برگردان در $roadName"
            RealManeuverType.ARRIVE -> "رسیدن به مقصد در $roadName"
            RealManeuverType.UNKNOWN -> "ادامه مسیر با احتیاط در $roadName"
        }
    }

    private fun buildEnglishInstruction(type: RealManeuverType, roadName: String, distMeters: Double, roundaboutExit: Int? = null): String {
        val distText = if (distMeters >= 1000) String.format("%.1f km", distMeters / 1000.0) else "${distMeters.toInt()} m"
        return when (type) {
            RealManeuverType.DEPART -> "Head on $roadName for $distText"
            RealManeuverType.TURN_LEFT -> "Turn left onto $roadName"
            RealManeuverType.TURN_RIGHT -> "Turn right onto $roadName"
            RealManeuverType.SLIGHT_LEFT -> "Keep left onto $roadName"
            RealManeuverType.SLIGHT_RIGHT -> "Keep right onto $roadName"
            RealManeuverType.SHARP_LEFT -> "Sharp left onto $roadName"
            RealManeuverType.SHARP_RIGHT -> "Sharp right onto $roadName"
            RealManeuverType.CONTINUE -> "Continue straight on $roadName ($distText)"
            RealManeuverType.MERGE -> "Merge onto $roadName"
            RealManeuverType.FORK_LEFT -> "Keep left at the fork toward $roadName"
            RealManeuverType.FORK_RIGHT -> "Keep right at the fork toward $roadName"
            RealManeuverType.RAMP_ENTER -> "Take the ramp onto $roadName"
            RealManeuverType.RAMP_EXIT -> "Take the exit toward $roadName"
            RealManeuverType.ROUNDABOUT -> if (roundaboutExit != null) "At the roundabout, take exit $roundaboutExit onto $roadName" else "Enter the roundabout toward $roadName"
            RealManeuverType.UTURN -> "Make a U-turn on $roadName"
            RealManeuverType.ARRIVE -> "Arrive at destination on $roadName"
            RealManeuverType.UNKNOWN -> "Proceed with caution on $roadName"
        }
    }
}
