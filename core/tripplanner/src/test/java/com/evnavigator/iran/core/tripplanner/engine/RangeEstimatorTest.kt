package com.evnavigator.iran.core.tripplanner.engine

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.Route
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.energy.EnergyEngine
import com.evnavigator.iran.core.energy.RouteFeasibilityResult
import com.evnavigator.iran.core.energy.ReachableRangeResult
import com.evnavigator.iran.core.energy.model.*
import com.evnavigator.iran.core.roadprofile.RoadProfileData
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RangeEstimatorTest {

    @Test
    fun testProjectSocAlongRoute() = runBlocking {
        val vehicleProfile = VehicleProfile.createDefaultTestProfile()

        val p1 = GeoCoordinate(35.6892, 51.3890)
        val p2 = GeoCoordinate(35.6992, 51.3990)
        val p3 = GeoCoordinate(35.7092, 51.4090)

        val segment1 = RouteSegment(
            id = "seg1",
            roadId = "road1",
            startCoordinate = p1,
            endCoordinate = p2,
            lengthMeters = 1000.0,
            gradientPercent = 0.0
        )
        val segment2 = RouteSegment(
            id = "seg2",
            roadId = "road2",
            startCoordinate = p2,
            endCoordinate = p3,
            lengthMeters = 1000.0,
            gradientPercent = 0.0
        )

        val route = Route(
            id = "test_route",
            name = "Test Route",
            startCoordinate = p1,
            endCoordinate = p3,
            segments = listOf(segment1, segment2),
            totalDistanceMeters = 2000.0,
            estimatedDurationSeconds = 600L,
            elevationGainMeters = 0.0
        )

        val initialBattery = BatteryState.fromInitialSoC(
            usableCapacityWh = vehicleProfile.usableBatteryCapacityWh,
            initialSoCPercent = 80.0
        )
        val stateAfterSeg1 = initialBattery.updateWithEnergyDelta(-1000.0)
        val stateAfterSeg2 = stateAfterSeg1.updateWithEnergyDelta(-1000.0)

        val mockSeg1 = SegmentEnergyResult(
            startCoordinate = p1,
            endCoordinate = p2,
            distanceMeters = 1000.0,
            averageSpeedMps = 16.6,
            estimatedTravelTimeSeconds = 60.0,
            energyConsumedWh = 1000.0,
            energyRecoveredWh = 0.0,
            netEnergyDeltaWh = -1000.0,
            rollingResistanceEnergyWh = 300.0,
            aerodynamicDragEnergyWh = 400.0,
            elevationEnergyWh = 0.0,
            accelerationEnergyWh = 100.0,
            decelerationEnergyWh = 0.0,
            auxiliaryEnergyWh = 100.0,
            gradePercent = 0.0,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            batteryStateAfterSegment = stateAfterSeg1,
            modelFidelity = EnergyModelFidelity.FULL_PHYSICS,
            dataQuality = DataQuality.HIGH
        )
        val mockSeg2 = mockSeg1.copy(
            startCoordinate = p2,
            endCoordinate = p3,
            batteryStateAfterSegment = stateAfterSeg2
        )

        val mockRouteResult = RouteEnergyResult(
            totalDistanceMeters = 2000.0,
            estimatedTotalTravelTimeSeconds = 120.0,
            totalEnergyConsumedWh = 2000.0,
            totalEnergyRecoveredWh = 0.0,
            netEnergyDeltaWh = -2000.0,
            initialBatteryState = initialBattery,
            finalBatteryState = stateAfterSeg2,
            minimumSoCPercent = stateAfterSeg2.currentSoCPercent,
            maximumSoCPercent = 80.0,
            minimumSoEWh = stateAfterSeg2.currentSoEWh.wattHours,
            maximumSoEWh = initialBattery.currentSoEWh.wattHours,
            segmentResults = listOf(mockSeg1, mockSeg2),
            modelFidelity = EnergyModelFidelity.FULL_PHYSICS,
            dataQuality = DataQuality.HIGH,
            isReachable = true
        )

        val mockEnergyEngine = object : EnergyEngine {
            override suspend fun calculateSegmentEnergy(
                startCoordinate: GeoCoordinate,
                endCoordinate: GeoCoordinate,
                vehicleProfile: VehicleProfile,
                batteryState: BatteryState,
                roadProfileData: RoadProfileData,
                elevationPoint: ElevationPoint?,
                averageSpeedMps: Double
            ): SegmentEnergyResult = mockSeg1

            override suspend fun calculateRouteEnergy(
                routePoints: List<GeoCoordinate>,
                vehicleProfile: VehicleProfile,
                initialBatteryState: BatteryState,
                modelFidelity: EnergyModelFidelity
            ): RouteEnergyResult = mockRouteResult

            override suspend fun evaluateRouteFeasibility(
                routePoints: List<GeoCoordinate>,
                vehicleProfile: VehicleProfile,
                initialBatteryState: BatteryState,
                modelFidelity: EnergyModelFidelity
            ): RouteFeasibilityResult {
                return RouteFeasibilityResult(
                    isReachable = true,
                    reasonForUnreachability = null,
                    estimatedEnergyNeededWh = 2000.0,
                    estimatedRangeKm = 300.0,
                    minimumSoCPercent = 40.0,
                    modelFidelity = modelFidelity
                )
            }

            override suspend fun estimateReachableRange(
                vehicleProfile: VehicleProfile,
                initialBatteryState: BatteryState,
                modelFidelity: EnergyModelFidelity
            ): ReachableRangeResult {
                return ReachableRangeResult(
                    estimatedRangeMeters = 300000.0,
                    estimatedEnergyConsumedWh = 2000.0,
                    estimatedTravelTimeSeconds = 600.0,
                    minimumSoCPercent = 10.0,
                    modelFidelity = modelFidelity
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
            ): BatteryState = currentBatteryState
        }

        val estimator = RangeEstimator(mockEnergyEngine)

        val socProjection = estimator.projectSocAlongRoute(
            route = route,
            startSocPercent = 80.0,
            vehicleProfile = vehicleProfile
        )

        assertEquals(3, socProjection.size)
        assertEquals(80.0, socProjection[0].second, 0.1)
        assertTrue(socProjection[1].second < socProjection[0].second)
        assertTrue(socProjection[2].second < socProjection[1].second)
    }
}
