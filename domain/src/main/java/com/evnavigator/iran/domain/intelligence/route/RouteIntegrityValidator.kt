package com.evnavigator.iran.domain.intelligence.route

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.model.Bearing
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Domain-level route geometry, origin/destination endpoint alignment, segment contiguity,
 * and speed-limit provenance validator (V13.2 Master Fix Specification — Sections 1 & 2).
 */
object RouteIntegrityValidator {

    const val DEFAULT_MAX_ENDPOINT_SNAP_METERS = 2_500.0
    const val DEFAULT_MAX_SEGMENT_GAP_METERS = 50.0
    const val MIN_ORIGIN_DESTINATION_SEPARATION_METERS = 5.0
    const val MIN_GEOMETRY_TO_REPORTED_DISTANCE_RATIO = 0.45
    const val MAX_GEOMETRY_TO_REPORTED_DISTANCE_RATIO = 1.65

    enum class ValidationReasonCode {
        VALID,
        EMPTY_OR_SINGLE_POINT_GEOMETRY,
        INVALID_COORDINATE_BOUNDS_OR_NAN,
        ORIGIN_EQUALS_DESTINATION,
        ORIGIN_ENDPOINT_MISMATCH,
        DESTINATION_ENDPOINT_MISMATCH,
        ROUTE_GEOMETRY_REVERSED,
        REPORTED_DISTANCE_INVALID,
        GEOMETRY_DISTANCE_MISMATCH,
        DISCONTINUOUS_SEGMENTS,
        INVALID_SPEED_VALUE
    }

    data class ValidatedRouteSegmentSpeed(
        val legalSpeedLimitKmh: Double?,
        val legalSpeedOrigin: InputOrigin,
        val estimatedCruiseSpeedKmh: Double,
        val cruiseSpeedOrigin: InputOrigin
    )

    data class RouteValidationResult(
        val isValid: Boolean,
        val reasonCode: ValidationReasonCode,
        val diagnosticMessage: String,
        val computedGeometryMeters: Double = 0.0,
        val originSnapOffsetMeters: Double = 0.0,
        val destinationSnapOffsetMeters: Double = 0.0,
        val routeProvider: String = "UNKNOWN",
        val routeId: String = "",
        val validatedAtEpochMs: Long = 0L
    )

    fun resolveSegmentSpeedWithProvenance(
        rawLegalSpeedLimitKmh: Double?,
        segmentDistanceMeters: Double? = null,
        segmentDurationSeconds: Double? = null,
        fallbackReferenceSpeedKmh: Double = EvDomainPolicyConfig.HIGHWAY_REFERENCE_CRUISE_SPEED_KMH
    ): Result<ValidatedRouteSegmentSpeed> {
        if (rawLegalSpeedLimitKmh != null && (!rawLegalSpeedLimitKmh.isFinite() || rawLegalSpeedLimitKmh < 0.0 || rawLegalSpeedLimitKmh > 250.0)) {
            return Result.failure(
                IllegalArgumentException("Invalid speedLimitKmh ($rawLegalSpeedLimitKmh): must be finite and within (0, 250] km/h")
            )
        }

        val validLegalLimit = rawLegalSpeedLimitKmh?.takeIf {
            it.isFinite() && it in EvDomainPolicyConfig.MIN_VALID_SPEED_KMH..EvDomainPolicyConfig.MAX_VALID_SPEED_KMH
        }
        val legalOrigin = if (validLegalLimit != null) InputOrigin.NETWORK_PROVIDER else InputOrigin.UNKNOWN

        val derivedFromTime = if (
            segmentDistanceMeters != null && segmentDurationSeconds != null &&
            segmentDistanceMeters.isFinite() && segmentDistanceMeters > 0.0 &&
            segmentDurationSeconds.isFinite() && segmentDurationSeconds > 0.5
        ) {
            ((segmentDistanceMeters / segmentDurationSeconds) * 3.6)
                .coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH)
        } else null

        val (cruiseSpeed, cruiseOrigin) = when {
            validLegalLimit != null && derivedFromTime != null ->
                minOf(validLegalLimit, derivedFromTime) to InputOrigin.DERIVED_FROM_ROUTE_TIME
            validLegalLimit != null -> validLegalLimit to InputOrigin.NETWORK_PROVIDER
            derivedFromTime != null -> derivedFromTime to InputOrigin.DERIVED_FROM_ROUTE_TIME
            else -> {
                val safeFallback = fallbackReferenceSpeedKmh.takeIf { it.isFinite() && it > 0.0 }
                    ?: EvDomainPolicyConfig.HIGHWAY_REFERENCE_CRUISE_SPEED_KMH
                safeFallback.coerceIn(
                    EvDomainPolicyConfig.MIN_VALID_SPEED_KMH,
                    EvDomainPolicyConfig.MAX_VALID_SPEED_KMH
                ) to InputOrigin.ESTIMATED_MODEL
            }
        }

        return Result.success(
            ValidatedRouteSegmentSpeed(
                legalSpeedLimitKmh = validLegalLimit,
                legalSpeedOrigin = legalOrigin,
                estimatedCruiseSpeedKmh = cruiseSpeed,
                cruiseSpeedOrigin = cruiseOrigin
            )
        )
    }

    fun buildFallbackRouteSegmentWithoutFakeSpeedLimit(
        id: String,
        start: GeoCoordinate,
        end: GeoCoordinate,
        distanceMeters: Double,
        roadName: String = "مسیر تخمینی (بدون فراداده سرعت شبکه)"
    ): RouteSegment = RouteSegment(
        id = id,
        start = start,
        end = end,
        distance = Distance.fromMeters(distanceMeters.coerceAtLeast(1.0)),
        bearing = Bearing(0.0),
        roadName = roadName,
        roadType = "UNKNOWN",
        speedLimitKmh = 0
    )

    fun validateRouteGeometryAndEndpoints(
        requestedOrigin: GeoCoordinate,
        requestedDestination: GeoCoordinate,
        geometry: List<GeoCoordinate>,
        reportedDistanceMeters: Double,
        segments: List<RouteSegment> = emptyList(),
        maxEndpointSnapMeters: Double = DEFAULT_MAX_ENDPOINT_SNAP_METERS,
        maxSegmentGapMeters: Double = DEFAULT_MAX_SEGMENT_GAP_METERS,
        routeProvider: String = "UNKNOWN",
        routeId: String = "",
        validatedAtEpochMs: Long = 0L
    ): RouteValidationResult {
        fun invalid(
            code: ValidationReasonCode,
            msg: String,
            polyM: Double = 0.0,
            origSnap: Double = 0.0,
            destSnap: Double = 0.0
        ) = RouteValidationResult(false, code, msg, polyM, origSnap, destSnap, routeProvider, routeId, validatedAtEpochMs)

        if (!isValidCoordinate(requestedOrigin) || !isValidCoordinate(requestedDestination)) {
            return invalid(ValidationReasonCode.INVALID_COORDINATE_BOUNDS_OR_NAN, "Origin or destination has invalid WGS84 coordinates")
        }

        val odSeparation = haversineMeters(requestedOrigin, requestedDestination)
        if (odSeparation < MIN_ORIGIN_DESTINATION_SEPARATION_METERS) {
            return invalid(ValidationReasonCode.ORIGIN_EQUALS_DESTINATION, "Origin and destination separation ${"%.2f".format(odSeparation)}m < ${MIN_ORIGIN_DESTINATION_SEPARATION_METERS}m")
        }

        if (geometry.size < 2) {
            return invalid(ValidationReasonCode.EMPTY_OR_SINGLE_POINT_GEOMETRY, "Route geometry has ${geometry.size} points (minimum 2 required)")
        }

        for ((idx, pt) in geometry.withIndex()) {
            if (!isValidCoordinate(pt)) {
                return invalid(ValidationReasonCode.INVALID_COORDINATE_BOUNDS_OR_NAN, "Geometry coordinate[$idx] (${pt.latitude}, ${pt.longitude}) is invalid")
            }
        }

        if (!reportedDistanceMeters.isFinite() || reportedDistanceMeters <= 0.0) {
            return invalid(ValidationReasonCode.REPORTED_DISTANCE_INVALID, "Reported route distance ($reportedDistanceMeters m) is non-positive or non-finite")
        }

        val firstPt = geometry.first()
        val lastPt = geometry.last()
        val originSnapMeters = haversineMeters(requestedOrigin, firstPt)
        val destSnapMeters = haversineMeters(requestedDestination, lastPt)
        val reversedOriginSnap = haversineMeters(requestedOrigin, lastPt)
        val reversedDestSnap = haversineMeters(requestedDestination, firstPt)

        if (originSnapMeters > maxEndpointSnapMeters &&
            destSnapMeters > maxEndpointSnapMeters &&
            reversedOriginSnap <= maxEndpointSnapMeters &&
            reversedDestSnap <= maxEndpointSnapMeters
        ) {
            return invalid(ValidationReasonCode.ROUTE_GEOMETRY_REVERSED, "Route geometry is reversed relative to origin/destination", 0.0, originSnapMeters, destSnapMeters)
        }

        if (originSnapMeters > maxEndpointSnapMeters) {
            return invalid(ValidationReasonCode.ORIGIN_ENDPOINT_MISMATCH, "Geometry start point is ${originSnapMeters.toInt()}m from origin", 0.0, originSnapMeters, destSnapMeters)
        }

        if (destSnapMeters > maxEndpointSnapMeters) {
            return invalid(ValidationReasonCode.DESTINATION_ENDPOINT_MISMATCH, "Geometry end point is ${destSnapMeters.toInt()}m from destination", 0.0, originSnapMeters, destSnapMeters)
        }

        val polyMeters = computePolylineLengthMeters(geometry)
        val ratio = polyMeters / reportedDistanceMeters
        if (polyMeters < 1.0 || ratio !in MIN_GEOMETRY_TO_REPORTED_DISTANCE_RATIO..MAX_GEOMETRY_TO_REPORTED_DISTANCE_RATIO) {
            return invalid(
                ValidationReasonCode.GEOMETRY_DISTANCE_MISMATCH,
                "Geometric length (${polyMeters.toInt()}m) diverges from reported (${reportedDistanceMeters.toInt()}m), ratio=${"%.2f".format(ratio)}",
                polyMeters, originSnapMeters, destSnapMeters
            )
        }

        if (segments.size >= 2) {
            for (i in 0 until segments.size - 1) {
                val gap = haversineMeters(segments[i].end, segments[i + 1].start)
                if (gap > maxSegmentGapMeters) {
                    return invalid(
                        ValidationReasonCode.DISCONTINUOUS_SEGMENTS,
                        "Segment[$i].end and Segment[${i + 1}].start disconnected by ${gap.toInt()}m",
                        polyMeters, originSnapMeters, destSnapMeters
                    )
                }
            }
        }

        return RouteValidationResult(
            isValid = true,
            reasonCode = ValidationReasonCode.VALID,
            diagnosticMessage = "VALID",
            computedGeometryMeters = polyMeters,
            originSnapOffsetMeters = originSnapMeters,
            destinationSnapOffsetMeters = destSnapMeters,
            routeProvider = routeProvider,
            routeId = routeId,
            validatedAtEpochMs = validatedAtEpochMs
        )
    }

    fun validateDomainRoute(
        route: Route,
        requestedOrigin: GeoCoordinate = route.start,
        requestedDestination: GeoCoordinate = route.end,
        maxEndpointSnapMeters: Double = DEFAULT_MAX_ENDPOINT_SNAP_METERS,
        maxSegmentGapMeters: Double = DEFAULT_MAX_SEGMENT_GAP_METERS
    ): RouteValidationResult {
        val geometry = when {
            route.waypoints.size >= 2 -> route.waypoints
            route.segments.isNotEmpty() -> listOf(route.segments.first().start) + route.segments.map { it.end }
            else -> emptyList()
        }
        return validateRouteGeometryAndEndpoints(
            requestedOrigin = requestedOrigin,
            requestedDestination = requestedDestination,
            geometry = geometry,
            reportedDistanceMeters = route.distance.meters,
            segments = route.segments,
            maxEndpointSnapMeters = maxEndpointSnapMeters,
            maxSegmentGapMeters = maxSegmentGapMeters,
            routeId = route.id
        )
    }

    fun isValidCoordinate(coord: GeoCoordinate): Boolean =
        coord.latitude.isFinite() &&
            coord.longitude.isFinite() &&
            coord.latitude in -90.0..90.0 &&
            coord.longitude in -180.0..180.0 &&
            !(abs(coord.latitude) < 1e-6 && abs(coord.longitude) < 1e-6)

    fun computePolylineLengthMeters(polyline: List<GeoCoordinate>): Double {
        if (polyline.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until polyline.size - 1) sum += haversineMeters(polyline[i], polyline[i + 1])
        return sum
    }

    fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val h = (sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon).coerceIn(0.0, 1.0)
        return 2.0 * r * atan2(sqrt(h), sqrt(1.0 - h))
    }
}
