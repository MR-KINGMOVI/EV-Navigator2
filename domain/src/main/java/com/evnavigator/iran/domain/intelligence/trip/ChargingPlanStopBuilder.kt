package com.evnavigator.iran.domain.intelligence.trip

import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.model.Soc
import com.evnavigator.iran.domain.model.trip.intelligence.*
import java.util.Date
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Helper for constructing [PlannedChargingStop] and final [ChargingPlanCandidate] instances (V13.2).
 */
internal object ChargingPlanStopBuilder {

    fun buildPlannedChargingStop(
        chosenCand: PredictiveChargingOrchestrator.ProjectedStationCandidate,
        legSimulation: SegmentPhysicsEnergyCalculator.LegEnergySimulationResult,
        departureSocAtStop: Double,
        durationBreakdown: PredictiveChargingOrchestrator.ChargingDurationBreakdown,
        arrivalTimestampMs: Long,
        isTempMeasured: Boolean,
        isSocAuthoritative: Boolean,
        minReserveSoc: Double
    ): Pair<PlannedChargingStop, List<String>> {
        val chosenStation = chosenCand.stationWithPorts.station
        val chosenPort = chosenCand.selectedPort
        val elig = chosenCand.portEligibility

        val stopWarnings = elig.warnings.toMutableList()
        if (!isTempMeasured) stopWarnings.add("دمای محیط بر اساس مقدار استاندارد فرض شده است.")
        if (chosenCand.detourCalculationSource != DetourCalculationSource.ROAD_GRAPH_VERIFIED) {
            stopWarnings.add("مسافت انحراف جاده‌ای تاییدشده در دسترس نیست؛ فقط فاصله عمودی تا مسیر (${chosenCand.estimatedCrossTrackMeters.roundToInt()} متر) محاسبه شد.")
        }

        val stopRecStatus = StationPortEligibilityEvaluator.determineStopRecommendationStatus(
            elig, isSocAuthoritative, legSimulation.arrivalSocNominalPercent, legSimulation.arrivalSocMinPercent, minReserveSoc
        )
        val stopConfidence = StationPortEligibilityEvaluator.calculateStopConfidence(chosenStation, elig, isSocAuthoritative)

        val stop = PlannedChargingStop(
            stationId = chosenStation.id,
            portId = chosenPort.id,
            locationName = chosenStation.name,
            latitude = chosenStation.latitude,
            longitude = chosenStation.longitude,
            connectorType = chosenCand.canonicalConnector.displayName,
            maxPowerKw = elig.portMaxPowerKw,
            estimatedArrivalTime = Date(arrivalTimestampMs),
            requiredEnergyKwh = durationBreakdown.deliveredEnergyKwh,
            effectiveChargingPowerKw = durationBreakdown.averageEffectivePowerKw,
            estimatedDurationMinutes = durationBreakdown.estimatedMinutes,
            isVerifiedStation = elig.isStationVerifiedWithEvidence && elig.stationFreshness.isFresh,
            freshnessStatus = elig.stationFreshness.classification.name,
            routePositionIndex = chosenCand.routeSegmentIndex,
            distanceAlongRouteMeters = chosenCand.distanceAlongRouteMeters,
            verifiedRoadDetourMeters = chosenCand.verifiedRoadDetourMeters,
            estimatedCrossTrackMeters = chosenCand.estimatedCrossTrackMeters,
            detourDistanceMeters = chosenCand.detourDistanceMeters,
            detourDurationMinutes = chosenCand.detourDurationMinutes,
            detourCalculationSource = chosenCand.detourCalculationSource,
            arrivalSocPercent = legSimulation.arrivalSocNominalPercent.coerceIn(0.0, 100.0),
            arrivalSocRangeMinPercent = legSimulation.arrivalSocMinPercent.coerceIn(0.0, 100.0),
            arrivalSocRangeMaxPercent = legSimulation.arrivalSocMaxPercent.coerceIn(0.0, 100.0),
            targetSocPercent = departureSocAtStop,
            portStatus = chosenPort.status,
            connectorCompatibilityStatus = elig.connectorCompatibility,
            isStationFresh = elig.stationFreshness.isFresh,
            isPortStatusFresh = elig.portLiveFreshness.isFresh,
            hasExternalSourceEvidence = elig.isStationVerifiedWithEvidence,
            dataProvenance = if (elig.isFullyVerifiedAndFresh) "VERIFIED_LIVE" else "REQUIRES_VERIFICATION",
            confidence = stopConfidence,
            feasibilityStatus = stopRecStatus.name,
            recommendationStatus = stopRecStatus,
            warnings = stopWarnings.distinct(),
            selectionReason = "درگاه سازگار ${chosenCand.canonicalConnector.displayName} (${elig.portMaxPowerKw.toInt()} kW) در کیلومتر ${(chosenCand.distanceAlongRouteMeters / 1000.0).roundToInt()} مسیر"
        )
        return stop to stopWarnings
    }

    fun assembleChargingPlanCandidate(
        tripId: String,
        plannedStops: List<PlannedChargingStop>,
        segmentProfiles: List<SegmentPhysicsEnergyCalculator.SegmentKineticProfile>,
        finalLegSim: SegmentPhysicsEnergyCalculator.LegEnergySimulationResult,
        finalArrivalSoc: Double,
        minReserveSoc: Double,
        isSocAuthoritative: Boolean,
        effectiveContext: TripIntelligenceContext,
        planWarnings: List<String>,
        planAssumptions: MutableSet<String>,
        rejectedReasons: Map<String, String>
    ): ChargingPlanCandidate {
        val totalChargingMinutes = plannedStops.sumOf { it.estimatedDurationMinutes }
        val totalDetourMeters = plannedStops.sumOf { it.detourDistanceMeters }
        val canonicalTravelTime = CanonicalTravelTimeEngine.computeTripDuration(
            segmentProfiles,
            com.evnavigator.iran.domain.model.units.Meters(totalDetourMeters.coerceAtLeast(0.0)),
            com.evnavigator.iran.domain.model.units.Seconds((totalChargingMinutes * 60.0).coerceAtLeast(0.0)),
            plannedStops.size
        )
        planAssumptions.add("CANONICAL_TRAVEL_TIME_EXPECTED_SEC:${canonicalTravelTime.expectedTotalDuration.value.roundToLong()}")
        planAssumptions.add("CANONICAL_CHARGING_OVERHEAD_SEC:${canonicalTravelTime.chargingOverheadDuration.value.roundToLong()}")

        val totalUncertaintyMinutes = plannedStops.sumOf { stop ->
            stop.estimatedDurationMinutes * (if (stop.recommendationStatus == PlanRecommendationStatus.VERIFIED_FEASIBLE) 0.10 else 0.28)
        }
        val overallRecStatus = when {
            plannedStops.any { it.recommendationStatus == PlanRecommendationStatus.INFEASIBLE } -> PlanRecommendationStatus.INFEASIBLE
            plannedStops.any { it.recommendationStatus == PlanRecommendationStatus.INSUFFICIENT_DATA } -> PlanRecommendationStatus.INSUFFICIENT_DATA
            plannedStops.any { it.recommendationStatus == PlanRecommendationStatus.NEEDS_VERIFICATION } -> PlanRecommendationStatus.NEEDS_VERIFICATION
            plannedStops.isNotEmpty() && plannedStops.all { it.recommendationStatus == PlanRecommendationStatus.VERIFIED_FEASIBLE } &&
                isSocAuthoritative && !finalLegSim.usedEstimatedVehiclePhysics -> PlanRecommendationStatus.VERIFIED_FEASIBLE
            isSocAuthoritative -> PlanRecommendationStatus.ESTIMATED_FEASIBLE
            else -> PlanRecommendationStatus.NEEDS_VERIFICATION
        }

        val isVerifiedPlan = overallRecStatus == PlanRecommendationStatus.VERIFIED_FEASIBLE
        val rawConfidence = if (plannedStops.isNotEmpty()) plannedStops.map { it.confidence }.average() else if (isSocAuthoritative) 0.82 else 0.55
        val overallConfidence = min(rawConfidence, finalLegSim.confidenceCeiling)
        val inputOriginsMap = mapOf(
            "batteryCapacityKwh" to effectiveContext.batteryCapacityKwh.origin.name,
            "currentSocPercent" to effectiveContext.currentSocPercent.origin.name,
            "curbWeightKg" to effectiveContext.curbWeightKg.origin.name,
            "dragCoefficient" to effectiveContext.dragCoefficient.origin.name,
            "elevationGainMeters" to effectiveContext.elevationGainMeters.origin.name,
            "averageSpeedKmh" to effectiveContext.averageSpeedKmh.origin.name,
            "ambientTemperatureCelsius" to effectiveContext.ambientTemperatureCelsius.origin.name,
            "travelTimeSpeedOrigin" to canonicalTravelTime.speedOrigin.name
        )

        return ChargingPlanCandidate(
            id = "plan_${UUID.randomUUID()}",
            tripId = tripId,
            plannedChargingStops = plannedStops,
            estimatedTotalEnergyKwh = plannedStops.sumOf { it.requiredEnergyKwh },
            estimatedChargingTimeMinutes = totalChargingMinutes,
            requiredReserveAfterCharging = Soc(minReserveSoc),
            isVerifiedPlan = isVerifiedPlan,
            uncertaintyMinutesDelta = totalUncertaintyMinutes,
            finalArrivalSocPercent = finalArrivalSoc,
            confidence = overallConfidence,
            feasibilityStatus = overallRecStatus.name,
            recommendationStatus = overallRecStatus,
            warnings = planWarnings.distinct(),
            planAssumptions = planAssumptions.toList(),
            inputOrigins = inputOriginsMap,
            rejectedStationReasons = rejectedReasons
        )
    }
}
