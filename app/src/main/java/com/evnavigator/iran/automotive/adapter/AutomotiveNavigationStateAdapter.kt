package com.evnavigator.iran.automotive.adapter

import com.evnavigator.iran.domain.model.*
import com.evnavigator.iran.domain.model.navigation.GpsQuality
import com.evnavigator.iran.domain.model.navigation.NavigationDataQuality
import com.evnavigator.iran.domain.model.navigation.NavigationState
import com.evnavigator.iran.domain.model.navigation.NavigationStatus as DomainNavigationStatus
import com.evnavigator.iran.domain.model.navigation.TrafficStatus
import com.evnavigator.iran.domain.model.navigation.WeatherStatus
import com.evnavigator.iran.domain.model.navigation.maneuver.Maneuver
import com.evnavigator.iran.domain.model.navigation.maneuver.ManeuverType

/**
 * Adapter for converting domain NavigationState into automotive-compatible state.
 * Strictly maps domain data quality, GPS freshness/accuracy, traffic status, and weather status
 * without Promoting offline or cached routes to LIVE (Item 36 & 37).
 */
class AutomotiveNavigationStateAdapter {
    companion object {
        private const val TAG = "AutomotiveNavAdapter"
        private const val MAX_LIVE_GPS_AGE_MS = 15_000L
        private const val MAX_STALE_GPS_AGE_MS = 45_000L
    }

    fun convertToAutomotiveState(navigationState: NavigationState?): AutomotiveNavigationState {
        if (navigationState == null) {
            return AutomotiveNavigationState(
                navigationStatus = AutomotiveNavigationStatus.IDLE,
                currentRoad = null,
                currentManeuver = null,
                nextManeuver = null,
                distanceToManeuver = null,
                remainingDistance = null,
                ETA = null,
                remainingTime = null,
                SOC = null,
                predictedArrivalSOC = null,
                nextChargingStop = null,
                dataQuality = AutomotiveDataQuality.UNKNOWN,
                GPSQuality = AutomotiveGPSQuality.UNKNOWN,
                trafficStatus = AutomotiveTrafficStatus.UNKNOWN,
                weatherStatus = AutomotiveWeatherStatus.UNKNOWN
            )
        }

        val automotiveStatus = convertNavigationStatus(navigationState.status)

        val currentManeuver = navigationState.currentManeuver?.let {
            convertManeuver(
                Maneuver(
                    type = it.type,
                    roadName = it.roadName,
                    exitNumber = it.exitNumber,
                    distanceToManeuver = it.distanceToManeuver,
                    timeToManeuver = it.timeToManeuver,
                    direction = it.direction
                )
            )
        }

        val nextManeuver = navigationState.nextManeuver?.let {
            convertManeuver(
                Maneuver(
                    type = it.type,
                    roadName = it.roadName,
                    exitNumber = it.exitNumber,
                    distanceToManeuver = it.distanceToManeuver,
                    timeToManeuver = it.timeToManeuver,
                    direction = it.direction
                )
            )
        }

        val distanceToManeuver = navigationState.currentManeuver?.distanceToManeuver
        val remainingDistance = navigationState.remainingDistance
            ?: navigationState.progress?.let { it.totalDistance - it.distanceTraveled }
        val currentRoad = navigationState.currentManeuver?.roadName
            ?: navigationState.upcomingManeuver?.roadName
            ?: navigationState.route?.segments?.firstOrNull()?.roadName
        val ETA = navigationState.eta?.eta
        val remainingTime = navigationState.remainingTime?.seconds
            ?: navigationState.eta?.travelTime?.seconds

        val currentSOC = navigationState.currentSoc
        val predictedArrivalSOC = navigationState.predictedArrivalSoc
        val nextChargingStop = navigationState.nextChargingStop?.let { convertChargingStop(it) }

        val dataQuality = determineDataQuality(navigationState)
        val gpsQuality = determineGPSQuality(navigationState)
        val trafficStatus = determineTrafficStatus(navigationState)
        val weatherStatus = determineWeatherStatus(navigationState)

        return AutomotiveNavigationState(
            navigationStatus = automotiveStatus,
            currentRoad = currentRoad,
            currentManeuver = currentManeuver,
            nextManeuver = nextManeuver,
            distanceToManeuver = distanceToManeuver,
            remainingDistance = remainingDistance,
            ETA = ETA,
            remainingTime = remainingTime,
            SOC = currentSOC,
            predictedArrivalSOC = predictedArrivalSOC,
            nextChargingStop = nextChargingStop,
            dataQuality = dataQuality,
            GPSQuality = gpsQuality,
            trafficStatus = trafficStatus,
            weatherStatus = weatherStatus
        )
    }

    private fun convertNavigationStatus(status: DomainNavigationStatus): AutomotiveNavigationStatus {
        return when (status) {
            DomainNavigationStatus.IDLE -> AutomotiveNavigationStatus.IDLE
            DomainNavigationStatus.STARTING -> AutomotiveNavigationStatus.STARTING
            DomainNavigationStatus.NAVIGATING -> AutomotiveNavigationStatus.NAVIGATING
            DomainNavigationStatus.RECALCULATING -> AutomotiveNavigationStatus.RECALCULATING
            DomainNavigationStatus.GPS_DEGRADED -> AutomotiveNavigationStatus.GPS_DEGRADED
            DomainNavigationStatus.OFFLINE -> AutomotiveNavigationStatus.OFFLINE
            DomainNavigationStatus.PAUSED -> AutomotiveNavigationStatus.PAUSED
            DomainNavigationStatus.ARRIVED -> AutomotiveNavigationStatus.ARRIVED
            DomainNavigationStatus.STOPPED -> AutomotiveNavigationStatus.STOPPED
            DomainNavigationStatus.ERROR -> AutomotiveNavigationStatus.ERROR
        }
    }

    private fun convertManeuver(maneuver: Maneuver): AutomotiveManeuver {
        val maneuverType = when (maneuver.type) {
            ManeuverType.CONTINUE -> AutomotiveManeuverType.STRAIGHT
            ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT -> AutomotiveManeuverType.TURN_LEFT
            ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT -> AutomotiveManeuverType.TURN_RIGHT
            ManeuverType.KEEP_LEFT, ManeuverType.SLIGHT_LEFT -> AutomotiveManeuverType.KEEP_LEFT
            ManeuverType.KEEP_RIGHT, ManeuverType.SLIGHT_RIGHT -> AutomotiveManeuverType.KEEP_RIGHT
            ManeuverType.MERGE -> AutomotiveManeuverType.MERGE_RIGHT
            ManeuverType.U_TURN -> AutomotiveManeuverType.U_TURN
            ManeuverType.ENTER_ROUNDABOUT, ManeuverType.EXIT_ROUNDABOUT -> AutomotiveManeuverType.ROUNDABOUT
            ManeuverType.TAKE_RAMP -> AutomotiveManeuverType.RAMP_RIGHT
            ManeuverType.TAKE_EXIT -> AutomotiveManeuverType.EXIT_RIGHT
            ManeuverType.ARRIVE -> AutomotiveManeuverType.DESTINATION
        }

        return AutomotiveManeuver(
            type = maneuverType,
            distanceToManeuver = maneuver.distanceToManeuver,
            roadName = maneuver.roadName,
            exitNumber = maneuver.exitNumber
        )
    }

    private fun convertChargingStop(stop: com.evnavigator.iran.domain.model.navigation.ChargingStop): AutomotiveChargingStop {
        val actualConnector = stop.charger.connectors.firstOrNull() ?: com.evnavigator.iran.domain.model.ChargingConnector(
            type = com.evnavigator.iran.domain.model.ConnectorType.UNKNOWN,
            power = stop.charger.maxPower.coerceAtLeast(0.0),
            voltage = 0.0,
            current = 0.0,
            isAvailable = stop.charger.status == com.evnavigator.iran.domain.model.ChargerStatus.AVAILABLE
        )
        return AutomotiveChargingStop(
            charger = stop.charger,
            distanceToStop = stop.distanceToStop,
            ETA = System.currentTimeMillis() + (stop.timeToStop.seconds * 1000),
            predictedArrivalSOC = stop.estimatedArrivalSoc.percentage,
            connector = actualConnector,
            chargingCapability = com.evnavigator.iran.domain.model.Power.ofKilowatts(stop.charger.maxPower.coerceAtLeast(0.0)),
            targetSOC = stop.targetSoc.percentage,
            chargingDurationEstimate = stop.estimatedChargingDuration.seconds
        )
    }

    /**
     * Strictly maps domain [NavigationDataQuality] and location timestamp age (Item 36).
     * Never promotes a route without verified live telemetry to LIVE.
     */
    private fun determineDataQuality(navigationState: NavigationState): AutomotiveDataQuality {
        if (navigationState.status == DomainNavigationStatus.ERROR) {
            return AutomotiveDataQuality.UNAVAILABLE
        }
        if (navigationState.status == DomainNavigationStatus.OFFLINE) {
            return AutomotiveDataQuality.OFFLINE
        }
        val loc = navigationState.matchedLocation
        if (loc != null) {
            val ageMs = System.currentTimeMillis() - loc.timestampEpochMs
            if (ageMs > MAX_STALE_GPS_AGE_MS) {
                return AutomotiveDataQuality.STALE
            }
        }
        return when (navigationState.dataQuality) {
            NavigationDataQuality.LIVE -> {
                if (loc != null && (System.currentTimeMillis() - loc.timestampEpochMs) <= MAX_LIVE_GPS_AGE_MS) {
                    AutomotiveDataQuality.LIVE
                } else {
                    AutomotiveDataQuality.CACHED
                }
            }
            NavigationDataQuality.CACHED -> AutomotiveDataQuality.CACHED
            NavigationDataQuality.PARTIAL -> AutomotiveDataQuality.STALE
            NavigationDataQuality.UNAVAILABLE -> AutomotiveDataQuality.UNAVAILABLE
            NavigationDataQuality.UNKNOWN -> AutomotiveDataQuality.UNKNOWN
        }
    }

    /**
     * Determines GPS quality based on domain [GpsQuality], horizontal accuracy, and fix age (Item 9 & 37).
     */
    private fun determineGPSQuality(navigationState: NavigationState): AutomotiveGPSQuality {
        val loc = navigationState.matchedLocation
        if (loc != null) {
            val ageMs = System.currentTimeMillis() - loc.timestampEpochMs
            if (ageMs > MAX_STALE_GPS_AGE_MS) {
                return AutomotiveGPSQuality.LOST
            }
            val acc = loc.accuracyMeters
            if (acc != null && acc > 50f) {
                return AutomotiveGPSQuality.POOR
            }
            if (acc != null && acc > 25f) {
                return AutomotiveGPSQuality.DEGRADED
            }
        }
        return when (navigationState.gpsQuality) {
            GpsQuality.EXCELLENT, GpsQuality.GOOD -> AutomotiveGPSQuality.GOOD
            GpsQuality.MODERATE, GpsQuality.DEGRADED -> AutomotiveGPSQuality.DEGRADED
            GpsQuality.POOR -> AutomotiveGPSQuality.POOR
            GpsQuality.UNAVAILABLE -> AutomotiveGPSQuality.UNAVAILABLE
            GpsQuality.UNKNOWN -> AutomotiveGPSQuality.UNKNOWN
        }
    }

    private fun determineTrafficStatus(navigationState: NavigationState): AutomotiveTrafficStatus {
        return when (navigationState.trafficStatus) {
            TrafficStatus.NORMAL,
            TrafficStatus.SLOW,
            TrafficStatus.HEAVY,
            TrafficStatus.STUCK -> {
                if (navigationState.eta?.isBasedOnLiveTraffic == true) {
                    AutomotiveTrafficStatus.LIVE
                } else {
                    AutomotiveTrafficStatus.HISTORICAL
                }
            }
            TrafficStatus.UNAVAILABLE -> AutomotiveTrafficStatus.UNAVAILABLE
            TrafficStatus.UNKNOWN -> AutomotiveTrafficStatus.UNKNOWN
        }
    }

    private fun determineWeatherStatus(navigationState: NavigationState): AutomotiveWeatherStatus {
        return when (navigationState.weatherStatus) {
            WeatherStatus.CLEAR,
            WeatherStatus.PARTLY_CLOUDY,
            WeatherStatus.OVERCAST,
            WeatherStatus.RAINY,
            WeatherStatus.SNOWY,
            WeatherStatus.FOGGY,
            WeatherStatus.WINDY,
            WeatherStatus.STORMY -> AutomotiveWeatherStatus.LIVE
            WeatherStatus.UNAVAILABLE -> AutomotiveWeatherStatus.UNAVAILABLE
            WeatherStatus.UNKNOWN -> AutomotiveWeatherStatus.UNKNOWN
        }
    }
}

data class AutomotiveNavigationState(
    val navigationStatus: AutomotiveNavigationStatus,
    val currentRoad: String?,
    val currentManeuver: AutomotiveManeuver?,
    val nextManeuver: AutomotiveManeuver?,
    val distanceToManeuver: Distance?,
    val remainingDistance: Distance?,
    val ETA: Long?,
    val remainingTime: Long?,
    val SOC: Double?,
    val predictedArrivalSOC: Double?,
    val nextChargingStop: AutomotiveChargingStop?,
    val dataQuality: AutomotiveDataQuality,
    val GPSQuality: AutomotiveGPSQuality,
    val trafficStatus: AutomotiveTrafficStatus,
    val weatherStatus: AutomotiveWeatherStatus,
    val predictedArrivalSocMin: Double? = null,
    val predictedArrivalSocMax: Double? = null,
    val predictionConfidence: Double? = null,
    val socSourceLabel: String? = null,
    val sessionTimestampMs: Long = System.currentTimeMillis()
)

enum class AutomotiveNavigationStatus {
    IDLE,
    STARTING,
    NAVIGATING,
    RECALCULATING,
    PAUSED,
    ARRIVED,
    STOPPED,
    GPS_DEGRADED,
    OFFLINE,
    ERROR,
    UNKNOWN
}

enum class AutomotiveManeuverType {
    UNKNOWN,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    KEEP_LEFT,
    KEEP_RIGHT,
    MERGE_LEFT,
    MERGE_RIGHT,
    U_TURN,
    ROUNDABOUT,
    RAMP_LEFT,
    RAMP_RIGHT,
    EXIT_LEFT,
    EXIT_RIGHT,
    DESTINATION
}

data class AutomotiveManeuver(
    val type: AutomotiveManeuverType,
    val distanceToManeuver: Distance?,
    val roadName: String?,
    val exitNumber: String?
)

data class AutomotiveChargingStop(
    val charger: Charger,
    val distanceToStop: Distance,
    val ETA: Long,
    val predictedArrivalSOC: Double,
    val connector: ChargingConnector,
    val chargingCapability: Power,
    val targetSOC: Double,
    val chargingDurationEstimate: Long
)

enum class AutomotiveDataQuality {
    LIVE,
    CACHED,
    STALE,
    OFFLINE,
    UNAVAILABLE,
    UNKNOWN
}

enum class AutomotiveGPSQuality {
    GOOD,
    DEGRADED,
    POOR,
    LOST,
    UNAVAILABLE,
    UNKNOWN
}

enum class AutomotiveTrafficStatus {
    LIVE,
    HISTORICAL,
    UNAVAILABLE,
    UNKNOWN
}

enum class AutomotiveWeatherStatus {
    LIVE,
    FORECAST,
    UNAVAILABLE,
    UNKNOWN
}
