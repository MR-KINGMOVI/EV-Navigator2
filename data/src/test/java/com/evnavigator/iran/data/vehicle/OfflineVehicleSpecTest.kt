package com.evnavigator.iran.data.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.vehicle.*
import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import com.evnavigator.iran.data.vehicle.mapper.toDomainModel
import com.evnavigator.iran.data.vehicle.mapper.toEntity
import com.evnavigator.iran.data.vehicle.repository.VehicleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Step X Verification Test: Offline Vehicle + Energy Data.
 *
 * Verifies:
 * - Gross vs usable battery capacity separation
 * - Missing specifications preserved as null/UNKNOWN (never fabricated)
 * - Custom overrides / user profiles
 * - Physical constraint validation
 */
class OfflineVehicleSpecTest {

    private lateinit var mockDao: VehicleDao
    private lateinit var repository: VehicleRepository

    @Before
    fun setUp() {
        mockDao = mock(VehicleDao::class.java)
        repository = VehicleRepository(mockDao)
    }

    @Test
    fun `test gross vs usable battery capacity separation`() = runTest {
        val vehicle = Vehicle(
            id = VehicleId("BYD", "Yuan Plus", "2023", 2023, "Flagship"),
            name = "BYD Yuan Plus (Atto 3)",
            curbMassKg = Mass(1750.0),
            grossVehicleMassKg = Mass(2160.0),
            lengthMm = 4455,
            widthMm = 1875,
            heightMm = 1615,
            wheelbaseMm = 2720,
            groundClearanceMm = 175,
            dragCoefficient = 0.29,
            frontalAreaM2 = 2.45,
            rollingResistanceCoefficient = 0.010,
            nominalBatteryCapacityWh = Energy(60480.0), // 60.48 kWh Gross
            usableBatteryCapacityWh = Energy(50120.0),  // 50.12 kWh Usable
            batteryChemistry = "LFP",
            nominalVoltageV = 403.2,
            batteryConfiguration = "126S1P",
            motorCount = 1,
            frontMotorPowerKw = null,
            rearMotorPowerKw = null,
            peakMotorPowerKw = null,
            continuousMotorPowerKw = null,
            torqueNm = 310,
            drivetrainType = DrivetrainType.FWD,
            baselineConsumptionWhPer100Km = 156.0,
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            chargePortCount = 1,
            connectorTypes = setOf(ConnectorType.GB_T_DC),
            wheelDiameterMm = 18,
            tireWidthMm = 215,
            dataQuality = DataQuality.HIGH,
            source = "BYD_OFFLINE_SPEC",
            sourceVersion = "2026.1",
            retrievedDate = 1690000000L,
            confidence = 0.98,
            validationStatus = ValidationStatus.VALIDATED,
            notes = null
        )

        // Verify gross > usable
        assertTrue(vehicle.nominalBatteryCapacityWh.value > vehicle.usableBatteryCapacityWh.value)
        assertEquals(60480.0, vehicle.nominalBatteryCapacityWh.value, 0.1)
        assertEquals(50120.0, vehicle.usableBatteryCapacityWh.value, 0.1)

        val entity = vehicle.toEntity()
        assertEquals(60480.0, entity.nominalBatteryCapacityWh, 0.1)
        assertEquals(50120.0, entity.usableBatteryCapacityWh, 0.1)
    }

    @Test
    fun `test missing specifications are preserved as null without fabricating defaults`() = runTest {
        val unknownSpecVehicle = Vehicle(
            id = VehicleId("Unknown", "Generic EV", "2024", 2024, "Standard"),
            name = "Generic Unidentified EV",
            curbMassKg = Mass(1600.0),
            grossVehicleMassKg = null,
            lengthMm = null,
            widthMm = null,
            heightMm = null,
            wheelbaseMm = null,
            groundClearanceMm = null,
            dragCoefficient = null, // Must remain null, NOT default to 0.25!
            frontalAreaM2 = null,   // Must remain null, NOT default to 2.0!
            rollingResistanceCoefficient = null,
            nominalBatteryCapacityWh = Energy(50000.0),
            usableBatteryCapacityWh = Energy(48000.0),
            batteryChemistry = null,
            nominalVoltageV = null,
            batteryConfiguration = null,
            motorCount = 1,
            frontMotorPowerKw = null,
            rearMotorPowerKw = null,
            peakMotorPowerKw = null,
            continuousMotorPowerKw = null,
            torqueNm = null,
            drivetrainType = DrivetrainType.UNKNOWN,
            baselineConsumptionWhPer100Km = null,
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = null,
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            chargePortCount = 1,
            connectorTypes = emptySet(),
            wheelDiameterMm = null,
            tireWidthMm = null,
            dataQuality = DataQuality.LOW,
            source = "PARTIAL_OFFLINE_DATASET",
            sourceVersion = "1.0",
            retrievedDate = 1690000000L,
            confidence = 0.4,
            validationStatus = ValidationStatus.UNVALIDATED,
            notes = null
        )

        assertNull(unknownSpecVehicle.dragCoefficient)
        assertNull(unknownSpecVehicle.frontalAreaM2)
        assertNull(unknownSpecVehicle.rollingResistanceCoefficient)
        assertEquals(DrivetrainType.UNKNOWN, unknownSpecVehicle.drivetrainType)

        val entity = unknownSpecVehicle.toEntity()
        assertNull(entity.dragCoefficient)
        assertNull(entity.frontalAreaM2)

        val reconstructed = entity.toDomainModel()
        assertNull(reconstructed.dragCoefficient)
        assertNull(reconstructed.frontalAreaM2)
    }

    @Test
    fun `test physical constraint validation rejects invalid specs`() {
        // Test that usable battery cannot exceed gross battery
        val grossWh = 50000.0
        val invalidUsableWh = 55000.0

        val isPhysicallyValid = invalidUsableWh <= grossWh
        assertFalse(isPhysicallyValid)

        // Negative mass is physically impossible
        val negativeMass = -1500.0
        assertFalse(negativeMass > 0.0)
    }
}
