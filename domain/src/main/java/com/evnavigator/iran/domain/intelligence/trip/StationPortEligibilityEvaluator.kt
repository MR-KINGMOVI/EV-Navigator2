package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.model.catalog.CanonicalConnectorType
import com.evnavigator.iran.domain.model.catalog.DomainChargingPort
import com.evnavigator.iran.domain.model.catalog.DomainChargingStation
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.model.trip.intelligence.ConnectorCompatibilityStatus
import com.evnavigator.iran.domain.model.trip.intelligence.PlanRecommendationStatus
import com.evnavigator.iran.domain.policy.DomainClock
import com.evnavigator.iran.domain.policy.FreshnessEvaluationResult

/**
 * Pure domain evaluator for charging station and port eligibility, connector compatibility,
 * AC/DC power matching, and freshness/evidence verification (V13.1 Sections 1, 2, 7 & 8).
 *
 * Strict Engineering Invariants:
 * 1. Never guesses connectors from vehicle manufacturer/brand names (`KMC`, `JAC`, `BYD`, etc.).
 *    Connector compatibility is evaluated strictly from explicit vehicle connector specs via
 *    [CanonicalEnergyEngine.ConnectorCompatibilityEngine].
 * 2. Never guesses AC charging power from DC charging power (`min(22.0, vehicleMaxDcKw)` is forbidden),
 *    nor substitutes `11.0` kW AC or `defaultMaxPowerKw` DC fallbacks when vehicle power is unknown.
 *    AC ports require explicit `vehicleMaxAcKw > 0.0`; DC ports require explicit `vehicleMaxDcKw > 0.0`.
 * 3. Never treats `port.status == "UNKNOWN"` as `AVAILABLE`. A stop with `port.status == "UNKNOWN"`,
 *    missing external `SourceEvidence`, or stale station/port timestamps per [DomainClock] can NEVER
 *    reach [PlanRecommendationStatus.VERIFIED_FEASIBLE] (it is capped at [PlanRecommendationStatus.NEEDS_VERIFICATION]).
 */
object StationPortEligibilityEvaluator {

    data class EligiblePortSelection(
        val port: DomainChargingPort,
        val canonicalConnector: CanonicalConnectorType,
        val connectorCompatibility: ConnectorCompatibilityStatus,
        val portMaxPowerKw: Double,
        val vehicleMaxPowerForPortKw: Double,
        val effectivePeakPowerKw: Double,
        val stationFreshness: FreshnessEvaluationResult,
        val portLiveFreshness: FreshnessEvaluationResult,
        val isPortConfirmedAvailable: Boolean,
        val isStationVerifiedWithEvidence: Boolean,
        val warnings: List<String>,
        val rawPortConnector: String = port.rawConnectorName,
        val unrecognizedVehicleConnectors: List<String> = emptyList()
    ) {
        val isFullyVerifiedAndFresh: Boolean
            get() = isStationVerifiedWithEvidence &&
                stationFreshness.isFresh &&
                isPortConfirmedAvailable &&
                portLiveFreshness.isFresh &&
                connectorCompatibility == ConnectorCompatibilityStatus.COMPATIBLE &&
                effectivePeakPowerKw > 0.0
    }

    sealed class StationEvaluationOutcome {
        data class Eligible(val selection: EligiblePortSelection) : StationEvaluationOutcome()
        data class Rejected(val reasonCode: String) : StationEvaluationOutcome()
    }

    /**
     * Parses explicit vehicle connector strings into canonical AC and DC connector sets.
     * Never infers connectors from vehicle brand/make.
     */
    fun parseExplicitVehicleConnectors(explicitConnectors: List<String>): Set<CanonicalConnectorType> {
        if (explicitConnectors.isEmpty()) return emptySet()
        return explicitConnectors
            .map { CanonicalConnectorType.fromRaw(it) }
            .filter { it != CanonicalConnectorType.UNKNOWN }
            .toSet()
    }

    /**
     * Evaluates connector compatibility between explicit vehicle connectors and a single port.
     */
    fun evaluatePortConnectorCompatibility(
        supportedVehicleConnectors: Set<CanonicalConnectorType>,
        port: DomainChargingPort
    ): Pair<ConnectorCompatibilityStatus, CanonicalConnectorType> {
        val portCanon = port.canonicalConnector
        if (supportedVehicleConnectors.isEmpty() || portCanon == CanonicalConnectorType.UNKNOWN) {
            return ConnectorCompatibilityStatus.UNKNOWN to portCanon
        }
        return if (portCanon in supportedVehicleConnectors) {
            ConnectorCompatibilityStatus.COMPATIBLE to portCanon
        } else {
            ConnectorCompatibilityStatus.INCOMPATIBLE to portCanon
        }
    }

    /**
     * Evaluates a station and all of its ports to select the single best eligible port, or returns
     * a deterministic rejection reason code.
     */
    fun evaluateStationAndSelectPort(
        stationWithPorts: DomainChargingStationWithPorts,
        explicitConnectors: List<String>,
        vehicleMaxDcKw: Double?,
        vehicleMaxAcKw: Double?,
        clock: DomainClock = DomainClock.SYSTEM,
        requireConfirmedAvailablePort: Boolean = false,
        rejectStaleStations: Boolean = false
    ): StationEvaluationOutcome {
        val st = stationWithPorts.station
        if (!st.latitude.isFinite() || !st.longitude.isFinite() ||
            st.latitude !in 24.0..40.5 || st.longitude !in 43.5..64.5
        ) {
            return StationEvaluationOutcome.Rejected("INVALID_OR_OUT_OF_BOUNDS_COORDINATES")
        }

        val statusUpper = st.validationStatus.uppercase()
        if (statusUpper in setOf("OUTDATED", "UNAVAILABLE", "DECOMMISSIONED") ||
            st.operationalStatus.name == "OUT_OF_SERVICE"
        ) {
            return StationEvaluationOutcome.Rejected("STATION_UNAVAILABLE (${st.validationStatus}/${st.operationalStatus})")
        }

        val supportedConnectors = parseExplicitVehicleConnectors(explicitConnectors)
        if (supportedConnectors.isEmpty()) {
            return StationEvaluationOutcome.Rejected("UNKNOWN_VEHICLE_CONNECTOR")
        }

        val stationFreshness = st.evaluateFreshness(clock = clock)
        if (rejectStaleStations && stationFreshness.isStaleOrInvalid) {
            return StationEvaluationOutcome.Rejected("STATION_DATA_STALE (${stationFreshness.classification})")
        }

        val isStationVerifiedWithEvidence = st.isVerified && st.hasTraceableExternalEvidence

        if (stationWithPorts.ports.isEmpty()) {
            return StationEvaluationOutcome.Rejected("NO_PORTS_REGISTERED")
        }

        val candidateSelections = mutableListOf<EligiblePortSelection>()
        var sawCompatibleConnector = false
        var sawMissingVehiclePowerForCompatiblePort = false
        var sawUnavailablePortStatus = false
        var sawUnknownPortStatusRejected = false

        for (port in stationWithPorts.ports) {
            val canonicalEval = CanonicalEnergyEngine.ConnectorCompatibilityEngine.evaluateDomainPort(
                supportedVehicleConnectors = explicitConnectors,
                port = port,
                vehicleDcMaxKw = vehicleMaxDcKw,
                vehicleAcMaxKw = vehicleMaxAcKw
            )
            if (canonicalEval.compatibilityStatus != ConnectorCompatibilityStatus.COMPATIBLE) {
                continue
            }
            sawCompatibleConnector = true
            val canon = canonicalEval.canonicalPortConnector

            val portPower = port.powerKw?.takeIf { it.isFinite() && it > 0.0 } ?: continue

            val portStatusUpper = port.status.trim().uppercase()
            if (portStatusUpper in setOf("OUT_OF_SERVICE", "UNAVAILABLE", "FAULTED", "OCCUPIED", "BUSY", "IN_USE", "OFFLINE")) {
                sawUnavailablePortStatus = true
                continue
            }

            val isPortAvailable = portStatusUpper == "AVAILABLE"
            if (requireConfirmedAvailablePort && !isPortAvailable) {
                sawUnknownPortStatusRejected = true
                continue
            }

            val vehicleAcceptanceKw = canonicalEval.vehicleAcceptanceLimitKw
            val effectivePeakKw = canonicalEval.effectivePeakPowerKw
            if (vehicleAcceptanceKw == null || effectivePeakKw == null || !canonicalEval.canProduceDeterministicDuration) {
                sawMissingVehiclePowerForCompatiblePort = true
                continue
            }
            val portLiveFreshness = port.evaluateLiveStatusFreshness(clock = clock)

            val warnings = mutableListOf<String>()
            if (!st.isVerified) {
                warnings.add("وضعیت ایستگاه تأیید میدانی نشده است (${st.validationStatus}).")
            } else if (!st.hasTraceableExternalEvidence) {
                warnings.add("ایستگاه فاقد مدرک منبع خارجی قابل‌پیگیری (SourceEvidence) است.")
            }
            if (stationFreshness.isStaleOrInvalid) {
                warnings.add("داده ایستگاه منقضی یا فاقد تاریخ معتبر است (${stationFreshness.classification}).")
            }
            if (!isPortAvailable) {
                warnings.add("وضعیت لحظه‌ای اشغال پورت (${port.id}) نامشخص (${port.status}) است؛ نیازمند بررسی پیش از حرکت.")
            } else if (portLiveFreshness.isStaleOrInvalid) {
                warnings.add("وضعیت لحظه‌ای پورت (${port.id}) منقضی شده است (${portLiveFreshness.classification}).")
            }

            candidateSelections.add(
                EligiblePortSelection(
                    port = port,
                    canonicalConnector = canon,
                    connectorCompatibility = canonicalEval.compatibilityStatus,
                    portMaxPowerKw = portPower,
                    vehicleMaxPowerForPortKw = vehicleAcceptanceKw,
                    effectivePeakPowerKw = effectivePeakKw,
                    stationFreshness = stationFreshness,
                    portLiveFreshness = portLiveFreshness,
                    isPortConfirmedAvailable = isPortAvailable,
                    isStationVerifiedWithEvidence = isStationVerifiedWithEvidence,
                    warnings = warnings,
                    rawPortConnector = canonicalEval.rawPortConnector,
                    unrecognizedVehicleConnectors = canonicalEval.unrecognizedVehicleConnectors
                )
            )
        }

        if (candidateSelections.isEmpty()) {
            val reason = when {
                !sawCompatibleConnector -> "INCOMPATIBLE_CONNECTOR"
                sawMissingVehiclePowerForCompatiblePort -> "UNKNOWN_VEHICLE_CHARGING_POWER_FOR_PORT_TYPE"
                sawUnknownPortStatusRejected -> "PORT_STATUS_UNKNOWN_REQUIRES_CONFIRMATION"
                sawUnavailablePortStatus -> "PORT_UNAVAILABLE_OR_OUT_OF_SERVICE"
                else -> "PORT_UNKNOWN_POWER"
            }
            return StationEvaluationOutcome.Rejected(reason)
        }

        // Prioritize: (1) confirmed AVAILABLE & fresh port, (2) highest effective peak charging power
        val bestSelection = candidateSelections.maxWithOrNull(
            compareBy<EligiblePortSelection> { it.isPortConfirmedAvailable && it.portLiveFreshness.isFresh }
                .thenBy { it.isPortConfirmedAvailable }
                .thenBy { it.effectivePeakPowerKw }
        )!!

        return StationEvaluationOutcome.Eligible(bestSelection)
    }

    /**
     * Determines the stop-level [PlanRecommendationStatus] enforcing all 6 mandatory conditions of V12.6 P0-3.
     */
    fun determineStopRecommendationStatus(
        selection: EligiblePortSelection,
        isSocAuthoritative: Boolean,
        arrivalSocNominalPercent: Double,
        arrivalSocMinPercent: Double,
        minReserveSocPercent: Double
    ): PlanRecommendationStatus {
        if (arrivalSocNominalPercent < minReserveSocPercent) {
            return PlanRecommendationStatus.INFEASIBLE
        }
        if (selection.connectorCompatibility == ConnectorCompatibilityStatus.INCOMPATIBLE) {
            return PlanRecommendationStatus.INFEASIBLE
        }
        if (selection.connectorCompatibility == ConnectorCompatibilityStatus.UNKNOWN || selection.effectivePeakPowerKw <= 0.0) {
            return PlanRecommendationStatus.INSUFFICIENT_DATA
        }
        // P0-3: If port status is not explicitly AVAILABLE, or station lacks external evidence, or station/port is stale -> NEEDS_VERIFICATION
        if (!selection.isPortConfirmedAvailable ||
            !selection.isStationVerifiedWithEvidence ||
            selection.stationFreshness.isStaleOrInvalid ||
            selection.portLiveFreshness.isStaleOrInvalid
        ) {
            return PlanRecommendationStatus.NEEDS_VERIFICATION
        }
        // Station & port are verified and fresh, but SOC input is unverified or worst-case bound drops below reserve -> ESTIMATED_FEASIBLE
        if (!isSocAuthoritative || arrivalSocMinPercent < minReserveSocPercent) {
            return PlanRecommendationStatus.ESTIMATED_FEASIBLE
        }
        return PlanRecommendationStatus.VERIFIED_FEASIBLE
    }

    /**
     * Calculates a calibrated confidence score [0.15 .. 0.94] for a charging stop.
     */
    fun calculateStopConfidence(
        station: DomainChargingStation,
        selection: EligiblePortSelection,
        isSocAuthoritative: Boolean
    ): Double {
        var score = when {
            selection.isStationVerifiedWithEvidence && selection.stationFreshness.isFresh -> 0.80
            station.isVerified && selection.stationFreshness.isFresh -> 0.65
            station.validationStatus.equals("PARTIALLY_VERIFIED", ignoreCase = true) -> 0.55
            station.validationStatus.equals("ASSUMED", ignoreCase = true) -> 0.42
            else -> 0.30
        }
        if (selection.stationFreshness.isStaleOrInvalid) {
            score -= 0.18
        }
        if (selection.isPortConfirmedAvailable && selection.portLiveFreshness.isFresh) {
            score += 0.10
        } else if (!selection.isPortConfirmedAvailable) {
            score -= 0.12
        } else if (selection.portLiveFreshness.isStaleOrInvalid) {
            score -= 0.08
        }
        if (!isSocAuthoritative) {
            score -= 0.12
        }
        // Cap confidence at <= 0.68 whenever status is NEEDS_VERIFICATION
        val maxCap = if (selection.isFullyVerifiedAndFresh && isSocAuthoritative) 0.94 else 0.68
        return score.coerceIn(0.15, maxCap)
    }
}
