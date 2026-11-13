package com.evnavigator.iran.core.energy.mapper

import com.evnavigator.iran.core.vehicle.*

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.energy.model.Power
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VehicleToVehicleProfileMapperTest {
    
    @Test
    fun `test map vehicle to vehicle profile`() {
        val vehicle = createSampleVehicle()
        
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicle)
        
        // Verify key mappings
        assertEquals(vehicle.id.id, profile.id)
        assertEquals(vehicle.name, profile.name)
        assertEquals(vehicle.curbMassKg, profile.curbMassKg)
        assertEquals(vehicle.nominalBatteryCapacityWh, profile.nominalBatteryCapacityWh)
        assertEquals(vehicle.usableBatteryCapacityWh, profile.usableBatteryCapacityWh)
        
        // Verify default values where not specified
        assertNotNull(profile.frontalAreaM2)
        assertNotNull(profile.dragCoefficient)
        assertNotNull(profile.rollingResistanceCoefficient)
    }
    
    @Test
    fun `test map vehicle to vehicle profile with payload`() {
        val vehicle = createSampleVehicle()
        
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicle, 200.0)
        
        // Verify payload is correctly applied
        assertEquals(Mass(1700.0), profile.totalOperatingMassKg) // 1500 + 200
    }
    
    @Test
    fun `test reverse mapping`() {
        val vehicle = createSampleVehicle()
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicle)
        
        val reversedVehicle = VehicleToVehicleProfileMapper.mapToVehicle(profile)
        
        // Basic assertions to ensure the mapping works
        assertEquals(profile.name, reversedVehicle.name)
        assertEquals(profile.nominalBatteryCapacityWh, reversedVehicle.nominalBatteryCapacityWh)
        assertEquals(profile.usableBatteryCapacityWh, reversedVehicle.usableBatteryCapacityWh)
        assertEquals(profile.curbMassKg, reversedVehicle.curbMassKg)
    }
    
    @Test
    fun testSpeedConsumptionCurveUnitsInKmh() {
        val vehicle = createSampleVehicle()
        val profile = VehicleToVehicleProfileMapper.mapToVehicleProfile(vehicle)
        val curve = profile.speedConsumptionCurve
        
        // 90 km/h must interpolate between 60 km/h (120.0) and 100 km/h (130.0)
        val c60 = curve.getConsumptionAtSafe(com.evnavigator.iran.core.energy.model.Speed.fromKilometersPerHour(60.0))
        val c90 = curve.getConsumptionAtSafe(com.evnavigator.iran.core.energy.model.Speed.fromKilometersPerHour(90.0))
        val c100 = curve.getConsumptionAtSafe(com.evnavigator.iran.core.energy.model.Speed.fromKilometersPerHour(100.0))
        
        org.junit.Assert.assertEquals(120.0, c60, 0.01)
        org.junit.Assert.assertEquals(130.0, c100, 0.01)
        org.junit.Assert.assertTrue("Consumption at 90 km/h ($c90) must be between 60 km/h ($c60) and 100 km/h ($c100)", c90 in 120.0..130.0)
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