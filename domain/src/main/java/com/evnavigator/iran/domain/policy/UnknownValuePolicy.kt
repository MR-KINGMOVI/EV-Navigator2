package com.evnavigator.iran.domain.policy

import com.evnavigator.iran.domain.intelligence.context.InputOrigin

/**
 * Explicit calculation mode for energy, range, and trip feasibility (V13.0 Audit — Item 12 & Item 22).
 *
 * - [STRICT]: Any missing critical input (vehicleId, battery capacity, curb mass, initial SoC,
 *   or verified route distance) causes an immediate structured failure (`InsufficientDataForStrictCalculation`)
 *   with zero silent fallback assumptions.
 * - [ESTIMATE_WITH_WARNING]: Missing non-critical parameters may use bounded domain reference models,
 *   but MUST attach structured [DiagnosticCode] entries, mark `origin = ASSUMED_DEFAULT` or `ESTIMATED_MODEL`,
 *   widen `[minEnergyWh, maxEnergyWh]` uncertainty intervals, and cap output confidence.
 * - [UI_ESTIMATE_ONLY]: Quick preview mode for UI exploration; results are explicitly tagged
 *   as non-operational estimates and cannot authorize safety-critical corridor decisions.
 */
enum class EnergyCalculationMode {
    STRICT,
    ESTIMATE_WITH_WARNING,
    UI_ESTIMATE_ONLY
}

/**
 * Policy governing how unknown, stale, or missing values are handled across domain engines (Item 21).
 */
enum class UnknownValuePolicy {
    FAIL_CLOSED,
    DEGRADE_WITH_DIAGNOSTIC,
    DISPLAY_UNKNOWN_ONLY
}

/**
 * Machine-readable diagnostic codes replacing ad-hoc string assumptions (V13.0 Audit — Item 25 & Item 78).
 */
enum class DiagnosticCode(
    val severity: DiagnosticSeverity,
    val confidenceCeiling: Double,
    val defaultMessageEn: String
) {
    MISSING_VEHICLE_ID(
        severity = DiagnosticSeverity.CRITICAL,
        confidenceCeiling = 0.35,
        defaultMessageEn = "Vehicle identifier is missing or unverified"
    ),
    ASSUMED_BATTERY_CAPACITY(
        severity = DiagnosticSeverity.CRITICAL,
        confidenceCeiling = 0.45,
        defaultMessageEn = "Battery usable capacity was not verified and uses an assumed fallback"
    ),
    ASSUMED_INITIAL_SOC(
        severity = DiagnosticSeverity.CRITICAL,
        confidenceCeiling = 0.45,
        defaultMessageEn = "Initial State of Charge (SoC) was not telemetry/user-verified"
    ),
    ASSUMED_MASS(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.65,
        defaultMessageEn = "Vehicle curb mass or payload uses an estimated model value"
    ),
    ASSUMED_DRAG_COEFF(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.72,
        defaultMessageEn = "Aerodynamic drag coefficient (Cd) uses an estimated model value"
    ),
    ASSUMED_FRONTAL_AREA(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.72,
        defaultMessageEn = "Vehicle frontal area (A) uses an estimated model value"
    ),
    ASSUMED_ROLLING_RESISTANCE(
        severity = DiagnosticSeverity.INFO,
        confidenceCeiling = 0.80,
        defaultMessageEn = "Tire rolling resistance coefficient (Crr) uses a reference EV value"
    ),
    ASSUMED_EFFICIENCY(
        severity = DiagnosticSeverity.INFO,
        confidenceCeiling = 0.82,
        defaultMessageEn = "Drivetrain efficiency uses a reference model value"
    ),
    ASSUMED_SPEED(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.60,
        defaultMessageEn = "Segment speed limit was unavailable; reference cruise speed was used"
    ),
    DERIVED_SPEED_FROM_DURATION(
        severity = DiagnosticSeverity.INFO,
        confidenceCeiling = 0.72,
        defaultMessageEn = "Segment reference speed was derived from total route distance and duration"
    ),
    UNKNOWN_ELEVATION_PROFILE(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.60,
        defaultMessageEn = "Per-segment DEM elevation profile is missing; elevation uncertainty applied"
    ),
    UNKNOWN_GRADE(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.62,
        defaultMessageEn = "Road grade percent is unknown for one or more route segments"
    ),
    UNKNOWN_WEATHER(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.68,
        defaultMessageEn = "Live weather telemetry is unavailable; ambient temperature/wind unverified"
    ),
    STALE_WEATHER(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.65,
        defaultMessageEn = "Weather observation has exceeded its freshness TTL"
    ),
    STALE_CHARGER(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.55,
        defaultMessageEn = "Charging station availability status is stale or offline"
    ),
    UNKNOWN_PORT_STATUS(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.50,
        defaultMessageEn = "Real-time connector port availability is unknown"
    ),
    APPROXIMATE_DETOUR_DISTANCE(
        severity = DiagnosticSeverity.WARNING,
        confidenceCeiling = 0.58,
        defaultMessageEn = "Station detour distance was estimated geometrically rather than via road graph"
    )
}

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class DiagnosticEntry(
    val code: DiagnosticCode,
    val parameterName: String,
    val details: String = code.defaultMessageEn,
    val timestampEpochMs: Long? = null
)

/**
 * Standardized timestamp semantics for domain entities (V13.0 Audit — Item 24).
 */
data class TimestampSemantics(
    val observedAtEpochMs: Long? = null,
    val retrievedAtEpochMs: Long? = null,
    val verifiedAtEpochMs: Long? = null,
    val generatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun ageMs(nowEpochMs: Long): Long? {
        val reference = observedAtEpochMs ?: verifiedAtEpochMs ?: retrievedAtEpochMs ?: return null
        return (nowEpochMs - reference).coerceAtLeast(0L)
    }

    fun isStale(nowEpochMs: Long, ttlMs: Long): Boolean {
        val age = ageMs(nowEpochMs) ?: return true
        return age > ttlMs
    }
}

/**
 * Value wrapper carrying explicit provenance, confidence, freshness, and structured diagnostics (Item 23).
 */
data class ValueWithProvenance<out T>(
    val value: T?,
    val origin: InputOrigin,
    val confidence: Double,
    val timestamps: TimestampSemantics = TimestampSemantics(),
    val isStale: Boolean = false,
    val diagnostics: List<DiagnosticCode> = emptyList()
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be in [0.0, 1.0], was $confidence" }
    }

    val isKnown: Boolean get() = value != null && origin != InputOrigin.UNKNOWN
    val isAuthoritative: Boolean get() = value != null && origin.isAuthoritativeSource && !isStale

    companion object {
        fun <T> unknown(diagnostic: DiagnosticCode? = null): ValueWithProvenance<T> =
            ValueWithProvenance(
                value = null,
                origin = InputOrigin.UNKNOWN,
                confidence = 0.0,
                diagnostics = listOfNotNull(diagnostic)
            )

        fun <T> verified(value: T, observedAtEpochMs: Long? = null): ValueWithProvenance<T> =
            ValueWithProvenance(
                value = value,
                origin = InputOrigin.VERIFIED_DATABASE,
                confidence = 1.0,
                timestamps = TimestampSemantics(
                    observedAtEpochMs = observedAtEpochMs,
                    verifiedAtEpochMs = observedAtEpochMs
                )
            )
    }
}
