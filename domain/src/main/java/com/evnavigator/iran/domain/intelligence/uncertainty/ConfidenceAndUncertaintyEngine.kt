package com.evnavigator.iran.domain.intelligence.uncertainty

import com.evnavigator.iran.domain.intelligence.context.IntelligenceCoverageStatus
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.TripEnergyPrediction
import kotlin.math.sqrt

/**
 * Qualitative level of uncertainty based on data provenance, freshness, and model coverage.
 */
enum class QualitativeUncertainty {
    LOW_UNCERTAINTY,
    MODERATE_UNCERTAINTY,
    HIGH_UNCERTAINTY,
    VERY_HIGH_UNCERTAINTY
}

/**
 * A physically grounded bounded interval [lowerBound, upperBound] centered at nominalValue.
 */
data class BoundedInterval(
    val nominalValue: Double,
    val lowerBound: Double, // best-case (e.g. minimum energy or maximum arrival SOC)
    val upperBound: Double  // worst-case (e.g. maximum energy or minimum arrival SOC)
) {
    init {
        require(lowerBound <= upperBound) { "Lower bound ($lowerBound) cannot exceed upper bound ($upperBound)." }
    }

    val span: Double get() = upperBound - lowerBound
}

/**
 * Detailed breakdown of uncertainty sources contributing to energy variation.
 */
data class UncertaintySources(
    val vehicleParamDeltaWh: Double,
    val elevationDeltaWh: Double,
    val weatherStalenessDeltaWh: Double,
    val trafficSpeedVarianceDeltaWh: Double,
    val coverageDegradationDeltaWh: Double
) {
    val totalRssDeltaWh: Double
        get() = sqrt(
            vehicleParamDeltaWh * vehicleParamDeltaWh +
            elevationDeltaWh * elevationDeltaWh +
            weatherStalenessDeltaWh * weatherStalenessDeltaWh +
            trafficSpeedVarianceDeltaWh * trafficSpeedVarianceDeltaWh +
            coverageDegradationDeltaWh * coverageDegradationDeltaWh
        )
}

/**
 * Comprehensive uncertainty assessment for an EV trip.
 */
data class TripUncertaintyAssessment(
    val energyIntervalWh: BoundedInterval,
    val arrivalSocIntervalPercent: BoundedInterval,
    val qualitativeLevel: QualitativeUncertainty,
    val sources: UncertaintySources,
    val primaryRiskFactors: List<String>
)

/**
 * Engine that rigorously calculates confidence and bounded uncertainty intervals.
 */
class ConfidenceAndUncertaintyEngine {

    /**
     * Compute bounded uncertainty from trip prediction, context, and data quality indicators.
     */
    fun evaluateUncertainty(
        prediction: TripEnergyPrediction,
        context: TripIntelligenceContext,
        isCalibrated: Boolean = false
    ): TripUncertaintyAssessment {
        val risks = mutableListOf<String>()
        val nominalWh = prediction.breakdown.netBatteryEnergyWh
        val batteryCapKwh = (context.usableBatteryCapacityKwh.value ?: context.batteryCapacityKwh.value)
            ?.takeIf { it.isFinite() && it > 0.0 }
        if (batteryCapKwh == null) {
            risks.add("MISSING_BATTERY_CAPACITY: Usable battery capacity is unknown; SoC interval is unverifiable.")
        }

        // 1. Vehicle parameter variance
        // If drag or weight is assumed, variance is higher (~10-15%), if verified ~3-5%
        val vehicleUncertaintyRatio = when {
            batteryCapKwh == null -> 0.25
            context.dragCoefficient.isAssumption || context.curbWeightKg.isAssumption -> {
                risks.add("Vehicle aerodynamic or mass specifications are assumed fallbacks.")
                0.12
            }
            !isCalibrated -> 0.06
            else -> 0.03 // Calibrated with actual vehicle telemetry
        }
        val vehicleDeltaWh = nominalWh * vehicleUncertaintyRatio

        // 2. Elevation uncertainty
        val elevationDeltaWh = when {
            context.coverageStatus == IntelligenceCoverageStatus.ELEVATION_MISSING -> {
                risks.add("Elevation profile is missing for part of the route.")
                nominalWh * 0.15
            }
            context.elevationGainMeters.isAssumption -> {
                risks.add("Elevation gain is an estimated approximation.")
                nominalWh * 0.08
            }
            else -> nominalWh * 0.02
        }

        // 3. Weather freshness
        val weatherDeltaWh = when {
            context.weatherFreshnessTimestamp == null -> {
                risks.add("No live weather observation available; standard climate assumed.")
                nominalWh * 0.08
            }
            System.currentTimeMillis() - context.weatherFreshnessTimestamp > 60 * 60 * 1000L -> {
                risks.add("Weather observation is older than 1 hour.")
                nominalWh * 0.06
            }
            else -> nominalWh * 0.02
        }

        // 4. Traffic / speed variance
        val trafficDeltaWh = when {
            context.trafficFreshnessTimestamp == null -> {
                risks.add("Live traffic flow unknown; speed profile assumes free-flow.")
                nominalWh * 0.07
            }
            System.currentTimeMillis() - context.trafficFreshnessTimestamp > 15 * 60 * 1000L -> {
                risks.add("Traffic updates are stale.")
                nominalWh * 0.05
            }
            else -> nominalWh * 0.02
        }

        // 5. Coverage degradation
        val coverageDeltaWh = when (context.coverageStatus) {
            IntelligenceCoverageStatus.FULL_COVERAGE -> 0.0
            IntelligenceCoverageStatus.PARTIAL_ROADS_ONLY -> {
                risks.add("Partial offline road coverage; local road geometry interpolated.")
                nominalWh * 0.10
            }
            IntelligenceCoverageStatus.OFFLINE_STALE -> {
                risks.add("Offline map dataset is out of date.")
                nominalWh * 0.05
            }
            IntelligenceCoverageStatus.UNAVAILABLE -> {
                risks.add("Map dataset unavailable for region.")
                nominalWh * 0.20
            }
            IntelligenceCoverageStatus.ELEVATION_MISSING -> 0.0 // covered in elevation
        }

        val sources = UncertaintySources(
            vehicleParamDeltaWh = vehicleDeltaWh,
            elevationDeltaWh = elevationDeltaWh,
            weatherStalenessDeltaWh = weatherDeltaWh,
            trafficSpeedVarianceDeltaWh = trafficDeltaWh,
            coverageDegradationDeltaWh = coverageDeltaWh
        )

        val totalDeltaWh = sources.totalRssDeltaWh

        // Best case energy consumption is lower, worst case is higher
        val energyInterval = BoundedInterval(
            nominalValue = nominalWh,
            lowerBound = (nominalWh - totalDeltaWh).coerceAtLeast(0.0),
            upperBound = nominalWh + totalDeltaWh
        )

        // Best case arrival SOC is higher, worst case (conservative) is lower
        val nominalArrivalSoc = prediction.predictedArrivalSocPercent.takeIf { it.isFinite() } ?: 0.0
        val socDelta = if (batteryCapKwh != null) {
            (totalDeltaWh / (batteryCapKwh * 1000.0)) * 100.0
        } else {
            100.0
        }

        val socInterval = BoundedInterval(
            nominalValue = nominalArrivalSoc,
            lowerBound = (nominalArrivalSoc - socDelta).coerceIn(-100.0, 100.0), // conservative worst-case
            upperBound = (nominalArrivalSoc + socDelta).coerceIn(-100.0, 100.0)  // optimistic best-case
        )

        val uncertaintyRatio = if (nominalWh > 0.0) totalDeltaWh / nominalWh else 1.0
        val qualitative = when {
            batteryCapKwh == null -> QualitativeUncertainty.VERY_HIGH_UNCERTAINTY
            uncertaintyRatio < 0.08 && risks.isEmpty() -> QualitativeUncertainty.LOW_UNCERTAINTY
            uncertaintyRatio < 0.18 -> QualitativeUncertainty.MODERATE_UNCERTAINTY
            uncertaintyRatio < 0.30 -> QualitativeUncertainty.HIGH_UNCERTAINTY
            else -> QualitativeUncertainty.VERY_HIGH_UNCERTAINTY
        }

        return TripUncertaintyAssessment(
            energyIntervalWh = energyInterval,
            arrivalSocIntervalPercent = socInterval,
            qualitativeLevel = qualitative,
            sources = sources,
            primaryRiskFactors = risks
        )
    }
}
