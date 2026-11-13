package com.evnavigator.iran.domain.model.trip

import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Route
import com.evnavigator.iran.domain.model.VehicleSpec
import com.evnavigator.iran.domain.model.charging.ChargingStation
import java.time.Instant

/**
 * Core interface for the Trip Planning Engine (V12.5 Streamlined Contract).
 * Value models are extracted into [TripPlanningModels.kt].
 */
interface TripPlanningEngine {

    suspend fun planTrip(request: TripRequest): TripPlanResult

    suspend fun findChargingStationsAlongRoute(
        route: Route,
        maxDistanceFromRouteMeters: Double = 5000.0,
        vehicleSpec: VehicleSpec? = null
    ): List<ChargingStation>

    suspend fun estimateBatteryStateAlongRoute(
        route: Route,
        initialBatteryPercent: Double,
        vehicleSpec: VehicleSpec,
        departureTime: Instant = Instant.now()
    ): List<BatteryStateEstimate>

    suspend fun updateTripPlan(
        currentPlan: TripPlan,
        currentLocation: GeoCoordinate,
        currentBatteryPercent: Double,
        currentTimestamp: Instant = Instant.now()
    ): TripPlanResult

    suspend fun calculateAlternativeRoutes(
        request: TripRequest,
        maxAlternatives: Int = 3
    ): List<TripPlanResult>

    suspend fun isTripFeasibleWithoutCharging(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        initialBatteryPercent: Double,
        vehicleSpec: VehicleSpec,
        minArrivalBatteryPercent: Double = 10.0
    ): Boolean

    suspend fun calculateOptimalChargingStops(
        route: Route,
        initialBatteryPercent: Double,
        vehicleSpec: VehicleSpec,
        availableStations: List<ChargingStation>,
        chargingRequirements: TripChargingRequirements = TripChargingRequirements()
    ): List<ChargingStop>

    suspend fun estimateChargingTime(
        station: ChargingStation,
        currentBatteryPercent: Double,
        targetBatteryPercent: Double,
        vehicleSpec: VehicleSpec
    ): Long

    suspend fun getRecommendedSpeedLimit(
        segment: RouteSegment,
        targetArrivalBatteryPercent: Double,
        currentBatteryPercent: Double,
        vehicleSpec: VehicleSpec
    ): Double?

    suspend fun evaluateEnvironmentalImpact(
        tripPlan: TripPlan,
        vehicleSpec: VehicleSpec
    ): EnvironmentImpact

    suspend fun optimizeMultiDestinationTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>,
        returnToStart: Boolean = false,
        initialBatteryPercent: Double,
        vehicleSpec: VehicleSpec
    ): TripPlanResult

    suspend fun validateTripRequest(request: TripRequest): List<TripValidationError>

    suspend fun getHistoricalConsumptionFactor(
        route: Route,
        vehicleSpec: VehicleSpec,
        departureTime: Instant
    ): Double

    suspend fun reserveChargingSlot(
        stationId: String,
        connectorId: String,
        estimatedArrivalTime: Instant,
        durationSeconds: Long,
        vehicleId: String
    ): Boolean

    suspend fun cancelChargingReservation(reservationId: String): Boolean

    suspend fun getRealTimeStationStatus(stationIds: List<String>): Map<String, ChargingStation>

    suspend fun exportTripPlan(tripPlan: TripPlan, format: ExportFormat): ByteArray

    suspend fun importTripPlan(data: ByteArray, format: ExportFormat): TripPlan?

    suspend fun estimateTripCost(
        tripPlan: TripPlan,
        electricityRatePerKwh: Double? = null
    ): Double

    suspend fun getElevationProfile(route: Route): List<Pair<Double, Double>>

    suspend fun clearCache()

    suspend fun getEngineStatus(): Map<String, Any>

    suspend fun setConfiguration(config: Map<String, Any>)

    suspend fun registerTripUpdateListener(listener: (TripPlan) -> Unit)

    suspend fun unregisterTripUpdateListener(listener: (TripPlan) -> Unit)

    suspend fun shutdown()

    suspend fun initialize(): Boolean

    suspend fun getVersion(): String

    suspend fun getSupportedFeatures(): Set<String>

    suspend fun isLocationSupported(coordinate: GeoCoordinate): Boolean

    suspend fun getNearestSupportedLocation(coordinate: GeoCoordinate): GeoCoordinate?

    suspend fun getSupportedVehicleSpecs(): List<VehicleSpec>

    suspend fun getDefaultVehicleSpec(): VehicleSpec

    suspend fun getChargingStationDetails(stationId: String): ChargingStation?

    suspend fun searchChargingStations(
        center: GeoCoordinate,
        radiusMeters: Double,
        minPowerKw: Double? = null,
        connectorTypes: Set<String>? = null,
        onlyAvailable: Boolean = false
    ): List<ChargingStation>

    suspend fun reportStationIssue(
        stationId: String,
        issueType: String,
        description: String
    ): Boolean

    suspend fun rateChargingStation(
        stationId: String,
        rating: Int,
        comment: String? = null
    ): Boolean

    suspend fun getTripHistory(
        vehicleId: String,
        limit: Int = 10,
        offset: Int = 0
    ): List<TripPlan>

    suspend fun saveTripToFavorites(
        tripPlan: TripPlan,
        name: String
    ): String

    suspend fun getFavoriteTrips(vehicleId: String): Map<String, TripPlan>

    suspend fun deleteFavoriteTrip(favoriteId: String): Boolean

    suspend fun shareTripPlan(
        tripPlan: TripPlan,
        recipientIds: List<String>
    ): String

    suspend fun getSharedTripPlan(shareId: String): TripPlan?

    suspend fun syncWithCloud(): Boolean

    suspend fun getLastSyncTimestamp(): Instant?

    suspend fun setOfflineMode(enabled: Boolean)

    suspend fun isOfflineMode(): Boolean

    suspend fun downloadOfflineData(
        boundingBox: Pair<GeoCoordinate, GeoCoordinate>
    ): Boolean

    suspend fun getDownloadedOfflineRegions(): List<Pair<String, Pair<GeoCoordinate, GeoCoordinate>>>

    suspend fun deleteOfflineRegion(regionId: String): Boolean

    suspend fun getOfflineDataSize(): Long

    suspend fun checkForOfflineDataUpdates(): List<String>

    suspend fun updateOfflineRegion(regionId: String): Boolean

    suspend fun getEmergencyAssistanceInfo(location: GeoCoordinate): Map<String, String>

    suspend fun requestRoadsideAssistance(
        location: GeoCoordinate,
        vehicleSpec: VehicleSpec,
        issueDescription: String
    ): String

    suspend fun getAssistanceStatus(requestId: String): String

    suspend fun cancelAssistanceRequest(requestId: String): Boolean

    suspend fun getBatteryHealthImpact(
        tripPlan: TripPlan,
        vehicleSpec: VehicleSpec
    ): Double

    suspend fun getChargingRecommendationsForBatteryLongevity(
        tripPlan: TripPlan,
        vehicleSpec: VehicleSpec
    ): List<String>

    suspend fun getCarbonFootprintComparison(
        tripPlan: TripPlan,
        vehicleSpec: VehicleSpec
    ): Map<String, Double>

    suspend fun getOptimalDepartureTime(request: TripRequest): Instant

    suspend fun getTrafficPrediction(
        route: Route,
        departureTime: Instant
    ): Map<String, Double>

    suspend fun getWeatherForecastForTrip(
        tripPlan: TripPlan
    ): Map<Instant, Map<String, Any>>

    suspend fun getSafetyAlertsForRoute(route: Route): List<String>

    suspend fun getPointsOfInterestNearChargingStations(
        stations: List<ChargingStation>,
        categories: Set<String>,
        maxDistanceMeters: Double = 500.0
    ): Map<String, List<Map<String, Any>>>

    suspend fun getEstimatedWaitTimeAtStations(
        stationIds: List<String>,
        arrivalTimes: Map<String, Instant>
    ): Map<String, Long>

    suspend fun getDynamicPricingForStations(
        stationIds: List<String>,
        timeRange: Pair<Instant, Instant>
    ): Map<String, Map<Instant, Double>>

    suspend fun getGridLoadForecast(
        region: String,
        timeRange: Pair<Instant, Instant>
    ): Map<Instant, Double>

    suspend fun participateInDemandResponse(
        stationId: String,
        sessionId: String,
        maxDelaySeconds: Long
    ): Double

    suspend fun getV2GOpportunities(
        location: GeoCoordinate,
        availableTimeRange: Pair<Instant, Instant>,
        vehicleSpec: VehicleSpec,
        minBatteryPercentToKeep: Double
    ): List<Map<String, Any>>

    suspend fun schedulePreconditioning(
        vehicleId: String,
        targetDepartureTime: Instant,
        targetCabinTempCelsius: Double
    ): Boolean

    suspend fun getFleetAnalytics(
        fleetId: String,
        timeRange: Pair<Instant, Instant>
    ): Map<String, Any>

    suspend fun optimizeFleetRouting(
        fleetId: String,
        tasks: List<Map<String, Any>>,
        availableVehicles: List<String>
    ): Map<String, TripPlan>

    suspend fun getDriverBehaviorScore(
        driverId: String,
        tripId: String
    ): Double

    suspend fun getEcoDrivingTips(
        driverId: String,
        recentTrips: List<String>
    ): List<String>

    suspend fun getMaintenanceAlerts(
        vehicleId: String,
        upcomingTrip: TripPlan
    ): List<String>

    suspend fun bookServiceAppointment(
        vehicleId: String,
        serviceCenterId: String,
        preferredTime: Instant,
        servicesNeeded: List<String>
    ): String

    suspend fun getServiceCentersAlongRoute(
        route: Route,
        brand: String? = null
    ): List<Map<String, Any>>

    suspend fun getTollCostsForRoute(
        route: Route,
        vehicleSpec: VehicleSpec,
        departureTime: Instant
    ): Double

    suspend fun payTollsForTrip(
        tripId: String,
        paymentMethodId: String
    ): Boolean

    suspend fun getBorderCrossingInfo(
        route: Route
    ): List<Map<String, Any>>

    suspend fun getFerrySchedulesForRoute(
        route: Route,
        departureTime: Instant
    ): List<Map<String, Any>>

    suspend fun bookFerryForTrip(
        ferryId: String,
        departureTime: Instant,
        vehicleSpec: VehicleSpec,
        passengers: Int
    ): String

    suspend fun getParkingAvailabilityAtDestination(
        destination: GeoCoordinate,
        arrivalTime: Instant,
        needsCharging: Boolean = false
    ): List<Map<String, Any>>

    suspend fun reserveParkingSpot(
        parkingLotId: String,
        spotId: String?,
        arrivalTime: Instant,
        durationSeconds: Long,
        vehicleId: String
    ): String

    suspend fun getIndoorNavigationForDestination(
        destination: GeoCoordinate
    ): Map<String, Any>?

    suspend fun getLastMileOptions(
        parkingLocation: GeoCoordinate,
        finalDestination: GeoCoordinate
    ): List<Map<String, Any>>

    suspend fun integrateWithCalendar(
        userId: String,
        calendarProvider: String,
        authToken: String
    ): Boolean

    suspend fun generateTripsFromCalendarEvents(
        userId: String,
        timeRange: Pair<Instant, Instant>
    ): List<TripPlanResult>

    suspend fun getVoiceGuidanceInstructions(
        tripPlan: TripPlan,
        languageCode: String = "fa-IR"
    ): List<Pair<GeoCoordinate, String>>

    suspend fun processVoiceCommand(
        audioData: ByteArray,
        currentContext: Map<String, Any>
    ): Map<String, Any>

    suspend fun getARNavigationData(
        currentLocation: GeoCoordinate,
        heading: Double,
        tripPlan: TripPlan
    ): Map<String, Any>

    suspend fun getTelemetryStreamUrl(tripId: String): String

    suspend fun uploadTripTelemetry(
        tripId: String,
        telemetryData: ByteArray
    ): Boolean

    suspend fun getSimulationResults(
        scenario: Map<String, Any>
    ): Map<String, Any>

    suspend fun runDiagnostics(): Map<String, Boolean>

    suspend fun estimateEnergyConsumption(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Double?

    suspend fun estimateTripDuration(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Long?

    suspend fun getChargingInfrastructureAlongRoute(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): TripChargingInfrastructure?

    suspend fun getWeatherImpact(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): TripWeatherImpact?

    suspend fun getTrafficImpact(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): TripTrafficImpact?

    suspend fun getChargingCost(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Double?

    suspend fun getFuelConsumption(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): Double?

    suspend fun getChargingTimeForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Long?

    suspend fun getCostForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Double?

    suspend fun getEmissionsForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Double?

    suspend fun getWeatherImpactForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): TripWeatherImpact?

    suspend fun getTrafficImpactForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): TripTrafficImpact?

    suspend fun getChargingNeedsForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): TripChargingRequirements?

    suspend fun getBatteryStateForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): List<BatteryStateEstimate>?

    suspend fun getRouteForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Route?

    suspend fun getRouteWithChargingForTrip(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteForWaypoints(
        waypoints: List<GeoCoordinate>
    ): Route?

    suspend fun getRouteWithPreferences(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteWithChargingForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteWithConstraints(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteWithPreferencesForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Route?

    suspend fun getRouteWithRequirements(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteWithPreferencesAndConstraintsForTrip(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>
    ): Route?

    suspend fun getRouteWithRequirementsAndConstraints(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteWithAllParameters(
        start: GeoCoordinate,
        destinations: List<GeoCoordinate>,
        vehicleSpec: VehicleSpec
    ): Route?

    suspend fun getRouteWithAllParametersAndConstraints(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        vehicleSpec: VehicleSpec
    ): Route?
}