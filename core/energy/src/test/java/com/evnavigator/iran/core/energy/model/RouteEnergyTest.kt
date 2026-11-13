package com.evnavigator.iran.core.energy.model

import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.elevation.*
import com.evnavigator.iran.core.energy.EnergyEngineImpl
import com.evnavigator.iran.core.roadprofile.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RouteEnergyTest {

    private lateinit var fakeElevationEngine: FakeElevationEngine
    private lateinit var fakeRoadProfileEngine: FakeRoadProfileEngine
    private lateinit var vehicleProfile: VehicleProfile

    // 100 km distance: 0.9 degrees latitude ~ 100 km
    private val startCoord = GeoCoordinate(35.0, 51.0)
    private val endCoord = GeoCoordinate(35.9, 51.0)
    private val routePoints = listOf(startCoord, endCoord)

    @Before
    fun setup() {
        fakeElevationEngine = FakeElevationEngine(grade = 0.0, elevation = 1200.0)
        fakeRoadProfileEngine = FakeRoadProfileEngine(speedLimit = 100)
        vehicleProfile = VehicleProfile.createDefaultTestProfile()
    }

    @Test
    fun testFlatRoute100kmCalculatesEnergyAndReaches() = runTest {
        val engine = EnergyEngineImpl(fakeElevationEngine, fakeRoadProfileEngine)
        val initialBattery = BatteryState(
            usableCapacityWh = Energy(60000.0),
            currentSoEWh = Energy(48000.0), // 80% SoC
            minimumOperationalSoCPercent = 10.0,
            maximumUsableSoCPercent = 100.0,
            reserveWh = Energy(3000.0)
        )

        val result = engine.calculateRouteEnergy(routePoints, vehicleProfile, initialBattery)

        assertTrue("100 km should be reachable with 48 kWh battery", result.isReachable)
        assertTrue("Total consumed energy should be > 10 kWh for 100 km, was: ${result.totalEnergyConsumedWh}", result.totalEnergyConsumedWh > 10000.0)
        assertTrue("Remaining minimum SoC should stay well above 10%, was: ${result.minimumSoCPercent}", result.minimumSoCPercent > 10.0)
        assertEquals(DataQuality.HIGH, result.dataQuality)
        assertNull(result.reasonForUnreachability)
    }

    @Test
    fun testClimbingRouteConsumesMoreThanFlat() = runTest {
        val engine = EnergyEngineImpl(fakeElevationEngine, fakeRoadProfileEngine)
        val battery = BatteryState(
            usableCapacityWh = Energy(80000.0),
            currentSoEWh = Energy(70000.0),
            minimumOperationalSoCPercent = 10.0,
            maximumUsableSoCPercent = 100.0
        )

        fakeElevationEngine.grade = 0.0
        val flatResult = engine.calculateRouteEnergy(routePoints, vehicleProfile, battery)

        fakeElevationEngine.grade = 2.5 // +2.5% climb over 100 km
        val climbResult = engine.calculateRouteEnergy(routePoints, vehicleProfile, battery)

        assertTrue(
            "Climbing route must consume more energy than flat route (climb: ${climbResult.totalEnergyConsumedWh}, flat: ${flatResult.totalEnergyConsumedWh})",
            climbResult.totalEnergyConsumedWh > flatResult.totalEnergyConsumedWh
        )
    }

    @Test
    fun testInsufficientBatteryMarksRouteUnreachable() = runTest {
        val engine = EnergyEngineImpl(fakeElevationEngine, fakeRoadProfileEngine)
        // Battery with only 5000 Wh (SoC 8.3%, below minimumOperational 10%)
        val depletedBattery = BatteryState(
            usableCapacityWh = Energy(60000.0),
            currentSoEWh = Energy(5000.0),
            minimumOperationalSoCPercent = 10.0,
            maximumUsableSoCPercent = 100.0,
            reserveWh = Energy(2000.0)
        )

        val result = engine.calculateRouteEnergy(routePoints, vehicleProfile, depletedBattery)

        assertFalse("Route should NOT be reachable with depleted battery", result.isReachable)
        assertTrue("Minimum SoC should drop below 10%, was: ${result.minimumSoCPercent}", result.minimumSoCPercent < 10.0)
        assertNotNull("Reason for unreachability must be specified", result.reasonForUnreachability)
    }

    @Test
    fun testRouteEnergyCalculatorFailsExplicitlyWhenEnginesAreNull() = runTest {
        val initialBattery = BatteryState(
            usableCapacityWh = Energy(60000.0),
            currentSoEWh = Energy(48000.0),
            minimumOperationalSoCPercent = 10.0,
            maximumUsableSoCPercent = 100.0
        )

        val result = RouteEnergyCalculator.calculateRouteEnergy(
            routePoints = routePoints,
            vehicleProfile = vehicleProfile,
            initialBatteryState = initialBattery,
            elevationEngine = null,
            roadProfileEngine = null
        )

        assertFalse("Route must be unreachable when engines are null", result.isReachable)
        assertEquals(DataQuality.UNKNOWN, result.dataQuality)
        assertEquals("Elevation and road-profile engines are required", result.reasonForUnreachability)
    }

    class FakeElevationEngine(
        var grade: Double = 0.0,
        var elevation: Double = 1200.0
    ) : ElevationEngine {
        override suspend fun calculateElevationProfile(routePoints: List<GeoCoordinate>): Flow<ElevationPoint> = flowOf()
        override suspend fun getElevationAt(point: GeoCoordinate): Double = elevation
        override suspend fun calculateElevationGainLoss(routePoints: List<GeoCoordinate>): ElevationGainLoss =
            ElevationGainLoss(0f, 0f)
        override suspend fun getElevationDataInArea(boundingBox: com.evnavigator.iran.core.elevation.BoundingBox): Flow<ElevationData> = flowOf()
        override suspend fun calculateGrade(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): Double = grade
        override suspend fun getRouteElevationCharacteristics(routePoints: List<GeoCoordinate>): RouteElevationCharacteristics {
            return RouteElevationCharacteristics(
                averageElevation = elevation.toFloat(),
                minElevation = elevation.toFloat(),
                maxElevation = elevation.toFloat(),
                totalElevationGain = 0f,
                totalElevationLoss = 0f,
                averageGrade = grade,
                maximumGrade = grade,
                minimumGrade = grade,
                climbSections = emptyList(),
                descentSections = emptyList()
            )
        }
        override suspend fun identifyClimbDescentSections(routePoints: List<GeoCoordinate>): List<ClimbDescentSection> = emptyList()
        override suspend fun calculateAverageElevation(routePoints: List<GeoCoordinate>): Double = elevation
        override suspend fun getMinMaxElevation(routePoints: List<GeoCoordinate>): MinMaxElevation {
            val coord = routePoints.firstOrNull() ?: GeoCoordinate(0.0, 0.0)
            return MinMaxElevation(elevation.toFloat(), elevation.toFloat(), coord, coord)
        }
        override suspend fun calculateDistanceBetweenPoints(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): Double {
            return startCoordinate.distanceTo(endCoordinate)
        }
    }

    class FakeRoadProfileEngine(
        var speedLimit: Int = 100
    ) : RoadProfileEngine {
        override suspend fun analyzeRoadProfile(routePoints: List<GeoCoordinate>): Flow<RoadProfileData> = flowOf()
        override suspend fun getRoadInfoAt(point: GeoCoordinate): RoadInfo? = null
        override suspend fun calculateSegmentRoadCharacteristics(
            startCoordinate: GeoCoordinate,
            endCoordinate: GeoCoordinate
        ): RoadSegmentCharacteristics {
            return RoadSegmentCharacteristics(
                startCoordinate = startCoordinate,
                endCoordinate = endCoordinate,
                lengthMeters = startCoordinate.distanceTo(endCoordinate),
                averageGradientPercent = 0.0,
                surfaceType = SurfaceType.ASPHALT,
                speedLimitKmh = speedLimit,
                isTollSection = false
            )
        }
        override suspend fun getRoadTypesAlongRoute(routePoints: List<GeoCoordinate>): Flow<RoadTypeInfo> = flowOf()
        override suspend fun calculateAverageRoadGrade(routePoints: List<GeoCoordinate>): Double = 0.0
        override suspend fun getRoadSurfaceDataInArea(boundingBox: com.evnavigator.iran.core.roadprofile.BoundingBox): Flow<RoadSurfaceData> = flowOf()
    }
}
