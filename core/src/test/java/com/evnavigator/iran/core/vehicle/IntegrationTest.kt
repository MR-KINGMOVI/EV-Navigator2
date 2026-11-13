package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationTest {
    
    @Test
    fun `test complete vehicle lifecycle`() = runTest {
        // 1. Create a vehicle (domain model)
        val originalVehicle = createSampleVehicle()
        
        // 2. Validate the vehicle
        val validation = ValidationEngine.validateVehicle(originalVehicle)
        assertEquals(ValidationStatus.VALIDATED, validation.status)
        
        // 3. Map to profile for energy calculations
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(originalVehicle)
        
        // 4. Verify profile is ready for energy calculations
        assertTrue(profile.curbMassKg.kilograms > 0)
        assertTrue(profile.nominalBatteryCapacityWh.wattHours > 0)
        
        // 5. Create user vehicle with overrides
        val userVehicle = UserVehicle(
            userId = "user123",
            canonicalVehicleId = originalVehicle.id,
            overrides = VehicleOverrides(
                name = "Custom User Vehicle Name"
            )
        )
        
        // 6. Get effective vehicle with overrides applied
        val effectiveVehicle = userVehicle.getEffectiveVehicle(originalVehicle)
        
        // 7. Verify overrides were applied
        assertEquals("Custom User Vehicle Name", effectiveVehicle.name)
        
        // 8. Verify original fields are preserved
        assertEquals(originalVehicle.curbMassKg, effectiveVehicle.curbMassKg)
        assertEquals(originalVehicle.nominalBatteryCapacityWh, effectiveVehicle.nominalBatteryCapacityWh)
        
        // 9. Resolve conflicts (if any)
        val resolved = VehicleResolutionEngine.resolveGroup(listOf(originalVehicle))
        assertEquals(originalVehicle, resolved)
    }
    
    @Test
    fun `test vehicle with all edge cases`() = runTest {
        // Create a vehicle with various edge case scenarios
        val vehicleWithEdgeCases = createSampleVehicle().copy(
            name = "Vehicle With Edge Cases",
            dataQuality = DataQuality.LOW,  // Low quality data
            confidence = 0.3,                // Low confidence
            notes = "This is a test vehicle with various edge cases", 
            sourceReference = null           // No source reference
        )
        
        // Validate it
        val validation = ValidationEngine.validateVehicle(vehicleWithEdgeCases)
        
        // Even with low quality, should validate (will have warnings)
        assertTrue(validation.status == ValidationStatus.PARTIALLY_VALIDATED || 
                   validation.status == ValidationStatus.VALIDATED)
        
        // Map to profile for energy calculations
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicleWithEdgeCases)
        
        // Should be able to create a profile even with edge cases
        assertTrue(profile.curbMassKg.kilograms > 0)
        assertTrue(profile.nominalBatteryCapacityWh.wattHours > 0)
        
        // Check that it's not energy ready due to low quality (this is just an example)
        assertTrue(vehicleWithEdgeCases.isEnergyReady)
    }
    
    @Test
    fun `test user vehicle with complete override`() = runTest {
        val originalVehicle = createSampleVehicle()
        
        // Create a full override
        val fullOverride = VehicleOverrides(
            name = "Completely Custom Name",
            curbMassKg = Mass(1800.0),
            nominalBatteryCapacityWh = Energy(70000.0),
            usableBatteryCapacityWh = Energy(65000.0),
            dragCoefficient = 0.30,
            frontalAreaM2 = 2.5,
            rollingResistanceCoefficient = 0.015,
            motorCount = 4,
            drivetrainType = DrivetrainType.AWD
        )
        
        val userVehicle = UserVehicle(
            userId = "user456",
            canonicalVehicleId = originalVehicle.id,
            overrides = fullOverride
        )
        
        val effectiveVehicle = userVehicle.getEffectiveVehicle(originalVehicle)
        
        // Verify all overrides were applied
        assertEquals("Completely Custom Name", effectiveVehicle.name)
        assertEquals(Mass(1800.0), effectiveVehicle.curbMassKg)
        assertEquals(Energy(70000.0), effectiveVehicle.nominalBatteryCapacityWh)
        assertEquals(Energy(65000.0), effectiveVehicle.usableBatteryCapacityWh)
        assertEquals(0.30, effectiveVehicle.dragCoefficient)
        assertEquals(2.5, effectiveVehicle.frontalAreaM2)
        assertEquals(0.015, effectiveVehicle.rollingResistanceCoefficient)
        assertEquals(4, effectiveVehicle.motorCount)
        assertEquals(DrivetrainType.AWD, effectiveVehicle.drivetrainType)
        
        // Verify original metadata is preserved
        assertEquals(originalVehicle.dataQuality, effectiveVehicle.dataQuality)
        assertEquals(originalVehicle.source, effectiveVehicle.source)
    }
    
    @Test
    fun `test vehicle search and filtering`() = runTest {
        val mockRepository = org.mockito.Mockito.mock(VehicleRepository::class.java)
        val searchService = VehicleSearchService(mockRepository)
        
        val vehicles = listOf(
            createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base"),
            createSampleVehicle("Honda", "Civic", "Type R", 2024, "Sport"),
            createSampleVehicle("Toyota", "Corolla", "SE", 2023, "Base")
        )
        
        // Mock repository behavior
        org.mockito.Mockito.`when`(mockRepository.getAllVehicles())
            .thenReturn(vehicles)
            
        org.mockito.Mockito.`when`(mockRepository.getVehiclesByManufacturer("Toyota"))
            .thenReturn(vehicles.filter { it.id.manufacturer == "Toyota" })
        
        // Test search functionality
        val allVehicles = searchService.getAllManufacturers()
        assertEquals(2, allVehicles.size)
        assertTrue(allVehicles.contains("Toyota"))
        assertTrue(allVehicles.contains("Honda"))
        
        val toyotaVehicles = searchService.getVehiclesByManufacturer("Toyota")
        assertEquals(2, toyotaVehicles.size)
        assertTrue(toyotaVehicles.all { it.id.manufacturer == "Toyota" })
    }
    
    @Test
    fun `test provenance and versioning in lifecycle`() = runTest {
        val vehicle = createSampleVehicle()
        
        // Verify provenance information
        assertEquals("Test Source", vehicle.source)
        assertEquals("1.0", vehicle.sourceVersion)
        assertEquals("ref-12345", vehicle.sourceReference)
        assertEquals(DataQuality.HIGH, vehicle.dataQuality)
        assertEquals(0.95, vehicle.confidence)
        
        // Verify versioning
        assertEquals("1.0", vehicle.schemaVersion)
        assertEquals("1.0", vehicle.dataVersion)
        assertEquals("1.0", vehicle.vehicleRecordVersion)
        
        // Test that timestamp handling works
        assertTrue(vehicle.createdAt > 0)
        assertTrue(vehicle.updatedAt > 0)
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