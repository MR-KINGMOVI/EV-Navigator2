package com.evnavigator.iran.domain.model.catalog

import com.evnavigator.iran.domain.intelligence.context.FieldFreshnessState
import com.evnavigator.iran.domain.intelligence.context.FieldProvenanceRecord
import com.evnavigator.iran.domain.intelligence.context.FieldVerificationState
import com.evnavigator.iran.domain.intelligence.context.InputOrigin
import com.evnavigator.iran.domain.intelligence.context.UrlVerificationStatus
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import kotlin.math.roundToLong

/**
 * V13.2 Section 5: Explicit currency unit for Iranian EV charging tariffs.
 * Prevents 10x calculation errors between Iranian Rial (IRR) and Toman (IRT).
 */
enum class TariffCurrency(val displayUnitFa: String, val isoOrLocalCode: String) {
    IRR_RIAL(displayUnitFa = "ریال", isoOrLocalCode = "IRR"),
    IRT_TOMAN(displayUnitFa = "تومان", isoOrLocalCode = "IRT"),
    UNKNOWN(displayUnitFa = "واحد پول نامشخص", isoOrLocalCode = "UNKNOWN")
}

/**
 * V13.2 Section 5: Centralized, single-source-of-truth currency converter between Rial and Toman.
 * Never converts when source or target currency is [TariffCurrency.UNKNOWN] or amount is invalid.
 */
object CentralCurrencyConverter {
    const val RIALS_PER_TOMAN: Double = 10.0

    fun rialToToman(amountRial: Double): Double {
        require(amountRial.isFinite() && amountRial >= 0.0) {
            "Amount in Rial must be finite and non-negative, was $amountRial"
        }
        return amountRial / RIALS_PER_TOMAN
    }

    fun tomanToRial(amountToman: Double): Double {
        require(amountToman.isFinite() && amountToman >= 0.0) {
            "Amount in Toman must be finite and non-negative, was $amountToman"
        }
        return amountToman * RIALS_PER_TOMAN
    }

    fun convert(amount: Double, from: TariffCurrency, to: TariffCurrency): Double? {
        if (!amount.isFinite() || amount < 0.0) return null
        if (from == TariffCurrency.UNKNOWN || to == TariffCurrency.UNKNOWN) return null
        if (from == to) return amount
        return when (from to to) {
            TariffCurrency.IRR_RIAL to TariffCurrency.IRT_TOMAN -> rialToToman(amount)
            TariffCurrency.IRT_TOMAN to TariffCurrency.IRR_RIAL -> tomanToRial(amount)
            else -> null
        }
    }

    fun formatTariffDisplayFa(
        amount: Double?,
        currency: TariffCurrency,
        freshness: FieldFreshnessState,
        verification: FieldVerificationState
    ): String {
        if (amount == null || !amount.isFinite() || amount <= 0.0 || currency == TariffCurrency.UNKNOWN) {
            return "تعرفه نامشخص (فاقد منبع یا واحد پول معتبر)"
        }
        val formattedNum = "%,d".format(amount.roundToLong())
        val qualifier = when {
            freshness == FieldFreshnessState.EXPIRED || freshness == FieldFreshnessState.STALE -> "قدیمی / منقضی (تخمینی)"
            verification == FieldVerificationState.VERIFIED && freshness == FieldFreshnessState.FRESH -> "تأییدشده"
            else -> "تخمینی (نیازمند بررسی)"
        }
        return "$formattedNum ${currency.displayUnitFa} به ازای هر kWh ($qualifier)"
    }
}

/**
 * V13.2 Section 5: Result of evaluating charging session cost from a [ChargingTariffSchedule].
 */
data class ChargingCostEvaluation(
    val amount: Double?,
    val currency: TariffCurrency,
    val isDeterministicVerified: Boolean,
    val freshnessState: FieldFreshnessState,
    val verificationState: FieldVerificationState,
    val statusCode: String,
    val uiLabelFa: String
)

/**
 * V13.2 Section 5: Source-backed, time-bounded tariff schedule for a charging station or port.
 *
 * Non-negotiable rules:
 * 1. Must have a non-blank [sourceName] and [sourceReference].
 * 2. Must have a valid [effectiveFromEpochMs].
 * 3. Must have either a valid [effectiveUntilEpochMs] OR an explicitly documented [isOpenEndedRegulatoryTariff]
 *    with [openEndedDocumentationNote].
 * 4. Must have an explicit [currency] ([TariffCurrency.IRR_RIAL] or [TariffCurrency.IRT_TOMAN]).
 * 5. Expired or stale tariffs transition to `STALE` / `EXPIRED` and are NEVER used for deterministic cost calculation.
 */
data class ChargingTariffSchedule(
    val tariffId: String,
    val pricePerKwh: Double?,
    val currency: TariffCurrency = TariffCurrency.UNKNOWN,
    val sourceName: String? = null,
    val sourceReference: String? = null,
    val effectiveFromEpochMs: Long? = null,
    val effectiveUntilEpochMs: Long? = null,
    val isOpenEndedRegulatoryTariff: Boolean = false,
    val openEndedDocumentationNote: String? = null,
    val verificationState: FieldVerificationState = FieldVerificationState.UNKNOWN
) {
    val hasValidSourceAndCurrency: Boolean
        get() = !sourceName.isNullOrBlank() &&
            !sourceReference.isNullOrBlank() &&
            currency != TariffCurrency.UNKNOWN &&
            pricePerKwh != null &&
            pricePerKwh.isFinite() &&
            pricePerKwh > 0.0 &&
            effectiveFromEpochMs != null &&
            effectiveFromEpochMs > 0L &&
            (effectiveUntilEpochMs != null && effectiveUntilEpochMs > effectiveFromEpochMs ||
                (isOpenEndedRegulatoryTariff && !openEndedDocumentationNote.isNullOrBlank()))

    fun evaluateFreshness(
        nowEpochMs: Long = System.currentTimeMillis(),
        maxOpenEndedAgeMs: Long = EvDomainPolicyConfig.TARIFF_SCHEDULE_FRESHNESS_TTL_MS
    ): FieldFreshnessState {
        if (!hasValidSourceAndCurrency) return FieldFreshnessState.UNKNOWN
        val start = effectiveFromEpochMs ?: return FieldFreshnessState.UNKNOWN
        if (nowEpochMs + EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS < start) {
            return FieldFreshnessState.UNKNOWN
        }
        if (effectiveUntilEpochMs != null && nowEpochMs > effectiveUntilEpochMs) {
            return FieldFreshnessState.STALE
        }
        if (nowEpochMs - start > maxOpenEndedAgeMs) {
            return FieldFreshnessState.STALE
        }
        return FieldFreshnessState.FRESH
    }

    fun canUseForDeterministicCost(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return hasValidSourceAndCurrency &&
            verificationState == FieldVerificationState.VERIFIED &&
            evaluateFreshness(nowEpochMs) == FieldFreshnessState.FRESH
    }

    fun computeSessionCost(
        energyKwh: Double,
        targetCurrency: TariffCurrency = currency,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ChargingCostEvaluation {
        if (!energyKwh.isFinite() || energyKwh <= 0.0) {
            return ChargingCostEvaluation(
                amount = null,
                currency = targetCurrency,
                isDeterministicVerified = false,
                freshnessState = FieldFreshnessState.UNKNOWN,
                verificationState = FieldVerificationState.UNKNOWN,
                statusCode = "INVALID_ENERGY_AMOUNT",
                uiLabelFa = "انرژی مصرفی نامعتبر است"
            )
        }
        if (sourceName.isNullOrBlank() || sourceReference.isNullOrBlank()) {
            return ChargingCostEvaluation(
                amount = null,
                currency = TariffCurrency.UNKNOWN,
                isDeterministicVerified = false,
                freshnessState = FieldFreshnessState.UNKNOWN,
                verificationState = FieldVerificationState.UNVERIFIED,
                statusCode = "MISSING_TARIFF_SOURCE",
                uiLabelFa = "تعرفه فاقد منبع معتبر است (محاسبه قطعی مجاز نیست)"
            )
        }
        if (currency == TariffCurrency.UNKNOWN || targetCurrency == TariffCurrency.UNKNOWN) {
            return ChargingCostEvaluation(
                amount = null,
                currency = TariffCurrency.UNKNOWN,
                isDeterministicVerified = false,
                freshnessState = FieldFreshnessState.UNKNOWN,
                verificationState = FieldVerificationState.UNKNOWN,
                statusCode = "UNKNOWN_CURRENCY",
                uiLabelFa = "واحد پول تعرفه (ریال/تومان) مشخص نیست"
            )
        }
        if (pricePerKwh == null || !pricePerKwh.isFinite() || pricePerKwh <= 0.0) {
            return ChargingCostEvaluation(
                amount = null,
                currency = targetCurrency,
                isDeterministicVerified = false,
                freshnessState = FieldFreshnessState.UNKNOWN,
                verificationState = FieldVerificationState.UNKNOWN,
                statusCode = "INVALID_PRICE",
                uiLabelFa = "نرخ تعرفه نامعتبر است"
            )
        }

        val freshness = evaluateFreshness(nowEpochMs)
        val convertedRate = CentralCurrencyConverter.convert(pricePerKwh, currency, targetCurrency)
            ?: return ChargingCostEvaluation(
                amount = null,
                currency = TariffCurrency.UNKNOWN,
                isDeterministicVerified = false,
                freshnessState = freshness,
                verificationState = verificationState,
                statusCode = "CURRENCY_CONVERSION_FAILED",
                uiLabelFa = "خطا در تبدیل واحد پول"
            )

        val rawCost = energyKwh * convertedRate
        if (freshness != FieldFreshnessState.FRESH) {
            return ChargingCostEvaluation(
                amount = rawCost,
                currency = targetCurrency,
                isDeterministicVerified = false,
                freshnessState = freshness,
                verificationState = FieldVerificationState.ESTIMATED,
                statusCode = "STALE_OR_EXPIRED_TARIFF",
                uiLabelFa = "هزینه تخمینی بر اساس تعرفه قدیمی/منقضی (%,d ${targetCurrency.displayUnitFa})".format(rawCost.roundToLong())
            )
        }

        val isVerifiedCurrent = verificationState == FieldVerificationState.VERIFIED
        return ChargingCostEvaluation(
            amount = rawCost,
            currency = targetCurrency,
            isDeterministicVerified = isVerifiedCurrent,
            freshnessState = FieldFreshnessState.FRESH,
            verificationState = if (isVerifiedCurrent) FieldVerificationState.VERIFIED else FieldVerificationState.ESTIMATED,
            statusCode = if (isVerifiedCurrent) "VERIFIED_CURRENT_TARIFF" else "ESTIMATED_TARIFF",
            uiLabelFa = if (isVerifiedCurrent) {
                "هزینه شارژ تأییدشده: %,d ${targetCurrency.displayUnitFa}".format(rawCost.roundToLong())
            } else {
                "هزینه شارژ تخمینی: %,d ${targetCurrency.displayUnitFa} (نیازمند تأیید)".format(rawCost.roundToLong())
            }
        )
    }
}

/**
 * V13.2 Section 3: Explicit station operational status, strictly separated from data verification status.
 * Verifying that a station physically exists (`VERIFIED`) does NOT mean the station is currently active (`OPERATIONAL`).
 */
enum class StationOperationalStatus {
    OPERATIONAL,
    DEGRADED,
    OUT_OF_SERVICE,
    UNKNOWN
}

/**
 * V13.2 Section 3: Explicit charger port occupancy/availability status, strictly separated from static port verification.
 */
enum class PortOccupancyStatus {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_ORDER,
    UNKNOWN
}

/**
 * V13.2 Section 3 & Section 6: Per-station independent verification, freshness, and operational status record.
 *
 * Enforces all mandatory V13.2 Section 3 & Section 6 rules:
 * 1. No global verification timestamp: each station carries its own `verifiedAt`, `evidenceSource`,
 *    `evidenceReference`, `verificationMethod`, `verificationStatus`, `expiresAt`, and `freshnessState`.
 * 2. Dataset publication time (`datasetPublishedAt`) and data retrieval time (`retrievedAt`) are NEVER
 *    accepted as physical verification time (`verifiedAt`).
 * 3. Records without a valid `verifiedAt` or without an independent `evidenceSource` / `evidenceReference`
 *    can NEVER be `VERIFIED`.
 * 4. An unchecked external URL (`LINK_UNCHECKED`) or internal URN (`INTERNAL_URN_REJECTED`) alone NEVER
 *    promotes a station to `VERIFIED`.
 * 5. Expired/old verification records transition to `STALE` and are excluded from `VERIFIED` recommendations.
 * 6. `operationalStatus` and `portOccupancyStatus` are managed independently from `verificationStatus`.
 */
data class StationIndependentVerificationRecord(
    val stationId: String,
    val verifiedAt: Long?,
    val retrievedAt: Long,
    val datasetPublishedAt: Long? = null,
    val evidenceSource: String?,
    val evidenceReference: String?,
    val externalUrl: String? = null,
    val urlVerificationStatus: UrlVerificationStatus = when {
        externalUrl.isNullOrBlank() -> UrlVerificationStatus.MISSING
        externalUrl.startsWith("urn:") -> UrlVerificationStatus.INTERNAL_URN_REJECTED
        else -> UrlVerificationStatus.LINK_UNCHECKED
    },
    val verificationMethod: VerificationMethod = VerificationMethod.UNVERIFIED_CORRIDOR_SEED,
    val rawVerificationStatus: String = "UNVERIFIED",
    val expiresAt: Long? = verifiedAt?.takeIf { it > 0L }?.plus(EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS),
    val operationalStatus: StationOperationalStatus = StationOperationalStatus.UNKNOWN,
    val coordinatesProvenance: FieldProvenanceRecord<Pair<Double, Double>>? = null
) {
    /**
     * Evaluates whether `verifiedAt` is distinct from `retrievedAt` and `datasetPublishedAt` and within TTL.
     */
    fun evaluateFreshnessState(
        nowEpochMs: Long = System.currentTimeMillis(),
        ttlMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): FieldFreshnessState {
        val vAt = verifiedAt ?: return FieldFreshnessState.UNKNOWN
        if (vAt <= 0L) return FieldFreshnessState.UNKNOWN
        // Rule: Retrieval time or dataset release time must not be conflated with physical verification time
        if (vAt == retrievedAt || (datasetPublishedAt != null && vAt == datasetPublishedAt)) {
            return FieldFreshnessState.UNKNOWN
        }
        if (vAt > nowEpochMs + EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS) {
            return FieldFreshnessState.UNKNOWN
        }
        if (expiresAt != null && nowEpochMs > expiresAt) {
            return FieldFreshnessState.STALE
        }
        if (nowEpochMs - vAt > ttlMs) {
            return FieldFreshnessState.STALE
        }
        return FieldFreshnessState.FRESH
    }

    /**
     * Resolves the effective verification state after checking source, timestamp independence, URL status, and freshness.
     */
    fun resolveEffectiveVerificationState(
        nowEpochMs: Long = System.currentTimeMillis(),
        ttlMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): FieldVerificationState {
        if (evidenceSource.isNullOrBlank() || evidenceReference.isNullOrBlank()) {
            return FieldVerificationState.UNVERIFIED
        }
        if (urlVerificationStatus == UrlVerificationStatus.INTERNAL_URN_REJECTED) {
            return FieldVerificationState.UNVERIFIED
        }
        val freshness = evaluateFreshnessState(nowEpochMs, ttlMs)
        if (freshness == FieldFreshnessState.UNKNOWN) {
            return FieldVerificationState.UNVERIFIED
        }
        if (freshness == FieldFreshnessState.STALE || freshness == FieldFreshnessState.EXPIRED) {
            return FieldVerificationState.PARTIALLY_VERIFIED
        }

        // Section 6: Mere existence of an unchecked URL (LINK_UNCHECKED) does not prove VERIFIED
        // unless backed by physical FIELD_INSPECTION, live OPERATOR_FEED_OCPP, or HTTP_CONTENT_VERIFIED.
        val hasStrongVerificationMethod = verificationMethod == VerificationMethod.FIELD_INSPECTION ||
            verificationMethod == VerificationMethod.OPERATOR_FEED_OCPP ||
            urlVerificationStatus == UrlVerificationStatus.HTTP_CONTENT_VERIFIED

        return if (rawVerificationStatus.equals("VERIFIED", ignoreCase = true) && hasStrongVerificationMethod) {
            FieldVerificationState.VERIFIED
        } else if (rawVerificationStatus.equals("VERIFIED", ignoreCase = true) ||
            rawVerificationStatus.equals("PARTIALLY_VERIFIED", ignoreCase = true)
        ) {
            FieldVerificationState.PARTIALLY_VERIFIED
        } else {
            FieldVerificationState.UNVERIFIED
        }
    }

    /**
     * True ONLY if the station has fresh, independently verified evidence.
     * Note: Even when true, [operationalStatus] may still be [StationOperationalStatus.UNKNOWN]!
     */
    fun isEligibleForFreshVerifiedRecommendation(
        nowEpochMs: Long = System.currentTimeMillis(),
        ttlMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): Boolean {
        return evaluateFreshnessState(nowEpochMs, ttlMs) == FieldFreshnessState.FRESH &&
            resolveEffectiveVerificationState(nowEpochMs, ttlMs) == FieldVerificationState.VERIFIED &&
            operationalStatus != StationOperationalStatus.OUT_OF_SERVICE
    }
}

/**
 * V13.2 Section 4: Independent port-level power, connector, and tariff provenance model.
 *
 * Non-negotiable rules:
 * 1. AC power is NEVER inferred from DC power, and DC power is NEVER inferred from AC power.
 * 2. Vehicle max power is strictly separate from port nominal power.
 * 3. Nominal nameplate power (`nominalPowerKw`) is strictly separate from live deliverable power (`realTimeDeliverablePowerKw`).
 * 4. If `nominalPowerKw` is missing, `<= 0`, `NaN`, or lacks a valid source/timestamp, its state is `UNKNOWN`
 *    and no default (`60.0` or `22.0`) is substituted.
 * 5. Connector type is NEVER guessed from station operator/brand name.
 */
data class PortIndependentSpecRecord(
    val portId: String,
    val stationId: String,
    val isAc: Boolean,
    val isDc: Boolean,
    val rawConnectorType: String?,
    val nominalPowerKw: Double?,
    val realTimeDeliverablePowerKw: Double? = null,
    val powerEvidenceSource: String? = null,
    val powerEvidenceReference: String? = null,
    val powerVerifiedAtEpochMs: Long? = null,
    val powerVerificationState: FieldVerificationState = FieldVerificationState.UNKNOWN,
    val connectorEvidenceSource: String? = null,
    val connectorVerifiedAtEpochMs: Long? = null,
    val occupancyStatus: PortOccupancyStatus = PortOccupancyStatus.UNKNOWN,
    val occupancyObservedAtEpochMs: Long? = null,
    val tariffSchedule: ChargingTariffSchedule? = null
) {
    val hasValidElectricalMode: Boolean
        get() = isAc xor isDc

    val canonicalConnector: CanonicalConnectorType
        get() = CanonicalConnectorType.fromRaw(rawConnectorType)

    val isConnectorKnown: Boolean
        get() = canonicalConnector != CanonicalConnectorType.UNKNOWN &&
            !connectorEvidenceSource.isNullOrBlank() &&
            ((isDc && canonicalConnector.isDcFast) || (isAc && !canonicalConnector.isDcFast))

    val effectiveNominalPowerKw: Double?
        get() {
            if (!hasValidElectricalMode) return null
            val p = nominalPowerKw ?: return null
            if (!p.isFinite() || p <= 0.0 || p > 1000.0) return null
            if (powerEvidenceSource.isNullOrBlank()) return null
            return p
        }

    val effectivePowerVerificationState: FieldVerificationState
        get() {
            val p = effectiveNominalPowerKw ?: return FieldVerificationState.UNKNOWN
            if (p <= 0.0 || powerVerifiedAtEpochMs == null || powerVerifiedAtEpochMs <= 0L) {
                return FieldVerificationState.ESTIMATED
            }
            return powerVerificationState
        }

    fun evaluatePowerFreshness(
        nowEpochMs: Long = System.currentTimeMillis(),
        ttlMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): FieldFreshnessState {
        if (effectiveNominalPowerKw == null) return FieldFreshnessState.UNKNOWN
        val vAt = powerVerifiedAtEpochMs ?: return FieldFreshnessState.UNKNOWN
        if (vAt <= 0L || vAt > nowEpochMs + EvDomainPolicyConfig.MAX_CLOCK_SKEW_TOLERANCE_MS) {
            return FieldFreshnessState.UNKNOWN
        }
        return if (nowEpochMs - vAt > ttlMs) FieldFreshnessState.STALE else FieldFreshnessState.FRESH
    }
}
