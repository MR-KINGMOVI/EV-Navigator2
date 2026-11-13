package com.evnavigator.iran.ui.screen.intelligence

import com.evnavigator.iran.domain.intelligence.charging.EvaluatedChargingStation
import com.evnavigator.iran.domain.intelligence.explanation.DecisionExplanation
import com.evnavigator.iran.domain.intelligence.routing.RouteOptimizationObjective
import com.evnavigator.iran.domain.intelligence.routing.ScoredRoute

/**
 * UI State for EV intelligence and trip planning.
 * Reflects real dynamic routes, physical topography, and true candidate stations.
 */
data class TripIntelligenceUiState(
    val isLoading: Boolean = false,
    val activeVehicleName: String = "Skywell ET5 (72 kWh)",
    val activeVehicleBatteryCapacityKwh: Double = 72.0,
    val predictedArrivalSocPercent: Double? = null,
    val conservativeArrivalSocPercent: Double? = null,
    val optimisticArrivalSocPercent: Double? = null,
    val totalEnergyKwh: Double? = null,
    val reserveEnergyKwh: Double? = null,
    val isFeasibleWithoutCharging: Boolean = true,
    val qualitativeUncertainty: String = "LOW",
    val explanations: List<DecisionExplanation> = emptyList(),
    val scoredRoutes: List<ScoredRoute> = emptyList(),
    val recommendedChargingStops: List<EvaluatedChargingStation> = emptyList(),
    val selectedObjective: RouteOptimizationObjective = RouteOptimizationObjective.BALANCED,
    val minReserveSocPercent: Double = 10.0,
    val totalDistanceKm: Double? = null,
    val routeDistanceKm: Double? = null,
    val elevationGainMeters: Double? = null,
    val elevationLossMeters: Double? = null,
    val elevationProfilePoints: List<Pair<Double, Double>> = emptyList(),
    val passSummitsCrossed: List<String> = emptyList(),
    val errorMessage: String? = null
)
