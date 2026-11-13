package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationEngineTest {
    
    @Test
    fun `test valid vehicle passes validation`() {
        val validVehicle = createSampleVehicle()
        
        val result = ValidationEngine.validateVehicle(validVehicle)
        
        assertEquals(ValidationStatus.VALIDATED, result.status)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }
    
    @Test
    fun `test invalid battery capacity fails validation`() {
        val vehicleWithInvalidBattery = createSampleVehicle().copy(
            nominalBatteryCapacityWh = Energy(-1000.0),
            usableBatteryCapacityWh = Energy(-500.0)
        )
        
        val result = ValidationEngine.validateVehicle(vehicleWithInvalidBattery)
        
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.field == "nominalBatteryCapacityWh" })
    }
    
    @Test
    fun `test battery capacity exceeds nominal fails validation`() {
        val vehicleWithInvalidBattery = createSampleVehicle().copy(
            usableBatteryCapacityWh = Energy(70000.0) // More than nominal (60000.0)
        )
        
        val result = ValidationEngine.validateVehicle(vehicleWithInvalidBattery)
        
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.field == "usableBatteryCapacityWh" })
    }
    
    @Test
    fun `test invalid mass fails validation`() {
        val vehicleWithInvalidMass = createSampleVehicle().copy(
            curbMassKg = Mass(-1000.0)
        )
        
        val result = ValidationEngine.validateVehicle(vehicleWithInvalidMass)
        
        assertEquals(ValidationStatus.INVALID, result.status)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.field == "curbMassKg" })
    }
    
    @Test
    fun `test vehicle with warnings`() {
        val vehicleWithWarnings = createSampleVehicle().copy(
            batteryChemistry = "" // Empty but provided
        )
        
        val result = ValidationEngine.validateVehicle(vehicleWithWarnings)
        
        assertEquals(ValidationStatus.PARTIALLY_VALIDATED, result.status)
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { it.field == "batteryChemistry" })
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