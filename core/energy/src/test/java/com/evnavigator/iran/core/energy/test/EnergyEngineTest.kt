package com.evnavigator.iran.core.energy.test

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.energy.EnergyEngine
import com.evnavigator.iran.core.energy.EnergyEngineImpl
import com.evnavigator.iran.core.energy.model.*
import com.evnavigator.iran.core.elevation.ElevationEngine
import com.evnavigator.iran.core.elevation.ClimbDescentSection
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.roadprofile.RoadProfileEngine
import com.evnavigator.iran.core.roadprofile.RoadProfileData
import com.evnavigator.iran.core.roadprofile.RoadType
import com.evnavigator.iran.core.roadprofile.SurfaceType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for EnergyEngine implementation
 */
class EnergyEngineTest {
    
    private val testVehicleProfile = VehicleProfile.createDefaultTestProfile()
    private val testBatteryState = BatteryState.fromInitialSoC(
        usableCapacityWh = Energy(54000.0), // 90% of nominal capacity
        initialSoCPercent = 80.0,
        minimumOperationalSoCPercent = 10.0,
        maximumUsableSoCPercent = 90.0
    )
    
    @Test
    fun testVehicleProfileCreation() = runTest {
        // Test that we can create a vehicle profile
        val profile = VehicleProfile.createDefaultTestProfile()
        
        assertNotNull("Vehicle profile should not be null", profile)
        assertEquals("Vehicle name should be correct", "Default Test Vehicle", profile.name)
        assertEquals("Curb mass should be 1500 kg", 1500.0, profile.curbMassKg.kilograms, 0.01)
        assertEquals("Frontal area should be 2.2 m²", 2.2, profile.frontalAreaM2, 0.01)
        assertEquals("Drag coefficient should be 0.32", 0.32, profile.dragCoefficient, 0.01)
    }
    
    @Test
    fun testEnergyCalculations() = runTest {
        // Test basic energy calculations
        val energy1 = Energy(1000.0) // 1000 Wh
        val energy2 = Energy(2000.0) // 2000 Wh
        
        val sum = energy1 + energy2
        assertEquals("Energy addition should work", 3000.0, sum.wattHours, 0.01)
        
        val product = energy1 * 2.0
        assertEquals("Energy multiplication should work", 2000.0, product.wattHours, 0.01)
    }
    
    @Test
    fun testSpeedConsumptionCurve() = runTest {
        // Test speed consumption curve interpolation
        val curve = testVehicleProfile.speedConsumptionCurve
        
        // Test at a point within the curve
        val consumptionAt40Kmh = curve.getConsumptionAtSafe(Speed.fromKilometersPerHour(40.0))
        assertTrue("Consumption should be non-negative", consumptionAt40Kmh >= 0.0)
        
        // Test extrapolation (should return boundary values)
        val consumptionAt0Kmh = curve.getConsumptionAtSafe(Speed.fromKilometersPerHour(0.0))
        assertTrue("Consumption at 0 km/h should be valid", consumptionAt0Kmh >= 0.0)
        
        val consumptionAt200Kmh = curve.getConsumptionAtSafe(Speed.fromKilometersPerHour(200.0))
        assertTrue("Consumption at high speed should be valid", consumptionAt200Kmh >= 0.0)
    }
    
    @Test
    fun testRoadLoadCalculations() = runTest {
        // Test rolling resistance calculation
        val mass = Mass(1500.0) // 1500 kg
        val distance = 1000.0 // 1 km
        val crr = 0.012 // Rolling resistance coefficient
        
        val energy = RoadLoadCalculator.calculateRollingResistanceEnergy(mass, distance, crr)
        assertTrue("Rolling resistance energy should be positive", energy > 0.0)
        
        // Test aerodynamic drag calculation  
        val speed = 20.0 // 20 m/s (72 km/h)
        val frontalArea = 2.2 // m²
        val dragCoeff = 0.32
        
        val aerodynamicEnergy = RoadLoadCalculator.calculateAerodynamicDragEnergy(
            speed, 
            frontalArea, 
            dragCoeff, 
            distance
        )
        assertTrue("Aerodynamic energy should be positive", aerodynamicEnergy > 0.0)
    }
    
    @Test
    fun testBatteryStateOperations() = runTest {
        // Test battery state creation and operations
        val batteryState = BatteryState.fromInitialSoC(
            usableCapacityWh = Energy(54000.0),
            initialSoCPercent = 80.0,
            minimumOperationalSoCPercent = 10.0,
            maximumUsableSoCPercent = 90.0
        )
        
        assertEquals("Initial SoC should be 80%", 80.0, batteryState.currentSoCPercent, 0.01)
        assertTrue("Battery should be operational", batteryState.isOperational)
        
        // Test energy update
        val updatedState = batteryState.updateWithEnergyDelta(-5000.0) // Consume 5000 Wh
        assertTrue("Updated SoE should be less than initial", 
            updatedState.currentSoEWh.wattHours < batteryState.currentSoEWh.wattHours)
        
        assertEquals("SoC should be reduced", 70.74, updatedState.currentSoCPercent, 0.01) // ~80% - 5000/54000 * 100
    }
    
    @Test
    fun testEnergyModelFidelity() = runTest {
        // Test that all model fidelities are defined
        assertTrue("Basic fidelity should exist", EnergyModelFidelity.values().isNotEmpty())
        assertEquals("Should have three fidelity levels", 3, EnergyModelFidelity.values().size)
        
        val basic = EnergyModelFidelity.BASIC
        val enhanced = EnergyModelFidelity.ENHANCED
        val fullPhysics = EnergyModelFidelity.FULL_PHYSICS
        
        assertNotNull("Basic should not be null", basic)
        assertNotNull("Enhanced should not be null", enhanced)
        assertNotNull("Full physics should not be null", fullPhysics)
    }
    
    @Test
    fun testDataQuality() = runTest {
        // Test that all data quality levels are defined
        assertTrue("Data quality enum should have values", DataQuality.values().isNotEmpty())
        assertEquals("Should have four quality levels", 4, DataQuality.values().size)
        
        val high = DataQuality.HIGH
        val medium = DataQuality.MEDIUM
        val low = DataQuality.LOW
        val unknown = DataQuality.UNKNOWN
        
        assertNotNull("High should not be null", high)
        assertNotNull("Medium should not be null", medium)
        assertNotNull("Low should not be null", low)
        assertNotNull("Unknown should not be null", unknown)
    }
    
    @Test
    fun testEnergyEngineInterface() = runTest {
        // Test that we can create an engine instance (this would require mocks for dependencies)
        // This is a basic structural test - actual implementation will be tested separately
        
        // Just make sure the interface and implementation are properly defined
        val vehicleProfile = VehicleProfile.createDefaultTestProfile()
        val batteryState = BatteryState.fromInitialSoC(
            usableCapacityWh = Energy(54000.0),
            initialSoCPercent = 80.0
        )
        
        // Test basic data structures work
        val coordinate1 = GeoCoordinate(35.6892, 51.3890)
        val coordinate2 = GeoCoordinate(35.7000, 51.4000)
        
        val roadProfileData = RoadProfileData(
            coordinate = coordinate1,
            roadType = RoadType.STREET,
            surfaceType = SurfaceType.ASPHALT,
            speedLimitKmh = 60,
            gradientPercent = 2.0,
            isTollRoad = false
        )
        
        val elevationPoint = ElevationPoint.known(coordinate1, 1200.0)
        
        // Test that we can create all the necessary components
        assertTrue("Vehicle profile should be valid", vehicleProfile.totalOperatingMassKg.kilograms > 0)
        assertTrue("Battery state should be valid", batteryState.currentSoEWh.wattHours >= 0)
        assertNotNull("Road profile data should not be null", roadProfileData)
        assertNotNull("Elevation point should not be null", elevationPoint)
    }
}