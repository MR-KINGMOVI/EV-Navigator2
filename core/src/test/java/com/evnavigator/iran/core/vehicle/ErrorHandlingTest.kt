package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ErrorHandlingTest {
    
    @Test
    fun `test vehicle with invalid battery capacity`() {
        val vehicle = createSampleVehicle().copy(
            nominalBatteryCapacityWh = Energy(-1000.0), // Invalid negative capacity
            usableBatteryCapacityWh = Energy(-500.0)   // Invalid negative capacity
        )
        
        // Should fail validation
        val result = ValidationEngine.validateVehicle(vehicle)
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test vehicle with battery capacity exceeding nominal`() {
        val vehicle = createSampleVehicle().copy(
            usableBatteryCapacityWh = Energy(70000.0) // Exceeds nominal capacity
        )
        
        // Should fail validation
        val result = ValidationEngine.validateVehicle(vehicle)
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test vehicle with invalid mass`() {
        val vehicle = createSampleVehicle().copy(
            curbMassKg = Mass(-1000.0) // Invalid negative mass
        )
        
        // Should fail validation
        val result = ValidationEngine.validateVehicle(vehicle)
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test vehicle with invalid motor power`() {
        val vehicle = createSampleVehicle().copy(
            frontMotorPowerKw = Power(-100.0) // Invalid negative power
        )
        
        // Should fail validation
        val result = ValidationEngine.validateVehicle(vehicle)
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test vehicle with invalid confidence`() {
        val vehicle = createSampleVehicle().copy(
            confidence = -0.1 // Invalid negative confidence
        )
        
        // Should fail validation
        assertFailsWith<IllegalArgumentException> {
            ValidationEngine.validateVehicle(vehicle)
        }
    }
    
    @Test
    fun `test vehicle resolution with invalid data`() {
        val invalidVehicle = createSampleVehicle().copy(
            curbMassKg = Mass(-1000.0) // Invalid mass
        )
        
        val validVehicle = createSampleVehicle()
        
        // Should resolve to the valid vehicle
        val resolved = VehicleResolutionEngine.resolveGroup(listOf(invalidVehicle, validVehicle))
        
        // Should return the valid vehicle despite invalid one being present
        assertEquals(validVehicle, resolved)
    }
    
    @Test
    fun `test vehicle resolution with all invalid data`() {
        val invalidVehicle1 = createSampleVehicle().copy(
            curbMassKg = Mass(-1000.0) // Invalid mass
        )
        
        val invalidVehicle2 = createSampleVehicle().copy(
            nominalBatteryCapacityWh = Energy(-5000.0) // Invalid capacity
        )
        
        // Should return one of them (the first valid one if any, or the first in list)
        val resolved = VehicleResolutionEngine.resolveGroup(listOf(invalidVehicle1, invalidVehicle2))
        
        // At least one should be returned (this is a conservative approach)
        assertTrue(resolved != null)
    }
    
    @Test
    fun `test vehicle with missing critical fields`() {
        val vehicle = createSampleVehicle().copy(
            name = "", // Empty name
            curbMassKg = Mass(0.0), // Zero mass
            nominalBatteryCapacityWh = Energy(0.0) // Zero capacity
        )
        
        // Should fail validation
        val result = ValidationEngine.validateVehicle(vehicle)
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test vehicle with partial data`() {
        val vehicle = createSampleVehicle().copy(
            name = "Partial Vehicle",
            curbMassKg = Mass(1500.0),
            // Missing many other fields
            dragCoefficient = null,
            frontalAreaM2 = null,
            rollingResistanceCoefficient = null,
            batteryChemistry = null
        )
        
        // Should still validate (some warnings expected)
        val result = ValidationEngine.validateVehicle(vehicle)
        assertEquals(ValidationStatus.PARTIALLY_VALIDATED, result.status)
        assertTrue(result.warnings.isNotEmpty())
    }
    
    @Test
    fun `test vehicle resolution with unknown data`() {
        // Test that we can handle vehicles with UNKNOWN status gracefully
        val vehicleWithUnknown = createSampleVehicle().copy(
            dataQuality = DataQuality.UNKNOWN,
            confidence = 0.1
        )
        
        val resolved = VehicleResolutionEngine.resolveGroup(listOf(vehicleWithUnknown))
        assertEquals(vehicleWithUnknown, resolved)
    }
    
    @Test
    fun `test vehicle resolution empty list`() {
        val result = VehicleResolutionEngine.resolveVehicleSpecifications(emptyList())
        assertEquals(null, result)
    }
    
    @Test
    fun `test is energy ready with missing data`() {
        val vehicle = createSampleVehicle().copy(
            curbMassKg = Mass(0.0) // Invalid mass
        )
        
        val isReady = VehicleResolutionEngine.isEnergyReady(vehicle)
        assertEquals(false, isReady)
    }
    
    @Test
    fun `test vehicle identity validation`() {
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Premium") // Different trim
        
        val sameIdentity = vehicle1.hasSameIdentity(vehicle2)
        assertEquals(false, sameIdentity) // Different trims should have different identities
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
            source = "Test Source",
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