package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VehicleResolutionEngineTest {
    
    @Test
    fun `test resolve single vehicle returns same vehicle`() {
        val vehicle = createSampleVehicle()
        
        val result = VehicleResolutionEngine.resolveVehicleSpecifications(listOf(vehicle))
        
        assertEquals(vehicle, result)
    }
    
    @Test
    fun `test resolve empty list returns null`() {
        val result = VehicleResolutionEngine.resolveVehicleSpecifications(emptyList())
        
        assertNull(result)
    }
    
    @Test
    fun `test resolve multiple vehicles selects highest quality`() {
        val vehicle1 = createSampleVehicle().copy(
            dataQuality = DataQuality.LOW,
            confidence = 0.5
        )
        
        val vehicle2 = createSampleVehicle().copy(
            id = VehicleId(
                manufacturer = "Test2",
                model = "Model",
                generation = "Gen1",
                modelYear = 2023,
                trim = "Base"
            ),
            dataQuality = DataQuality.HIGH,
            confidence = 0.9
        )
        
        val result = VehicleResolutionEngine.resolveVehicleSpecifications(listOf(vehicle1, vehicle2))
        
        assertNotNull(result)
        assertEquals(DataQuality.HIGH, result.dataQuality)
    }
    
    @Test
    fun `test resolve multiple vehicles with same identity`() {
        val vehicle1 = createSampleVehicle().copy(
            dataQuality = DataQuality.LOW,
            confidence = 0.5
        )
        
        val vehicle2 = createSampleVehicle().copy(
            dataQuality = DataQuality.HIGH,
            confidence = 0.9,
            name = "Modified Vehicle Name"
        )
        
        val result = VehicleResolutionEngine.resolveVehicleSpecifications(listOf(vehicle1, vehicle2))
        
        assertNotNull(result)
        assertEquals(DataQuality.HIGH, result.dataQuality)
    }
    
    @Test
    fun `test determine preferred specification`() {
        val vehicle1 = createSampleVehicle().copy(
            dataQuality = DataQuality.LOW,
            confidence = 0.5
        )
        
        val vehicle2 = createSampleVehicle().copy(
            dataQuality = DataQuality.HIGH,
            confidence = 0.9
        )
        
        val result = VehicleResolutionEngine.determinePreferredSpecification(listOf(vehicle1, vehicle2))
        
        assertNotNull(result)
        assertEquals(DataQuality.HIGH, result.dataQuality)
    }
    
    @Test
    fun `test is energy ready`() {
        val vehicle = createSampleVehicle()
        
        val result = VehicleResolutionEngine.isEnergyReady(vehicle)
        
        assertEquals(true, result)
    }
    
    @Test
    fun `test is not energy ready when missing key fields`() {
        val vehicle = createSampleVehicle().copy(
            curbMassKg = Mass(0.0) // Invalid mass
        )
        
        val result = VehicleResolutionEngine.isEnergyReady(vehicle)
        
        assertEquals(false, result)
    }
    
    private fun createSampleVehicle(): Vehicle {
        return Vehicle(
            id = VehicleId(
                manufacturer = "Test",
                model = "Model",
                generation = "Gen1",
                modelYear = 2023,
                trim = "Base"
            ),
            name = "Test Vehicle",
            curbMassKg = Mass(1500.0),
            grossVehicleMassKg = Mass(1800.0),
            lengthMm = 4500,
            widthMm = 1800,
            heightMm = 1500,
            wheelbaseMm = 2700,
            groundClearanceMm = 150,
            dragCoefficient = 0.25,
            frontalAreaM2 = 2.2,
            rollingResistanceCoefficient = 0.012,
            nominalBatteryCapacityWh = Energy(60000.0),
            usableBatteryCapacityWh = Energy(55000.0),
            batteryChemistry = "Lithium Iron Phosphate",
            nominalVoltageV = 370.0,
            batteryConfiguration = "48S10P",
            motorCount = 2,
            frontMotorPowerKw = Power(100.0),
            rearMotorPowerKw = Power(150.0),
            peakMotorPowerKw = Power(300.0),
            continuousMotorPowerKw = Power(200.0),
            torqueNm = 400,
            drivetrainType = DrivetrainType.AWD,
            baselineConsumptionWhPer100Km = 1500.0,
            acChargingMaxPowerKw = Power(7.0),
            dcChargingMaxPowerKw = Power(120.0),
            chargingVoltageRangeV = 230..480,
            chargingCurrentRangeA = 6..32,
            chargePortCount = 2,
            connectorTypes = setOf(ConnectorType.TYPE_2, ConnectorType.CCS_COMBO_2),
            wheelDiameterMm = 19,
            tireWidthMm = 225,
            dataQuality = DataQuality.HIGH,
            source = "Test Source",
            sourceVersion = "1.0",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.95,
            validationStatus = ValidationStatus.VALIDATED,
            notes = null
        )
    }
}