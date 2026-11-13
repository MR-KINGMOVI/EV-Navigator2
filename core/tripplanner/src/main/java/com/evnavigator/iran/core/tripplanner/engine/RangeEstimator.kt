package com.evnavigator.iran.core.tripplanner.engine

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.Route
import com.evnavigator.iran.core.energy.EnergyEngine
import com.evnavigator.iran.core.energy.model.BatteryState
import com.evnavigator.iran.core.energy.model.VehicleProfile

/**
 * Wraps core/energy to predict SOC along a route using actual elevation, road profile, and user vehicle parameters.
 */
class RangeEstimator(
    private val energyEngine: EnergyEngine
) {
    /**
     * Given a route and a starting SOC, returns the predicted SOC (percent)
     * at every RouteSegment boundary along the route, using the full EnergyEngine physics.
     * Does not account for charging stops — this is a "if we never stopped" projection used
     * to find where SOC crosses safety buffer thresholds.
     */
    suspend fun projectSocAlongRoute(
        route: Route,
        startSocPercent: Double,
        vehicleProfile: VehicleProfile,
        minSafeSocPercent: Double = 10.0,
        maxUsableSocPercent: Double = 100.0
    ): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>() // (cumulative distance meters, predicted SOC percent)

        if (route.segments.isEmpty()) {
            return emptyList()
        }

        val initialBatteryState = BatteryState.fromInitialSoC(
            usableCapacityWh = vehicleProfile.usableBatteryCapacityWh,
            initialSoCPercent = startSocPercent,
            minimumOperationalSoCPercent = minSafeSocPercent,
            maximumUsableSoCPercent = maxUsableSocPercent
        )

        val routePoints = mutableListOf<GeoCoordinate>()
        routePoints.add(route.segments.first().startCoordinate)
        route.segments.forEach { routePoints.add(it.endCoordinate) }

        // Initial point at km 0
        result.add(Pair(0.0, startSocPercent))

        val routeEnergyResult = energyEngine.calculateRouteEnergy(
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            initialBatteryState = initialBatteryState
        )

        var cumulativeDistanceMeters = 0.0
        for (seg in routeEnergyResult.segmentResults) {
            cumulativeDistanceMeters += seg.distanceMeters
            result.add(Pair(cumulativeDistanceMeters, seg.batteryStateAfterSegment.currentSoCPercent))
        }

        return result
    }

    /**
     * Returns the cumulative distance (meters) along the route at which SOC
     * is predicted to first drop to or below thresholdSocPercent, or null if
     * it never does within the route.
     */
    suspend fun findDistanceWhereSocReaches(
        route: Route,
        startSocPercent: Double,
        vehicleProfile: VehicleProfile,
        thresholdSocPercent: Double
    ): Double? {
        val socProjection = projectSocAlongRoute(
            route = route,
            startSocPercent = startSocPercent,
            vehicleProfile = vehicleProfile,
            minSafeSocPercent = thresholdSocPercent
        )

        for ((distance, soc) in socProjection) {
            if (soc <= thresholdSocPercent) {
                return distance
            }
        }

        return null
    }
}
