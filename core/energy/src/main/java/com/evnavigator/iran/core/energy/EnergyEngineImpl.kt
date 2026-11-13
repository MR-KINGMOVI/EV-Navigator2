package com.evnavigator.iran.core.energy

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.elevation.ElevationEngine
import com.evnavigator.iran.core.energy.model.*
import com.evnavigator.iran.core.roadprofile.RoadProfileData
import com.evnavigator.iran.core.roadprofile.RoadProfileEngine
import com.evnavigator.iran.core.roadprofile.RoadType
import com.evnavigator.iran.core.roadprofile.SurfaceType
import java.util.Locale

/**
 * Implementation of EnergyEngine that provides pure EV energy consumption calculations
 * using longitudinal vehicle physics (SegmentPhysics).
 */
class EnergyEngineImpl(
    private val elevationEngine: ElevationEngine,
    private val roadProfileEngine: RoadProfileEngine
) : EnergyEngine {

    override suspend fun calculateSegmentEnergy(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        vehicleProfile: VehicleProfile,
        batteryState: BatteryState,
        roadProfileData: RoadProfileData,
        elevationPoint: ElevationPoint?,
        averageSpeedMps: Double
    ): SegmentEnergyResult {
        // Validate inputs: zero or negative/NaN/infinite speed or non-positive distance
        if (averageSpeedMps <= 0.0 || averageSpeedMps.isNaN() || averageSpeedMps.isInfinite()) {
            return SegmentEnergyResult.createInvalidSegment(
                startCoordinate = startCoordinate,
                endCoordinate = endCoordinate,
                modelFidelity = EnergyModelFidelity.FULL_PHYSICS,
                dataQuality = DataQuality.LOW
            )
        }

        val distanceMeters = startCoordinate.distanceTo(endCoordinate)
        if (distanceMeters <= 0.0 || distanceMeters.isNaN() || distanceMeters.isInfinite()) {
            return SegmentEnergyResult.createInvalidSegment(
                startCoordinate = startCoordinate,
                endCoordinate = endCoordinate,
                modelFidelity = EnergyModelFidelity.FULL_PHYSICS,
                dataQuality = DataQuality.LOW
            )
        }

        val massKg = vehicleProfile.totalOperatingMassKg.kilograms
        val cd = vehicleProfile.dragCoefficient
        val areaM2 = vehicleProfile.frontalAreaM2

        // If mass, drag coefficient, and frontal area are valid, compute with SegmentPhysics
        if (massKg > 0.0 && cd > 0.0 && areaM2 > 0.0) {
            val altM = elevationPoint?.elevationMeters?.takeIf { !it.isNaN() } ?: 0.0
            val physics = SegmentPhysics.calculate(
                horizontalDistanceMeters = distanceMeters,
                gradePercent = roadProfileData.gradientPercent,
                absoluteAltitudeMeters = altM,
                speedMps = averageSpeedMps,
                vehicleMassKg = massKg,
                dragCoefficient = cd,
                frontalAreaM2 = areaM2,
                rollingResistanceCoeff = vehicleProfile.rollingResistanceCoefficient,
                drivetrainEfficiency = vehicleProfile.uphillEfficiency.fraction,
                downhillEfficiency = vehicleProfile.downhillEfficiency.fraction,
                auxiliaryPowerWatts = vehicleProfile.fixedAuxiliaryPowerW.watts
            )

            val updatedBattery = batteryState.updateWithEnergyDelta(-physics.netWh)

            val isGradeUnk = roadProfileData.gradientPercent.isNaN() || !roadProfileData.gradientPercent.isFinite()
            val segQuality = if (isGradeUnk || elevationPoint == null || !elevationPoint.isValid()) DataQuality.LOW else DataQuality.HIGH

            return SegmentEnergyResult(
                startCoordinate = startCoordinate,
                endCoordinate = endCoordinate,
                distanceMeters = distanceMeters,
                averageSpeedMps = averageSpeedMps,
                estimatedTravelTimeSeconds = physics.travelTimeSeconds,
                energyConsumedWh = physics.drawWh,
                energyRecoveredWh = physics.regenWh,
                netEnergyDeltaWh = physics.netWh,
                rollingResistanceEnergyWh = physics.rollingResistanceWh,
                aerodynamicDragEnergyWh = physics.aerodynamicDragWh,
                elevationEnergyWh = physics.gradeWh,
                accelerationEnergyWh = 0.0,
                decelerationEnergyWh = 0.0,
                auxiliaryEnergyWh = physics.auxiliaryWh,
                gradePercent = if (isGradeUnk) 0.0 else roadProfileData.gradientPercent,
                elevationGainMeters = if (physics.riseMeters > 0.0) physics.riseMeters else 0.0,
                elevationLossMeters = if (physics.riseMeters < 0.0) kotlin.math.abs(physics.riseMeters) else 0.0,
                batteryStateAfterSegment = updatedBattery,
                modelFidelity = EnergyModelFidelity.FULL_PHYSICS,
                dataQuality = segQuality,
                demUncertaintyWh = physics.demUncertaintyWh,
                isGradeUnknown = isGradeUnk
            )
        } else {
            // Fallback to speedConsumptionCurve when mass/Cd/frontal area are missing
            val baselineWhPerKm = vehicleProfile.speedConsumptionCurve.getConsumptionAtSafe(
                Speed(averageSpeedMps)
            )
            val tractiveWh = baselineWhPerKm * (distanceMeters / 1000.0)
            val travelTimeSec = distanceMeters / averageSpeedMps
            val auxWh = (vehicleProfile.fixedAuxiliaryPowerW.watts * travelTimeSec) / 3600.0

            val isGradeUnk = !roadProfileData.gradientPercent.isFinite()
            val gradePercent = if (isGradeUnk) 0.0 else roadProfileData.gradientPercent.coerceIn(-30.0, 30.0)
            val fallbackMassKg = vehicleProfile.totalOperatingMassKg.kilograms.takeIf { it.isFinite() && it > 0.0 }
                ?: (vehicleProfile.curbMassKg.kilograms + vehicleProfile.payloadMassKg.kilograms).takeIf { it.isFinite() && it > 0.0 }
                ?: 1850.0
            val rise = distanceMeters * (gradePercent / 100.0)
            val gradeWh = (fallbackMassKg * 9.80665 * rise) / 3600.0
            val segDemUncertaintyWh = if (isGradeUnk) {
                val potentialRiseM = (distanceMeters * 0.025).coerceAtMost(80.0)
                (fallbackMassKg * 9.80665 * potentialRiseM) / (3600.0 * 0.88)
            } else 0.0

            val drawWh = tractiveWh + auxWh + (if (gradeWh > 0) gradeWh else 0.0)
            val regenWh = if (gradeWh < 0) -gradeWh * 0.70 else 0.0
            val netWh = drawWh - regenWh
            val updatedBattery = batteryState.updateWithEnergyDelta(-netWh)

            return SegmentEnergyResult(
                startCoordinate = startCoordinate,
                endCoordinate = endCoordinate,
                distanceMeters = distanceMeters,
                averageSpeedMps = averageSpeedMps,
                estimatedTravelTimeSeconds = travelTimeSec,
                energyConsumedWh = drawWh,
                energyRecoveredWh = regenWh,
                netEnergyDeltaWh = netWh,
                rollingResistanceEnergyWh = 0.0,
                aerodynamicDragEnergyWh = 0.0,
                elevationEnergyWh = gradeWh,
                accelerationEnergyWh = 0.0,
                decelerationEnergyWh = 0.0,
                auxiliaryEnergyWh = auxWh,
                gradePercent = gradePercent,
                elevationGainMeters = if (rise > 0) rise else 0.0,
                elevationLossMeters = if (rise < 0) kotlin.math.abs(rise) else 0.0,
                batteryStateAfterSegment = updatedBattery,
                modelFidelity = EnergyModelFidelity.BASIC,
                dataQuality = DataQuality.LOW,
                demUncertaintyWh = segDemUncertaintyWh,
                isGradeUnknown = isGradeUnk
            )
        }
    }

    override suspend fun calculateRouteEnergy(
        routePoints: List<GeoCoordinate>,
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity
    ): RouteEnergyResult {
        if (routePoints.size < 2) {
            return RouteEnergyResult(
                totalDistanceMeters = 0.0,
                estimatedTotalTravelTimeSeconds = 0.0,
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
                dataQuality = DataQuality.HIGH,
                isReachable = true
            )
        }

        val capacityWh = initialBatteryState.usableCapacityWh.wattHours
        var currentBattery = initialBatteryState
        var runningSoEWh = initialBatteryState.currentSoEWh.wattHours
        var minRunningSoEWh = runningSoEWh

        var totalDistanceMeters = 0.0
        var totalTravelTimeSec = 0.0
        var totalConsumedWh = 0.0
        var totalRecoveredWh = 0.0
        var totalDemUncertaintyWh = 0.0
        var minSoC = initialBatteryState.currentSoCPercent
        var maxSoC = initialBatteryState.currentSoCPercent
        val segments = mutableListOf<SegmentEnergyResult>()
        val routeWarnings = mutableListOf<String>()
        var overallDataQuality = DataQuality.HIGH

        for (i in 0 until routePoints.size - 1) {
            val start = routePoints[i]
            val end = routePoints[i + 1]
            val dist = start.distanceTo(end)
            if (dist <= 0.0) continue

            var grade = 0.0
            var isGradeUnknown = false
            var altitude = 0.0
            var isAltitudeUnknown = false
            var speedLimitKmh: Int? = null

            try {
                val rawGrade = elevationEngine.calculateGrade(start, end)
                if (rawGrade.isNaN() || !rawGrade.isFinite()) {
                    isGradeUnknown = true
                    grade = Double.NaN
                    routeWarnings.add("Elevation grade is UNKNOWN for segment $i: DEM data missing or incomplete")
                    overallDataQuality = DataQuality.LOW
                } else {
                    grade = rawGrade
                }
            } catch (e: Exception) {
                isGradeUnknown = true
                grade = Double.NaN
                routeWarnings.add("Elevation grade failed for segment $i: ${e.message}")
                overallDataQuality = DataQuality.LOW
            }

            try {
                val rawAlt = elevationEngine.getElevationAt(start)
                if (rawAlt.isNaN() || !rawAlt.isFinite()) {
                    isAltitudeUnknown = true
                    altitude = Double.NaN
                    routeWarnings.add("Elevation lookup is UNKNOWN for segment $i")
                    overallDataQuality = DataQuality.LOW
                } else {
                    altitude = rawAlt
                }
            } catch (e: Exception) {
                isAltitudeUnknown = true
                altitude = Double.NaN
                routeWarnings.add("Elevation lookup failed for segment $i: ${e.message}")
                overallDataQuality = DataQuality.LOW
            }

            try {
                val roadCharacteristics = roadProfileEngine.calculateSegmentRoadCharacteristics(start, end)
                speedLimitKmh = roadCharacteristics.speedLimitKmh
            } catch (e: Exception) {
                routeWarnings.add("Road profile lookup failed for segment $i: ${e.message}")
                overallDataQuality = DataQuality.LOW
            }

            // Speed: limit * 0.95 clamped to [20, 120] km/h; without limit -> 80 km/h
            val targetSpeedKmh = if (speedLimitKmh != null && speedLimitKmh > 0) {
                (speedLimitKmh * 0.95).coerceIn(20.0, 120.0)
            } else {
                80.0
            }
            val speedMps = targetSpeedKmh / 3.6

            val segmentRoadProfile = RoadProfileData(
                coordinate = start,
                roadType = RoadType.HIGHWAY,
                surfaceType = SurfaceType.ASPHALT,
                speedLimitKmh = speedLimitKmh ?: 80,
                gradientPercent = grade,
                isTollRoad = false
            )

            val elevPoint = if (isAltitudeUnknown) ElevationPoint.unknown(start) else ElevationPoint.known(start, altitude)

            val segResult = calculateSegmentEnergy(
                startCoordinate = start,
                endCoordinate = end,
                vehicleProfile = vehicleProfile,
                batteryState = currentBattery,
                roadProfileData = segmentRoadProfile,
                elevationPoint = elevPoint,
                averageSpeedMps = speedMps
            )

            segments.add(segResult)
            totalDistanceMeters += segResult.distanceMeters
            totalTravelTimeSec += segResult.estimatedTravelTimeSeconds
            totalConsumedWh += segResult.energyConsumedWh
            totalRecoveredWh += segResult.energyRecoveredWh
            totalDemUncertaintyWh += segResult.demUncertaintyWh

            // Track unclamped remaining battery energy (with capacity ceiling for regen)
            runningSoEWh = (runningSoEWh - segResult.netEnergyDeltaWh).coerceAtMost(capacityWh)
            if (runningSoEWh < minRunningSoEWh) {
                minRunningSoEWh = runningSoEWh
            }

            val segSoc = if (capacityWh > 0.0) (runningSoEWh / capacityWh) * 100.0 else 0.0
            if (segSoc < minSoC) minSoC = segSoc
            if (segSoc > maxSoC) maxSoC = segSoc

            currentBattery = segResult.batteryStateAfterSegment
        }

        val netDeltaWh = totalConsumedWh - totalRecoveredWh
        val requiredThresholdWh = (capacityWh * (initialBatteryState.minimumOperationalSoCPercent / 100.0)) +
                initialBatteryState.reserveWh.wattHours
        val conservativeThresholdWh = requiredThresholdWh + totalDemUncertaintyWh
        val isReachable = minRunningSoEWh >= conservativeThresholdWh

        val reasonForUnreachability = if (!isReachable) {
            if (minRunningSoEWh >= requiredThresholdWh) {
                String.format(
                    Locale.US,
                    "Route reachability uncertain due to missing DEM: minimum SoE (%.1f Wh) falls below conservative safety margin including DEM uncertainty (%.1f Wh)",
                    minRunningSoEWh,
                    conservativeThresholdWh
                )
            } else {
                String.format(
                    Locale.US,
                    "Insufficient battery: minimum SoE during trip would drop to %.1f Wh (required safety threshold: %.1f Wh)",
                    minRunningSoEWh,
                    requiredThresholdWh
                )
            }
        } else {
            null
        }

        return RouteEnergyResult(
            totalDistanceMeters = totalDistanceMeters,
            estimatedTotalTravelTimeSeconds = totalTravelTimeSec,
            totalEnergyConsumedWh = totalConsumedWh,
            totalEnergyRecoveredWh = totalRecoveredWh,
            netEnergyDeltaWh = netDeltaWh,
            initialBatteryState = initialBatteryState,
            finalBatteryState = currentBattery,
            minimumSoCPercent = minSoC,
            maximumSoCPercent = maxSoC,
            minimumSoEWh = minRunningSoEWh,
            maximumSoEWh = initialBatteryState.currentSoEWh.wattHours,
            segmentResults = segments,
            modelFidelity = modelFidelity,
            dataQuality = overallDataQuality,
            totalDemUncertaintyWh = totalDemUncertaintyWh,
            warnings = routeWarnings,
            isReachable = isReachable,
            reasonForUnreachability = reasonForUnreachability
        )
    }

    override suspend fun evaluateRouteFeasibility(
        routePoints: List<GeoCoordinate>,
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity
    ): RouteFeasibilityResult {
        val routeEnergyResult = calculateRouteEnergy(
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            initialBatteryState = initialBatteryState,
            modelFidelity = modelFidelity
        )

        val reachableRangeKm = estimateReachableRange(
            vehicleProfile = vehicleProfile,
            initialBatteryState = initialBatteryState,
            modelFidelity = modelFidelity
        ).estimatedRangeMeters / 1000.0

        return RouteFeasibilityResult(
            isReachable = routeEnergyResult.isReachable,
            reasonForUnreachability = routeEnergyResult.reasonForUnreachability,
            estimatedEnergyNeededWh = routeEnergyResult.netEnergyDeltaWh,
            estimatedRangeKm = reachableRangeKm,
            minimumSoCPercent = routeEnergyResult.minimumSoCPercent,
            modelFidelity = modelFidelity
        )
    }

    override suspend fun estimateReachableRange(
        vehicleProfile: VehicleProfile,
        initialBatteryState: BatteryState,
        modelFidelity: EnergyModelFidelity
    ): ReachableRangeResult {
        val speedMps = 25.0 // 90 km/h reference
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
            speedMps = speedMps,
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
        val travelTimeSec = if (speedMps > 0.0) nominalRangeMeters / speedMps else 0.0

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
            estimatedTravelTimeSeconds = travelTimeSec,
            minimumSoCPercent = minSafeSoCPercent,
            modelFidelity = modelFidelity,
            optimisticRangeMeters = optimisticRangeMeters,
            nominalRangeMeters = nominalRangeMeters,
            pessimisticRangeMeters = pessimisticRangeMeters
        )
    }

    override suspend fun propagateBatteryState(
        startCoordinate: GeoCoordinate,
        endCoordinate: GeoCoordinate,
        vehicleProfile: VehicleProfile,
        currentBatteryState: BatteryState,
        roadProfileData: RoadProfileData,
        elevationPoint: ElevationPoint?,
        averageSpeedMps: Double
    ): BatteryState {
        val segmentResult = calculateSegmentEnergy(
            startCoordinate = startCoordinate,
            endCoordinate = endCoordinate,
            vehicleProfile = vehicleProfile,
            batteryState = currentBatteryState,
            roadProfileData = roadProfileData,
            elevationPoint = elevationPoint,
            averageSpeedMps = averageSpeedMps
        )

        return segmentResult.batteryStateAfterSegment
    }
}
