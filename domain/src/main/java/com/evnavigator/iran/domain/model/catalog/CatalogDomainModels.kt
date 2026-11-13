package com.evnavigator.iran.domain.model.catalog

import com.evnavigator.iran.domain.intelligence.context.FieldFreshnessState
import com.evnavigator.iran.domain.intelligence.context.FieldVerificationState
import com.evnavigator.iran.domain.intelligence.context.UrlVerificationStatus
import com.evnavigator.iran.domain.intelligence.energy.VehiclePhysicsParams
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.domain.policy.FreshnessEvaluationResult
import com.evnavigator.iran.domain.policy.FreshnessPolicyEvaluator

/**
 * Pure Domain representations of Vehicle, ChargingStation, ChargingPort, ActiveVehicleState,
 * ActiveJourney, and TripHistorySummary.
 *
 * V13.2 Invariants:
 * 1. [SourceEvidence] distinguishes internal registry URNs (`urn:iran-ev-registry:...`) from
 *    real traceable external evidence (`externalRecordId`, `sourceUrl`, `lastVerifiedAtEpochMs`),
 *    and explicitly tracks [urlVerificationStatus] (`HTTP_CONTENT_VERIFIED` vs `LINK_UNCHECKED`).
 * 2. [DomainVehicle.toPhysicsParamsOrNull] never substitutes `?: 60.0` DC or `?: 11.0` AC fallbacks;
 *    `dcMaxChargePowerKw` and `acMaxChargePowerKw` remain independent and `null` when unknown.
 * 3. [DomainChargingStation] and [DomainChargingPort] evaluate staleness via [FreshnessPolicyEvaluator]
 *    and [DomainClock], never conflating `retrievedDate` with `verifiedAt` or `liveStatusUpdatedAtEpochMs`.
 * 4. [DomainChargingStation.operationalStatus] is strictly separated from [DomainChargingStation.validationStatus].
 */

enum class DomainConnectorType {
    TYPE_2,
    CCS_COMBO_1,
    CCS_COMBO_2,
    CHAdeMO,
    GB_T_AC,
    GB_T_DC,
    TESLA_NACS,
    TESLA_SUPERCHARGER,
    OTHER,
    UNKNOWN
}

/**
 * Canonical normalized charging connector representation across all catalog, routing,
 * and trip-intelligence modules.
 *
 * Eliminates string mismatch bugs between `"GB/T DC"`, `"GB_T_DC"`, `"GBT_DC"`, `"CCS2"`,
 * `"CCS_COMBO_2"`, `"Type 2"`, and `"TYPE_2"`.
 */
enum class CanonicalConnectorType(
    val displayName: String,
    val isDcFast: Boolean
) {
    CCS2(displayName = "CCS2", isDcFast = true),
    GBT_DC(displayName = "GB/T DC", isDcFast = true),
    CHADEMO(displayName = "CHAdeMO", isDcFast = true),
    TESLA_NACS(displayName = "NACS / Tesla", isDcFast = true),
    CCS1(displayName = "CCS1", isDcFast = true),
    TYPE_2_AC(displayName = "Type 2 AC", isDcFast = false),
    GBT_AC(displayName = "GB/T AC", isDcFast = false),
    UNKNOWN(displayName = "نامشخص", isDcFast = false);

    companion object {
        private val AMBIGUOUS_CONNECTOR_TOKENS = setOf("CCS", "GB_T", "GBT", "COMBO", "AC_DC", "UNKNOWN")

        /**
         * Returns true if the raw connector token is ambiguous (e.g., bare "CCS" without 1/2,
         * or bare "GB/T" / "GBT" without AC/DC).
         */
        fun isAmbiguousRaw(raw: String?): Boolean {
            if (raw.isNullOrBlank()) return false
            val u = raw.trim().uppercase()
                .replace("-", "_")
                .replace("/", "_")
                .replace(" ", "_")
            return u in AMBIGUOUS_CONNECTOR_TOKENS
        }

        fun fromDomainType(type: DomainConnectorType): CanonicalConnectorType = when (type) {
            DomainConnectorType.CCS_COMBO_2 -> CCS2
            DomainConnectorType.GB_T_DC -> GBT_DC
            DomainConnectorType.CHAdeMO -> CHADEMO
            DomainConnectorType.TESLA_NACS, DomainConnectorType.TESLA_SUPERCHARGER -> TESLA_NACS
            DomainConnectorType.CCS_COMBO_1 -> CCS1
            DomainConnectorType.TYPE_2 -> TYPE_2_AC
            DomainConnectorType.GB_T_AC -> GBT_AC
            DomainConnectorType.OTHER, DomainConnectorType.UNKNOWN -> UNKNOWN
        }

        fun fromRaw(raw: String?): CanonicalConnectorType {
            if (raw.isNullOrBlank()) return UNKNOWN
            val u = raw.trim().uppercase()
                .replace("-", "_")
                .replace("/", "_")
                .replace(" ", "_")
            if (u in AMBIGUOUS_CONNECTOR_TOKENS) return UNKNOWN
            return when {
                u.contains("CCS_COMBO_2") || u.contains("CCS2") || u.contains("CCS_2") || u.contains("COMBO_2") || u.contains("COMBO2") -> CCS2
                u.contains("CCS_COMBO_1") || u.contains("CCS1") || u.contains("CCS_1") || u.contains("COMBO_1") || u.contains("COMBO1") -> CCS1
                (u.contains("GB_T") || u.contains("GBT")) && u.contains("AC") -> GBT_AC
                (u.contains("GB_T") || u.contains("GBT")) && (u.contains("DC") || u.contains("FAST") || u.contains("20234_3")) -> GBT_DC
                u.contains("CHADEMO") -> CHADEMO
                u.contains("NACS") || u.contains("TESLA") || u.contains("J3400") -> TESLA_NACS
                u.contains("TYPE_2") || u.contains("TYPE2") || u.contains("MENNEKES") || u.contains("62196_T2") -> TYPE_2_AC
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Type-safe normalizer for vehicle and station connector lists (V13.3 Sections 1 & 9).
 * Prevents calling String.split(",") on List<String> and rejects ambiguous tokens ("CCS", "GB/T").
 */
object ConnectorNormalizer {

    fun normalizeConnectorStrings(connectors: List<String>): List<String> {
        if (connectors.isEmpty()) return emptyList()
        return connectors
            .flatMap { raw ->
                if (raw.contains(",")) raw.split(",") else listOf(raw)
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun normalizeFromCsvBoundary(rawCsv: String?): List<String> {
        if (rawCsv.isNullOrBlank()) return emptyList()
        return rawCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun toCanonicalSet(connectors: List<String>): Set<CanonicalConnectorType> {
        return normalizeConnectorStrings(connectors)
            .map { CanonicalConnectorType.fromRaw(it) }
            .filter { it != CanonicalConnectorType.UNKNOWN }
            .toCollection(LinkedHashSet())
    }

    fun findAmbiguousOrUnknownConnectors(connectors: List<String>): List<String> {
        return normalizeConnectorStrings(connectors)
            .filter { CanonicalConnectorType.fromRaw(it) == CanonicalConnectorType.UNKNOWN }
    }
}

enum class DomainAccessType {
    PUBLIC,
    PRIVATE,
    RESTRICTED,
    SEMI_PUBLIC,
    UNKNOWN
}

enum class DomainSourceType {
    NETWORK_PROVIDER,
    GOVERNMENT,
    MANUFACTURER,
    CROWDSOURCED,
    USER_CONTRIBUTION,
    FIELD_SURVEY,
    UNKNOWN
}

enum class VerificationMethod {
    FIELD_INSPECTION,
    OPERATOR_FEED_OCPP,
    DOCUMENTED_PUBLIC_RELEASE,
    MUNICIPAL_SURVEY,
    UNVERIFIED_CORRIDOR_SEED
}

enum class SpecProvenance {
    VERIFIED_NAMEPLATE_OR_FEED,
    OPERATOR_PUBLISHED_SPEC,
    ESTIMATED_CORRIDOR_TEMPLATE,
    UNKNOWN
}

/**
 * Explicit provenance and verification evidence for a charging station or port record (V13.2 Sections 3 & 12).
 *
 * Invariant: Internal registry URNs (`urn:iran-ev-registry:...`) are stored in [internalRegistryUrn]
 * and NEVER treated as external verification evidence. [hasTraceableExternalEvidence] requires
 * a non-blank [externalRecordId], an external [sourceUrl] (not an internal URN), and a positive
 * [lastVerifiedAtEpochMs].
 */
data class SourceEvidence(
    val sourceName: String,
    val sourceType: DomainSourceType,
    val externalRecordId: String? = null,
    val sourceUrl: String? = null,
    val internalRegistryUrn: String? = null,
    val retrievedAtEpochMs: Long,
    val lastVerifiedAtEpochMs: Long? = null,
    val catalogGeneratedAtEpochMs: Long = retrievedAtEpochMs,
    val verificationMethod: VerificationMethod = VerificationMethod.UNVERIFIED_CORRIDOR_SEED,
    val verificationStatus: String = "UNVERIFIED",
    val portPowerProvenance: SpecProvenance = SpecProvenance.UNKNOWN,
    val connectorProvenance: SpecProvenance = SpecProvenance.UNKNOWN,
    val evidenceNotes: String? = null,
    val sourceChecksumOrVersion: String? = null,
    val urlVerificationStatus: UrlVerificationStatus = when {
        sourceUrl.isNullOrBlank() -> UrlVerificationStatus.MISSING
        sourceUrl.startsWith("urn:") -> UrlVerificationStatus.INTERNAL_URN_REJECTED
        else -> UrlVerificationStatus.LINK_UNCHECKED
    }
) {
    val hasTraceableExternalEvidence: Boolean
        get() = !externalRecordId.isNullOrBlank() &&
            !sourceUrl.isNullOrBlank() &&
            !sourceUrl.startsWith("urn:iran-ev-registry:") &&
            (sourceUrl.startsWith("https://") || sourceUrl.startsWith("http://")) &&
            lastVerifiedAtEpochMs != null &&
            lastVerifiedAtEpochMs > 0L

    val hasIndependentlyInspectedOrContentVerifiedEvidence: Boolean
        get() = hasTraceableExternalEvidence &&
            lastVerifiedAtEpochMs != retrievedAtEpochMs &&
            (urlVerificationStatus == UrlVerificationStatus.HTTP_CONTENT_VERIFIED ||
                verificationMethod == VerificationMethod.FIELD_INSPECTION ||
                verificationMethod == VerificationMethod.OPERATOR_FEED_OCPP)
}

enum class DomainVehicleSocSource {
    USER_MANUAL_INPUT,
    USER_MANUAL,
    LIVE_VEHICLE_TELEMETRY,
    OBD_II_LIVE,
    VEHICLE_PROFILE,
    ENERGY_MODEL_ESTIMATE,
    ESTIMATED_DURING_NAVIGATION,
    DEFAULT_FALLBACK,
    UNCONFIRMED_DEFAULT
}

data class DomainVehicle(
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val generation: String? = null,
    val modelYear: Int,
    val trim: String? = null,
    val market: String = "GLOBAL",
    val powertrainType: String = "BEV",
    val curbMassKg: Double,
    val grossVehicleMassKg: Double? = null,
    val lengthMm: Double? = null,
    val widthMm: Double? = null,
    val heightMm: Double? = null,
    val wheelbaseMm: Double? = null,
    val groundClearanceMm: Double? = null,
    val dragCoefficient: Double? = null,
    val frontalAreaM2: Double? = null,
    val rollingResistanceCoefficient: Double? = null,
    val nominalBatteryCapacityWh: Double,
    val usableBatteryCapacityWh: Double,
    val batteryChemistry: String? = null,
    val nominalVoltageV: Double? = null,
    val batteryConfiguration: String? = null,
    val motorCount: Int = 1,
    val frontMotorPowerKw: Double? = null,
    val rearMotorPowerKw: Double? = null,
    val peakMotorPowerKw: Double,
    val continuousMotorPowerKw: Double? = null,
    val torqueNm: Double? = null,
    val drivetrainType: String = "FWD",
    val baselineConsumptionWhPer100Km: Double,
    val acChargingMaxPowerKw: Double? = null,
    val dcChargingMaxPowerKw: Double? = null,
    val chargingVoltageRangeV: String? = null,
    val chargingCurrentRangeA: String? = null,
    val chargePortCount: Int = 1,
    val connectorTypes: List<String> = emptyList(),
    val wheelDiameterMm: Double? = null,
    val tireWidthMm: Double? = null,
    val dataQuality: String = "VERIFIED",
    val source: String = "MANUFACTURER_SPEC",
    val sourceVersion: String? = null,
    val retrievedDate: Long = 0L,
    val confidence: Double = 0.8,
    val validationStatus: String = "VALIDATED",
    val notes: String? = null,
    val schemaVersion: Int = 1,
    val dataVersion: Int = 1,
    val vehicleRecordVersion: String = "1.0",
    val consumptionStandard: String = "WLTP",
    val consumptionReferenceSpeedKmh: Double? = null,
    val sourceUrl: String? = null,
    val verifiedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val usableBatteryKwh: Double get() = usableBatteryCapacityWh / 1000.0
    val nominalBatteryKwh: Double get() = nominalBatteryCapacityWh / 1000.0
    val grossBatteryKwh: Double get() = nominalBatteryCapacityWh / 1000.0
    val dcMaxChargePowerKw: Double? get() = dcChargingMaxPowerKw?.takeIf { it.isFinite() && it > 0.0 }
    val acMaxChargePowerKw: Double? get() = acChargingMaxPowerKw?.takeIf { it.isFinite() && it > 0.0 }
    val consumptionWhPerKm: Double get() = baselineConsumptionWhPer100Km / 100.0
    val isVerified: Boolean get() = validationStatus == "VERIFIED" || dataQuality == "VERIFIED"

    fun evaluateQuality(clock: DomainClock = DomainClock.SYSTEM): VehicleQualityAssessment =
        VehicleDataQualityEvaluator.evaluate(this, clock)

    /**
     * Converts this [DomainVehicle] into physical model parameters, or returns `null` if any
     * mandatory aerodynamic/rolling/mass/battery specification is missing or invalid.
     *
     * V12.6 P1-6 Invariant: Never invents `60.0` kW DC or `11.0` kW AC fallbacks!
     * `dcMaxChargePowerKw` and `acMaxChargePowerKw` are preserved independently (`null` if unknown).
     */
    fun toPhysicsParamsOrNull(assumedPayloadKg: Double = 100.0): VehiclePhysicsParams? {
        val cd = dragCoefficient?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val area = frontalAreaM2?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val crr = rollingResistanceCoefficient?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (!usableBatteryCapacityWh.isFinite() || usableBatteryCapacityWh <= 0.0) return null
        if (!curbMassKg.isFinite() || curbMassKg <= 0.0) return null
        val dcKw = dcMaxChargePowerKw
        val acKw = acMaxChargePowerKw
        val primaryMaxChargeKw = dcKw ?: acKw ?: 0.0
        return VehiclePhysicsParams(
            vehicleId = id,
            usableBatteryKwh = usableBatteryCapacityWh / 1000.0,
            nominalGrossBatteryKwh = nominalBatteryCapacityWh.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0),
            totalMassKg = curbMassKg + assumedPayloadKg,
            dragCoefficient = cd,
            frontalAreaM2 = area,
            rollingResistanceCoeff = crr,
            maxChargePowerKw = primaryMaxChargeKw,
            dcMaxChargePowerKw = dcKw,
            acMaxChargePowerKw = acKw,
            supportedConnectors = connectorTypes,
            isAssumedFallback = false,
            dataQuality = dataQuality,
            uncertaintyPercent = if (dataQuality == "VERIFIED") 5.0 else 12.0
        )
    }
}

data class DomainChargingStation(
    val id: String,
    val externalId: String? = null,
    val canonicalName: String,
    val operatorId: String? = null,
    val operatorName: String? = null,
    val networkId: String? = null,
    val networkName: String? = null,
    val accessType: DomainAccessType = DomainAccessType.PUBLIC,
    val accessRestrictions: Set<String>? = null,
    val is247: Boolean = false,
    val openingHoursJson: String? = null,
    val paymentMethods: Set<String>? = null,
    val amenities: Set<String>? = null,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val latitude: Double,
    val longitude: Double,
    val province: String? = null,
    val city: String? = null,
    val locality: String? = null,
    val roadContext: String? = null,
    val address: String? = null,
    val source: String = "MAPNA",
    val sourceType: DomainSourceType = DomainSourceType.NETWORK_PROVIDER,
    val sourceReference: String? = null,
    val version: String? = null,
    val retrievedDate: Long = 0L,
    val createdDate: Long? = null,
    val validatedDate: Long? = null,
    val confidence: Double = 0.5,
    val validationStatus: String = "UNVERIFIED",
    val isPrimarySource: Boolean = false,
    val datasetVersion: String? = null,
    val coverage: String? = null,
    val coverageType: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: Long? = null,
    val coverageLevel: String? = null,
    val lastAccessedAt: Long = 0L,
    val sourceEvidence: SourceEvidence? = null,
    val operationalStatus: StationOperationalStatus = StationOperationalStatus.UNKNOWN,
    val verificationRecord: StationIndependentVerificationRecord? = null,
    val tariffSchedule: ChargingTariffSchedule? = null
) {
    /**
     * Secondary constructor supporting test and domain fixtures.
     * Note: When `isVerified = true` and `verifiedAt` is not explicitly passed, `verifiedAt` is set to
     * `retrievedDate - 3_600_000L` (1 hour prior to retrieval) so physical verification time is distinct from retrieval time.
     */
    constructor(
        id: String,
        name: String,
        operatorName: String? = null,
        latitude: Double,
        longitude: Double,
        address: String? = null,
        city: String? = null,
        province: String? = null,
        is247: Boolean = false,
        isVerified: Boolean = false,
        validationStatus: String = if (isVerified) "VERIFIED" else "UNVERIFIED",
        confidence: Double = if (isVerified) 0.85 else 0.50,
        retrievedDate: Long = System.currentTimeMillis(),
        verifiedAt: Long? = if (isVerified) (retrievedDate - 3_600_000L).coerceAtLeast(1L) else null,
        externalId: String? = if (isVerified) "EXT_$id" else null,
        sourceUrl: String? = if (isVerified) "https://mapnagroup.com/stations/$id" else null,
        sourceEvidence: SourceEvidence? = null,
        operationalStatus: StationOperationalStatus = StationOperationalStatus.UNKNOWN
    ) : this(
        id = id,
        externalId = externalId,
        canonicalName = name,
        operatorName = operatorName,
        latitude = latitude,
        longitude = longitude,
        address = address,
        city = city,
        province = province,
        is247 = is247,
        validationStatus = validationStatus,
        confidence = confidence,
        retrievedDate = retrievedDate,
        verifiedAt = verifiedAt,
        sourceUrl = sourceUrl,
        sourceEvidence = sourceEvidence,
        operationalStatus = operationalStatus
    )

    val name: String get() = canonicalName
    val updatedAt: Long get() = retrievedDate
    val isVerified: Boolean get() = validationStatus == "VERIFIED"

    /**
     * True ONLY when this station has traceable external verification evidence (not an internal URN)
     * AND a positive verification timestamp.
     */
    val hasTraceableExternalEvidence: Boolean
        get() = sourceEvidence?.hasTraceableExternalEvidence == true ||
            (
                !externalId.isNullOrBlank() &&
                    !sourceUrl.isNullOrBlank() &&
                    !sourceUrl.startsWith("urn:iran-ev-registry:") &&
                    (sourceUrl.startsWith("https://") || sourceUrl.startsWith("http://")) &&
                    (verifiedAt != null && verifiedAt > 0L)
                )

    fun toIndependentVerificationRecord(): StationIndependentVerificationRecord {
        verificationRecord?.let { return it }
        val effectiveVerifiedAt = sourceEvidence?.lastVerifiedAtEpochMs ?: verifiedAt ?: validatedDate
        val effectiveUrl = sourceEvidence?.sourceUrl ?: sourceUrl
        val urlStatus = sourceEvidence?.urlVerificationStatus ?: when {
            effectiveUrl.isNullOrBlank() -> UrlVerificationStatus.MISSING
            effectiveUrl.startsWith("urn:") -> UrlVerificationStatus.INTERNAL_URN_REJECTED
            else -> UrlVerificationStatus.LINK_UNCHECKED
        }
        return StationIndependentVerificationRecord(
            stationId = id,
            verifiedAt = effectiveVerifiedAt,
            retrievedAt = retrievedDate,
            datasetPublishedAt = sourceEvidence?.catalogGeneratedAtEpochMs,
            evidenceSource = sourceEvidence?.sourceName?.takeIf { it.isNotBlank() } ?: source.takeIf { it.isNotBlank() },
            evidenceReference = sourceEvidence?.externalRecordId?.takeIf { it.isNotBlank() }
                ?: externalId?.takeIf { it.isNotBlank() }
                ?: sourceReference?.takeIf { it.isNotBlank() },
            externalUrl = effectiveUrl,
            urlVerificationStatus = urlStatus,
            verificationMethod = sourceEvidence?.verificationMethod ?: when (validationStatus.uppercase()) {
                "VERIFIED" -> VerificationMethod.DOCUMENTED_PUBLIC_RELEASE
                "PARTIALLY_VERIFIED" -> VerificationMethod.MUNICIPAL_SURVEY
                else -> VerificationMethod.UNVERIFIED_CORRIDOR_SEED
            },
            rawVerificationStatus = validationStatus,
            operationalStatus = operationalStatus
        )
    }

    fun evaluateFreshness(
        clock: DomainClock = DomainClock.SYSTEM,
        maxAgeMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): FreshnessEvaluationResult = FreshnessPolicyEvaluator.evaluateStationVerificationFreshness(
        verifiedAtEpochMs = sourceEvidence?.lastVerifiedAtEpochMs ?: verifiedAt ?: validatedDate,
        retrievedAtEpochMs = retrievedDate.takeIf { it > 0L },
        clock = clock,
        ttlMs = maxAgeMs
    )

    fun isStale(
        maxAgeMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean = evaluateFreshness(
        clock = DomainClock.fixed(nowEpochMs),
        maxAgeMs = maxAgeMs
    ).isStaleOrInvalid

    fun isEligibleForFreshVerifiedRecommendation(
        clock: DomainClock = DomainClock.SYSTEM,
        maxAgeMs: Long = EvDomainPolicyConfig.STATION_STATIC_SPEC_TTL_MS
    ): Boolean {
        val freshness = evaluateFreshness(clock, maxAgeMs)
        if (!freshness.isFresh) return false
        if (!isVerified || !hasTraceableExternalEvidence) return false
        if (operationalStatus == StationOperationalStatus.OUT_OF_SERVICE) return false
        return true
    }
}

data class DomainChargingPort(
    val id: String,
    val stationId: String,
    val connectorType: DomainConnectorType,
    val connectorCount: Int = 1,
    val voltage: Double? = null,
    val current: Double? = null,
    val powerKw: Double? = null,
    val isAc: Boolean,
    val isDc: Boolean,
    val phaseCount: Int? = null,
    val protocol: String? = null,
    val status: String = "UNKNOWN",
    val pricePerKwh: Double? = null,
    val source: String? = null,
    val sourceReference: String? = null,
    val retrievedDate: Long? = null,
    val liveStatusUpdatedAtEpochMs: Long? = null,
    val confidence: Double = 0.5,
    val validationStatus: String = "UNVERIFIED",
    val datasetVersion: String? = null,
    val schemaVersion: String? = null,
    val applicationVersion: String? = null,
    val sourceEvidence: SourceEvidence? = null,
    val rawConnectorName: String = connectorType.name,
    val occupancyStatus: PortOccupancyStatus = when (status.trim().uppercase()) {
        "AVAILABLE" -> PortOccupancyStatus.AVAILABLE
        "OCCUPIED", "BUSY", "IN_USE" -> PortOccupancyStatus.OCCUPIED
        "OUT_OF_SERVICE", "OUT_OF_ORDER", "FAULTED", "UNAVAILABLE", "OFFLINE" -> PortOccupancyStatus.OUT_OF_ORDER
        else -> PortOccupancyStatus.UNKNOWN
    },
    val independentSpecRecord: PortIndependentSpecRecord? = null,
    val tariffSchedule: ChargingTariffSchedule? = null
) {
    /**
     * Secondary constructor accepting `connectorType: String` for domain/test callers.
     */
    constructor(
        id: String,
        stationId: String,
        connectorType: String,
        powerKw: Double?,
        isDc: Boolean,
        status: String = "UNKNOWN",
        connectorCount: Int = 1,
        retrievedDate: Long? = System.currentTimeMillis(),
        liveStatusUpdatedAtEpochMs: Long? = if (status.equals("AVAILABLE", ignoreCase = true)) retrievedDate else null
    ) : this(
        id = id,
        stationId = stationId,
        connectorType = when (CanonicalConnectorType.fromRaw(connectorType)) {
            CanonicalConnectorType.CCS2 -> DomainConnectorType.CCS_COMBO_2
            CanonicalConnectorType.CCS1 -> DomainConnectorType.CCS_COMBO_1
            CanonicalConnectorType.GBT_DC -> DomainConnectorType.GB_T_DC
            CanonicalConnectorType.GBT_AC -> DomainConnectorType.GB_T_AC
            CanonicalConnectorType.CHADEMO -> DomainConnectorType.CHAdeMO
            CanonicalConnectorType.TESLA_NACS -> DomainConnectorType.TESLA_NACS
            CanonicalConnectorType.TYPE_2_AC -> DomainConnectorType.TYPE_2
            CanonicalConnectorType.UNKNOWN -> DomainConnectorType.UNKNOWN
        },
        connectorCount = connectorCount,
        powerKw = powerKw,
        isAc = !isDc,
        isDc = isDc,
        status = status,
        retrievedDate = retrievedDate,
        liveStatusUpdatedAtEpochMs = liveStatusUpdatedAtEpochMs,
        rawConnectorName = connectorType
    )

    val canonicalConnector: CanonicalConnectorType
        get() = CanonicalConnectorType.fromDomainType(connectorType)

    fun toIndependentSpecRecord(): PortIndependentSpecRecord {
        independentSpecRecord?.let { return it }
        return PortIndependentSpecRecord(
            portId = id,
            stationId = stationId,
            isAc = isAc,
            isDc = isDc,
            rawConnectorType = rawConnectorName,
            nominalPowerKw = powerKw,
            powerEvidenceSource = sourceEvidence?.sourceName ?: source,
            powerEvidenceReference = sourceEvidence?.externalRecordId ?: sourceReference,
            powerVerifiedAtEpochMs = sourceEvidence?.lastVerifiedAtEpochMs,
            powerVerificationState = when (validationStatus.uppercase()) {
                "VERIFIED" -> FieldVerificationState.VERIFIED
                "PARTIALLY_VERIFIED" -> FieldVerificationState.PARTIALLY_VERIFIED
                "ASSUMED", "ESTIMATED" -> FieldVerificationState.ESTIMATED
                "UNVERIFIED" -> FieldVerificationState.UNVERIFIED
                else -> FieldVerificationState.UNKNOWN
            },
            connectorEvidenceSource = sourceEvidence?.sourceName ?: source,
            connectorVerifiedAtEpochMs = sourceEvidence?.lastVerifiedAtEpochMs,
            occupancyStatus = occupancyStatus,
            occupancyObservedAtEpochMs = liveStatusUpdatedAtEpochMs,
            tariffSchedule = tariffSchedule
        )
    }

    /**
     * V13.2 Section 3 & 4 Invariant: Evaluates live port occupancy freshness strictly from
     * [liveStatusUpdatedAtEpochMs], NEVER falling back to static [retrievedDate]!
     */
    fun evaluateLiveStatusFreshness(
        clock: DomainClock = DomainClock.SYSTEM,
        ttlMs: Long = EvDomainPolicyConfig.PORT_LIVE_STATUS_TTL_MS
    ): FreshnessEvaluationResult = FreshnessPolicyEvaluator.evaluatePortLiveStatusFreshness(
        liveStatusUpdatedAtEpochMs = liveStatusUpdatedAtEpochMs,
        clock = clock,
        ttlMs = ttlMs
    )

    fun isLiveStatusStale(
        clock: DomainClock = DomainClock.SYSTEM,
        ttlMs: Long = EvDomainPolicyConfig.PORT_LIVE_STATUS_TTL_MS
    ): Boolean = evaluateLiveStatusFreshness(clock, ttlMs).isStaleOrInvalid
}

data class DomainChargingStationWithPorts(
    val station: DomainChargingStation,
    val ports: List<DomainChargingPort>
)

/**
 * V13.1 Section 8: Duplicate station detection and evidence-preserving merge utility.
 */
object StationCatalogDeduplicator {

    private fun statusRank(status: String): Int = when (status.uppercase()) {
        "VERIFIED" -> 4
        "PARTIALLY_VERIFIED" -> 3
        "ASSUMED" -> 2
        else -> 1
    }

    private fun normalizeName(raw: String): String =
        raw.trim().lowercase()
            .replace("ایستگاه", "")
            .replace("شارژ", "")
            .replace("سریع", "")
            .replace("فوق‌سریع", "")
            .replace("فوق سریع", "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return r * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt((1.0 - a).coerceAtLeast(0.0)))
    }

    fun areDuplicates(
        a: DomainChargingStation,
        b: DomainChargingStation,
        proximityThresholdMeters: Double = 120.0
    ): Boolean {
        if (a.id == b.id) return true
        if (!a.externalId.isNullOrBlank() && !b.externalId.isNullOrBlank() &&
            a.externalId.equals(b.externalId, ignoreCase = true)
        ) {
            return true
        }
        val distM = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        if (distM <= 35.0) return true
        val normA = normalizeName(a.canonicalName)
        val normB = normalizeName(b.canonicalName)
        return distM <= proximityThresholdMeters && normA.isNotEmpty() && normA == normB
    }

    fun mergeDuplicatePair(
        first: DomainChargingStationWithPorts,
        second: DomainChargingStationWithPorts
    ): DomainChargingStationWithPorts {
        val s1 = first.station
        val s2 = second.station
        val pickFirstAsPrimary = when {
            s1.hasTraceableExternalEvidence != s2.hasTraceableExternalEvidence -> s1.hasTraceableExternalEvidence
            statusRank(s1.validationStatus) != statusRank(s2.validationStatus) ->
                statusRank(s1.validationStatus) > statusRank(s2.validationStatus)
            else -> s1.confidence >= s2.confidence
        }
        val primary = if (pickFirstAsPrimary) s1 else s2
        val secondary = if (pickFirstAsPrimary) s2 else s1

        val mergedExternalId = primary.externalId?.takeIf { it.isNotBlank() }
            ?: secondary.externalId?.takeIf { it.isNotBlank() }
        val mergedSourceUrl = primary.sourceUrl?.takeIf {
            !it.startsWith("urn:iran-ev-registry:") && (it.startsWith("https://") || it.startsWith("http://"))
        } ?: secondary.sourceUrl?.takeIf {
            !it.startsWith("urn:iran-ev-registry:") && (it.startsWith("https://") || it.startsWith("http://"))
        }
        val mergedVerifiedAt = listOfNotNull(primary.verifiedAt, secondary.verifiedAt).maxOrNull()
        val mergedEvidence = when {
            primary.sourceEvidence?.hasTraceableExternalEvidence == true -> primary.sourceEvidence
            secondary.sourceEvidence?.hasTraceableExternalEvidence == true -> secondary.sourceEvidence
            else -> primary.sourceEvidence ?: secondary.sourceEvidence
        }
        val mergedSourceReference = listOfNotNull(primary.sourceReference, secondary.sourceReference)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .ifBlank { null }

        val mergedStation = primary.copy(
            externalId = mergedExternalId,
            sourceUrl = mergedSourceUrl,
            verifiedAt = mergedVerifiedAt,
            validatedDate = mergedVerifiedAt ?: primary.validatedDate ?: secondary.validatedDate,
            sourceEvidence = mergedEvidence,
            sourceReference = mergedSourceReference,
            confidence = maxOf(primary.confidence, secondary.confidence)
        )

        val combinedPorts = (first.ports + second.ports)
            .map { it.copy(stationId = mergedStation.id) }
            .distinctBy { "${it.canonicalConnector.name}_${it.powerKw ?: 0.0}_${it.isDc}" }

        return DomainChargingStationWithPorts(station = mergedStation, ports = combinedPorts)
    }

    fun deduplicateAndMergeDomainStations(
        stations: List<DomainChargingStationWithPorts>,
        proximityThresholdMeters: Double = 120.0
    ): List<DomainChargingStationWithPorts> {
        val mergedList = mutableListOf<DomainChargingStationWithPorts>()
        for (item in stations) {
            val existingIdx = mergedList.indexOfFirst {
                areDuplicates(it.station, item.station, proximityThresholdMeters)
            }
            if (existingIdx >= 0) {
                mergedList[existingIdx] = mergeDuplicatePair(mergedList[existingIdx], item)
            } else {
                mergedList.add(item)
            }
        }
        return mergedList
    }
}

sealed interface DomainActiveVehicleSelectionState {
    data object NoActiveVehicle : DomainActiveVehicleSelectionState
    data class Selected(val vehicle: DomainVehicle) : DomainActiveVehicleSelectionState
    data class InvalidSelection(val requestedId: String) : DomainActiveVehicleSelectionState
}

data class DomainPendingNavigationHandoff(
    val originName: String,
    val originLat: Double,
    val originLon: Double,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val autoStart: Boolean = true
)

data class DomainActiveJourneyState(
    val journeyId: String,
    val lifecycleState: String,
    val vehicleId: String,
    val originName: String,
    val destinationName: String,
    val waypointsJson: String,
    val currentSocPercent: Double,
    val batteryCapacityKwh: Double,
    val totalDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val lastLatitude: Double,
    val lastLongitude: Double,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

data class DomainTripHistorySummary(
    val tripId: String,
    val vehicleId: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val distanceMeters: Double,
    val predictedEnergyWh: Double,
    val measuredEnergyWh: Double,
    val averageSpeedKmh: Double,
    val ambientTempCelsius: Double,
    val isVerifiedTelemetry: Boolean,
    val modelCalibrationVersion: Int
)

/**
 * Shared domain state holder for active vehicle & SOC synchronization across legacy feature screens.
 */
object ActiveVehicleDomainStateHolder {
    const val CANONICAL_DEFAULT_VEHICLE_ID = "vw_id4_crozz_2024"

    private val _activeVehicleId = kotlinx.coroutines.flow.MutableStateFlow("")
    val activeVehicleId: kotlinx.coroutines.flow.StateFlow<String> = _activeVehicleId

    private val _activeVehicleState = kotlinx.coroutines.flow.MutableStateFlow<DomainVehicle?>(null)
    val activeVehicleState: kotlinx.coroutines.flow.StateFlow<DomainVehicle?> = _activeVehicleState

    private val _currentSocPercent = kotlinx.coroutines.flow.MutableStateFlow(85.0)
    val currentSocPercent: kotlinx.coroutines.flow.StateFlow<Double> = _currentSocPercent

    private val _socDataSource = kotlinx.coroutines.flow.MutableStateFlow(DomainVehicleSocSource.UNCONFIRMED_DEFAULT)
    val socDataSource: kotlinx.coroutines.flow.StateFlow<DomainVehicleSocSource> = _socDataSource

    private var pendingHandoff: DomainPendingNavigationHandoff? = null

    fun isSocConfirmed(): Boolean =
        _socDataSource.value == DomainVehicleSocSource.USER_MANUAL_INPUT ||
            _socDataSource.value == DomainVehicleSocSource.USER_MANUAL ||
            _socDataSource.value == DomainVehicleSocSource.LIVE_VEHICLE_TELEMETRY ||
            _socDataSource.value == DomainVehicleSocSource.OBD_II_LIVE

    fun consumePendingNavigationHandoff(): DomainPendingNavigationHandoff? {
        val h = pendingHandoff
        pendingHandoff = null
        return h
    }

    fun resetSharedStateForTesting() {
        _activeVehicleId.value = ""
        _activeVehicleState.value = null
        _currentSocPercent.value = 85.0
        _socDataSource.value = DomainVehicleSocSource.UNCONFIRMED_DEFAULT
        pendingHandoff = null
    }
}

typealias VehicleDomainModel = DomainVehicle
typealias ChargingStationDomainModel = DomainChargingStation
typealias ChargingPortDomainModel = DomainChargingPort
typealias ChargingStationWithPortsDomainModel = DomainChargingStationWithPorts
typealias ActiveJourneyDomainModel = DomainActiveJourneyState
typealias ConnectorTypeDomain = DomainConnectorType
typealias CanonicalSourceType = DomainSourceType
typealias CanonicalAccessType = DomainAccessType

