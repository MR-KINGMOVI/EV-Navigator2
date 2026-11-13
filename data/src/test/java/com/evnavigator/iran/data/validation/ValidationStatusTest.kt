package com.evnavigator.iran.data.validation

import com.evnavigator.iran.core.charging.AccessType
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.SourceType
import com.evnavigator.iran.data.charging.dto.NearbyStationProjection
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationStatusTest {

    @Test
    fun testVehicleEntityIsValid() {
        val base = VehicleEntity(
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
            batteryChemistry = "LFP",
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
            validationStatus = "VERIFIED",
            notes = null,
            schemaVersion = "1.0",
            dataVersion = "1.0",
            vehicleRecordVersion = "1.0"
        )

        assertTrue("VERIFIED status must be valid", base.copy(validationStatus = "VERIFIED").isValid())
        assertTrue("VALID status must be valid", base.copy(validationStatus = "VALID").isValid())
        assertFalse("INVALID status must not be valid", base.copy(validationStatus = "INVALID").isValid())
        assertFalse("PENDING status must not be valid", base.copy(validationStatus = "PENDING").isValid())
        assertFalse("UNKNOWN status must not be valid", base.copy(validationStatus = "UNKNOWN").isValid())
    }

    @Test
    fun testChargingStationEntityIsValid() {
        val base = ChargingStationEntity(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.7,
            longitude = 51.4,
            accessType = AccessType.PUBLIC,
            source = "MAPNA",
            sourceType = SourceType.UNKNOWN,
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VERIFIED"
        )

        assertTrue(base.copy(validationStatus = "VERIFIED").isValid())
        assertTrue(base.copy(validationStatus = "VALID").isValid())
        assertFalse(base.copy(validationStatus = "INVALID").isValid())
        assertFalse(base.copy(validationStatus = "PENDING").isValid())
        assertFalse(base.copy(validationStatus = "UNKNOWN").isValid())
    }

    @Test
    fun testChargingPortEntityIsValid() {
        val base = ChargingPortEntity(
            id = "port_1",
            stationId = "station_1",
            connectorType = ConnectorType.CCS_COMBO_2,
            connectorCount = 2,
            powerKw = 120.0,
            isAc = false,
            isDc = true,
            status = "AVAILABLE",
            source = "MEKO",
            retrievedDate = System.currentTimeMillis(),
            validationStatus = "VERIFIED"
        )

        assertTrue(base.copy(validationStatus = "VERIFIED").isValid())
        assertTrue(base.copy(validationStatus = "VALID").isValid())
        assertFalse(base.copy(validationStatus = "INVALID").isValid())
        assertFalse(base.copy(validationStatus = "PENDING").isValid())
        assertFalse(base.copy(validationStatus = "UNKNOWN").isValid())
    }

    @Test
    fun testNearbyStationProjectionIsValid() {
        val base = NearbyStationProjection(
            id = "station_1",
            canonicalName = "Test Station",
            latitude = 35.7,
            longitude = 51.4,
            accessType = "PUBLIC",
            connectorCount = 2,
            powerKw = 120.0,
            confidence = 0.95,
            validationStatus = "VERIFIED",
            retrievedDate = System.currentTimeMillis()
        )

        assertTrue(base.copy(validationStatus = "VERIFIED").isValid())
        assertTrue(base.copy(validationStatus = "VALID").isValid())
        assertFalse(base.copy(validationStatus = "INVALID").isValid())
        assertFalse(base.copy(validationStatus = "PENDING").isValid())
        assertFalse(base.copy(validationStatus = "UNKNOWN").isValid())
    }
}
