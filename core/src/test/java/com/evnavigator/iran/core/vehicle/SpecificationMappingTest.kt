package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecificationMappingTest {
    
    @Test
    fun `test round trip mapping vehicle to entity and back`() {
        val originalVehicle = createSampleVehicle()
        
        // Map to entity
        val entity = originalVehicle.toEntity()
        
        // Map back to domain model
        val mappedBackVehicle = entity.toDomainModel()
        
        // Verify key fields are preserved (some loss is expected due to simplified reverse mapping)
        assertEquals(originalVehicle.id.id, mappedBackVehicle.id.id)
        assertEquals(originalVehicle.name, mappedBackVehicle.name)
        assertEquals(originalVehicle.curbMassKg, mappedBackVehicle.curbMassKg)
        assertEquals(originalVehicle.nominalBatteryCapacityWh, mappedBackVehicle.nominalBatteryCapacityWh)
        assertEquals(originalVehicle.usableBatteryCapacityWh, mappedBackVehicle.usableBatteryCapacityWh)
        assertEquals(originalVehicle.motorCount, mappedBackVehicle.motorCount)
        assertEquals(originalVehicle.connectorTypes, mappedBackVehicle.connectorTypes)
    }
    
    @Test
    fun `test vehicle to profile mapping`() {
        val vehicle = createSampleVehicle()
        
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicle)
        
        // Verify critical fields are properly mapped
        assertEquals(vehicle.id.id, profile.id)
        assertEquals(vehicle.name, profile.name)
        assertEquals(vehicle.curbMassKg, profile.curbMassKg)
        assertEquals(vehicle.nominalBatteryCapacityWh, profile.nominalBatteryCapacityWh)
        assertEquals(vehicle.usableBatteryCapacityWh, profile.usableBatteryCapacityWh)
        
        // Verify defaults are applied where needed
        assertTrue(profile.frontalAreaM2 > 0)
        assertTrue(profile.dragCoefficient > 0)
        assertTrue(profile.rollingResistanceCoefficient >= 0)
    }
    
    @Test
    fun `test vehicle identity preservation`() {
        val vehicle = createSampleVehicle()
        
        // Ensure the unique identifier is preserved through mappings
        val entity = vehicle.toEntity()
        val mappedBack = entity.toDomainModel()
        
        assertEquals(vehicle.id.id, mappedBack.id.id)
        assertEquals(vehicle.id.manufacturer, mappedBack.id.manufacturer)
        assertEquals(vehicle.id.model, mappedBack.id.model)
        assertEquals(vehicle.id.generation, mappedBack.id.generation)
        assertEquals(vehicle.id.modelYear, mappedBack.id.modelYear)
        assertEquals(vehicle.id.trim, mappedBack.id.trim)
        assertEquals(vehicle.id.market, mappedBack.id.market)
        assertEquals(vehicle.id.powertrainType, mappedBack.id.powertrainType)
    }
    
    @Test
    fun `test vehicle provenance preservation`() {
        val vehicle = createSampleVehicle()
        
        val entity = vehicle.toEntity()
        val mappedBack = entity.toDomainModel()
        
        // Verify provenance information is preserved
        assertEquals(vehicle.source, mappedBack.source)
        assertEquals(vehicle.sourceVersion, mappedBack.sourceVersion)
        assertEquals(vehicle.sourceReference, mappedBack.sourceReference)
        assertEquals(vehicle.confidence, mappedBack.confidence)
        assertEquals(vehicle.dataQuality, mappedBack.dataQuality)
        assertEquals(vehicle.validationStatus, mappedBack.validationStatus)
    }
    
    @Test
    fun `test vehicle versioning preservation`() {
        val vehicle = createSampleVehicle()
        
        val entity = vehicle.toEntity()
        val mappedBack = entity.toDomainModel()
        
        // Verify versioning information is preserved
        assertEquals(vehicle.schemaVersion, mappedBack.schemaVersion)
        assertEquals(vehicle.dataVersion, mappedBack.dataVersion)
        assertEquals(vehicle.vehicleRecordVersion, mappedBack.vehicleRecordVersion)
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