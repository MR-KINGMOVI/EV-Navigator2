package com.evnavigator.iran.core.routing.engine

import com.evnavigator.iran.core.routing.model.LaneGuidanceInfo
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.CancellationException
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * V13.2 Section 1 & Section 15: Explicit route completeness classification for OSRM responses.
 */
enum class OsrmRouteCompleteness {
    COMPLETE,
    INSUFFICIENT_ROUTE_DETAILS,
    UNKNOWN
}

/**
 * V13.2 Section 1 & Section 15: Typed outcome for OSRM HTTP and JSON parsing,
 * strictly separating Network, HTTP, JSON syntax, Geometry, Distance, Duration, No-Route, and Step errors.
 */
sealed class OsrmParseOutcome {
    data class Success(
        val route: RealRoadRouteResult,
        val completeness: OsrmRouteCompleteness,
        val hasTurnByTurnGuidance: Boolean,
        val warnings: List<String> = emptyList()
    ) : OsrmParseOutcome()

    sealed class Failure(
        open val errorCode: String,
        open val message: String,
        open val isRecoverable: Boolean
    ) : OsrmParseOutcome() {
        data class NetworkFailure(val causeMessage: String) :
            Failure(
                errorCode = "NETWORK_ERROR",
                message = "خطای شبکه در ارتباط با سرور مسیریابی OSRM: $causeMessage",
                isRecoverable = true
            )

        data class HttpFailure(val statusCode: Int, val statusMessage: String = "") :
            Failure(
                errorCode = "HTTP_ERROR_$statusCode",
                message = "خطای HTTP ($statusCode) از سرور OSRM: $statusMessage",
                isRecoverable = statusCode in listOf(408, 429, 500, 502, 503, 504)
            )

        data class MalformedJsonFailure(val details: String) :
            Failure(
                errorCode = "MALFORMED_JSON",
                message = "ساختار JSON پاسخ OSRM خراب یا نامعتبر است: $details",
                isRecoverable = false
            )

        data class NoRouteFound(val osrmCode: String) :
            Failure(
                errorCode = "NO_ROUTE_FOUND",
                message = "هیچ مسیر معتبری در پاسخ OSRM یافت نشد (کد: $osrmCode)",
                isRecoverable = false
            )

        data class InvalidDistance(val rawValue: Any?) :
            Failure(
                errorCode = "INVALID_DISTANCE",
                message = "مسافت مسیر در پاسخ OSRM نامعتبر یا ناموجود است ($rawValue)",
                isRecoverable = false
            )

        data class InvalidDuration(val rawValue: Any?) :
            Failure(
                errorCode = "INVALID_DURATION",
                message = "مدت‌زمان مسیر در پاسخ OSRM نامعتبر یا ناموجود است ($rawValue)",
                isRecoverable = false
            )

        data class EmptyOrIncompleteGeometry(val pointCount: Int, val details: String) :
            Failure(
                errorCode = "INVALID_GEOMETRY",
                message = "هندسه مسیر خالی یا ناقص است (تعداد نقاط: $pointCount — $details)",
                isRecoverable = false
            )

        data class StepParsingFailure(val legIndex: Int, val stepIndex: Int, val details: String) :
            Failure(
                errorCode = "STEP_PARSING_ERROR",
                message = "خطا در پردازش گام مسیریابی (قطعه $legIndex، گام $stepIndex): $details",
                isRecoverable = false
            )
    }
}

/**
 * V13.2 Audited Online OSRM HTTP client & GeoJSON maneuver parser.
 *
 * Non-negotiable V13.2 Guarantees (Section 1):
 * 1. Never fabricates distance (`polyline.size * 50.0` or Haversine fallback when `distance` is missing/invalid).
 * 2. Never fabricates duration (`polyline.size * 2.0` or `distance / 25.0` when `duration` is missing/invalid).
 * 3. Never assigns a default legal speed limit (`speedLimitKmh = 100`) or guesses speed limits from road names.
 * 4. Never creates synthetic [RealRoadManeuver] items when step data is absent; routes without steps are marked
 *    `INSUFFICIENT_ROUTE_DETAILS` with `hasTurnByTurnGuidance = false` and `maneuvers = emptyList()`.
 * 5. Never swallows JSON or step parsing errors with silent `catch` blocks.
 */
object OsrmOnlineRouteClient {

    fun fetchOnlineOsrmRouteDetailed(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        connectionTimeoutMs: Int = 4000
    ): OsrmParseOutcome {
        if (!origin.latitude.isFinite() || !origin.longitude.isFinite() ||
            origin.latitude !in -90.0..90.0 || origin.longitude !in -180.0..180.0 ||
            !destination.latitude.isFinite() || !destination.longitude.isFinite() ||
            destination.latitude !in -90.0..90.0 || destination.longitude !in -180.0..180.0
        ) {
            return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(
                pointCount = 0,
                details = "Origin or destination coordinate is outside valid WGS84 bounds"
            )
        }

        val urlString = "https://router.project-osrm.org/route/v1/driving/" +
            "${origin.longitude},${origin.latitude};" +
            "${destination.longitude},${destination.latitude}" +
            "?overview=full&geometries=geojson&steps=true"

        val conn: HttpURLConnection = try {
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectionTimeoutMs.coerceIn(500, 15_000)
                readTimeout = connectionTimeoutMs.coerceIn(500, 15_000)
                setRequestProperty("User-Agent", "EVNavigatorIran/13.2 (Automotive Cockpit)")
                setRequestProperty("Accept", "application/json")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return OsrmParseOutcome.Failure.NetworkFailure(e.message ?: "Connection open failed")
        }

        return try {
            val status = conn.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val msg = try { conn.responseMessage ?: "" } catch (_: Exception) { "" }
                return OsrmParseOutcome.Failure.HttpFailure(statusCode = status, statusMessage = msg)
            }
            val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            parseOsrmResponseDetailed(responseText, origin, destination)
        } catch (e: IOException) {
            OsrmParseOutcome.Failure.NetworkFailure(e.message ?: "I/O failure while reading OSRM response")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            OsrmParseOutcome.Failure.NetworkFailure(e.message ?: "Unexpected transport error")
        } finally {
            conn.disconnect()
        }
    }

    fun fetchOnlineOsrmRoute(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        connectionTimeoutMs: Int = 4000
    ): RealRoadRouteResult? {
        return when (val outcome = fetchOnlineOsrmRouteDetailed(origin, destination, connectionTimeoutMs)) {
            is OsrmParseOutcome.Success -> outcome.route
            is OsrmParseOutcome.Failure -> null
        }
    }

    /**
     * Parses raw OSRM JSON into a typed [OsrmParseOutcome].
     * Never swallows JSON errors or invents distance, duration, speed limits, or maneuvers.
     */
    fun parseOsrmResponseDetailed(
        json: String,
        @Suppress("UNUSED_PARAMETER") origin: GeoCoordinate,
        @Suppress("UNUSED_PARAMETER") destination: GeoCoordinate
    ): OsrmParseOutcome {
        if (json.isBlank()) {
            return OsrmParseOutcome.Failure.MalformedJsonFailure("Empty or blank JSON response")
        }

        val rootObj: Map<String, Any?> = try {
            val parsed = StrictJsonParser(json).parseRoot()
            if (parsed !is Map<*, *>) {
                return OsrmParseOutcome.Failure.MalformedJsonFailure("Root JSON element must be an object")
            }
            @Suppress("UNCHECKED_CAST")
            parsed as Map<String, Any?>
        } catch (e: JsonSyntaxException) {
            return OsrmParseOutcome.Failure.MalformedJsonFailure(e.message ?: "JSON syntax error")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return OsrmParseOutcome.Failure.MalformedJsonFailure(e.message ?: "Invalid JSON payload")
        }

        val code = rootObj["code"] as? String
        if (code.isNullOrBlank()) {
            return OsrmParseOutcome.Failure.MalformedJsonFailure("Missing 'code' string field in OSRM root object")
        }
        if (code != "Ok") {
            return OsrmParseOutcome.Failure.NoRouteFound(osrmCode = code)
        }

        val routesRaw = rootObj["routes"]
            ?: return OsrmParseOutcome.Failure.NoRouteFound(osrmCode = "MISSING_ROUTES_ARRAY")
        if (routesRaw !is List<*>) {
            return OsrmParseOutcome.Failure.MalformedJsonFailure("'routes' field must be a JSON array")
        }
        if (routesRaw.isEmpty()) {
            return OsrmParseOutcome.Failure.NoRouteFound(osrmCode = "EMPTY_ROUTES")
        }

        val routeObj = routesRaw.firstOrNull() as? Map<*, *>
            ?: return OsrmParseOutcome.Failure.MalformedJsonFailure("First element in 'routes' must be a JSON object")

        if (!routeObj.containsKey("distance")) {
            return OsrmParseOutcome.Failure.InvalidDistance(rawValue = null)
        }
        val totalDistanceMeters = asFiniteDouble(routeObj["distance"])
            ?: return OsrmParseOutcome.Failure.InvalidDistance(rawValue = routeObj["distance"])
        if (totalDistanceMeters <= 0.0) {
            return OsrmParseOutcome.Failure.InvalidDistance(rawValue = totalDistanceMeters)
        }

        if (!routeObj.containsKey("duration")) {
            return OsrmParseOutcome.Failure.InvalidDuration(rawValue = null)
        }
        val totalDurationSeconds = asFiniteDouble(routeObj["duration"])
            ?: return OsrmParseOutcome.Failure.InvalidDuration(rawValue = routeObj["duration"])
        if (totalDurationSeconds <= 0.0) {
            return OsrmParseOutcome.Failure.InvalidDuration(rawValue = totalDurationSeconds)
        }

        val geometryObj = routeObj["geometry"] as? Map<*, *>
            ?: return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(0, "Missing 'geometry' object")
        val coordsList = geometryObj["coordinates"] as? List<*>
            ?: return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(0, "Missing 'geometry.coordinates' array")

        if (coordsList.isEmpty()) {
            return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(0, "Empty 'geometry.coordinates' array")
        }

        val polyline = mutableListOf<GeoCoordinate>()
        for ((idx, item) in coordsList.withIndex()) {
            val pair = item as? List<*>
                ?: return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(
                    pointCount = idx,
                    details = "Coordinate at index $idx is not an array"
                )
            if (pair.size < 2) {
                return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(
                    pointCount = idx,
                    details = "Coordinate at index $idx has fewer than 2 elements"
                )
            }
            val lon = asFiniteDouble(pair[0])
            val lat = asFiniteDouble(pair[1])
            if (lon == null || lat == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(
                    pointCount = idx,
                    details = "Coordinate at index $idx has invalid WGS84 values (lat=$lat, lon=$lon)"
                )
            }
            polyline.add(GeoCoordinate(lat, lon))
        }

        if (polyline.size < 2) {
            return OsrmParseOutcome.Failure.EmptyOrIncompleteGeometry(
                pointCount = polyline.size,
                details = "Route geometry must contain at least 2 points"
            )
        }

        val stepParseResult = parseLegsAndStepsStrict(routeObj)
        if (stepParseResult is StepExtractionOutcome.Error) {
            return stepParseResult.failure
        }
        val extractedSteps = (stepParseResult as StepExtractionOutcome.Extracted).maneuvers
        val warnings = mutableListOf<String>()

        val completeness: OsrmRouteCompleteness
        val hasGuidance: Boolean
        val summary: String
        if (extractedSteps.isEmpty()) {
            completeness = OsrmRouteCompleteness.INSUFFICIENT_ROUTE_DETAILS
            hasGuidance = false
            warnings.add("INSUFFICIENT_ROUTE_DETAILS: پاسخ OSRM دارای هندسه مسیر است اما فاقد گام‌های ناوبری (steps) است؛ راهنمای گام‌به‌گام (Turn-by-Turn) ساخته نشد.")
            summary = "مسیر ناقص (INSUFFICIENT_ROUTE_DETAILS)"
        } else {
            completeness = OsrmRouteCompleteness.COMPLETE
            hasGuidance = true
            summary = extractedSteps.first().roadName.ifBlank { "مسیر جاده‌ای" }
        }

        val routeResult = RealRoadRouteResult(
            polyline = polyline,
            totalDistanceMeters = totalDistanceMeters,
            totalDurationSeconds = totalDurationSeconds,
            maneuvers = extractedSteps,
            isOnlineRouting = true,
            routeSummary = summary,
            completenessStatus = completeness.name,
            hasTurnByTurnGuidance = hasGuidance,
            warnings = warnings
        )

        return OsrmParseOutcome.Success(
            route = routeResult,
            completeness = completeness,
            hasTurnByTurnGuidance = hasGuidance,
            warnings = warnings
        )
    }

    fun parseOsrmResponse(
        json: String,
        origin: GeoCoordinate,
        destination: GeoCoordinate
    ): RealRoadRouteResult? {
        return when (val outcome = parseOsrmResponseDetailed(json, origin, destination)) {
            is OsrmParseOutcome.Success -> outcome.route
            is OsrmParseOutcome.Failure -> null
        }
    }

    /**
     * Parses OSRM steps strictly without fabricating fallback maneuvers or fake speed limits.
     * Throws [IllegalArgumentException] if the JSON is malformed or step attributes are invalid.
     */
    fun parseOsrmSteps(
        json: String,
        @Suppress("UNUSED_PARAMETER") polyline: List<GeoCoordinate>
    ): List<RealRoadManeuver> {
        val parsed = try {
            StrictJsonParser(json).parseRoot()
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed OSRM JSON in parseOsrmSteps: ${e.message}", e)
        }
        val rootObj = parsed as? Map<*, *>
            ?: throw IllegalArgumentException("OSRM root must be a JSON object")
        val routes = rootObj["routes"] as? List<*>
            ?: throw IllegalArgumentException("Missing 'routes' array in OSRM JSON")
        if (routes.isEmpty()) return emptyList()
        val routeObj = routes.firstOrNull() as? Map<*, *>
            ?: throw IllegalArgumentException("First route entry must be a JSON object")

        return when (val out = parseLegsAndStepsStrict(routeObj)) {
            is StepExtractionOutcome.Extracted -> out.maneuvers
            is StepExtractionOutcome.Error -> throw IllegalArgumentException(out.failure.message)
        }
    }

    private sealed class StepExtractionOutcome {
        data class Extracted(val maneuvers: List<RealRoadManeuver>) : StepExtractionOutcome()
        data class Error(val failure: OsrmParseOutcome.Failure.StepParsingFailure) : StepExtractionOutcome()
    }

    private fun parseLegsAndStepsStrict(routeObj: Map<*, *>): StepExtractionOutcome {
        if (!routeObj.containsKey("legs") || routeObj["legs"] == null) {
            return StepExtractionOutcome.Extracted(emptyList())
        }
        val legs = routeObj["legs"] as? List<*>
            ?: return StepExtractionOutcome.Error(
                OsrmParseOutcome.Failure.StepParsingFailure(0, 0, "'legs' field is not a JSON array")
            )

        val maneuvers = mutableListOf<RealRoadManeuver>()
        var globalStepIndex = 0
        var cumulativeDistanceMeters = 0.0

        for ((legIdx, legRaw) in legs.withIndex()) {
            val legObj = legRaw as? Map<*, *>
                ?: return StepExtractionOutcome.Error(
                    OsrmParseOutcome.Failure.StepParsingFailure(legIdx, 0, "Leg at index $legIdx is not a JSON object")
                )
            if (!legObj.containsKey("steps") || legObj["steps"] == null) {
                continue
            }
            val steps = legObj["steps"] as? List<*>
                ?: return StepExtractionOutcome.Error(
                    OsrmParseOutcome.Failure.StepParsingFailure(legIdx, 0, "'steps' in leg $legIdx is not a JSON array")
                )

            for ((stepIdx, stepRaw) in steps.withIndex()) {
                val stepObj = stepRaw as? Map<*, *>
                    ?: return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Step is not a JSON object")
                    )

                if (!stepObj.containsKey("distance")) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Missing step 'distance'")
                    )
                }
                val distMeters = asFiniteDouble(stepObj["distance"])
                if (distMeters == null || distMeters < 0.0) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Invalid step distance: ${stepObj["distance"]}")
                    )
                }

                if (!stepObj.containsKey("duration")) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Missing step 'duration'")
                    )
                }
                val durationSec = asFiniteDouble(stepObj["duration"])
                if (durationSec == null || durationSec < 0.0) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Invalid step duration: ${stepObj["duration"]}")
                    )
                }

                val maneuverObj = stepObj["maneuver"] as? Map<*, *>
                    ?: return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Missing 'maneuver' object in step")
                    )

                val locList = maneuverObj["location"] as? List<*>
                if (locList == null || locList.size < 2) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Missing or incomplete 'maneuver.location'")
                    )
                }
                val lon = asFiniteDouble(locList[0])
                val lat = asFiniteDouble(locList[1])
                if (lon == null || lat == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Invalid WGS84 maneuver.location ($lat, $lon)")
                    )
                }
                val coord = GeoCoordinate(lat, lon)

                val typeStr = (maneuverObj["type"] as? String)?.trim().orEmpty()
                if (typeStr.isEmpty()) {
                    return StepExtractionOutcome.Error(
                        OsrmParseOutcome.Failure.StepParsingFailure(legIdx, stepIdx, "Missing 'maneuver.type'")
                    )
                }
                val modifierStr = (maneuverObj["modifier"] as? String)?.trim().orEmpty()
                val maneuverType = mapOsrmType(typeStr, modifierStr)

                val nameRaw = (stepObj["name"] as? String)?.trim().orEmpty()
                val refRaw = (stepObj["ref"] as? String)?.trim().orEmpty()
                val roadName = when {
                    nameRaw.isNotEmpty() -> nameRaw
                    refRaw.isNotEmpty() -> refRaw
                    else -> "مسیر بدون نام (UNKNOWN_ROAD_NAME)"
                }

                // V13.2 Section 1 Invariant: NEVER guess speedLimitKmh from roadName and NEVER default to 100!
                // Only extract an explicit verified speed limit if provided in the step payload; otherwise 0 (UNKNOWN).
                val explicitSpeedLimit = extractExplicitSpeedLimitKmh(stepObj, maneuverObj)

                val instructionFa = generatePersianInstruction(maneuverType, roadName, distMeters)
                val instructionEn = generateEnglishInstruction(maneuverType, roadName, distMeters)
                val laneGuidance = parseLaneGuidance(stepObj["intersections"])

                val bearingBefore = asFiniteDouble(maneuverObj["bearing_before"]) ?: 0.0
                val bearingAfter = asFiniteDouble(maneuverObj["bearing_after"]) ?: 0.0

                maneuvers.add(
                    RealRoadManeuver(
                        stepIndex = globalStepIndex++,
                        coordinate = coord,
                        roadName = roadName,
                        instructionFarsi = instructionFa,
                        instructionEn = instructionEn,
                        maneuverType = maneuverType,
                        distanceMeters = distMeters,
                        durationSeconds = durationSec,
                        speedLimitKmh = explicitSpeedLimit ?: 0,
                        legalSpeedLimitKmh = explicitSpeedLimit,
                        laneGuidance = laneGuidance,
                        bearingBefore = bearingBefore,
                        bearingAfter = bearingAfter,
                        cumulativeDistanceMeters = cumulativeDistanceMeters
                    )
                )
                cumulativeDistanceMeters += distMeters
            }
        }

        // V13.2 Section 1 Invariant: NEVER append a fake maneuver if maneuvers is empty!
        return StepExtractionOutcome.Extracted(maneuvers)
    }

    private fun extractExplicitSpeedLimitKmh(stepObj: Map<*, *>, maneuverObj: Map<*, *>): Int? {
        val rawMaxSpeed = stepObj["maxspeed"] ?: stepObj["speed_limit"] ?: maneuverObj["maxspeed"]
        return when (rawMaxSpeed) {
            is Number -> {
                val d = rawMaxSpeed.toDouble()
                if (d.isFinite() && d in 5.0..250.0) d.roundToInt() else null
            }
            is Map<*, *> -> {
                val unknownFlag = rawMaxSpeed["unknown"] as? Boolean ?: false
                val noneFlag = rawMaxSpeed["none"] as? Boolean ?: false
                if (unknownFlag || noneFlag) {
                    null
                } else {
                    val spd = asFiniteDouble(rawMaxSpeed["speed"])
                    val unit = (rawMaxSpeed["unit"] as? String)?.lowercase() ?: "km/h"
                    if (spd != null && spd > 0.0) {
                        val kmh = if (unit == "mph") spd * 1.60934 else spd
                        if (kmh in 5.0..250.0) kmh.roundToInt() else null
                    } else {
                        null
                    }
                }
            }
            else -> null
        }
    }

    private fun parseLaneGuidance(intersectionsRaw: Any?): LaneGuidanceInfo {
        val intersections = intersectionsRaw as? List<*> ?: return LaneGuidanceInfo.UNKNOWN
        val firstIntersection = intersections.firstOrNull() as? Map<*, *> ?: return LaneGuidanceInfo.UNKNOWN
        val lanesList = firstIntersection["lanes"] as? List<*> ?: return LaneGuidanceInfo.UNKNOWN
        if (lanesList.isEmpty()) return LaneGuidanceInfo.UNKNOWN

        val active = mutableListOf<Int>()
        val arrows = mutableListOf<String>()
        for ((idx, laneRaw) in lanesList.withIndex()) {
            val laneObj = laneRaw as? Map<*, *> ?: continue
            val valid = laneObj["valid"] as? Boolean ?: false
            if (valid) active.add(idx)
            val indications = laneObj["indications"] as? List<*>
            val firstInd = (indications?.firstOrNull() as? String)?.trim()?.uppercase()
            arrows.add(if (!firstInd.isNullOrEmpty()) firstInd else "UNKNOWN")
        }
        return LaneGuidanceInfo(
            totalLanes = lanesList.size,
            activeLaneIndices = active,
            laneArrows = arrows,
            isKnown = true
        )
    }

    private fun asFiniteDouble(raw: Any?): Double? {
        return when (raw) {
            is Number -> {
                val d = raw.toDouble()
                if (d.isFinite()) d else null
            }
            else -> null
        }
    }

    fun mapOsrmType(type: String, modifier: String): RealManeuverType {
        return when (type.lowercase()) {
            "depart" -> RealManeuverType.DEPART
            "arrive" -> RealManeuverType.ARRIVE
            "turn", "end of road", "new name", "notification", "continue" -> when (modifier.lowercase()) {
                "left" -> RealManeuverType.TURN_LEFT
                "right" -> RealManeuverType.TURN_RIGHT
                "slight left" -> RealManeuverType.SLIGHT_LEFT
                "slight right" -> RealManeuverType.SLIGHT_RIGHT
                "sharp left" -> RealManeuverType.SHARP_LEFT
                "sharp right" -> RealManeuverType.SHARP_RIGHT
                "uturn" -> RealManeuverType.UTURN
                "straight", "" -> if (type.equals("continue", ignoreCase = true)) RealManeuverType.CONTINUE else RealManeuverType.UNKNOWN
                else -> RealManeuverType.UNKNOWN
            }
            "merge" -> RealManeuverType.MERGE
            "on ramp" -> RealManeuverType.RAMP_ENTER
            "off ramp" -> RealManeuverType.RAMP_EXIT
            "fork" -> when {
                modifier.lowercase().contains("left") -> RealManeuverType.FORK_LEFT
                modifier.lowercase().contains("right") -> RealManeuverType.FORK_RIGHT
                else -> RealManeuverType.UNKNOWN
            }
            "roundabout", "rotary", "exit roundabout", "exit rotary" -> RealManeuverType.ROUNDABOUT
            else -> RealManeuverType.UNKNOWN
        }
    }

    fun generatePersianInstruction(type: RealManeuverType, roadName: String, distMeters: Double): String {
        val distText = if (distMeters >= 1000) "%.1f کیلومتر".format(distMeters / 1000.0) else "${distMeters.roundToInt()} متر"
        return when (type) {
            RealManeuverType.DEPART -> "حرکت به سمت $roadName"
            RealManeuverType.TURN_LEFT -> "در $distText به سمت چپ بپیچید به $roadName"
            RealManeuverType.TURN_RIGHT -> "در $distText به سمت راست بپیچید به $roadName"
            RealManeuverType.SLIGHT_LEFT -> "در $distText کمی به چپ متمایل شوید وارد $roadName"
            RealManeuverType.SLIGHT_RIGHT -> "در $distText کمی به راست متمایل شوید وارد $roadName"
            RealManeuverType.SHARP_LEFT -> "در $distText گردش تند به چپ به سمت $roadName"
            RealManeuverType.SHARP_RIGHT -> "در $distText گردش تند به راست به سمت $roadName"
            RealManeuverType.FORK_LEFT -> "در $distText در دوراهی به چپ ($roadName) بروید"
            RealManeuverType.FORK_RIGHT -> "در $distText در دوراهی به راست ($roadName) بروید"
            RealManeuverType.RAMP_ENTER -> "در $distText وارد رمپ ورودی به سمت $roadName شوید"
            RealManeuverType.RAMP_EXIT -> "در $distText از خروجی خارج شده و وارد $roadName شوید"
            RealManeuverType.MERGE -> "در $distText ادغام در $roadName"
            RealManeuverType.ROUNDABOUT -> "در $distText وارد میدان شده و به $roadName خروج کنید"
            RealManeuverType.UTURN -> "در $distText دور برگردانید به سمت $roadName"
            RealManeuverType.ARRIVE -> "به مقصد رسیدید: $roadName"
            RealManeuverType.CONTINUE -> "در $roadName به مسیر خود ادامه دهید ($distText)"
            RealManeuverType.UNKNOWN -> "مانور نامشخص در $roadName ($distText)"
        }
    }

    fun generateEnglishInstruction(type: RealManeuverType, roadName: String, distMeters: Double): String {
        val distText = if (distMeters >= 1000) "%.1f km".format(distMeters / 1000.0) else "${distMeters.roundToInt()} m"
        return when (type) {
            RealManeuverType.DEPART -> "Head towards $roadName"
            RealManeuverType.TURN_LEFT -> "In $distText, turn left onto $roadName"
            RealManeuverType.TURN_RIGHT -> "In $distText, turn right onto $roadName"
            RealManeuverType.SLIGHT_LEFT -> "In $distText, keep left onto $roadName"
            RealManeuverType.SLIGHT_RIGHT -> "In $distText, keep right onto $roadName"
            RealManeuverType.SHARP_LEFT -> "In $distText, sharp left onto $roadName"
            RealManeuverType.SHARP_RIGHT -> "In $distText, sharp right onto $roadName"
            RealManeuverType.FORK_LEFT -> "In $distText, keep left at the fork onto $roadName"
            RealManeuverType.FORK_RIGHT -> "In $distText, keep right at the fork onto $roadName"
            RealManeuverType.RAMP_ENTER -> "In $distText, take the on-ramp onto $roadName"
            RealManeuverType.RAMP_EXIT -> "In $distText, take exit toward $roadName"
            RealManeuverType.MERGE -> "In $distText, merge onto $roadName"
            RealManeuverType.ROUNDABOUT -> "In $distText, enter roundabout toward $roadName"
            RealManeuverType.UTURN -> "In $distText, make a U-turn onto $roadName"
            RealManeuverType.ARRIVE -> "You have arrived at $roadName"
            RealManeuverType.CONTINUE -> "Continue on $roadName for $distText"
            RealManeuverType.UNKNOWN -> "Unknown maneuver on $roadName ($distText)"
        }
    }

    internal class JsonSyntaxException(message: String) : Exception(message)

    /**
     * Strict RFC-8259 recursive-descent JSON parser.
     * Works identically on Android runtime and pure JVM unit tests (without relying on android.jar org.json stubs)
     * and rejects trailing garbage, unclosed structures, NaN/Infinity literals, and malformed numbers.
     */
    internal class StrictJsonParser(private val input: String) {
        private var pos = 0
        private val len = input.length

        fun parseRoot(): Any? {
            skipWhitespace()
            if (pos >= len) throw JsonSyntaxException("Unexpected end of JSON input")
            val value = parseValue()
            skipWhitespace()
            if (pos < len) {
                throw JsonSyntaxException("Unexpected trailing token at position $pos")
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= len) throw JsonSyntaxException("Unexpected end of JSON input at position $pos")
            return when (input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw JsonSyntaxException("Unexpected character '${input[pos]}' at position $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expectChar('{')
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (pos < len && input[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (pos >= len || input[pos] != '"') {
                    throw JsonSyntaxException("Expected quoted object key at position $pos")
                }
                val key = parseString()
                skipWhitespace()
                expectChar(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos >= len) throw JsonSyntaxException("Unclosed JSON object at position $pos")
                when (input[pos]) {
                    ',' -> {
                        pos++
                        skipWhitespace()
                        if (pos < len && input[pos] == '}') {
                            throw JsonSyntaxException("Trailing comma in JSON object at position $pos")
                        }
                    }
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw JsonSyntaxException("Expected ',' or '}' in object at position $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expectChar('[')
            skipWhitespace()
            val list = ArrayList<Any?>()
            if (pos < len && input[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                val item = parseValue()
                list.add(item)
                skipWhitespace()
                if (pos >= len) throw JsonSyntaxException("Unclosed JSON array at position $pos")
                when (input[pos]) {
                    ',' -> {
                        pos++
                        skipWhitespace()
                        if (pos < len && input[pos] == ']') {
                            throw JsonSyntaxException("Trailing comma in JSON array at position $pos")
                        }
                    }
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw JsonSyntaxException("Expected ',' or ']' in array at position $pos")
                }
            }
        }

        private fun parseString(): String {
            expectChar('"')
            val sb = StringBuilder()
            while (pos < len) {
                val c = input[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= len) throw JsonSyntaxException("Incomplete escape sequence at end of string")
                        when (val esc = input[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > len) throw JsonSyntaxException("Incomplete unicode escape at position $pos")
                                val hex = input.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonSyntaxException("Invalid unicode escape \\u$hex at position $pos")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonSyntaxException("Invalid escape character '\\$esc' at position $pos")
                        }
                    }
                    else -> {
                        if (c.code < 0x20) {
                            throw JsonSyntaxException("Unescaped control character in string at position ${pos - 1}")
                        }
                        sb.append(c)
                    }
                }
            }
            throw JsonSyntaxException("Unterminated string literal")
        }

        private fun parseBoolean(): Boolean {
            if (input.startsWith("true", pos)) {
                pos += 4
                return true
            }
            if (input.startsWith("false", pos)) {
                pos += 5
                return false
            }
            throw JsonSyntaxException("Invalid boolean literal at position $pos")
        }

        private fun parseNull(): Any? {
            if (input.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw JsonSyntaxException("Invalid null literal at position $pos")
        }

        private fun parseNumber(): Double {
            val start = pos
            if (pos < len && input[pos] == '-') pos++
            if (pos >= len || input[pos] !in '0'..'9') {
                throw JsonSyntaxException("Invalid number literal at position $start")
            }
            if (input[pos] == '0') {
                pos++
            } else {
                while (pos < len && input[pos] in '0'..'9') pos++
            }
            if (pos < len && input[pos] == '.') {
                pos++
                if (pos >= len || input[pos] !in '0'..'9') {
                    throw JsonSyntaxException("Invalid fractional number at position $start")
                }
                while (pos < len && input[pos] in '0'..'9') pos++
            }
            if (pos < len && (input[pos] == 'e' || input[pos] == 'E')) {
                pos++
                if (pos < len && (input[pos] == '+' || input[pos] == '-')) pos++
                if (pos >= len || input[pos] !in '0'..'9') {
                    throw JsonSyntaxException("Invalid exponent in number at position $start")
                }
                while (pos < len && input[pos] in '0'..'9') pos++
            }
            val rawNum = input.substring(start, pos)
            val parsed = rawNum.toDoubleOrNull()
                ?: throw JsonSyntaxException("Unparseable number '$rawNum' at position $start")
            if (!parsed.isFinite()) {
                throw JsonSyntaxException("Non-finite number '$rawNum' at position $start")
            }
            return parsed
        }

        private fun skipWhitespace() {
            while (pos < len) {
                when (input[pos]) {
                    ' ', '\t', '\r', '\n' -> pos++
                    else -> return
                }
            }
        }

        private fun expectChar(expected: Char) {
            if (pos >= len || input[pos] != expected) {
                throw JsonSyntaxException("Expected '$expected' at position $pos")
            }
            pos++
        }
    }
}
