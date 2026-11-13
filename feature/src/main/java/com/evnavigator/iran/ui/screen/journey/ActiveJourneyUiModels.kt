package com.evnavigator.iran.ui.screen.journey

import com.evnavigator.iran.core.routing.model.LaneGuidanceInfo
import com.evnavigator.iran.core.routing.model.RealRoadManeuver
import com.evnavigator.iran.core.routing.model.RealRoadRouteSegment
import com.evnavigator.iran.domain.intelligence.trip.FinalPlannedRoute
import com.evnavigator.iran.domain.journey.charging.ChargingSessionState
import com.evnavigator.iran.domain.journey.lifecycle.JourneyLifecycleState
import com.evnavigator.iran.domain.journey.multistop.MultiStopJourneyPlan
import com.evnavigator.iran.domain.journey.network.JourneyConnectivityMode
import com.evnavigator.iran.domain.journey.tracking.JourneyProgress
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.ChargingStationWithPortsDomainModel
import com.evnavigator.iran.domain.policy.EvDomainPolicyConfig
import com.evnavigator.iran.ui.screen.map.model.DestinationSearchResult

/**
 * The 10 Explicit Automotive Navigation Modes for EV Navigator Iran (V11 Extracted).
 */
enum class NavigationScreenMode {
    IDLE_FREE_DRIVE,
    DESTINATION_SEARCH,
    ROUTE_PREVIEW,
    ACTIVE_NAVIGATION,
    NAVIGATION_PAUSED,
    GPS_WEAK_OR_DISABLED,
    OFFLINE_MODE,
    MISSING_OFFLINE_MAP,
    MISSING_ROUTE_DATA,
    ROUTE_CALCULATION_ERROR
}

enum class RerouteState {
    IDLE,
    REQUESTED,
    IN_PROGRESS,
    COOLDOWN,
    FAILED
}

data class ActiveJourneyUiState(
    val navigationMode: NavigationScreenMode = NavigationScreenMode.IDLE_FREE_DRIVE,
    val lifecycleState: JourneyLifecycleState = JourneyLifecycleState.READY_TO_START,
    val plan: MultiStopJourneyPlan? = null,
    val finalPlannedRoute: FinalPlannedRoute? = null,
    val isDemoPlan: Boolean = false,
    val isHypotheticalPlan: Boolean = false,
    val progress: JourneyProgress? = null,
    val chargingSession: ChargingSessionState? = null,
    val currentSocPercent: Double = EvDomainPolicyConfig.DEFAULT_INITIAL_SOC_PERCENT,
    val vehicleModelName: String = "خودرو انتخاب نشده",
    val vehicleBatteryKwh: Double = 0.0,
    val vehicleId: String = "",
    val vehicleMaxChargeKw: Double = 0.0,
    val vehicleSupportedConnectors: List<String> = emptyList(),
    val isVehicleSelected: Boolean = false,
    val connectivityMode: JourneyConnectivityMode = JourneyConnectivityMode.ONLINE_FULL,
    val isInternetConnected: Boolean = true,
    val isOfflineMapAvailable: Boolean = true,
    val activeWarning: String? = null,
    val routeCalculationError: String? = null,
    val statusMessage: String = "آماده حرکت در حالت گشت آزاد",
    val isAudioMuted: Boolean = false,
    val currentManeuver: String = "",
    val currentManeuverIcon: String = "",
    val nextManeuverDistanceMeters: Double = 0.0,
    val nextRoadName: String = "",
    val speedLimitKmh: Int = 0,
    val isHeadingUpCamera: Boolean = true,
    val isTunnelMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<DestinationSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedDestination: DestinationSearchResult? = null,
    val chargingStations: List<ChargingStationWithPortsDomainModel> = emptyList(),
    val selectedStation: ChargingStationWithPortsDomainModel? = null,
    val directBearingDegrees: Float? = null,
    val directDistanceMeters: Double? = null,
    val averageConsumptionWhPerKm: Double = 0.0,
    val predictedArrivalSocPercent: Double? = null,
    val roadPolyline: List<GeoCoordinate> = emptyList(),
    val routeSegments: List<RealRoadRouteSegment> = emptyList(),
    val maneuvers: List<RealRoadManeuver> = emptyList(),
    val isSimulatingDrive: Boolean = false,
    val simulatedCarCoordinate: GeoCoordinate? = null,
    val simulatedCarBearing: Float = 0f,
    val simulatedSpeedKmh: Double = 0.0,
    val currentHighwayShield: String? = null,
    val activeLaneConfig: LaneGuidanceInfo? = null,
    val routeGenerationId: Long = 1L,
    val activeRouteId: String = "",
    val rerouteState: RerouteState = RerouteState.IDLE,
    val lastRerouteTimestampMs: Long = 0L
)
