package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.elevation.ElevationEngine
import com.evnavigator.iran.core.energy.EnergyEngineImpl
import com.evnavigator.iran.core.energy.ReachableRangeResult
import com.evnavigator.iran.core.energy.RouteFeasibilityResult
import com.evnavigator.iran.core.roadprofile.RoadProfileEngine

/**
 * Calculator for complete route energy calculations based on physical equations.
 * If ElevationEngine or RoadProfileEngine are absent, fails explicitly instead of
 * silently returning unreachable/stub data.
 */
object RouteEnergyCalculator {

    private const val DEFAULT_SPEED_MPS = 25.0 // 90 km/h highway speed reference

    /**
     * Calculates total route energy with segment-by-segment breakdown.
     * Requires valid elevation and road profile engines.
     */
    suspend fun calculateRouteEnergy(
        routePoints: List<GeoCoordinate>,
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity = EnergyModelFidelity.FULL_PHYSICS,
        elevationEngine: ElevationEngine? = null,
        roadProfileEngine: RoadProfileEngine? = null
    ): RouteEnergyResult {
        if (elevationEngine == null || roadProfileEngine == null) {
            val totalDist = if (routePoints.size >= 2) {
                routePoints.zipWithNext().sumOf { it.first.distanceTo(it.second) }
            } else {
                0.0
            }

            return RouteEnergyResult(
                totalDistanceMeters = totalDist,
                estimatedTotalTravelTimeSeconds = if (DEFAULT_SPEED_MPS > 0.0) totalDist / DEFAULT_SPEED_MPS else 0.0,
                totalEnergyConsumedWh = 0.0,
                totalEnergyRecoveredWh = 0.0,
                netEnergyDeltaWh = 0.0,
                initialBatteryState = initialBatteryState,
                finalBatteryState = initialBatteryState,
                minimumSoCPercent = initialBatteryState.currentSoCPercent,
                maximumSoCPercent = initialBatteryState.currentSoCPercent,
                minimumSoEWh = initialBatteryState.currentSoEWh.wattHours,
                maximumSoEWh = initialBatteryState.currentSoEWh.wattHours,
                segmentResults = emptyList(),
                modelFidelity = modelFidelity,
                dataQuality = DataQuality.UNKNOWN,
                warnings = listOf("Elevation and road-profile engines are required"),
                isReachable = false,
                reasonForUnreachability = "Elevation and road-profile engines are required"
            )
        }

        val engine = EnergyEngineImpl(
            elevationEngine = elevationEngine,
            roadProfileEngine = roadProfileEngine
        )

        return engine.calculateRouteEnergy(
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            initialBatteryState = initialBatteryState,
            modelFidelity = modelFidelity
        )
    }

    /**
     * Evaluates whether a route is reachable with current battery state.
     */
    suspend fun evaluateRouteFeasibility(
        routePoints: List<GeoCoordinate>,
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity = EnergyModelFidelity.FULL_PHYSICS,
        elevationEngine: ElevationEngine? = null,
        roadProfileEngine: RoadProfileEngine? = null
    ): RouteFeasibilityResult {
        if (elevationEngine == null || roadProfileEngine == null) {
            return RouteFeasibilityResult(
                isReachable = false,
                reasonForUnreachability = "Elevation and road-profile engines are required",
                estimatedEnergyNeededWh = 0.0,
                estimatedRangeKm = 0.0,
                minimumSoCPercent = initialBatteryState.currentSoCPercent,
                modelFidelity = modelFidelity
            )
        }

        val engine = EnergyEngineImpl(
            elevationEngine = elevationEngine,
            roadProfileEngine = roadProfileEngine
        )

        return engine.evaluateRouteFeasibility(
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            initialBatteryState = initialBatteryState,
            modelFidelity = modelFidelity
        )
    }

    /**
     * Estimates the reachable range from current position with given battery state.
     */
    suspend fun estimateReachableRange(
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity = EnergyModelFidelity.FULL_PHYSICS
    ): ReachableRangeResult {
        val usableCapacityWh = initialBatteryState.usableCapacityWh.wattHours
        val minSafeSoCPercent = initialBatteryState.minimumOperationalSoCPercent
        val reserveWh = initialBatteryState.reserveWh.wattHours

        val availableEnergyWh = (initialBatteryState.currentSoEWh.wattHours -
                (usableCapacityWh * (minSafeSoCPercent / 100.0)) -
                reserveWh).coerceAtLeast(0.0)

        val massKg = if (vehicleProfile.totalOperatingMassKg.kilograms > 0.0) {
            vehicleProfile.totalOperatingMassKg.kilograms
        } else {
            vehicleProfile.curbMassKg.kilograms + vehicleProfile.payloadMassKg.kilograms
        }
        val cd = vehicleProfile.dragCoefficient
        val areaM2 = vehicleProfile.frontalAreaM2
        val crr = vehicleProfile.rollingResistanceCoefficient

        // Nominal baseline (standard 90 km/h cruising, level road, 0m sea level reference)
        val physicsNominal = SegmentPhysics.calculate(
            horizontalDistanceMeters = 1000.0,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = DEFAULT_SPEED_MPS,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr,
            drivetrainEfficiency = vehicleProfile.uphillEfficiency.fraction,
            downhillEfficiency = vehicleProfile.downhillEfficiency.fraction,
            auxiliaryPowerWatts = vehicleProfile.fixedAuxiliaryPowerW.watts
        )
        val nominalWhPerKm = physicsNominal.netWh.coerceAtLeast(1.0)
        val nominalRangeMeters = (availableEnergyWh / nominalWhPerKm) * 1000.0

        // Optimistic scenario: eco-cruising (70 km/h ~ 19.44 m/s), low auxiliary load, minimal Crr
        val physicsOptimistic = SegmentPhysics.calculate(
            horizontalDistanceMeters = 1000.0,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = 19.44,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = (crr * 0.95).coerceAtLeast(0.005),
            drivetrainEfficiency = vehicleProfile.uphillEfficiency.fraction,
            downhillEfficiency = vehicleProfile.downhillEfficiency.fraction,
            auxiliaryPowerWatts = (vehicleProfile.fixedAuxiliaryPowerW.watts * 0.7).coerceAtLeast(50.0)
        )
        val optimisticWhPerKm = physicsOptimistic.netWh.coerceAtLeast(1.0)
        val optimisticRangeMeters = (availableEnergyWh / optimisticWhPerKm) * 1000.0

        // Pessimistic scenario: fast highway (110 km/h ~ 30.56 m/s), high auxiliary/HVAC (+500W), adverse Crr
        val physicsPessimistic = SegmentPhysics.calculate(
            horizontalDistanceMeters = 1000.0,
            gradePercent = 0.0,
            absoluteAltitudeMeters = 0.0,
            speedMps = 30.56,
            vehicleMassKg = massKg,
            dragCoefficient = cd,
            frontalAreaM2 = areaM2,
            rollingResistanceCoeff = crr * 1.15,
            drivetrainEfficiency = vehicleProfile.uphillEfficiency.fraction,
            downhillEfficiency = vehicleProfile.downhillEfficiency.fraction,
            auxiliaryPowerWatts = vehicleProfile.fixedAuxiliaryPowerW.watts + 500.0
        )
        val pessimisticWhPerKm = physicsPessimistic.netWh.coerceAtLeast(1.0)
        val pessimisticRangeMeters = (availableEnergyWh / pessimisticWhPerKm) * 1000.0

        return ReachableRangeResult(
            estimatedRangeMeters = nominalRangeMeters,
            estimatedEnergyConsumedWh = availableEnergyWh,
            estimatedTravelTimeSeconds = if (DEFAULT_SPEED_MPS > 0) nominalRangeMeters / DEFAULT_SPEED_MPS else 0.0,
            minimumSoCPercent = minSafeSoCPercent,
            modelFidelity = modelFidelity,
            optimisticRangeMeters = optimisticRangeMeters,
            nominalRangeMeters = nominalRangeMeters,
            pessimisticRangeMeters = pessimisticRangeMeters
        )
    }

    /**
     * Calculates route statistics from segment results
     */
    fun calculateRouteStatistics(
        segmentResults: List<SegmentEnergyResult>
    ): RouteStatistics {
        if (segmentResults.isEmpty()) {
            return RouteStatistics(
                totalDistanceMeters = 0.0,
                totalEnergyConsumedWh = 0.0,
                totalEnergyRecoveredWh = 0.0,
                netEnergyDeltaWh = 0.0,
                minimumSoCPercent = 100.0,
                maximumSoCPercent = 0.0,
                minimumSoEWh = 0.0,
                maximumSoEWh = 0.0
            )
        }

        val totalDistanceMeters = segmentResults.sumOf { it.distanceMeters }
        val totalEnergyConsumedWh = segmentResults.sumOf { it.energyConsumedWh }
        val totalEnergyRecoveredWh = segmentResults.sumOf { it.energyRecoveredWh }
        val netEnergyDeltaWh = totalEnergyConsumedWh - totalEnergyRecoveredWh

        val minSoC = segmentResults.minOfOrNull { it.batteryStateAfterSegment.currentSoCPercent } ?: 0.0
        val maxSoC = segmentResults.maxOfOrNull { it.batteryStateAfterSegment.currentSoCPercent } ?: 100.0
        val minSoE = segmentResults.minOfOrNull { it.batteryStateAfterSegment.currentSoEWh.wattHours } ?: 0.0
        val maxSoE = segmentResults.maxOfOrNull { it.batteryStateAfterSegment.currentSoEWh.wattHours } ?: 0.0

        return RouteStatistics(
            totalDistanceMeters = totalDistanceMeters,
            totalEnergyConsumedWh = totalEnergyConsumedWh,
            totalEnergyRecoveredWh = totalEnergyRecoveredWh,
            netEnergyDeltaWh = netEnergyDeltaWh,
            minimumSoCPercent = minSoC,
            maximumSoCPercent = maxSoC,
            minimumSoEWh = minSoE,
            maximumSoEWh = maxSoE
        )
    }
}

/**
 * Represents route statistics calculated from segment results
 */
data class RouteStatistics(
    val totalDistanceMeters: Double,
    val totalEnergyConsumedWh: Double,
    val totalEnergyRecoveredWh: Double,
    val netEnergyDeltaWh: Double,
    val minimumSoCPercent: Double,
    val maximumSoCPercent: Double,
    val minimumSoEWh: Double,
    val maximumSoEWh: Double
)
