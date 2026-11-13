package com.evnavigator.iran.domain.model.trip.intelligence

import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Trip
import com.evnavigator.iran.domain.model.Vehicle
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig

/**
 * Request for adaptive trip intelligence that encapsulates all necessary inputs
 * for calculating optimal routes with charging requirements.
 *
 * V12.6 Contract:
 * - Explicit [initialSocPercent] and [isInitialSocVerified] prevent silent 50% SOC assumptions.
 * - Separate [maxRoadDetourMeters] (for road-graph verified detours) and [maxCrossTrackMeters]
 *   (for perpendicular geometric distance from route polyline).
 * - Explicit [acMaxChargingPowerKw] and [dcMaxChargingPowerKw] prevent guessing AC power from DC power.
 * - Explicit [vehicleSpecOrigin], [elevationOrigin], and [ambientTemperatureOrigin] prevent falsely
 *   labeling caller-supplied or estimated inputs as `InputOrigin.VERIFIED_DATABASE`.
 */
data class TripIntelligenceRequest(
    val trip: Trip,
    val vehicle: Vehicle? = null,
    val routeDistanceMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val elevationDescentMeters: Double? = null,
    val initialSocPercent: Double? = 85.0,
    val isInitialSocVerified: Boolean = false,
    val maxDetourMeters: Double = EvDomainPolicyConfig.DEFAULT_MAX_ROAD_DETOUR_METERS,
    val maxRoadDetourMeters: Double = maxDetourMeters,
    val maxCrossTrackMeters: Double = EvDomainPolicyConfig.DEFAULT_MAX_CROSS_TRACK_METERS,
    val routePolyline: List<GeoCoordinate> = emptyList(),
    val supportedConnectors: List<String> = emptyList(),
    val acMaxChargingPowerKw: Double? = null,
    val dcMaxChargingPowerKw: Double? = null,
    val vehicleSpecOrigin: InputOrigin = InputOrigin.USER_INPUT,
    val elevationOrigin: InputOrigin = InputOrigin.USER_INPUT,
    val ambientTemperatureCelsius: Double? = null,
    val ambientTemperatureOrigin: InputOrigin = InputOrigin.UNKNOWN
)
