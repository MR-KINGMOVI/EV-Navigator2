package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceTest {
    
    @Test
    fun `test vehicle resolution performance`() = runTest {
        // Create a list of vehicles with different qualities and confidences
        val vehicles = (1..100).map { i ->
            createSampleVehicle(
                manufacturer = "Manufacturer$i",
                model = "Model$i", 
                generation = "Gen$i",
                modelYear = 2020 + (i % 5),
                trim = "Trim$i"
            ).copy(
                dataQuality = if (i % 3 == 0) DataQuality.HIGH else DataQuality.MEDIUM,
                confidence = 0.5 + (i % 10) * 0.05
            )
        }
        
        // Time the resolution process
        val startTime = System.currentTimeMillis()
        val resolvedVehicle = VehicleResolutionEngine.resolveVehicleSpecifications(vehicles)
        val endTime = System.currentTimeMillis()
        
        // Should resolve quickly
        assertTrue(endTime - startTime < 1000) // Should complete in under 1 second
        
        // Should return a vehicle
        assertTrue(resolvedVehicle != null)
    }
    
    @Test
    fun `test vehicle validation performance`() = runTest {
        val vehicle = createSampleVehicle()
        
        // Time the validation process
        val startTime = System.currentTimeMillis()
        val result = ValidationEngine.validateVehicle(vehicle)
        val endTime = System.currentTimeMillis()
        
        // Should validate quickly
        assertTrue(endTime - startTime < 100) // Should complete in under 100ms
        
        // Should be valid
        assertEquals(ValidationStatus.VALIDATED, result.status)
    }
    
    @Test
    fun `test vehicle mapping performance`() = runTest {
        val vehicle = createSampleVehicle()
        
        // Time the mapping process
        val startTime = System.currentTimeMillis()
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicle)
        val endTime = System.currentTimeMillis()
        
        // Should map quickly
        assertTrue(endTime - startTime < 100) // Should complete in under 100ms
        
        // Should produce a valid profile
        assertTrue(profile.curbMassKg.kilograms > 0)
        assertTrue(profile.nominalBatteryCapacityWh.wattHours > 0)
    }
    
    @Test
    fun `test vehicle search performance`() = runTest {
        val mockRepository = org.mockito.Mockito.mock(VehicleRepository::class.java)
        val service = VehicleSearchService(mockRepository)
        
        // Create a reasonable number of test vehicles
        val vehicles = (1..50).map { i ->
            createSampleVehicle(
                manufacturer = "Manufacturer$i",
                model = "Model",
                generation = "Gen1",
                modelYear = 2023,
                trim = "Trim$i"
            )
        }
        
        // Mock repository to return vehicles quickly
        org.mockito.Mockito.`when`(mockRepository.getAllVehicles())
            .thenReturn(vehicles)
        
        // Time the search process
        val startTime = System.currentTimeMillis()
        val results = service.searchVehicles(
            manufacturer = "Manufacturer10",
            model = "Model"
        )
        val endTime = System.currentTimeMillis()
        
        // Should complete quickly
        assertTrue(endTime - startTime < 500) // Should complete in under 500ms
        
        // Should return expected results
        assertEquals(1, results.size)
        assertEquals("Manufacturer10", results[0].id.manufacturer)
    }
    
    @Test
    fun `test vehicle identity resolution performance`() = runTest {
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Premium")
        
        // Time the identity comparison
        val startTime = System.currentTimeMillis()
        val sameIdentity = vehicle1.hasSameIdentity(vehicle2)
        val endTime = System.currentTimeMillis()
        
        // Should be very fast
        assertTrue(endTime - startTime < 10) // Should complete in under 10ms
        
        // Should correctly identify different identities
        assertEquals(false, sameIdentity)
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