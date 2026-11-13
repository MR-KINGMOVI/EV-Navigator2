package com.evnavigator.iran.domain.policy

/**
 * Single Source of Truth for EV domain policy thresholds, freshness TTLs, reference speeds,
 * corridor detour limits, and safety reserves across the entire application.
 *
 * V12.6 Policy Invariants:
 * 1. Explicit reference speeds ([HIGHWAY_REFERENCE_CRUISE_SPEED_KMH], [ARTERIAL_REFERENCE_SPEED_KMH],
 *    [URBAN_REFERENCE_SPEED_KMH]) used ONLY as tagged [ESTIMATED_MODEL] fallbacks when segment or route
 *    duration/speed-limit inputs are absent.
 * 2. Strict separation of [STATION_STATIC_SPEC_TTL_MS] (30 days for static infrastructure verification)
 *    and [PORT_LIVE_STATUS_TTL_MS] (15 minutes for real-time OCPP/telemetry port occupancy).
 * 3. Strict separation of [DEFAULT_MAX_ROAD_DETOUR_METERS] (verified road graph detour) and
 *    [DEFAULT_MAX_CROSS_TRACK_METERS] (perpendicular geometric cross-track filter).
 */
object EvDomainPolicyConfig {

    /**
     * Unified default initial battery SoC (%) when no telemetry or user override is present.
     */
    const val DEFAULT_INITIAL_SOC_PERCENT: Double = 80.0

    /**
     * Minimum arrival or leg safety reserve SoC (%).
     */
    const val DEFAULT_MIN_RESERVE_SOC_PERCENT: Double = 15.0

    /**
     * Default target departure SoC (%) when charging at an intermediate fast-charging stop.
     */
    const val DEFAULT_TARGET_CHARGING_SOC_PERCENT: Double = 80.0

    /**
     * Maximum recommended target SoC (%) for automated DC fast charging before deep CV taper.
     */
    const val MAX_AUTOMATED_TARGET_CHARGING_SOC_PERCENT: Double = 85.0

    /**
     * Strictly validates that an input SoC percentage is finite and within [0.0, 100.0].
     * Rejects out-of-range values (< 0% or > 100%) instead of silently clamping them (Section 11).
     */
    fun validateSocPercentOrThrow(socPercent: Double, fieldName: String = "socPercent"): Double {
        require(socPercent.isFinite() && socPercent in 0.0..100.0) {
            "$fieldName ($socPercent%) is out of physical bounds [0.0, 100.0]"
        }
        return socPercent
    }

    /**
     * Reference highway cruise speed (km/h) for Iranian intercity corridors.
     * Used ONLY as an explicitly tagged `ESTIMATED_MODEL` fallback when neither segment speed limits
     * nor route estimated duration are available.
     */
    const val HIGHWAY_REFERENCE_CRUISE_SPEED_KMH: Double = 95.0

    /**
     * Reference arterial road speed (km/h).
     */
    const val ARTERIAL_REFERENCE_SPEED_KMH: Double = 70.0

    /**
     * Reference urban speed (km/h).
     */
    const val URBAN_REFERENCE_SPEED_KMH: Double = 45.0

    /**
     * Minimum physically valid moving speed (km/h) for segment energy and ETA modeling.
     */
    const val MIN_VALID_SPEED_KMH: Double = 5.0

    /**
     * Maximum physically valid cruise speed (km/h) for Iranian highways.
     */
    const val MAX_VALID_SPEED_KMH: Double = 140.0

    /**
     * Maximum age (ms) for a user-confirmed or telemetry-reported battery SoC reading (12 hours).
     */
    const val SOC_FRESHNESS_TTL_MS: Long = 12L * 3600L * 1000L

    /**
     * Maximum age (ms) for live weather observations before flagging as stale (1 hour).
     */
    const val WEATHER_OBSERVATION_FRESHNESS_TTL_MS: Long = 60L * 60L * 1000L

    /**
     * Maximum age (ms) for live traffic observations before flagging as stale (15 minutes).
     */
    const val TRAFFIC_OBSERVATION_FRESHNESS_TTL_MS: Long = 15L * 60L * 1000L

    /**
     * Maximum age (ms) for real-time charging port occupancy status (`AVAILABLE`/`OCCUPIED`) (15 minutes).
     */
    const val PORT_LIVE_STATUS_TTL_MS: Long = 15L * 60L * 1000L

    /**
     * Maximum age (ms) for static station field/operator verification before requiring re-verification (30 days).
     */
    const val STATION_STATIC_SPEC_TTL_MS: Long = 30L * 24L * 60L * 60L * 1000L

    /**
     * Maximum tolerated future clock skew (ms) when validating sensor/telemetry/catalog timestamps (60 seconds).
     */
    const val MAX_CLOCK_SKEW_TOLERANCE_MS: Long = 60_000L

    /**
     * Default maximum verified road detour distance (meters, round-trip off-corridor and back).
     */
    const val DEFAULT_MAX_ROAD_DETOUR_METERS: Double = 25_000.0

    /**
     * Default maximum perpendicular geometric cross-track distance from route polyline (meters).
     */
    const val DEFAULT_MAX_CROSS_TRACK_METERS: Double = 15_000.0

    /**
     * Maximum confidence score allowed for offline/static catalog records without live OCPP telemetry.
     */
    const val CATALOG_OFFLINE_MAX_CONFIDENCE: Double = 0.85

    /**
     * International Standard Atmosphere (ISA) sea-level reference temperature (15.0 °C = 288.15 K).
     * Used ONLY as an explicitly tagged `MODEL_ASSUMPTION` with thermal uncertainty when ambient
     * temperature telemetry/forecast is absent (never silently substituted as live 22.0 °C!).
     */
    const val ISA_STANDARD_SEA_LEVEL_TEMP_CELSIUS: Double = 15.0

    /**
     * Maximum validity age (ms) for an operator/regulatory tariff schedule before marking as STALE (90 days).
     */
    const val TARIFF_SCHEDULE_FRESHNESS_TTL_MS: Long = 90L * 24L * 60L * 60L * 1000L
}
