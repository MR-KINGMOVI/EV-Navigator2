package com.evnavigator.iran.core.vehicle

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserVehicleTest {
    
    @Test
    fun `test get effective vehicle applies overrides`() {
        val canonicalVehicle = createSampleVehicle()
        
        val userVehicle = UserVehicle(
            userId = "user123",
            canonicalVehicleId = canonicalVehicle.id,
            overrides = VehicleOverrides(
                name = "Custom Vehicle Name",
                curbMassKg = Mass(1600.0),
                batteryChemistry = "Custom Battery Chemistry"
            )
        )
        
        val effectiveVehicle = userVehicle.getEffectiveVehicle(canonicalVehicle)
        
        assertEquals("Custom Vehicle Name", effectiveVehicle.name)
        assertEquals(Mass(1600.0), effectiveVehicle.curbMassKg)
        assertEquals("Custom Battery Chemistry", effectiveVehicle.batteryChemistry)
        // Other fields should remain from canonical vehicle
        assertEquals(canonicalVehicle.nominalBatteryCapacityWh, effectiveVehicle.nominalBatteryCapacityWh)
    }
    
    @Test
    fun `test get effective vehicle preserves original metadata`() {
        val canonicalVehicle = createSampleVehicle()
        
        val userVehicle = UserVehicle(
            userId = "user123",
            canonicalVehicleId = canonicalVehicle.id,
            overrides = VehicleOverrides(
                name = "Custom Name"
            )
        )
        
        val effectiveVehicle = userVehicle.getEffectiveVehicle(canonicalVehicle)
        
        // These should preserve original values from canonical vehicle
        assertEquals(canonicalVehicle.dataQuality, effectiveVehicle.dataQuality)
        assertEquals(canonicalVehicle.source, effectiveVehicle.source)
        assertEquals(canonicalVehicle.sourceVersion, effectiveVehicle.sourceVersion)
        assertEquals(canonicalVehicle.retrievedDate, effectiveVehicle.retrievedDate)
        assertEquals(canonicalVehicle.validationStatus, effectiveVehicle.validationStatus)
    }
    
    @Test
    fun `test get effective vehicle with no overrides`() {
        val canonicalVehicle = createSampleVehicle()
        
        val userVehicle = UserVehicle(
            userId = "user123",
            canonicalVehicleId = canonicalVehicle.id,
            overrides = VehicleOverrides()
        )
        
        val effectiveVehicle = userVehicle.getEffectiveVehicle(canonicalVehicle)
        
        // Should be identical to canonical vehicle
        assertEquals(canonicalVehicle, effectiveVehicle)
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