package com.evnavigator.iran.core.routing.provider

import com.evnavigator.iran.core.routing.codec.PolylineCodec
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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Online routing provider backed by a GraphHopper instance (V13.0 Audit — Items 2, 3, 4, 28–39, 91–93, 165).
 *
 * Key guarantees:
 * 1. Supports both GeoJSON `points.coordinates` (`points_encoded=false`) and encoded polyline strings
 *    (`points_encoded=true`, precision 5).
 * 2. Strictly rejects any response where `points` is missing or decodes to `< 2` coordinates with
 *    [RoutingError.RouteGeometryMissing] — never falls back to a 2-point straight line.
 * 3. Encodes [RoutePreferences] (`avoid=toll;ferry;tunnel;unpaved`, `weighting=shortest|fastest`) and
 *    uses `NetworkRoutingHttpClient` with API key masking.
 * 4. Parses multiple `paths` when `algorithm=alternative_route` is used and deduplicates via geometry signature.
 */
class GraphHopperRoutingProvider(
    private val baseUrl: String = "",
    private val apiKey: String = "",
    override val priority: Int = 3
) : RoutingProvider {

    override val providerName: String = "GraphHopper-Online"
    override val sourceType: RoutingSourceType = RoutingSourceType.ONLINE_PRIMARY

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        providerName = providerName,
        supportsAlternativeRoutes = true,
        supportsAvoidTolls = true,
        supportsAvoidFerries = true,
        supportsAvoidTunnels = true,
        supportsAvoidUnpaved = true,
        supportsShortestOptimization = true,
        supportsWaypoints = true,
        supportsElevationProfile = true
    )

    override suspend fun isAvailable(origin: GeoCoordinate, destination: GeoCoordinate): Boolean {
        return baseUrl.isNotBlank() &&
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
        executeGraphHopperQuery(
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            preferences = preferences,
            maxAlternatives = if (waypoints.isEmpty()) 2 else 1
        )
    }

    override suspend fun calculateAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<List<EnrichedRouteResult>> = withContext(Dispatchers.IO) {
        val primaryResult = executeGraphHopperQuery(
            origin = origin,
            destination = destination,
            waypoints = emptyList(),
            preferences = preferences,
            maxAlternatives = maxAlternatives.coerceIn(1, 5)
        )
        primaryResult.map { enriched ->
            buildList {
                add(enriched)
                addAll(enriched.alternatives)
            }.take(maxAlternatives.coerceAtLeast(1))
        }
    }

    private suspend fun executeGraphHopperQuery(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<EnrichedRouteResult> {
        if (baseUrl.isBlank()) {
            return Result.failure(
                RoutingError.ProviderUnavailable(providerName, "GraphHopper baseUrl is not configured")
            )
        }
        RouteIntegrityValidator.validateWaypoints(origin, destination, waypoints)
            .onFailure { return Result.failure(it) }

        val appliedPrefs = mutableListOf<String>()
        val unsupportedPrefs = mutableListOf<String>()
        val queryParams = buildGraphHopperQueryParams(
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            preferences = preferences,
            maxAlternatives = maxAlternatives,
            appliedPrefs = appliedPrefs
        )

        val endpointUrl = try {
            NetworkRoutingHttpClient.buildSafeUrl(baseUrl, "/route", queryParams)
        } catch (e: Exception) {
            return Result.failure(RoutingError.NetworkFailure("Invalid GraphHopper URL: ${e.message}", e))
        }

        val responseText = NetworkRoutingHttpClient.executeGet(
            providerName = providerName,
            url = endpointUrl
        ).getOrElse { return Result.failure(it) }

        return parseGraphHopperPayload(
            rawJson = responseText,
            origin = origin,
            destination = destination,
            appliedPrefs = appliedPrefs,
            unsupportedPrefs = unsupportedPrefs,
            requestedAlternatives = maxAlternatives
        )
    }

    internal fun buildGraphHopperQueryParams(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences,
        maxAlternatives: Int,
        appliedPrefs: MutableList<String>
    ): List<Pair<String, String>> {
        val params = ArrayList<Pair<String, String>>()
        val allPoints = buildList {
            add(origin)
            addAll(waypoints)
            add(destination)
        }
        for (p in allPoints) {
            params.add("point" to "${p.latitude},${p.longitude}")
        }
        params.add("profile" to "car")
        params.add("locale" to "fa")
        params.add("points_encoded" to "false")
        params.add("instructions" to "true")
        params.add("elevation" to "true")

        val avoids = mutableListOf<String>()
        if (preferences.avoidTolls) {
            avoids.add("toll")
            appliedPrefs.add("avoidTolls")
        }
        if (preferences.avoidFerries) {
            avoids.add("ferry")
            appliedPrefs.add("avoidFerries")
        }
        if (preferences.avoidTunnels) {
            avoids.add("tunnel")
            appliedPrefs.add("avoidTunnels")
        }
        if (preferences.avoidUnpavedRoads) {
            avoids.add("track")
            avoids.add("Ford")
            appliedPrefs.add("avoidUnpavedRoads")
        }
        if (avoids.isNotEmpty()) {
            params.add("ch.disable" to "true")
            params.add("avoid" to avoids.joinToString(";"))
        }
        if (preferences.optimizeFor == RoutingPreference.SHORTEST) {
            params.add("weighting" to "shortest")
            appliedPrefs.add("optimizeFor=SHORTEST")
        } else {
            appliedPrefs.add("optimizeFor=${preferences.optimizeFor.name}")
        }
        if (maxAlternatives > 1 && waypoints.isEmpty()) {
            params.add("algorithm" to "alternative_route")
            params.add("alternative_route.max_paths" to maxAlternatives.toString())
        }
        if (apiKey.isNotBlank()) {
            params.add("key" to apiKey)
        }
        return params
    }

    internal fun parseGraphHopperPayload(
        rawJson: String,
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        appliedPrefs: List<String> = emptyList(),
        unsupportedPrefs: List<String> = emptyList(),
        requestedAlternatives: Int = 1
    ): Result<EnrichedRouteResult> {
        try {
            val root = JSONObject(rawJson)
            val paths = root.optJSONArray("paths")
            if (paths == null || paths.length() == 0) {
                val msg = root.optString("message", "GraphHopper returned no paths")
                return Result.failure(RoutingError.NoPathFound(origin, destination, msg))
            }

            val primaryPath = paths.optJSONObject(0)
                ?: return Result.failure(RoutingError.MalformedResponse(providerName, "Null primary path at paths[0]"))
            val primaryRoute = parseSingleGraphHopperPath(primaryPath, origin, destination, routeIndex = 0)
                .getOrElse { return Result.failure(it) }

            val seenSignatures = LinkedHashSet<String>()
            seenSignatures.add(PolylineCodec.geometrySignature(primaryRoute.polyline))

            val parsedAlternatives = mutableListOf<EnrichedRouteResult>()
            for (i in 1 until paths.length()) {
                val altPath = paths.optJSONObject(i) ?: continue
                val altRoute = parseSingleGraphHopperPath(altPath, origin, destination, routeIndex = i)
                    .getOrNull() ?: continue
                val sig = PolylineCodec.geometrySignature(altRoute.polyline)
                if (seenSignatures.add(sig)) {
                    parsedAlternatives.add(
                        EnrichedRouteResult(
                            route = altRoute,
                            provenance = RouteProvenance(
                                providerName = providerName,
                                sourceType = sourceType,
                                graphVersion = "graphhopper-live",
                                confidenceScore = 0.89,
                                alternativeRouteSupport = AlternativeRouteSupport.SUPPORTED_AND_RETURNED,
                                unsupportedPreferences = unsupportedPrefs,
                                appliedPreferences = appliedPrefs,
                                originSnapDistanceMeters = altRoute.originSnapDistanceMeters,
                                destinationSnapDistanceMeters = altRoute.destinationSnapDistanceMeters
                            )
                        )
                    )
                }
            }

            val altStatus = when {
                parsedAlternatives.isNotEmpty() -> AlternativeRouteSupport.SUPPORTED_AND_RETURNED
                requestedAlternatives > 1 -> AlternativeRouteSupport.SUPPORTED_NONE_AVAILABLE
                else -> AlternativeRouteSupport.SUPPORTED_NONE_AVAILABLE
            }

            val routeWithAlts = primaryRoute.copy(
                alternativeRoutes = parsedAlternatives.map { it.route }
            )

            return Result.success(
                EnrichedRouteResult(
                    route = routeWithAlts,
                    provenance = RouteProvenance(
                        providerName = providerName,
                        sourceType = sourceType,
                        graphVersion = "graphhopper-live",
                        confidenceScore = 0.92,
                        alternativeRouteSupport = altStatus,
                        unsupportedPreferences = unsupportedPrefs,
                        appliedPreferences = appliedPrefs,
                        originSnapDistanceMeters = routeWithAlts.originSnapDistanceMeters,
                        destinationSnapDistanceMeters = routeWithAlts.destinationSnapDistanceMeters
                    ),
                    alternatives = parsedAlternatives
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            return Result.failure(
                RoutingError.MalformedResponse(providerName, "Failed to parse GraphHopper JSON: ${e.message}", e)
            )
        }
    }

    private fun parseSingleGraphHopperPath(
        path: JSONObject,
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        routeIndex: Int
    ): Result<RealRoadRouteResult> {
        val distanceMeters = path.optDouble("distance", Double.NaN)
        val durationSeconds = path.optDouble("time", Double.NaN) / 1000.0

        val polyline = extractGraphHopperPoints(path)
            .getOrElse { return Result.failure(it) }

        RouteIntegrityValidator.validateDecodedRouteGeometry(
            providerName = providerName,
            polyline = polyline,
            reportedDistanceMeters = distanceMeters,
            reportedDurationSeconds = durationSeconds
        ).onFailure { return Result.failure(it) }

        val maneuvers = ArrayList<RealRoadManeuver>()
        val instructions = path.optJSONArray("instructions")
        var cumulativeDistance = 0.0
        if (instructions != null) {
            for (i in 0 until instructions.length()) {
                val inst = instructions.optJSONObject(i) ?: continue
                val stepDist = inst.optDouble("distance", 0.0).coerceAtLeast(0.0)
                val stepTime = inst.optDouble("time", 0.0).coerceAtLeast(0.0) / 1000.0
                val text = inst.optString("text", "ادامه مسیر")
                val streetName = inst.optString("street_name", "").ifBlank { "مسیر اصلی" }
                val sign = inst.optInt("sign", Int.MIN_VALUE)
                val interval = inst.optJSONArray("interval")
                val startVertexIdx = interval?.optInt(0, 0)?.coerceIn(0, polyline.lastIndex) ?: 0
                val stepCoord = polyline[startVertexIdx]
                val roundaboutExit = if (inst.has("exit_number")) {
                    inst.optInt("exit_number").takeIf { it > 0 }
                } else null

                val explicitMaxSpeed = if (inst.has("max_speed")) {
                    inst.optInt("max_speed", 0).takeIf { it in 5..140 } ?: 0
                } else 0

                maneuvers.add(
                    RealRoadManeuver(
                        stepIndex = i,
                        coordinate = stepCoord,
                        roadName = streetName,
                        instructionFarsi = text,
                        instructionEn = text,
                        maneuverType = mapGraphHopperSign(sign),
                        distanceMeters = stepDist,
                        durationSeconds = stepTime,
                        speedLimitKmh = explicitMaxSpeed,
                        edgeId = "gh_edge_${routeIndex}_$i",
                        segmentIndex = i,
                        roundaboutExitNumber = roundaboutExit,
                        cumulativeDistanceMeters = cumulativeDistance
                    )
                )
                cumulativeDistance += stepDist
            }
        }

        val originSnapMeters = RouteIntegrityValidator.haversineMeters(origin, polyline.first())
        val destSnapMeters = RouteIntegrityValidator.haversineMeters(destination, polyline.last())

        val segments = RouteIntegrityValidator.buildContiguousSegmentsFromManeuvers(
            providerPrefix = "gh_$routeIndex",
            fullPolyline = polyline,
            maneuvers = maneuvers,
            totalDistanceMeters = distanceMeters
        )

        return Result.success(
            RealRoadRouteResult(
                polyline = polyline,
                totalDistanceMeters = distanceMeters,
                totalDurationSeconds = durationSeconds,
                maneuvers = maneuvers,
                isOnlineRouting = true,
                routeSummary = "مسیر گراف‌هاپر",
                originSnapDistanceMeters = originSnapMeters,
                destinationSnapDistanceMeters = destSnapMeters,
                routeId = "gh_${routeIndex}_${PolylineCodec.geometrySignature(polyline).hashCode()}",
                segments = segments
            )
        )
    }

    private fun extractGraphHopperPoints(path: JSONObject): Result<List<GeoCoordinate>> {
        if (!path.has("points") || path.isNull("points")) {
            return Result.failure(
                RoutingError.RouteGeometryMissing(
                    providerName = providerName,
                    reason = "GraphHopper path is missing 'points' geometry field"
                )
            )
        }
        val pointsObj = path.optJSONObject("points")
        if (pointsObj != null) {
            val coordsArray: JSONArray = pointsObj.optJSONArray("coordinates")
                ?: return Result.failure(
                    RoutingError.RouteGeometryMissing(
                        providerName = providerName,
                        reason = "GraphHopper GeoJSON 'points' object is missing 'coordinates' array"
                    )
                )
            val result = ArrayList<GeoCoordinate>(coordsArray.length())
            for (i in 0 until coordsArray.length()) {
                val pt = coordsArray.optJSONArray(i) ?: continue
                if (pt.length() < 2) continue
                val lon = pt.optDouble(0, Double.NaN)
                val lat = pt.optDouble(1, Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) continue
                val coord = GeoCoordinate(lat, lon)
                val prev = result.lastOrNull()
                if (prev == null || prev.latitude != coord.latitude || prev.longitude != coord.longitude) {
                    result.add(coord)
                }
            }
            if (result.size < 2) {
                return Result.failure(
                    RoutingError.RouteGeometryMissing(
                        providerName = providerName,
                        reason = "GraphHopper GeoJSON coordinates contained ${result.size} valid points (< 2 required)"
                    )
                )
            }
            return Result.success(result)
        }

        val encodedPoints = path.optString("points", "")
        if (encodedPoints.isNotBlank()) {
            val decoded = PolylineCodec.decode(encodedPoints, precision = 5)
            if (decoded.size < 2) {
                return Result.failure(
                    RoutingError.RouteGeometryMissing(
                        providerName = providerName,
                        reason = "GraphHopper encoded 'points' decoded to ${decoded.size} points (< 2 required)"
                    )
                )
            }
            return Result.success(decoded)
        }

        return Result.failure(
            RoutingError.RouteGeometryMissing(
                providerName = providerName,
                reason = "GraphHopper 'points' field was empty"
            )
        )
    }

    internal fun mapGraphHopperSign(sign: Int): RealManeuverType = when (sign) {
        -98, -8, 8 -> RealManeuverType.UTURN
        -7 -> RealManeuverType.FORK_LEFT
        -3 -> RealManeuverType.SHARP_LEFT
        -2 -> RealManeuverType.TURN_LEFT
        -1 -> RealManeuverType.SLIGHT_LEFT
        0 -> RealManeuverType.CONTINUE
        1 -> RealManeuverType.SLIGHT_RIGHT
        2 -> RealManeuverType.TURN_RIGHT
        3 -> RealManeuverType.SHARP_RIGHT
        4, 5 -> RealManeuverType.ARRIVE
        6 -> RealManeuverType.ROUNDABOUT
        7 -> RealManeuverType.FORK_RIGHT
        else -> RealManeuverType.UNKNOWN
    }
}
