package com.evnavigator.iran.domain.model.catalog

import com.evnavigator.iran.domain.intelligence.trip.ParameterProvenanceStatus
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.FreshnessEvaluationResult
import com.evnavigator.iran.domain.policy.FreshnessPolicyEvaluator

/**
 * Comprehensive field-level quality, provenance, and readiness evaluator for [DomainVehicle]
 * (V13.3.1 Section 15).
 *
 * Tracks per-property provenance ([ParameterProvenanceStatus]) for every physical and electrical
 * attribute (`grossBatteryKwh`, `usableBatteryKwh`, `curbMassKg`, `dragCoefficient`, `frontalAreaM2`,
 * `rollingResistanceCoefficient`, `acMaxChargePowerKw`, `dcMaxChargePowerKw`, `connectorTypes`, `chargeCurve`).
 */
enum class VehicleQualityStatus {
    VERIFIED_COMPLETE,
    USABLE_WITH_ASSUMPTIONS,
    INCOMPLETE,
    INVALID
}

data class VehiclePropertyProvenance(
    val propertyName: String,
    val status: ParameterProvenanceStatus,
    val source: String,
    val verifiedAtEpochMs: Long?
)

data class VehicleQualityAssessment(
    val vehicleId: String,
    val status: VehicleQualityStatus,
    val isUsableForDeterministicPlanning: Boolean,
    val isFullPhysicsAvailable: Boolean,
    val canUseFastDcCharging: Boolean,
    val canUseAcCharging: Boolean,
    val usableBatteryKwh: Double?,
    val grossBatteryKwh: Double?,
    val canonicalConnectors: Set<CanonicalConnectorType>,
    val ambiguousOrUnknownConnectors: List<String>,
    val missingOrInvalidFields: List<String>,
    val assumedOrEstimatedFields: List<String>,
    val warnings: List<String>,
    val freshness: FreshnessEvaluationResult,
    val calibratedConfidence: Double,
    val propertyProvenance: Map<String, VehiclePropertyProvenance> = emptyMap()
) {
    val isValidForPlanning: Boolean
        get() = status == VehicleQualityStatus.VERIFIED_COMPLETE || status == VehicleQualityStatus.USABLE_WITH_ASSUMPTIONS
}

object VehicleDataQualityEvaluator {

    fun evaluate(
        vehicle: DomainVehicle?,
        clock: DomainClock = DomainClock.SYSTEM
    ): VehicleQualityAssessment {
        if (vehicle == null) {
            return VehicleQualityAssessment(
                vehicleId = "",
                status = VehicleQualityStatus.INVALID,
                isUsableForDeterministicPlanning = false,
                isFullPhysicsAvailable = false,
                canUseFastDcCharging = false,
                canUseAcCharging = false,
                usableBatteryKwh = null,
                grossBatteryKwh = null,
                canonicalConnectors = emptySet(),
                ambiguousOrUnknownConnectors = emptyList(),
                missingOrInvalidFields = listOf("vehicle"),
                assumedOrEstimatedFields = emptyList(),
                warnings = listOf("هیچ خودروی فعالی انتخاب نشده است."),
                freshness = FreshnessPolicyEvaluator.evaluateVehicleCatalogFreshness(null, null, clock),
                calibratedConfidence = 0.0,
                propertyProvenance = emptyMap()
            )
        }

        val missingOrInvalid = mutableListOf<String>()
        val assumedOrEstimated = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val provenanceMap = LinkedHashMap<String, VehiclePropertyProvenance>()

        val baseVerifiedStatus = when {
            vehicle.isVerified && vehicle.verifiedAt != null && vehicle.verifiedAt > 0L -> ParameterProvenanceStatus.VERIFIED
            vehicle.dataSource.contains("USER", ignoreCase = true) -> ParameterProvenanceStatus.USER_PROVIDED
            else -> ParameterProvenanceStatus.ESTIMATED
        }
        val baseSource = vehicle.dataSource.ifBlank { "CATALOG_SPEC" }

        fun recordProp(name: String, isPresentAndValid: Boolean, isEstimatedFallback: Boolean = false) {
            val st = when {
                !isPresentAndValid -> ParameterProvenanceStatus.UNKNOWN
                isEstimatedFallback -> ParameterProvenanceStatus.ESTIMATED
                else -> baseVerifiedStatus
            }
            provenanceMap[name] = VehiclePropertyProvenance(
                propertyName = name,
                status = st,
                source = if (isPresentAndValid) baseSource else "MISSING",
                verifiedAtEpochMs = if (st == ParameterProvenanceStatus.VERIFIED) vehicle.verifiedAt else null
            )
        }

        if (vehicle.id.isBlank()) missingOrInvalid.add("id")
        if (vehicle.manufacturer.isBlank()) missingOrInvalid.add("manufacturer")
        if (vehicle.model.isBlank()) missingOrInvalid.add("model")

        val usableKwh = vehicle.usableBatteryKwh.takeIf { it.isFinite() && it in 5.0..250.0 }
        val grossKwh = vehicle.nominalBatteryKwh.takeIf { it.isFinite() && it in 5.0..280.0 }

        recordProp("usableBatteryKwh", usableKwh != null)
        recordProp("grossBatteryKwh", grossKwh != null)

        if (usableKwh == null) {
            missingOrInvalid.add("usableBatteryCapacityWh")
            warnings.add("ظرفیت قابل‌استفاده باتری (Usable Battery Capacity) نامعتبر یا نامشخص است.")
        }
        if (grossKwh != null && usableKwh != null && usableKwh > grossKwh + 0.5) {
            missingOrInvalid.add("usableExceedsGrossBatteryCapacity")
            warnings.add("ظرفیت قابل‌استفاده باتری ($usableKwh kWh) بزرگ‌تر از ظرفیت اسمی کل ($grossKwh kWh) است.")
        }

        val dcKw = vehicle.dcMaxChargePowerKw?.takeIf { it in 10.0..600.0 }
        val acKw = vehicle.acMaxChargePowerKw?.takeIf { it in 1.4..44.0 }
        recordProp("dcMaxChargePowerKw", dcKw != null)
        recordProp("acMaxChargePowerKw", acKw != null)
        recordProp("chargeCurve", dcKw != null || acKw != null, isEstimatedFallback = !vehicle.isVerified)

        if (dcKw == null && acKw == null) {
            missingOrInvalid.add("chargingPowerKw")
            warnings.add("حداکثر توان شارژ خودرو (AC و DC) نامشخص است.")
        } else {
            if (dcKw == null) warnings.add("توان شارژ سریع DC خودرو نامشخص است؛ فقط شارژ AC قابل برنامه‌ریزی قطعی است.")
            if (acKw == null) warnings.add("توان شارژ AC خودرو نامشخص است؛ فقط شارژ DC قابل برنامه‌ریزی قطعی است.")
        }

        val normalizedRawConnectors = ConnectorNormalizer.normalizeConnectorStrings(vehicle.connectorTypes)
        val canonicalConnectors = ConnectorNormalizer.toCanonicalSet(vehicle.connectorTypes)
        val ambiguousConnectors = ConnectorNormalizer.findAmbiguousOrUnknownConnectors(vehicle.connectorTypes)

        recordProp(
            "connectorTypes",
            normalizedRawConnectors.isNotEmpty() && canonicalConnectors.isNotEmpty(),
            isEstimatedFallback = ambiguousConnectors.isNotEmpty()
        )

        if (normalizedRawConnectors.isEmpty() || canonicalConnectors.isEmpty()) {
            missingOrInvalid.add("connectorTypes")
            warnings.add("هیچ درگاه شارژ استاندارد و معتبری برای خودرو ثبت نشده است.")
        }
        if (ambiguousConnectors.isNotEmpty()) {
            warnings.add("درگاه‌های شارژ مبهم یا غیراستاندارد نادیده گرفته شدند: ${ambiguousConnectors.joinToString(", ")}")
        }

        val hasValidMass = vehicle.curbMassKg.isFinite() && vehicle.curbMassKg in 450.0..5000.0
        val hasValidCd = vehicle.dragCoefficient != null && vehicle.dragCoefficient.isFinite() && vehicle.dragCoefficient in 0.15..0.65
        val hasValidArea = vehicle.frontalAreaM2 != null && vehicle.frontalAreaM2.isFinite() && vehicle.frontalAreaM2 in 1.2..4.5
        val hasValidCrr = vehicle.rollingResistanceCoefficient != null &&
            vehicle.rollingResistanceCoefficient.isFinite() &&
            vehicle.rollingResistanceCoefficient in 0.004..0.030

        recordProp("curbMassKg", hasValidMass)
        recordProp("dragCoefficient", hasValidCd)
        recordProp("frontalAreaM2", hasValidArea)
        recordProp("rollingResistanceCoefficient", hasValidCrr)

        if (!hasValidMass) assumedOrEstimated.add("curbMassKg")
        if (!hasValidCd) assumedOrEstimated.add("dragCoefficient")
        if (!hasValidArea) assumedOrEstimated.add("frontalAreaM2")
        if (!hasValidCrr) assumedOrEstimated.add("rollingResistanceCoefficient")

        val isFullPhysicsAvailable = hasValidMass && hasValidCd && hasValidArea && hasValidCrr && usableKwh != null
        val canUseDc = dcKw != null && canonicalConnectors.any { it.isDcFast }
        val canUseAc = acKw != null && canonicalConnectors.any { !it.isDcFast }

        val freshness = FreshnessPolicyEvaluator.evaluateVehicleCatalogFreshness(
            verifiedAtEpochMs = vehicle.verifiedAt,
            retrievedAtEpochMs = vehicle.retrievedDate.takeIf { it > 0L },
            clock = clock
        )

        val status = when {
            vehicle.id.isBlank() || usableKwh == null || (usableKwh != null && grossKwh != null && usableKwh > grossKwh + 0.5) ->
                VehicleQualityStatus.INVALID
            missingOrInvalid.isNotEmpty() ->
                VehicleQualityStatus.INCOMPLETE
            !isFullPhysicsAvailable || !vehicle.isVerified || ambiguousConnectors.isNotEmpty() || freshness.isStaleOrInvalid ->
                VehicleQualityStatus.USABLE_WITH_ASSUMPTIONS
            else ->
                VehicleQualityStatus.VERIFIED_COMPLETE
        }

        var confidence = vehicle.confidence.coerceIn(0.20, 0.95)
        if (!vehicle.isVerified) confidence = minOf(confidence, 0.72)
        if (!isFullPhysicsAvailable) confidence -= 0.12
        if (ambiguousConnectors.isNotEmpty()) confidence -= 0.10
        if (freshness.isStaleOrInvalid) confidence -= 0.08
        if (status == VehicleQualityStatus.INCOMPLETE || status == VehicleQualityStatus.INVALID) {
            confidence = minOf(confidence, 0.25)
        }

        return VehicleQualityAssessment(
            vehicleId = vehicle.id,
            status = status,
            isUsableForDeterministicPlanning = status == VehicleQualityStatus.VERIFIED_COMPLETE && (canUseDc || canUseAc),
            isFullPhysicsAvailable = isFullPhysicsAvailable,
            canUseFastDcCharging = canUseDc,
            canUseAcCharging = canUseAc,
            usableBatteryKwh = usableKwh,
            grossBatteryKwh = grossKwh,
            canonicalConnectors = canonicalConnectors,
            ambiguousOrUnknownConnectors = ambiguousConnectors,
            missingOrInvalidFields = missingOrInvalid,
            assumedOrEstimatedFields = assumedOrEstimated,
            warnings = warnings,
            freshness = freshness,
            calibratedConfidence = confidence.coerceIn(0.0, 0.95),
            propertyProvenance = provenanceMap
        )
    }
}
