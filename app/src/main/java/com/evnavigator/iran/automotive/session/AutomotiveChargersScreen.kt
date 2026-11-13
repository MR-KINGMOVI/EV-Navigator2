package com.evnavigator.iran.automotive.session

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.evnavigator.iran.automotive.adapter.AutomotiveDataQuality
import com.evnavigator.iran.automotive.adapter.AutomotiveGPSQuality
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationState
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationStateAdapter
import com.evnavigator.iran.automotive.adapter.AutomotiveNavigationStatus
import com.evnavigator.iran.automotive.adapter.AutomotiveTrafficStatus
import com.evnavigator.iran.automotive.adapter.AutomotiveWeatherStatus
import com.evnavigator.iran.core.routing.engine.IranOfflineRoadCorridors
import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.model.RealManeuverType
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.seed.IranStationsCatalog
import com.evnavigator.iran.domain.model.Charger
import com.evnavigator.iran.domain.model.ChargerStatus
import com.evnavigator.iran.domain.model.Distance
import com.evnavigator.iran.domain.model.Duration
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.Location
import com.evnavigator.iran.domain.model.Soc
import com.evnavigator.iran.domain.model.navigation.AutomotiveDestinationRequest
import com.evnavigator.iran.domain.model.navigation.AutomotiveNavigationStateHolder
import com.evnavigator.iran.domain.model.navigation.ChargingStop
import com.evnavigator.iran.domain.model.navigation.EtaEstimate
import com.evnavigator.iran.domain.model.navigation.GpsQuality
import com.evnavigator.iran.domain.model.navigation.ManeuverType
import com.evnavigator.iran.domain.model.navigation.NavigationDataQuality
import com.evnavigator.iran.domain.model.navigation.NavigationProgress
import com.evnavigator.iran.domain.model.navigation.NavigationSessionId
import com.evnavigator.iran.domain.model.navigation.NavigationState
import com.evnavigator.iran.domain.model.navigation.NavigationStatus
import com.evnavigator.iran.domain.model.navigation.TrafficStatus
import com.evnavigator.iran.domain.model.navigation.UpcomingManeuver
import com.evnavigator.iran.domain.model.navigation.WeatherStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Android Auto Screen for browsing genuine Iranian EV Charging Stations (V11 Extracted).
 */
class AutomotiveChargersScreen(
    carContext: CarContext,
    private val onNavigationStateComputed: ((AutomotiveNavigationState) -> Unit)? = null
) : Screen(carContext) {

    private val routingEngine = IranRealRoadRoutingEngine(enableOnlineRouting = false)
    private val navAdapter = AutomotiveNavigationStateAdapter()

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        val (allStations, allPorts) = IranStationsCatalog.getProductionStationsAndPorts()
        val portsByStation = allPorts.groupBy { it.stationId }
        val userGps = AutomotiveNavigationStateHolder.lastKnownLocation.value

        val filteredStations = allStations.filter { st ->
            st.validationStatus == "VERIFIED" || st.validationStatus == "PARTIALLY_VERIFIED"
        }

        val sortedStations = if (userGps != null) {
            filteredStations.sortedBy { st ->
                haversineMeters(userGps, GeoCoordinate(st.latitude, st.longitude))
            }
        } else {
            filteredStations.sortedWith(
                compareBy<ChargingStationEntity> {
                    if (it.validationStatus == "VERIFIED") 0 else 1
                }.thenBy { it.canonicalName }
            )
        }.take(12)

        for (st in sortedStations) {
            val stationPorts = portsByStation[st.id].orEmpty()
            val maxPower = stationPorts.maxOfOrNull { it.powerKw ?: 0.0 } ?: 0.0
            val connectors = stationPorts.mapNotNull { it.connectorType?.name }.distinct().joinToString(" + ")
            val validationBadge = if (st.validationStatus == "VERIFIED") {
                "[تأییدشده - VERIFIED]"
            } else {
                "[تأیید بخشی - PARTIALLY_VERIFIED]"
            }
            val distancePrefix = userGps?.let {
                val distKm = haversineMeters(it, GeoCoordinate(st.latitude, st.longitude)) / 1000.0
                "${String.format("%.1f km", distKm)} • "
            } ?: ""

            val specs = "$distancePrefix${maxPower.toInt()} kW DC • $connectors • ${st.city}"
            val statusHours = "${if (st.is247) "شبانه‌روزی (24/7)" else "ساعات کاری محدود"} • وضعیت اشغال نازل: نامشخص (آفلاین)"

            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("${st.canonicalName} $validationBadge")
                    .addText(specs)
                    .addText(statusHours)
                    .setOnClickListener {
                        lifecycleScope.launch {
                            val gpsOrigin = AutomotiveNavigationStateHolder.lastKnownLocation.value
                            val currentSoc = AutomotiveNavigationStateHolder.currentVehicleSocPercent.value
                            val batteryKwh = AutomotiveNavigationStateHolder.vehicleBatteryCapacityKwh.value
                            val whPerKm = AutomotiveNavigationStateHolder.vehicleConsumptionWhPerKm.value
                            val socSource = AutomotiveNavigationStateHolder.socDataSource.value.name
                            val targetCoord = GeoCoordinate(st.latitude, st.longitude)

                            AutomotiveNavigationStateHolder.requestDestinationNavigation(
                                AutomotiveDestinationRequest(
                                    stationId = st.id,
                                    title = st.canonicalName,
                                    coordinate = targetCoord
                                )
                            )

                            val navState = if (gpsOrigin != null) {
                                try {
                                    val routeResult = routingEngine.calculateRealRoute(
                                        origin = gpsOrigin,
                                        destination = targetCoord
                                    )
                                    val totalDist = Distance.fromMeters(routeResult.totalDistanceMeters)
                                    val firstStep = routeResult.maneuvers.firstOrNull()
                                    val secondStep = routeResult.maneuvers.getOrNull(1)
                                    val firstDist = firstStep?.let { Distance.fromMeters(it.distanceMeters) } ?: totalDist
                                    val durSec = routeResult.totalDurationSeconds.roundToLong().coerceAtLeast(1L)

                                    var predictedArrivalSoc: Double? = null
                                    var predictedMin: Double? = null
                                    var predictedMax: Double? = null
                                    var confidence: Double? = null

                                    if (currentSoc != null && batteryKwh != null && batteryKwh > 0.0 && whPerKm != null && whPerKm > 0.0) {
                                        val netElevGainM = estimateCorridorElevationDeltaMeters(gpsOrigin, targetCoord)
                                        val elevationEnergyKwh = if (netElevGainM >= 0.0) {
                                            (2100.0 * 9.81 * netElevGainM) / (3_600_000.0 * 0.88)
                                        } else {
                                            (2100.0 * 9.81 * netElevGainM * 0.65) / 3_600_000.0
                                        }
                                        val baseEnergyKwh = (routeResult.totalDistanceMeters / 1000.0) * (whPerKm / 1000.0)
                                        val totalEnergyKwh = (baseEnergyKwh + elevationEnergyKwh).coerceAtLeast(baseEnergyKwh * 0.35)
                                        val socDrop = (totalEnergyKwh / batteryKwh) * 100.0
                                        predictedArrivalSoc = (currentSoc - socDrop).coerceIn(0.0, 100.0)
                                        val uncertaintyBand = (socDrop * 0.15).coerceAtLeast(1.5)
                                        predictedMin = (predictedArrivalSoc - uncertaintyBand).coerceIn(0.0, 100.0)
                                        predictedMax = (predictedArrivalSoc + uncertaintyBand).coerceIn(0.0, 100.0)
                                        confidence = 0.78
                                    }

                                    val firstDurationSec = firstStep?.let {
                                        it.durationSeconds.roundToLong().coerceAtLeast(1L).takeIf { sec -> sec > 1L }
                                            ?: (it.distanceMeters / ((it.speedLimitKmh.takeIf { s -> s > 0 } ?: 90) / 3.6)).roundToLong().coerceAtLeast(1L)
                                    } ?: durSec

                                    val secondDurationSec = secondStep?.let {
                                        it.durationSeconds.roundToLong().coerceAtLeast(1L).takeIf { sec -> sec > 1L }
                                            ?: (it.distanceMeters / ((it.speedLimitKmh.takeIf { s -> s > 0 } ?: 90) / 3.6)).roundToLong().coerceAtLeast(1L)
                                    } ?: 1L

                                    val domainChargingStop = ChargingStop(
                                        charger = Charger(
                                            id = st.id,
                                            name = st.canonicalName,
                                            location = targetCoord,
                                            connectors = emptyList(),
                                            maxPower = maxPower.coerceAtLeast(50.0),
                                            status = ChargerStatus.AVAILABLE,
                                            isPublic = true,
                                            provider = st.operatorName
                                        ),
                                        distanceToStop = totalDist,
                                        timeToStop = Duration.fromSeconds(durSec),
                                        estimatedArrivalSoc = Soc((predictedArrivalSoc ?: 20.0).coerceIn(0.0, 100.0)),
                                        targetSoc = Soc(85.0),
                                        estimatedChargingDuration = Duration.fromMinutes(25L),
                                        isMandatory = true
                                    )

                                    val domainProgress = NavigationProgress(
                                        distanceTraveled = Distance.fromMeters(0.0),
                                        totalDistance = totalDist,
                                        routePercentage = 0.0,
                                        elapsedTime = Duration.fromSeconds(0L),
                                        currentSegmentIndex = 0,
                                        totalSegments = max(1, routeResult.segments.size)
                                    )

                                    val domainState = NavigationState(
                                        sessionId = NavigationSessionId("auto_${st.id}_${System.currentTimeMillis()}"),
                                        status = NavigationStatus.NAVIGATING,
                                        route = null,
                                        routePosition = null,
                                        matchedLocation = Location(
                                            latitude = gpsOrigin.latitude,
                                            longitude = gpsOrigin.longitude,
                                            accuracyMeters = AutomotiveNavigationStateHolder.lastLocationAccuracyMeters.value,
                                            timestampEpochMs = AutomotiveNavigationStateHolder.lastLocationTimestampMs.value ?: System.currentTimeMillis()
                                        ),
                                        progress = domainProgress,
                                        upcomingManeuver = firstStep?.let {
                                            UpcomingManeuver(
                                                type = mapToDomainManeuverType(it.maneuverType),
                                                roadName = it.roadName,
                                                exitNumber = null,
                                                distanceToManeuver = firstDist,
                                                timeToManeuver = Duration.fromSeconds(firstDurationSec),
                                                direction = null
                                            )
                                        },
                                        nextManeuver = secondStep?.let {
                                            UpcomingManeuver(
                                                type = mapToDomainManeuverType(it.maneuverType),
                                                roadName = it.roadName,
                                                exitNumber = null,
                                                distanceToManeuver = Distance.fromMeters(it.distanceMeters),
                                                timeToManeuver = Duration.fromSeconds(secondDurationSec),
                                                direction = null
                                            )
                                        },
                                        eta = EtaEstimate(
                                            eta = System.currentTimeMillis() + durSec * 1000L,
                                            travelTime = Duration.fromSeconds(durSec),
                                            confidence = 0.75,
                                            isBasedOnLiveTraffic = false,
                                            adjustmentReason = "محاسبه آفلاین بر اساس کلاس معبر"
                                        ),
                                        remainingDistance = totalDist,
                                        remainingTime = Duration.fromSeconds(durSec),
                                        currentSoc = currentSoc,
                                        predictedArrivalSoc = predictedArrivalSoc,
                                        nextChargingStop = domainChargingStop,
                                        dataQuality = NavigationDataQuality.CACHED,
                                        gpsQuality = GpsQuality.GOOD,
                                        trafficStatus = TrafficStatus.UNAVAILABLE,
                                        weatherStatus = WeatherStatus.UNAVAILABLE,
                                        generationId = routeResult.generationId
                                    )
                                    AutomotiveNavigationStateHolder.updateNavigationState(domainState)

                                    navAdapter.convertToAutomotiveState(domainState)?.copy(
                                        predictedArrivalSocMin = predictedMin,
                                        predictedArrivalSocMax = predictedMax,
                                        predictionConfidence = confidence,
                                        socSourceLabel = socSource
                                    ) ?: buildUnverifiedOriginState(st.canonicalName, currentSoc, hasGps = true)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    buildUnverifiedOriginState(st.canonicalName, currentSoc, hasGps = true)
                                }
                            } else {
                                buildUnverifiedOriginState(st.canonicalName, currentSoc, hasGps = false)
                            }

                            onNavigationStateComputed?.invoke(navState)
                            screenManager.push(AutomotiveTurnByTurnScreen(carContext, navState))
                        }
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("ایستگاه‌های شارژ (با وضعیت اعتبارسنجی)")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemListBuilder.build())
            .build()
    }

    private fun buildUnverifiedOriginState(
        stationName: String,
        currentSoc: Double?,
        hasGps: Boolean
    ): AutomotiveNavigationState {
        return AutomotiveNavigationState(
            navigationStatus = AutomotiveNavigationStatus.ERROR,
            currentRoad = stationName,
            currentManeuver = null,
            nextManeuver = null,
            distanceToManeuver = null,
            remainingDistance = null,
            ETA = null,
            remainingTime = null,
            SOC = currentSoc,
            predictedArrivalSOC = null,
            predictedArrivalSocMin = null,
            predictedArrivalSocMax = null,
            predictionConfidence = null,
            socSourceLabel = AutomotiveNavigationStateHolder.socDataSource.value.name,
            nextChargingStop = null,
            dataQuality = AutomotiveDataQuality.UNAVAILABLE,
            GPSQuality = if (hasGps) AutomotiveGPSQuality.POOR else AutomotiveGPSQuality.UNAVAILABLE,
            trafficStatus = AutomotiveTrafficStatus.UNAVAILABLE,
            weatherStatus = AutomotiveWeatherStatus.UNAVAILABLE,
            sessionTimestampMs = System.currentTimeMillis()
        )
    }

    private fun estimateCorridorElevationDeltaMeters(origin: GeoCoordinate, dest: GeoCoordinate): Double {
        val nearestStart = IranOfflineRoadCorridors.nodes.minByOrNull { haversineMeters(origin, it.coordinate) }
        val nearestEnd = IranOfflineRoadCorridors.nodes.minByOrNull { haversineMeters(dest, it.coordinate) }
        val startElev = nearestStart?.elevationMeters ?: return 0.0
        val endElev = nearestEnd?.elevationMeters ?: return 0.0
        return endElev - startElev
    }

    private fun haversineMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun mapToDomainManeuverType(type: RealManeuverType?): ManeuverType {
        return when (type) {
            RealManeuverType.TURN_LEFT -> ManeuverType.TURN_LEFT
            RealManeuverType.TURN_RIGHT -> ManeuverType.TURN_RIGHT
            RealManeuverType.SHARP_LEFT -> ManeuverType.SHARP_LEFT
            RealManeuverType.SHARP_RIGHT -> ManeuverType.SHARP_RIGHT
            RealManeuverType.SLIGHT_LEFT -> ManeuverType.SLIGHT_LEFT
            RealManeuverType.SLIGHT_RIGHT -> ManeuverType.SLIGHT_RIGHT
            RealManeuverType.UTURN -> ManeuverType.U_TURN
            RealManeuverType.RAMP_EXIT -> ManeuverType.TAKE_EXIT
            RealManeuverType.MERGE -> ManeuverType.MERGE
            RealManeuverType.ROUNDABOUT -> ManeuverType.ENTER_ROUNDABOUT
            RealManeuverType.ARRIVE -> ManeuverType.ARRIVE
            else -> ManeuverType.CONTINUE
        }
    }
}
