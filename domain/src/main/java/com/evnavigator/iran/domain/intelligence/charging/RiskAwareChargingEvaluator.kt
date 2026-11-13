package com.evnavigator.iran.domain.intelligence.charging

import com.evnavigator.iran.domain.intelligence.uncertainty.BoundedInterval
import com.evnavigator.iran.domain.journey.multistop.CanonicalConnectorType

/**
 * Operational confidence of a charging station.
 */
enum class OperationalConfidence {
    /** Live dynamic telemetry received within 15 minutes */
    CONFIRMED_OPERATIONAL_LIVE,
    /** Alias for recent verified operational telemetry */
    VERIFIED_RECENT,
    /** Static registry entry verified, but live status is unconfirmed */
    STATIC_VERIFIED_LIVE_UNKNOWN,
    /** Telemetry indicates station is busy, throttled, or reporting errors */
    SUSPECTED_DEGRADED,
    /** Station confirmed out of service or decommissioned */
    CONFIRMED_UNAVAILABLE
}

typealias StationOperationalConfidence = OperationalConfidence

enum class StationFreshnessState {
    VERIFIED_RECENT,
    STALE,
    UNKNOWN
}

/**
 * Candidate charging station evaluated along a route.
 */
data class CandidateStation(
    val stationId: String,
    val stationName: String,
    val compatibleConnectors: List<String>, // e.g. ["CCS2", "GB/T_DC"]
    val maxPowerKw: Double,
    val detourDistanceMeters: Double,
    val detourDurationSeconds: Double,
    val distanceAlongRouteMeters: Double,
    val liveStatusLastUpdatedEpochMs: Long? = null,
    val isReportedAvailable: Boolean? = null,
    val freshnessState: StationFreshnessState = when {
        liveStatusLastUpdatedEpochMs == null -> StationFreshnessState.UNKNOWN
        System.currentTimeMillis() - liveStatusLastUpdatedEpochMs <= 15 * 60 * 1000L -> StationFreshnessState.VERIFIED_RECENT
        else -> StationFreshnessState.STALE
    }
) {
    val supportedConnectors: List<String>
        get() = compatibleConnectors

    val isVerified: Boolean
        get() = freshnessState == StationFreshnessState.VERIFIED_RECENT && isReportedAvailable == true

    val lastVerifiedTimestampMs: Long?
        get() = liveStatusLastUpdatedEpochMs
}

/**
 * Result of evaluating a candidate charging station with risk and uncertainty awareness.
 */
data class EvaluatedChargingStation(
    val candidate: CandidateStation,
    val isConnectorCompatible: Boolean,
    val effectiveChargingPowerKw: Double,
    val arrivalSocInterval: BoundedInterval,
    val isMathematicallyReachable: Boolean,
    val isSafelyReachable: Boolean,
    val operationalConfidence: OperationalConfidence,
    val backupAlternativeCount: Int,
    val riskFactors: List<String>,
    val recommendationSummary: String
)

/**
 * Evaluates charging stop feasibility, connector compatibility, reserve buffers,
 * live operational uncertainty, and reachability of backup alternatives.
 */
class RiskAwareChargingEvaluator(
    private val maxLiveFreshnessMs: Long = 15 * 60 * 1000L // 15 minutes
) {

    /**
     * Evaluate candidate charging stations along a route.
     * Uses canonical connector normalization ([CanonicalConnectorType]) so aliases like
     * "CCS2 Combo" and "CCS2" or "GB/T DC" and "GBT" match consistently across stages.
     */
    fun evaluateStation(
        candidate: CandidateStation,
        vehicleConnectorType: String,
        vehicleMaxChargePowerKw: Double,
        nominalArrivalSocPercent: Double,
        socUncertaintyDeltaPercent: Double,
        minReserveSocPercent: Double = 10.0,
        otherReachableStationsCount: Int = 0
    ): EvaluatedChargingStation {
        val risks = mutableListOf<String>()

        // 1. Canonical Connector compatibility
        val targetCanonical = CanonicalConnectorType.parse(vehicleConnectorType)
        val isCompatible = if (targetCanonical != CanonicalConnectorType.UNKNOWN) {
            candidate.compatibleConnectors.any { stConn ->
                CanonicalConnectorType.parse(stConn) == targetCanonical
            }
        } else {
            val cleanVeh = vehicleConnectorType.trim()
            cleanVeh.isNotEmpty() &&
                !cleanVeh.equals("INCOMPATIBLE", ignoreCase = true) &&
                !cleanVeh.equals("UNKNOWN", ignoreCase = true) &&
                !cleanVeh.equals("NONE", ignoreCase = true) &&
                candidate.compatibleConnectors.any { it.trim().equals(cleanVeh, ignoreCase = true) }
        }
        if (!isCompatible) {
            risks.add("Incompatible connector: vehicle requires $vehicleConnectorType, station offers ${candidate.compatibleConnectors.joinToString()}.")
        }

        // 2. Charging power bottleneck
        val safeStationKw = candidate.maxPowerKw.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val safeVehicleKw = vehicleMaxChargePowerKw.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val effectivePower = minOf(safeStationKw, safeVehicleKw)

        // 3. Reachability & Uncertainty
        val conservativeArrivalSoc = nominalArrivalSocPercent - socUncertaintyDeltaPercent
        val optimisticArrivalSoc = nominalArrivalSocPercent + socUncertaintyDeltaPercent
        val arrivalInterval = BoundedInterval(
            nominalValue = nominalArrivalSocPercent,
            lowerBound = conservativeArrivalSoc.coerceIn(-100.0, 100.0),
            upperBound = optimisticArrivalSoc.coerceIn(-100.0, 100.0)
        )

        val isMathematicallyReachable = nominalArrivalSocPercent > 0.0
        val isSafelyReachable = conservativeArrivalSoc >= minReserveSocPercent && isCompatible && effectivePower > 0.0

        if (!isMathematicallyReachable) {
            risks.add("Unreachable: Vehicle battery depleted before arriving at station.")
        } else if (!isSafelyReachable && isCompatible) {
            risks.add("Insufficient safety reserve: Worst-case arrival SOC (${String.format("%.1f", conservativeArrivalSoc)}%) is below required ${minReserveSocPercent}% reserve.")
        }

        // 4. Operational Confidence (never claim operational if live status unknown)
        val operationalConfidence = when {
            candidate.liveStatusLastUpdatedEpochMs == null || candidate.isReportedAvailable == null -> {
                risks.add("Station existence verified from registry, but real-time availability is unconfirmed.")
                OperationalConfidence.STATIC_VERIFIED_LIVE_UNKNOWN
            }
            System.currentTimeMillis() - candidate.liveStatusLastUpdatedEpochMs > maxLiveFreshnessMs -> {
                risks.add("Live availability report is stale (> 15 minutes old).")
                OperationalConfidence.STATIC_VERIFIED_LIVE_UNKNOWN
            }
            candidate.isReportedAvailable == false -> {
                risks.add("Station is currently reported in-use or out of service.")
                OperationalConfidence.CONFIRMED_UNAVAILABLE
            }
            else -> OperationalConfidence.CONFIRMED_OPERATIONAL_LIVE
        }

        // 5. Reachable backup alternatives
        if (isSafelyReachable && otherReachableStationsCount == 0) {
            risks.add("Single point of failure: No reachable alternative chargers exist within remaining range if this station fails.")
        }

        val recommendation = when {
            !isCompatible -> "REJECTED: Incompatible connector."
            !isMathematicallyReachable -> "REJECTED: Unreachable with current charge."
            !isSafelyReachable -> "WARNING: High risk of depletion before arrival (reserve compromised)."
            operationalConfidence == OperationalConfidence.CONFIRMED_UNAVAILABLE -> "AVOID: Confirmed out of service."
            operationalConfidence == OperationalConfidence.STATIC_VERIFIED_LIVE_UNKNOWN ->
                "CAUTION: Reachable (${String.format("%.1f", nominalArrivalSocPercent)}% SOC), but live status unknown."
            else -> "RECOMMENDED: Safely reachable with verified fast charger (${effectivePower.toInt()} kW)."
        }

        return EvaluatedChargingStation(
            candidate = candidate,
            isConnectorCompatible = isCompatible,
            effectiveChargingPowerKw = effectivePower,
            arrivalSocInterval = arrivalInterval,
            isMathematicallyReachable = isMathematicallyReachable,
            isSafelyReachable = isSafelyReachable,
            operationalConfidence = operationalConfidence,
            backupAlternativeCount = otherReachableStationsCount,
            riskFactors = risks,
            recommendationSummary = recommendation
        )
    }
}
