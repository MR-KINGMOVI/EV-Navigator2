package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvenanceVersioningTest {
    
    @Test
    fun `test vehicle provenance information`() {
        val vehicle = createSampleVehicle()
        
        // Verify source information
        assertEquals("Manufacturer Source", vehicle.source)
        assertEquals("1.0", vehicle.sourceVersion)
        assertEquals("ref-12345", vehicle.sourceReference)
        
        // Verify timestamp
        assertTrue(vehicle.retrievedDate > 0)
        
        // Verify versioning
        assertEquals("1.0", vehicle.schemaVersion)
        assertEquals("1.0", vehicle.dataVersion)
        assertEquals("1.0", vehicle.vehicleRecordVersion)
    }
    
    @Test
    fun `test vehicle identity uniqueness`() {
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Premium")
        
        // Different trims should have different IDs
        assertTrue(vehicle1.id.id != vehicle2.id.id)
    }
    
    @Test
    fun `test vehicle versioning consistency`() {
        val vehicle = createSampleVehicle()
        
        // All version fields should be set
        assertEquals("1.0", vehicle.schemaVersion)
        assertEquals("1.0", vehicle.dataVersion)
        assertEquals("1.0", vehicle.vehicleRecordVersion)
    }
    
    @Test
    fun `test vehicle timestamp handling`() {
        val vehicle = createSampleVehicle()
        
        // Should have creation and update timestamps
        assertTrue(vehicle.createdAt > 0)
        assertTrue(vehicle.updatedAt > 0)
        assertEquals(vehicle.createdAt, vehicle.updatedAt) // Initially same
    }
    
    @Test
    fun `test vehicle update timestamp`() {
        val vehicle = createSampleVehicle()
        val updatedVehicle = vehicle.withUpdatedTimestamp()
        
        // Updated timestamp should be different from created timestamp
        assertTrue(updatedVehicle.updatedAt > vehicle.createdAt)
    }
    
    @Test
    fun `test vehicle human readable description`() {
        val vehicle = createSampleVehicle()
        val description = vehicle.getHumanReadableDescription()
        
        assertTrue(description.contains(vehicle.name))
        assertTrue(description.contains(vehicle.id.id))
    }
    
    private fun createSampleVehicle(
        manufacturer: String = "Test",
        model: String = "Model", 
        generation: String = "Gen1",
        modelYear: Int = 2023,
        trim: String = "Base"
    ): Vehicle {
        return Vehicle(
            id = VehicleId(
                manufacturer = manufacturer,
                model = model,
                generation = generation,
                modelYear = modelYear,
                trim = trim
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
            source = "Manufacturer Source",
            sourceVersion = "1.0",
            sourceReference = "ref-12345",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.95,
            validationStatus = ValidationStatus.VALIDATED,
            notes = null,
            schemaVersion = "1.0",
            dataVersion = "1.0",
            vehicleRecordVersion = "1.0"
        )
    }
}