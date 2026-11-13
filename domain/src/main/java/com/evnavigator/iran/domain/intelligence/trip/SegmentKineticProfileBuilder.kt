package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.RouteSegment
import com.evnavigator.iran.domain.model.catalog.DomainVehicle
import com.evnavigator.iran.domain.model.trip.intelligence.TripIntelligenceRequest
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.abs

/**
 * Builds explicit [TripIntelligenceContext] and per-segment [SegmentPhysicsEnergyCalculator.SegmentKineticProfile]
 * sequences from route geometry and elevation metadata (V13.3 Sections 5 & 13).
 *
 * V13.3 Invariants:
 * 1. Strictly separates legal speed limit (`speedLimitKmh`) from expected average cruising speed (`speedKmh`).
 * 2. A 2-point polyline produces exactly 1 segment (never `coerceAtLeast(2)`).
 * 3. Never fabricates a 50% uphill / 50% downhill synthetic mountain profile from route-level elevation totals.
 * 4. Detects geometry-vs-route distance mismatch instead of silently scaling arbitrary geometry.
 */
internal object SegmentKineticProfileBuilder {

    fun resolveRouteReferenceSpeed(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?,
        routeDistanceMeters: Double
    ): Pair<Double, InputOrigin> {
        val route = request.trip.route
        val routeDurationSec = context?.routeEstimatedDurationSeconds?.takeIf { it.isFinite() && it > 1.0 }
            ?: route.estimatedTime.takeIf { it > 1L }?.toDouble()
            ?: request.trip.totalTime.takeIf { it > 1L }?.toDouble()
        if (routeDurationSec != null && routeDistanceMeters > 0.0) {
            val derivedKmh = ((routeDistanceMeters / routeDurationSec) * 3.6)
                .coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH)
            return derivedKmh to InputOrigin.DERIVED_FROM_ROUTE_TIME
        }
        val segmentsWithSpeed = route.segments.filter { it.hasKnownSpeedLimit || (it.estimatedAverageSpeedKmh != null && it.estimatedAverageSpeedKmh > 5.0) }
        if (segmentsWithSpeed.isNotEmpty()) {
            val totalSegDist = segmentsWithSpeed.sumOf { it.distance.meters.coerceAtLeast(1.0) }
            val weightedSpeed = segmentsWithSpeed.sumOf { seg ->
                val effectiveCruise = seg.estimatedAverageSpeedKmh?.takeIf { it in 5.0..140.0 }
                    ?: (seg.speedLimitKmh * 0.90)
                effectiveCruise * seg.distance.meters.coerceAtLeast(1.0)
            } / totalSegDist
            return weightedSpeed.coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH) to InputOrigin.NETWORK_PROVIDER
        }
        val contextSpeed = context?.averageSpeedKmh?.value?.takeIf { it.isFinite() && it > 0.0 }
        if (contextSpeed != null) {
            return contextSpeed.coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH) to context.averageSpeedKmh.origin
        }
        return EvDomainPolicyConfig.HIGHWAY_REFERENCE_CRUISE_SPEED_KMH to InputOrigin.ESTIMATED_MODEL
    }

    fun buildExplicitContext(
        request: TripIntelligenceRequest,
        catalogVehicle: DomainVehicle?,
        batteryCapKwh: Double,
        currentSoc: Double,
        isSocVerified: Boolean,
        minReserveSoc: Double,
        routeDistanceMeters: Double,
        nowMs: Long
    ): TripIntelligenceContext {
        val vehicle = request.vehicle ?: request.trip.vehicle
        val isVerifiedCatalog = catalogVehicle != null && catalogVehicle.isVerified
        val batteryOrigin = when {
            isVerifiedCatalog -> InputOrigin.VERIFIED_DATABASE
            request.vehicle != null -> request.vehicleSpecOrigin
            else -> InputOrigin.ASSUMED_DEFAULT
        }
        val socOrigin = if (isSocVerified) InputOrigin.USER_INPUT else InputOrigin.ASSUMED_DEFAULT
        val knownMassKg = catalogVehicle?.curbMassKg?.takeIf { it > 400.0 } ?: vehicle.weight.takeIf { it > 400.0 }
        val massOrigin = when {
            isVerifiedCatalog && catalogVehicle?.curbMassKg != null -> InputOrigin.VERIFIED_DATABASE
            knownMassKg != null && request.vehicle != null -> request.vehicleSpecOrigin
            else -> InputOrigin.ASSUMED_DEFAULT
        }
        val knownCd = catalogVehicle?.dragCoefficient?.takeIf { it > 0.1 }
        val knownArea = catalogVehicle?.frontalAreaM2?.takeIf { it > 1.0 }
        val knownCrr = catalogVehicle?.rollingResistanceCoefficient?.takeIf { it > 0.001 }
        val cdOrigin = if (isVerifiedCatalog && knownCd != null) InputOrigin.VERIFIED_DATABASE else InputOrigin.ESTIMATED_MODEL
        val areaOrigin = if (isVerifiedCatalog && knownArea != null) InputOrigin.VERIFIED_DATABASE else InputOrigin.ESTIMATED_MODEL
        val crrOrigin = if (isVerifiedCatalog && knownCrr != null) InputOrigin.VERIFIED_DATABASE else InputOrigin.ESTIMATED_MODEL
        val (resolvedSpeedKmh, speedOrigin) = resolveRouteReferenceSpeed(request, null, routeDistanceMeters)
        val hasElevation = request.elevationGainMeters != null || request.elevationDescentMeters != null
        val elevOrigin = if (hasElevation) request.elevationOrigin else InputOrigin.UNKNOWN
        val tempOrigin = if (request.ambientTemperatureCelsius != null) {
            request.ambientTemperatureOrigin.takeIf { it != InputOrigin.UNKNOWN } ?: InputOrigin.USER_INPUT
        } else InputOrigin.UNKNOWN
        val grossBatteryKwh = catalogVehicle?.grossBatteryKwh?.takeIf { it >= batteryCapKwh } ?: batteryCapKwh

        return TripIntelligenceContext(
            vehicleId = vehicle.id.trim(),
            batteryCapacityKwh = InputQualityAttribute(grossBatteryKwh, batteryOrigin, nowMs),
            usableBatteryCapacityKwh = InputQualityAttribute(batteryCapKwh, batteryOrigin, nowMs),
            currentSocPercent = InputQualityAttribute(currentSoc, socOrigin, nowMs),
            curbWeightKg = if (knownMassKg != null) InputQualityAttribute(knownMassKg, massOrigin, nowMs) else InputQualityAttribute.unknown("Curb mass missing"),
            dragCoefficient = if (knownCd != null) InputQualityAttribute(knownCd, cdOrigin, nowMs) else InputQualityAttribute.estimated(0.28, 0.70, "Reference EV Cd"),
            frontalAreaM2 = if (knownArea != null) InputQualityAttribute(knownArea, areaOrigin, nowMs) else InputQualityAttribute.estimated(2.35, 0.70, "Reference EV frontal area"),
            rollingResistanceCoeff = if (knownCrr != null) InputQualityAttribute(knownCrr, crrOrigin, nowMs) else InputQualityAttribute.estimated(0.010, 0.75, "Reference EV Crr"),
            driveEfficiency = InputQualityAttribute.estimated(0.91, 0.80, "Reference EV drivetrain efficiency"),
            routeDistanceMeters = routeDistanceMeters,
            elevationGainMeters = if (request.elevationGainMeters != null) InputQualityAttribute(request.elevationGainMeters, elevOrigin, nowMs) else InputQualityAttribute.unknown(),
            elevationLossMeters = if (request.elevationDescentMeters != null) InputQualityAttribute(request.elevationDescentMeters, elevOrigin, nowMs) else InputQualityAttribute.unknown(),
            averageElevationMeters = InputQualityAttribute.unknown("No verified DEM elevation profile"),
            averageSpeedKmh = InputQualityAttribute(resolvedSpeedKmh, speedOrigin, nowMs),
            ambientTemperatureCelsius = if (request.ambientTemperatureCelsius != null) InputQualityAttribute(request.ambientTemperatureCelsius, tempOrigin, nowMs) else InputQualityAttribute.unknown("No ambient temperature observation"),
            minReserveSocPercent = InputQualityAttribute(minReserveSoc, InputOrigin.USER_INPUT, nowMs)
        )
    }

    fun buildRouteSegmentProfiles(
        request: TripIntelligenceRequest,
        context: TripIntelligenceContext?,
        routePolyline: List<GeoCoordinate>,
        routeDistanceMeters: Double
    ): List<SegmentPhysicsEnergyCalculator.SegmentKineticProfile> {
        val (refSpeedKmh, refSpeedOrigin) = resolveRouteReferenceSpeed(request, context, routeDistanceMeters)
        val explicitSegments: List<RouteSegment> = request.trip.route.segments.ifEmpty { request.trip.routeSegments }
        val knownContextAlt = context?.averageElevationMeters?.takeIf { it.isPresent }?.value?.takeIf { it.isFinite() }

        if (explicitSegments.isNotEmpty()) {
            val rawTotal = explicitSegments.sumOf { it.distance.meters.coerceAtLeast(1.0) }
            val mismatchRatio = if (routeDistanceMeters > 0.0) abs(rawTotal - routeDistanceMeters) / routeDistanceMeters else 0.0
            val scale = if (rawTotal > 0.0 && mismatchRatio <= 0.15) routeDistanceMeters / rawTotal else 1.0
            var cursor = 0.0
            return explicitSegments.mapIndexed { idx, seg ->
                val len = seg.distance.meters.coerceAtLeast(1.0) * scale
                val startD = cursor
                val endD = cursor + len
                cursor = endD
                val validElev = seg.elevationProfile.mapNotNull { it.elevation.altitude.value().takeIf { v -> v.isFinite() } }
                val interpAlt = if (validElev.isNotEmpty()) validElev.average() else knownContextAlt
                val profileGrade = if (validElev.size >= 2 && (endD - startD) >= 5.0) ((validElev.last() - validElev.first()) / (endD - startD)) * 100.0 else Double.NaN
                val resolvedGrade = if (!seg.averageGrade.isNaN() && seg.averageGrade.isFinite()) seg.averageGrade else profileGrade
                val gradeKnown = !resolvedGrade.isNaN() && resolvedGrade.isFinite()
                val gradePct = if (gradeKnown) resolvedGrade.coerceIn(-25.0, 25.0) else 0.0

                val legalLimit = seg.knownSpeedLimitKmhOrNull
                val segAvgSpeed = seg.estimatedAverageSpeedKmh?.takeIf { it in 5.0..140.0 }
                    ?: legalLimit?.let { (it * 0.90).coerceIn(15.0, 125.0) }
                    ?: refSpeedKmh
                val segSpeedOrigin = when {
                    seg.estimatedAverageSpeedKmh != null -> InputOrigin.DERIVED_FROM_ROUTE_TIME
                    legalLimit != null -> InputOrigin.NETWORK_PROVIDER
                    else -> refSpeedOrigin
                }
                val surface = when (seg.roadType?.uppercase()) { "GRAVEL", "UNPAVED" -> 1.35; "MOUNTAIN", "SECONDARY" -> 1.08; else -> 1.00 }
                SegmentPhysicsEnergyCalculator.SegmentKineticProfile(
                    segmentIndex = idx,
                    startDistanceMeters = startD,
                    endDistanceMeters = endD,
                    lengthMeters = (endD - startD).coerceAtLeast(1.0),
                    gradePercent = gradePct,
                    isGradeKnown = gradeKnown,
                    altitudeMeters = interpAlt,
                    isAltitudeKnown = interpAlt != null,
                    speedKmh = segAvgSpeed.coerceIn(EvDomainPolicyConfig.MIN_VALID_SPEED_KMH, EvDomainPolicyConfig.MAX_VALID_SPEED_KMH),
                    speedOrigin = segSpeedOrigin,
                    roadSurfaceFactor = surface,
                    trafficStopGoFactor = 0.05,
                    legalSpeedLimitKmh = legalLimit
                )
            }
        }

        // V13.3 Section 5.3 & 13: A 2-point polyline creates exactly 1 segment (never 2 artificial segments),
        // and NEVER fabricates a 50/50 uphill-then-downhill synthetic grade profile!
        val numSegs = (routePolyline.size - 1).coerceAtLeast(1)
        val rawSegLengths = DoubleArray(numSegs) { i ->
            if (routePolyline.size >= 2 && i < routePolyline.size - 1) {
                CorridorStationProjector.haversineMeters(routePolyline[i], routePolyline[i + 1]).coerceAtLeast(1.0)
            } else {
                routeDistanceMeters / numSegs
            }
        }
        val geomSum = rawSegLengths.sum()
        val mismatchRatio = if (routeDistanceMeters > 0.0 && geomSum > 0.0) abs(geomSum - routeDistanceMeters) / routeDistanceMeters else 0.0
        val hasGeometryDistanceMismatch = mismatchRatio > 0.15
        val scale = if (geomSum > 0.0 && !hasGeometryDistanceMismatch) routeDistanceMeters / geomSum else 1.0

        val rawGain = (request.elevationGainMeters ?: context?.elevationGainMeters?.value ?: 0.0).coerceAtLeast(0.0)
        val rawLoss = (request.elevationDescentMeters ?: context?.elevationLossMeters?.value ?: 0.0).coerceAtLeast(0.0)
        val hasNetElevationOnly = request.elevationGainMeters != null || request.elevationDescentMeters != null ||
            context?.elevationGainMeters?.isPresent == true || context?.elevationLossMeters?.isPresent == true
        val netElevationDeltaM = rawGain - rawLoss
        val totalEffectiveDist = (if (hasGeometryDistanceMismatch && routeDistanceMeters > 0.0) routeDistanceMeters else geomSum * scale).coerceAtLeast(1.0)
        // If only route-level gain/loss is provided without per-vertex DEM, only net uniform grade is known when non-zero;
        // per-segment local topography remains marked isGradeKnown = false so DEM uncertainty is preserved!
        val uniformNetGradePct = if (hasNetElevationOnly) ((netElevationDeltaM / totalEffectiveDist) * 100.0).coerceIn(-25.0, 25.0) else 0.0

        var cursor = 0.0
        return (0 until numSegs).map { idx ->
            val len = if (numSegs == 1 && routeDistanceMeters > 0.0) {
                routeDistanceMeters.coerceAtLeast(1.0)
            } else {
                (rawSegLengths[idx] * scale).coerceAtLeast(1.0)
            }
            val startD = cursor
            val endD = cursor + len
            cursor = endD
            SegmentPhysicsEnergyCalculator.SegmentKineticProfile(
                segmentIndex = idx,
                startDistanceMeters = startD,
                endDistanceMeters = endD,
                lengthMeters = len,
                gradePercent = uniformNetGradePct,
                isGradeKnown = false,
                altitudeMeters = knownContextAlt,
                isAltitudeKnown = knownContextAlt != null,
                speedKmh = refSpeedKmh,
                speedOrigin = refSpeedOrigin,
                roadSurfaceFactor = 1.0,
                trafficStopGoFactor = 0.05,
                legalSpeedLimitKmh = null
            )
        }
    }
}
