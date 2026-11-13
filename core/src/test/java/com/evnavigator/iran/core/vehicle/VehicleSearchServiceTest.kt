package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleSearchServiceTest {
    
    @Test
    fun `test search vehicles by manufacturer`() = runTest {
        val mockRepository = Mockito.mock(VehicleRepository::class.java)
        val service = VehicleSearchService(mockRepository)
        
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Toyota", "Corolla", "SE", 2023, "Base")
        val vehicle3 = createSampleVehicle("Honda", "Civic", "Type R", 2024, "Sport")
        
        `when`(mockRepository.getVehiclesByManufacturer("Toyota"))
            .thenReturn(listOf(vehicle1, vehicle2))
        
        val results = service.getVehiclesByManufacturer("Toyota")
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.id.manufacturer == "Toyota" })
    }
    
    @Test
    fun `test search vehicles by model`() = runTest {
        val mockRepository = Mockito.mock(VehicleRepository::class.java)
        val service = VehicleSearchService(mockRepository)
        
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Honda", "Camry", "SE", 2023, "Base")
        
        `when`(mockRepository.getVehiclesByModel("Camry"))
            .thenReturn(listOf(vehicle1, vehicle2))
        
        val results = service.getVehiclesByModel("Camry")
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.id.model == "Camry" })
    }
    
    @Test
    fun `test search vehicles with multiple criteria`() = runTest {
        val mockRepository = Mockito.mock(VehicleRepository::class.java)
        val service = VehicleSearchService(mockRepository)
        
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Premium")
        
        `when`(mockRepository.getAllVehicles())
            .thenReturn(listOf(vehicle1, vehicle2))
        
        val results = service.searchVehicles(
            manufacturer = "Toyota",
            model = "Camry",
            year = 2023
        )
        
        assertEquals(2, results.size)
        assertTrue(results.all { 
            it.id.manufacturer == "Toyota" && 
            it.id.model == "Camry" && 
            it.id.modelYear == 2023 
        })
    }
    
    @Test
    fun `test get unique manufacturers`() = runTest {
        val mockRepository = Mockito.mock(VehicleRepository::class.java)
        val service = VehicleSearchService(mockRepository)
        
        val vehicle1 = createSampleVehicle("Toyota", "Camry", "XLE", 2023, "Base")
        val vehicle2 = createSampleVehicle("Honda", "Civic", "Type R", 2024, "Sport")
        val vehicle3 = createSampleVehicle("Toyota", "Corolla", "SE", 2023, "Base")
        
        `when`(mockRepository.getAllVehicles())
            .thenReturn(listOf(vehicle1, vehicle2, vehicle3))
        
        val manufacturers = service.getAllManufacturers()
        
        assertEquals(2, manufacturers.size)
        assertTrue(manufacturers.contains("Toyota"))
        assertTrue(manufacturers.contains("Honda"))
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
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.95,
            validationStatus = ValidationStatus.VALIDATED,
            notes = null
        )
    }
}