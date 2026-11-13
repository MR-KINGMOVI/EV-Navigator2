package com.evnavigator.iran.ui.screen.map.model

import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteResult
import com.evnavigator.iran.domain.intelligence.charging.CandidateStation
import com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.journey.multistop.MultiStopJourneyPlan
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route

enum class DestinationType {
    CITY_HUB,
    CHARGING_STATION,
    CUSTOM_MAP_PIN
}

data class DestinationSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val coordinate: GeoCoordinate,
    val type: DestinationType,
    val stationDetails: CandidateStation? = null,
    val distanceFromUserKm: Double? = null
)

sealed class RouteCalculationState {
    object Idle : RouteCalculationState()
    object Calculating : RouteCalculationState()
    data class Success(
        val plan: MultiStopJourneyPlan,
        val originName: String,
        val destinationName: String,
        val polylinePoints: List<GeoCoordinate>,
        val totalDistanceKm: Double,
        val totalDurationMinutes: Int,
        val totalEnergyKwh: Double,
        val arrivalSocPercent: Double,
        val chargingStopsCount: Int,
        val isFeasible: Boolean,
        val warnings: List<String>,
        val maneuvers: List<RealRoadManeuver> = emptyList(),
        val finalPlannedRoute: FinalPlannedRoute? = plan.finalPlannedRoute,
        val route: Route? = finalPlannedRoute?.toDomainRoute(),
        val realRoadResult: RealRoadRouteResult? = null,
        val estimatedDuration: Long = (totalDurationMinutes * 60L),
        val distance: Distance = Distance.fromMeters(totalDistanceKm * 1000.0),
        val energyRequired: Double = totalEnergyKwh,
        val alternativeRoutesCount: Int = 0,
        val isOnlineRoute: Boolean = false,
        val routeSourceClassification: RouteSourceClassification =
            finalPlannedRoute?.sourceClassification ?: plan.sourceClassification
    ) : RouteCalculationState()
    data class Error(val message: String) : RouteCalculationState()

    companion object {
        val Loading: RouteCalculationState get() = Calculating
    }
}
