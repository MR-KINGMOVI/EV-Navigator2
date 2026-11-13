package com.evnavigator.iran.domain.intelligence

import com.evnavigator.iran.domain.intelligence.calibration.EnergyCalibrationEngine
import com.evnavigator.iran.domain.intelligence.calibration.TripEnergyObservation
import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.charging.ChargingSessionOptimizer
import com.evnavigator.iran.domain.intelligence.charging.RiskAwareChargingEvaluator
import com.evnavigator.iran.domain.intelligence.charging.VehicleChargingProfile
import com.evnavigator.iran.domain.intelligence.config.IntelligenceConfigurationManager
import com.evnavigator.iran.domain.intelligence.context.DrivingMode
import com.evnavigator.iran.domain.intelligence.context.InputQualityAttribute
import com.evnavigator.iran.domain.intelligence.context.IntelligenceCoverageStatus
import com.evnavigator.iran.domain.intelligence.context.TripIntelligenceContext
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.explanation.ExplanationFactory
import com.evnavigator.iran.domain.intelligence.explanation.ReasonCodes
import com.evnavigator.iran.domain.intelligence.offline.CachedEnvironmentSnapshot
import com.evnavigator.iran.domain.intelligence.offline.OfflineDatasetAvailability
import com.evnavigator.iran.domain.intelligence.offline.OfflineIntelligenceAdapter
import com.evnavigator.iran.domain.intelligence.personalization.PersonalizationManager
import com.evnavigator.iran.domain.intelligence.replanning.DynamicReplanningCoordinator
import com.evnavigator.iran.domain.intelligence.replanning.ReplanningTrigger
import com.evnavigator.iran.domain.intelligence.routing.IntelligentRouteScorer
import com.evnavigator.iran.domain.intelligence.routing.RouteCandidate
import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective
import com.evnavigator.iran.domain.intelligence.uncertainty.ConfidenceAndUncertaintyEngine
import com.evnavigator.iran.domain.intelligence.uncertainty.QualitativeUncertainty
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end integration and regression test verifying seamless interoperability
 * of all Phase 14 intelligence components across the entire pipeline.
 */
class Phase14EndToEndIntegrationTest {

    private val energyOrchestrator = PredictiveEnergyOrchestrator()
    private val uncertaintyEngine = ConfidenceAndUncertaintyEngine()
    private val calibrationEngine = EnergyCalibrationEngine()
    private val personalizationManager = PersonalizationManager()
    private val routeScorer = IntelligentRouteScorer()
    private val chargingEvaluator = RiskAwareChargingEvaluator()
    private val sessionOptimizer = ChargingSessionOptimizer()
    private val replanningCoordinator = DynamicReplanningCoordinator()
    private val offlineAdapter = OfflineIntelligenceAdapter()
    private val configManager = IntelligenceConfigurationManager()

    @Test
    fun testFullTripIntelligenceLifecycle() {
        val now = System.currentTimeMillis()

        // 1. Offline & Environment resolution
        val offlineDatasets = OfflineDatasetAvailability(
            hasMapRoutingDataset = true,
            mapDatasetVersion = "2026.1",
            hasElevationDataset = true,
            elevationDatasetVersion = "2026.1",
            hasChargingRegistryDataset = true,
            chargingDatasetVersion = "2026.1"
        )
        val coverage = offlineAdapter.determineCoverageStatus(offlineDatasets)
        assertEquals(IntelligenceCoverageStatus.FULL_COVERAGE, coverage)

        val cachedEnv = CachedEnvironmentSnapshot(temperatureCelsius = 22.0, windSpeedMps = 3.0, timestampEpochMs = now)
        val (tempAttr, windAttr) = offlineAdapter.resolveEnvironmentalInputs(isOnline = true, cachedWeather = cachedEnv, currentEpochMs = now)

        // 2. Build Trip Intelligence Context
        val context = TripIntelligenceContext(
            vehicleId = "ev_iran_khodro_tara_ev",
            batteryCapacityKwh = InputQualityAttribute.verified(50.0),
            curbWeightKg = InputQualityAttribute.verified(1550.0),
            additionalPayloadKg = InputQualityAttribute.userConfigured(160.0),
            dragCoefficient = InputQualityAttribute.verified(0.29),
            frontalAreaM2 = InputQualityAttribute.verified(2.15),
            rollingResistanceCoeff = InputQualityAttribute.verified(0.010),
            driveEfficiency = InputQualityAttribute.verified(0.90),
            currentSocPercent = InputQualityAttribute.measured(80.0, now),
            targetArrivalSocPercent = InputQualityAttribute.userConfigured(20.0),
            minReserveSocPercent = InputQualityAttribute.userConfigured(10.0),
            routeDistanceMeters = 150_000.0, // 150 km
            routeEstimatedDurationSeconds = 5400.0,
            elevationGainMeters = InputQualityAttribute.verified(500.0),
            elevationLossMeters = InputQualityAttribute.verified(200.0),
            averageSpeedKmh = InputQualityAttribute.verified(100.0),
            ambientTemperatureCelsius = tempAttr,
            windSpeedMps = windAttr,
            windDirectionDegrees = InputQualityAttribute.measured(180.0, now),
            hvacPowerKw = InputQualityAttribute.verified(1.0),
            drivingMode = DrivingMode.NORMAL,
            weatherFreshnessTimestamp = now,
            trafficFreshnessTimestamp = now,
            coverageStatus = coverage
        )

        // 3. Predictive Energy Orchestration
        val prediction = energyOrchestrator.predictTripEnergy(context)
        assertTrue("150km on 50kWh pack should consume ~25-35 kWh", prediction.breakdown.netBatteryKwh in 20.0..40.0)
        assertTrue("Arrival SOC should be positive", prediction.predictedArrivalSocPercent > 0.0)

        // 4. Uncertainty Evaluation
        val uncertainty = uncertaintyEngine.evaluateUncertainty(prediction, context)
        assertEquals(QualitativeUncertainty.LOW_UNCERTAINTY, uncertainty.qualitativeLevel)
        assertTrue("Conservative arrival SOC must be strictly lower than nominal", uncertainty.arrivalSocIntervalPercent.lowerBound < prediction.predictedArrivalSocPercent)

        // 5. Adaptive Calibration (record 3 previous trips)
        repeat(3) { i ->
            calibrationEngine.recordObservation(
                TripEnergyObservation(
                    tripId = "trip_$i",
                    vehicleId = "ev_iran_khodro_tara_ev",
                    distanceMeters = 50_000.0,
                    predictedWh = 10_000.0,
                    measuredWh = 10_800.0, // driver uses slightly more power (8% higher)
                    averageSpeedKmh = 100.0,
                    ambientTempCelsius = 22.0,
                    timestampEpochMs = now
                )
            )
        }
        val calState = calibrationEngine.getCalibration("ev_iran_khodro_tara_ev")
        assertTrue(calState.isCalibrated)
        assertEquals(1.08, calState.correctionMultiplier, 0.02)

        // Apply calibration
        val calibratedEnergyWh = calibrationEngine.applyCalibration(prediction.breakdown.netBatteryEnergyWh, "ev_iran_khodro_tara_ev")
        assertTrue("Calibrated energy should reflect 8% higher driver consumption", calibratedEnergyWh > prediction.breakdown.netBatteryEnergyWh)

        // 6. Intelligent Route Scoring across competing alternatives
        val candidate1 = RouteCandidate(
            routeId = "highway_freeway",
            distanceMeters = 150_000.0,
            durationSeconds = 5400.0,
            predictedEnergyKwh = 30.0,
            predictedArrivalSocPercent = 20.0,
            chargingStopCount = 0,
            totalChargingAndDetourMinutes = 0.0,
            uncertaintyScore = 0.05
        )
        val candidate2 = RouteCandidate(
            routeId = "old_road_scenic",
            distanceMeters = 140_000.0,
            durationSeconds = 7200.0, // slower
            predictedEnergyKwh = 23.0, // more efficient
            predictedArrivalSocPercent = 34.0,
            chargingStopCount = 0,
            totalChargingAndDetourMinutes = 0.0,
            uncertaintyScore = 0.10
        )
        val rankedBalanced = routeScorer.scoreAndRankRoutes(listOf(candidate1, candidate2), RouteOptimizationObjective.BALANCED)
        assertEquals(2, rankedBalanced.size)

        // 7. Charging Stop Evaluation
        val candidateStation = CandidateStation(
            stationId = "station_km_90",
            stationName = "Highway Rest Area 90",
            compatibleConnectors = listOf("CCS2"),
            maxPowerKw = 120.0,
            detourDistanceMeters = 1000.0,
            detourDurationSeconds = 120.0,
            distanceAlongRouteMeters = 90_000.0,
            liveStatusLastUpdatedEpochMs = now,
            isReportedAvailable = true
        )
        val stationEval = chargingEvaluator.evaluateStation(
            candidate = candidateStation,
            vehicleConnectorType = "CCS2",
            vehicleMaxChargePowerKw = 100.0,
            nominalArrivalSocPercent = 40.0,
            socUncertaintyDeltaPercent = 4.0,
            otherReachableStationsCount = 1
        )
        assertTrue(stationEval.isSafelyReachable)
        assertTrue(stationEval.recommendationSummary.contains("RECOMMENDED"))

        // 8. Session Optimization
        val chargingProfile = VehicleChargingProfile(
            vehicleId = "ev_iran_khodro_tara_ev",
            batteryCapacityKwh = 50.0,
            maxDCPowerKw = 100.0
        )
        val sessionResult = sessionOptimizer.optimizeSession(
            profile = chargingProfile,
            chargerPowerLimitKw = 120.0,
            startSocPercent = 30.0,
            targetSocPercent = 80.0
        )
        assertTrue("Charging 30% to 80% (25 kWh) on 100kW peak should take ~20-30 min", sessionResult.estimatedDurationMinutes in 18.0..32.0)

        // 9. Dynamic Replanning Event
        val deviationEval = replanningCoordinator.evaluateDeviation(65.0, currentEpochMs = now + 60_000L)
        assertTrue("65m off-route must trigger replan", deviationEval.shouldReplan)
        assertEquals(ReplanningTrigger.ROUTE_DEVIATION, deviationEval.trigger)

        // 10. Explainability
        val exp = ExplanationFactory.routeSelected("highway_freeway", ReasonCodes.ROUTE_FASTER_TIME, 30, null)
        assertEquals(ReasonCodes.ROUTE_FASTER_TIME, exp.reasonCode)
        assertTrue(exp.summaryEn.isNotBlank())
        assertTrue(exp.summaryFa.isNotBlank())
    }
}
