package com.evnavigator.iran.domain.intelligence.context

import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig

/**
 * Driving powertrain operating profile.
 */
enum class DrivingMode {
    ECO,
    NORMAL,
    SPORT
}

/**
 * Level of coverage available for the area / route.
 */
enum class IntelligenceCoverageStatus {
    FULL_COVERAGE,
    PARTIAL_ROADS_ONLY,
    ELEVATION_MISSING,
    OFFLINE_STALE,
    UNAVAILABLE
}

/**
 * Unified, validated trip-intelligence context.
 * Gathers vehicle state, physical parameters, environmental observations, and route properties
 * with explicit provenance and quality metadata for every input.
 */
data class TripIntelligenceContext(
    val vehicleId: String,
    val batteryCapacityKwh: InputQualityAttribute<Double>,
    val curbWeightKg: InputQualityAttribute<Double>,
    val additionalPayloadKg: InputQualityAttribute<Double> = InputQualityAttribute.assumed(0.0, "Zero additional payload"),
    val dragCoefficient: InputQualityAttribute<Double>,
    val frontalAreaM2: InputQualityAttribute<Double>,
    val rollingResistanceCoeff: InputQualityAttribute<Double>,
    val driveEfficiency: InputQualityAttribute<Double>,

    // Energy state
    val currentSocPercent: InputQualityAttribute<Double>,
    val targetArrivalSocPercent: InputQualityAttribute<Double> = InputQualityAttribute.assumed(
        EvDomainPolicyConfig.DEFAULT_MIN_RESERVE_SOC_PERCENT,
        "Default target arrival SoC"
    ),
    val minReserveSocPercent: InputQualityAttribute<Double>,

    // Route characteristics
    val routeDistanceMeters: Double,
    val routeEstimatedDurationSeconds: Double = 0.0,
    val elevationGainMeters: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val elevationLossMeters: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val averageSpeedKmh: InputQualityAttribute<Double>,

    // Environment
    val ambientTemperatureCelsius: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val windSpeedMps: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val windDirectionDegrees: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val hvacPowerKw: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val drivingMode: DrivingMode = DrivingMode.NORMAL,

    // Data freshness & dataset metadata
    val trafficFreshnessTimestamp: Long? = null,
    val weatherFreshnessTimestamp: Long? = null,
    val offlineDatasetVersion: String? = null,
    val coverageStatus: IntelligenceCoverageStatus = IntelligenceCoverageStatus.FULL_COVERAGE,
    val averageElevationMeters: InputQualityAttribute<Double> = InputQualityAttribute.unknown(),
    val routeHeadingDegrees: Double? = null,
    val usableBatteryCapacityKwh: InputQualityAttribute<Double> = batteryCapacityKwh
) {
    /** Total effective mass in kilograms (curb + payload) */
    val totalMassKg: Double?
        get() {
            val curb = curbWeightKg.value ?: return null
            val payload = additionalPayloadKg.value ?: 0.0
            return curb + payload
        }

    /** Effective aerodynamic drag area Cd * A */
    val dragAreaM2: Double?
        get() {
            val cd = dragCoefficient.value ?: return null
            val a = frontalAreaM2.value ?: return null
            return cd * a
        }

    /**
     * Computes the minimum confidence across critical inputs (V13.0 Audit — Item 147:
     * final confidence must never exceed the weakest critical input confidence).
     */
    val criticalInputMinConfidence: Double
        get() = listOf(
            batteryCapacityKwh.confidence,
            currentSocPercent.confidence,
            curbWeightKg.confidence,
            averageSpeedKmh.confidence
        ).minOrNull() ?: 0.0
}
