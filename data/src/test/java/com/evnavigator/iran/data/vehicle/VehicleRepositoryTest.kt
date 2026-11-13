package com.evnavigator.iran.data.vehicle.repository

import com.evnavigator.iran.core.vehicle.*
import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import com.evnavigator.iran.data.vehicle.mapper.toDomainModel
import com.evnavigator.iran.data.vehicle.mapper.toEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VehicleRepositoryTest {
    
    @Test
    fun `test get vehicle by id`() = runTest {
        val mockDao = Mockito.mock(VehicleDao::class.java)
        val repository = VehicleRepository(mockDao)
        
        val entity = createSampleEntity()
        `when`(mockDao.getVehicleById("test-id")).thenReturn(entity)
        
        val result = repository.getVehicleById("test-id")
        
        assertNotNull(result)
        assertEquals("Test Vehicle", result.name)
    }
    
    @Test
    fun `test insert vehicle`() = runTest {
        val mockDao = Mockito.mock(VehicleDao::class.java)
        val repository = VehicleRepository(mockDao)
        
        val vehicle = createSampleVehicle()
        
        repository.insertVehicle(vehicle)
        
        // Verify that the DAO was called with the correct entity
        Mockito.verify(mockDao).insertVehicle(vehicle.toEntity())
    }
    
    @Test
    fun `test map entity to domain`() {
        val entity = createSampleEntity()
        val domainModel = entity.toDomainModel()
        
        assertEquals("Test Vehicle", domainModel.name)
        assertEquals(Mass(1500.0), domainModel.curbMassKg)
        assertEquals(Energy(60000.0), domainModel.nominalBatteryCapacityWh)
    }
    
    @Test
    fun `test map domain to entity`() {
        val vehicle = createSampleVehicle()
        val entity = vehicle.toEntity()
        
        assertEquals(vehicle.id.id, entity.id)
        assertEquals("Test Vehicle", entity.name)
        assertEquals(1500.0, entity.curbMassKg)
        assertEquals(60000.0, entity.nominalBatteryCapacityWh)
    }

    @Test
    fun `test ActiveVehicleRepository rejects invalid vehicle ID without falling back to first`() = runTest {
        ActiveVehicleRepository.resetSharedStateForTesting()
        val mockDao = Mockito.mock(VehicleDao::class.java)
        val sampleEntity = createSampleEntity()
        `when`(mockDao.getAllVehicles()).thenReturn(listOf(sampleEntity))
        `when`(mockDao.getVehicleById("non_existent_id")).thenReturn(null)
        `when`(mockDao.getVehicleById("test-id")).thenReturn(sampleEntity)

        val tempFile = java.io.File.createTempFile("active_vehicle_test", ".properties")
        tempFile.delete()
        tempFile.deleteOnExit()
        val activeRepo = ActiveVehicleRepository(mockDao, persistenceFile = tempFile)

        // Cold boot with no persisted selection must remain NoActiveVehicle (never auto-pick first())
        val coldBootVehicle = activeRepo.getActiveVehicle()
        kotlin.test.assertNull(coldBootVehicle)
        assertEquals("", activeRepo.getActiveVehicleId())
        kotlin.test.assertTrue(activeRepo.selectionState.value is ActiveVehicleSelectionState.NoActiveVehicle)

        // Attempt to select an invalid vehicle ID -> must return InvalidSelection and never pick first()
        val selected = activeRepo.setActiveVehicleId("non_existent_id")
        kotlin.test.assertFalse(selected.isSelected)
        kotlin.test.assertTrue(activeRepo.selectionState.value is ActiveVehicleSelectionState.InvalidSelection)

        // Select a valid vehicle ID -> must succeed and persist to backing file
        val validSelected = activeRepo.setActiveVehicleId("test-id")
        kotlin.test.assertTrue(validSelected.isSelected)
        assertEquals("test-id", activeRepo.getActiveVehicleId())
        kotlin.test.assertTrue(activeRepo.selectionState.value is ActiveVehicleSelectionState.Selected)
        kotlin.test.assertTrue(activeRepo.isPersistenceConfirmed.value)

        // Update SoC with explicit provenance
        val now = System.currentTimeMillis()
        kotlin.test.assertTrue(activeRepo.updateBatterySoc(73.5, VehicleSocSource.LIVE_VEHICLE_TELEMETRY, timestampEpochMs = now, nowEpochMs = now))
        assertEquals(73.5, activeRepo.batterySocPercent.value)
        assertEquals(73.5, activeRepo.confirmedBatterySocPercent.value)
        kotlin.test.assertTrue(activeRepo.isSocConfirmed(now))
        assertEquals(VehicleSocSource.LIVE_VEHICLE_TELEMETRY, activeRepo.socSource.value)
        kotlin.test.assertTrue(activeRepo.isPersistenceConfirmed.value)

        // Non-authoritative sources (ENERGY_MODEL_ESTIMATE, VEHICLE_PROFILE, DEFAULT_FALLBACK) must NEVER mark isSocConfirmed = true
        kotlin.test.assertTrue(activeRepo.updateBatterySoc(68.0, VehicleSocSource.ENERGY_MODEL_ESTIMATE, timestampEpochMs = now, nowEpochMs = now))
        kotlin.test.assertNull(activeRepo.confirmedBatterySocPercent.value)
        assertEquals(68.0, activeRepo.estimatedBatterySocPercent.value)
        kotlin.test.assertFalse(activeRepo.isSocConfirmed(now))

        kotlin.test.assertTrue(activeRepo.updateBatterySoc(80.0, VehicleSocSource.VEHICLE_PROFILE, timestampEpochMs = now, nowEpochMs = now))
        kotlin.test.assertNull(activeRepo.confirmedBatterySocPercent.value)
        kotlin.test.assertFalse(activeRepo.isSocConfirmed(now))

        // Invalid SOC values (NaN, Infinity, < 0, > 100) must be rejected without mutating state
        kotlin.test.assertTrue(activeRepo.updateBatterySoc(64.0, VehicleSocSource.USER_MANUAL_INPUT, timestampEpochMs = now, nowEpochMs = now))
        kotlin.test.assertFalse(activeRepo.updateBatterySoc(Double.NaN, VehicleSocSource.USER_MANUAL_INPUT, timestampEpochMs = now, nowEpochMs = now))
        kotlin.test.assertFalse(activeRepo.updateBatterySoc(Double.POSITIVE_INFINITY, VehicleSocSource.USER_MANUAL_INPUT, timestampEpochMs = now, nowEpochMs = now))
        kotlin.test.assertFalse(activeRepo.updateBatterySoc(-5.0, VehicleSocSource.USER_MANUAL_INPUT, timestampEpochMs = now, nowEpochMs = now))
        kotlin.test.assertFalse(activeRepo.updateBatterySoc(105.0, VehicleSocSource.USER_MANUAL_INPUT, timestampEpochMs = now, nowEpochMs = now))
        assertEquals(64.0, activeRepo.batterySocPercent.value)
        assertEquals(64.0, activeRepo.confirmedBatterySocPercent.value)

        // Stale timestamp (> 30 minutes old) must not be treated as confirmed
        val staleTs = now - (35L * 60L * 1000L)
        kotlin.test.assertTrue(activeRepo.updateBatterySoc(55.0, VehicleSocSource.OBD_II_LIVE, timestampEpochMs = staleTs, nowEpochMs = now))
        kotlin.test.assertNull(activeRepo.confirmedBatterySocPercent.value)
        kotlin.test.assertFalse(activeRepo.isSocConfirmed(now))

        // Repository without configured persistenceFile must report isPersistenceConfirmed = false (never silently claim tmpdir durability)
        ActiveVehicleRepository.resetSharedStateForTesting()
        val unconfiguredRepo = ActiveVehicleRepository(mockDao, persistenceFile = null)
        unconfiguredRepo.setActiveVehicleId("test-id")
        kotlin.test.assertFalse(unconfiguredRepo.isPersistenceConfirmed.value)
        kotlin.test.assertNotNull(unconfiguredRepo.lastPersistenceError.value)
        ActiveVehicleRepository.resetSharedStateForTesting()
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
    
    private fun createSampleEntity(): VehicleEntity {
        return VehicleEntity(
            id = "test-id",
            name = "Test Vehicle",
            manufacturer = "Test",
            model = "Model",
            generation = "Gen1",
            modelYear = 2023,
            trim = "Base",
            market = "IR",
            powertrainType = "BEV",
            curbMassKg = 1500.0,
            grossVehicleMassKg = 1800.0,
            lengthMm = 4500,
            widthMm = 1800,
            heightMm = 1500,
            wheelbaseMm = 2700,
            groundClearanceMm = 150,
            dragCoefficient = 0.25,
            frontalAreaM2 = 2.2,
            rollingResistanceCoefficient = 0.012,
            nominalBatteryCapacityWh = 60000.0,
            usableBatteryCapacityWh = 55000.0,
            batteryChemistry = "Lithium Iron Phosphate",
            nominalVoltageV = 370.0,
            batteryConfiguration = "48S10P",
            motorCount = 2,
            frontMotorPowerKw = 100.0,
            rearMotorPowerKw = 150.0,
            peakMotorPowerKw = 300.0,
            continuousMotorPowerKw = 200.0,
            torqueNm = 400,
            drivetrainType = "AWD",
            baselineConsumptionWhPer100Km = 1500.0,
            acChargingMaxPowerKw = 7.0,
            dcChargingMaxPowerKw = 120.0,
            chargingVoltageRangeV = "230-480",
            chargingCurrentRangeA = "6-32",
            chargePortCount = 2,
            connectorTypes = "TYPE_2,CCS_COMBO_2",
            wheelDiameterMm = 19,
            tireWidthMm = 225,
            dataQuality = "HIGH",
            source = "Test Source",
            sourceVersion = "1.0",
            retrievedDate = System.currentTimeMillis(),
            confidence = 0.95,
            validationStatus = "VALIDATED",
            notes = null,
            schemaVersion = "1.0",
            dataVersion = "1.0",
            vehicleRecordVersion = "1.0",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}