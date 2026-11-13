package com.evnavigator.iran.domain.policy

/**
 * Classification of timestamp freshness against a policy TTL and [DomainClock].
 */
enum class FreshnessClassification {
    /** Timestamp is positive, not in the future beyond clock skew tolerance, and within TTL. */
    FRESH,
    /** Timestamp is positive and valid, but age exceeds the configured policy TTL. */
    STALE,
    /** Timestamp is <= 0 (e.g. uninitialized epoch 0 or negative value). */
    INVALID_ZERO_OR_NEGATIVE_TIMESTAMP,
    /** Timestamp is further in the future than [EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS]. */
    INVALID_FUTURE_TIMESTAMP,
    /** Timestamp is null. */
    MISSING_TIMESTAMP
}

/**
 * Structured result of evaluating a record's timestamp freshness.
 */
data class FreshnessEvaluationResult(
    val classification: FreshnessClassification,
    val ageMs: Long?,
    val maxAllowedTtlMs: Long,
    val evaluatedAtEpochMs: Long,
    val sourceTimestampEpochMs: Long?,
    val diagnosticReason: String
) {
    val isFresh: Boolean
        get() = classification == FreshnessClassification.FRESH

    val isStaleOrInvalid: Boolean
        get() = classification != FreshnessClassification.FRESH
}

/**
 * Evaluates station static verification freshness and port live status freshness using an
 * injected [DomainClock].
 *
 * V12.6 P0-5 Invariants:
 * 1. `timestamp == null` -> [FreshnessClassification.MISSING_TIMESTAMP] (never treated as fresh).
 * 2. `timestamp <= 0L` -> [FreshnessClassification.INVALID_ZERO_OR_NEGATIVE_TIMESTAMP].
 * 3. `timestamp > now + MAX_CLOCK_SKEW_TOLERANCE_MS` -> [FreshnessClassification.INVALID_FUTURE_TIMESTAMP].
 * 4. `now - timestamp > maxAllowedTtlMs` -> [FreshnessClassification.STALE].
 * 5. Even if a station's static label is `VERIFIED`, an expired timestamp (e.g. a 2024 seed timestamp
 *    evaluated in 2026) is classified as [FreshnessClassification.STALE] and cannot produce a
 *    `VERIFIED_FEASIBLE` charging stop.
 */
object FreshnessPolicyEvaluator {

    fun evaluateTimestamp(
        timestampEpochMs: Long?,
        maxAllowedTtlMs: Long,
        clock: DomainClock = DomainClock.SYSTEM,
        clockSkewToleranceMs: Long = EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS
    ): FreshnessEvaluationResult {
        val now = clock.nowEpochMs()
        if (timestampEpochMs == null) {
            return FreshnessEvaluationResult(
                classification = FreshnessClassification.MISSING_TIMESTAMP,
                ageMs = null,
                maxAllowedTtlMs = maxAllowedTtlMs,
                evaluatedAtEpochMs = now,
                sourceTimestampEpochMs = null,
                diagnosticReason = "MISSING_TIMESTAMP: زمان آخرین بررسی یا بروزرسانی ثبت نشده است."
            )
        }
        if (timestampEpochMs <= 0L) {
            return FreshnessEvaluationResult(
                classification = FreshnessClassification.INVALID_ZERO_OR_NEGATIVE_TIMESTAMP,
                ageMs = null,
                maxAllowedTtlMs = maxAllowedTtlMs,
                evaluatedAtEpochMs = now,
                sourceTimestampEpochMs = timestampEpochMs,
                diagnosticReason = "INVALID_ZERO_OR_NEGATIVE_TIMESTAMP ($timestampEpochMs): زمان ثبت‌شده صفر یا منفی است."
            )
        }
        val delta = now - timestampEpochMs
        if (delta < -clockSkewToleranceMs) {
            return FreshnessEvaluationResult(
                classification = FreshnessClassification.INVALID_FUTURE_TIMESTAMP,
                ageMs = delta,
                maxAllowedTtlMs = maxAllowedTtlMs,
                evaluatedAtEpochMs = now,
                sourceTimestampEpochMs = timestampEpochMs,
                diagnosticReason = "INVALID_FUTURE_TIMESTAMP ($timestampEpochMs > $now + $clockSkewToleranceMs): زمان ثبت‌شده در آینده است."
            )
        }
        val effectiveAgeMs = delta.coerceAtLeast(0L)
        if (effectiveAgeMs > maxAllowedTtlMs) {
            val ageDays = effectiveAgeMs / (24L * 3600_000L)
            val ageMinutes = effectiveAgeMs / 60_000L
            return FreshnessEvaluationResult(
                classification = FreshnessClassification.STALE,
                ageMs = effectiveAgeMs,
                maxAllowedTtlMs = maxAllowedTtlMs,
                evaluatedAtEpochMs = now,
                sourceTimestampEpochMs = timestampEpochMs,
                diagnosticReason = "STALE_DATA: داده منقضی شده است (سن داده: ${if (ageDays > 0) "${ageDays}d" else "${ageMinutes}m"} > سقف مجاز)."
            )
        }
        return FreshnessEvaluationResult(
            classification = FreshnessClassification.FRESH,
            ageMs = effectiveAgeMs,
            maxAllowedTtlMs = maxAllowedTtlMs,
            evaluatedAtEpochMs = now,
            sourceTimestampEpochMs = timestampEpochMs,
            diagnosticReason = "FRESH"
        )
    }

    /**
     * Evaluates static station verification freshness (30-day TTL by default).
     * V13.2 Section 3 Invariant: NEVER falls back to `retrievedAtEpochMs` when `verifiedAtEpochMs` is null!
     * Data retrieval time or dataset generation time is strictly distinct from physical verification time.
     */
    fun evaluateStationVerificationFreshness(
        verifiedAtEpochMs: Long?,
        retrievedAtEpochMs: Long? = null,
        clock: DomainClock = DomainClock.SYSTEM,
        ttlMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): FreshnessEvaluationResult {
        return evaluateTimestamp(
            timestampEpochMs = verifiedAtEpochMs,
            maxAllowedTtlMs = ttlMs,
            clock = clock
        )
    }

    /**
     * Evaluates real-time port status freshness (15-minute TTL by default).
     */
    fun evaluatePortLiveStatusFreshness(
        liveStatusUpdatedAtEpochMs: Long?,
        clock: DomainClock = DomainClock.SYSTEM,
        ttlMs: Long = EvDomainPolicyConfig.PORT_LIVE_STATUS_TTL_MS
    ): FreshnessEvaluationResult {
        return evaluateTimestamp(
            timestampEpochMs = liveStatusUpdatedAtEpochMs,
            maxAllowedTtlMs = ttlMs,
            clock = clock
        )
    }

    /**
     * Evaluates static vehicle catalog specification freshness (180-day TTL by default).
     */
    fun evaluateVehicleCatalogFreshness(
        verifiedAtEpochMs: Long?,
        retrievedAtEpochMs: Long? = null,
        clock: DomainClock = DomainClock.SYSTEM,
        ttlMs: Long = 180L * 24L * 3600_000L
    ): FreshnessEvaluationResult {
        return evaluateTimestamp(
            timestampEpochMs = verifiedAtEpochMs ?: retrievedAtEpochMs,
            maxAllowedTtlMs = ttlMs,
            clock = clock
        )
    }
}
