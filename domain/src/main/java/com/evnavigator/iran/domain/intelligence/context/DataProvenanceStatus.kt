package com.evnavigator.iran.domain.intelligence.context

/**
 * Indicates the authentic origin and trustworthiness of an input datum (V13.2 Section 2 & Section 12).
 *
 * Strictly distinguishes:
 * - [MEASURED_TELEMETRY]: Directly measured by onboard sensors, GPS, or vehicle OBD-II/telemetry
 * - [VERIFIED_DATABASE]: From a verified authoritative database (e.g. verified OEM catalog or surveyed DEM)
 * - [NETWORK_PROVIDER]: Directly supplied by an external routing, weather, or OCPP network provider
 * - [DERIVED_FROM_ROUTE_TIME]: Mathematically derived from route distance divided by route duration
 * - [USER_INPUT] / [USER_CONFIGURED]: Explicitly entered by the user/driver in the request or UI
 * - [ESTIMATED_MODEL]: Derived via physical, statistical, or mathematical modeling
 * - [MODEL_ASSUMPTION]: Explicit physics/engineering model assumption when an input is absent (never hidden!)
 * - [ASSUMED_DEFAULT]: Substituted as a fallback assumption when actual value is unavailable
 * - [UNKNOWN]: Value is entirely unknown or absent
 */
enum class InputOrigin {
    MEASURED_TELEMETRY,
    VERIFIED_DATABASE,
    NETWORK_PROVIDER,
    DERIVED_FROM_ROUTE_TIME,
    USER_INPUT,
    USER_CONFIGURED,
    ESTIMATED_MODEL,
    MODEL_ASSUMPTION,
    ASSUMED_DEFAULT,
    UNKNOWN;

    val isAuthoritativeSource: Boolean
        get() = this == MEASURED_TELEMETRY ||
            this == VERIFIED_DATABASE ||
            this == NETWORK_PROVIDER ||
            this == USER_INPUT ||
            this == USER_CONFIGURED
}

/**
 * V13.2 Section 12: Standardized freshness state for any evidence or field.
 */
enum class FieldFreshnessState {
    FRESH,
    STALE,
    EXPIRED,
    UNKNOWN
}

/**
 * V13.2 Section 12: Standardized verification state for any evidence or field.
 */
enum class FieldVerificationState {
    VERIFIED,
    PARTIALLY_VERIFIED,
    UNVERIFIED,
    ESTIMATED,
    UNKNOWN
}

/**
 * V13.2 Section 6: Separates mere presence of a URL from verified page existence and verified content.
 */
enum class UrlVerificationStatus {
    HTTP_CONTENT_VERIFIED,
    LINK_UNCHECKED,
    INTERNAL_URN_REJECTED,
    MISSING
}

/**
 * V13.2 Section 12 & 14: Quantitative uncertainty range preserved through calculations.
 */
data class UncertaintyRange(
    val min: Double,
    val nominal: Double,
    val max: Double,
    val unit: String
) {
    init {
        require(min.isFinite() && nominal.isFinite() && max.isFinite()) {
            "UncertaintyRange bounds must be finite ($min, $nominal, $max)"
        }
        require(min <= nominal && nominal <= max) {
            "UncertaintyRange must satisfy min ($min) <= nominal ($nominal) <= max ($max)"
        }
    }

    val halfWidth: Double get() = (max - min) / 2.0
    val relativeUncertaintyPercent: Double
        get() = if (kotlin.math.abs(nominal) > 1e-9) (halfWidth / kotlin.math.abs(nominal)) * 100.0 else 0.0
}

/**
 * V13.2 Section 12: Central Evidence Document model.
 * Enforces separation between retrieval time (`retrievedAtEpochMs`), publication time (`publishedAtEpochMs`),
 * and independent verification time (`verifiedAtEpochMs`).
 */
data class EvidenceDocument(
    val evidenceId: String,
    val sourceName: String,
    val sourceId: String,
    val retrievedAtEpochMs: Long,
    val publishedAtEpochMs: Long? = null,
    val verifiedAtEpochMs: Long? = null,
    val verificationMethod: String,
    val verificationState: FieldVerificationState,
    val freshnessState: FieldFreshnessState,
    val expiresAtEpochMs: Long? = null,
    val externalUrl: String? = null,
    val urlVerificationStatus: UrlVerificationStatus = when {
        externalUrl.isNullOrBlank() -> UrlVerificationStatus.MISSING
        externalUrl.startsWith("urn:") -> UrlVerificationStatus.INTERNAL_URN_REJECTED
        else -> UrlVerificationStatus.LINK_UNCHECKED
    },
    val limitations: List<String> = emptyList()
) {
    /**
     * V13.2 Section 3 & 6 Invariant: A URL alone (`LINK_UNCHECKED`) or an identical retrieval/verification timestamp
     * without independent verification cannot qualify as strictly `VERIFIED` unless backed by verified timestamp & method.
     */
    val hasValidIndependentVerification: Boolean
        get() = verifiedAtEpochMs != null &&
            verifiedAtEpochMs > 0L &&
            verifiedAtEpochMs != retrievedAtEpochMs &&
            freshnessState == FieldFreshnessState.FRESH &&
            urlVerificationStatus != UrlVerificationStatus.INTERNAL_URN_REJECTED &&
            verificationState == FieldVerificationState.VERIFIED
}

/**
 * V13.2 Section 12: Field-by-field provenance and uncertainty record.
 * Rules enforced:
 * 1. Positive confidence without valid evidence is forbidden for `VERIFIED` state.
 * 2. `UNKNOWN` value is `null` (never replaced with `0.0`).
 * 3. `ESTIMATED` / `MODEL_ASSUMPTION` is strictly separated from `MEASURED_TELEMETRY`.
 */
data class FieldProvenanceRecord<T>(
    val fieldName: String,
    val value: T?,
    val valueOrigin: InputOrigin,
    val evidenceId: String? = null,
    val observedAtEpochMs: Long? = null,
    val verificationState: FieldVerificationState = when {
        value == null || valueOrigin == InputOrigin.UNKNOWN -> FieldVerificationState.UNKNOWN
        valueOrigin == InputOrigin.ESTIMATED_MODEL || valueOrigin == InputOrigin.MODEL_ASSUMPTION ||
            valueOrigin == InputOrigin.ASSUMED_DEFAULT || valueOrigin == InputOrigin.DERIVED_FROM_ROUTE_TIME -> FieldVerificationState.ESTIMATED
        evidenceId.isNullOrBlank() -> FieldVerificationState.UNVERIFIED
        else -> FieldVerificationState.PARTIALLY_VERIFIED
    },
    val freshnessState: FieldFreshnessState = FieldFreshnessState.UNKNOWN,
    val confidence: Double = defaultFieldConfidence(value, valueOrigin, evidenceId, verificationState, freshnessState),
    val uncertaintyRange: UncertaintyRange? = null
) {
    init {
        require(confidence in 0.0..1.0) { "Field confidence must be in [0.0, 1.0], was $confidence" }
        if (value == null || valueOrigin == InputOrigin.UNKNOWN || verificationState == FieldVerificationState.UNKNOWN) {
            require(confidence == 0.0) { "UNKNOWN field '$fieldName' must have confidence == 0.0" }
        }
        if (evidenceId.isNullOrBlank() && verificationState == FieldVerificationState.VERIFIED) {
            throw IllegalArgumentException("Field '$fieldName' cannot be VERIFIED without a non-blank evidenceId")
        }
    }

    val isMeasured: Boolean get() = value != null && valueOrigin == InputOrigin.MEASURED_TELEMETRY
    val isEstimatedOrAssumed: Boolean
        get() = valueOrigin == InputOrigin.ESTIMATED_MODEL ||
            valueOrigin == InputOrigin.MODEL_ASSUMPTION ||
            valueOrigin == InputOrigin.ASSUMED_DEFAULT ||
            valueOrigin == InputOrigin.DERIVED_FROM_ROUTE_TIME

    companion object {
        fun <T> defaultFieldConfidence(
            value: T?,
            origin: InputOrigin,
            evidenceId: String?,
            verificationState: FieldVerificationState,
            freshnessState: FieldFreshnessState
        ): Double {
            if (value == null || origin == InputOrigin.UNKNOWN || verificationState == FieldVerificationState.UNKNOWN) {
                return 0.0
            }
            if (freshnessState == FieldFreshnessState.EXPIRED) return 0.15
            if (freshnessState == FieldFreshnessState.STALE) return 0.35
            val base = when (origin) {
                InputOrigin.MEASURED_TELEMETRY -> 0.92
                InputOrigin.VERIFIED_DATABASE -> if (!evidenceId.isNullOrBlank()) 0.85 else 0.50
                InputOrigin.NETWORK_PROVIDER -> if (!evidenceId.isNullOrBlank()) 0.80 else 0.50
                InputOrigin.USER_INPUT, InputOrigin.USER_CONFIGURED -> 0.75
                InputOrigin.DERIVED_FROM_ROUTE_TIME -> 0.65
                InputOrigin.ESTIMATED_MODEL -> 0.55
                InputOrigin.MODEL_ASSUMPTION -> 0.40
                InputOrigin.ASSUMED_DEFAULT -> 0.35
                InputOrigin.UNKNOWN -> 0.0
            }
            return if (evidenceId.isNullOrBlank() && origin != InputOrigin.MEASURED_TELEMETRY) {
                base.coerceAtMost(0.55)
            } else {
                base
            }
        }
    }
}

/**
 * Encapsulates an input value with explicit quality, provenance, freshness, and assumptions.
 */
data class InputQualityAttribute<T>(
    val value: T?,
    val origin: InputOrigin,
    val timestampEpochMs: Long? = null,
    val confidence: Double = defaultConfidenceFor(value, origin, false),
    val isStale: Boolean = false,
    val assumptionNote: String? = null
) {
    /**
     * Secondary constructor supporting 4-arg `(value, origin, timestampEpochMs, isStale)` callers.
     */
    constructor(
        value: T?,
        origin: InputOrigin,
        timestampEpochMs: Long?,
        isStale: Boolean
    ) : this(
        value = value,
        origin = origin,
        timestampEpochMs = timestampEpochMs,
        confidence = defaultConfidenceFor(value, origin, isStale),
        isStale = isStale,
        assumptionNote = null
    )

    init {
        require(confidence in 0.0..1.0) { "Confidence must be within [0.0, 1.0], was $confidence" }
    }

    val isPresent: Boolean get() = value != null && origin != InputOrigin.UNKNOWN
    val isMissing: Boolean get() = value == null || origin == InputOrigin.UNKNOWN
    val isAssumption: Boolean get() = origin == InputOrigin.ASSUMED_DEFAULT || origin == InputOrigin.MODEL_ASSUMPTION

    /**
     * Returns true only when [value] is present, [origin] is authoritative, and the observation is not stale.
     */
    fun isAuthoritative(): Boolean = value != null && origin.isAuthoritativeSource && !isStale

    companion object {
        private fun <T> defaultConfidenceFor(value: T?, origin: InputOrigin, isStale: Boolean): Double = when {
            value == null || origin == InputOrigin.UNKNOWN -> 0.0
            isStale -> 0.40
            origin == InputOrigin.VERIFIED_DATABASE -> 1.0
            origin == InputOrigin.MEASURED_TELEMETRY -> 0.95
            origin == InputOrigin.NETWORK_PROVIDER -> 0.90
            origin == InputOrigin.USER_INPUT || origin == InputOrigin.USER_CONFIGURED -> 0.88
            origin == InputOrigin.DERIVED_FROM_ROUTE_TIME -> 0.72
            origin == InputOrigin.ESTIMATED_MODEL -> 0.68
            origin == InputOrigin.MODEL_ASSUMPTION -> 0.45
            origin == InputOrigin.ASSUMED_DEFAULT -> 0.45
            else -> 0.45
        }

        fun <T> unknown(note: String? = null): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = null,
                origin = InputOrigin.UNKNOWN,
                confidence = 0.0,
                assumptionNote = note
            )

        fun <T> modelAssumption(value: T, reason: String, confidence: Double = 0.45): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.MODEL_ASSUMPTION,
                confidence = confidence,
                assumptionNote = reason
            )

        fun <T> assumed(value: T, reason: String, confidence: Double = 0.45): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.ASSUMED_DEFAULT,
                confidence = confidence,
                assumptionNote = reason
            )

        fun <T> derived(value: T, rationale: String, confidence: Double = 0.72): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.DERIVED_FROM_ROUTE_TIME,
                confidence = confidence,
                assumptionNote = rationale
            )

        fun <T> derivedFromRouteTime(
            value: T,
            timestampEpochMs: Long? = null,
            confidence: Double = 0.72,
            note: String = "Derived from route distance and duration"
        ): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.DERIVED_FROM_ROUTE_TIME,
                timestampEpochMs = timestampEpochMs,
                confidence = confidence,
                assumptionNote = note
            )

        fun <T> verified(value: T, timestampEpochMs: Long? = null): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.VERIFIED_DATABASE,
                timestampEpochMs = timestampEpochMs,
                confidence = 1.0
            )

        fun <T> measured(value: T, timestampEpochMs: Long): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.MEASURED_TELEMETRY,
                timestampEpochMs = timestampEpochMs,
                confidence = 0.95
            )

        fun <T> networkProvider(value: T, timestampEpochMs: Long? = null, confidence: Double = 0.90): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.NETWORK_PROVIDER,
                timestampEpochMs = timestampEpochMs,
                confidence = confidence
            )

        fun <T> userInput(value: T, timestampEpochMs: Long? = null): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.USER_INPUT,
                timestampEpochMs = timestampEpochMs,
                confidence = 0.90
            )

        fun <T> userConfigured(value: T): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.USER_CONFIGURED,
                confidence = 0.90
            )

        fun <T> estimated(value: T, confidence: Double = 0.68, note: String? = null): InputQualityAttribute<T> =
            InputQualityAttribute(
                value = value,
                origin = InputOrigin.ESTIMATED_MODEL,
                confidence = confidence,
                assumptionNote = note
            )
    }
}
