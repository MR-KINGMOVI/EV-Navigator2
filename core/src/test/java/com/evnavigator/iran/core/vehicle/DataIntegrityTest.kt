package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataIntegrityTest {
    
    @Test
    fun `test vehicle id uniqueness`() {
        val vehicle1 = createSampleVehicle("Test", "Model", "Gen1", 2023, "Base")
        val vehicle2 = createSampleVehicle("Test", "Model", "Gen1", 2023, "Base")
        
        // These should have the same ID
        assertEquals(vehicle1.id.id, vehicle2.id.id)
    }
    
    @Test
    fun `test vehicle id uniqueness with different variants`() {
        val vehicle1 = createSampleVehicle("Test", "Model", "Gen1", 2023, "Base")
        val vehicle2 = createSampleVehicle("Test", "Model", "Gen1", 2023, "Premium")
        
        // These should have different IDs due to trim difference
        assertFalse(vehicle1.id.id == vehicle2.id.id)
    }
    
    @Test
    fun `test vehicle id with different markets`() {
        val vehicle1 = createSampleVehicle("Test", "Model", "Gen1", 2023, "Base")
        val vehicle2 = createSampleVehicle("Test", "Model", "Gen1", 2023, "Base", market = "US")
        
        // These should have different IDs due to market difference
        assertFalse(vehicle1.id.id == vehicle2.id.id)
    }
    
    @Test
    fun `test vehicle id stability`() {
        val originalVehicle = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val expectedId = "toyota_camry_xle_2023_base_ir_bev"
        
        assertEquals(expectedId, originalVehicle.id.id)
    }
    
    @Test
    fun `test vehicle identity consistency`() {
        val vehicle = createSampleVehicle("Honda", "Civic", "Type R", 2024, "Sport")
        
        // Verify all identity components are correctly set
        assertEquals("Honda", vehicle.id.manufacturer)
        assertEquals("Civic", vehicle.id.model)
        assertEquals("Type R", vehicle.id.generation)
        assertEquals(2024, vehicle.id.modelYear)
        assertEquals("Sport", vehicle.id.trim)
        assertEquals("IR", vehicle.id.market)
        assertEquals(PowertrainType.BEV, vehicle.id.powertrainType)
    }
    
    @Test
    fun `test vehicle validation integrity`() {
        val validVehicle = createSampleVehicle()
        val validationResult = ValidationEngine.validateVehicle(validVehicle)
        
        assertEquals(ValidationStatus.VALIDATED, validationResult.status)
        assertTrue(validationResult.errors.isEmpty())
        assertTrue(validationResult.warnings.isEmpty())
    }
    
    @Test
    fun `test vehicle validation with invalid data`() {
        val invalidVehicle = createSampleVehicle().copy(
            curbMassKg = Mass(-1000.0) // Invalid negative mass
        )
        
        val validationResult = ValidationEngine.validateVehicle(invalidVehicle)
        
        assertEquals(ValidationStatus.INVALID, validationResult.status)
        assertFalse(validationResult.errors.isEmpty())
    }
    
    @Test
    fun `test vehicle resolution preserves provenance`() {
        val vehicle1 = createSampleVehicle().copy(
            source = "Manufacturer Source",
            confidence = 0.95,
            dataQuality = DataQuality.HIGH
        )
        
        val vehicle2 = createSampleVehicle().copy(
            source = "Third Party Source", 
            confidence = 0.80,
            dataQuality = DataQuality.MEDIUM
        )
        
        // Test that resolution preserves the best quality data
        val resolved = VehicleResolutionEngine.resolveGroup(listOf(vehicle1, vehicle2))
        
        // Should return the higher quality one (vehicle1)
        assertEquals(vehicle1.source, resolved?.source)
        assertEquals(vehicle1.dataQuality, resolved?.dataQuality)
    }
    
    private fun createSampleVehicle(
        manufacturer: String = "Test",
        model: String = "Model", 
        generation: String = "Gen1",
        modelYear: Int = 2023,
        trim: String = "Base",
        market: String = "IR"
    ): Vehicle {
        return Vehicle(
            id = VehicleId(
                manufacturer = manufacturer,
                model = model,
                generation = generation,
                modelYear = modelYear,
                trim = trim,
                market = market
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