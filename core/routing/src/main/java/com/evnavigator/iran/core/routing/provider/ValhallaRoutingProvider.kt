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
 * Online routing provider backed by a Valhalla routing engine instance (V13.0 Audit — Items 1, 3, 4, 28–39, 91–93, 165).
 *
 * Key guarantees:
 * 1. Decodes Valhalla's `precision = 6` (`1e-6`) encoded `leg.shape` polyline via [PolylineCodec.decode].
 * 2. Strictly rejects any response where `shape` is missing or decodes to `< 2` coordinates with
 *    [RoutingError.RouteGeometryMissing] — never falls back to a 2-point straight line.
 * 3. Maps [RoutePreferences] (`optimizeFor`, `avoidTolls`, `avoidFerries`, `avoidUnpavedRoads`, `preferHighwayAccess`)
 *    into `costing_options.auto` and records any unsupported preferences in [RouteProvenance.unsupportedPreferences].
 * 4. Parses authentic alternative routes from Valhalla's `alternates` array and deduplicates via geometry signature.
 */
class ValhallaRoutingProvider(
    private val baseUrl: String = "",
    override val priority: Int = 2
) : RoutingProvider {

    override val providerName: String = "Valhalla-Online"
    override val sourceType: RoutingSourceType = RoutingSourceType.ONLINE_PRIMARY

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        providerName = providerName,
        supportsAlternativeRoutes = true,
        supportsAvoidTolls = true,
        supportsAvoidFerries = true,
        supportsAvoidTunnels = false,
        supportsAvoidUnpaved = true,
        supportsShortestOptimization = true,
        supportsWaypoints = true,
        supportsElevationProfile = false
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
        executeValhallaQuery(
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            preferences = preferences,
            requestedAlternates = if (waypoints.isEmpty()) 2 else 0
        )
    }

    override suspend fun calculateAlternativeRoutes(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        preferences: RoutePreferences,
        maxAlternatives: Int
    ): Result<List<EnrichedRouteResult>> = withContext(Dispatchers.IO) {
        val primaryResult = executeValhallaQuery(
            origin = origin,
            destination = destination,
            waypoints = emptyList(),
            preferences = preferences,
            requestedAlternates = maxAlternatives.coerceIn(1, 5)
        )
        primaryResult.map { enriched ->
            buildList {
                add(enriched)
                addAll(enriched.alternatives)
            }.take(maxAlternatives.coerceAtLeast(1))
        }
    }

    private suspend fun executeValhallaQuery(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences,
        requestedAlternates: Int
    ): Result<EnrichedRouteResult> {
        if (baseUrl.isBlank()) {
            return Result.failure(
                RoutingError.ProviderUnavailable(providerName, "Valhalla baseUrl is not configured")
            )
        }
        RouteIntegrityValidator.validateWaypoints(origin, destination, waypoints)
            .onFailure { return Result.failure(it) }

        val unsupportedPrefs = mutableListOf<String>()
        val appliedPrefs = mutableListOf<String>()
        if (preferences.avoidTunnels) {
            if (preferences.strictPreferenceEnforcement) {
                return Result.failure(RoutingError.UnsupportedPreference(providerName, "avoidTunnels"))
            }
            unsupportedPrefs.add("avoidTunnels")
        }

        val requestPayload = buildValhallaRequestJson(
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            preferences = preferences,
            requestedAlternates = requestedAlternates,
            appliedPrefs = appliedPrefs
        )

        val endpointUrl = try {
            NetworkRoutingHttpClient.buildSafeUrl(baseUrl, "/route")
        } catch (e: Exception) {
            return Result.failure(RoutingError.NetworkFailure("Invalid Valhalla URL: ${e.message}", e))
        }

        val responseText = NetworkRoutingHttpClient.executePostJson(
            providerName = providerName,
            url = endpointUrl,
            jsonBody = requestPayload.toString()
        ).getOrElse { return Result.failure(it) }

        return parseValhallaPayload(
            rawJson = responseText,
            origin = origin,
            destination = destination,
            appliedPrefs = appliedPrefs,
            unsupportedPrefs = unsupportedPrefs,
            requestedAlternates = requestedAlternates
        )
    }

    internal fun buildValhallaRequestJson(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        waypoints: List<GeoCoordinate>,
        preferences: RoutePreferences,
        requestedAlternates: Int,
        appliedPrefs: MutableList<String>
    ): JSONObject {
        val locations = JSONArray().apply {
            put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
            waypoints.forEach { wp ->
                put(JSONObject().put("lat", wp.latitude).put("lon", wp.longitude))
            }
            put(JSONObject().put("lat", destination.latitude).put("lon", destination.longitude))
        }

        val autoOptions = JSONObject().apply {
            if (preferences.avoidTolls) {
                put("use_tolls", 0.0)
                appliedPrefs.add("avoidTolls")
            } else {
                put("use_tolls", 1.0)
            }
            if (preferences.avoidFerries) {
                put("use_ferries", 0.0)
                appliedPrefs.add("avoidFerries")
            } else {
                put("use_ferries", 1.0)
            }
            if (preferences.avoidUnpavedRoads) {
                put("use_tracks", 0.0)
                appliedPrefs.add("avoidUnpavedRoads")
            } else {
                put("use_tracks", 0.5)
            }
            put("use_highways", if (preferences.preferHighwayAccess) 1.0 else 0.35)
            if (preferences.optimizeFor == RoutingPreference.SHORTEST) {
                put("shortest", true)
                appliedPrefs.add("optimizeFor=SHORTEST")
            } else {
                appliedPrefs.add("optimizeFor=${preferences.optimizeFor.name}")
            }
        }

        return JSONObject().apply {
            put("locations", locations)
            put("costing", "auto")
            put("costing_options", JSONObject().put("auto", autoOptions))
            put("directions_options", JSONObject().put("units", "kilometers").put("language", "fa-IR"))
            if (requestedAlternates > 0 && waypoints.isEmpty()) {
                put("alternates", requestedAlternates)
            }
        }
    }

    internal fun parseValhallaPayload(
        rawJson: String,
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        appliedPrefs: List<String> = emptyList(),
        unsupportedPrefs: List<String> = emptyList(),
        requestedAlternates: Int = 0
    ): Result<EnrichedRouteResult> {
        try {
            val root = JSONObject(rawJson)
            val primaryTrip = root.optJSONObject("trip")
                ?: return Result.failure(
                    RoutingError.MalformedResponse(providerName, "Valhalla response missing 'trip' object")
                )

            val primaryRoute = parseSingleValhallaTrip(primaryTrip, origin, destination, routeIndex = 0)
                .getOrElse { return Result.failure(it) }

            val seenSignatures = LinkedHashSet<String>()
            seenSignatures.add(PolylineCodec.geometrySignature(primaryRoute.polyline))

            val parsedAlternatives = mutableListOf<EnrichedRouteResult>()
            val alternatesArray = root.optJSONArray("alternates")
            if (alternatesArray != null) {
                for (i in 0 until alternatesArray.length()) {
                    val altObj = alternatesArray.optJSONObject(i) ?: continue
                    val altTrip = altObj.optJSONObject("trip") ?: continue
                    val altRoute = parseSingleValhallaTrip(altTrip, origin, destination, routeIndex = i + 1)
                        .getOrNull() ?: continue
                    val sig = PolylineCodec.geometrySignature(altRoute.polyline)
                    if (seenSignatures.add(sig)) {
                        parsedAlternatives.add(
                            EnrichedRouteResult(
                                route = altRoute,
                                provenance = RouteProvenance(
                                    providerName = providerName,
                                    sourceType = sourceType,
                                    graphVersion = "valhalla-live",
                                    confidenceScore = 0.90,
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
            }

            val altSupportStatus = when {
                parsedAlternatives.isNotEmpty() -> AlternativeRouteSupport.SUPPORTED_AND_RETURNED
                requestedAlternates > 0 -> AlternativeRouteSupport.SUPPORTED_NONE_AVAILABLE
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
                        graphVersion = "valhalla-live",
                        confidenceScore = 0.94,
                        alternativeRouteSupport = altSupportStatus,
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
                RoutingError.MalformedResponse(providerName, "Failed to parse Valhalla JSON: ${e.message}", e)
            )
        }
    }

    private fun parseSingleValhallaTrip(
        trip: JSONObject,
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        routeIndex: Int
    ): Result<RealRoadRouteResult> {
        val status = trip.optInt("status", 0)
        if (status != 0) {
            val statusMsg = trip.optString("status_message", "Valhalla non-zero trip status $status")
            return Result.failure(RoutingError.NoPathFound(origin, destination, statusMsg))
        }

        val summary = trip.optJSONObject("summary")
            ?: return Result.failure(RoutingError.MalformedResponse(providerName, "Valhalla trip missing 'summary'"))
        val totalDistanceMeters = summary.optDouble("length", Double.NaN) * 1000.0
        val totalDurationSeconds = summary.optDouble("time", Double.NaN)

        val legs = trip.optJSONArray("legs")
        if (legs == null || legs.length() == 0) {
            return Result.failure(RoutingError.RouteGeometryMissing(providerName, "Valhalla trip contains no legs"))
        }

        val fullPolyline = ArrayList<GeoCoordinate>()
        val maneuvers = ArrayList<RealRoadManeuver>()
        var cumulativeDistance = 0.0
        var globalStepIndex = 0

        for (legIdx in 0 until legs.length()) {
            val leg = legs.optJSONObject(legIdx)
                ?: return Result.failure(RoutingError.MalformedResponse(providerName, "Null leg at index $legIdx"))
            val encodedShape = leg.optString("shape", "")
            if (encodedShape.isBlank()) {
                return Result.failure(
                    RoutingError.RouteGeometryMissing(
                        providerName = providerName,
                        reason = "Valhalla leg[$legIdx] is missing encoded 'shape' polyline"
                    )
                )
            }
            val legCoords = PolylineCodec.decode(encodedShape, precision = 6)
            if (legCoords.size < 2) {
                return Result.failure(
                    RoutingError.RouteGeometryMissing(
                        providerName = providerName,
                        reason = "Valhalla leg[$legIdx] 'shape' decoded to ${legCoords.size} points (< 2 required)"
                    )
                )
            }

            for (coord in legCoords) {
                val last = fullPolyline.lastOrNull()
                if (last == null || last.latitude != coord.latitude || last.longitude != coord.longitude) {
                    fullPolyline.add(coord)
                }
            }

            val legManeuvers = leg.optJSONArray("maneuvers")
            if (legManeuvers != null) {
                for (mIdx in 0 until legManeuvers.length()) {
                    val m = legManeuvers.optJSONObject(mIdx) ?: continue
                    val stepDist = m.optDouble("length", 0.0).coerceAtLeast(0.0) * 1000.0
                    val stepTime = m.optDouble("time", 0.0).coerceAtLeast(0.0)
                    val instruction = m.optString("instruction", "ادامه مسیر")
                    val streetNames = m.optJSONArray("street_names")
                    val roadName = if (streetNames != null && streetNames.length() > 0) {
                        streetNames.optString(0, "مسیر اصلی")
                    } else {
                        "مسیر اصلی"
                    }
                    val beginShapeIndex = m.optInt("begin_shape_index", 0)
                        .coerceIn(0, legCoords.lastIndex)
                    val stepCoord = legCoords[beginShapeIndex]
                    val valhallaType = m.optInt("type", -1)
                    val roundaboutExit = if (m.has("roundabout_exit_count")) {
                        m.optInt("roundabout_exit_count").takeIf { it > 0 }
                    } else null

                    val explicitSpeedLimit = if (m.has("speed_limit")) {
                        m.optInt("speed_limit", 0).takeIf { it in 5..140 } ?: 0
                    } else 0

                    maneuvers.add(
                        RealRoadManeuver(
                            stepIndex = globalStepIndex,
                            coordinate = stepCoord,
                            roadName = roadName,
                            instructionFarsi = instruction,
                            instructionEn = instruction,
                            maneuverType = mapValhallaManeuverType(valhallaType),
                            distanceMeters = stepDist,
                            durationSeconds = stepTime,
                            speedLimitKmh = explicitSpeedLimit,
                            edgeId = "valhalla_edge_${routeIndex}_$globalStepIndex",
                            segmentIndex = globalStepIndex,
                            roundaboutExitNumber = roundaboutExit,
                            cumulativeDistanceMeters = cumulativeDistance
                        )
                    )
                    cumulativeDistance += stepDist
                    globalStepIndex++
                }
            }
        }

        RouteIntegrityValidator.validateDecodedRouteGeometry(
            providerName = providerName,
            polyline = fullPolyline,
            reportedDistanceMeters = totalDistanceMeters,
            reportedDurationSeconds = totalDurationSeconds
        ).onFailure { return Result.failure(it) }

        val originSnapMeters = RouteIntegrityValidator.haversineMeters(origin, fullPolyline.first())
        val destSnapMeters = RouteIntegrityValidator.haversineMeters(destination, fullPolyline.last())

        val segments = RouteIntegrityValidator.buildContiguousSegmentsFromManeuvers(
            providerPrefix = "valhalla_$routeIndex",
            fullPolyline = fullPolyline,
            maneuvers = maneuvers,
            totalDistanceMeters = totalDistanceMeters
        )

        return Result.success(
            RealRoadRouteResult(
                polyline = fullPolyline,
                totalDistanceMeters = totalDistanceMeters,
                totalDurationSeconds = totalDurationSeconds,
                maneuvers = maneuvers,
                isOnlineRouting = true,
                routeSummary = summary.optString("summary", "مسیر والهالا"),
                originSnapDistanceMeters = originSnapMeters,
                destinationSnapDistanceMeters = destSnapMeters,
                routeId = "valhalla_${routeIndex}_${PolylineCodec.geometrySignature(fullPolyline).hashCode()}",
                segments = segments
            )
        )
    }

    internal fun mapValhallaManeuverType(valhallaType: Int): RealManeuverType = when (valhallaType) {
        1, 2, 3 -> RealManeuverType.DEPART
        4, 5, 6 -> RealManeuverType.ARRIVE
        7, 8 -> RealManeuverType.CONTINUE
        9 -> RealManeuverType.SLIGHT_RIGHT
        10 -> RealManeuverType.TURN_RIGHT
        11 -> RealManeuverType.SHARP_RIGHT
        12, 13 -> RealManeuverType.UTURN
        14 -> RealManeuverType.SHARP_LEFT
        15 -> RealManeuverType.TURN_LEFT
        16 -> RealManeuverType.SLIGHT_LEFT
        17 -> RealManeuverType.CONTINUE
        18, 19 -> RealManeuverType.RAMP_ENTER
        20, 21 -> RealManeuverType.RAMP_EXIT
        22, 23, 24 -> RealManeuverType.FORK_RIGHT
        25 -> RealManeuverType.MERGE
        26, 27 -> RealManeuverType.ROUNDABOUT
        else -> RealManeuverType.UNKNOWN
    }
}
